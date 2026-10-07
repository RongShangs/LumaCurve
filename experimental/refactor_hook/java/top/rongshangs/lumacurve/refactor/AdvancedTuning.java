package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import java.util.*;
import de.robv.android.xposed.*;

/** Optional, object-scoped hooks; no periodic worker, sensor substitution or driver writes. */
final class AdvancedTuning {
    static final Set<Class<?>> thresholds=new HashSet<>(),animators=new HashSet<>(),sunlights=new HashSet<>(),touches=new HashSet<>();
    static final ThreadLocal<HookRuntime> thresholdScope=new ThreadLocal<>();
    static final Map<Object,double[]> animationOriginal=new WeakHashMap<>();
    static void install(ClassLoader loader){installThresholds(loader);installAnimator(loader);installSunlight(loader);installTouch(loader);}
    static void installThresholds(ClassLoader loader){
        List<XC_MethodHook.Unhook> added=new ArrayList<>();
        try{Class<?> cls=Class.forName("com.android.server.display.HysteresisLevelsImpl",false,loader);if(thresholds.contains(cls))return;
            // Hysteresis implementations may be shared. Scope to the primary sensor consumer,
            // rather than altering every display that happens to call the same instance.
            for(String cname:new String[]{"AutomaticBrightnessController","DualSensorPolicy"}){
                Class<?> consumer=Class.forName("com.android.server.display."+cname,false,loader);
                int count=0;for(Method m:consumer.getDeclaredMethods()){
                    if(!(cname.equals("AutomaticBrightnessController")?m.getName().equals("setAmbientLux"):
                        Arrays.asList("setAmbientLuxWhenInvalid","updateDualSensorPolicy","updateSingleSensorPolicy").contains(m.getName())))continue;
                    count++;added.add(XposedBridge.hookMethod(m,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
                        p.setObjectExtra("hyperlux.threshold.scope",new HookRuntime[]{thresholdScope.get()});thresholdScope.remove();
                        synchronized(HookEntry.states){for(HookRuntime s:HookEntry.states.values()){if(!s.onDisplayThread())continue;
                            try{Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl");if(HookEntry.get(impl,cname.equals("AutomaticBrightnessController")?"mAutomaticBrightnessController":"mDualSensorPolicy")==p.thisObject){thresholdScope.set(s);break;}}catch(Throwable ignored){}
                        }}
                    }protected void afterHookedMethod(MethodHookParam p){HookRuntime[] previous=(HookRuntime[])p.getObjectExtra("hyperlux.threshold.scope");if(previous!=null){if(previous[0]==null)thresholdScope.remove();else thresholdScope.set(previous[0]);}}}));
                }if(count==0)throw new IllegalStateException("threshold consumer ABI");
            }
            for(String name:new String[]{"getBrighteningThreshold","getDarkeningThreshold","getBrighteningSmallThreshold"}){
                Method method=cls.getDeclaredMethod(name,float.class);if(method.getReturnType()!=float.class)throw new IllegalStateException("threshold ABI");
                final boolean bright=!name.equals("getDarkeningThreshold"),small=name.equals("getBrighteningSmallThreshold");
                added.add(XposedBridge.hookMethod(method,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){
                    if(p.hasThrowable())return;
                    synchronized(HookEntry.states){for(HookRuntime s:HookEntry.states.values()){
                        if(thresholdScope.get()!=s||(!s.advanced.enabled[0]&&!(s.lowLightEnabled&&s.lowThresholds.enabled))||!s.onDisplayThread())continue;
                        try{Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl");if(HookEntry.get(impl,"mHysteresisLevelsImpl")!=p.thisObject)continue;
                            Object abc=HookEntry.get(impl,"mAutomaticBrightnessController");
                            float lux=(Float)p.args[0];if(!Float.isFinite(lux)||lux<0)return;
                            boolean custom=s.advanced.enabled[0]&&lux<=s.advanced.values[5]&&s.normalTuningAllowed(abc,impl);
                            boolean guard=s.lowLightEnabled&&s.lowThresholds.enabled&&lux<=s.lowLightLimit&&s.lowLightApplies(abc,impl,lux);
                            if(!custom&&!guard)return;
                            Object hbm=HookEntry.get(p.thisObject,"mHbmController");
                            if(hbm!=null){Object data=HookEntry.get(hbm,"mHbmData");if(data!=null){float minimum=HookRuntime.optionalNumber(data,"minimumLux");if(!Float.isFinite(minimum)||lux>=minimum){s.thresholdSkipReason="hbm_range";return;}}}
                            float original=(Float)p.getResult();
                            float next=applyThreshold(s,lux,original,bright,small,custom,guard);
                            // Keep the small-change threshold at or below the normal threshold.
                            if(small){Method normal=cls.getDeclaredMethod("getBrighteningThreshold",float.class);float nativeNormal=((Number)XposedBridge.invokeOriginalMethod(normal,p.thisObject,new Object[]{lux})).floatValue();next=Math.min(next,applyThreshold(s,lux,nativeNormal,true,false,custom,guard));}
                            s.thresholdSkipReason="";s.thresholdError="";
                            if(guard){s.lastLowThresholdLux=lux;if(small)s.lastLowSmallThreshold=next;else if(bright)s.lastLowBrightThreshold=next;else s.lastLowDarkThreshold=next;}
                            if(Float.compare(next,original)!=0){p.setResult(next);s.thresholdAdjustments++;if(guard)s.lowLightThresholdAdjustments++;}return;
                        }catch(Throwable unavailable){s.thresholdSkipReason="interface_error";String error=unavailable.toString();if(!error.equals(s.thresholdError)){s.thresholdError=error;s.log("暗光阈值未应用："+error);}return;}
                    }}
                }}));
            }thresholds.add(cls);
        }catch(Throwable unavailable){for(XC_MethodHook.Unhook h:added)h.unhook();XposedBridge.log("HyperLux threshold options unavailable: "+unavailable);}
    }
    static float applyThreshold(HookRuntime s,float lux,float original,boolean bright,boolean small,boolean custom,boolean guard){
        float next=custom?AdvancedPolicy.threshold(lux,original,bright,(float)s.advanced.values[small?2:bright?0:1],small?0:(float)s.advanced.values[bright?3:4]):original;
        // Preserve OEM margins even if another custom setting would make them more sensitive.
        return guard?s.lowThresholds.threshold(lux,bright?Math.max(original,next):Math.min(original,next),bright):next;
    }
    static void installAnimator(ClassLoader loader){
        try{Class<?> cls=Class.forName("com.android.server.display.RefactorAutoBrightnessAnimator",false,loader);if(animators.contains(cls))return;
            for(String field:new String[]{"mDuration","mDurationTime","mLogicalStart","mLogicalTarget"})if(HookEntry.field(cls,field).getType()!=double.class)throw new IllegalStateException("animation ABI");
            Method method=cls.getDeclaredMethod("updatePerceptualDuration");if(method.getReturnType()!=void.class)throw new IllegalStateException("animation method ABI");
            XposedBridge.hookMethod(method,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){
                if(p.hasThrowable())return;
                synchronized(HookEntry.states){for(HookRuntime s:HookEntry.states.values()){
                    if(!s.advanced.enabled[2]||!s.onDisplayThread())continue;
                    try{if(HookEntry.get(s.owner,"mSdrBrightnessAnimator")!=p.thisObject&&HookEntry.get(s.owner,"mBrightnessAnimator")!=p.thisObject)continue;
                        Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl"),abc=HookEntry.get(impl,"mAutomaticBrightnessController");if(!s.animationTuningAllowed(abc,impl))return;
                        double start=((Number)HookEntry.get(p.thisObject,"mLogicalStart")).doubleValue(),target=((Number)HookEntry.get(p.thisObject,"mLogicalTarget")).doubleValue();
                        if(!Double.isFinite(start)||!Double.isFinite(target)||start==target)return;
                        double original=((Number)HookEntry.get(p.thisObject,"mDuration")).doubleValue(),next=AdvancedPolicy.duration(original,s.advanced.values[target>start?9:10]);
                        if(next!=original){animationOriginal.put(p.thisObject,new double[]{original,next});HookEntry.field(cls,"mDuration").setDouble(p.thisObject,next);HookEntry.field(cls,"mDurationTime").setDouble(p.thisObject,next);s.animationAdjustments++;s.lastAnimationSeconds=next;}else animationOriginal.remove(p.thisObject);return;
                    }catch(Throwable unavailable){return;}
                }}
            }});animators.add(cls);
        }catch(Throwable unavailable){XposedBridge.log("HyperLux animation options unavailable: "+unavailable);}
    }
    static void installSunlight(ClassLoader loader){
        List<XC_MethodHook.Unhook> added=new ArrayList<>();
        try{Class<?> cls=Class.forName("com.android.server.display.SunlightController",false,loader);if(sunlights.contains(cls))return;
            if(((Number)HookEntry.field(cls,"THRESHOLD_ENTER_SUNLIGHT_DURATION").get(null)).longValue()!=5000||
               ((Number)HookEntry.field(cls,"THRESHOLD_EXIT_SUNLIGHT_DURATION").get(null)).longValue()!=2000||
               ((Number)HookEntry.field(cls,"SUNLIGHT_AMBIENT_LIGHT_HORIZON").get(null)).longValue()!=10000)throw new IllegalStateException("sunlight timing semantics changed");
            for(String f:new String[]{"mSunlightSensorEnabled","mScreenOn","mAutoBrightnessSettingsEnable"})if(HookEntry.field(cls,f).getType()!=boolean.class)throw new IllegalStateException("sunlight ABI");
            for(String name:new String[]{"nextEnterSunlightModeTransition","nextExitSunlightModeTransition"}){
                Method method=cls.getDeclaredMethod(name,long.class);if(method.getReturnType()!=long.class)throw new IllegalStateException("sunlight deadline ABI");
                final boolean enter=name.contains("Enter");
                added.add(XposedBridge.hookMethod(method,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){
                    if(p.hasThrowable())return;
                    synchronized(HookEntry.states){for(HookRuntime s:HookEntry.states.values()){
                        if(!s.advanced.enabled[3]||!s.onDisplayThread())continue;
                        try{if(HookEntry.get(s.owner,"mSunlightController")!=p.thisObject)continue;
                            // This is Xiaomi's MANUAL sunlight mode, not automatic HBM/HDR.
                            if(!s.phase.equals("active")||!s.appliesToUser()||!Boolean.FALSE.equals(s.hdrActive())||
                                !Boolean.TRUE.equals(HookRuntime.optionalBoolean(p.thisObject,"mScreenOn"))||
                                !Boolean.TRUE.equals(HookRuntime.optionalBoolean(p.thisObject,"mSunlightSensorEnabled"))||
                                !Boolean.FALSE.equals(HookRuntime.optionalBoolean(p.thisObject,"mAutoBrightnessSettingsEnable")))return;
                            long base=enter?5000:2000,chosen=(long)s.advanced.values[enter?11:12];
                            if(chosen!=base){p.setResult(DelayPolicy.deadline((Long)p.getResult(),(Long)p.args[0],base,chosen));s.sunlightAdjustments++;}return;
                        }catch(Throwable unavailable){return;}
                    }}
                }}));
            }sunlights.add(cls);
        }catch(Throwable unavailable){for(XC_MethodHook.Unhook h:added)h.unhook();XposedBridge.log("HyperLux sunlight options unavailable: "+unavailable);}
    }
    static boolean supported(int group,HookRuntime s){try{
        Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl");
        if(s.hdrProbe==null||!ResponseTuning.supported(s.owner))return false;
        if(group==0)return thresholds.contains(HookEntry.get(impl,"mHysteresisLevelsImpl").getClass());
        if(group==1)return LowLightTuning.supported(s);
        if(group==2)return animators.contains(HookEntry.get(s.owner,"mBrightnessAnimator").getClass())&&animators.contains(HookEntry.get(s.owner,"mSdrBrightnessAnimator").getClass());
        if(group==3)return sunlights.contains(HookEntry.get(s.owner,"mSunlightController").getClass());
        return group==4&&touches.contains(HookEntry.get(impl,"mTouchAreaHelper").getClass());
    }catch(Throwable missing){return false;}}
    static void capabilities(JSONObjectSink sink,HookRuntime s)throws Exception{for(int i=0;i<AdvancedOptions.GROUPS.length;i++)sink.put(AdvancedOptions.GROUPS[i]+"_supported",supported(i,s));}
    static void installTouch(ClassLoader loader){try{
        Class<?> cls=Class.forName("com.android.server.display.TouchCoverProtectionHelper",false,loader);if(touches.contains(cls))return;
        if(HookEntry.field(cls,"mTouchEventDebounce").getType()!=int.class)throw new IllegalStateException("touch debounce ABI");
        Method method=cls.getDeclaredMethod("isTouchCoverProtectionActive");if(method.getReturnType()!=boolean.class)throw new IllegalStateException("touch protection ABI");
        XposedBridge.hookMethod(method,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
            synchronized(HookEntry.states){for(HookRuntime s:HookEntry.states.values()){
                if(!s.advanced.enabled[4]||!s.onDisplayThread())continue;
                try{Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl");if(HookEntry.get(impl,"mTouchAreaHelper")!=p.thisObject)continue;
                    if(!s.normalTuningAllowed(HookEntry.get(impl,"mAutomaticBrightnessController"),impl))return;
                    int before=((Number)HookEntry.get(p.thisObject,"mTouchEventDebounce")).intValue(),chosen=(int)s.advanced.values[13];if(before==chosen)return;
                    p.setObjectExtra("hyperlux.touch.saved",before);HookEntry.field(cls,"mTouchEventDebounce").setInt(p.thisObject,chosen);if(!s.probingScenes)s.touchAdjustments++;return;
                }catch(Throwable unavailable){return;}
            }}
        }protected void afterHookedMethod(MethodHookParam p){Object original=p.getObjectExtra("hyperlux.touch.saved");if(original!=null)try{HookEntry.field(cls,"mTouchEventDebounce").setInt(p.thisObject,(Integer)original);}catch(Throwable failed){XposedBridge.log("HyperLux touch restoration failed: "+failed);}}});touches.add(cls);
    }catch(Throwable missing){XposedBridge.log("HyperLux touch options unavailable: "+missing);}}
    interface JSONObjectSink{void put(String key,boolean value)throws Exception;}
    static void refreshThresholds(HookRuntime s){
        // Recompute cached thresholds on apply/stop; never reset a sensor buffer or a drag anchor.
        HookRuntime previous=thresholdScope.get();thresholdScope.set(s);
        try{Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl"),abc=HookEntry.get(impl,"mAutomaticBrightnessController"),h=HookEntry.get(impl,"mHysteresisLevelsImpl");
            if(!thresholds.contains(h.getClass()))return;
            refresh(h,abc,"mAmbientLux",new String[]{"mAmbientBrighteningThreshold","mAmbientDarkeningThreshold","mAmbientBrighteningSmallThreshold"});
            Object dual=HookEntry.get(impl,"mDualSensorPolicy");if(Boolean.TRUE.equals(HookRuntime.optionalBoolean(dual,"mAssistAmbientLuxValid")))refresh(h,dual,"mAssistFastAmbientLux",new String[]{"mAssistBrighteningThreshold","mAssistDarkeningThreshold","mAssistBrighteningSmallThreshold"});
        }catch(Throwable unavailable){s.log("阈值将在系统下次确认照度时更新");}finally{if(previous==null)thresholdScope.remove();else thresholdScope.set(previous);}
    }
    static void restoreAnimation(HookRuntime s){for(String name:new String[]{"mBrightnessAnimator","mSdrBrightnessAnimator"})try{
        Object animator=HookEntry.get(s.owner,name);double[] previous=animationOriginal.remove(animator);if(previous==null)continue;
        if(((Number)HookEntry.get(animator,"mDuration")).doubleValue()==previous[1]){
            HookEntry.field(animator.getClass(),"mDuration").setDouble(animator,previous[0]);
            if(((Number)HookEntry.get(animator,"mDurationTime")).doubleValue()==previous[1])HookEntry.field(animator.getClass(),"mDurationTime").setDouble(animator,previous[0]);
        }
    }catch(Throwable unavailable){s.log("动画参数等待系统下次过渡重建");}}
    static void refresh(Object h,Object target,String reference,String[] fields)throws Exception{
        float lux=HookRuntime.optionalNumber(target,reference);if(!Float.isFinite(lux)||lux<0)return;
        String[] names={"getBrighteningThreshold","getDarkeningThreshold","getBrighteningSmallThreshold"};float[] result=new float[3];
        for(int i=0;i<3;i++)result[i]=((Number)h.getClass().getMethod(names[i],float.class).invoke(h,lux)).floatValue();
        for(int i=0;i<3;i++)HookEntry.field(target.getClass(),fields[i]).setFloat(target,result[i]);
    }
}
