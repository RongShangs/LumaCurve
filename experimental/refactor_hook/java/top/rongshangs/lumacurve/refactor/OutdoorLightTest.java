package top.rongshangs.lumacurve.refactor;

import android.os.SystemClock;
import java.util.*;
import org.json.*;
import top.rongshangs.lumacurve.refactor.hooking.*;

/** Bounded simulation scoped to native calculations; no fake sensor or learning inputs. */
final class OutdoorLightTest {
    static final long DURATION=20000;static final float TEST_LUX=100000;
    final HookRuntime s;final List<XC_MethodHook.Unhook> hooks=new ArrayList<>();
    OutdoorLightTestAccess access;boolean active,stopping,stopQueued;String session="",reason="idle",error="",profile="unconfirmed";long until,started;float realLux=Float.NaN,calculatedRaw=Float.NaN;double peak=Double.NaN,peakFraction=Double.NaN;long samples;
    final List<FieldOverride> calculations=new ArrayList<>();
    final Runnable timeout=()->stop("timeout");
    int cleanupAttempts;final Runnable cleanupRetry=()->cleanup();
    boolean oldActive;long oldEnter,oldExit,oldStarted,oldCooldown,oldManual,oldNext;float oldManualLux;String oldReason;
    private String preparingSession="",preparingRevision="";
    private long preparationUntil,nodeReadyAt;
    private boolean pausedNodes;
    OutdoorLightTest(HookRuntime state){s=state;}
    boolean preparing(){return !preparingSession.isEmpty()&&SystemClock.uptimeMillis()<preparationUntil;}
    boolean blocksNodes(){return active||stopping||preparing();}
    String blocked(){return blocked(pausedNodes);}
    private String blocked(boolean nodePaused){
        if(s.closed||!s.onDisplayThread()||!s.appliesToUser()||!s.phase.equals("active"))return "inactive";
        if(s.outdoor.options.nodeBoost&&!nodePaused)return "node_mode";
        if(!"none".equals(s.brightnessControl.owner)||s.brightnessControl.independentActive()||!s.brightnessControl.unlocked())return "owned_or_locked";
        if(access!=null&&!access.current())return "display_changed";
        try{if(access!=null&&!access.scope.allows(s.owner))return "protected_frame";}catch(Throwable unavailable){return "scope_unavailable";}
        String block=s.outdoor.testBlock();return block;
    }
    boolean usable(){if(!active)return false;if(!s.onDisplayThread()){if(!stopQueued){stopQueued=true;if(!s.handler.post(()->{stopQueued=false;stop("thread_changed");}))error="Display handler closed during test";}return false;}String block=blocked();if(block==null&&SystemClock.elapsedRealtime()>=until)block="timeout";if(block!=null){stop(block);return false;}return true;}
    void remember(float value){if(Float.isFinite(value)&&value>=0&&value<=1000000)realLux=value;}
    void rememberPolicy(){oldActive=s.outdoor.policy.active;oldEnter=s.outdoor.policy.enterAt;oldExit=s.outdoor.policy.exitAt;oldStarted=s.outdoor.policy.started;oldCooldown=s.outdoor.policy.cooldownUntil;oldManual=s.outdoor.policy.manualAt;oldNext=s.outdoor.policy.next;oldManualLux=s.outdoor.policy.manualLux;oldReason=s.outdoor.policy.reason;}
    void restorePolicy(){s.outdoor.policy.active=oldActive;s.outdoor.policy.enterAt=oldEnter;s.outdoor.policy.exitAt=oldExit;s.outdoor.policy.started=oldStarted;s.outdoor.policy.cooldownUntil=oldCooldown;s.outdoor.policy.manualAt=oldManual;s.outdoor.policy.next=oldNext;s.outdoor.policy.manualLux=oldManualLux;s.outdoor.policy.reason=oldReason;}
    // Never call setAmbientLux with synthetic input: that method may inline model resets.
    void synthetic()throws Exception{access.notifyLux(TEST_LUX);}
    void closeCalculations(){for(int i=calculations.size()-1;i>=0;i--)try{calculations.get(i).close();calculations.remove(i);}catch(Throwable failure){error=failure.toString();}}
    long prepare(String token,JSONObject binding)throws Exception{
        if(blocksNodes())throw new IllegalStateException("已有强光测试正在进行");
        if(!s.outdoor.options.nodeBoost||binding==null||!s.revision.equals(binding.optString("revision"))
                ||s.boundUser!=binding.optInt("user_serial",-1))throw new IllegalStateException("强光测试设置已变化，请重试");
        access=new OutdoorLightTestAccess(s);
        if(access.physical!=binding.optLong("physical"))throw new IllegalStateException("当前屏幕已切换，请刷新后重试");
        verifyStart(true);
        preparingSession=token;preparingRevision=s.revision;
        preparationUntil=SystemClock.uptimeMillis()+6000;
        nodeReadyAt=Math.max(SystemClock.uptimeMillis(),s.outdoorNode.pauseForTest());
        return nodeReadyAt;
    }
    private void verifyStart(boolean nodePaused)throws Exception{
        String block=blocked(nodePaused);
        if(block!=null||!access.ready())throw new IllegalStateException("当前不能进行强光测试："+(block==null?"sensor_not_ready":block));
    }
    void start(String token,boolean prepared)throws Exception{
        if(active||stopping)throw new IllegalStateException("已有强光测试正在进行");if(!token.matches("[A-Za-z0-9_-]{1,80}"))throw new IllegalArgumentException("测试会话无效");
        if(prepared){
            if(!preparing()||!preparingSession.equals(token)||!preparingRevision.equals(s.revision)||SystemClock.uptimeMillis()<nodeReadyAt)
                throw new IllegalStateException("节点高亮释放尚未确认，请重试");
        }else if(preparing())throw new IllegalStateException("已有强光测试正在准备");
        closeCalculations();cleanup();if(!hooks.isEmpty()||!calculations.isEmpty())throw new IllegalStateException("旧测试接口尚未清理，请稍后重试");cleanupAttempts=0;
        if(!prepared)access=new OutdoorLightTestAccess(s);
        verifyStart(prepared);
        realLux=access.read();calculatedRaw=Float.NaN;rememberPolicy();session=token;reason="starting";error="";profile="unconfirmed";peak=peakFraction=Double.NaN;samples=0;started=SystemClock.elapsedRealtime();until=started+DURATION;
        try(HookTransaction transaction=new HookTransaction()){
            XC_MethodHook noReset=new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){if(p.thisObject==access.abc&&active&&s.onDisplayThread()&&!calculations.isEmpty())p.setResult(null);}};
            own(transaction,XposedBridge.hookMethod(access.reset,noReset));own(transaction,XposedBridge.hookMethod(access.maybeReset,noReset));
            XC_MethodHook manual=new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){if(p.thisObject==access.abc&&active){if(!s.onDisplayThread()){s.handler.post(()->stop("thread_changed"));return;}stop("manual");try{float restored=access.read();if(p.args.length==2&&Float.isFinite(restored))p.args[0]=restored;}catch(Exception unavailable){if(p.args.length==2&&Float.isFinite(realLux))p.args[0]=realLux;}}}};
            own(transaction,XposedBridge.hookMethod(access.user,manual));own(transaction,XposedBridge.hookMethod(access.userLux,manual));
            own(transaction,XposedBridge.hookMethod(access.sdr,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){if(p.thisObject!=s.owner||!active)return;if(!s.onDisplayThread()){usable();return;}try{if(!Boolean.TRUE.equals(p.args[1]))stop("protected_frame");else if(usable()&&calculations.isEmpty()&&Float.compare(access.raw(),calculatedRaw)!=0)stop("input_route_changed");}catch(Throwable unavailable){error=unavailable.toString();stop("protected_frame");}}protected void afterHookedMethod(MethodHookParam p){if(p.thisObject==s.owner&&active&&p.hasThrowable())stop("native_error");}}));
            own(transaction,XposedBridge.hookMethod(access.ambient,new XC_MethodHook(){
                protected void afterHookedMethod(MethodHookParam p){if(p.thisObject!=access.abc||!active)return;if(p.hasThrowable()){stop("native_error");return;}try{if(usable()){remember(access.read());synthetic();}}catch(Throwable failure){error=failure.toString();stop("native_error");}}
            }));
            if(access.dual!=null)own(transaction,XposedBridge.hookMethod(access.dual,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){if(p.thisObject!=access.abc||!active)return;try{if(p.hasThrowable())stop("native_error");else if(usable()){remember(access.read());synthetic();}}catch(Throwable failure){error=failure.toString();stop("native_error");}}}));
            own(transaction,XposedBridge.hookMethod(access.update,new XC_MethodHook(){
                protected void beforeHookedMethod(MethodHookParam p){if(p.thisObject!=access.abc||!active)return;try{if(Boolean.TRUE.equals(p.args[1])){stop("manual");return;}if(usable()){if(calculations.isEmpty())remember(access.read());synthetic();FieldOverride scope=FieldOverride.apply(access.abc,access.lux,TEST_LUX);calculations.add(scope);p.setObjectExtra("outdoor_test_lux",scope);}}catch(Throwable failure){error=failure.toString();stop("native_error");}}
                protected void afterHookedMethod(MethodHookParam p){Object value=p.getObjectExtra("outdoor_test_lux");if(!(value instanceof FieldOverride))return;try{((FieldOverride)value).close();calculations.remove(value);if(active){if(p.hasThrowable())stop("native_error");else calculatedRaw=access.raw();}}catch(Throwable failure){error=failure.toString();stop("native_error");}}
            }));
            transaction.commit();profile="abc_native_lux_lease";
        }catch(Throwable failure){error=failure.toString();cleanup();reason="install_failed";throw new IllegalStateException("强光测试接口未完整安装",failure);}
        active=true;pausedNodes=prepared;preparingSession="";reason="running";
        s.outdoorNode.release();
        try{if(!s.handler.postDelayed(timeout,DURATION))throw new IllegalStateException("测试超时保护未就绪");synthetic();access.update.invoke(access.abc,true,false);if(!active||!Float.isFinite(calculatedRaw))throw new IllegalStateException("测试照度未进入原生计算");s.outdoor.tick();s.requestRecalculation();s.queuePublish();}
        catch(Throwable failure){error=failure.toString();stop("start_failed");throw new IllegalStateException("强光测试未启动",failure);}
    }
    void own(HookTransaction transaction,XC_MethodHook.Unhook handle){transaction.add(handle);hooks.add(handle);}
    void stopFor(String token){if(active&&!session.equals(token)||preparing()&&!preparingSession.equals(token))throw new IllegalArgumentException("测试会话已变化");stop("user_stop");}
    void cleanup(){
        closeCalculations();s.handler.removeCallbacks(cleanupRetry);for(int i=hooks.size()-1;i>=0;i--)try{hooks.get(i).unhook();hooks.remove(i);}catch(Throwable unavailable){error=unavailable.toString();}
        if((!hooks.isEmpty()||!calculations.isEmpty())&&cleanupAttempts++<2)s.handler.postDelayed(cleanupRetry,cleanupAttempts==1?250:1000);
    }
    void stop(String why){
        boolean suspended=!preparingSession.isEmpty();preparingSession="";
        if(!active||stopping){if(suspended&&!stopping)s.outdoorNode.changed();return;}stopping=true;
        if(access!=null&&access.current()&&s.onDisplayThread()&&(why.equals("timeout")||why.equals("user_stop"))&&blocked()==null)try{JSONObject last=new JSONObject();ScreenBrightnessReading.capture(s.owner,last);record(last);}catch(Throwable unavailable){}
        active=false;reason=why;s.handler.removeCallbacks(timeout);
        try{
            closeCalculations();cleanup();
            boolean current=access!=null&&access.current();
            if(current)restorePolicy();else s.outdoor.policy.reset();
            if(current){try{float real=access.read();remember(real);access.notifyLux(real);}catch(Throwable restore){error=restore.toString();}
                try{s.outdoor.tick();access.update.invoke(access.abc,true,false);}catch(Throwable restore){error=restore.toString();}}
            if(!s.closed){s.requestRecalculation();s.queuePublish();}
        }finally{pausedNodes=false;stopping=false;s.outdoorNode.changed();}
    }
    void check(){if(active)usable();}
    void record(JSONObject state){boolean accepted=false;double fraction=state.optDouble("actual_brightness",Double.NaN);if(Double.isFinite(fraction)&&fraction>=0&&fraction<=1&&"display_power_state".equals(state.optString("actual_brightness_source"))){peakFraction=Double.isFinite(peakFraction)?Math.max(peakFraction,fraction):fraction;accepted=true;}double n=state.optDouble("actual_nit",Double.NaN);if(Double.isFinite(n)&&n>=0&&"device_backlight_mapping".equals(state.optString("actual_nit_source"))){peak=Double.isFinite(peak)?Math.max(peak,n):n;accepted=true;}if(accepted)samples++;}
    void observe(JSONObject state){if(active){check();if(active)record(state);}}
    JSONObject status()throws JSONException{JSONObject out=new JSONObject().put("active",active).put("preparing",preparing()).put("session",session).put("reason",reason).put("profile",profile).put("simulated_lux",TEST_LUX).put("until_elapsed_ms",until).put("remaining_ms",active?Math.max(0,until-SystemClock.elapsedRealtime()):0).put("duration_ms",DURATION).put("samples",samples).put("hooks_pending",hooks.size());if(Float.isFinite(realLux))out.put("real_lux",realLux);if(Double.isFinite(peak))out.put("recorded_peak_nit",peak);if(Double.isFinite(peakFraction))out.put("recorded_peak_brightness",peakFraction);if(!error.isEmpty())out.put("error",error);return out;}
}
