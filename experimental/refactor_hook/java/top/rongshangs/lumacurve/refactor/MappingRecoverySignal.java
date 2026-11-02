package top.rongshangs.lumacurve.refactor;
import android.content.*;
/** Explicit readiness wake-up protected by a platform signature permission. */
final class MappingRecoverySignal {
    static final String ACTION="top.rongshangs.lumacurve.MAPPING_READY",PERMISSION="android.permission.STATUS_BAR_SERVICE";
    static final String APP="top.rongshangs.lumacurve",RECEIVER=APP+".refactor.MappingStartup$ReadyReceiver";
    static void send(Context context,String generation){
        if(context.checkSelfPermission(PERMISSION)!=0)throw new SecurityException("SystemUI readiness permission unavailable");
        context.sendBroadcast(new Intent(ACTION).setComponent(new ComponentName(APP,RECEIVER)).putExtra("generation",generation));
    }
}
