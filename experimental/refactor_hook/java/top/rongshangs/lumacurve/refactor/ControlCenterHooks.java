package top.rongshangs.lumacurve.refactor;

import android.animation.ValueAnimator;
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
    static boolean installed;static volatile boolean observersReady;static boolean observerQueued;static int observerAttempts;static Context context;static Handler main;static volatile Profile profile;static String profileIdentity="";
    static final MappingRecoveryState.Gate recoveryGate=new MappingRecoveryState.Gate();static volatile String recoverySignal="not_requested";
    static volatile String error="",route="waiting_controller",gestureEvidence="",coordinateEvidence="";
    static volatile boolean abiReady,widgetReady,trackingLifecycle;
    private static long lastEvidencePublication;
    private static final String OBSERVED_V1="oem_v1_native_progress";
    private static final int V1_LOGICAL_PROGRESS_MAX=16383;
    static final Map<Object,Binding> bindings=Collections.synchronizedMap(new WeakHashMap<>());
    static final class Gesture{final Binding binding;final int position;Gesture(Binding b,int p){binding=b;position=p;}}
    static final ThreadLocal<Gesture> gesturePosition=new ThreadLocal<>();
    static final class Feedback{
        final Binding binding;
        final Profile profile;
        final float actual;
        final boolean observedLogical;
        final float targetLevel,realtimeLevel;
        boolean converted,animating,sampled;
        ValueAnimator animator;
        int start,target;
        Feedback(Binding binding,Profile profile,float actual,boolean observedLogical,Object owner)throws IllegalAccessException{
            this.binding=binding;this.profile=profile;this.actual=actual;this.observedLogical=observedLogical;
            boolean nativeLevels=observedLogical&&binding.abi.hasNativeLevels();
            targetLevel=nativeLevels?binding.abi.sliderTarget.getFloat(owner):Float.NaN;
            realtimeLevel=nativeLevels?binding.abi.sliderRealtime.getFloat(owner):Float.NaN;
        }
        float logicalLevel(){
            // The animated endpoint is the target. Initial direct feedback prefers a positive realtime value.
            return animating||realtimeLevel<=0f?targetLevel:realtimeLevel;
        }
        void captureAnimation(Object candidate,Object values){
            if(!observedLogical||animating||!(candidate instanceof ValueAnimator)||!(values instanceof int[]))return;
            int[] points=(int[])values;
            if(points.length!=2)return;
            animator=(ValueAnimator)candidate;
            start=points[0];
            target=points[1];
        }
    }
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
    static Field field(Class<?> c,String name,Class<?> type)throws Exception{Field f=c.getDeclaredField(name);if(f.getType()!=type||Modifier.isStatic(f.getModifiers()))throw new NoSuchFieldException(name);f.setAccessible(true);return f;}
    static Method method(Class<?> c,String name,Class<?> result,Class<?>...args)throws Exception{Method m=c.getDeclaredMethod(name,args);if(m.getReturnType()!=result)throw new NoSuchMethodException(name);m.setAccessible(true);return m;}
    private static Field controllerObject(Class<?> owner,String name)throws Exception{
        Field f=owner.getDeclaredField(name);
        if(Modifier.isStatic(f.getModifiers())||f.getType().isPrimitive()||f.getType().isArray()||f.getType()==Object.class)
            throw new IllegalArgumentException("未知的亮度控制器关联字段 "+name);
        f.setAccessible(true);
        return f;
    }
    private static Method logicalProgress(Class<?> policy)throws Exception{
        Method progress;
        try{progress=policy.getDeclaredMethod("brightnessToSliderProgress",float.class);}
        catch(NoSuchMethodException absent){return null;}
        if(progress.getReturnType()!=int.class||!Modifier.isStatic(progress.getModifiers()))
            throw new IllegalArgumentException("未知逻辑亮度坐标签名");
        progress.setAccessible(true);
        return progress;
    }
    private static Field optionalLevel(Class<?> controller,String name)throws Exception{
        try{return field(controller,name,float.class);}
        catch(NoSuchFieldException unavailable){return null;}
    }
    static final class Abi {
        final Field external,display,app,automatic,min,max,toggles,policy,animator,background,update;
        final Field sliderTarget,sliderRealtime;
        final Method forward,inverse,support,logicalProgress,onChanged,setValue,add,feedback,trackingStart,trackingStop;
        final boolean nested;
        Abi(ClassLoader loader)throws Exception{
            Class<?> c=Class.forName("com.android.systemui.controlcenter.policy.MiuiBrightnessController",false,loader);
            Class<?> slider=Class.forName("com.android.systemui.plugins.miui.controlcenter.ToggleSliderBase",false,loader);
            Class<?> u=Class.forName("com.android.systemui.controlcenter.policy.BrightnessUtils",false,loader);
            external=field(c,"mExternalChange",boolean.class);display=field(c,"mDisplayId",int.class);app=field(c,"mContext",Context.class);
            automatic=field(c,"mAutomatic",boolean.class);min=field(c,"mMinimumBacklight",float.class);max=field(c,"mMaximumBacklight",float.class);
            toggles=controllerObject(c,"mToggleSlidersController");policy=controllerObject(c,"mBrightnessPolicy");
            animator=field(c,"mSliderAnimator",ValueAnimator.class);
            background=field(c,"mBackgroundHandler",Handler.class);update=c.getDeclaredField("mUpdateSliderRunnable");update.setAccessible(true);if(!Runnable.class.isAssignableFrom(update.getType()))throw new NoSuchFieldException("未知亮度条刷新接口");
            forward=method(u,"convertGammaToLinearFloat",float.class,int.class,float.class,float.class);inverse=method(u,"convertLinearToGammaFloat",int.class,float.class,float.class,float.class);
            support=method(policy.getType(),"getSupportV1BrightnessPolicy",boolean.class);
            logicalProgress=logicalProgress(policy.getType());
            sliderTarget=logicalProgress==null?optionalLevel(c,"mSliderTargetValue"):null;
            sliderRealtime=logicalProgress==null?optionalLevel(c,"mRealtimeValue"):null;
            if(!Modifier.isStatic(forward.getModifiers())||!Modifier.isStatic(inverse.getModifiers()))throw new NoSuchMethodException("非静态亮度转换");
            onChanged=method(c,"onChanged",void.class,slider,boolean.class,boolean.class,int.class,boolean.class);setValue=method(toggles.getType(),"setValue",void.class,int.class,slider);add=method(c,"addToggleSlider",void.class,slider);
            if(Modifier.isStatic(support.getModifiers())||Modifier.isStatic(setValue.getModifiers())||
                    Modifier.isStatic(onChanged.getModifiers())||Modifier.isStatic(add.getModifiers()))
                throw new IllegalArgumentException("未知的亮度条实例接口");
            Method start=null,stop=null;
            try{start=method(c,"onStart",void.class,int.class);stop=method(c,"onStop",void.class,int.class);if(Modifier.isStatic(start.getModifiers())||Modifier.isStatic(stop.getModifiers()))throw new NoSuchMethodException("未知滑动生命周期");}catch(NoSuchMethodException absent){start=stop=null;}
            trackingStart=start;trackingStop=stop;
            Method f;boolean n;try{f=method(c,"-$$Nest$mupdateSlider",void.class,c,float.class);n=true;if(!Modifier.isStatic(f.getModifiers()))throw new NoSuchMethodException("未知回显接口");}catch(NoSuchMethodException absent){f=method(c,"updateSlider",void.class,float.class);n=false;}feedback=f;nested=n;
        }
        boolean hasNativeLevels(){return sliderTarget!=null&&sliderRealtime!=null;}
    }
    /** Evidence from the original controller's logical source or the independent device calibration. */
    static final class ObservedProgress {
        int first=-1;
        boolean ready;
        String evidence="waiting_feedback";
        void reset(){first=-1;ready=false;evidence="waiting_feedback";}
        boolean matches(float logical,int progress,int minimum,int maximum){
            if(!Float.isFinite(logical))return mismatch("invalid_native_level");
            if(progress<minimum||progress>maximum)return mismatch("outside_widget_range");
            float bounded=Math.max(0f,Math.min(1f,logical));
            int expected=minimum+Math.round(bounded*(maximum-minimum));
            if(Math.abs(progress-expected)>1)return mismatch("native_level_mismatch");
            // The fixed V1 widget range and an exact interior sample independently
            // verify the inlined conversion, even while ambient brightness stays stable.
            if(minimum==0&&maximum==V1_LOGICAL_PROGRESS_MAX&&
                    progress>minimum&&progress<maximum&&progress==expected){
                ready=true;
                evidence="confirmed";
                return true;
            }
            return observe(progress,minimum,maximum);
        }
        boolean matches(Profile profile,double node,int progress,int minimum,int maximum){
            if(progress<minimum||progress>maximum)return mismatch("outside_widget_range");
            ControlCenterCurve logical=profile.v1;
            if(logical==null)return false;
            if(profile.holding||profile.mode==4&&node>profile.manualNode||
                    node<=logical.node[0]||node>=logical.node[logical.node.length-1])return ready;
            int lower=Math.max(minimum,progress-1),upper=Math.min(maximum,progress+1);
            double low=logical.toNode(ControlCenterMapping.fraction(lower,minimum,maximum));
            double high=logical.toNode(ControlCenterMapping.fraction(upper,minimum,maximum));
            double tolerance=2*ControlCenterCurve.ERROR;
            if(node<low-tolerance||node>high+tolerance)return mismatch("calibration_mismatch");
            return observe(progress,minimum,maximum);
        }
        private boolean mismatch(String reason){
            if(!ready){first=-1;evidence=reason;}
            return false;
        }
        private boolean observe(int progress,int minimum,int maximum){
            if(!ready&&progress>minimum&&progress<maximum){
                if(first<0)first=progress;
                ready=Math.abs(progress-first)>=Math.max(2,(maximum-minimum)/16);
                evidence=ready?"confirmed":"waiting_second_progress";
            }
            return ready;
        }
    }
    static final class SliderBounds {
        final Method maximum,minimum;
        final Exception unavailable;
        SliderBounds(Class<?> type)throws Exception{
            unavailable=null;
            maximum=type.getMethod("getMax");
            if(maximum.getReturnType()!=int.class||Modifier.isStatic(maximum.getModifiers()))throw new NoSuchMethodException("getMax");
            maximum.setAccessible(true);
            Method min;
            try{min=type.getMethod("getMin");}catch(NoSuchMethodException absent){min=null;}
            if(min!=null){
                if(min.getReturnType()!=int.class||Modifier.isStatic(min.getModifiers()))throw new NoSuchMethodException("getMin");
                min.setAccessible(true);
            }
            minimum=min;
        }
        SliderBounds(Exception failure){maximum=minimum=null;unavailable=failure;}
    }
    static final ClassValue<SliderBounds> sliderBounds=new ClassValue<SliderBounds>(){
        protected SliderBounds computeValue(Class<?> type){
            try{return new SliderBounds(type);}
            catch(Exception unavailable){return new SliderBounds(unavailable);}
        }
    };
    static final class Binding {
        final WeakReference<Object> owner;final Abi abi;final ControlCenterPartition.Gate partition=new ControlCenterPartition.Gate();int minimum,maximum,rawThumb;long rawThumbUntil;double feedbackNode=Double.NaN;android.widget.Toast hint;boolean valid,animationPhysical;
        final ObservedProgress observed=new ObservedProgress();
        Binding(Object o,Abi a){owner=new WeakReference<>(o);abi=a;}
        void slider(Object slider)throws Exception{
            SliderBounds bounds=sliderBounds.get(slider.getClass());
            if(bounds.unavailable!=null)throw bounds.unavailable;
            int hi=(Integer)bounds.maximum.invoke(slider);
            int lo=bounds.minimum==null?0:(Integer)bounds.minimum.invoke(slider);
            if(hi<=lo||hi>1000000||lo<0)throw new IllegalArgumentException("滑块范围无效");if(valid&&(minimum!=lo||maximum!=hi)){partition.reset();observed.reset();rawThumbUntil=0;}minimum=lo;maximum=hi;valid=true;
        }
        boolean v1()throws Exception{return (Boolean)abi.support.invoke(abi.policy.get(owner.get()));}
        boolean observesLogical()throws Exception{return abi.logicalProgress==null&&v1();}
        void v1Range()throws Exception{
            if(abi.logicalProgress==null){
                if(!observed.ready)throw new IllegalStateException("原生亮度条坐标尚未核验");
            }else if((Integer)abi.logicalProgress.invoke(null,0f)!=minimum||(Integer)abi.logicalProgress.invoke(null,1f)!=maximum){
                throw new IllegalArgumentException("逻辑亮度与控件坐标不一致");
            }
        }
        Profile eligible()throws Exception{if(!observersReady)return null;Object o=owner.get();Profile p=profile;if(p!=null&&p.raw&&!ControlCenterScreen.allowsNodes(context)){partition.reset();rawThumbUntil=0;feedbackNode=Double.NaN;animationPhysical=false;return null;}if(o==null||!valid||p==null||abi.display.getInt(o)!=0||p.mode==1&&abi.automatic.getBoolean(o)||(!p.raw||p.mode==4)&&v1()&&p.v1==null)return null;return p;}
        Profile active()throws Exception{
            Profile p=eligible();
            return p!=null&&(!observesLogical()||observed.ready)?p:null;
        }
        int nativeFeedback(Feedback frame,int nativeProgress)throws Exception{
            Profile p=frame.profile;
            if(eligible()!=p||!observesLogical())return nativeProgress;
            frame.sampled=true;
            double node=p.standard.toNode(frame.actual);
            boolean verified=abi.hasNativeLevels()
                ?observed.matches(frame.logicalLevel(),nativeProgress,minimum,maximum)
                :observed.matches(p,node,nativeProgress,minimum,maximum);
            reportCoordinates(this,true);
            if(!verified)return nativeProgress;
            if(p.raw&&SystemClock.elapsedRealtime()<rawThumbUntil)return rawThumb;
            if(p.raw&&p.holding&&!partition.releasePending)node=p.held/(double)p.maximum;
            return displayNode(p,node);
        }
        float low()throws Exception{return abi.min.getFloat(owner.get());}float high()throws Exception{return abi.max.getFloat(owner.get());}
        void range()throws Exception{if(!Float.isFinite(low())||!Float.isFinite(high())||low()<0||high()>1||high()<=low())throw new IllegalArgumentException("系统亮度范围无效");}
        int input(Profile p,int position)throws Exception{
            return inputNode(p,ControlCenterMapping.fraction(position,minimum,maximum));
        }
        int inputNode(Profile p,double node)throws Exception{if(v1()){v1Range();return ControlCenterMapping.progress(p.v1.toInput(node),minimum,maximum);}
            range();double brightness=p.standard.toInput(node);int value=(Integer)abi.inverse.invoke(null,(float)Math.max(low(),Math.min(high(),brightness)),low(),high());ControlCenterMapping.fraction(value,minimum,maximum);return value;
        }
        int partitionInput(Profile p,int shown,double node)throws Exception{
            if(v1())return ControlCenterPartition.logicalInput(shown,minimum,maximum);
            return inputNode(p,node);
        }
        int output(Profile p,int position)throws Exception{
            double node;if(v1()){v1Range();node=p.v1.toNode(ControlCenterMapping.fraction(position,minimum,maximum));}else{range();float value=(Float)abi.forward.invoke(null,position,low(),high());if(!Float.isFinite(value))throw new IllegalArgumentException("系统亮度坐标无效");node=p.standard.toNode(value);}return displayNode(p,node);
        }
        // Native ordinary input uses perceptual/logical coordinates on V1.
        // Its feedback must invert that same device table before compressing to
        // 75%; a physical-node ratio is not the inverse of logicalInput.
        int displayNode(Profile p,double value)throws Exception{
            if(p.mode==4&&(!p.holding||partition.releasePending)&&value<=p.manualNode&&v1())return ControlCenterPartition.logicalPosition(ControlCenterMapping.progress(p.v1.toInput(value),minimum,maximum),minimum,maximum);
            return ControlCenterMapping.progress(position(p,value),minimum,maximum);
        }
        double displayedNode(Profile p,int value)throws Exception{
            if(p.mode==4&&(!p.holding||partition.releasePending)&&value<=ControlCenterMapping.progress(ControlCenterPartition.ORDINARY_SPAN,minimum,maximum)&&v1())return p.v1.toNode(ControlCenterMapping.fraction(ControlCenterPartition.logicalInput(value,minimum,maximum),minimum,maximum));
            return node(p,ControlCenterMapping.fraction(value,minimum,maximum));
        }
        double position(Profile p,double node){return p.mode==4?ControlCenterPartition.position(node,p.manualNode,p.partitionUpper,p.maximum):node;}
        double node(Profile p,double position){return p.mode==4?ControlCenterPartition.node(position,p.manualNode,p.partitionUpper,p.maximum):position;}
        void showThumb(int position)throws Exception{Object o=owner.get();boolean external=abi.external.getBoolean(o);abi.external.setBoolean(o,true);try{abi.setValue.invoke(abi.toggles.get(o),position,null);}finally{abi.external.setBoolean(o,external);}}
    }
    static Binding binding(Object owner,Abi abi,Object slider)throws Exception{
        if(context==null)bind((Context)abi.app.get(owner));Object toggle=abi.toggles.get(owner);if(toggle==null)throw new IllegalStateException("亮度控件尚未就绪");Binding b=bindings.get(toggle);if(b==null||b.owner.get()!=owner){b=new Binding(owner,abi);bindings.put(toggle,b);}if(slider!=null)b.slider(slider);
        if(b.valid&&abi.display.getInt(owner)==0)reportCoordinates(b,b.v1());
        return b;
    }
    private static void reportCoordinates(Binding binding,boolean v1){
        widgetReady=true;
        boolean observed=v1&&binding.abi.logicalProgress==null;
        String next=observed?OBSERVED_V1:v1?"oem_v1":"gamma";
        boolean ready=!observed||binding.observed.ready;
        String evidence=observed?(binding.abi.hasNativeLevels()?"native_levels/":"calibration/")+binding.observed.evidence:"";
        boolean evidenceChanged=!evidence.equals(coordinateEvidence);
        coordinateEvidence=evidence;
        long now=SystemClock.elapsedRealtime();
        if(abiReady!=ready||!next.equals(route)||evidenceChanged&&now-lastEvidencePublication>=1000){
            abiReady=ready;route=next;lastEvidencePublication=now;publish();
        }
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
                    if(c.mode==4){if(b.v1())b.v1Range();int boundary=ControlCenterMapping.progress(ControlCenterPartition.ORDINARY_SPAN,b.minimum,b.maximum);double nodeFraction=b.node(c,fraction);if(boundary<=b.minimum||boundary>=b.maximum)throw new IllegalArgumentException("普通亮度区间不足以分区");
                        if(!b.partition.stroke&&Boolean.TRUE.equals(p.args[1])&&!last&&physical>boundary&&b.feedbackNode>c.manualNode&&abi.automatic.getBoolean(p.thisObject)&&!c.holding)b.partition.fromAutomaticHigh(SystemClock.elapsedRealtime());
                        b.feedbackNode=Double.NaN;
                        boolean wasRaw=b.partition.raw;int action=b.partition.move(physical,boundary,Boolean.TRUE.equals(p.args[1]),last,SystemClock.elapsedRealtime(),c.holding);
                        if(action==ControlCenterPartition.Gate.PROMPT||action==ControlCenterPartition.Gate.RELEASE||action==ControlCenterPartition.Gate.RAW&&!wasRaw||last)recordGesture(b,"changed",physical,action);
                        if(action==ControlCenterPartition.Gate.RAW){b.rawThumb=physical;b.rawThumbUntil=SystemClock.elapsedRealtime()+1000;b.showThumb(physical);sendNode(c,ControlCenterMapping.target(nodeFraction,c.maximum),last,"raw",b.partition);p.setResult(null);return;}
                        shown=Math.min(physical,boundary);b.rawThumb=shown;b.rawThumbUntil=SystemClock.elapsedRealtime()+1000;b.showThumb(shown);
                        if(action==ControlCenterPartition.Gate.PROMPT){if(b.hint!=null)b.hint.cancel();android.widget.Toast hint=android.widget.Toast.makeText(context,("zh".equals(Locale.getDefault().getLanguage())?"再次滑动突破手动亮度限制":"Swipe again to exceed the manual brightness limit"),android.widget.Toast.LENGTH_SHORT);b.hint=hint;hint.show();main.postDelayed(()->{hint.cancel();if(b.hint==hint)b.hint=null;},ControlCenterPartition.WINDOW_MS);}
                        if(action==ControlCenterPartition.Gate.RELEASE)sendNode(c,Math.min(ControlCenterMapping.target(nodeFraction,c.maximum),(int)Math.ceil(c.manualNode*c.maximum)),true,"release",b.partition);
                        original=b.partitionInput(c,shown,Math.min(nodeFraction,c.manualNode));
                    }else if(c.raw){b.rawThumb=physical;b.rawThumbUntil=SystemClock.elapsedRealtime()+1000;sendNode(c,ControlCenterMapping.target(fraction,c.maximum),last);p.setResult(null);return;}else original=b.input(c,physical);p.setObjectExtra("hyperlux.coordinate.previous",gesturePosition.get());p.setObjectExtra("hyperlux.coordinate.changed",true);gesturePosition.set(new Gesture(b,shown));p.args[3]=original;
                }catch(Exception failure){failed(failure);}
            }protected void afterHookedMethod(MethodHookParam p){if(Boolean.TRUE.equals(p.getObjectExtra("hyperlux.coordinate.changed"))){Gesture previous=(Gesture)p.getObjectExtra("hyperlux.coordinate.previous");if(previous==null)gesturePosition.remove();else gesturePosition.set(previous);}}}));
            added.add(XposedBridge.hookMethod(abi.setValue,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
                try{
                    Binding b=bindings.get(p.thisObject);
                    if(b==null)return;
                    Feedback frame=feedbackPosition.get();
                    if(frame!=null&&frame.binding==b&&frame.observedLogical&&!frame.animating&&p.args[1]==null){
                        p.args[0]=b.nativeFeedback(frame,(Integer)p.args[0]);
                        return;
                    }
                    Profile c=b.active();
                    if(c==null)return;
                    if(c.raw&&SystemClock.elapsedRealtime()<b.rawThumbUntil){p.args[0]=b.rawThumb;return;}
                    if(c.raw&&c.holding&&!b.partition.releasePending){p.args[0]=ControlCenterMapping.progress(b.position(c,c.held/(double)c.maximum),b.minimum,b.maximum);return;}
                    Gesture gesture=gesturePosition.get();
                    if(gesture!=null&&gesture.binding==b)p.args[0]=gesture.position;
                    else if(!b.animationPhysical&&!b.observesLogical())p.args[0]=b.output(c,(Integer)p.args[0]);
                }catch(Exception failure){failed(failure);}
            }}));
            added.add(XposedBridge.hookMethod(abi.feedback,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){
                try{Object o=abi.nested?p.args[0]:p.thisObject;Binding b=binding(o,abi,null);Profile c=b.eligible();if(c==null)return;
                    boolean observed=b.observesLogical();
                    if(observed&&c.v1==null)return;
                    if(!observed&&(!c.raw||c.mode==4)){if(b.v1())b.v1Range();else b.range();}
                    float value=((Number)p.args[abi.nested?1:0]).floatValue();if(!Float.isFinite(value)||value<0||value>1)return;
                    b.feedbackNode=c.mode==4&&!c.holding?c.standard.toNode(value):Double.NaN;
                    Feedback frame=new Feedback(b,c,value,observed,o);
                    p.setObjectExtra("hyperlux.coordinate.feedback",true);p.setObjectExtra("hyperlux.coordinate.previous_feedback",feedbackPosition.get());feedbackPosition.set(frame);b.animationPhysical=!observed;
                }catch(Exception failure){failed(failure);}
            }protected void afterHookedMethod(MethodHookParam p){
                if(!Boolean.TRUE.equals(p.getObjectExtra("hyperlux.coordinate.feedback")))return;
                Feedback finished=feedbackPosition.get();
                Feedback previous=(Feedback)p.getObjectExtra("hyperlux.coordinate.previous_feedback");
                if(previous==null)feedbackPosition.remove();else feedbackPosition.set(previous);
                if(finished==null||!finished.observedLogical)return;
                ObservedProgress proof=finished.binding.observed;
                if(p.hasThrowable())proof.reset();
                else if(!finished.sampled&&"waiting_feedback".equals(proof.evidence))proof.evidence="waiting_native_progress";
                reportCoordinates(finished.binding,true);
            }}));
            // Only the verified UI feedback call is transformed; ordinary OEM brightness conversion is untouched.
            XC_MethodHook displayCoordinate=new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){Feedback f=feedbackPosition.get();if(f==null||f.observedLogical||p.hasThrowable())return;try{if(f.binding.active()!=f.profile)return;}catch(Exception unavailable){return;}try{double node;if(f.profile.raw&&f.profile.holding&&!f.binding.partition.releasePending){node=f.profile.held/(double)f.profile.maximum;}else if(f.profile.raw){node=f.profile.standard.toNode(f.actual);}else if(!f.converted){node=f.profile.standard.toNode(f.actual);f.converted=true;}else node=Objects.equals(abi.logicalProgress,p.method)?f.profile.v1.toNode(((Number)p.args[0]).doubleValue()):f.profile.standard.toNode(((Number)p.args[0]).doubleValue());p.setResult(f.binding.displayNode(f.profile,node));}catch(Exception failure){failed(failure);}}};
            if(abi.logicalProgress!=null)added.add(XposedBridge.hookMethod(abi.logicalProgress,displayCoordinate));
            else installNativeProgressAnimation(added);
            added.add(XposedBridge.hookMethod(abi.inverse,displayCoordinate));
            added.add(XposedBridge.hookMethod(abi.forward,new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){Feedback f=feedbackPosition.get();if(f==null||p.hasThrowable())return;try{if(f.binding.active()!=f.profile)return;}catch(Exception unavailable){return;}try{p.setResult((float)f.profile.standard.toInput(f.binding.displayedNode(f.profile,(Integer)p.args[0])));}catch(Exception failure){failed(failure);}}}));
            added.add(XposedBridge.hookMethod(Application.class.getDeclaredMethod("onCreate"),new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){if(p.thisObject instanceof Application)bind((Application)p.thisObject);}}));installed=true;
        }catch(Throwable failure){trackingLifecycle=false;HookTransaction.rollbackAll(added);failed(failure);}
    }
    private static void installNativeProgressAnimation(List<XC_MethodHook.Unhook> added)throws Exception{
        Method factory=method(ValueAnimator.class,"ofInt",ValueAnimator.class,int[].class);
        Method start=method(ValueAnimator.class,"start",void.class);
        added.add(XposedBridge.hookMethod(factory,new XC_MethodHook(){
            protected void afterHookedMethod(MethodHookParam p){
                Feedback frame=feedbackPosition.get();
                if(frame!=null&&!p.hasThrowable())frame.captureAnimation(p.getResult(),p.args[0]);
            }
        }));
        // The defining setter can remain a real invocation when ART inlines the factory.
        try{
            Method values=method(ValueAnimator.class,"setIntValues",void.class,int[].class);
            added.add(XposedBridge.hookMethod(values,new XC_MethodHook(){
                protected void afterHookedMethod(MethodHookParam p){
                    Feedback frame=feedbackPosition.get();
                    if(frame!=null&&!p.hasThrowable())frame.captureAnimation(p.thisObject,p.args[0]);
                }
            }));
        }catch(Exception unavailable){/* The factory path still observes its original endpoint. */}
        added.add(XposedBridge.hookMethod(start,new XC_MethodHook(){
            protected void beforeHookedMethod(MethodHookParam p){
                Feedback frame=feedbackPosition.get();
                if(frame==null||!frame.observedLogical)return;
                frame.animating=true;
                if(frame.animator!=p.thisObject)return;
                Binding binding=frame.binding;
                Object owner=binding.owner.get();
                try{
                    if(owner==null||binding.abi.animator.get(owner)!=frame.animator)return;
                    if(frame.start<binding.minimum||frame.start>binding.maximum)return;
                    int target=binding.nativeFeedback(frame,frame.target);
                    if(target!=frame.target)frame.animator.setIntValues(frame.start,target);
                }catch(Exception failure){failed(failure);}
            }
        }));
    }
    static void failed(Throwable failure){String next=failure.toString();if(!next.equals(error)){error=next;XposedBridge.log("HyperLux slider coordinates unavailable: "+next);publish();}}
    static void bind(Context c){
        if(c==null||!"com.android.systemui".equals(c.getPackageName()))return;
        synchronized(ControlCenterHooks.class){if(context!=null)return;context=c;main=new Handler(Looper.getMainLooper());}
        queueObservers();
    }
    static void queueObservers(){
        synchronized(ControlCenterHooks.class){if(observersReady||observerQueued||observerAttempts>=3)return;observerQueued=true;observerAttempts++;}
        try{io.execute(ControlCenterHooks::registerObservers);}catch(RejectedExecutionException rejected){synchronized(ControlCenterHooks.class){observerQueued=false;}failed(rejected);}
    }
    static void registerObservers(){
        // SettingsProvider and broadcast registration may wait for services at boot.
        // Never hold Application.onCreate, addToggleSlider or the UI thread here.
        Context c=context;ContentObserver changed=new ContentObserver(main){public void onChange(boolean self){refresh();}};boolean retry=false;
        try{
            c.getContentResolver().registerContentObserver(Settings.Global.getUriFor(ControlCenterRoot.PROFILE),false,changed);
            c.getContentResolver().registerContentObserver(Settings.Global.getUriFor(ControlCenterEngine.TABLES),false,changed);
            c.getContentResolver().registerContentObserver(Settings.Global.getUriFor("boot_count"),false,changed);
            c.getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),false,changed);
            ControlCenterScreen.observe(c,ControlCenterHooks::screenChanged);observersReady=true;refreshNow();
        }catch(RuntimeException|LinkageError unavailable){
            observersReady=false;try{c.getContentResolver().unregisterContentObserver(changed);}catch(RuntimeException ignored){}failed(unavailable);retry=true;
        }finally{synchronized(ControlCenterHooks.class){observerQueued=false;}}
        if(retry&&observerAttempts<3)main.postDelayed(ControlCenterHooks::queueObservers,observerAttempts*1000L);
    }
    static void screenChanged(){
        if(main==null)return;
        synchronized(ControlCenterHooks.class){pendingNode=null;nextNodeWrite=null;nodeQueued=false;main.removeCallbacks(nodeFlush);}
        main.post(()->{
            List<Binding> copy;synchronized(bindings){copy=new ArrayList<>(bindings.values());}
            for(Binding b:copy)try{Object o=b.owner.get();if(o==null)continue;
                ValueAnimator animator=(ValueAnimator)b.abi.animator.get(o);if(animator!=null)animator.cancel();
                b.partition.reset();b.rawThumbUntil=0;b.feedbackNode=Double.NaN;b.animationPhysical=false;
                if(b.hint!=null){b.hint.cancel();b.hint=null;}
                Handler handler=(Handler)b.abi.background.get(o);Runnable update=(Runnable)b.abi.update.get(o);if(handler!=null&&update!=null)handler.post(update);
            }catch(Exception failure){failed(failure);}
            publish();
        });
    }
    static void refresh(){if(!observersReady)return;try{io.execute(ControlCenterHooks::refreshNow);}catch(RejectedExecutionException ignored){}}
    static void refreshNow(){if(!observersReady)return;try{

        String raw=Settings.Global.getString(context.getContentResolver(),ControlCenterRoot.PROFILE),tables=Settings.Global.getString(context.getContentResolver(),ControlCenterEngine.TABLES),boot=Settings.Global.getString(context.getContentResolver(),"boot_count");Profile parsed=null;
        if(raw!=null&&tables!=null&&raw.length()<260000&&tables.length()<260000){JSONObject p=new JSONObject(raw),t=new JSONObject(tables);if(t.optBoolean("enabled")&&p.optInt("user_serial",-1)==0&&p.getInt("user_serial")==t.optInt("user_serial",-1)&&!p.optString("revision").isEmpty()&&p.getString("revision").equals(t.optString("revision"))&&AppBuild.BUILD.equals(t.optString("build"))&&p.getInt("pid")==t.getInt("pid")&&p.getString("process_start").equals(t.getString("process_start"))&&boot!=null&&boot.equals(p.getString("boot_count"))&&boot.equals(t.getString("boot_count"))&&p.getJSONObject("calibration").toString().equals(t.getJSONObject("calibration").toString())&&(!("raw_node_v3".equals(p.optString("kind"))||"partition_node_v4".equals(p.optString("kind")))||p.optInt("maximum")==t.optInt("maximum")&&p.optString("physical").equals(t.optString("physical")))){parsed=new Profile(p);if(parsed.mode==4&&(!p.getJSONObject("partition").toString().equals(t.getJSONObject("partition").toString())||p.optInt("partition_maximum",0)!=t.optInt("partition_maximum",0)))throw new IllegalStateException("普通亮度区间已改变");parsed.automatic=Settings.System.getInt(context.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,1)!=0;JSONObject held=t.optJSONObject("raw_control");if(held!=null)parsed.autoEpoch=held.optLong("auto_epoch",0);if(parsed.raw&&held!=null&&held.optBoolean("active")&&held.optBoolean("confirmed")&&Settings.System.getInt(context.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,1)==0){parsed.holdSession=held.optString("session");parsed.held=held.optInt("target",-1);parsed.holding=parsed.held>=10&&parsed.held<=parsed.maximum;}}}
        if(parsed==null&&widgetReady&&tables!=null)try{String key=MappingRecoveryState.generation(new JSONObject(tables),boot);if(recoveryGate.claim(key,SystemClock.elapsedRealtime())){MappingRecoverySignal.send(context,key);recoverySignal="requested";}}catch(Exception unavailable){recoverySignal=unavailable.getClass().getSimpleName();}
        String key=parsed==null?"":raw+(parsed.mode==1||parsed.raw?":"+Settings.System.getInt(context.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,0):"");applyProfile(parsed,key);
    }catch(Exception failure){applyProfile(null,"");failed(failure);}}
    static boolean sameGeneration(Profile a,Profile b){if(a==null||b==null)return false;for(String key:new String[]{"build","pid","process_start","boot_count","revision","user_serial","maximum","physical","mode"})if(!String.valueOf(a.generation.opt(key)).equals(String.valueOf(b.generation.opt(key))))return false;return true;}
    static void applyProfile(Profile next,String identity){main.post(()->{
        boolean newGeneration=!sameGeneration(profile,next);
        boolean resetPartition=newGeneration||profile!=null&&next!=null&&(profile.autoEpoch!=next.autoEpoch||!profile.automatic&&next.automatic);
        if(!profileIdentity.equals(identity)||resetPartition){
            if(newGeneration&&OBSERVED_V1.equals(route)){abiReady=false;coordinateEvidence="waiting_feedback";}
            List<Binding> copy; synchronized(bindings){copy=new ArrayList<>(bindings.values());}
            for(Binding b:copy)try{Object o=b.owner.get();if(o==null)continue;ValueAnimator a=(ValueAnimator)b.abi.animator.get(o);if(a!=null)a.cancel();b.animationPhysical=false;if(newGeneration)b.observed.reset();if(resetPartition){b.feedbackNode=Double.NaN;b.rawThumbUntil=0;b.partition.reset();if(b.hint!=null){b.hint.cancel();b.hint=null;}}}catch(Exception failure){failed(failure);}
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
        if(!ControlCenterScreen.allowsNodes(context))return;
        JSONObject request=new JSONObject();for(String key:new String[]{"build","pid","process_start","boot_count","revision","user_serial","maximum","physical","mode"})request.put(key,p.generation.get(key));request.put("auto_epoch",p.autoEpoch).put("id",nodeSession+"_"+(++nodeSequence)).put("session",nodeSession).put("target",target).put("elapsed",SystemClock.elapsedRealtime());
        request.put("action",action).put("previous_session",p.holdSession);if(gate!=null)request.put("partition_since",gate.since).put("partition_confirmed",gate.confirmed).put("partition_automatic_high",gate.automaticHigh);
        pendingNode=request;
        if(last){main.removeCallbacks(nodeFlush);nodeQueued=false;flushNode();}
        else if(!nodeQueued){nodeQueued=true;main.postDelayed(nodeFlush,32);}
    }
    static synchronized void flushNode(){
        JSONObject request=pendingNode;pendingNode=null;nodeQueued=false;if(request==null||!ControlCenterScreen.allowsNodes(context))return;
        nextNodeWrite=request;if(nodeWriting)return;nodeWriting=true;
        try{io.execute(ControlCenterHooks::writeNodes);}catch(RejectedExecutionException failure){nodeWriting=false;nextNodeWrite=null;if(SystemClock.elapsedRealtime()-request.optLong("elapsed",0)<3000){pendingNode=request;nodeQueued=true;main.postDelayed(nodeFlush,100);}else failed(failure);}
    }
    static void writeNodes(){
        for(;;){
            JSONObject request;synchronized(ControlCenterHooks.class){request=nextNodeWrite;nextNodeWrite=null;if(request==null){nodeWriting=false;return;}}
            try{Profile current=profile;if(current==null||!current.raw||!ControlCenterScreen.allowsNodes(context))continue;
                boolean matches=true;for(String key:new String[]{"build","pid","process_start","boot_count","revision","user_serial","maximum","physical","mode"})if(!String.valueOf(current.generation.opt(key)).equals(String.valueOf(request.opt(key))))matches=false;
                if(!matches)continue;
                if(!Settings.Global.putString(context.getContentResolver(),NODE_REQUEST,request.toString()))throw new IllegalStateException("节点请求未写入");
            }catch(Exception failure){failed(failure);}
        }
    }
    static void publish(){if(context!=null)try{io.execute(ControlCenterHooks::publishNow);}catch(RejectedExecutionException ignored){}}
    static void recordGesture(Binding b,String event,int position,int action){try{ControlCenterPartition.Gate g=b.partition;gestureEvidence=new JSONObject().put("event",event).put("elapsed",SystemClock.elapsedRealtime()).put("position",position).put("minimum",b.minimum).put("maximum",b.maximum).put("boundary",ControlCenterMapping.progress(ControlCenterPartition.ORDINARY_SPAN,b.minimum,b.maximum)).put("action",action).put("since",g.since).put("confirmed",g.confirmed).put("released",g.released).put("stroke",g.stroke).put("raw",g.raw).toString();publish();}catch(Exception ignored){}}
    static void publishNow(){try{Profile bound=profile;String evidence=gestureEvidence;String detail=error;
        if(detail.isEmpty()&&OBSERVED_V1.equals(route)&&!abiReady)detail="等待核对原生亮度条坐标";
        Settings.Global.putString(context.getContentResolver(),ControlCenterEngine.UI,new JSONObject().put("build",AppBuild.BUILD).put("pid",android.os.Process.myPid()).put("process_start",UI_PROCESS_START).put("input",bound!=null&&bound.mode==4?"partition_node_v4":bound!=null&&bound.raw?"raw_node_v3":"coordinate_v2").put("observers_ready",observersReady).put("observer_attempts",observerAttempts).put("coordinate_abi",abiReady).put("coordinate_evidence",coordinateEvidence).put("route",route).put("tracking_lifecycle",trackingLifecycle?"controller_start_stop":"on_changed_stop").put("gesture",evidence.isEmpty()?JSONObject.NULL:new JSONObject(evidence)).put("lockscreen_native",!ControlCenterScreen.allowsNodes(context)).put("profile_ready",bound!=null).put("bound_profile",bound==null?JSONObject.NULL:MappingRecoveryState.identity(bound.generation)).put("recovery_signal",recoverySignal).put("detail",detail).toString());}catch(Exception ignored){}}
}
