import java.lang.reflect.*;

/** Read-only settings bridge using a registered external-provider token. */
public final class LumaFrameworkProbeSettings {
    static final String[] KEYS={"screen_brightness_mode","screen_auto_brightness_adj","screen_brightness"};
    static void settings() throws Exception { readSettings(false); }
    public static String[] readSettings(boolean quiet) throws Exception {
        Class<?> activity=Class.forName("android.app.ActivityManager");
        Object am=LumaFrameworkProbe.invoke(activity,"getService",new Class<?>[0]);
        int user=(Integer)LumaFrameworkProbe.invoke(activity,"getCurrentUser",new Class<?>[0]);
        Class<?> binderInterface=Class.forName("android.os.IBinder");
        Object token=Class.forName("android.os.Binder").getConstructor().newInstance();
        Object holder=null;
        try {
            holder=LumaFrameworkProbe.invoke(am,"getContentProviderExternal",
                new Class<?>[]{String.class,int.class,binderInterface,String.class},"settings",user,token,"LumaCurve-read-only");
            if(holder==null)throw new IllegalStateException("external settings provider missing");
            Object provider=holder.getClass().getField("provider").get(holder);
            Class<?> bundleClass=Class.forName("android.os.Bundle");
            Object extras=bundleClass.getConstructor().newInstance();
            LumaFrameworkProbe.invoke(extras,"putInt",new Class<?>[]{String.class,int.class},"_user",user);
            Class<?> sourceClass=Class.forName("android.content.AttributionSource");
            Object builder=Class.forName("android.content.AttributionSource$Builder").getConstructor(int.class).newInstance(0);
            LumaFrameworkProbe.invoke(builder,"setPackageName",new Class<?>[]{String.class},"android");
            try {
                int pid=(Integer)LumaFrameworkProbe.invoke(Class.forName("android.os.Process"),"myPid",new Class<?>[0]);
                LumaFrameworkProbe.invoke(builder,"setPid",new Class<?>[]{int.class},pid);
            } catch(NoSuchMethodException absent) {if(!quiet)System.out.println("ATTRIBUTION pid_builder_unavailable");}
            Object attribution=LumaFrameworkProbe.invoke(builder,"build",new Class<?>[0]);
            if(!quiet)System.out.println("SETTINGS_PROVIDER external user="+user);
            boolean valid=true;
            String[] values=new String[KEYS.length];int index=0;
            for(String key:KEYS) {
                Object reply=LumaFrameworkProbe.invoke(provider,"call",
                    new Class<?>[]{sourceClass,String.class,String.class,String.class,bundleClass},
                    attribution,"settings","GET_system",key,extras);
                String value=reply==null?null:(String)LumaFrameworkProbe.invoke(reply,"getString",new Class<?>[]{String.class},"value");
                values[index++]=value;
                if(!quiet)System.out.println("SETTING "+key+"="+value);
                try {
                    if("screen_brightness_mode".equals(key))valid&="0".equals(value)||"1".equals(value);
                    else if("screen_auto_brightness_adj".equals(key)){
                        float adjustment=Float.parseFloat(value);
                        valid&=LumaFrameworkProbe.finite(adjustment)&&adjustment>=-1&&adjustment<=1;
                    } else valid&=Integer.parseInt(value)>=0;
                }catch(RuntimeException invalid){valid=false;}
            }
            if(!valid)throw new IllegalStateException("missing or invalid settings: no valid mode/preference snapshot");
            if(!quiet)System.out.println("SETTINGS_VALUES valid");
            return values;
        } finally {
            if(holder!=null) {
                try {
                    LumaFrameworkProbe.invoke(am,"removeContentProviderExternalAsUser",
                        new Class<?>[]{String.class,binderInterface,int.class},"settings",token,user);
                    if(!quiet)System.out.println("PROVIDER_RELEASE confirmed_call_completed");
                } catch(Throwable failure) {
                    if(quiet)throw new IllegalStateException("external provider release failed",failure);
                    System.out.println("PROVIDER_RELEASE_UNAVAILABLE "+failure);
                    // The token also dies when this short-lived probe process exits.
                }
            }
        }
    }
    static void units() throws Exception {
        LumaFrameworkProbe.AndroidBridge bridge=new LumaFrameworkProbe.AndroidBridge();
        System.out.println("DISPLAY0 "+LumaFrameworkProbe.invoke(bridge.global,"getDisplayInfo",new Class<?>[]{int.class},0));
        LumaFrameworkProbe.values(bridge.info());
        System.out.println("SAVED_FRAMEWORK_BRIGHTNESS "+bridge.brightness());
        for(int unit:new int[]{1,2}) {
            try {
                Object value=LumaFrameworkProbe.invoke(bridge.global,"getBrightness",new Class<?>[]{int.class,int.class},0,unit);
                System.out.println("SAVED_BRIGHTNESS_UNIT "+unit+"="+value);
            }catch(Throwable error){System.out.println("UNIT_UNAVAILABLE "+unit+" "+error);}
        }
    }
    public static void main(String[] args) {
        if(args.length!=0){System.err.println("This probe is read-only and accepts no arguments");System.exit(2);}
        System.out.println("probe_build=20260929-framework05-settings");
        boolean ok=true;
        try{units();}catch(Throwable error){ok=false;error.printStackTrace();}
        try{settings();}catch(Throwable error){ok=false;error.printStackTrace();}
        System.out.println("result="+(ok?"read_only_calls_completed":"read_only_interface_failure"));
        if(!ok)System.exit(2);
    }
}
