package top.rongshangs.lumacurve.refactor;

import android.os.SystemClock;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;
import top.rongshangs.lumacurve.refactor.hooking.*;

/** Observes model handovers; optional tuning changes only validated native predicates. */
final class MemoryLifecycle {
    static final Map<Object,MemoryLifecycle> links=Collections.synchronizedMap(new WeakHashMap<>());
    static final Set<Method> hooked=new HashSet<>();
    final HookRuntime state;
    final ArrayDeque<JSONObject> events=new ArrayDeque<>();
    Object abc,impl,mapper,model;boolean resetSupported,timeoutSupported,modelSupported,modeSupported,foreverSupported;
    String lastEvent="尚未观察到记忆交接";
    MemoryLifecycle(HookRuntime s){state=s;}
    static MemoryLifecycle active(Object target){MemoryLifecycle m=links.get(target);return m!=null&&!m.state.closed&&m.state.onDisplayThread()?m:null;}
    boolean allowed(){return !state.closed&&!state.changing&&!state.persistentMemory.replaying&&state.phase.equals("active")&&state.appliesToUser();}
    void attach(){
        if(!state.kernel.memoryHandoverSupported())return;
        links.put(state.owner,this);try{installMode(state.owner);modeSupported=true;}catch(Throwable absent){}
        try{impl=TraditionalAdapter.read(state.owner,"mAutomaticBrightnessControllerImpl");abc=TraditionalAdapter.read(impl,"mAutomaticBrightnessController");mapper=TraditionalAdapter.read(state.owner,"mBrightnessMapper");model=TraditionalAdapter.read(abc,"mShortTermModel");
            for(Object o:new Object[]{state.owner,impl,abc,mapper,model})if(o!=null)links.put(o,this);
            Method modelSetter=TraditionalAdapter.method(model.getClass(),"setUserBrightness",float.class,float.class);modelSupported=modelSetter.getReturnType()==void.class&&TraditionalAdapter.find(model.getClass(),"mAnchor").getType()==float.class&&TraditionalAdapter.find(model.getClass(),"mBrightness").getType()==float.class&&TraditionalAdapter.find(model.getClass(),"mIsValid").getType()==boolean.class;
            observe(abc,"resetShortTermModel");boolean expiry=observe(model,"invalidate");observe(impl,"needResetShortTermModelPolicy");foreverSupported=modeSupported&&modelSupported&&expiry&&state.kernel.persistentMemorySupported();
            if(state.kernel.name().equals("refactor")){Object target=state.kernel.target();links.put(target,this);observe(target,"shouldUseGoodCurve");}
            try{installAmbient(abc);}catch(Throwable absent){}try{installDisplayPolicy(abc);}catch(Throwable absent){}installResetRule();installTimeout();
        }catch(Throwable optional){state.log("手动记忆交接观察接口未完整兼容："+optional.getClass().getSimpleName());}
    }
    static void hook(Method method,XC_MethodHook callback){synchronized(hooked){if(hooked.contains(method))return;XposedBridge.hookMethod(method,callback);hooked.add(method);}}
    static boolean observe(Object target,String name){try{
        Method method=TraditionalAdapter.method(target.getClass(),name);
        if(name.equals("invalidate")&&method.getReturnType()!=void.class)return false;
        hook(method,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){MemoryLifecycle m=active(p.thisObject);if(m!=null)try{p.setObjectExtra("memory.before_count",m.state.kernel.manualPoints().length());if(name.equals("resetShortTermModel")&&m.state.persistentMemory.options.themeHold)m.state.persistentMemory.captureForHandover();if(name.equals("invalidate")&&m.keepValid(p.thisObject)){p.setResult(null);p.setObjectExtra("memory.kept",true);}}catch(Throwable ignored){}}protected void afterHookedMethod(MethodHookParam p){MemoryLifecycle m=active(p.thisObject);if(m!=null&&!p.hasThrowable()&&(!name.equals("needResetShortTermModelPolicy")||Boolean.TRUE.equals(p.getResult()))){m.event(Boolean.TRUE.equals(p.getObjectExtra("memory.kept"))?"keep_memory":name);if(name.equals("resetShortTermModel"))m.state.persistentMemory.requestAfterReset();if(!m.events.isEmpty()&&p.getObjectExtra("memory.before_count")!=null)try{m.events.getLast().put("manual_points_before",p.getObjectExtra("memory.before_count"));}catch(JSONException ignored){}}}});
        return true;
    }catch(Throwable unavailable){return false;}}
    boolean keepValid(Object target){
        try{if(!allowed()||!state.persistentMemory.options.forever||state.memoryStrength<=0||target!=model||TraditionalAdapter.read(abc,"mShortTermModel")!=target||!Boolean.TRUE.equals(state.kernel.get("mUseAutoBrightness"))||Boolean.TRUE.equals(state.hdrActive())||!normalMapper(mapper))return false;
            JSONObject current=modelRecord();if(current==null||!current.optBoolean("valid"))return false;
            JSONArray points=state.kernel.manualPoints();for(int i=0;i<points.length();i++)if(Math.abs(points.getJSONObject(i).getDouble("lux")-current.getDouble("lux"))<.5)return true;
        }catch(Throwable unavailable){}return false;
    }
    static void installMode(Object owner)throws Exception{
        Method method=TraditionalAdapter.method(owner.getClass(),"setAutoBrightnessState",boolean.class);if(method.getReturnType()!=void.class||TraditionalAdapter.find(owner.getClass(),"mUseAutoBrightness").getType()!=boolean.class)throw new NoSuchMethodException("mode lifecycle");
        hook(method,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){MemoryLifecycle m=active(p.thisObject);if(m==null||!m.allowed())return;try{boolean was=(Boolean)TraditionalAdapter.read(p.thisObject,"mUseAutoBrightness"),now=(Boolean)p.args[0];if(was&&!now)m.state.persistentMemory.captureForHandover();}catch(Throwable unavailable){}}});
    }
    static void installAmbient(Object abc)throws Exception {
        for(Class<?> type=abc.getClass();type!=null;type=type.getSuperclass())for(Method method:type.getDeclaredMethods())if(method.getName().equals("setAmbientLux")){
            Class<?>[] parameters=method.getParameterTypes();if(!Arrays.equals(parameters,new Class<?>[]{float.class})&&!Arrays.equals(parameters,new Class<?>[]{int.class,float.class,boolean.class,boolean.class}))continue;
            method.setAccessible(true);hook(method,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){MemoryLifecycle m=active(p.thisObject);if(m!=null&&!p.hasThrowable())m.state.persistentMemory.ambientReady(m.ambientLux());}});
        }
    }
    static void installDisplayPolicy(Object abc)throws Exception {
        Method policy=TraditionalAdapter.method(abc.getClass(),"setDisplayPolicy",int.class),interactive=TraditionalAdapter.method(abc.getClass(),"isInteractivePolicy",int.class);
        hook(policy,new XC_MethodHook(){
            protected void beforeHookedMethod(MethodHookParam p){MemoryLifecycle m=active(p.thisObject);if(m==null)return;try{int old=((Number)TraditionalAdapter.read(p.thisObject,"mDisplayPolicy")).intValue();boolean before=(Boolean)interactive.invoke(null,old),after=(Boolean)interactive.invoke(null,p.args[0]);p.setObjectExtra("hyperlux.policy_changed",old!=((Number)p.args[0]).intValue());p.setObjectExtra("hyperlux.wake",!before&&after);if(before&&!after)m.state.persistentMemory.screenOff();}catch(Throwable ignored){}}
            protected void afterHookedMethod(MethodHookParam p){MemoryLifecycle m=active(p.thisObject);if(m==null||p.hasThrowable())return;if(Boolean.TRUE.equals(p.getObjectExtra("hyperlux.wake")))m.state.persistentMemory.screenOn();if(Boolean.TRUE.equals(p.getObjectExtra("hyperlux.policy_changed")))m.event("display_policy");}
        });
    }
    void installResetRule(){try{
        Method method=TraditionalAdapter.method(impl.getClass(),"isNeedResetShortTermModel",float.class,long.class);
        if(method.getReturnType()!=boolean.class||TraditionalAdapter.find(impl.getClass(),"mLastCloseTime").getType()!=long.class||TraditionalAdapter.find(impl.getClass(),"mLastCloseScreenLux").getType()!=float.class)return;
        hook(method,resetCallback());resetSupported=true;
    }catch(Throwable absent){}}
    static XC_MethodHook resetCallback(){return new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){MemoryLifecycle m=active(p.thisObject);if(m==null||p.hasThrowable())return;try{MemoryOptions o=m.state.persistentMemory.options;
            if(!m.allowed()||!o.resetOverride&&!o.forever||Boolean.TRUE.equals(m.state.hdrActive())||!Boolean.TRUE.equals(m.state.kernel.get("mUseAutoBrightness")))return;
            if(o.forever){if(m.state.memoryStrength>0)p.setResult(false);return;}
            long elapsed=((Number)p.args[1]).longValue()-((Number)TraditionalAdapter.read(p.thisObject,"mLastCloseTime")).longValue();if(elapsed<0)return;
            float old=((Number)TraditionalAdapter.read(p.thisObject,"mLastCloseScreenLux")).floatValue(),lux=((Number)p.args[0]).floatValue();if(!Float.isFinite(old)||!Float.isFinite(lux)||old<0||lux<0)return;
            p.setResult(MemoryScene.reset(old,lux,elapsed,o));
        }catch(Throwable optional){}}};}
    void installTimeout(){try{
        Method method=TraditionalAdapter.method(mapper.getClass(),"getShortTermModelTimeout");if(method.getReturnType()!=long.class||TraditionalAdapter.method(abc.getClass(),"isInIdleMode").getReturnType()!=boolean.class)return;
        hook(method,timeoutCallback());timeoutSupported=true;
    }catch(Throwable absent){}}
    static XC_MethodHook timeoutCallback(){return new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){MemoryLifecycle m=active(p.thisObject);if(m!=null&&!p.hasThrowable()&&m.allowed()&&!m.state.persistentMemory.options.forever&&m.state.persistentMemory.options.timeoutOverride&&!Boolean.TRUE.equals(m.state.hdrActive())&&m.normalMapper(p.thisObject))p.setResult(m.state.persistentMemory.options.timeoutMinutes*60000L);}};}
    boolean normalMapper(Object target){try{return TraditionalAdapter.read(abc,"mCurrentBrightnessMapper")==target&&!Boolean.TRUE.equals(TraditionalAdapter.method(abc.getClass(),"isInIdleMode").invoke(abc));}catch(Throwable absent){return false;}}
    float ambientLux(){try{if(!Boolean.TRUE.equals(TraditionalAdapter.read(abc,"mAmbientLuxValid"))||!Boolean.TRUE.equals(TraditionalAdapter.read(abc,"mLightSensorEnabled")))return Float.NaN;return ((Number)TraditionalAdapter.read(abc,"mAmbientLux")).floatValue();}catch(Throwable unavailable){return Float.NaN;}}
    JSONObject modelRecord(){try{if(!modelSupported)return null;float lux=((Number)TraditionalAdapter.read(model,"mAnchor")).floatValue(),br=((Number)TraditionalAdapter.read(model,"mBrightness")).floatValue();if(!Float.isFinite(lux)||lux<0||!Float.isFinite(br)||br<0||br>1)return null;return new JSONObject().put("lux",lux).put("brightness",br).put("valid",TraditionalAdapter.read(model,"mIsValid"));}catch(Throwable unavailable){return null;}}
    boolean restoreModel(JSONObject record){if(!state.kernel.name().equals("refactor")||record==null||!modelSupported)return false;
        try{float lux=(float)record.getDouble("lux"),brightness=(float)record.getDouble("brightness");if(!Float.isFinite(lux)||lux<0||lux>state.kernel.factoryLux[3]||!Float.isFinite(brightness)||brightness<0||brightness>1)return false;
            TraditionalAdapter.method(model.getClass(),"setUserBrightness",float.class,float.class).invoke(model,lux,brightness);return true;
        }catch(Throwable optional){state.log("曲线节点已恢复，但短期模型未同步");return false;}
    }
    void event(String kind){if(state.changing||state.persistentMemory.replaying||state.closed)return;
        try{lastEvent=kind;JSONObject j=new JSONObject().put("event",kind).put("uptime_ms",SystemClock.uptimeMillis()).put("time_ms",System.currentTimeMillis()).put("manual_points",state.kernel.manualPoints().length());JSONObject record=modelRecord();if(record!=null)j.put("short_term_model",record);
            if(state.kernel.name().equals("refactor"))j.put("good_curve_available",state.kernel.get("mIsHaveGoodCurve"));if(events.size()>=16)events.removeFirst();events.addLast(j);state.queuePublish();
        }catch(Throwable optional){}}
    void put(JSONObject j)throws JSONException{j.put("memory_mode_supported",modeSupported).put("memory_forever_supported",foreverSupported).put("memory_reset_supported",resetSupported).put("memory_timeout_supported",timeoutSupported).put("memory_model_sync_supported",modelSupported).put("memory_last_system_event",lastEvent).put("memory_lifecycle",new JSONArray(events));JSONObject record=modelRecord();if(record!=null)j.put("memory_short_term_model",record);}
    void close(){synchronized(links){links.entrySet().removeIf(e->e.getValue()==this);}}
}
