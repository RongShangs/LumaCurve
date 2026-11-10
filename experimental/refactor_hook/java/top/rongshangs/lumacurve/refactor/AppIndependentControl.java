package top.rongshangs.lumacurve.refactor;

import android.os.SystemClock;
import android.provider.Settings;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Uses BrightnessControl's existing mode lease; no second output loop or window replay. */
final class AppIndependentControl {
    static final String MEMORY="hyperlux_application_brightness_memory_v1";
    static Executor IO=new ThreadPoolExecutor(0,1,10,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(4),r->{Thread t=new Thread(r,"HyperLux-app-memory");t.setDaemon(true);return t;});
    final BrightnessControl c;final HookRuntime s;final AppIndependentPolicy policy=new AppIndependentPolicy();
    volatile long generation;volatile boolean active,suspended;String key="",pkg="",physical="",signature="";Object range,configuration,memoryRange,memoryConfiguration;
    boolean wasAuto,loading,loaded;float baseline=Float.NaN,owned=Float.NaN;float pending=Float.NaN;long pendingGeneration,pendingAt;boolean queued;
    final Runnable userUpdate=this::applyUser;long loadGeneration;
    Pending input,lockInput;boolean lockSaved,lockQueued;
    final Runnable lockUpdate=this::saveLockInput;
    int loadAttempts;boolean loadRetryQueued;String loadBinding="";long retryGeneration;
    final Runnable loadRetry=this::retryLoad;
    static final class Pending {
        final float value;final long generation,at;final String key,pkg,session,binding,signature;
        Pending(AppIndependentControl c,float v){value=v;generation=c.generation;at=SystemClock.uptimeMillis();key=c.key;pkg=c.pkg;session=c.c.session;binding=c.policy.binding;signature=c.signature;}
    }
    boolean awaitingNative,nativeQueued;long nativeGeneration;
    final Runnable nativeUpdate=this::applyNative;
    AppIndependentControl(BrightnessControl control){c=control;s=c.s;}
    boolean owned(){return active&&c.owner.equals("app")&&c.session.startsWith("independent_");}
    boolean blocksLearning(){return active;}
    AppIndependentAccess access()throws Exception{AppIndependentAccess.install(s.owner.getClass());AppIndependentAccess p=AppIndependentAccess.profiles.get(s.owner.getClass());if(p==null)throw new IllegalStateException(AppIndependentAccess.errors.getOrDefault(s.owner.getClass(),"独立亮度接口尚未就绪"));return p;}
    boolean bound(AppIndependentAccess p)throws Exception{return physical.equals(p.identity(s))&&range==p.hbm.get(s.owner)&&configuration==p.configuration.get(s.owner);}
    String focused(){ForegroundApps.Snapshot f=c.foreground;return !c.foregroundPending&&!c.foregroundQuerying&&f.user==0&&f.display==0?f.pkg:"";}
    void configure(BrightnessControlOptions before,BrightnessControlOptions next){
        String nextSignature=next.independentSignature();if(!signature.equals(nextSignature)){generation++;resetLoad();input=lockInput=null;signature=nextSignature;policy.retain(next);}
        if((before.independentKeepReboot||!next.independentApps.isEmpty()||!next.independentGroups.isEmpty())&&(!next.independentKeepReboot||!next.independentKeepLock))persist(false);
    }
    boolean step()throws Exception{
        if((c.foregroundPending||c.foregroundQuerying)&&owned()&&!suspended&&c.enabled()&&c.awake()&&c.unlocked())return true;
        String nextPkg=focused(),next=c.options.independentKey(nextPkg);
        boolean allowed=!next.isEmpty()&&c.enabled()&&c.mainDisplay()&&c.awake()&&c.unlocked()&&!c.appExemptions.contains(nextPkg);
        if(!allowed){if(owned())c.relinquish("independent_left",true);return false;}
        if(c.owner.equals("raw_panel")||c.owner.equals("panel"))return false;
        if(!c.available())throw new IllegalStateException("主屏亮度接口尚未就绪");
        AppIndependentAccess p;String id;float ceiling;try{p=access();id=p.identity(s);ceiling=p.ceiling(s);}catch(Exception unavailable){if(owned())c.relinquish("independent_unavailable",true);c.error="独立亮度暂不可用："+unavailable;return false;}String binding=s.fingerprint+"|"+s.baselineIdentity+"|0|"+id;
        if(owned()&&!bound(p)){policy.clear();c.relinquish("independent_display_changed",true);return false;}
        Object nextRange=p.hbm.get(s.owner),nextConfiguration=p.configuration.get(s.owner);if(memoryRange!=null&&(memoryRange!=nextRange||memoryConfiguration!=nextConfiguration))policy.clear();memoryRange=nextRange;memoryConfiguration=nextConfiguration;
        policy.bind(binding);if(c.options.independentKeepReboot&&c.options.independentKeepLock&&!loaded){load();return owned();}
        if(owned()&&next.equals(key)){pkg=nextPkg;suspended=false;nativeFrame();return true;}
        float initial;boolean transfer=owned();
        if(transfer){saveInput();initial=baseline;generation++;s.handler.removeCallbacks(userUpdate);s.handler.removeCallbacks(nativeUpdate);nativeQueued=false;queued=false;}
        else{if(c.owner.equals("dark"))c.relinquish("independent_entered",true);else if(!c.owner.equals("none"))return false;initial=(float)c.display.read().getDouble("value");wasAuto=c.auto();baseline=initial;}
        float target=policy.get(next,initial,ceiling);if(!Float.isFinite(target))return false;
        physical=id;range=p.hbm.get(s.owner);configuration=p.configuration.get(s.owner);key=next;pkg=nextPkg;suspended=false;
        if(transfer){boolean previous=c.busy;c.busy=true;try{owned=c.display.write(target);c.held=owned;updateLease();}finally{c.busy=previous;}}
        else{boolean claimed=false;active=true;awaitingNative=wasAuto;generation++;try{c.claim("app","independent_"+UUID.randomUUID(),target);owned=c.held;claimed=true;}finally{if(!claimed){active=false;awaitingNative=false;}}}
        c.reason="app_independent";s.queueControlPublish();return true;
    }
    synchronized void enqueueUser(float value){
        if(!active||suspended||s.closed||!focused().equals(pkg))return;input=new Pending(this,value);pending=value;pendingGeneration=generation;pendingAt=SystemClock.uptimeMillis();if(queued)return;queued=true;if(!s.handler.postDelayed(userUpdate,180))queued=false;
    }
    void applyUser(){
        final Pending candidate;final float value;final long epoch,at;synchronized(this){queued=false;candidate=input;if(candidate==null)return;value=candidate.value;epoch=candidate.generation;at=candidate.at;}
        // A foreground query may outlast the debounce. The lease handover will
        // consume this input once its destination is known.
        if(c.foregroundPending||c.foregroundQuerying)return;
        try{if(!owned()||suspended||epoch!=generation||SystemClock.uptimeMillis()-at>2000||!focused().equals(pkg)||c.auto()||!c.enabled()||!c.awake()||!c.unlocked())return;
            AppIndependentAccess p=access();if(!bound(p)||!p.ordinaryScene(s)||!AppIndependentPolicy.ordinary(value,p.ceiling(s)))return;
            boolean previous=c.busy;c.busy=true;try{owned=c.display.write(value);c.held=owned;c.brightnessSnapshot=c.brightnessValues();updateLease();}finally{c.busy=previous;}
            if(policy.remember(key,value,p.ceiling(s))){persist(true);s.queueControlPublish();}
        }catch(Throwable failure){c.fail(failure);}
        finally{synchronized(this){if(input==candidate)input=null;}}
    }
    // Commit the input to its original lease before cancelling the debounce.
    // No output write is needed when the application is already leaving.
    void saveInput(){Pending last;synchronized(this){last=input;input=null;}if(last!=null&&last.generation==generation)rememberInput(last,false);}
    boolean matches(Pending last){return last!=null&&owned()&&last.key.equals(key)&&last.key.equals(c.options.independentKey(last.pkg))&&last.session.equals(c.session)&&last.binding.equals(policy.binding)&&last.signature.equals(signature)&&SystemClock.uptimeMillis()-last.at>=0&&SystemClock.uptimeMillis()-last.at<=2000;}
    void rememberInput(Pending last,boolean locking){
        try{if(!matches(last)||!c.enabled()||!c.mainDisplay()||c.auto()||(!locking&&(!c.awake()||!c.unlocked())))return;
            AppIndependentAccess p=access();if(!bound(p)||!p.ordinaryScene(s))return;
            if(policy.remember(last.key,last.value,p.ceiling(s))){persist(true);s.queueControlPublish();}
        }catch(Throwable unavailable){c.error="独立亮度最后调节未保存："+unavailable;}
    }
    void saveLockInput(){
        Pending last;synchronized(this){lockQueued=false;if(lockSaved)return;last=lockInput;lockSaved=true;}
        s.handler.removeCallbacks(lockUpdate);if(c.options.independentKeepLock)rememberInput(last,true);
    }
    void nativeFrame(){
        // The mode setting changes before OEM code finishes its manual handover.
        // Observe the native settings consumer; never retry on a timer or at boot.
        if(!awaitingNative||nativeQueued||!owned()||suspended||!s.onDisplayThread())return;
        // The caller can still hold the native strategy lock. Do all service
        // reads and writes only after that stack has returned to the handler.
        nativeGeneration=generation;nativeQueued=true;if(!s.handler.post(nativeUpdate))nativeQueued=false;
    }
    void applyNative(){
        nativeQueued=false;
        if(!awaitingNative||nativeGeneration!=generation||!owned()||suspended)return;
        try{if(c.auto()||!c.enabled()||!c.awake()||!c.unlocked()||!focused().equals(pkg))return;
            AppIndependentAccess p=access();if(!bound(p)||!p.ordinaryScene(s)||!Boolean.FALSE.equals(s.kernel.get("mUseAutoBrightness")))return;
            awaitingNative=false;
            // A real drag queued after mode handover owns the next write. A later
            // external writer also wins over this one-shot correction.
            if(queued||input!=null)return;
            float now=(float)c.display.read().getDouble("value");if(Math.abs(now-baseline)>.00001f&&Math.abs(now-owned)>.00001f)return;
            boolean previous=c.busy;c.busy=true;try{owned=c.display.write(c.held);c.held=owned;updateLease();}finally{c.busy=previous;}
        }catch(Throwable failure){c.fail(failure);}
    }
    void finish(boolean restore){
        saveInput();saveLockInput();
        final float lastInput;final boolean pendingOwned;
        synchronized(this){if(!active)return;suspended=true;lastInput=pending;pendingOwned=(pendingGeneration==generation&&SystemClock.uptimeMillis()-pendingAt<=2000||matches(lockInput))&&Float.isFinite(lastInput);generation++;queued=false;}
        s.handler.removeCallbacks(userUpdate);s.handler.removeCallbacks(nativeUpdate);s.handler.removeCallbacks(lockUpdate);awaitingNative=nativeQueued=false;lockQueued=false;
        try{if(restore&&Float.isFinite(baseline)){AppIndependentAccess p=access();float now=(float)c.display.read().getDouble("value");boolean ours=Float.isFinite(owned)&&Math.abs(now-owned)<.00001f||pendingOwned&&AppIndependentPolicy.ordinary(lastInput,p.ceiling(s))&&Math.abs(now-lastInput)<.00001f;
            if(bound(p)&&ours){boolean previous=c.busy;c.busy=true;try{c.display.write(baseline);p.settle(s);}finally{c.busy=previous;}}}}
        catch(Throwable unavailable){c.error=unavailable.toString();}
        finally{active=false;suspended=false;key=pkg="";range=configuration=null;owned=Float.NaN;input=lockInput=null;}
    }
    void screenChanging(){
        // Called under OEM locks: copy/fence RAM state only; provider and scene
        // checks run on the existing handler after the native stack returns.
        synchronized(this){if(!suspended){lockInput=input;lockSaved=false;}input=null;suspended=true;generation++;queued=false;}
        s.handler.removeCallbacks(userUpdate);s.handler.removeCallbacks(nativeUpdate);cancelLoadRetry();awaitingNative=nativeQueued=false;
        if(!c.options.independentKeepLock)policy.clear();
        else if(lockInput!=null&&!lockSaved&&!lockQueued){lockQueued=true;if(!s.handler.post(lockUpdate))lockQueued=false;}
    }
    void screenOff(){screenChanging();saveLockInput();if(active||!c.options.independentApps.isEmpty()||!c.options.independentGroups.isEmpty())persist(true);}
    void stop(){generation++;resetLoad();input=lockInput=null;s.handler.removeCallbacks(lockUpdate);lockQueued=false;s.handler.removeCallbacks(userUpdate);s.handler.removeCallbacks(nativeUpdate);awaitingNative=nativeQueued=false;queued=false;}
    void marker(JSONObject record,float target)throws Exception{
        JSONObject limits=c.display.read();float expected=Math.max((float)limits.getDouble("minimum"),Math.min((float)limits.getDouble("maximum"),target));
        record.put("independent_baseline",baseline).put("independent_owned",expected).put("independent_physical",physical);
    }
    void updateLease()throws Exception{JSONObject record=new JSONObject().put("owner","app").put("fingerprint",s.fingerprint).put("user",0).put("was_auto",wasAuto).put("session",c.session);marker(record,owned);c.mark(record.toString());}
    static void recover(BrightnessControl control,JSONObject record){
        if(HookEntry.currentUser.serial()!=0||!control.mainDisplay()||!record.optString("session").startsWith("independent_")||!record.optString("fingerprint").equals(control.s.fingerprint)||record.optInt("user",-1)!=0||control.auto())return;
        try{float baseline=(float)record.getDouble("independent_baseline"),owned=(float)record.getDouble("independent_owned");if(!Float.isFinite(baseline)||baseline<0||baseline>1||!Float.isFinite(owned)||owned<0||owned>1||!control.available())return;
            AppIndependentAccess p=AppIndependentAccess.inspect(control.s.owner.getClass());if(!p.identity(control.s).equals(record.optString("independent_physical")))return;float now=(float)control.display.read().getDouble("value");
            if(Math.abs(now-owned)<.00001f){boolean busy=control.busy;control.busy=true;try{control.display.write(baseline);p.settle(control.s);}finally{control.busy=busy;}}
        }catch(Throwable unavailable){control.error="独立亮度原模式恢复未完成："+unavailable;}
    }
    void load(){
        if(!loadBinding.equals(policy.binding)){resetLoad();loadBinding=policy.binding;}
        if(loading||loaded||loadRetryQueued||loadAttempts>=3)return;loading=true;loadAttempts++;final long epoch=++loadGeneration;final String binding=policy.binding;
        try{IO.execute(()->{String raw=null,error="";try{raw=Settings.Global.getString(s.context.getContentResolver(),MEMORY);}catch(Throwable unavailable){error=unavailable.toString();}final String data=raw,detail=error;s.handler.post(()->{
            if(s.closed||epoch!=loadGeneration)return;loading=false;if(!binding.equals(policy.binding))return;loaded=true;try{if(!detail.isEmpty())throw new IllegalStateException(detail);policy.merge(data,s.fingerprint);policy.retain(c.options);if(c.error.startsWith("独立亮度记忆加载暂未提交："))c.error="";}catch(Throwable invalid){c.error="独立亮度记忆未恢复："+invalid;}c.tick();
        });});}catch(RuntimeException rejected){loading=false;c.error="独立亮度记忆加载暂未提交："+rejected;if(loadAttempts<3){retryGeneration=epoch;loadRetryQueued=true;if(!s.handler.postDelayed(loadRetry,loadAttempts==1?250:1000))loadRetryQueued=false;}}
    }
    void cancelLoadRetry(){s.handler.removeCallbacks(loadRetry);loadRetryQueued=false;}
    void resetLoad(){loadGeneration++;cancelLoadRetry();loading=loaded=false;loadAttempts=0;loadBinding="";}
    void retryLoad(){loadRetryQueued=false;if(s.closed||retryGeneration!=loadGeneration||!loadBinding.equals(policy.binding)||!c.enabled()||!c.awake()||!c.unlocked()||c.options.independentKey(focused()).isEmpty()||!c.options.independentKeepLock||!c.options.independentKeepReboot)return;load();}
    void persist(boolean requested){
        // A rejected read must not erase a still-unread archive.
        if(requested&&c.options.independentKeepLock&&c.options.independentKeepReboot&&!loaded)return;
        try{String value=requested&&c.options.independentKeepLock&&c.options.independentKeepReboot?policy.export(s.fingerprint).toString():null;Map<String,String> values=new LinkedHashMap<>();values.put(MEMORY,value);s.stateWriter.submit("app_memory",values);}catch(Throwable unavailable){c.error=unavailable.toString();}
    }
    JSONObject status()throws JSONException{return new JSONObject().put("profile",AppIndependentAccess.probes.containsKey(s.owner.getClass())?"focused_app_fixed_manual":"").put("active",owned()).put("package",pkg).put("key",key).put("loading",loading).put("memory_loaded",loaded).put("memory_load_attempts",loadAttempts).put("memory_load_retry_queued",loadRetryQueued).put("native_mode_pending",awaitingNative).put("native_reapply_queued",nativeQueued).put("remembered",policy.export(s.fingerprint).getJSONObject("values")).put("physical_display_id",physical);}
}
