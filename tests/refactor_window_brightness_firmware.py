"""Private native window consumers; never packages firmware. No device execution claim."""
from pathlib import Path
R=Path(__file__).resolve().parents[1];base=R/'build/minimized-window-beta19-20261114';cases=0

def check(value):
 global cases
 assert value,'Window firmware check '+str(cases)
 cases+=1

def read(root,name):return (root/(name+'.txt')).read_text('utf-8')
def body(text,name):return next(x for x in text.split('\nMETHOD ') if x.startswith(name+' '))
for profile in ['os41','os3','pandora']:
 root=base/profile;wm=read(root,'com.android.server.wm.WindowManagerServiceImpl');windows=read(root,'com.android.server.wm.RootWindowContainer');stack=read(root,'com.android.server.wm.MiuiFreeFormActivityStack');manager=read(root,'com.android.server.wm.MiuiFreeFormManagerService');service=read(root,'com.android.server.wm.WindowManagerService');task=read(root,'com.android.server.wm.Task');activity=read(root,'com.android.server.wm.ActivityTaskManagerService');window=read(root,'com.android.server.wm.WindowState')
 for signature in ['shouldApplyOverrideBrightness (Lcom/android/server/wm/WindowState;)Z','notifySystemBrightnessChange (Lcom/android/server/wm/WindowState;)V']:check('METHOD '+signature in wm)
 decision=body(windows,'handleNotObscuredLocked');gate=decision.index('->shouldApplyOverrideBrightness(');store=decision.index('->brightness F',gate);check(gate<store);check('->isOnScreen()Z' in decision[:gate]);check('->mDisplayBrightnessOverrides' in decision and '->getWindowTag()' in decision)
 check('->mTaskIdScreenBrightnessOverrides' in body(wm,'shouldApplyOverrideBrightness'))
 check('->screenBrightness F' in body(wm,'shouldApplyOverrideBrightness'))
 for name in ['isInMiniFreeFormMode','inPinMode']:check('METHOD '+name+' (I)Z' in manager)
 check('->isInMiniFreeFormMode()Z' in body(manager,'isInMiniFreeFormMode'));check('->inPinMode()Z' in body(manager,'inPinMode'))
 check('mMiuiFreeFromWindowMode I' in body(stack,'isInMiniFreeFormMode'));check('mMiuiFreeFromWindowMode I' in body(stack,'inPinMode'));check('iput' in body(stack,'setStackFreeFormMode'))
 check('METHOD getMiuiFreeFromWindowMode ()I' in stack);check('METHOD setStackFreeFormMode (I)V' in stack);check('METHOD onConfigurationChanged (Landroid/content/res/Configuration;)V' in task)
 for field in ['FIELD mGlobalLock Lcom/android/server/wm/WindowManagerGlobalLock;','FIELD mH Lcom/android/server/wm/WindowManagerService$H;']:check(field in service)
 check('->requestTraversal()V' in body(service,'requestTraversal'))
 check('FIELD mMiuiFreeFormManagerService Lcom/android/server/wm/MiuiFreeFormManagerServiceStub;' in activity)
 check('FIELD mUserId I' in task);check('METHOD getRootTaskId ()I' in task);check('FIELD mAttrs Landroid/view/WindowManager$LayoutParams;' in window)
 # Every frame recomputes the display candidate map before passing it to display management.
 placement=body(windows,'performSurfacePlacement');placement=body(windows,'performSurfacePlacementNoTrace') if '->performSurfacePlacementNoTrace()V' in placement else placement;check('->clear()V' in placement and '->mDisplayBrightnessOverrides' in placement);check('->clone()Landroid/util/SparseArray;' in placement and '->sendToTarget()V' in placement)
 check('METHOD notifySystemBrightnessChange (Lcom/android/server/wm/WindowState;)V' in wm)
for name,signature in [('android.app.TaskInfo','METHOD getWindowingMode ()I'),('android.app.WindowConfiguration','FIELD WINDOWING_MODE_FULLSCREEN I')]:check(signature in read(base/'os41',name))
print('Window brightness firmware: '+str(cases)+' checks PASS; three private native consumers, no Android execution')
