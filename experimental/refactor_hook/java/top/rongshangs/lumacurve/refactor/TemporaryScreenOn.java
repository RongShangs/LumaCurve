package top.rongshangs.lumacurve.refactor;

import android.content.Context;
import android.app.KeyguardManager;
import android.os.PowerManager;

/** Screen-only hold for this unlocked session. Never edits the user's timeout setting. */
final class TemporaryScreenOn {
    private static PowerManager.WakeLock hold;
    static synchronized boolean active(){return hold!=null&&hold.isHeld();}
    static synchronized void set(Context context,boolean enabled){
        if(!enabled){clear();return;}
        PowerManager power=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
        KeyguardManager keyguard=(KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
        if(power==null||!power.isInteractive()||keyguard==null||keyguard.isKeyguardLocked())throw new IllegalStateException("请解锁并保持主屏亮起");
        if(active())return;
        PowerManager.WakeLock next=power.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,"HyperLux:TemporaryScreenOn");
        next.setReferenceCounted(false);next.acquire();hold=next;
    }
    static synchronized void clear(){if(hold!=null){try{if(hold.isHeld())hold.release();}finally{hold=null;}}}
}
