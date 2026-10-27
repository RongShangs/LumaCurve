package top.rongshangs.lumacurve.refactor;

import android.app.Application;
import android.content.*;
import android.database.ContentObserver;
import android.os.*;
import android.provider.Settings;
import java.lang.reflect.*;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import top.rongshangs.lumacurve.refactor.hooking.*;

/** Bidirectional UI coordinates only. OEM input, learning and output protections still run. */
final class ControlCenterHooks {
    static boolean installed;static Context context;static Handler main;static volatile Profile profile;static String profileIdentity="";
    static volatile String error="",route="waiting_controller";static volatile boolean abiReady;
    static final Map<Object,Binding> bindings=Collections.synchronizedMap(new WeakHashMap<>());
    static final class Gesture{final Binding binding;final int position;Gesture(Binding b,int p){binding=b;position=p;}}
    static final ThreadLocal<Gesture> gesturePosition=new ThreadLocal<>();
    static final class Feedback{final Binding binding;final Profile profile;final float actual;boolean converted;Feedback(Binding b,Profile p,float a){binding=b;profile=p;actual=a;}}
    static final ThreadLocal<Feedback> feedbackPosition=new ThreadLocal<>();
    static final ThreadPoolExecutor io=new ThreadPoolExecutor(0,1,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"HyperLux-slider-ui");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    static final class Profile {
        final int mode,maximum;final ControlCenterCurve standard,v1;
        Profile(JSONObject p)throws Exception{
            if(!AppBuild.BUILD.equals(p.getString("build"))||!"coordinate_v2".equals(p.getString("kind"))||p.getInt("pid")<=0||p.getString("process_start").isEmpty())throw new IllegalArgumentException("映射代次无效");
            mode=p.getInt("mode");maximum=p.getInt("maximum");if(mode<1||mode>2||maximum<=10||maximum>65535)throw new IllegalArgumentException("节点范围无效");
            JSONObject c=p.getJSONObject("calibration");standard=ControlCenterCurve.read(c.getJSONObject("standard"));v1=c.has("oem_v1")?ControlCenterCurve.read(c.getJSONObject("oem_v1")):null;
        }
    }
    static Field field(Class<?> c,String name,Class<?> type)throws Exception{Field f=c.getDeclaredField(name);if(f.getType()!=type)throw new NoSuchFieldException(name);f.setAccessible(true);return f;}
    static Method method(Class<?> c,String name,Class<?> result,Class<?>...args)throws Exception{Method m=c.getDeclaredMethod(name,args);if(m.getReturnType()!=result)throw new NoSuchMethodException(name);m.setAccessible(true);return m;}
    static final class Abi {
        final Field external,display,app,automatic,min,max,toggles,policy,target,animator,background,update;
        final Method forward,inverse,support,logicalProgress,onChanged,setValue,add,feedback;
        final boolean nested;
        Abi(ClassLoader loader)throws Exception{
            Class<?> c=Class.forName("com.android.systemui.controlcenter.policy.MiuiBrightnessController",false,loader),t=Class.forName("com.android.systemui.controlcenter.policy.ToggleSlidersControllerImpl",false,loader),slider=Class.forName("com.android.systemui.plugins.miui.controlcenter.ToggleSliderBase",false,loader),u=Class.forName("com.android.systemui.controlcenter.policy.BrightnessUtils",false,loader),v=Class.forName("com.android.systemui.controlcenter.policy.HyperOSBrightnessPolicyV1",false,loader);
            external=field(c,"mExternalChange",boolean.class);display=field(c,"mDisplayId",int.class);app=field(c,"mContext",Context.class);automatic=field(c,"mAutomatic",boolean.class);min=field(c,"mMinimumBacklight",float.class);max=field(c,"mMaximumBacklight",float.class);toggles=field(c,"mToggleSlidersController",t);policy=field(c,"mBrightnessPolicy",v);target=field(c,"mSliderTargetValue",float.class);
            animator=c.getDeclaredField("mSliderAnimator");animator.setAccessible(true);if(!"android.animation.ValueAnimator".equals(animator.getType().getName()))throw new NoSuchFieldException("未知亮度条动画");
            background=field(c,"mBackgroundHandler",Handler.class);update=c.getDeclaredField("mUpdateSliderRunnable");update.setAccessible(true);if(!Runnable.class.isAssignableFrom(update.getType()))throw new NoSuchFieldException("未知亮度条刷新接口");
            forward=method(u,"convertGammaToLinearFloat",float.class,int.class,float.class,float.class);inverse=method(u,"convertLinearToGammaFloat",int.class,float.class,float.class,float.class);support=method(v,"getSupportV1BrightnessPolicy",boolean.class);
            logicalProgress=method(v,"brightnessToSliderProgress",int.class,float.class);if(!Modifier.isStatic(logicalProgress.getModifiers()))throw new NoSuchMethodException("未知逻辑亮度坐标");
            if(!Modifier.isStatic(forward.getModifiers())||!Modifier.isStatic(inverse.getModifiers()))throw new NoSuchMethodException("非静态亮度转换");
            onChanged=method(c,"onChanged",void.class,slider,boolean.class,boolean.class,int.class,boolean.class);setValue=method(t,"setValue",void.class,int.class,slider);add=method(c,"addToggleSlider",void.class,slider);
            Method f;boolean n;try{f=method(c,"-$$Nest$mupdateSlider",void.class,c,float.class);n=true;if(!Modifier.isStatic(f.getModifiers()))throw new NoSuchMethodException("未知回显接口");}catch(NoSuchMethodException absent){f=method(c,"updateSlider",void.class,float.class);n=false;}feedback=f;nested=n;
        }
    }
    static final class Binding {
        final WeakReference<Object> owner;final Abi abi;int minimum,maximum;boolean valid,animationPhysical;
        Binding(Object o,Abi a){owner=new WeakReference<>(o);abi=a;}
        void slider(Object slider)throws Exception{
            Method mx=slider.getClass().getMethod("getMax");if(mx.getReturnType()!=int.class)throw new NoSuchMethodException("getMax");mx.setAccessible(true);int hi=(Integer)mx.invoke(slider),lo=0;
            Method mn=null;try{mn=slider.getClass().getMethod("getMin");}catch(NoSuchMethodException optional){}
            if(mn!=null){if(mn.getReturnType()!=int.class)throw new NoSuchMethodException("getMin type");mn.setAccessible(true);lo=(Integer)mn.invoke(slider);}
            if(hi<=lo||hi>1000000||lo<0)throw new IllegalArgumentException("滑块范围无效");minimum=lo;maximum=hi;valid=true;
        }
        boolean v1()throws Exception{return (Boolean)abi.support.invoke(abi.policy.get(owner.get()));}
        void v1Range()throws Exception{if((Integer)abi.logicalProgress.invoke(null,0f)!=minimum||(Integer)abi.logicalProgress.invoke(null,1f)!=maximum)throw new IllegalArgumentException("逻辑亮度与控件坐标不一致");}
        Profile active()throws Exception{Object o=owner.get();Profile p=profile;if(o==null||!valid||p==null||abi.display.getInt(o)!=0||p.mode==1&&abi.automatic.getBoolean(o)||v1()&&p.v1==null)return null;return p;}
        float low()throws Exception{return abi.min.getFloat(owner.get());}float high()throws Exception{return abi.max.getFloat(owner.get());}
        void range()throws Exception{if(!Float.isFinite(low())||!Float.isFinite(high())||low()<0||high()>1||high()<=low())throw new IllegalArgumentException("系统亮度范围无效");}
        int input(Profile p,int position)throws Exception{
            double node=ControlCenterMapping.fraction(position,minimum,maximum);if(v1()){v1Range();return ControlCenterMapping.progress(p.v1.toInput(node),minimum,maximum);}
            range();double brightness=p.standard.toInput(node);int value=(Integer)abi.inverse.invoke(null,(float)Math.max(low(),Math.min(high(),brightness)),low(),high());ControlCenterMapping.fraction(value,minimum,maximum);return value;
        }
        int output(Profile p,int position)throws Exception{
            double node;if(v1()){v1Range();node=p.v1.toNode(ControlCenterMapping.fraction(position,minimum,maximum));}else{range();float value=(Float)abi.forward.invoke(null,position,low(),high());if(!Float.isFinite(value))throw new IllegalArgumentException("系统亮度坐标无效");node=p.standard.toNode(value);}return ControlCenterMapping.progress(node,minimum,maximum);
        }
    }
    static Binding binding(Object owner,Abi abi,Object slider)throws Exception{
        if(context==null)bind((Context)abi.app.get(owner));Object toggle=abi.toggles.get(owner);if(toggle==null)throw new IllegalStateException("亮度控件尚未就绪");Binding b=bindings.get(toggle);if(b==null||b.owner.get()!=owner){b=new Binding(owner,abi);bindings.put(toggle,b);}if(slider!=null)b.slider(slider);
        if(b.valid){boolean v=b.v1();String next=v?"oem_v1":"gamma";if(!abiReady||!next.equals(route)){abiReady=true;route=next;publish();}}return b;
    }
    static void install(ClassLoader loader){
        if(installed)return;List<XC_MethodHook.Unhook> added=new ArrayList<>();
        try{
            Abi abi=new Abi(loader);
            added.add(XposedBridge.hookMethod(abi.add,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){try{binding(p.thisObject,abi,p.args[0]);}catch(Exception failure){failed(failure);}}}));
            added.add(XposedBridge.hookMethod(abi.onChanged,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
                try{Binding b=binding(p.thisObject,abi,p.args[0]);if(abi.external.getBoolean(p.thisObject)||!Boolean.TRUE.equals(p.args[1])&&!Boolean.TRUE.equals(p.args[4]))return;Profile c=b.active();if(c==null)return;int physical=(Integer)p.args[3],original=b.input(c,physical);p.setObjectExtra("hyperlux.coordinate.previous",gesturePosition.get());p.setObjectExtra("hyperlux.coordinate.changed",true);gesturePosition.set(new Gesture(b,physical));p.args[3]=original;
                }catch(Exception failure){failed(failure);}
            }protected void afterHookedMethod(MethodHookParam p){if(Boolean.TRUE.equals(p.getObjectExtra("hyperlux.coordinate.changed"))){Gesture previous=(Gesture)p.getObjectExtra("hyperlux.coordinate.previous");if(previous==null)gesturePosition.remove();else gesturePosition.set(previous);}}}));
            added.add(XposedBridge.hookMethod(abi.setValue,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){try{Binding b=bindings.get(p.thisObject);if(b==null)return;Profile c=b.active();if(c==null)return;Gesture gesture=gesturePosition.get();if(gesture!=null&&gesture.binding==b)p.args[0]=gesture.position;else if(!b.animationPhysical)p.args[0]=b.output(c,(Integer)p.args[0]);}catch(Exception failure){failed(failure);}}}));
            added.add(XposedBridge.hookMethod(abi.feedback,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
                try{Object o=abi.nested?p.args[0]:p.thisObject;Binding b=binding(o,abi,null);Profile c=b.active();if(c==null)return;if(b.v1())b.v1Range();else b.range();float value=((Number)p.args[abi.nested?1:0]).floatValue();if(!Float.isFinite(value)||value<0||value>1)return;
                    p.setObjectExtra("hyperlux.coordinate.feedback",true);p.setObjectExtra("hyperlux.coordinate.previous_feedback",feedbackPosition.get());feedbackPosition.set(new Feedback(b,c,value));b.animationPhysical=true;
                }catch(Exception failure){failed(failure);}
            }protected void afterHookedMethod(MethodHookParam p){if(Boolean.TRUE.equals(p.getObjectExtra("hyperlux.coordinate.feedback"))){Feedback previous=(Feedback)p.getObjectExtra("hyperlux.coordinate.previous_feedback");if(previous==null)feedbackPosition.remove();else feedbackPosition.set(previous);}}}));
            // Only the verified UI feedback call is transformed; ordinary OEM brightness conversion is untouched.
            XC_MethodHook displayCoordinate=new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){Feedback f=feedbackPosition.get();if(f==null||p.hasThrowable())return;try{double node;if(!f.converted){node=f.profile.standard.toNode(f.actual);f.converted=true;}else node=abi.logicalProgress.equals(p.method)?f.profile.v1.toNode(((Number)p.args[0]).doubleValue()):f.profile.standard.toNode(((Number)p.args[0]).doubleValue());p.setResult(ControlCenterMapping.progress(node,f.binding.minimum,f.binding.maximum));}catch(Exception failure){failed(failure);}}};
            added.add(XposedBridge.hookMethod(abi.logicalProgress,displayCoordinate));added.add(XposedBridge.hookMethod(abi.inverse,displayCoordinate));
            added.add(XposedBridge.hookMethod(abi.forward,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){Feedback f=feedbackPosition.get();if(f==null||p.hasThrowable())return;try{p.setResult((float)f.profile.standard.toInput(ControlCenterMapping.fraction((Integer)p.args[0],f.binding.minimum,f.binding.maximum)));}catch(Exception failure){failed(failure);}}}));
            added.add(XposedBridge.hookMethod(Application.class.getDeclaredMethod("onCreate"),new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){if(p.thisObject instanceof Application)bind((Application)p.thisObject);}}));installed=true;
        }catch(Throwable failure){HookTransaction.rollbackAll(added);failed(failure);}
    }
    static void failed(Throwable failure){String next=failure.toString();if(!next.equals(error)){error=next;XposedBridge.log("HyperLux slider coordinates unavailable: "+next);publish();}}
    static void bind(Context c){
        if(c==null||context!=null||!"com.android.systemui".equals(c.getPackageName()))return;context=c;main=new Handler(Looper.getMainLooper());ContentObserver changed=new ContentObserver(main){public void onChange(boolean self){refresh();}};
        c.getContentResolver().registerContentObserver(Settings.Global.getUriFor(ControlCenterRoot.PROFILE),false,changed);c.getContentResolver().registerContentObserver(Settings.Global.getUriFor(ControlCenterEngine.TABLES),false,changed);c.getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),false,changed);refresh();
    }
    static void refresh(){try{io.execute(()->{try{
        String raw=Settings.Global.getString(context.getContentResolver(),ControlCenterRoot.PROFILE),tables=Settings.Global.getString(context.getContentResolver(),ControlCenterEngine.TABLES),boot=Settings.Global.getString(context.getContentResolver(),"boot_count");Profile parsed=null;
        if(raw!=null&&tables!=null&&raw.length()<260000&&tables.length()<260000){JSONObject p=new JSONObject(raw),t=new JSONObject(tables);if(AppBuild.BUILD.equals(t.optString("build"))&&p.getInt("pid")==t.getInt("pid")&&p.getString("process_start").equals(t.getString("process_start"))&&boot!=null&&boot.equals(p.getString("boot_count"))&&boot.equals(t.getString("boot_count"))&&p.getJSONObject("calibration").toString().equals(t.getJSONObject("calibration").toString()))parsed=new Profile(p);}
        String key=parsed==null?"":raw+(parsed.mode==1?":"+Settings.System.getInt(context.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,0):"");applyProfile(parsed,key);
    }catch(Exception failure){applyProfile(null,"");failed(failure);}});}catch(RejectedExecutionException ignored){}}
    static void applyProfile(Profile next,String identity){main.post(()->{
        if(!profileIdentity.equals(identity)){
            List<Binding> copy; synchronized(bindings){copy=new ArrayList<>(bindings.values());}
            for(Binding b:copy)try{Object o=b.owner.get();if(o==null)continue;Object a=b.abi.animator.get(o);if(a!=null)a.getClass().getMethod("cancel").invoke(a);b.animationPhysical=false;}catch(Exception failure){failed(failure);}
            profile=next;profileIdentity=identity;
            // Ask the original background reader to refresh UI only, never synthesize an input gesture.
            for(Binding b:copy)try{Object o=b.owner.get();if(o!=null){Handler h=(Handler)b.abi.background.get(o);Runnable r=(Runnable)b.abi.update.get(o);if(h!=null&&r!=null)h.post(r);}}catch(Exception failure){failed(failure);}
        }else profile=next;publish();
    });}
    static void publish(){if(context!=null)try{io.execute(ControlCenterHooks::publishNow);}catch(RejectedExecutionException ignored){}}
    static void publishNow(){try{Settings.Global.putString(context.getContentResolver(),ControlCenterEngine.UI,new JSONObject().put("build",AppBuild.BUILD).put("input","coordinate_v2").put("coordinate_abi",abiReady).put("route",route).put("profile_ready",profile!=null).put("detail",error).toString());}catch(Exception ignored){}}
}
