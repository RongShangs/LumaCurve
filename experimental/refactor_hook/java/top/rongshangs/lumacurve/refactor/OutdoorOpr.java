package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.Method;
import java.util.*;
import de.robv.android.xposed.*;

/** Optional SDR-only relaxation of the content/grayscale cap, before thermal and animation.
 * No OEM fields, curves, mode settings or panel nodes are written here.
 */
final class OutdoorOpr {
    static final int OPR_MODIFIER=8192;
    static final Set<Class<?>> owners=new HashSet<>();
    static final ThreadLocal<Frame> current=new ThreadLocal<>();
    static final class Frame {
        final Object owner;final boolean auto;final Frame previous;
        Frame(Object owner,boolean auto){this.owner=owner;this.auto=auto;previous=current.get();}
    }
    static final class Attempt {
        final Object reason;final int modifiers;
        Attempt(Object reason,int modifiers){this.reason=reason;this.modifiers=modifiers;}
    }
    static void install(Class<?> owner){
        if(owners.contains(owner))return;
        List<XC_MethodHook.Unhook> hooks=new ArrayList<>();
        try{
            Class<?> reason=Class.forName("com.android.server.display.brightness.BrightnessReason",false,owner.getClassLoader());
            Method sdr=HbmAccess.method(owner,"adjustSdrBrightness",float.class,float.class,boolean.class,reason,boolean.class,boolean.class);
            Method opr=HbmAccess.method(owner,"adjustBrightnessByOpr",float.class,float.class,reason);
            Method privacy=HbmAccess.method(owner,"shouldUsePrivacyOprBrightness",boolean.class);
            Method hdr=HbmAccess.method(owner,"isHdrScene",boolean.class);
            Method get=HbmAccess.method(reason,"getModifier",int.class),set=HbmAccess.method(reason,"setModifier",void.class,int.class);
            HbmAccess.typed(owner,"mAutoBrightnessEnable",boolean.class);
            hooks.add(XposedBridge.hookMethod(sdr,new XC_MethodHook(){
                protected void beforeHookedMethod(MethodHookParam p){
                    Frame frame=new Frame(p.thisObject,Boolean.TRUE.equals(p.args[1]));
                    p.setObjectExtra("hyperlux.opr.frame",frame);current.set(frame);
                }
                protected void afterHookedMethod(MethodHookParam p){
                    Object saved=p.getObjectExtra("hyperlux.opr.frame");
                    if(saved instanceof Frame){Frame frame=(Frame)saved;if(frame.previous==null)current.remove();else current.set(frame.previous);}
                }
            }));
            hooks.add(XposedBridge.hookMethod(opr,new XC_MethodHook(){
                boolean eligible(MethodHookParam p,HookRuntime s)throws Exception{
                    Frame frame=current.get();
                    return frame!=null&&frame.owner==p.thisObject&&frame.auto&&s!=null&&s.onDisplayThread()
                        &&Boolean.TRUE.equals(HookEntry.get(p.thisObject,"mAutoBrightnessEnable"))
                        &&Boolean.FALSE.equals(hdr.invoke(p.thisObject))&&Boolean.FALSE.equals(privacy.invoke(p.thisObject))
                        &&s.outdoor.oprAllowed();
                }
                protected void beforeHookedMethod(MethodHookParam p){
                    try{HookRuntime s=HookEntry.ownerState(p.thisObject);
                        if(eligible(p,s)&&reason.isInstance(p.args[1]))p.setObjectExtra("hyperlux.opr.attempt",new Attempt(p.args[1],(Integer)get.invoke(p.args[1])));
                    }catch(Throwable unknown){/* An unknown interface retains the OEM limit. */}
                }
                protected void afterHookedMethod(MethodHookParam p){
                    if(p.hasThrowable())return;
                    Object saved=p.getObjectExtra("hyperlux.opr.attempt");if(!(saved instanceof Attempt))return;
                    try{HookRuntime s=HookEntry.ownerState(p.thisObject);if(!eligible(p,s))return;
                        Attempt attempt=(Attempt)saved;
                        float before=(Float)p.args[0],limited=(Float)p.getResult(),next=s.outdoor.opr(before,limited);
                        if(next<=limited)return;
                        // Remove only the OPR bit introduced by this invocation. Retain existing
                        // modifiers, including an OPR bit that was already present upstream.
                        int now=(Integer)get.invoke(attempt.reason);
                        set.invoke(attempt.reason,(now&~OPR_MODIFIER)|(attempt.modifiers&OPR_MODIFIER));
                        p.setResult(next);s.outdoor.oprApplied(before,limited,next);
                    }catch(Throwable unknown){/* Fail closed; downstream safety stages are untouched. */}
                }
            }));
            owners.add(owner);
        }catch(Throwable unavailable){for(XC_MethodHook.Unhook hook:hooks)hook.unhook();XposedBridge.log("HyperLux outdoor OPR unavailable: "+unavailable);}
    }
}
