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

/** Bidirectional coordinates plus opt-in v3 gestures into the existing raw-panel owner. */
final class ControlCenterHooks {
    static final String UI_PROCESS_START=uiProcessStart();
    static String uiProcessStart(){try(java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.FileReader("/proc/self/stat"))){String text=reader.readLine();return text.substring(text.lastIndexOf(')')+2).split(" +")[19];}catch(Exception unavailable){return "";}}
    static boolean installed;static Context context;static Handler main;static volatile Profile profile;static String profileIdentity="";
    static volatile String error="",route="waiting_controller",gestureEvidence="";static volatile boolean abiReady,trackingLifecycle;
    static final Map<Object,Binding> bindings=Collections.synchronizedMap(new WeakHashMap<>());
    static final class Gesture{final Binding binding;final int position;Gesture(Binding b,int p){binding=b;position=p;}}
    static final ThreadLocal<Gesture> gesturePosition=new ThreadLocal<>();
    static final class Feedback{final Binding binding;final Profile profile;final float actual;boolean converted;Feedback(Binding b,Profile p,float a){binding=b;profile=p;actual=a;}}
    static final ThreadLocal<Feedback> feedbackPosition=new ThreadLocal<>();
    static final ThreadPoolExecutor io=new ThreadPoolExecutor(0,1,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"HyperLux-slider-ui");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    static final class Profile {
        final int mode,maximum;final ControlCenterCurve standard,v1;final boolean raw;final JSONObject generation;boolean holding,automatic;String holdSession="";int held=-1;long autoEpoch;final double manualNode;final int partitionUpper;
        Profile(JSONObject p)throws Exception{
            raw="raw_node_v3".equals(p.optString("kind"))||"partition_node_v4".equals(p.optString("kind"));generation=new JSONObject(p.toString());
            if(!AppBuild.BUILD.equals(p.getString("build"))||!raw&&!"coordinate_v2".equals(p.getString("kind"))||p.getInt("pid")<=0||p.getString("process_start").isEmpty())throw new IllegalArgumentException("映射代次无效");
            mode=p.getInt("mode");maximum=p.getInt("maximum");if(mode<1||mode>4||raw!=(mode>=3)||mode==4&&!"partition_node_v4".equals(p.optString("kind"))||mode==3&&!"raw_node_v3".equals(p.optString("kind"))||raw&&p.optString("physical").isEmpty()||maximum<=10||maximum>65535)throw new IllegalArgumentException("节点范围无效");
            manualNode=mode==4?p.getJSONObject("partition").getDouble("node"):0;if(mode==4&&(!Double.isFinite(manualNode)||manualNode<=0||manualNode>=1))throw new IllegalArgumentException("普通亮度区间无效");
            partitionUpper=mode==4?ControlCenterPartition.upper(p.optInt("partition_maximum",0),maximum,manualNode):maximum;
            JSONObject c=p.getJSONObject("calibration");standard=ControlCenterCurve.read(c.getJSONObject("standard"));v1=c.has("oem_v1")?ControlCenterCurve.read(c.getJSONObject("oem_v1")):null;
        }
    }
    static Field field(Class<?> c,String name,Class<?> type)throws Exception{Field f=c.getDeclaredField(name);if(f.getType()!=type)throw new NoSuchFieldException(name);f.setAccessible(true);return f;}
    static Method method(Class<?> c,String name,Class<?> result,Class<?>...args)throws Exception{Method m=c.getDeclaredMethod(name,args);if(m.getReturnType()!=result)throw new NoSuchMethodException(name);m.setAccessible(true);return m;}
    static final class Abi {
        final Field external,display,app,automatic,min,max,toggles,policy,target,animator,background,update;
        final Method forward,inverse,support,logicalProgress,onChanged,setValue,add,feedback,trackingStart,trackingStop;
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
            Method start=null,stop=null;
            try{start=method(c,"onStart",void.class,int.class);stop=method(c,"onStop",void.class,int.class);if(Modifier.isStatic(start.getModifiers())||Modifier.isStatic(stop.getModifiers()))throw new NoSuchMethodException("未知滑动生命周期");}catch(NoSuchMethodException absent){start=stop=null;}
            trackingStart=start;trackingStop=stop;
            Method f;boolean n;try{f=method(c,"-$$Nest$mupdateSlider",void.class,c,float.class);n=true;if(!Modifier.isStatic(f.getModifiers()))throw new NoSuchMethodException("未知回显接口");}catch(NoSuchMethodException absent){f=method(c,"updateSlider",void.class,float.class);n=false;}feedback=f;nested=n;
        }
    }
    static final class Binding {
        final WeakReference<Object> owner;final Abi abi;final ControlCenterPartition.Gate partition=new ControlCenterPartition.Gate();int minimum,maximum,rawThumb;long rawThumbUntil;double feedbackNode=Double.NaN;android.widget.Toast hint;boolean valid,animationPhysical;
        Binding(Object o,Abi a){owner=new WeakReference<>(o);abi=a;}
        void slider(Object slider)throws Exception{
            Method mx=slider.getClass().getMethod("getMax");if(mx.getReturnType()!=int.class)throw new NoSuchMethodException("getMax");mx.setAccessible(true);int hi=(Integer)mx.invoke(slider),lo=0;
            Method mn=null;try{mn=slider.getClass().getMethod("getMin");}catch(NoSuchMethodException optional){}
            if(mn!=null){if(mn.getReturnType()!=int.class)throw new NoSuchMethodException("getMin type");mn.setAccessible(true);lo=(Integer)mn.invoke(slider);}
            if(hi<=lo||hi>1000000||lo<0)throw new IllegalArgumentException("滑块范围无效");if(valid&&(minimum!=lo||maximum!=hi)){partition.reset();rawThumbUntil=0;}minimum=lo;maximum=hi;valid=true;
        }
        boolean v1()throws Exception{return (Boolean)abi.support.invoke(abi.policy.get(owner.get()));}
        void v1Range()throws Exception{if((Integer)abi.logicalProgress.invoke(null,0f)!=minimum||(Integer)abi.logicalProgress.invoke(null,1f)!=maximum)throw new IllegalArgumentException("逻辑亮度与控件坐标不一致");}
        Profile active()throws Exception{Object o=owner.get();Profile p=profile;if(o==null||!valid||p==null||abi.display.getInt(o)!=0||p.mode==1&&abi.automatic.getBoolean(o)||(!p.raw||p.mode==4)&&v1()&&p.v1==null)return null;return p;}
        float low()throws Exception{return abi.min.getFloat(owner.get());}float high()throws Exception{return abi.max.getFloat(owner.get());}
        void range()throws Exception{if(!Float.isFinite(low())||!Float.isFinite(high())||low()<0||high()>1||high()<=low())throw new IllegalArgumentException("系统亮度范围无效");}
        int input(Profile p,int position)throws Exception{
            return inputNode(p,ControlCenterMapping.fraction(position,minimum,maximum));
        }
        int inputNode(Profile p,double node)throws Exception{if(v1()){v1Range();return ControlCenterMapping.progress(p.v1.toInput(node),minimum,maximum);}
            range();double brightness=p.standard.toInput(node);int value=(Integer)abi.inverse.invoke(null,(float)Math.max(low(),Math.min(high(),brightness)),low(),high());ControlCenterMapping.fraction(value,minimum,maximum);return value;
        }
        int output(Profile p,int position)throws Exception{
            double node;if(v1()){v1Range();node=p.v1.toNode(ControlCenterMapping.fraction(position,minimum,maximum));}else{range();float value=(Float)abi.forward.invoke(null,position,low(),high());if(!Float.isFinite(value))throw new IllegalArgumentException("系统亮度坐标无效");node=p.standard.toNode(value);}return ControlCenterMapping.progress(position(p,node),minimum,maximum);
        }
        double position(Profile p,double node){return p.mode==4?ControlCenterPartition.position(node,p.manualNode,p.partitionUpper,p.maximum):node;}
        double node(Profile p,double position){return p.mode==4?ControlCenterPartition.node(position,p.manualNode,p.partitionUpper,p.maximum):position;}
        void showThumb(int position)throws Exception{Object o=owner.get();boolean external=abi.external.getBoolean(o);abi.external.setBoolean(o,true);try{abi.setValue.invoke(abi.toggles.get(o),position,null);}finally{abi.external.setBoolean(o,external);}}
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
            // Optional lifecycle evidence is independent of progress notifications.
            // Keep the original onChanged stop path for controllers without this ABI.
            if(abi.trackingStart!=null&&abi.trackingStop!=null){
                try(HookTransaction lifecycle=new HookTransaction()){
                    lifecycle.add(XposedBridge.hookMethod(abi.trackingStart,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){try{Binding b=binding(p.thisObject,abi,null);Profile c=b.active();if(!abi.external.getBoolean(p.thisObject)&&c!=null&&c.mode==4){b.partition.beginStroke();recordGesture(b,"start",(Integer)p.args[0],-1);}}catch(Exception failure){failed(failure);}}}));
                    lifecycle.add(XposedBridge.hookMethod(abi.trackingStop,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){try{Binding b=binding(p.thisObject,abi,null);Profile c=b.active();if(!abi.external.getBoolean(p.thisObject)&&c!=null&&c.mode==4){b.partition.endStroke(SystemClock.elapsedRealtime());recordGesture(b,"stop",(Integer)p.args[0],-1);}}catch(Exception failure){failed(failure);}}}));
                    lifecycle.commitTo(added);trackingLifecycle=true;
                }catch(Throwable unavailable){trackingLifecycle=false;}
            }
            added.add(XposedBridge.hookMethod(abi.onChanged,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
                try{Binding b=binding(p.thisObject,abi,p.args[0]);if(abi.external.getBoolean(p.thisObject)||!Boolean.TRUE.equals(p.args[1])&&!Boolean.TRUE.equals(p.args[4]))return;Profile c=b.active();if(c==null)return;int physical=(Integer)p.args[3],shown=physical,original;double fraction=ControlCenterMapping.fraction(physical,b.minimum,b.maximum);boolean last=Boolean.TRUE.equals(p.args[4]);
                    if(c.mode==4){int boundary=ControlCenterMapping.progress(ControlCenterPartition.ORDINARY_SPAN,b.minimum,b.maximum);double nodeFraction=b.node(c,fraction);if(boundary<=b.minimum||boundary>=b.maximum)throw new IllegalArgumentException("普通亮度区间不足以分区");
                        if(!b.partition.stroke&&Boolean.TRUE.equals(p.args[1])&&!last&&physical>boundary&&b.feedbackNode>c.manualNode&&abi.automatic.getBoolean(p.thisObject)&&!c.holding)b.partition.fromAutomaticHigh(SystemClock.elapsedRealtime());
                        b.feedbackNode=Double.NaN;
                        boolean wasRaw=b.partition.raw;int action=b.partition.move(physical,boundary,Boolean.TRUE.equals(p.args[1]),last,SystemClock.elapsedRealtime(),c.holding);
                        if(action==ControlCenterPartition.Gate.PROMPT||action==ControlCenterPartition.Gate.RELEASE||action==ControlCenterPartition.Gate.RAW&&!wasRaw||last)recordGesture(b,"changed",physical,action);
                        if(action==ControlCenterPartition.Gate.RAW){b.rawThumb=physical;b.rawThumbUntil=SystemClock.elapsedRealtime()+1000;b.showThumb(physical);sendNode(c,ControlCenterMapping.target(nodeFraction,c.maximum),last,"raw",b.partition);p.setResult(null);return;}
                        shown=Math.min(physical,boundary);b.rawThumb=shown;b.rawThumbUntil=SystemClock.elapsedRealtime()+1000;b.showThumb(shown);
                        if(action==ControlCenterPartition.Gate.PROMPT){if(b.hint!=null)b.hint.cancel();android.widget.Toast hint=android.widget.Toast.makeText(context,("zh".equals(Locale.getDefault().getLanguage())?"再次滑动突破手动亮度限制":"Swipe again to exceed the manual brightness limit"),android.widget.Toast.LENGTH_SHORT);b.hint=hint;hint.show();main.postDelayed(()->{hint.cancel();if(b.hint==hint)b.hint=null;},ControlCenterPartition.WINDOW_MS);}
                        if(action==ControlCenterPartition.Gate.RELEASE)sendNode(c,Math.min(ControlCenterMapping.target(nodeFraction,c.maximum),(int)Math.ceil(c.manualNode*c.maximum)),true,"release",b.partition);
                        original=b.inputNode(c,Math.min(nodeFraction,c.manualNode));
                    }else if(c.raw){b.rawThumb=physical;b.rawThumbUntil=SystemClock.elapsedRealtime()+1000;sendNode(c,ControlCenterMapping.target(fraction,c.maximum),last);p.setResult(null);return;}else original=b.input(c,physical);p.setObjectExtra("hyperlux.coordinate.previous",gesturePosition.get());p.setObjectExtra("hyperlux.coordinate.changed",true);gesturePosition.set(new Gesture(b,shown));p.args[3]=original;
                }catch(Exception failure){failed(failure);}
            }protected void afterHookedMethod(MethodHookParam p){if(Boolean.TRUE.equals(p.getObjectExtra("hyperlux.coordinate.changed"))){Gesture previous=(Gesture)p.getObjectExtra("hyperlux.coordinate.previous");if(previous==null)gesturePosition.remove();else gesturePosition.set(previous);}}}));
            added.add(XposedBridge.hookMethod(abi.setValue,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){try{Binding b=bindings.get(p.thisObject);if(b==null)return;Profile c=b.active();if(c==null)return;if(c.raw&&SystemClock.elapsedRealtime()<b.rawThumbUntil){p.args[0]=b.rawThumb;return;}if(c.raw&&c.holding&&!b.partition.releasePending){p.args[0]=ControlCenterMapping.progress(b.position(c,c.held/(double)c.maximum),b.minimum,b.maximum);return;}Gesture gesture=gesturePosition.get();if(gesture!=null&&gesture.binding==b)p.args[0]=gesture.position;else if(!b.animationPhysical)p.args[0]=b.output(c,(Integer)p.args[0]);}catch(Exception failure){failed(failure);}}}));
            added.add(XposedBridge.hookMethod(abi.feedback,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
                try{Object o=abi.nested?p.args[0]:p.thisObject;Binding b=binding(o,abi,null);Profile c=b.active();if(c==null)return;if(!c.raw){if(b.v1())b.v1Range();else b.range();}float value=((Number)p.args[abi.nested?1:0]).floatValue();if(!Float.isFinite(value)||value<0||value>1)return;
                    b.feedbackNode=c.mode==4&&!c.holding?c.standard.toNode(value):Double.NaN;
                    p.setObjectExtra("hyperlux.coordinate.feedback",true);p.setObjectExtra("hyperlux.coordinate.previous_feedback",feedbackPosition.get());feedbackPosition.set(new Feedback(b,c,value));b.animationPhysical=true;
                }catch(Exception failure){failed(failure);}
            }protected void afterHookedMethod(MethodHookParam p){if(Boolean.TRUE.equals(p.getObjectExtra("hyperlux.coordinate.feedback"))){Feedback previous=(Feedback)p.getObjectExtra("hyperlux.coordinate.previous_feedback");if(previous==null)feedbackPosition.remove();else feedbackPosition.set(previous);}}}));
            // Only the verified UI feedback call is transformed; ordinary OEM brightness conversion is untouched.
            XC_MethodHook displayCoordinate=new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){Feedback f=feedbackPosition.get();if(f==null||p.hasThrowable())return;try{double node;if(f.profile.raw&&f.profile.holding&&!f.binding.partition.releasePending){node=f.profile.held/(double)f.profile.maximum;}else if(f.profile.raw){node=f.profile.standard.toNode(f.actual);}else if(!f.converted){node=f.profile.standard.toNode(f.actual);f.converted=true;}else node=abi.logicalProgress.equals(p.method)?f.profile.v1.toNode(((Number)p.args[0]).doubleValue()):f.profile.standard.toNode(((Number)p.args[0]).doubleValue());p.setResult(ControlCenterMapping.progress(f.binding.position(f.profile,node),f.binding.minimum,f.binding.maximum));}catch(Exception failure){failed(failure);}}};
            added.add(XposedBridge.hookMethod(abi.logicalProgress,displayCoordinate));added.add(XposedBridge.hookMethod(abi.inverse,displayCoordinate));
            added.add(XposedBridge.hookMethod(abi.forward,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){Feedback f=feedbackPosition.get();if(f==null||p.hasThrowable())return;try{p.setResult((float)f.profile.standard.toInput(f.binding.node(f.profile,ControlCenterMapping.fraction((Integer)p.args[0],f.binding.minimum,f.binding.maximum))));}catch(Exception failure){failed(failure);}}}));
            added.add(XposedBridge.hookMethod(Application.class.getDeclaredMethod("onCreate"),new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){if(p.thisObject instanceof Application)bind((Application)p.thisObject);}}));installed=true;
        }catch(Throwable failure){trackingLifecycle=false;HookTransaction.rollbackAll(added);failed(failure);}
    }
    static void failed(Throwable failure){String next=failure.toString();if(!next.equals(error)){error=next;XposedBridge.log("HyperLux slider coordinates unavailable: "+next);publish();}}
    static void bind(Context c){
        if(c==null||context!=null||!"com.android.systemui".equals(c.getPackageName()))return;context=c;main=new Handler(Looper.getMainLooper());ContentObserver changed=new ContentObserver(main){public void onChange(boolean self){refresh();}};
        c.getContentResolver().registerContentObserver(Settings.Global.getUriFor(ControlCenterRoot.PROFILE),false,changed);c.getContentResolver().registerContentObserver(Settings.Global.getUriFor(ControlCenterEngine.TABLES),false,changed);c.getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),false,changed);refresh();
    }
    static void refresh(){try{io.execute(()->{try{
        String raw=Settings.Global.getString(context.getContentResolver(),ControlCenterRoot.PROFILE),tables=Settings.Global.getString(context.getContentResolver(),ControlCenterEngine.TABLES),boot=Settings.Global.getString(context.getContentResolver(),"boot_count");Profile parsed=null;
        if(raw!=null&&tables!=null&&raw.length()<260000&&tables.length()<260000){JSONObject p=new JSONObject(raw),t=new JSONObject(tables);if(t.optBoolean("enabled")&&p.optInt("user_serial",-1)==0&&p.getInt("user_serial")==t.optInt("user_serial",-1)&&!p.optString("revision").isEmpty()&&p.getString("revision").equals(t.optString("revision"))&&AppBuild.BUILD.equals(t.optString("build"))&&p.getInt("pid")==t.getInt("pid")&&p.getString("process_start").equals(t.getString("process_start"))&&boot!=null&&boot.equals(p.getString("boot_count"))&&boot.equals(t.getString("boot_count"))&&p.getJSONObject("calibration").toString().equals(t.getJSONObject("calibration").toString())&&(!("raw_node_v3".equals(p.optString("kind"))||"partition_node_v4".equals(p.optString("kind")))||p.optInt("maximum")==t.optInt("maximum")&&p.optString("physical").equals(t.optString("physical")))){parsed=new Profile(p);if(parsed.mode==4&&(!p.getJSONObject("partition").toString().equals(t.getJSONObject("partition").toString())||p.optInt("partition_maximum",0)!=t.optInt("partition_maximum",0)))throw new IllegalStateException("普通亮度区间已改变");parsed.automatic=Settings.System.getInt(context.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,1)!=0;JSONObject held=t.optJSONObject("raw_control");if(held!=null)parsed.autoEpoch=held.optLong("auto_epoch",0);if(parsed.raw&&held!=null&&held.optBoolean("active")&&held.optBoolean("confirmed")&&Settings.System.getInt(context.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,1)==0){parsed.holdSession=held.optString("session");parsed.held=held.optInt("target",-1);parsed.holding=parsed.held>=10&&parsed.held<=parsed.maximum;}}}
        String key=parsed==null?"":raw+(parsed.mode==1||parsed.raw?":"+Settings.System.getInt(context.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,0):"");applyProfile(parsed,key);
    }catch(Exception failure){applyProfile(null,"");failed(failure);}});}catch(RejectedExecutionException ignored){}}
    static boolean sameGeneration(Profile a,Profile b){if(a==null||b==null)return false;for(String key:new String[]{"build","pid","process_start","boot_count","revision","user_serial","maximum","physical","mode"})if(!String.valueOf(a.generation.opt(key)).equals(String.valueOf(b.generation.opt(key))))return false;return true;}
    static void applyProfile(Profile next,String identity){main.post(()->{
        boolean resetPartition=!sameGeneration(profile,next)||profile!=null&&next!=null&&(profile.autoEpoch!=next.autoEpoch||!profile.automatic&&next.automatic);
        if(!profileIdentity.equals(identity)||resetPartition){
            List<Binding> copy; synchronized(bindings){copy=new ArrayList<>(bindings.values());}
            for(Binding b:copy)try{Object o=b.owner.get();if(o==null)continue;Object a=b.abi.animator.get(o);if(a!=null)a.getClass().getMethod("cancel").invoke(a);b.animationPhysical=false;if(resetPartition){b.feedbackNode=Double.NaN;b.rawThumbUntil=0;b.partition.reset();if(b.hint!=null){b.hint.cancel();b.hint=null;}}}catch(Exception failure){failed(failure);}
            profile=next;profileIdentity=identity;if(resetPartition)gestureEvidence="";
            // Ask the original background reader to refresh UI only, never synthesize an input gesture.
            for(Binding b:copy)try{Object o=b.owner.get();if(o!=null){Handler h=(Handler)b.abi.background.get(o);Runnable r=(Runnable)b.abi.update.get(o);if(h!=null&&r!=null)h.post(r);}}catch(Exception failure){failed(failure);}
        }else profile=next;publish();
    });}
    static final String NODE_REQUEST="hyperlux_control_center_node_request_v3";
    static final String nodeSession="cc_"+java.util.UUID.randomUUID().toString();
    static long nodeSequence;static JSONObject pendingNode,nextNodeWrite;static boolean nodeQueued,nodeWriting;
    static final Runnable nodeFlush=()->flushNode();
    static synchronized void sendNode(Profile p,int target,boolean last)throws Exception{sendNode(p,target,last,"raw",null);}
    static synchronized void sendNode(Profile p,int target,boolean last,String action,ControlCenterPartition.Gate gate)throws Exception{
        JSONObject request=new JSONObject();for(String key:new String[]{"build","pid","process_start","boot_count","revision","user_serial","maximum","physical","mode"})request.put(key,p.generation.get(key));request.put("auto_epoch",p.autoEpoch).put("id",nodeSession+"_"+(++nodeSequence)).put("session",nodeSession).put("target",target).put("elapsed",SystemClock.elapsedRealtime());
        request.put("action",action).put("previous_session",p.holdSession);if(gate!=null)request.put("partition_since",gate.since).put("partition_confirmed",gate.confirmed).put("partition_automatic_high",gate.automaticHigh);
        pendingNode=request;
        if(last){main.removeCallbacks(nodeFlush);nodeQueued=false;flushNode();}
        else if(!nodeQueued){nodeQueued=true;main.postDelayed(nodeFlush,32);}
    }
    static synchronized void flushNode(){
        JSONObject request=pendingNode;pendingNode=null;nodeQueued=false;if(request==null)return;
        nextNodeWrite=request;if(nodeWriting)return;nodeWriting=true;
        try{io.execute(ControlCenterHooks::writeNodes);}catch(RejectedExecutionException failure){nodeWriting=false;nextNodeWrite=null;if(SystemClock.elapsedRealtime()-request.optLong("elapsed",0)<3000){pendingNode=request;nodeQueued=true;main.postDelayed(nodeFlush,100);}else failed(failure);}
    }
    static void writeNodes(){
        for(;;){
            JSONObject request;synchronized(ControlCenterHooks.class){request=nextNodeWrite;nextNodeWrite=null;if(request==null){nodeWriting=false;return;}}
            try{Profile current=profile;if(current==null||!current.raw)continue;
                boolean matches=true;for(String key:new String[]{"build","pid","process_start","boot_count","revision","user_serial","maximum","physical","mode"})if(!String.valueOf(current.generation.opt(key)).equals(String.valueOf(request.opt(key))))matches=false;
                if(!matches)continue;
                if(!Settings.Global.putString(context.getContentResolver(),NODE_REQUEST,request.toString()))throw new IllegalStateException("节点请求未写入");
            }catch(Exception failure){failed(failure);}
        }
    }
    static void publish(){if(context!=null)try{io.execute(ControlCenterHooks::publishNow);}catch(RejectedExecutionException ignored){}}
    static void recordGesture(Binding b,String event,int position,int action){try{ControlCenterPartition.Gate g=b.partition;gestureEvidence=new JSONObject().put("event",event).put("elapsed",SystemClock.elapsedRealtime()).put("position",position).put("minimum",b.minimum).put("maximum",b.maximum).put("boundary",ControlCenterMapping.progress(ControlCenterPartition.ORDINARY_SPAN,b.minimum,b.maximum)).put("action",action).put("since",g.since).put("confirmed",g.confirmed).put("released",g.released).put("stroke",g.stroke).put("raw",g.raw).toString();publish();}catch(Exception ignored){}}
    static void publishNow(){try{String evidence=gestureEvidence;Settings.Global.putString(context.getContentResolver(),ControlCenterEngine.UI,new JSONObject().put("build",AppBuild.BUILD).put("pid",android.os.Process.myPid()).put("process_start",UI_PROCESS_START).put("input",profile!=null&&profile.mode==4?"partition_node_v4":profile!=null&&profile.raw?"raw_node_v3":"coordinate_v2").put("coordinate_abi",abiReady).put("route",route).put("tracking_lifecycle",trackingLifecycle?"controller_start_stop":"on_changed_stop").put("gesture",evidence.isEmpty()?JSONObject.NULL:new JSONObject(evidence)).put("profile_ready",profile!=null).put("detail",error).toString());}catch(Exception ignored){}}
}
