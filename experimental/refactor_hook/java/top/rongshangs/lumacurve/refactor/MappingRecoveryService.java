package top.rongshangs.lumacurve.refactor;

import android.app.job.*;
import org.json.*;

/** Short-lived job. Idle native listening is restored; no old gesture or brightness is replayed. */
public final class MappingRecoveryService extends JobService {
    static final class Run {final JobParameters params;volatile RootPanelBridge bridge;volatile boolean stopped;Run(JobParameters value){params=value;}}
    volatile Run current;
    @Override public synchronized boolean onStartJob(JobParameters params){
        final int attempt=params.getExtras().getInt("attempt",0);
        if(attempt<0||attempt>=MappingStartup.DELAYS.length||!MappingStartup.enabled(this))return false;Run run=new Run(params);current=run;
        Thread worker=new Thread(()->{
            JSONObject answer=null;RootPanelBridge connection=new RootPanelBridge(this);run.bridge=connection;
            try{if(!run.stopped)answer=connection.command("resume-mapping",null,8000);
                synchronized(this){if(!run.stopped&&current==run)MappingStartup.record(this,attempt,answer==null?"cancelled":answer.optString("state","error"),answer==null?"":answer.optString("message"));}
            }catch(Exception failure){synchronized(this){if(!run.stopped&&current==run)MappingStartup.record(this,attempt,"root_unavailable",failure.toString());}}
            finally{connection.close();run.bridge=null;}
            synchronized(this){if(!run.stopped&&current==run){jobFinished(params,false);current=null;if(MappingStartup.retry(answer,attempt))MappingStartup.schedule(this,attempt+1);}}
        },"HyperLux-mapping-recovery");worker.setDaemon(true);worker.start();return true;
    }
    @Override public synchronized boolean onStopJob(JobParameters params){Run run=current;if(run!=null&&run.params.getJobId()==params.getJobId()&&run.params.getExtras().getInt("attempt",-1)==params.getExtras().getInt("attempt",-2)){run.stopped=true;current=null;RootPanelBridge live=run.bridge;if(live!=null)live.cancel();}return false;}
}
