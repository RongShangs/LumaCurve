package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import java.util.*;
import org.json.*;
import top.rongshangs.lumacurve.refactor.hooking.*;

/** Proven window-sync isolation and optional sharing at the same native boundary.
 * No settings/sysfs writes, mode changes, animation overrides or background work. */
final class AppBrightnessIsolation {
    static final String SCOPED="app_override_scoped_sync",NATIVE="native_window_no_sync";
    static final Map<Class<?>,Profile> profiles=new java.util.concurrent.ConcurrentHashMap<>();
    static final Map<Class<?>,String> errors=new java.util.concurrent.ConcurrentHashMap<>();
    static final Map<Object,Session> sessions=Collections.synchronizedMap(new WeakHashMap<>());
    static final ThreadLocal<Frame> current=new ThreadLocal<>();
    static final class Session {
        String tag="";boolean active,userAdjusted;int user=-1;long generation,blocked;String last="waiting_window";
        void accept(String source,boolean valid,int serial){
            if(!valid){active=false;tag="";userAdjusted=false;generation++;last="window_released";return;}
            if(!active||!tag.equals(source)||user!=serial){generation++;userAdjusted=false;}
            tag=source;user=serial;active=true;last=userAdjusted?"user_adjustment":"window_override";
        }
    }
    static final class Frame {
        final Frame previous;final HookRuntime state;final Profile profile;final Session session;final long generation;final float value;final boolean eligible;
        Frame(Frame before,HookRuntime s,Profile p,Session session,float value,boolean eligible){previous=before;state=s;profile=p;this.session=session;generation=session==null?-1:session.generation;this.value=value;this.eligible=eligible;}
    }
    static Field field(Class<?> c,String name,Class<?> type)throws Exception{
        Field f=HookEntry.field(c,name);if(f.getType()!=type||Modifier.isStatic(f.getModifiers()))throw new IllegalStateException("应用亮度字段类型变化："+name);return f;
    }
    static Method method(Class<?> c,String name,Class<?> result,Class<?>...args)throws Exception{
        Method m=c.getDeclaredMethod(name,args);if(m.getReturnType()!=result||Modifier.isStatic(m.getModifiers()))throw new IllegalStateException("应用亮度接口类型变化："+name);m.setAccessible(true);return m;
    }
    static int constant(Class<?> c,String name)throws Exception{
        Field f=HookEntry.field(c,name);if(f.getType()!=int.class||!Modifier.isStatic(f.getModifiers()))throw new IllegalStateException("应用亮度原因类型变化");return f.getInt(null);
    }
    static boolean named(Class<?> c,String name){for(Class<?> at=c;at!=null;at=at.getSuperclass())for(Method m:at.getDeclaredMethods())if(m.getName().equals(name))return true;return false;}
    static final class Profile {
        final Class<?> owner;final Field display,controller,controllerDisplay,brightnessController;
        String route;Method sync,drag,scope,accepted,temporary,reason,tag,hdr,screen,power,applied;
        Field requestValue,requestTag,powerRequest,policy;int overrideReason,brightPolicy;
        Profile(Class<?> owner)throws Exception{
            this.owner=owner;ClassLoader cl=owner.getClassLoader();Class<?> dpc=Class.forName("com.android.server.display.DisplayPowerController",false,cl),dbc=Class.forName("com.android.server.display.brightness.DisplayBrightnessController",false,cl);
            display=field(owner,"mDisplayId",int.class);controller=field(owner,"mDisplayPowerController",dpc);controllerDisplay=field(dpc,"mDisplayId",int.class);brightnessController=field(dpc,"mDisplayBrightnessController",dbc);
            Class<?> req=Class.forName("android.hardware.display.DisplayManagerInternal$DisplayBrightnessOverrideRequest",false,cl);
            accepted=method(dbc,"updateWindowManagerBrightnessOverride",boolean.class,req);requestValue=field(req,"brightness",float.class);requestTag=field(req,"tag",CharSequence.class);
            if(!named(owner,"requestBrightnessSync")){
                if(named(owner,"consumePendingBrightnessSync")||named(dpc,"syncBrightnessToSettingAndUi"))throw new IllegalStateException("应用亮度同步结构不完整");
                route=NATIVE;return;
            }
            route=SCOPED;Class<?> why=Class.forName("com.android.server.display.brightness.BrightnessReason",false,cl),state=Class.forName("com.android.server.display.DisplayBrightnessState",false,cl);
            sync=method(owner,"requestBrightnessSync",void.class,float.class);method(owner,"consumePendingBrightnessSync",boolean.class);method(dpc,"syncBrightnessToSettingAndUi",void.class,float.class,state);
            drag=method(owner,"updateOverrideDragMode",void.class,boolean.class,float.class);scope=method(owner,"limitQRCodeBrightnessMax",float.class,float.class,float.class,why);
            temporary=method(dpc,"setTemporaryBrightness",void.class,float.class);applied=method(dbc,"isAppliedOverrideStrategy",boolean.class);
            reason=method(why,"getReason",int.class);tag=method(why,"getTag",CharSequence.class);overrideReason=constant(why,"REASON_OVERRIDE");
            if(overrideReason<=0||overrideReason==constant(why,"REASON_MANUAL")||overrideReason==constant(why,"REASON_AUTOMATIC"))throw new IllegalStateException("应用亮度原因不明确");
            Class<?> pr=Class.forName("android.hardware.display.DisplayManagerInternal$DisplayPowerRequest",false,cl),ps=Class.forName("com.android.server.display.DisplayPowerState",false,cl);
            powerRequest=field(dpc,"mPowerRequest",pr);policy=field(pr,"policy",int.class);brightPolicy=constant(pr,"POLICY_BRIGHT");
            power=method(dpc,"getDisplayPowerState",ps);screen=method(ps,"getScreenState",int.class);hdr=method(owner,"isHdrScene",boolean.class);
        }
        Object dpc(Object o)throws Exception{Object d=controller.get(o);if(d==null||display.getInt(o)!=0||controllerDisplay.getInt(d)!=0)throw new IllegalStateException("等待当前主屏对象");return d;}
    }
    static synchronized void install(Class<?> owner){
        if(profiles.containsKey(owner))return;
        try{Profile p=new Profile(owner);
            if(SCOPED.equals(p.route))try(HookTransaction tx=new HookTransaction()){
                tx.add(XposedBridge.hookMethod(p.accepted,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam m){
                    if(m.hasThrowable()||!Boolean.TRUE.equals(m.getResult()))return;
                    HookRuntime s=resolve(p,m.thisObject,false);if(s==null||!s.onDisplayThread())return;
                    try{Object d=p.dpc(s.owner),req=m.args[0];float value=req==null?Float.NaN:p.requestValue.getFloat(req);Object raw=req==null?null:p.requestTag.get(req);String source=raw instanceof CharSequence?raw.toString():"";
                        Session session=session(d);synchronized(session){session.accept(source,valid(value)&&!source.isEmpty()&&source.length()<=512,s.kernel.integer("mUserSerial"));}
                    }catch(Throwable ignored){invalidate(p,s);}
                }}));
                tx.add(XposedBridge.hookMethod(p.temporary,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam m){
                    // A genuine controller adjustment wins for this window session.
                    // Release/NaN messages do not erase that decision.
                    if(!valid((Float)m.args[0])||resolve(p,m.thisObject,true)==null)return;
                    Session session=sessions.get(m.thisObject);if(session!=null)synchronized(session){session.userAdjusted=true;session.last="user_adjustment";}
                }}));
                tx.add(XposedBridge.hookMethod(p.scope,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam m){
                    Frame before=current.get();HookRuntime s=HookEntry.ownerState(m.thisObject);Session session=null;boolean allowed=false;
                    try{if(s!=null&&s.owner.getClass()==p.owner){Object d=p.dpc(s.owner);session=sessions.get(d);Object why=m.args[2];
                        synchronized(session==null?p:session){Object source=why==null?null:p.tag.invoke(why);
                            allowed=eligible(p,s)&&session!=null&&session.active&&(!session.userAdjusted||s.brightnessControl.independentActive())&&session.user==s.kernel.integer("mUserSerial")&&source!=null&&session.tag.contentEquals((CharSequence)source)
                                &&((Integer)p.reason.invoke(why))==p.overrideReason&&Boolean.TRUE.equals(p.applied.invoke(p.brightnessController.get(d)))&&valid((Float)m.args[1]);}
                    }}catch(Throwable ignored){allowed=false;}
                    Frame frame=new Frame(before,s,p,session,(Float)m.args[1],allowed);
                    m.setObjectExtra("hyperlux.app.isolation.frame",frame);current.set(frame);
                }protected void afterHookedMethod(MethodHookParam m){Object value=m.getObjectExtra("hyperlux.app.isolation.frame");if(value instanceof Frame){Frame f=(Frame)value;try{}finally{if(f.previous==null)current.remove();else current.set(f.previous);}}}}));
                tx.add(XposedBridge.hookMethod(p.drag,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam m){if(Boolean.TRUE.equals(m.args[0])&&blocked(p,m.thisObject,(Float)m.args[1],false))m.setResult(null);}}));
                tx.add(XposedBridge.hookMethod(p.sync,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam m){if(blocked(p,m.thisObject,(Float)m.args[0],true))m.setResult(null);}}));
                profiles.put(owner,p);errors.remove(owner);tx.commit();
            }else{profiles.put(owner,p);errors.remove(owner);}
        }catch(Throwable unsupported){errors.put(owner,unsupported.toString());XposedBridge.log("HyperLux application brightness isolation unavailable: "+unsupported);}
    }
    static boolean valid(float value){return Float.isFinite(value)&&value>=0&&value<=1;}
    static Session session(Object d){synchronized(sessions){Session s=sessions.get(d);if(s==null){s=new Session();sessions.put(d,s);}return s;}}
    static HookRuntime resolve(Profile p,Object value,boolean controller){synchronized(HookEntry.states){for(HookRuntime s:HookEntry.states.values())try{if(!s.closed&&s.owner.getClass()==p.owner){Object d=p.dpc(s.owner);if((controller?d:p.brightnessController.get(d))==value)return s;}}catch(Throwable ignored){}}return null;}
    static void invalidate(Profile p,HookRuntime s){try{Session value=sessions.get(p.dpc(s.owner));if(value!=null)synchronized(value){value.accept("",false,-1);}}catch(Throwable ignored){}}
    static boolean eligible(Profile p,HookRuntime s)throws Exception{
        if(s==null||s.closed||s.changing||!s.phase.equals("active")||!s.appliesToUser()||!s.onDisplayThread()||!(s.brightnessControl.options.appIsolation||s.brightnessControl.independentActive()))return false;
        // Existing owners, HDR, DIM and screen-off retain all native decisions.
        if(!s.brightnessControl.owner.equals("none")&&!s.brightnessControl.independentActive()||Boolean.TRUE.equals(p.hdr.invoke(s.owner)))return false;
        Object d=p.dpc(s.owner),request=p.powerRequest.get(d),power=p.power.invoke(d);
        return request!=null&&power!=null&&p.policy.getInt(request)==p.brightPolicy&&((Integer)p.screen.invoke(power))==2;
    }
    static boolean blocked(Profile p,Object owner,float value,boolean count){
        Frame f=current.get();if(f==null||!f.eligible||f.profile!=p||f.state==null||f.state.owner!=owner||!valid(value)||Float.compare(value,f.value)!=0)return false;
        try{synchronized(f.session){if(f.session.generation!=f.generation||!f.session.active||f.session.userAdjusted&&!f.state.brightnessControl.independentActive()||!eligible(p,f.state))return false;
            if(count){f.session.blocked++;f.session.last="isolated";}return true;}}
        catch(Throwable unavailable){return false;}
    }
    static boolean supported(HookRuntime s)throws Exception{Profile p=profiles.get(s.owner.getClass());if(p==null)throw new NoSuchMethodException(errors.getOrDefault(s.owner.getClass(),"应用亮度接口尚未匹配"));return p.brightnessController.get(p.dpc(s.owner))!=null;}
    // Retired Beta03 flag stays readable in configuration; no window sharing runs.
    static boolean shareSupported(HookRuntime s){return false;}
    static void clear(HookRuntime s,String reason){}
    static void configure(HookRuntime s,BrightnessControlOptions before,BrightnessControlOptions next){}
    static JSONObject status(HookRuntime s)throws JSONException{
        Profile p=profiles.get(s.owner.getClass());boolean nativeIsolation=p!=null&&NATIVE.equals(p.route);
        JSONObject out=new JSONObject().put("profile",p==null?"":p.route).put("enabled",nativeIsolation||s.brightnessControl.options.appIsolation).put("native_isolated",nativeIsolation).put("module_requested",s.brightnessControl.options.appIsolation).put("detail",errors.getOrDefault(s.owner.getClass(),""));
        out.put("share_enabled",false).put("share_profile","").put("share_detail","旧版临时亮度共享已停用，保存选择保留");
        try{Object d=p==null?null:p.dpc(s.owner);Session session=d==null?null:sessions.get(d);if(session!=null)synchronized(session){out.put("window_active",session.active).put("user_adjustment_priority",session.userAdjusted).put("blocked_syncs",session.blocked).put("state",session.last);}}
        catch(Throwable pending){out.put("detail",pending.toString());}return out;
    }
}
