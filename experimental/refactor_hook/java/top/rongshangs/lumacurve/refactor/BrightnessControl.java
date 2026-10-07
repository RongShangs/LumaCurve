package top.rongshangs.lumacurve.refactor;

import android.content.*;
import android.database.ContentObserver;
import android.hardware.*;
import android.hardware.display.DisplayManager;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.*;

/** Event-driven mode owner inside system_server; raw output uses an optional native guard. */
final class BrightnessControl implements SensorEventListener {
    static final String REQUEST="hyperlux_brightness_request_v1",ACK="hyperlux_brightness_ack_v1",OWNER="hyperlux_brightness_owner_v1";
    final HookRuntime s;final DarkLockPolicy policy=new DarkLockPolicy();
    BrightnessControlOptions options=new BrightnessControlOptions();FrameworkBrightness display;SensorManager sensors;Sensor main,assist;
    boolean listening,registered,busy,failed;String owner="none",session="",reason="inactive",error="",lastRequest="";
    static final java.lang.reflect.Method SENSOR_HANDLE=sensorHandle();
    static java.lang.reflect.Method sensorHandle(){try{java.lang.reflect.Method m=Sensor.class.getDeclaredMethod("getHandle");m.setAccessible(true);return m;}catch(Throwable unavailable){return null;}}
    long mainSamples,assistSamples,unmatchedSamples;
    float mainLux=Float.NaN,assistLux=Float.NaN,held=Float.NaN,mainScale=1,assistScale=1;long mainAt=-1,assistAt=-1,ignoreBrightnessUntil,lastAutoEnabledAt=-1;
    String brightnessSnapshot="";
    final ContentObserver modeObserver,commandObserver,brightnessObserver;
    final Runnable timer,snapshot,retry;
    DisplayManager displayEvents;
    final DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener(){
        public void onDisplayAdded(int id){}
        public void onDisplayRemoved(int id){if(id==0)relinquish("main_display_removed",false);}
        public void onDisplayChanged(int id){if(id==0)displayChanged();}
    };
    boolean awake(){return s.screenOn()&&s.power!=null&&s.power.isInteractive();}
    void displayChanged(){if(!awake())screenOff();else screenOn();}
    boolean rawWasAuto,rawPaused,rawConfirmed,rawScreenOff;int rawTarget=-1;long rawLeaseUntil,rawBlockedWrites;final Runnable rawRenew=()->renewRaw();
    boolean unlocked(){try{android.app.KeyguardManager keyguard=(android.app.KeyguardManager)s.context.getSystemService(Context.KEYGUARD_SERVICE);return keyguard!=null&&!keyguard.isKeyguardLocked();}catch(Throwable missing){return false;}}
    boolean rawOutputBlocked(float value){return Float.isFinite(value)&&value>0&&enabled()&&owner.equals("raw_panel")&&rawConfirmed&&!rawPaused&&!rawScreenOff&&rawTarget>=10&&SystemClock.uptimeMillis()<rawLeaseUntil&&!auto()&&awake()&&unlocked();}
    void screenOn(){if(owner.equals("raw_panel")){rawScreenOff=false;renewRaw();}else tick();}
    BrightnessControl(HookRuntime state){
        s=state;timer=()->tick();snapshot=()->{try{
            if(owner.equals("dark")&&!auto()){float requested=(float)display.read().getDouble("value");if(Math.abs(requested-held)>.0005f){relinquish("user_brightness",false);return;}}
            brightnessSnapshot=brightnessValues();
        }catch(Throwable failure){fail(failure);}
        };retry=()->{if(s.closed)return;failed=false;tick();};
        modeObserver=new ContentObserver(s.handler){public void onChange(boolean self){try{if(busy)return;if(auto()){lastAutoEnabledAt=SystemClock.elapsedRealtime();if(!owner.equals("none"))relinquish("user_auto",false);else tick();}else tick();}catch(Throwable failure){fail(failure);}}};
        brightnessObserver=new ContentObserver(s.handler){public void onChange(boolean self){try{if(!busy&&owner.equals("dark")&&SystemClock.uptimeMillis()>ignoreBrightnessUntil&&!brightnessSnapshot.equals(brightnessValues()))relinquish("user_brightness",false);}catch(Throwable failure){fail(failure);}}};
        commandObserver=new ContentObserver(s.handler){public void onChange(boolean self){command();}};
    }
    boolean auto(){return Settings.System.getInt(s.context.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,0)==1;}
    boolean mainDisplay(){try{return ((Number)HookEntry.get(s.owner,"mDisplayId")).intValue()==0;}catch(Throwable unknown){return false;}}
    boolean supported(){
        try{
            if(display==null)display=new FrameworkBrightness(s.context);
            if(!mainDisplay())return false;
            return true;
        }catch(Throwable unknown){error=unknown.toString();return false;}
    }
    boolean available(){try{if(!supported())return false;display.read();return true;}catch(Throwable unknown){error=unknown.toString();return false;}}
    boolean bindSensors(){
        try{
            Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl"),abc=HookEntry.get(impl,"mAutomaticBrightnessController");
            Sensor selectedMain=(Sensor)HookEntry.get(abc,"mLightSensor");if(selectedMain==null||selectedMain.getType()!=Sensor.TYPE_LIGHT)return false;
            Object dual=null;try{dual=HookEntry.get(impl,"mDualSensorPolicy");}catch(Throwable optional){}
            float selectedMainScale=1,selectedAssistScale=1;Sensor selectedAssist=null;
            if(dual!=null){selectedAssist=(Sensor)HookEntry.get(dual,"mAssistLightSensor");selectedMainScale=((Number)HookEntry.get(dual,"mFovAmplifyFactor")).floatValue();selectedAssistScale=((Number)HookEntry.get(dual,"mAssistFovAmplifyFactor")).floatValue();
                if(!Float.isFinite(selectedMainScale)||!Float.isFinite(selectedAssistScale)||selectedMainScale<=0||selectedAssistScale<=0||selectedMainScale>100||selectedAssistScale>100)return false;}
            // Xiaomi's native auxiliary ALS uses vendor type 33171055; its consumer reads lux at values[0].
            if(selectedAssist!=null&&((selectedAssist.getType()!=Sensor.TYPE_LIGHT&&selectedAssist.getType()!=33171055)||sameSensor(selectedAssist,selectedMain)))return false;
            // SensorEvent.sensor belongs to its registering manager's handle map.
            // The display service uses a separately constructed SensorManager.
            SensorManager selectedManager=(SensorManager)HookEntry.get(abc,"mSensorManager");
            if(selectedManager==null)return false;
            if(listening&&(!sameSensor(main,selectedMain)||!sameSensor(assist,selectedAssist)||sensors!=selectedManager||mainScale!=selectedMainScale||assistScale!=selectedAssistScale))relinquish("sensor_changed",true);
            main=selectedMain;assist=selectedAssist;mainScale=selectedMainScale;assistScale=selectedAssistScale;
            sensors=selectedManager;return true;
        }catch(Throwable unknown){error=unknown.toString();return false;}
    }
    boolean enabled(){return !s.closed&&!failed&&s.phase.equals("active")&&s.appliesToUser();}
    boolean eligible(){
        if(!enabled()||!s.screenOn()||s.power==null||!s.power.isInteractive()||!Boolean.FALSE.equals(s.hdrActive()))return false;
        try{Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl");return Boolean.FALSE.equals(HookRuntime.optionalBoolean(impl,"mProximityPositive"));}catch(Throwable unknown){return false;}
    }
    boolean automaticReady(){
        try{Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl"),abc=HookEntry.get(impl,"mAutomaticBrightnessController");
            float accepted=((Number)HookEntry.get(abc,"mAmbientLux")).floatValue();
            return Boolean.TRUE.equals(HookRuntime.optionalBoolean(abc,"mAmbientLuxValid"))&&Boolean.TRUE.equals(HookRuntime.optionalBoolean(abc,"mLightSensorEnabled"))&&DarkLockPolicy.valid(accepted)&&accepted<=options.enterLux;
        }catch(Throwable unknown){return false;}
    }
    void start(){
        try{
            try{RawPanelLease.clear();}catch(Throwable optional){error=optional.toString();}
            s.context.getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),false,modeObserver);
            s.context.getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),false,brightnessObserver);
            s.context.getContentResolver().registerContentObserver(Settings.System.getUriFor("screen_brightness_float"),false,brightnessObserver);
            s.context.getContentResolver().registerContentObserver(Settings.Global.getUriFor(REQUEST),false,commandObserver);registered=true;
            displayEvents=(DisplayManager)s.context.getSystemService(Context.DISPLAY_SERVICE);
            if(displayEvents!=null)displayEvents.registerDisplayListener(displayListener,s.handler);
            // Restart recovery only restores an automatic mode that this feature owned.
            String raw=Settings.Global.getString(s.context.getContentResolver(),OWNER);
            if(raw!=null){JSONObject old=new JSONObject(raw);boolean ours=old.optString("owner").equals("dark")&&old.optInt("user",-1)==0&&old.optBoolean("was_auto")&&old.optString("session").matches("[A-Za-z0-9_-]{1,80}");
                if(ours&&HookEntry.currentUser.serial()==0&&!auto()){setAuto(true);s.log("已恢复暗光锁定前的自动亮度（系统进程重启）");}
                mark(null);
            }
        }catch(Throwable failure){fail(failure);}
    }
    void configure(BrightnessControlOptions next){
        if(!options.same(next))relinquish("settings_changed",true);
        options=next;failed=false;error="";tick();
    }
    void mode(boolean on)throws Exception{if(!Settings.System.putInt(s.context.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,on?1:0)||auto()!=on)throw new IllegalStateException("系统未确认自动亮度模式");}
    void setAuto(boolean on)throws Exception{boolean previous=busy;busy=true;try{mode(on);}finally{busy=previous;}}
    void mark(String value)throws Exception{if(!Settings.Global.putString(s.context.getContentResolver(),OWNER,value))throw new IllegalStateException("亮度控制状态保存失败");}
    void claim(String kind,String token,float value)throws Exception{
        boolean wasAuto=auto();if(kind.equals("dark")&&!wasAuto)throw new IllegalStateException("用户已关闭自动亮度");busy=true;try{
            // Persist intent before switching mode, so a process restart can recover dark locks.
            mark(new JSONObject().put("owner",kind).put("fingerprint",s.fingerprint).put("user",0).put("was_auto",wasAuto).put("session",token).toString());
            owner=kind;session=token;held=value;mode(false);held=display.write(value);
            ignoreBrightnessUntil=SystemClock.uptimeMillis()+2000;brightnessSnapshot=brightnessValues();s.handler.removeCallbacks(snapshot);s.handler.postDelayed(snapshot,2000);
        }catch(Throwable failure){if(wasAuto)try{mode(true);}catch(Throwable ignored){}owner="none";session="";try{mark(null);}catch(Throwable ignored){}throw failure;}
        finally{busy=false;}
    }
    String brightnessValues(){return Settings.System.getString(s.context.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS)+"|"+Settings.System.getString(s.context.getContentResolver(),"screen_brightness_float");}
    void relinquish(String why,boolean restore){
        String previous=owner;if(previous.equals("raw_panel"))stopRaw();owner="none";session="";held=Float.NaN;policy.reset();reason=why;s.handler.removeCallbacks(snapshot);s.handler.removeCallbacks(timer);s.handler.removeCallbacks(retry);unlisten();
        try{if(previous.equals("dark")&&restore&&!auto())setAuto(true);mark(null);}catch(Throwable failure){error=failure.toString();}
        if(previous.equals("raw_panel"))s.handler.postDelayed(()->{if(awake())s.requestRecalculation();},400);
        if(!previous.equals("none")){s.log(previous.equals("dark")?"暗光锁定退出："+why:"主屏手动面板已退让："+why);s.queueControlPublish();}
    }
    void screenOff(){if(owner.equals("raw_panel")){rawScreenOff=true;pauseRaw();}else relinquish("screen_off",true);unlisten();s.handler.removeCallbacks(timer);s.handler.removeCallbacks(retry);failed=false;}
    void stop(){relinquish("disabled",true);unlisten();s.handler.removeCallbacks(timer);s.handler.removeCallbacks(retry);options=new BrightnessControlOptions();}
    void close(){stop();if(displayEvents!=null)displayEvents.unregisterDisplayListener(displayListener);if(registered)try{s.context.getContentResolver().unregisterContentObserver(modeObserver);s.context.getContentResolver().unregisterContentObserver(commandObserver);s.context.getContentResolver().unregisterContentObserver(brightnessObserver);}catch(Throwable ignored){}registered=false;}
    void unlisten(){try{if(listening&&sensors!=null)sensors.unregisterListener(this);}catch(Throwable failure){error=failure.toString();}finally{listening=false;mainLux=assistLux=Float.NaN;mainAt=assistAt=-1;}}
    boolean listen(){
        if(!bindSensors())return false;if(listening)return true;
        try{if(!sensors.registerListener(this,main,1000000,s.handler))return false;
            if(assist!=null&&!sensors.registerListener(this,assist,1000000,s.handler)){sensors.unregisterListener(this);return false;}
            listening=true;mainAt=assistAt=-1;return true;
        }catch(Throwable failure){sensors.unregisterListener(this);error=failure.toString();return false;}
    }
    float usable(Sensor sensor,float lux,long time,long now){return sensor!=null&&DarkLockPolicy.valid(lux)&&time>=0&&(sensor.getReportingMode()==Sensor.REPORTING_MODE_ON_CHANGE||now-time<=10000)?lux:Float.NaN;}
    static boolean sameSensor(Sensor a,Sensor b){
        if(a==b)return true;if(a==null||b==null||a.getType()!=b.getType())return false;
        try{return SENSOR_HANDLE!=null&&((Number)SENSOR_HANDLE.invoke(a)).intValue()==((Number)SENSOR_HANDLE.invoke(b)).intValue();}catch(Throwable unavailable){return false;}
    }
    public void onSensorChanged(SensorEvent e){if(!listening||e==null||e.sensor==null||e.values==null||e.values.length==0)return;float v=e.values[0];long now=SystemClock.uptimeMillis();if(sameSensor(e.sensor,main)){mainLux=v*mainScale;mainAt=now;mainSamples++;}else if(sameSensor(e.sensor,assist)){assistLux=v*assistScale;assistAt=now;assistSamples++;}else{unmatchedSamples++;return;}tick();}
    public void onAccuracyChanged(Sensor sensor,int accuracy){}
    void tick(){
        if(busy||s.closed)return;s.handler.removeCallbacks(timer);
        try{
            if(owner.equals("raw_panel")){if(!awake())screenOff();return;}
            boolean ok=eligible();if(!options.darkLock||!ok||(owner.equals("panel")||owner.equals("raw_panel"))||!auto()&&!owner.equals("dark")){
                if(owner.equals("dark"))relinquish(ok?"mode_changed":"scene_changed",true);else policy.reset();
                unlisten();reason=(owner.equals("panel")||owner.equals("raw_panel"))?"manual_panel":!options.darkLock?"disabled":!ok?"scene":"user_manual";return;
            }
            if(!available()||!listen())throw new IllegalStateException("暗光锁定接口不可用");
            long now=SystemClock.uptimeMillis();float front=usable(main,mainLux,mainAt,now),back=usable(assist,assistLux,assistAt,now);
            // Both real sensors must agree on darkness. Either can prove a bright environment.
            int action=policy.step(options,policy.locked||automaticReady(),auto(),front,back,assist!=null,now);
            if(action==DarkLockPolicy.ENTER){
                JSONObject range=display.read();float current=(float)range.getDouble("actual");
                if(!Float.isFinite(current)||current<0||current>1)throw new IllegalStateException("当前主屏亮度不可用");
                claim("dark",java.util.UUID.randomUUID().toString(),current);reason="locked";s.log("暗光持续 "+options.minutes+" 分钟，保持当前主屏亮度");s.queuePublish();
            }else if(action==DarkLockPolicy.EXIT)relinquish("ambient_bright",true);
            else reason=owner.equals("dark")?"locked":!DarkLockPolicy.valid(front)||assist!=null&&!DarkLockPolicy.valid(back)?"waiting_sensor_data":policy.darkSince>=0?"countdown":"watching";
            // Slow health checks catch controller replacement and scene changes without a new lux event.
            // No wake lock: screen-off cancels the timer and both listeners.
            long next=policy.next(options),health=main.getReportingMode()!=Sensor.REPORTING_MODE_ON_CHANGE||assist!=null&&assist.getReportingMode()!=Sensor.REPORTING_MODE_ON_CHANGE?10000:30000;
            if(listening)s.handler.postDelayed(timer,next<0?health:Math.max(1,Math.min(health,next-now)));
        }catch(Throwable failure){fail(failure);}
    }
    void fail(Throwable failure){error=failure.toString();failed=true;relinquish("error",true);unlisten();s.handler.removeCallbacks(timer);s.handler.removeCallbacks(retry);if(options.darkLock&&!s.closed&&s.phase.equals("active")&&s.screenOn())s.handler.postDelayed(retry,30000);s.log("主屏亮度控制退出："+error);s.queuePublish();}
    JSONObject status()throws JSONException{
        JSONObject j=new JSONObject();options.put(j);boolean supported=supported();j.put("elapsed_ms",SystemClock.elapsedRealtime()).put("manual_panel_supported",mainDisplay()&&RawPanelOutputHooks.supports(s.owner.getClass())).put("dark_lock_supported",supported&&bindSensors()).put("owner",owner).put("reason",reason).put("error",error).put("auto",auto()).put("listening",listening).put("session",session).put("raw_target",rawTarget).put("raw_paused",rawPaused).put("raw_confirmed",rawConfirmed).put("raw_blocked_output_calls",rawBlockedWrites);
        if(DarkLockPolicy.valid(mainLux))j.put("watch_main_lux",mainLux);if(DarkLockPolicy.valid(assistLux))j.put("watch_assist_lux",assistLux);
        j.put("watch_main_samples",mainSamples).put("watch_assist_samples",assistSamples).put("watch_unmatched_samples",unmatchedSamples).put("watch_main_age_ms",mainAt<0?-1:SystemClock.uptimeMillis()-mainAt).put("watch_assist_age_ms",assistAt<0?-1:SystemClock.uptimeMillis()-assistAt);
        if(main!=null)j.put("watch_main_sensor",main.getName()).put("watch_main_type",main.getType());if(assist!=null)j.put("watch_assist_sensor",assist.getName()).put("watch_assist_type",assist.getType());
        j.put("countdown_left_ms",policy.darkSince<0?0:Math.max(0,policy.darkSince+options.minutes*60000L-SystemClock.uptimeMillis()));
        if(Float.isFinite(held))j.put("held",held);try{if(supported)j.put("range",display.read());}catch(Throwable ignored){}return j;
    }
    void stopRaw(){
        s.handler.removeCallbacks(rawRenew);
        rawTarget=-1;rawConfirmed=false;rawPaused=false;rawScreenOff=false;rawLeaseUntil=0;
        try{RawPanelLease.clear();}catch(Throwable failure){error=failure.toString();}
    }
    void writeRawLease(boolean active)throws Exception{
        rawLeaseUntil=SystemClock.uptimeMillis()+8000;
        RawPanelLease.write(session,android.os.Process.myPid(),rawLeaseUntil,active?1:0,rawTarget);
    }
    void pauseRaw(){
        if(!owner.equals("raw_panel"))return;
        try{boolean changed=!rawPaused;rawPaused=true;reason="raw_screen_paused";writeRawLease(false);s.handler.removeCallbacks(rawRenew);s.handler.postDelayed(rawRenew,2000);if(changed){s.log("主屏节点已释放，保留手动目标等待解锁");s.queueControlPublish();}}catch(Throwable failure){fail(failure);}
    }
    void renewRaw(){
        if(!owner.equals("raw_panel"))return;
        s.handler.removeCallbacks(rawRenew);
        try{
            if(!enabled()||auto()){relinquish("raw_mode_changed",false);return;}
            if(!RawPanelLease.healthy(session))throw new IllegalStateException("主屏节点守护已退出");
            boolean paused=rawScreenOff||!awake()||!unlocked(),changed=rawPaused!=paused;
            rawPaused=paused;reason=paused?"raw_screen_paused":"manual_panel";writeRawLease(!paused);
            s.handler.postDelayed(rawRenew,2000);
            if(changed){s.log(paused?"主屏节点已释放，保留手动目标等待解锁":"解锁后恢复主屏手动目标："+rawTarget);s.queueControlPublish();}
        }catch(Throwable failure){boolean restore=rawWasAuto;relinquish("raw_guard_error",false);if(restore)try{setAuto(true);}catch(Throwable ignored){}s.log("主屏节点守护续约失败，已退让");s.queuePublish();}
    }
    void armRaw(String token,int target)throws Exception{
        if(!options.manualPanel)throw new IllegalStateException("主屏手动面板已关闭");
        if(!RawPanelOutputHooks.supports(s.owner.getClass()))throw new IllegalStateException("当前系统尚未接入主屏输出隔离");
        // A dark lock paused automatic mode on the user's behalf. Preserve that
        // ownership before relinquishing it, so a failed raw transaction can recover.
        boolean wasAuto=owner.equals("raw_panel")?rawWasAuto:owner.equals("dark")||auto();
        if(!owner.equals("raw_panel"))relinquish("raw_panel",false);
        busy=true;
        try{
            mark(new JSONObject().put("owner","raw_panel").put("user",0).put("was_auto",wasAuto).put("session",token).toString());
            owner="raw_panel";session=token;rawWasAuto=wasAuto;held=Float.NaN;reason="manual_panel";
            rawTarget=target;rawPaused=false;rawScreenOff=false;rawConfirmed=false;mode(false);writeRawLease(true);
            s.handler.removeCallbacks(rawRenew);s.handler.postDelayed(rawRenew,2000);
        }catch(Throwable failure){
            stopRaw();owner="none";session="";try{mark(null);}catch(Throwable ignored){}
            if(wasAuto)try{mode(true);}catch(Throwable ignored){}throw failure;
        }finally{busy=false;}
    }
    void command(){
        String id="";
        try{
            String raw=Settings.Global.getString(s.context.getContentResolver(),REQUEST);if(raw==null||raw.length()>2048)return;
            JSONObject request=new JSONObject(raw);id=request.getString("id");if(id.equals(lastRequest))return;lastRequest=id;
            if(!id.matches("[A-Za-z0-9_-]{1,80}")||!request.getString("build").equals(HookRuntime.BUILD)||!request.getString("process_start").equals(HookRuntime.processStart())||Math.abs(SystemClock.elapsedRealtime()-request.getLong("elapsed"))>15000)throw new IllegalArgumentException("亮度请求已过期，请重新打开面板");
            String action=request.getString("action"),token=request.getString("session");
            if(!enabled()||!awake()||!mainDisplay())throw new IllegalStateException("请先启用引擎并保持主屏亮起");
            // Raw control only needs main-display ownership and mode acknowledgement.
            // HBM's float range and temporarily missing BrightnessInfo are irrelevant to sysfs.
            if(!action.startsWith("raw_")&&!available())throw new IllegalStateException("主屏框架亮度接口尚未就绪");
            if(!token.matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("面板会话无效");
            if(action.equals("raw_begin")){
                long gesture=request.getLong("gesture_elapsed"),age=SystemClock.elapsedRealtime()-gesture;
                if(age<0||age>3000||lastAutoEnabledAt>gesture)throw new IllegalStateException("系统模式已改变，请再次滑动");
                int target=request.getInt("value");if(request.getDouble("value")!=target||target<10||target>65535||!unlocked())throw new IllegalStateException("请解锁并选择有效的主屏节点值");armRaw(token,target);
            }else if(action.equals("raw_validate")||action.equals("raw_ready")){
                if(!owner.equals("raw_panel")||!session.equals(token)||auto())throw new IllegalStateException("自动亮度已开启或会话已失效，停止节点写入");
                if(!unlocked()||rawScreenOff)throw new IllegalStateException("锁屏期间停止节点写入");
                int target=request.getInt("value");if(request.getDouble("value")!=target||target<10||target>65535)throw new IllegalArgumentException("主屏节点值无效");
                if(action.equals("raw_ready")){if(!RawPanelLease.healthy(session))throw new IllegalStateException("主屏节点守护未确认接管");rawConfirmed=true;}
                rawTarget=target;writeRawLease(true);
            }else if(action.equals("raw_abort")){
                if(owner.equals("raw_panel")&&session.equals(token)){boolean restore=rawWasAuto;relinquish("raw_write_error",false);if(restore)setAuto(true);}
            }else if(action.equals("raw_restore")){
                relinquish("user_auto",false);setAuto(true);
            }else if(action.equals("end")){if(owner.equals("panel")&&session.equals(token))relinquish("panel_closed",false);}
            else if(action.equals("restore")){relinquish("panel_auto",true);setAuto(true);unlisten();}
            else{
                if(!options.manualPanel)throw new IllegalStateException("主屏手动面板已关闭");
                float value=(float)request.getDouble("value");if(!Float.isFinite(value)||value<0||value>1)throw new IllegalArgumentException("亮度值无效");
                if(action.equals("begin")){long gesture=request.getLong("gesture_elapsed"),age=SystemClock.elapsedRealtime()-gesture;if(age<0||age>3000||lastAutoEnabledAt>gesture)throw new IllegalStateException("系统模式已改变或操作已过期，请再次滑动");relinquish("panel_drag",false);unlisten();claim("panel",token,value);s.log("主屏手动面板：自动亮度已关闭");}
                else if(action.equals("set")){if(!owner.equals("panel")||!session.equals(token)||auto())throw new IllegalStateException("自动亮度已开启，手动面板停止写入");held=display.write(value);}
                else throw new IllegalArgumentException("未知亮度请求");
            }
            JSONObject ack=new JSONObject().put("id",id).put("ok",true).put("state",status());Settings.Global.putString(s.context.getContentResolver(),ACK,ack.toString());
            if(action.equals("raw_validate")||action.equals("set"))s.queuePublish();else s.queueControlPublish();
        }catch(Throwable failure){try{Settings.Global.putString(s.context.getContentResolver(),ACK,new JSONObject().put("id",id).put("ok",false).put("message",String.valueOf(failure.getMessage())).toString());}catch(Throwable ignored){}s.queuePublish();}
    }
}
