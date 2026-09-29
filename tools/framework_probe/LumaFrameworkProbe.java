import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Root app_process probe. No Binder transaction numbers or driver writes. */
public final class LumaFrameworkProbe {
    static Object invoke(Object object, String name, Class<?>[] types, Object... args) throws Exception {
        Class<?> cls = object instanceof Class ? (Class<?>) object : object.getClass();
        Method method = cls.getMethod(name, types);
        try { return method.invoke(object instanceof Class ? null : object, args); }
        catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw error;
        }
    }
    static void methods(Class<?> cls) {
        System.out.println("CLASS " + cls.getName());
        try {
            Method[] methods = cls.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(Method::toString));
            for (Method method : methods) {
                String name = method.getName().toLowerCase(Locale.ROOT);
                if (name.contains("bright") || name.contains("ramp") || name.contains("lux"))
                    System.out.println("METHOD " + method.toString());
            }
        } catch (Throwable error) { System.out.println("METHODS_UNAVAILABLE " + error); }
    }
    static String file(String path) {
        try (BufferedReader in = new BufferedReader(new FileReader(path))) {
            String line = in.readLine(); return line == null ? "NA" : line.trim();
        } catch (IOException error) { return "NA"; }
    }
    static float floatField(Object object, String name) throws Exception {
        return object.getClass().getField(name).getFloat(object);
    }
    static boolean finite(float f) { return !Float.isNaN(f) && !Float.isInfinite(f); }
    static List<String> serviceJars() {
        List<String> jars=new ArrayList<>();
        for (String root:new String[]{"/system/framework","/system_ext/framework","/product/framework"}) {
            File[] files=new File(root).listFiles(); if(files==null) continue;
            for(File f:files) {
                String name=f.getName().toLowerCase(Locale.ROOT);
                if(name.endsWith(".jar")&&(name.contains("service")||name.contains("miui")||name.contains("xiaomi")||name.contains("display")))
                    jars.add(f.getAbsolutePath());
            }
        }
        Collections.sort(jars);return jars;
    }
    static void vendorIndex(List<String> jars) {
        for(String path:jars) {
            System.out.println("JAR "+path+" bytes="+new File(path).length());
            Object dex=null;
            try {
                Class<?> dexClass=Class.forName("dalvik.system.DexFile");
                dex=dexClass.getConstructor(String.class).newInstance(path);
                Enumeration<?> entries=(Enumeration<?>)invoke(dex,"entries",new Class<?>[0]);
                while(entries.hasMoreElements()) {
                    String name=String.valueOf(entries.nextElement());
                    String key=name.toLowerCase(Locale.ROOT);
                    if(key.contains("brightness")||key.contains("displaypower")||key.contains("backlight")||key.contains("rampanimator")||key.contains("highbrightness"))
                        System.out.println("DEX_CLASS "+path+" "+name);
                }
            } catch(Throwable error) {System.out.println("DEX_INDEX_UNAVAILABLE "+path+" "+error);}
            finally {if(dex!=null)try{invoke(dex,"close",new Class<?>[0]);}catch(Throwable ignored){}}
        }
    }
    interface Bridge {
        String mode() throws Exception;
        float brightness() throws Exception;
        float minimum() throws Exception;
        float maximum() throws Exception;
        void mode(String value) throws Exception;
        void brightness(float value) throws Exception;
        void sample(String phase, float target) throws Exception;
        void delay(long milliseconds) throws Exception;
    }
    static final class AndroidBridge implements Bridge {
        final Class<?> globalClass = Class.forName("android.hardware.display.DisplayManagerGlobal");
        final Object global = invoke(globalClass, "getInstance", new Class<?>[0]);
        Object resolver; int user; Class<?> resolverClass, settingsClass;
        AndroidBridge() throws Exception { if (global == null) throw new IllegalStateException("display service missing"); }
        void settings() throws Exception {
            if (resolver != null) return;
            Class<?> looper = Class.forName("android.os.Looper");
            if (invoke(looper, "getMainLooper", new Class<?>[0]) == null)
                invoke(looper, "prepareMainLooper", new Class<?>[0]);
            Class<?> thread = Class.forName("android.app.ActivityThread");
            Object at = invoke(thread, "systemMain", new Class<?>[0]);
            Object context = invoke(at, "getSystemContext", new Class<?>[0]);
            resolver = invoke(context, "getContentResolver", new Class<?>[0]);
            resolverClass = Class.forName("android.content.ContentResolver");
            settingsClass = Class.forName("android.provider.Settings$System");
            user = (Integer) invoke(Class.forName("android.app.ActivityManager"), "getCurrentUser", new Class<?>[0]);
        }
        Object info() throws Exception { return invoke(global, "getBrightnessInfo", new Class<?>[]{int.class}, 0); }
        public String mode() throws Exception {
            settings();
            return (String) invoke(settingsClass, "getStringForUser", new Class<?>[]{resolverClass,String.class,int.class},
                                   resolver,"screen_brightness_mode",user);
        }
        public float brightness() throws Exception { return (Float) invoke(global,"getBrightness",new Class<?>[]{int.class},0); }
        public float minimum() throws Exception { return floatField(info(),"brightnessMinimum"); }
        public float maximum() throws Exception { return floatField(info(),"brightnessMaximum"); }
        public void mode(String value) throws Exception {
            settings();
            Object ok = invoke(settingsClass,"putStringForUser",new Class<?>[]{resolverClass,String.class,String.class,int.class},
                               resolver,"screen_brightness_mode",value,user);
            if (!Boolean.TRUE.equals(ok)) throw new IOException("mode write rejected");
        }
        public void brightness(float value) throws Exception {
            invoke(global,"setBrightness",new Class<?>[]{int.class,float.class},0,value);
        }
        public void sample(String phase,float target) throws Exception {
            Object info=info();
            System.out.printf(Locale.ROOT,"SAMPLE,%d,%s,%.7f,%.7f,%.7f,%s%n",System.nanoTime(),phase,target,
                              brightness(),floatField(info,"brightness"),
                              file("/sys/class/backlight/panel0-backlight/brightness"));
        }
        public void delay(long ms) throws InterruptedException { Thread.sleep(ms); }
    }
    static void control(Bridge bridge) throws Exception {
        String oldMode=bridge.mode(); float oldBrightness=bridge.brightness();
        float min=bridge.minimum(),max=bridge.maximum();
        if (!("0".equals(oldMode)||"1".equals(oldMode)) || !finite(oldBrightness) ||
            !finite(min)||!finite(max)||min<0||max>1||min>=max||oldBrightness<min||oldBrightness>max)
            throw new IllegalStateException("preflight failed: valid original mode/brightness/range required");
        System.out.printf(Locale.ROOT,"SAVED mode=%s brightness=%.7f min=%.7f max=%.7f%n",oldMode,oldBrightness,min,max);
        Throwable failure=null; boolean started=false;
        try {
            started=true; bridge.mode("0"); bridge.delay(700);
            if (!"0".equals(bridge.mode())) throw new IOException("manual mode readback mismatch");
            float[] targets={oldBrightness,Math.min(max,oldBrightness+.02f),Math.max(min,oldBrightness-.02f),oldBrightness};
            for (int p=0;p<targets.length;p++) {
                if (!"0".equals(bridge.mode())) throw new IOException("mode changed during test");
                bridge.brightness(targets[p]);
                for (int i=0;i<30;i++) { bridge.sample("target"+p,targets[p]); bridge.delay(100); }
            }
        } catch (Throwable error) { failure=error; }
        finally {
            if (started) {
                boolean brightnessSent=false;
                try { bridge.brightness(oldBrightness); brightnessSent=true; }
                catch (Throwable error) { if (failure==null) failure=error; else failure.addSuppressed(error); }
                try {
                    bridge.mode(oldMode);
                    if (!oldMode.equals(bridge.mode())) throw new IOException("restore mode readback mismatch");
                    System.out.println("RESTORE mode=ok brightness_request="+(brightnessSent?"sent":"failed"));
                } catch (Throwable error) { if (failure==null) failure=error; else failure.addSuppressed(error); }
            }
        }
        if (failure!=null) throw new Exception("control test failed; inspect restore result",failure);
        System.out.println("result=control_calls_completed (not proof of stable physical brightness)");
    }
    static void inspect() throws Exception {
        System.out.println("probe_build=20260929-framework01");
        System.out.println("uid="+invoke(Class.forName("android.os.Process"),"myUid",new Class<?>[0]));
        for (String name:new String[]{"android.hardware.display.DisplayManagerGlobal","android.hardware.display.IDisplayManager",
                                    "android.view.SurfaceControl"}) {
            try { methods(Class.forName(name)); } catch (Throwable error) { System.out.println("UNAVAILABLE "+name+" "+error); }
        }
        try {
            AndroidBridge bridge=new AndroidBridge();
            System.out.println("DISPLAY0 "+invoke(bridge.global,"getDisplayInfo",new Class<?>[]{int.class},0));
            System.out.println("BRIGHTNESS_INFO "+bridge.info());
            System.out.println("BRIGHTNESS_SETTING "+bridge.brightness());
            try { System.out.println("SYSTEM_MODE "+bridge.mode()); }
            catch (Throwable error) { System.out.println("SETTINGS_UNAVAILABLE "+error); }
        } catch(Throwable error) {System.out.println("BRIDGE_UNAVAILABLE "+error);}
        List<String> jars=serviceJars();vendorIndex(jars);
        try {
            Class<?> loader=Class.forName("dalvik.system.PathClassLoader");
            ClassLoader services=(ClassLoader)loader.getConstructor(String.class,ClassLoader.class)
                .newInstance(String.join(":",jars),LumaFrameworkProbe.class.getClassLoader());
            for (String name:new String[]{"com.android.server.display.DisplayPowerController",
                                         "com.android.server.display.AutomaticBrightnessController",
                                         "com.android.server.display.RampAnimator",
                                         "com.android.server.display.brightness.strategy.AutomaticBrightnessStrategy",
                                         "com.android.server.display.brightness.strategy.AutomaticBrightnessStrategy2"}) {
                try { methods(Class.forName(name,false,services)); }
                catch (Throwable error) { System.out.println("SERVICES_UNAVAILABLE "+name+" "+error); }
            }
        } catch (Throwable error) { System.out.println("SERVICES_LOADER_UNAVAILABLE "+error); }
        System.out.println("result=read_only_completed");
    }
    public static void main(String[] args) {
        try {
            if (args.length==0||args.length==1&&"inspect".equals(args[0])) inspect();
            else if (args.length==1&&"control".equals(args[0])) control(new AndroidBridge());
            else throw new IllegalArgumentException("inspect | control");
        } catch (Throwable error) { error.printStackTrace(); System.out.println("result=fail"); System.exit(2); }
    }
}
