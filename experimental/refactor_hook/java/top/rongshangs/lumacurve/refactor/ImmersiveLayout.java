package top.rongshangs.lumacurve.refactor;

import android.graphics.Insets;
import android.view.*;
import android.widget.FrameLayout;

/** Shared safe areas for the main shell and tutorial/quick-setup siblings. */
final class ImmersiveLayout extends FrameLayout {
    final MainActivity a;Insets safe=Insets.NONE;int ime,navigationObstruction;boolean keyboard;
    ImmersiveLayout(MainActivity owner){super(owner);a=owner;setBackgroundColor(a.BG);setClipChildren(false);}
    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets){WindowInsetsController controller=a.getWindow().getInsetsController();if(controller!=null){int light=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;controller.setSystemBarsAppearance(a.darkTheme?0:light,light);}safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());navigationObstruction=insets.getInsets(WindowInsets.Type.tappableElement()|WindowInsets.Type.displayCutout()).bottom;ime=insets.getInsets(WindowInsets.Type.ime()).bottom;keyboard=insets.isVisible(WindowInsets.Type.ime())&&ime>safe.bottom;refresh();return WindowInsets.CONSUMED;}
    @Override public void onViewAdded(View child){super.onViewAdded(child);if(a!=null)apply(child);}
    void apply(View child){if(child==a.root)child.setPadding(safe.left,safe.top,safe.right,keyboard?ime:0);else child.setPadding(safe.left,safe.top,safe.right,Math.max(safe.bottom,ime));}
    void refresh(){for(int i=0;i<getChildCount();i++)apply(getChildAt(i));a.updateSecondaryInsets(keyboard?0:safe.bottom);if(a.floatingNavigation!=null)a.floatingNavigation.insets(navigationObstruction,keyboard);}
}
