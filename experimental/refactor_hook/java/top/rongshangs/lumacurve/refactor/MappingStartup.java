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
    static boolean schedule(Context context,int attempt){return schedule(context,attempt,"");}
    static boolean schedule(Context context,int attempt,String signal){
        if(attempt<0||attempt>=DELAYS.length||!enabled(context))return false;
        JobScheduler jobs=context.getSystemService(JobScheduler.class);if(jobs==null)return false;
        if(attempt==0&&signal.isEmpty()){
            if(jobs.getPendingJob(JOB)!=null)return true;
            SharedPreferences saved=context.getSharedPreferences(PREFS,0);String key=generation(context),state=saved.getString("state","");
            boolean terminal=!state.isEmpty()&&!state.equals("pending")||saved.getInt("attempt",0)+1>=DELAYS.length;
            if(!key.isEmpty()&&key.equals(saved.getString("generation",""))&&terminal)return true;
        }
        PersistableBundle extra=new PersistableBundle();extra.putInt("attempt",attempt);extra.putString("signal",signal);
        if(attempt==0)try{
            JobInfo urgent=new JobInfo.Builder(JOB,new ComponentName(context,MappingRecoveryService.class)).setExpedited(true).setExtras(extra).build();
            if(jobs.schedule(urgent)==JobScheduler.RESULT_SUCCESS){context.getSharedPreferences(PREFS,0).edit().putString("dispatch","expedited").apply();return true;}
        }catch(RuntimeException denied){} // Quota/framework restrictions keep the original bounded job path.
        JobInfo job=new JobInfo.Builder(JOB,new ComponentName(context,MappingRecoveryService.class)).setMinimumLatency(DELAYS[attempt]).setOverrideDeadline(DELAYS[attempt]+15000).setExtras(extra).build();
        boolean submitted=jobs.schedule(job)==JobScheduler.RESULT_SUCCESS;if(submitted)context.getSharedPreferences(PREFS,0).edit().putString("dispatch","scheduled").apply();return submitted;
    }
    static boolean ready(Context context,Intent intent){
        if(intent==null||!MappingRecoverySignal.ACTION.equals(intent.getAction()))return false;
        try{String raw=Settings.Global.getString(context.getContentResolver(),ControlCenterEngine.TABLES),boot=Settings.Global.getString(context.getContentResolver(),"boot_count");
            if(raw==null||raw.length()>260000)return false;String key=MappingRecoveryState.generation(new JSONObject(raw),boot);if(key.isEmpty()||!key.equals(intent.getStringExtra("generation")))return false;
            SharedPreferences saved=context.getSharedPreferences(PREFS,0);
            if(!enabled(context)){String config=null;try{config=Settings.Global.getString(context.getContentResolver(),RootControl.CONFIG);}catch(Exception unreadable){}
                if(config!=null)return false; // A readable disabled/invalid config always wins.
                // Signed SystemUI + current enabled tables are a hint only; Root rechecks the original config.
                saved.edit().putBoolean("enabled",true).apply();
            }
            String state=saved.getString("state","");
            if(key.equals(saved.getString("ready_generation",""))||generation(context).equals(saved.getString("generation",""))&&(state.equals("root_unavailable")||state.equals("unavailable")||state.equals("unsupported")))return false;
            saved.edit().putString("ready_generation",key).putLong("ready_elapsed_ms",SystemClock.elapsedRealtime()).apply();
            JobScheduler jobs=context.getSystemService(JobScheduler.class);JobInfo pending=jobs==null?null:jobs.getPendingJob(JOB);if(pending!=null&&pending.isExpedited())return true;
            boolean submitted=schedule(context,0,key);if(!submitted)saved.edit().putString("ready_generation","").apply();return submitted;
        }catch(Exception unavailable){return false;}
    }

    static void record(Context context,int attempt,String state,String detail){record(context,attempt,state,detail,"");}
    static void record(Context context,int attempt,String state,String detail,String signal){context.getSharedPreferences(PREFS,0).edit().putString("generation",generation(context)).putString("trigger",signal.isEmpty()?"boot_receiver":"systemui_ready").putInt("attempt",attempt).putString("state",state).putString("detail",detail==null?"":detail.substring(0,Math.min(300,detail.length()))).putLong("elapsed",SystemClock.elapsedRealtime()).commit();}
    static JSONObject report(Context context)throws JSONException{
        SharedPreferences p=context.getSharedPreferences(PREFS,0);JobScheduler jobs=context.getSystemService(JobScheduler.class);
        return new JSONObject().put("route","android_boot_job").put("configured",p.getBoolean("enabled",false)).put("state",p.getString("state","not_run")).put("detail",p.getString("detail","")).put("attempt",p.getInt("attempt",0)).put("elapsed_ms",p.getLong("elapsed",0)).put("pending",jobs!=null&&jobs.getPendingJob(JOB)!=null).put("maximum_attempts",DELAYS.length).put("trigger",p.getString("trigger","boot_receiver")).put("dispatch",p.getString("dispatch","")).put("ready_elapsed_ms",p.getLong("ready_elapsed_ms",0));
    }
    /** Export is restricted by the platform signature permission in the manifest. */
    public static final class ReadyReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context,Intent intent){ready(context,intent);}
    }
    public static final class Receiver extends BroadcastReceiver {
        @Override public void onReceive(Context context,Intent intent){
            String action=intent==null?null:intent.getAction();
            if(Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)||Intent.ACTION_USER_UNLOCKED.equals(action))schedule(context,0);
        }
    }
}
