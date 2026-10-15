package top.rongshangs.lumacurve.refactor;

import java.lang.reflect.*;
import java.util.*;

/** One immutable, mutually exclusive SDR profile per actual controller class/loader.
 * A missing modern privacy guard is accepted only with the complete older OPR ABI.
 * Runtime failures never downgrade a modern profile to the legacy profile.
 */
final class OutdoorSdrScope {
    static final Map<Class<?>,OutdoorSdrScope> profiles=new HashMap<>();
    static final Map<Class<?>,String> errors=new HashMap<>();
    final String name;
    final Method hdr,privacy,legal;
    final Field automatic,available,grayscale,thresholds,nits,ambient,maxNit,config;
    private OutdoorSdrScope(Class<?> owner)throws Exception {
        hdr=HbmAccess.method(owner,"isHdrScene",boolean.class);
        automatic=typed(owner,"mAutoBrightnessEnable",boolean.class);
        Method modern;
        try{modern=owner.getDeclaredMethod("shouldUsePrivacyOprBrightness");}
        catch(NoSuchMethodException absent){modern=null;}
        if(modern!=null){
            if(modern.getReturnType()!=boolean.class||Modifier.isStatic(modern.getModifiers()))throw new IllegalStateException("Unexpected privacy guard signature");
            modern.setAccessible(true);privacy=modern;name="privacy_aware";
            legal=null;available=grayscale=thresholds=nits=ambient=maxNit=config=null;
        }else{
            // A partly ported privacy implementation is not the old non-privacy ABI.
            for(Class<?> c=owner;c!=null&&c!=Object.class;c=c.getSuperclass()){
                for(Field f:c.getDeclaredFields())rejectPrivacy(f.getName());
                for(Method m:c.getDeclaredMethods())rejectPrivacy(m.getName());
            }
            HbmAccess.method(owner,"getRestrictedOprBrightness",float.class,float.class);
            HbmAccess.method(owner,"resetScreenGrayscaleState",void.class);
            legal=HbmAccess.method(owner,"isGrayScaleLegal",boolean.class,float.class);
            available=typed(owner,"mOprBrightnessControlAvailable",boolean.class);
            grayscale=typed(owner,"mCurrentGrayScale",float.class);
            thresholds=typed(owner,"mOprGrayscaleThreshold",int[].class);
            nits=typed(owner,"mOprNitThreshold",int[].class);
            ambient=typed(owner,"mOprAmbientLuxThreshold",int.class);
            maxNit=typed(owner,"mOprMaxNitThreshold",int.class);
            typed(owner,"mPendingResetGrayscaleStateForOpr",boolean.class);
            typed(owner,"mAppliedMaxOprBrightness",float.class);
            config=HookEntry.field(owner,"mDisplayDeviceConfig");
            if(!config.getType().getName().equals("com.android.server.display.DisplayDeviceConfig"))throw new IllegalStateException("Unknown OPR display configuration");
            privacy=null;name="legacy_opr";
        }
    }
    static Field typed(Class<?> owner,String name,Class<?> type)throws Exception {
        Field f=HookEntry.field(owner,name);
        if(f.getType()!=type||Modifier.isStatic(f.getModifiers()))throw new IllegalStateException("Unexpected SDR field: "+name);
        return f;
    }
    static void rejectPrivacy(String name){
        String n=name.toLowerCase(Locale.ROOT);
        if(n.contains("privacy")||n.contains("uhbm")||n.contains("boostmultiplier")||n.contains("boosttargetnit"))throw new IllegalStateException("Incomplete privacy-aware OPR interface");
    }
    static synchronized OutdoorSdrScope resolve(Class<?> owner)throws Exception {
        OutdoorSdrScope found=profiles.get(owner);if(found!=null)return found;
        try{found=new OutdoorSdrScope(owner);profiles.put(owner,found);errors.remove(owner);return found;}
        catch(Exception unavailable){errors.put(owner,unavailable.toString());throw unavailable;}
    }
    static synchronized String profile(Class<?> owner){OutdoorSdrScope s=profiles.get(owner);return s==null?"unsupported":s.name;}
    static synchronized String detail(Class<?> owner){String error=errors.get(owner);return error==null?"":error;}
    boolean allows(Object owner)throws Exception {
        if(!automatic.getBoolean(owner)||!Boolean.FALSE.equals(hdr.invoke(owner)))return false;
        if(privacy!=null)return Boolean.FALSE.equals(privacy.invoke(owner));
        if(!available.getBoolean(owner)||config.get(owner)==null||ambient.getInt(owner)<0||maxNit.getInt(owner)<=0)return false;
        float gray=grayscale.getFloat(owner);
        if(!Float.isFinite(gray)||!Boolean.TRUE.equals(legal.invoke(owner,gray)))return false;
        int[] levels=(int[])thresholds.get(owner),values=(int[])nits.get(owner);
        // The final nit entry covers grayscale above the last threshold (N+1 bins).
        if(levels==null||values==null||levels.length==0||levels.length>128||values.length!=levels.length+1)return false;
        for(int i=0;i<levels.length;i++)if(levels[i]<0||i>0&&levels[i]<=levels[i-1])return false;
        for(int i=0;i<values.length;i++)if(values[i]<=0||values[i]>20000||i>0&&values[i]>values[i-1])return false;
        return true;
    }
}
