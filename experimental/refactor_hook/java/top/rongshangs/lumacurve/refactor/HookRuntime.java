package top.rongshangs.lumacurve.refactor;

import android.content.*;
import android.database.ContentObserver;
import android.os.*;
import android.provider.Settings;
import org.json.*;
import de.robv.android.xposed.XposedBridge;
import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;

final class HookRuntime {
    static final String BUILD=AppBuild.BUILD,CONFIG="lumacurve_refactor_config_v1",STATUS="lumacurve_refactor_status_v1",REFRESH="lumacurve_refactor_refresh_v1";
    final Object owner; final RefactorAdapter kernel; final Handler handler; final Context context;
    final String fingerprint; final ContentObserver observer,refresh;
    final ThermalPolicy thermalGate=new ThermalPolicy();
    final MemoryPolicy memoryPolicy=new MemoryPolicy();
    final ArrayDeque<String> logs=new ArrayDeque<>();
    final PowerManager power;
    final PowerManager.OnThermalStatusChangedListener thermalListener;
    final BroadcastReceiver batteryListener;
    String revision="",phase="attached",message="已连接小米曲线，当前沿用官方基准";
    long consumed,lastPublish,thermalSkipped,lastThermalLog,viewUntil;
    float lastLux=Float.NaN,lastNit=Float.NaN,batteryTemperature=Float.NaN,thermalCeiling=43,lastOfficialCap=Float.NaN;
    int thermalSeverity=-1,boundUser=-1;
    boolean closed,changing,faultPending,thermalEnabled,receiverRegistered,thermalRegistered,publishQueued;
    float memoryStrength=1;long memoryEvents,lastMemoryLog;String lastReset="";
    boolean responseEnabled;long brightenDelay=1500,darkenDelay=5000,responseAdjustments,memoryWindow=1500;float memoryLuxRange=.3f,thermalCooling=1;
    boolean smallResponseEnabled;long smallBrightenDelay=5000,smallResponseAdjustments;
    boolean lowLightEnabled;long lowLightMainAdjustments,lowLightAssistAdjustments;
    final PipelineHistory pipelineHistory=new PipelineHistory();PipelineHistory.Frame traceFrame;
    final ArrayDeque<JSONObject> outputHistory=new ArrayDeque<>();
    final Map<String,JSONObject> lastOutput=new HashMap<>();
    java.lang.reflect.Method hdrProbe;
    java.lang.reflect.Method strategyProbe;Object strategyController;
    HookRuntime(Object owner,Object ref,Handler handler)throws Exception {
        this.owner=owner;this.handler=handler;context=(Context)HookEntry.get(owner,"mContext");fingerprint=Build.FINGERPRINT;
        Class<?> util=Class.forName("com.android.server.display.RefactorBrightnessUtil",true,ref.getClass().getClassLoader());
        if(HookEntry.field(util,"MIN_NIT").getType()!=float.class||HookEntry.field(util,"MAX_NIT").getType()!=float.class)throw new IllegalStateException("逻辑亮度边界类型已变化");
        float min=((Number)HookEntry.field(util,"MIN_NIT").get(null)).floatValue(),max=((Number)HookEntry.field(util,"MAX_NIT").get(null)).floatValue();
        kernel=new RefactorAdapter(ref,min,max);
        try{hdrProbe=owner.getClass().getDeclaredMethod("isHdrScene");if(hdrProbe.getReturnType()!=boolean.class)hdrProbe=null;else hdrProbe.setAccessible(true);}catch(Throwable optional){}
        observer=new ContentObserver(handler){public void onChange(boolean self){reload();}};
        refresh=new ContentObserver(handler){public void onChange(boolean self){viewUntil=SystemClock.elapsedRealtime()+5000;publish();}};
        power=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
        thermalListener=level->{thermalSeverity=level;environmentChanged();};
        batteryListener=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){
            batteryTemperature=intent.hasExtra(BatteryManager.EXTRA_TEMPERATURE)?intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1000)/10f:Float.NaN;
            environmentChanged();
        }};
    }
    void log(String value){
        String line="["+new SimpleDateFormat("MM-dd HH:mm:ss",Locale.ROOT).format(new Date())+"] "+value;
        if(logs.size()>=160)logs.removeFirst();logs.addLast(line);
        XposedBridge.log("LumaCurve "+value);
    }
    void start(){
        try{
            context.getContentResolver().registerContentObserver(Settings.Global.getUriFor(CONFIG),false,observer);
            context.getContentResolver().registerContentObserver(Settings.Global.getUriFor(REFRESH),false,refresh);
            if(power!=null){thermalSeverity=power.getCurrentThermalStatus();power.addThermalStatusListener(command->handler.post(command),thermalListener);thermalRegistered=true;}
            Intent initial=context.registerReceiver(batteryListener,new IntentFilter(Intent.ACTION_BATTERY_CHANGED),null,handler,Context.RECEIVER_EXPORTED);receiverRegistered=true;
            if(initial!=null)batteryListener.onReceive(context,initial);
            log("系统曲线已连接；主屏接口核对通过");reload();
        }catch(Throwable error){fault(error);}
    }
    void close(){
        closed=true;
        try{context.getContentResolver().unregisterContentObserver(observer);context.getContentResolver().unregisterContentObserver(refresh);}catch(Throwable ignored){}
        try{if(receiverRegistered)context.unregisterReceiver(batteryListener);}catch(Throwable ignored){}
        try{if(thermalRegistered)power.removeThermalStatusListener(thermalListener);}catch(Throwable ignored){}
        thermalEnabled=false;try{if(kernel.plan()!=null)kernel.configure(null);}catch(Throwable ignored){}
    }
    boolean relaxThermal(){
        return !closed&&phase.equals("active")&&appliesToUser()&&thermalGate.evaluate(thermalEnabled,HookEntry.thermalSupported.contains(owner.getClass()),thermalSeverity,batteryTemperature,thermalCeiling);
    }
    void environmentChanged(){
        if(closed)return;
        boolean before=thermalAllowed;boolean now=relaxThermal();thermalAllowed=now;
        if(before!=now){log(now?"温控亮度保持可用（仅显示层）":"已交回官方温控亮度策略");requestRecalculation();}
        queuePublish();
    }
    boolean thermalAllowed;
    void thermalCall(float cap,float input){
        lastOfficialCap=cap;
        if(Float.isFinite(input)&&input>cap){thermalSkipped++;
            if(SystemClock.elapsedRealtime()-lastThermalLog>=10000){lastThermalLog=SystemClock.elapsedRealtime();log("已跳过显示层温控降亮；电池 "+String.format(Locale.ROOT,"%.1f℃",batteryTemperature));}
        }
        queuePublish();
    }
    void memoryEvent(float lux,float requested,float remembered){
        memoryEvents++;
        if(SystemClock.elapsedRealtime()-lastMemoryLog>=1000){lastMemoryLog=SystemClock.elapsedRealtime();
            log(Float.isNaN(remembered)?"手动亮度已调整，本次不写入曲线锚点":"官方手动锚点更新："+String.format(Locale.ROOT,"%.1f lux，记忆强度 %.0f%%",lux,memoryStrength*100));
        }
        queuePublish();
    }
    void queuePublish(){
        if(closed||publishQueued)return;publishQueued=true;
        long delay=Math.max(0,publishInterval()-(SystemClock.elapsedRealtime()-lastPublish));
        handler.postDelayed(()->{publishQueued=false;if(!closed)publish();},delay);
    }
    void reload(){
        if(closed||changing)return;changing=true;
        lowLightEnabled=false;
        try{
            String text=Settings.Global.getString(context.getContentResolver(),CONFIG);
            if(text==null||text.equals("null")){
                if(kernel.plan()!=null)kernel.configure(null);thermalEnabled=false;responseEnabled=false;smallResponseEnabled=false;memoryStrength=1;phase="attached";revision="";message="已连接 · 官方曲线";
            }else{
                if(text.length()>2048)throw new IllegalArgumentException("配置过长");JSONObject config=new JSONObject(text);
                if(config.getInt("schema")!=1)throw new IllegalArgumentException("配置格式不兼容");
                revision=config.getString("revision");if(!revision.matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("配置标识无效");
                if(config.getBoolean("enabled")){
                    if(!fingerprint.equals(config.getString("fingerprint")))throw new IllegalArgumentException("系统已更新，请重新应用曲线");
                    if(config.getInt("user_serial")!=kernel.integer("mUserSerial"))throw new IllegalArgumentException("当前用户与曲线配置不一致");
                    float ceiling=(float)config.optDouble("thermal_ceiling",43);ThermalPolicy.validate(ceiling);
                    boolean thermal=config.optBoolean("thermal_relax",false);
                    float strength=(float)config.optDouble("memory_strength",1);MemoryPolicy.validate(strength);
                    long memoryMs=config.optLong("memory_window",1500);float memoryRange=(float)config.optDouble("memory_lux_range",.3);MemoryPolicy.validateGrouping(memoryMs,memoryRange);
                    float cooling=(float)config.optDouble("thermal_cooling",1);ThermalPolicy.validateCooling(cooling);
                    long bright=config.optLong("brighten_delay",1500),dark=config.optLong("darken_delay",5000);DelayPolicy.validate(bright,dark);
                    long small=config.optLong("small_brighten_delay",5000);DelayPolicy.validateSmall(small);
                    if(thermal&&!HookEntry.thermalSupported.contains(owner.getClass()))throw new IllegalArgumentException("此固件的温控亮度接口尚未兼容");
                    float[] factors=CurvePlan.factors(config.getString("factors"));
                    boolean curveChanged=kernel.plan()==null||!Arrays.equals(kernel.plan().nits(),new CurvePlan(kernel.factoryLux,kernel.factoryNit,kernel.min,kernel.max,factors).nits());
                    if(curveChanged)kernel.configure(factors);
                    boundUser=config.getInt("user_serial");thermalCeiling=ceiling;thermalEnabled=thermal;memoryStrength=strength;
                    memoryWindow=memoryMs;memoryLuxRange=memoryRange;memoryPolicy.configure(memoryMs,memoryRange);thermalCooling=cooling;thermalGate.configure(cooling);
                    brightenDelay=bright;darkenDelay=dark;responseEnabled=config.optBoolean("response_override",false);
                    smallBrightenDelay=small;smallResponseEnabled=config.optBoolean("small_brighten_override",false);
                    lowLightEnabled=config.optBoolean("low_light_stability",false);
                    if(lowLightEnabled&&!LowLightTuning.supported(this))throw new IllegalArgumentException("暗光稳定接口尚未完整兼容");
                    phase="active";message="自定义曲线已接入，官方光感和过渡动画继续运行";
                    log(curveChanged?"曲线已应用，重新建立基础锚点":"设置已更新，保留当前手动锚点");
                }else{
                    if(kernel.plan()!=null)kernel.configure(null);thermalEnabled=false;responseEnabled=false;smallResponseEnabled=false;memoryStrength=1;phase="attached";message="已恢复官方曲线与温控策略";log(message);
                }
                String reset=config.optString("reset_anchors","");
                if(!reset.isEmpty()&&!reset.equals(lastReset)){
                    kernel.configure(kernel.plan()==null?null:CurvePlan.factors(config.getString("factors")));lastReset=reset;memoryPolicy.reset();log("已清除本次系统曲线的手动锚点，保留基础曲线");
                }
            }
            consumed=0;lastLux=lastNit=Float.NaN;thermalAllowed=relaxThermal();publish();requestRecalculation();
        }catch(Throwable error){
            lowLightEnabled=false;
            thermalEnabled=false;responseEnabled=false;smallResponseEnabled=false;memoryStrength=1;try{kernel.configure(null);}catch(Throwable rollback){XposedBridge.log("LumaCurve fallback failed: "+rollback);}
            phase="error";message="未启用："+error;log(message);publish();
        }finally{changing=false;}
    }
    void requestRecalculation(){
        try{
            Object dpc=HookEntry.get(owner,"mDisplayPowerController");if(dpc!=null)dpc.getClass().getMethod("updateBrightness").invoke(dpc);
            Object abc=HookEntry.get(owner,"mAutomaticBrightnessControllerImpl");
            if(abc!=null&&HookEntry.get(abc,"mAutomaticBrightnessController")!=null)owner.getClass().getMethod("updateAutoBrightness").invoke(owner);
        }catch(Throwable notReady){XposedBridge.log("LumaCurve deferred OEM update: "+notReady);}
    }
    void fault(Throwable error){
        if(closed||faultPending)return;faultPending=true;thermalEnabled=false;lowLightEnabled=false;
        handler.post(()->{if(closed)return;try{kernel.configure(null);}catch(Throwable ignored){}
            phase="error";message="接入异常，已尝试恢复官方："+error;log(message);publish();faultPending=false;});
    }
    boolean appliesToUser(){try{return boundUser==kernel.integer("mUserSerial");}catch(Throwable error){return false;}}
    void userChanged(){thermalEnabled=false;try{kernel.configure(null);phase="error";message="用户已切换，恢复官方策略";log(message);publish();}catch(Throwable error){fault(error);}}
    void sample(float lux,float nit){
        if(closed||!Float.isFinite(lux)||!Float.isFinite(nit)||lux<0||nit<0)return;
        lastLux=lux;lastNit=nit;if(kernel.plan()!=null)consumed++;
        if(consumed==1||SystemClock.elapsedRealtime()-lastPublish>=publishInterval())publish();
    }
    long publishInterval(){return SystemClock.elapsedRealtime()<viewUntil?2000:10000;}
    void publish(){
        lastPublish=SystemClock.elapsedRealtime();
        try{
            JSONObject status=new JSONObject().put("build",BUILD).put("phase",phase).put("message",message).put("revision",revision)
                .put("pid",android.os.Process.myPid()).put("fingerprint",fingerprint).put("process_start",processStart())
                .put("user_serial",kernel.integer("mUserSerial")).put("elapsed_ms",lastPublish).put("consumed",consumed)
                .put("auto_mode",kernel.get("mUseAutoBrightness")).put("user_anchor_lux",kernel.number("mUserLux"))
                .put("factory_lux",array(kernel.factoryLux)).put("factory_logical_nit",array(kernel.factoryNit)).put("min_logical_nit",kernel.min).put("max_logical_nit",kernel.max)
                .put("thermal_supported",HookEntry.thermalSupported.contains(owner.getClass())).put("thermal_relax",thermalEnabled)
                .put("thermal_permitted",relaxThermal()).put("thermal_ceiling",thermalCeiling).put("thermal_severity",thermalSeverity)
                .put("thermal_skipped",thermalSkipped).put("logs",new JSONArray(logs));
            status.put("memory_strength",memoryStrength).put("memory_events",memoryEvents).put("good_curve_available",kernel.get("mIsHaveGoodCurve"));
            status.put("memory_window",memoryWindow).put("memory_lux_range",memoryLuxRange).put("thermal_cooling",thermalCooling)
                .put("response_supported",ResponseTuning.supported(owner)).put("response_override",responseEnabled).put("brighten_delay",brightenDelay).put("darken_delay",darkenDelay).put("response_adjustments",responseAdjustments);
            status.put("small_response_supported",ResponseTuning.smallSupported(owner)).put("small_brighten_override",smallResponseEnabled).put("small_brighten_delay",smallBrightenDelay).put("small_response_adjustments",smallResponseAdjustments);
            status.put("low_light_supported",LowLightTuning.supported(this)).put("low_light_stability",lowLightEnabled)
                .put("low_light_limit_lux",LowLightPolicy.LIMIT_LUX).put("low_light_brighten_ms",LowLightPolicy.BRIGHTEN_MS).put("low_light_darken_ms",LowLightPolicy.DARKEN_MS)
                .put("low_light_main_adjustments",lowLightMainAdjustments).put("low_light_assist_adjustments",lowLightAssistAdjustments);
            JSONArray trace=new JSONArray();for(PipelineHistory.Frame f:pipelineHistory.snapshot())trace.put(traceJson(f));
            status.put("pipeline_trace",trace).put("output_trace",new JSONArray(outputHistory));
            PipelineHistory.Frame recent=pipelineHistory.last();if(recent!=null)status.put("last_pipeline",traceJson(recent));
            Boolean hdr=hdrActive();if(hdr!=null)status.put("hdr_active",hdr);
            int count=kernel.integer("mAnchorCount");
            status.put("current_anchors_lux",array(Arrays.copyOf((float[])kernel.get("mAnchorLux"),count)))
                .put("current_anchors_nit",array(Arrays.copyOf((float[])kernel.get("mAnchorNit"),count)));
            JSONArray flags=new JSONArray();boolean[] user=(boolean[])kernel.get("mAnchorIsUserDrag");for(int i=0;i<count;i++)flags.put(user[i]);status.put("manual_anchor_flags",flags);
            collectOfficial(status);
            if(Float.isFinite(batteryTemperature))status.put("battery_temperature",batteryTemperature);
            if(Float.isFinite(lastOfficialCap))status.put("thermal_last_cap",lastOfficialCap);
            if(Float.isFinite(lastLux))status.put("last_lux",lastLux);
            if(Float.isFinite(lastNit))status.put("calculated_physical_nit",lastNit);
            try{
                Object dpc=HookEntry.get(owner,"mDisplayPowerController");
                Object display=dpc.getClass().getMethod("getDisplayPowerState").invoke(dpc);
                float brightness=((Number)display.getClass().getMethod("getScreenBrightness").invoke(display)).floatValue();
                float nit=((Number)owner.getClass().getMethod("convertBrightnessToNit",float.class).invoke(owner,brightness)).floatValue();
                if(Float.isFinite(nit)&&nit>=0)status.put("actual_nit",nit);
                status.put("screen_state",display.getClass().getMethod("getScreenState").invoke(display));
            }catch(Throwable unavailable){}
            CurvePlan plan=kernel.plan();if(plan!=null)status.put("active_logical_nit",array(plan.nits()));
            if(!Settings.Global.putString(context.getContentResolver(),STATUS,status.toString()))throw new IOException("状态写入失败");
        }catch(Throwable error){XposedBridge.log("LumaCurve status: "+error);}
    }
    void collectOfficial(JSONObject status){
        try{Object dpc=HookEntry.get(owner,"mDisplayPowerController");String strategy=outputStrategy(dpc);if(strategy!=null)status.put("output_strategy",strategy);
            status.put("output_reason",String.valueOf(HookEntry.get(dpc,"mBrightnessReason")));
        }catch(Throwable optional){}
        try{Object hbm=HookEntry.get(owner,"mHbmController");if(hbm!=null){
            int mode=((Number)hbm.getClass().getMethod("getHighBrightnessMode").invoke(hbm)).intValue();status.put("hbm_mode",mode);
            Class<?> info=Class.forName("android.hardware.display.BrightnessInfo",false,owner.getClass().getClassLoader());
            int sunlight=info.getField("HIGH_BRIGHTNESS_MODE_SUNLIGHT").getInt(null);status.put("sunlight_active",mode==sunlight);
        }}catch(Throwable optional){}
        try{
            Object impl=HookEntry.get(owner,"mAutomaticBrightnessControllerImpl"),abc=HookEntry.get(impl,"mAutomaticBrightnessController");
            readNumber(status,"official_effective_lux",abc,"mAmbientLux");readNumber(status,"official_observed_lux",abc,"mLastObservedLux");
            readNumber(status,"brightening_lux_threshold",abc,"mAmbientBrighteningThreshold");readNumber(status,"darkening_lux_threshold",abc,"mAmbientDarkeningThreshold");
            Object dual=HookEntry.get(impl,"mDualSensorPolicy");if(dual!=null){
                readNumber(status,"main_fast_lux",dual,"mMainFastAmbientLux");
                status.put("assist_valid",HookEntry.get(dual,"mAssistAmbientLuxValid"));
                if((Boolean)HookEntry.get(dual,"mAssistAmbientLuxValid"))readNumber(status,"assist_fast_lux",dual,"mAssistFastAmbientLux");
                status.put("sensor_policy_flag",HookEntry.get(dual,"mUseLightSensorFlag"));
                String reference=sensorName(((Number)HookEntry.get(dual,"mUseLightSensorFlag")).intValue());if(reference!=null)status.put("sensor_reference_name",reference);
            }
            status.put("proximity_near",HookEntry.get(impl,"mProximityPositive"));
        }catch(Throwable optional){}
        readNumber(status,"official_thermal_cap",owner,"mThermalMaxBrightness");
    }
    static void readNumber(JSONObject json,String key,Object object,String field){try{float n=((Number)HookEntry.get(object,field)).floatValue();if(Float.isFinite(n)&&n>=0)json.put(key,n);}catch(Throwable optional){}}
    Boolean hdrActive(){try{return hdrProbe==null?null:(Boolean)hdrProbe.invoke(owner);}catch(Throwable absent){return null;}}
    float mainCandidate(Object abc,Object impl){
        try{Object dual=HookEntry.get(impl,"mDualSensorPolicy");if(dual!=null)return optionalNumber(dual,"mMainFastAmbientLux");}catch(Throwable absent){}
        return optionalNumber(abc,"mFastAmbientLux");
    }
    String sensorName(int flag){try{Class<?> stub=Class.forName("com.android.server.display.AutomaticBrightnessControllerStub",false,owner.getClass().getClassLoader());
        int main=HookEntry.field(stub,"USE_MAIN_LIGHT_SENSOR").getInt(null),assist=HookEntry.field(stub,"USE_ASSIST_LIGHT_SENSOR").getInt(null);
        return flag==main?"main":flag==assist?"assist":"other";
    }catch(Throwable absent){return null;}}
    boolean lowLightApplies(Object abc,Object impl,float candidateLux){
        if(!lowLightEnabled||closed||!phase.equals("active")||!appliesToUser())return false;
        try{Boolean hdr=hdrActive();if(hdr==null)return false;
            if(!Boolean.TRUE.equals(optionalBoolean(abc,"mAmbientLuxValid")))return false;
            Object dpc=HookEntry.get(owner,"mDisplayPowerController"),display=dpc.getClass().getMethod("getDisplayPowerState").invoke(dpc);
            int screen=((Number)display.getClass().getMethod("getScreenState").invoke(display)).intValue();
            java.lang.reflect.Method idle=abc.getClass().getDeclaredMethod("isInIdleMode");idle.setAccessible(true);
            return LowLightPolicy.applies(true,(Boolean)kernel.get("mUseAutoBrightness"),screen==2,
                (Boolean)idle.invoke(abc),(Boolean)impl.getClass().getMethod("getDrivingStatus").invoke(impl),hdr,
                ((Number)HookEntry.get(abc,"mAmbientLux")).floatValue(),candidateLux);
        }catch(Throwable absent){return false;}
    }
    static float optionalNumber(Object o,String field){try{return ((Number)HookEntry.get(o,field)).floatValue();}catch(Throwable absent){return Float.NaN;}}
    static Boolean optionalBoolean(Object o,String field){try{return (Boolean)HookEntry.get(o,field);}catch(Throwable absent){return null;}}
    PipelineHistory.Frame beginTrace(Object abc){
        PipelineHistory.Frame f=pipelineHistory.begin(SystemClock.uptimeMillis(),System.currentTimeMillis());f.lux=optionalNumber(abc,"mAmbientLux");
        f.nightWake=optionalBoolean(abc,"mIsNightWakeMode");
        try{Object model=HookEntry.get(abc,"mShortTermModel");Boolean valid=optionalBoolean(model,"mIsValid");float anchor=optionalNumber(model,"mAnchor");
            if(valid!=null&&Float.isFinite(anchor))f.shortTermMemory=valid&&anchor>=0;
        }catch(Throwable absent){}
        try{Object impl=HookEntry.get(owner,"mAutomaticBrightnessControllerImpl"),dual=HookEntry.get(impl,"mDualSensorPolicy");
            f.main=optionalNumber(dual,"mMainFastAmbientLux");if(Boolean.TRUE.equals(optionalBoolean(dual,"mAssistAmbientLuxValid")))f.assist=optionalNumber(dual,"mAssistFastAmbientLux");
            f.sensor=((Number)HookEntry.get(dual,"mUseLightSensorFlag")).intValue();
            f.sensorName=sensorName(f.sensor);
        }catch(Throwable absent){}return f;
    }
    void finishTrace(PipelineHistory.Frame f,Object abc,boolean failed){
        if(f.route.equals("unobserved")&&!failed)return;
        f.failed=failed;f.finalTarget=optionalNumber(abc,"mScreenAutoBrightness");PipelineHistory.Frame previous=pipelineHistory.last();
        pipelineHistory.finish(f);
        if(previous!=null&&Float.isFinite(f.lux)&&Float.isFinite(previous.lux)&&Math.abs(f.lux-previous.lux)>Math.max(3,Math.max(f.lux,previous.lux)*.5f))
            log("照度跳变："+String.format(Locale.ROOT,"%.1f → %.1f lux",previous.lux,f.lux)+" · "+f.route);
        if(previous!=null&&!previous.route.equals(f.route))log("曲线路径切换："+previous.route+" → "+f.route);
        if(previous!=null&&previous.sensor>=0&&f.sensor>=0&&previous.sensor!=f.sensor)log("光感参考切换："+previous.sensor+" → "+f.sensor);
        if(previous!=null&&Float.isFinite(f.sceneIn)&&Float.isFinite(f.sceneOut)&&Float.isFinite(previous.sceneIn)&&Float.isFinite(previous.sceneOut)&&PipelineHistory.Frame.changed(f.sceneIn,f.sceneOut)!=PipelineHistory.Frame.changed(previous.sceneIn,previous.sceneOut))log("场景修正："+(PipelineHistory.Frame.changed(f.sceneIn,f.sceneOut)?"开始改写目标":"停止改写目标"));
        if(previous!=null&&Float.isFinite(f.overrideIn)&&Float.isFinite(f.overrideOut)&&Float.isFinite(previous.overrideIn)&&Float.isFinite(previous.overrideOut)&&PipelineHistory.Frame.changed(f.overrideIn,f.overrideOut)!=PipelineHistory.Frame.changed(previous.overrideIn,previous.overrideOut))log("手动保持修正："+(PipelineHistory.Frame.changed(f.overrideIn,f.overrideOut)?"开始改写目标":"停止改写目标"));
        queuePublish();
    }
    static void traceNumber(JSONObject j,String key,float n)throws JSONException{if(Float.isFinite(n)&&n>=0)j.put(key,n);}
    static JSONObject traceJson(PipelineHistory.Frame f)throws JSONException{
        JSONObject j=new JSONObject().put("sequence",f.sequence).put("uptime_ms",f.uptime).put("unix_ms",f.unixMs).put("route",f.route).put("failed",f.failed);
        traceNumber(j,"lux",f.lux);traceNumber(j,"main_lux",f.main);traceNumber(j,"assist_lux",f.assist);if(f.sensor>=0)j.put("sensor_reference",f.sensor);
        if(f.sensorName!=null)j.put("sensor_reference_name",f.sensorName);
        traceNumber(j,"mapped_brightness",f.mapped);traceNumber(j,"curve_brightness",f.curve);traceNumber(j,"scene_before",f.sceneIn);traceNumber(j,"scene_after",f.sceneOut);
        traceNumber(j,"override_before",f.overrideIn);traceNumber(j,"override_after",f.overrideOut);traceNumber(j,"auto_target",f.finalTarget);
        if(f.nightWake!=null)j.put("night_wake",f.nightWake);if(f.shortTermMemory!=null)j.put("short_term_memory",f.shortTermMemory);
        if(Float.isFinite(f.sceneIn)&&Float.isFinite(f.sceneOut))j.put("scene_changed",PipelineHistory.Frame.changed(f.sceneIn,f.sceneOut));
        if(Float.isFinite(f.overrideIn)&&Float.isFinite(f.overrideOut))j.put("override_changed",PipelineHistory.Frame.changed(f.overrideIn,f.overrideOut));return j;
    }
    void outputTrace(String method,float before,float after,String reason){
        try{Boolean hdr=hdrActive();String strategy=outputStrategy(HookEntry.get(owner,"mDisplayPowerController"));
            String limitedReason=reason.substring(0,Math.min(160,reason.length()));
            JSONObject sameStage=lastOutput.get(method);
            if(sameStage!=null&&Float.compare(before,(float)sameStage.optDouble("before",Double.NaN))==0&&Float.compare(after,(float)sameStage.optDouble("after",Double.NaN))==0&&
                    limitedReason.equals(sameStage.optString("reason"))&&Objects.equals(hdr,sameStage.has("hdr")?sameStage.optBoolean("hdr"):null)&&Objects.equals(strategy,sameStage.optString("strategy",null)))return;
            // Unchanged per-frame calls do not allocate a record or schedule state publication.
            JSONObject j=new JSONObject().put("uptime_ms",SystemClock.uptimeMillis()).put("unix_ms",System.currentTimeMillis()).put("stage",method).put("reason",limitedReason);
            traceNumber(j,"before",before);traceNumber(j,"after",after);if(hdr!=null)j.put("hdr",hdr);if(strategy!=null)j.put("strategy",strategy);
            lastOutput.put(method,j);JSONObject previous=outputHistory.peekLast();
            if(previous!=null&&hdr!=null&&previous.has("hdr")&&hdr!=previous.optBoolean("hdr"))log(hdr?"HDR 场景进入，输出可能增强":"HDR 场景退出，输出恢复常规策略");
            if(previous!=null&&strategy!=null&&previous.has("strategy")&&!strategy.equals(previous.optString("strategy")))log("输出路径切换："+previous.optString("strategy")+" → "+strategy);
            // A separate event, not a claim that the latest calculation caused this output.
            if(outputHistory.size()>=16)outputHistory.removeFirst();outputHistory.addLast(j);queuePublish();
        }catch(Throwable ignored){}
    }
    String outputStrategy(Object dpc){try{
        if(strategyController!=dpc){strategyController=dpc;strategyProbe=null;
            try{strategyProbe=dpc.getClass().getDeclaredMethod("getLastSelectedStrategy");strategyProbe.setAccessible(true);}catch(Throwable absent){}
        }
        if(strategyProbe==null)return null;Object strategy=strategyProbe.invoke(dpc);
        return strategy==null?null:strategy.getClass().getSimpleName();}catch(Throwable unavailable){return null;}}
    static JSONArray array(float[] numbers)throws JSONException{JSONArray out=new JSONArray();for(float n:numbers)out.put((double)n);return out;}
    static String processStart()throws IOException{try(BufferedReader reader=new BufferedReader(new FileReader("/proc/self/stat"))){String text=reader.readLine();return text.substring(text.lastIndexOf(')')+2).split(" +")[19];}}
    static void reportAttachFailure(Object owner,Throwable error){
        try{Context context=(Context)HookEntry.get(owner,"mContext");JSONObject status=new JSONObject().put("build",BUILD).put("phase","error").put("message","此固件曲线接口尚未兼容，保留官方行为："+error)
            .put("pid",android.os.Process.myPid()).put("process_start",processStart()).put("fingerprint",Build.FINGERPRINT);
            Settings.Global.putString(context.getContentResolver(),STATUS,status.toString());}catch(Throwable ignored){}
    }
}
