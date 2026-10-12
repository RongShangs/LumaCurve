package top.rongshangs.lumacurve.refactor;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/** Only the launcher alias changes; the exported module settings activity stays available. */
final class LauncherVisibility {
    static ComponentName component(Context context){return new ComponentName(context,context.getPackageName()+".Launcher");}
    static boolean hidden(Context context){int state=context.getPackageManager().getComponentEnabledSetting(component(context));return state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED||state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER||state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;}
    static void setHidden(Context context,boolean hidden){context.getPackageManager().setComponentEnabledSetting(component(context),hidden?PackageManager.COMPONENT_ENABLED_STATE_DISABLED:PackageManager.COMPONENT_ENABLED_STATE_ENABLED,PackageManager.DONT_KILL_APP);}
}
