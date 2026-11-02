package top.rongshangs.lumacurve.refactor;

import android.app.KeyguardManager;
import android.content.*;
import android.os.PowerManager;

/** Platform screen/keyguard state only. Unknown state retains native lockscreen controls. */
final class ControlCenterScreen {
    static boolean allowsNodes(Context context){
        if(context==null)return false;
        try{
            Object keyguard=context.getSystemService(Context.KEYGUARD_SERVICE),power=context.getSystemService(Context.POWER_SERVICE);
            return keyguard instanceof KeyguardManager&&power instanceof PowerManager
                &&((PowerManager)power).isInteractive()&&!((KeyguardManager)keyguard).isKeyguardLocked();
        }catch(RuntimeException|LinkageError unavailable){return false;}
    }
    static void observe(Context context,Runnable changed){
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);
        context.registerReceiver(new BroadcastReceiver(){public void onReceive(Context ignored,Intent intent){changed.run();}},filter,Context.RECEIVER_NOT_EXPORTED);
    }
}
