package top.rongshangs.lumacurve.refactor;

import android.app.job.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import org.json.*;

/** One bounded recovery job for an already saved mapping. No root during discovery. */
public final class MappingStartup {
    static final int JOB=44001;
    static final String PREFS="hyperlux_mapping_startup";
    static final long[] DELAYS={3000,10000,30000,60000};
    static boolean wanted(JSONObject config){return BrightnessControlOptions.mappingRecoveryWanted(config);}
    static void remember(Context context,JSONObject config){if(config==null)return;boolean on=wanted(config);context.getSharedPreferences(PREFS,0).edit().putBoolean("enabled",on).apply();if(!on){JobScheduler jobs=context.getSystemService(JobScheduler.class);if(jobs!=null)jobs.cancel(JOB);}}
    static boolean enabled(Context context){
        // A hint only. Root revalidates original config, user, process and nodes.
        // Unreadable settings do not erase an existing saved selection.
        try{String raw=Settings.Global.getString(context.getContentResolver(),RootControl.CONFIG);if(raw!=null&&raw.length()<=28000){JSONObject config=new JSONObject(raw);remember(context,config);return wanted(config);}}catch(Exception unavailable){}
        return context.getSharedPreferences(PREFS,0).getBoolean("enabled",false);
    }
    static boolean supported(Context context){return context.getSystemService(JobScheduler.class)!=null;}
    static String generation(Context context){try{String boot=Settings.Global.getString(context.getContentResolver(),"boot_count");return boot==null?"":AppBuild.BUILD+":"+boot;}catch(Exception unavailable){return "";}}
    static boolean retry(JSONObject result,int attempt){return result!=null&&result.optBoolean("ok")&&"pending".equals(result.optString("state"))&&attempt>=0&&attempt+1<DELAYS.length;}
    static boolean schedule(Context context,int attempt){
        if(attempt<0||attempt>=DELAYS.length||!enabled(context))return false;
        JobScheduler jobs=context.getSystemService(JobScheduler.class);if(jobs==null)return false;
        if(attempt==0){
            if(jobs.getPendingJob(JOB)!=null)return true;
            SharedPreferences saved=context.getSharedPreferences(PREFS,0);String key=generation(context),state=saved.getString("state","");
            boolean terminal=!state.isEmpty()&&!state.equals("pending")||saved.getInt("attempt",0)+1>=DELAYS.length;
            if(!key.isEmpty()&&key.equals(saved.getString("generation",""))&&terminal)return true;
        }
        PersistableBundle extra=new PersistableBundle();extra.putInt("attempt",attempt);
        JobInfo job=new JobInfo.Builder(JOB,new ComponentName(context,MappingRecoveryService.class)).setMinimumLatency(DELAYS[attempt]).setOverrideDeadline(DELAYS[attempt]+15000).setExtras(extra).build();
        return jobs.schedule(job)==JobScheduler.RESULT_SUCCESS;
    }
    static void record(Context context,int attempt,String state,String detail){context.getSharedPreferences(PREFS,0).edit().putString("generation",generation(context)).putInt("attempt",attempt).putString("state",state).putString("detail",detail==null?"":detail.substring(0,Math.min(300,detail.length()))).putLong("elapsed",SystemClock.elapsedRealtime()).commit();}
    static JSONObject report(Context context)throws JSONException{
        SharedPreferences p=context.getSharedPreferences(PREFS,0);JobScheduler jobs=context.getSystemService(JobScheduler.class);
        return new JSONObject().put("route","android_boot_job").put("configured",p.getBoolean("enabled",false)).put("state",p.getString("state","not_run")).put("detail",p.getString("detail","")).put("attempt",p.getInt("attempt",0)).put("elapsed_ms",p.getLong("elapsed",0)).put("pending",jobs!=null&&jobs.getPendingJob(JOB)!=null).put("maximum_attempts",DELAYS.length);
    }
    public static final class Receiver extends BroadcastReceiver {
        @Override public void onReceive(Context context,Intent intent){
            String action=intent==null?null:intent.getAction();
            if(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)||Intent.ACTION_USER_UNLOCKED.equals(action))schedule(context,0);
        }
    }
}
