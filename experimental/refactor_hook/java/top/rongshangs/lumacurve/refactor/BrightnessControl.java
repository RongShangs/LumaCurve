package top.rongshangs.lumacurve.refactor;

import android.content.*;
import android.database.ContentObserver;
import android.hardware.*;
import android.hardware.display.DisplayManager;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.*;
import java.util.*;

/** Event-driven mode owner inside system_server; raw output uses an optional native guard. */
final class BrightnessControl implements SensorEventListener {
    static final String REQUEST="hyperlux_brightness_request_v1",ACK="hyperlux_brightness_ack_v1",OWNER="hyperlux_brightness_owner_v1";
    final HookRuntime s;final DarkLockPolicy policy=new DarkLockPolicy();final DarkLockSchedule schedule;
    BrightnessControlOptions options=new BrightnessControlOptions();FrameworkBrightness display;SensorManager sensors;Sensor main,assist;
    boolean listening,registered,busy,failed;String owner="none",session="",reason="inactive",error="",lastRequest="";
    BrightnessSensorAccess.Binding sensorBinding;SensorEventListener sensorListener;
    long mainSamples,assistSamples,unmatchedSamples,sensorEvaluations;
    boolean sensorTickQueued;
    final Runnable sensorTick=()->runSensorTick();
    void runSensorTick(){sensorTickQueued=false;if(listening&&!s.closed){sensorEvaluations++;tick();}}
    void queueSensorTick(){
        if(sensorTickQueued||screenCleanupQueued||s.closed)return;
        sensorTickQueued=true;if(!s.handler.post(sensorTick))sensorTickQueued=false;
    }
    void sensorChanged(float value){
        // Never coalesce away invalid data or an interruption of a confirmation window.
        boolean interrupted=!DarkLockPolicy.valid(value)||!policy.locked&&policy.darkSince>=0&&value>options.enterLux
            ||policy.locked&&policy.brightSince>=0&&mainLux<options.exitLux&&(assist==null||assistLux<options.exitLux);
        if(interrupted){s.handler.removeCallbacks(sensorTick);sensorTickQueued=false;sensorEvaluations++;tick();}
        else queueSensorTick();
    }
    float mainLux=Float.NaN,assistLux=Float.NaN,held=Float.NaN,mainScale=1,assistScale=1;long mainAt=-1,assistAt=-1,ignoreBrightnessUntil,lastAutoEnabledAt=-1;
    String brightnessSnapshot="";
    volatile ForegroundApps.Snapshot foreground=ForegroundApps.Snapshot.unknown();
    String appSuppressed="";boolean foregroundQueued,foregroundPending,foregroundQuerying;long foregroundGeneration,foregroundQueryGeneration;
    final Set<String> appExemptions=new LinkedHashSet<>();boolean appSuppressionPending;
    ForegroundApps.Snapshot foregroundHint=ForegroundApps.Snapshot.unknown();
    final Runnable foregroundUpdate=this::runForegroundUpdate;
    final Runnable foregroundQuery=this::queryForeground;
    Runnable foregroundTimeout;
    void runForegroundUpdate(){synchronized(this){foregroundQueued=false;}queryForeground();}
    void queryForeground(){
        final long generation;synchronized(this){
            if(s.closed||options.manualApps.isEmpty()||foregroundQuerying)return;
            generation=foregroundGeneration;foregroundQueryGeneration=generation;foregroundQuerying=true;
            foregroundTimeout=()->timeoutForegroundQuery(generation);s.handler.postDelayed(foregroundTimeout,3000);
        }
        try{ForegroundApps.query(this,generation);}catch(Throwable rejected){finishForegroundQuery(generation,ForegroundApps.Snapshot.unknown(),"前台应用读取失败："+rejected);}
    }
    void finishForegroundQuery(long generation,ForegroundApps.Snapshot value,String detail){
        boolean accepted=false;synchronized(this){
            if(!foregroundQuerying||generation!=foregroundQueryGeneration)return;
            if(foregroundTimeout!=null)s.handler.removeCallbacks(foregroundTimeout);foregroundTimeout=null;foregroundQuerying=false;
            // A WM event or detach received during the query is newer evidence.
            if(generation==foregroundGeneration&&!s.closed&&!options.manualApps.isEmpty()){acceptForeground(value);if(!detail.isEmpty())error=detail;else if(error.startsWith("前台应用读取"))error="";accepted=true;}
            else if(foregroundPending&&!s.closed&&!options.manualApps.isEmpty())queueForeground();
        }
        if(accepted&&!s.closed)tick();
    }
    void timeoutForegroundQuery(long generation){
        synchronized(this){if(!foregroundQuerying||generation!=foregroundQueryGeneration)return;foregroundQuerying=false;foregroundTimeout=null;foregroundGeneration++;acceptForeground(ForegroundApps.Snapshot.unknown());error="前台应用读取超时，已释放应用接管";}
        if(!s.closed)tick();
    }
    void acceptForeground(ForegroundApps.Snapshot value){
        foreground=value;foregroundHint=value;foregroundPending=false;s.handler.removeCallbacks(foregroundUpdate);foregroundQueued=false;
        if(appSuppressionPending&&(value.visibilityKnown||!value.pkg.isEmpty())){for(String pkg:options.manualApps)if(value.contains(pkg))appExemptions.add(pkg);appSuppressionPending=false;syncApplicationSuppression();}
    }
    void syncApplicationSuppression(){appSuppressed=appExemptions.isEmpty()?"":appExemptions.iterator().next();}
    void suppressApplication(){
        for(String pkg:options.manualApps)if(foreground.contains(pkg))appExemptions.add(pkg);
        if(!options.manualApps.isEmpty()&&(foregroundPending||foregroundQuerying||!foreground.visibilityKnown&&foreground.pkg.isEmpty()))appSuppressionPending=true;
        syncApplicationSuppression();
    }
    synchronized void enqueueForeground(ForegroundApps.Snapshot value){
        foregroundGeneration++;
        ForegroundApps.Snapshot previous=foregroundHint;foregroundHint=value;
        if(previous.pkg.equals(value.pkg)&&previous.user==value.user&&previous.display==value.display&&!foregroundPending&&!foregroundQuerying)return;
        queueForeground();
    }
    synchronized void invalidateForeground(){foregroundGeneration++;queueForeground();}
    void queueForeground(){if(s.closed)return;foregroundPending=true;if(foregroundQueued)return;foregroundQueued=true;if(!s.handler.postDelayed(foregroundUpdate,150))foregroundQueued=foregroundPending=false;}
    synchronized void cancelForeground(){foregroundGeneration++;s.handler.removeCallbacks(foregroundUpdate);s.handler.removeCallbacks(foregroundQuery);if(foregroundTimeout!=null)s.handler.removeCallbacks(foregroundTimeout);foregroundTimeout=null;foregroundQuerying=false;foregroundQueued=foregroundPending=false;foreground=foregroundHint=ForegroundApps.Snapshot.unknown();}
    boolean applicationMode()throws Exception {
        ForegroundApps.Snapshot top=foreground;
        boolean eligible=!options.manualApps.isEmpty()&&enabled()&&mainDisplay()&&awake()&&unlocked();
        // Retain a current session while a coalesced visibility refresh is pending.
        // Screen, user and keyguard checks still release it immediately.
        if(eligible&&foregroundPending)return owner.equals("app");
        String selected=top.selected(options.manualApps);boolean match=eligible&&!selected.isEmpty();
        for(Iterator<String> i=appExemptions.iterator();i.hasNext();){String pkg=i.next();if(!options.manualApps.contains(pkg)||(top.visibilityKnown||!top.pkg.isEmpty())&&!top.contains(pkg))i.remove();}
        syncApplicationSuppression();
        if(!match){if(owner.equals("app"))relinquish("app_left",true);return false;}
        if(owner.equals("app"))return true;
        if(owner.equals("panel")||owner.equals("raw_panel")||!appSuppressed.isEmpty())return false;
        if(owner.equals("dark"))relinquish("app_entered",true);
        if(!auto())return false; // Never own/restore an automatic mode the user had disabled.
        if(!available())return false;
        float value=(float)display.read().getDouble("actual");if(!Float.isFinite(value)||value<0||value>1)return false;
        claim("app",java.util.UUID.randomUUID().toString(),value);reason="app_manual";s.log("应用暂停自动亮度："+selected);s.queueControlPublish();return true;
    }
    final Runnable manualUpdate=()->acceptManualBrightness();
    void acceptManualBrightness(){
        if(s.closed||!owner.equals("dark"))return;
        try{if(auto())return;if(options.manualExit){relinquish("user_brightness",false);return;}
            float value=(float)display.read().getDouble("value");
            if(!Float.isFinite(value)||value<0||value>1)throw new IllegalStateException("手动亮度读数无效");
            held=value;brightnessSnapshot=brightnessValues();policy.brightSince=-1;reason="locked";
            tick();s.queueControlPublish();
        }catch(Throwable failure){fail(failure);}
    }
    void manualBrightnessChanged(){
        if(options.manualExit){relinquish("user_brightness",false);return;}
        s.handler.removeCallbacks(manualUpdate);s.handler.postDelayed(manualUpdate,150);
    }
    final ContentObserver modeObserver,commandObserver,brightnessObserver;
    final Runnable timer,snapshot,retry;
    DisplayManager displayEvents;
    final DisplayManager.DisplayListener displayListener=new DisplayManager.DisplayListener(){
        public void onDisplayAdded(int id){}
        public void onDisplayRemoved(int id){if(id==0)relinquish("main_display_removed",false);}
        public void onDisplayChanged(int id){if(id==0)displayChanged();}
    };
    boolean awake(){return !screenCleanupQueued&&s.screenOn()&&s.power!=null&&s.power.isInteractive();}
    boolean screenCleanupQueued;
    final Runnable screenCleanup=()->finishScreenCleanup();
    void finishScreenCleanup(){screenCleanupQueued=false;if(!s.closed)screenOff();}
    // The OEM screen-state hook must not call providers or services while its locks are held.
    void screenChanging(){
        rawScreenOff=true;
        if(s.closed||screenCleanupQueued)return;
        screenCleanupQueued=true;
        if(!s.handler.post(screenCleanup))screenCleanupQueued=false;
    }
    void displayChanged(){if(!awake())screenOff();else screenOn();}
    String rawPhysicalId="";boolean rawWasAuto,rawPaused,rawConfirmed,rawScreenOff;int rawTarget=-1;long rawLeaseUntil,rawBlockedWrites;final Runnable rawRenew=()->renewRaw();
    boolean unlocked(){try{android.app.KeyguardManager keyguard=(android.app.KeyguardManager)s.context.getSystemService(Context.KEYGUARD_SERVICE);return keyguard!=null&&!keyguard.isKeyguardLocked();}catch(Throwable missing){return false;}}
    boolean rawIdentityCurrent(){if(rawPhysicalId.isEmpty())return true;try{return rawPhysicalId.equals(HookEntry.get(HookEntry.get(s.owner,"mDisplayPowerController"),"mUniqueDisplayId"));}catch(Throwable missing){return false;}}
    boolean rawOutputBlocked(float value){return rawIdentityCurrent()&&owner.equals("raw_panel")&&rawConfirmed&&(!session.startsWith("cc_")||s.thermalSeverity>=0&&s.thermalSeverity<3)&&!rawPaused&&!rawScreenOff&&Float.isFinite(value)&&value>0&&rawTarget>=10&&SystemClock.uptimeMillis()<rawLeaseUntil&&enabled()&&!auto()&&awake()&&unlocked();}
    void screenOn(){if(screenCleanupQueued){s.handler.removeCallbacks(screenCleanup);screenCleanup.run();}if(!registered&&!s.closed)start();if(!options.manualApps.isEmpty())ForegroundApps.attach(this);if(owner.equals("raw_panel")){rawScreenOff=false;renewRaw();}else tick();}
    BrightnessControl(HookRuntime state){
        s=state;schedule=new DarkLockSchedule(s.owner.getClass().getClassLoader(),s.handler,()->{if(!s.closed){tick();s.queueControlPublish();}});timer=()->tick();snapshot=()->{try{
            if(owner.equals("dark")&&!auto()){float requested=(float)display.read().getDouble("value");if(Math.abs(requested-held)>.0005f){manualBrightnessChanged();return;}}
            brightnessSnapshot=brightnessValues();
        }catch(Throwable failure){fail(failure);}
        };retry=()->{if(s.closed)return;if(!registered){start();if(!registered)return;}failed=false;tick();};
        modeObserver=new ContentObserver(s.handler){public void onChange(boolean self){try{if(s.closed)return;boolean automatic=auto();s.memorySettingChanged(automatic);if(busy)return;if(automatic){lastAutoEnabledAt=SystemClock.elapsedRealtime();suppressApplication();if(!owner.equals("none"))relinquish("user_auto",false);else tick();}else tick();}catch(Throwable failure){fail(failure);}}};
        brightnessObserver=new ContentObserver(s.handler){public void onChange(boolean self){try{if(!s.closed&&!busy&&owner.equals("dark")&&SystemClock.uptimeMillis()>ignoreBrightnessUntil&&!brightnessSnapshot.equals(brightnessValues()))manualBrightnessChanged();}catch(Throwable failure){fail(failure);}}};
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
            BrightnessSensorAccess.Binding selected=BrightnessSensorAccess.read(s.owner);
            if(listening&&!selected.same(sensorBinding))relinquish("sensor_changed",true);
            main=selected.main;assist=selected.assist;mainScale=selected.mainScale;assistScale=selected.assistScale;
            sensors=selected.manager;sensorBinding=selected;return true;
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
            if(raw!=null){JSONObject old=new JSONObject(raw);boolean validSession=old.optString("session").matches("[A-Za-z0-9_-]{1,80}");boolean ours=(old.optString("owner").equals("dark")||old.optString("owner").equals("app"))&&old.optInt("user",-1)==0&&old.optBoolean("was_auto")&&validSession;
                boolean rawWasAuto=old.optString("owner").equals("raw_panel")&&old.optInt("user",-1)==0&&old.optBoolean("was_auto")&&validSession;
                if((ours||rawWasAuto)&&HookEntry.currentUser.serial()==0&&!auto()){setAuto(true);s.log(ours?"已恢复模块接管前的自动亮度（系统进程重启）":"已恢复手动接管前的自动亮度（系统进程重启；未恢复节点目标）");}
                mark(null);
            }
        }catch(Throwable failure){unregisterObservers();fail(failure);}
    }
    void configure(BrightnessControlOptions next){
        if(!options.sameOwnership(next))relinquish("settings_changed",true);
        if(owner.equals("raw_panel")&&!session.startsWith("cc_")&&next.manualMaximum>0&&rawTarget>next.manualMaximum)relinquish("manual_limit_changed",false);
        if(next.manualApps.isEmpty()){appExemptions.clear();appSuppressionPending=false;syncApplicationSuppression();}
        options=next;failed=false;error="";if(!registered)start();try{s.memoryModeKnown(auto());}catch(Throwable unavailable){s.log("记忆保持等待模式状态就绪");}tick();
        if(options.manualApps.isEmpty())ForegroundApps.detach(this);else ForegroundApps.attach(this);
    }
    void mode(boolean on)throws Exception{if(!Settings.System.putInt(s.context.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,on?1:0)||auto()!=on)throw new IllegalStateException("系统未确认自动亮度模式");}
    void setAuto(boolean on)throws Exception{boolean previous=busy;busy=true;try{mode(on);}finally{busy=previous;}}
    void mark(String value)throws Exception{if(!Settings.Global.putString(s.context.getContentResolver(),OWNER,value))throw new IllegalStateException("亮度控制状态保存失败");}
    void claim(String kind,String token,float value)throws Exception{
        s.outdoorNode.release();boolean wasAuto=auto();if((kind.equals("dark")||kind.equals("app"))&&!wasAuto)throw new IllegalStateException("用户已关闭自动亮度");busy=true;try{
            // Persist intent before switching mode, so a process restart can recover dark locks.
            mark(new JSONObject().put("owner",kind).put("fingerprint",s.fingerprint).put("user",0).put("was_auto",wasAuto).put("session",token).toString());
            owner=kind;session=token;held=value;mode(false);held=display.write(value);
            ignoreBrightnessUntil=SystemClock.uptimeMillis()+2000;brightnessSnapshot=brightnessValues();s.handler.removeCallbacks(snapshot);if(kind.equals("dark"))s.handler.postDelayed(snapshot,2000);
        }catch(Throwable failure){if(wasAuto)try{mode(true);}catch(Throwable ignored){}owner="none";session="";try{mark(null);}catch(Throwable ignored){}throw failure;}
        finally{busy=false;}
    }
    String brightnessValues(){return Settings.System.getString(s.context.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS)+"|"+Settings.System.getString(s.context.getContentResolver(),"screen_brightness_float");}
    void relinquish(String why,boolean restore){
        String previous=owner;boolean restoreAuto=restore&&(previous.equals("dark")||previous.equals("app")||previous.equals("raw_panel")&&rawWasAuto);if(previous.equals("raw_panel"))stopRaw();owner="none";session="";held=Float.NaN;policy.reset();reason=why;s.handler.removeCallbacks(snapshot);s.handler.removeCallbacks(manualUpdate);s.handler.removeCallbacks(timer);s.handler.removeCallbacks(retry);unlisten();
        try{if(restoreAuto&&!auto())setAuto(true);if(!previous.equals("none"))mark(null);}catch(Throwable failure){error=failure.toString();}
        if(previous.equals("raw_panel"))s.handler.postDelayed(()->{if(awake())s.requestRecalculation();},400);
        if(!previous.equals("none")){s.log(previous.equals("dark")?"暗光锁定退出："+why:previous.equals("app")?"应用亮度接管结束："+why:"主屏手动面板已退让："+why);s.queueControlPublish();}
    }
    void screenOff(){schedule.stop();TemporaryScreenOn.clear();ForegroundApps.detach(this);s.handler.removeCallbacks(screenCleanup);screenCleanupQueued=false;if(owner.equals("raw_panel")){rawScreenOff=true;pauseRaw();}else relinquish("screen_off",true);unlisten();s.handler.removeCallbacks(timer);s.handler.removeCallbacks(retry);failed=false;}
    void stop(){schedule.stop();TemporaryScreenOn.clear();ForegroundApps.detach(this);appExemptions.clear();appSuppressionPending=false;syncApplicationSuppression();s.handler.removeCallbacks(screenCleanup);screenCleanupQueued=false;relinquish("disabled",true);unlisten();s.handler.removeCallbacks(timer);s.handler.removeCallbacks(retry);options=new BrightnessControlOptions();}
    void close(){stop();unregisterObservers();}
    void unregisterObservers(){
        try{if(displayEvents!=null)displayEvents.unregisterDisplayListener(displayListener);}catch(Throwable failure){error=failure.toString();}
        for(ContentObserver observer:new ContentObserver[]{modeObserver,commandObserver,brightnessObserver})try{s.context.getContentResolver().unregisterContentObserver(observer);}catch(Throwable failure){error=failure.toString();}
        registered=false;
    }
    void unlisten(){
        s.handler.removeCallbacks(sensorTick);sensorTickQueued=false;SensorEventListener old=sensorListener;sensorListener=null;
        try{if(old!=null&&sensors!=null)sensors.unregisterListener(old);}catch(Throwable failure){error=failure.toString();}
        finally{listening=false;mainLux=assistLux=Float.NaN;mainAt=assistAt=-1;}
    }
    boolean listen(){
        if(!bindSensors())return false;if(listening)return true;
        SensorEventListener next=new SensorEventListener(){
            public void onSensorChanged(SensorEvent e){if(sensorListener==this)BrightnessControl.this.onSensorChanged(e);}
            public void onAccuracyChanged(Sensor sensor,int accuracy){}
        };
        sensorListener=next;
        try{
            if(!sensors.registerListener(next,main,1000000,s.handler))throw new IllegalStateException("Main sensor registration rejected");
            if(assist!=null&&!sensors.registerListener(next,assist,1000000,s.handler))throw new IllegalStateException("Auxiliary sensor registration rejected");
            listening=true;mainAt=assistAt=-1;return true;
        }catch(Throwable failure){unlisten();error=failure.toString();return false;}
    }
    float usable(Sensor sensor,float lux,long time,long now){return sensor!=null&&DarkLockPolicy.valid(lux)&&time>=0&&(sensor.getReportingMode()==Sensor.REPORTING_MODE_ON_CHANGE||now-time<=10000)?lux:Float.NaN;}
    static boolean sameSensor(Sensor a,Sensor b){return BrightnessSensorAccess.same(a,b);}
    public void onSensorChanged(SensorEvent e){if(!listening||e==null||e.sensor==null||e.values==null||e.values.length==0)return;float v=e.values[0];long now=SystemClock.uptimeMillis();if(sameSensor(e.sensor,main)){try{v=BrightnessSensorAccess.mainValue(sensorBinding,e);}catch(Throwable invalid){fail(invalid);return;}mainLux=v;mainAt=now;mainSamples++;}else if(sameSensor(e.sensor,assist)){v*=assistScale;assistLux=v;assistAt=now;assistSamples++;}else{unmatchedSamples++;return;}sensorChanged(v);}
    public void onAccuracyChanged(Sensor sensor,int accuracy){}
    void tick(){
        if(screenCleanupQueued)return;
        if(busy||s.closed)return;s.handler.removeCallbacks(timer);
        try{
            schedule.enabled(options.darkLock&&options.sunsetOnly&&enabled()&&awake()&&!owner.equals("raw_panel")&&!owner.equals("panel"));
            if(owner.equals("raw_panel")){if(!awake())screenOff();return;}
            if(applicationMode()){policy.reset();unlisten();reason="app_manual";return;}
            if(!options.darkLock&&!owner.equals("dark")){policy.reset();unlisten();reason=owner.equals("panel")?"manual_panel":"disabled";return;}
            boolean ok=eligible();if(!options.darkLock||!ok||(owner.equals("panel")||owner.equals("raw_panel"))||!auto()&&!owner.equals("dark")){
                if(owner.equals("dark"))relinquish(ok?"mode_changed":"scene_changed",true);else policy.reset();
                unlisten();reason=(owner.equals("panel")||owner.equals("raw_panel"))?"manual_panel":!options.darkLock?"disabled":!ok?"scene":"user_manual";return;
            }
            if(options.sunsetOnly&&!schedule.allowed()){
                if(owner.equals("dark"))relinquish("night_window_ended",true);else policy.reset();
                unlisten();reason=schedule.window.known?"before_sunset":"waiting_twilight";return;
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
    void fail(Throwable failure){error=failure.toString();failed=true;relinquish("error",true);unlisten();s.handler.removeCallbacks(timer);s.handler.removeCallbacks(retry);if((options.darkLock||!options.manualApps.isEmpty()||options.manualPanel&&!registered)&&!s.closed&&s.phase.equals("active")&&s.screenOn())s.handler.postDelayed(retry,30000);s.log("主屏亮度控制退出："+error);s.queuePublish();}
    boolean sensorsReady(){try{BrightnessSensorAccess.read(s.owner);return true;}catch(Throwable unavailable){return false;}}
    JSONObject status()throws JSONException{
        JSONObject j=new JSONObject();options.put(j);boolean supported=supported();j.put("elapsed_ms",SystemClock.elapsedRealtime()).put("manual_panel_supported",mainDisplay()&&RawPanelOutputHooks.supports(s.owner.getClass())).put("dark_lock_supported",supported&&sensorsReady()).put("owner",owner).put("reason",reason).put("error",error).put("auto",auto()).put("listening",listening).put("session",session).put("raw_physical_display_id",rawPhysicalId).put("raw_target",rawTarget).put("raw_paused",rawPaused).put("raw_confirmed",rawConfirmed).put("raw_blocked_output_calls",rawBlockedWrites);
        j.put("dark_lock_schedule",schedule.status());
        j.put("watch_binding_active",listening).put("watch_main_profile",listening&&sensorBinding!=null?sensorBinding.profile:"").put("watch_physical_display_id",listening&&sensorBinding!=null?sensorBinding.physicalId:"");
        j.put("app_auto_supported",ForegroundApps.supported()).put("foreground_package",foreground.pkg).put("foreground_auto_suppressed",!appSuppressed.isEmpty());
        j.put("app_multiwindow_supported",ForegroundApps.multiWindowSupported).put("foreground_visibility_known",foreground.visibilityKnown).put("foreground_visible_packages",new JSONArray(foreground.visible)).put("foreground_matched_package",foreground.selected(options.manualApps)).put("foreground_visibility_pending",foregroundPending);
        j.put("foreground_auto_exemptions",new JSONArray(appExemptions)).put("foreground_auto_override_pending",appSuppressionPending);
        if(!ForegroundApps.error.isEmpty())j.put("app_auto_detail",ForegroundApps.error);
        if(DarkLockPolicy.valid(mainLux))j.put("watch_main_lux",mainLux);if(DarkLockPolicy.valid(assistLux))j.put("watch_assist_lux",assistLux);
        j.put("watch_main_samples",mainSamples).put("watch_assist_samples",assistSamples).put("watch_unmatched_samples",unmatchedSamples).put("watch_evaluations",sensorEvaluations).put("watch_requested_period_us",1000000).put("watch_main_age_ms",mainAt<0?-1:SystemClock.uptimeMillis()-mainAt).put("watch_assist_age_ms",assistAt<0?-1:SystemClock.uptimeMillis()-assistAt);
        if(main!=null)j.put("watch_main_sensor",main.getName()).put("watch_main_type",main.getType());if(assist!=null)j.put("watch_assist_sensor",assist.getName()).put("watch_assist_type",assist.getType());
        j.put("countdown_left_ms",policy.darkSince<0?0:Math.max(0,policy.darkSince+options.minutes*60000L-SystemClock.uptimeMillis()));
        j.put("temporary_screen_on",TemporaryScreenOn.active());if(Float.isFinite(held))j.put("held",held);try{if(supported)j.put("range",display.read());}catch(Throwable ignored){}return j;
    }
    void stopRaw(){
        s.handler.removeCallbacks(rawRenew);
        rawTarget=-1;rawPhysicalId="";rawConfirmed=false;rawPaused=false;rawScreenOff=false;rawLeaseUntil=0;
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
            if(!rawIdentityCurrent()){relinquish("fold_display_changed",true);return;}
            if(!enabled()||auto()){relinquish("raw_mode_changed",false);return;}
            if(session.startsWith("cc_")&&(s.thermalSeverity<0||s.thermalSeverity>=3)){relinquish("raw_protection",false);return;}
            if(!RawPanelLease.healthy(session))throw new IllegalStateException("主屏节点守护已退出");
            boolean paused=rawScreenOff||!awake()||!unlocked(),changed=rawPaused!=paused;
            rawPaused=paused;reason=paused?"raw_screen_paused":"manual_panel";writeRawLease(!paused);
            s.handler.postDelayed(rawRenew,2000);
            if(changed){s.log(paused?"主屏节点已释放，保留手动目标等待解锁":"解锁后恢复主屏手动目标："+rawTarget);s.queueControlPublish();}
        }catch(Throwable failure){boolean restore=rawWasAuto;relinquish("raw_guard_error",false);if(restore)try{setAuto(true);}catch(Throwable ignored){}s.log("主屏节点守护续约失败，已退让");s.queuePublish();}
    }
    void armRaw(String token,int target)throws Exception{armRaw(token,target,"");}
    void armRaw(String token,int target,String physical)throws Exception{
        if(!token.startsWith("cc_")&&options.manualMaximum>0&&target>options.manualMaximum)throw new IllegalArgumentException("节点目标超过磁贴设置上限");
        if(!options.manualPanel&&!token.startsWith("cc_"))throw new IllegalStateException("主屏手动面板已关闭");
        if(!RawPanelOutputHooks.supports(s.owner.getClass()))throw new IllegalStateException("当前系统尚未接入主屏输出隔离");
        // A dark lock paused automatic mode on the user's behalf. Preserve that
        // ownership before relinquishing it, so a failed raw transaction can recover.
        boolean wasAuto=owner.equals("raw_panel")?rawWasAuto:owner.equals("dark")||owner.equals("app")||auto();
        s.outdoorNode.release();if(!owner.equals("raw_panel"))relinquish("raw_panel",false);
        if(token.startsWith("cc_"))suppressApplication();
        busy=true;
        try{
            mark(new JSONObject().put("owner","raw_panel").put("user",0).put("was_auto",wasAuto).put("session",token).toString());
            owner="raw_panel";session=token;rawPhysicalId=physical;rawWasAuto=wasAuto;held=Float.NaN;reason="manual_panel";
            rawTarget=target;rawPaused=false;rawScreenOff=false;rawConfirmed=false;mode(false);writeRawLease(true);
            s.handler.removeCallbacks(rawRenew);s.handler.postDelayed(rawRenew,2000);
        }catch(Throwable failure){
            stopRaw();owner="none";session="";try{mark(null);}catch(Throwable ignored){}
            if(wasAuto)try{mode(true);}catch(Throwable ignored){}throw failure;
        }finally{busy=false;}
    }
    void command(){
        if(s.closed)return;
        String id="";
        try{
            String raw=Settings.Global.getString(s.context.getContentResolver(),REQUEST);if(raw==null||raw.length()>2048)return;
            JSONObject request=new JSONObject(raw);id=request.getString("id");if(id.equals(lastRequest))return;lastRequest=id;
            if(!id.matches("[A-Za-z0-9_-]{1,80}")||!request.getString("build").equals(HookRuntime.BUILD)||!request.getString("process_start").equals(HookRuntime.processStart())||Math.abs(SystemClock.elapsedRealtime()-request.getLong("elapsed"))>15000)throw new IllegalArgumentException("亮度请求已过期，请重新打开面板");
            String action=request.getString("action"),token=request.getString("session");
            if(!enabled()||!awake()||!mainDisplay())throw new IllegalStateException("请先启用引擎并保持主屏亮起");
            // Raw control only needs main-display ownership and mode acknowledgement.
            // HBM's float range and temporarily missing BrightnessInfo are irrelevant to sysfs.
            if(!action.startsWith("raw_")&&!action.equals("keep_awake")&&!available())throw new IllegalStateException("主屏框架亮度接口尚未就绪");
            if(!token.matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("面板会话无效");
            if(action.equals("keep_awake")){if(!unlocked())throw new IllegalStateException("请先解锁");TemporaryScreenOn.set(s.context,request.getBoolean("enabled"));}
            else if(action.equals("raw_begin")){
                long gesture=request.getLong("gesture_elapsed"),age=SystemClock.elapsedRealtime()-gesture;
                if(age<0||age>3000||lastAutoEnabledAt>gesture)throw new IllegalStateException("系统模式已改变，请再次滑动");
                String physical=request.optString("panel_display_id");
                if(!physical.isEmpty()&&!physical.equals(HookEntry.get(HookEntry.get(s.owner,"mDisplayPowerController"),"mUniqueDisplayId")))throw new IllegalStateException("当前屏幕已切换，请重新打开面板后调节");
                int target=request.getInt("value");if(request.getDouble("value")!=target||target<10||target>65535||!unlocked())throw new IllegalStateException("请解锁并选择有效的主屏节点值");armRaw(token,target,physical);
            }else if(action.equals("raw_validate")||action.equals("raw_ready")){
                if(!owner.equals("raw_panel")||!session.equals(token)||auto())throw new IllegalStateException("自动亮度已开启或会话已失效，停止节点写入");
                if(!rawIdentityCurrent()){relinquish("fold_display_changed",true);throw new IllegalStateException("当前屏幕已切换，请重新打开面板后调节");}
                if(!unlocked()||rawScreenOff)throw new IllegalStateException("锁屏期间停止节点写入");
                int target=request.getInt("value");if(request.getDouble("value")!=target||target<10||target>65535)throw new IllegalArgumentException("主屏节点值无效");
                if(action.equals("raw_ready")){if(!RawPanelLease.healthy(session))throw new IllegalStateException("主屏节点守护未确认接管");rawConfirmed=true;}
                if(options.manualMaximum>0&&target>options.manualMaximum)throw new IllegalArgumentException("节点目标超过磁贴设置上限");
                rawTarget=target;writeRawLease(true);
            }else if(action.equals("raw_abort")){
                if(owner.equals("raw_panel")&&session.equals(token)){boolean restore=rawWasAuto;relinquish("raw_write_error",false);if(restore)setAuto(true);}
            }else if(action.equals("raw_restore")){
                suppressApplication();
                relinquish("user_auto",false);setAuto(true);
            }else if(action.equals("end")){if(owner.equals("panel")&&session.equals(token))relinquish("panel_closed",false);}
            else if(action.equals("restore")){suppressApplication();relinquish("panel_auto",true);setAuto(true);unlisten();}
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
