package top.rongshangs.lumacurve.refactor;
import android.content.Context;
import android.os.*;
import android.provider.Settings;
import org.json.JSONObject;
/** Separates confirmed system-server injection from supported curve attachment. */
final class InjectionStatus {
    static final String KEY="lumacurve_refactor_injection_v1";
    static String lastStage="";
    static void write(Context context,String stage,String message){
        if(stage.equals(lastStage))return;
        try{if(context==null){Object thread=Class.forName("android.app.ActivityThread").getMethod("currentActivityThread").invoke(null);if(thread==null)return;context=(Context)thread.getClass().getMethod("getSystemContext").invoke(thread);}if(context==null)return;
            JSONObject marker=new JSONObject().put("build",AppBuild.BUILD).put("pid",android.os.Process.myPid()).put("process_start",HookRuntime.processStart())
                .put("fingerprint",Build.FINGERPRINT).put("elapsed_ms",SystemClock.elapsedRealtime()).put("stage",stage).put("message",message);
            if(Settings.Global.putString(context.getContentResolver(),KEY,marker.toString()))lastStage=stage;
        }catch(Throwable unavailable){}
    }
}
