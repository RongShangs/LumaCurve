"""Private OEM consumer evidence only. Firmware is never included in release assets."""
from pathlib import Path
R=Path(__file__).resolve().parents[1];count=0
D=R/'build/device-reports/framework02/disassembly';F=R/'build/beta-4.3.3-beta05/firmware';OLD=R/'build/os3-discussion-20261018-75bb25/extra'
def read(root,name):return (root/(name+'.txt')).read_text('utf-8')
def check(b):
 global count
 assert b,'Application isolation consumer '+str(count)
 count+=1
def body(s,n):return next(x for x in s.split('\nMETHOD ') if x.startswith(n+' '))
o=read(D,'com.android.server.display.DisplayPowerControllerImpl');d=read(D,'com.android.server.display.DisplayPowerController')
for name,sig in [('requestBrightnessSync','(F)V'),('consumePendingBrightnessSync','()Z'),('updateOverrideDragMode','(Z F)V'),('limitQRCodeBrightnessMax','(F F Lcom/android/server/display/brightness/BrightnessReason;)F')]:check('METHOD '+name+' '+sig in o)
sync=body(o,'requestBrightnessSync');check('->updateLogicalNit(F)V' in sync and '->mPendingBrightnessSync Z' in sync)
scoped=body(o,'limitQRCodeBrightnessMax');check(scoped.count('->requestBrightnessSync(F)V')==2);check(scoped.count('->updateOverrideDragMode(Z F)V')>=2)
check(o.count('->requestBrightnessSync(F)V')==2)
check('METHOD syncBrightnessToSettingAndUi (F Lcom/android/server/display/DisplayBrightnessState;)V' in d and 'METHOD setTemporaryBrightness (F)V' in d)
update=body(d,'updatePowerStateInternal');site=update.index('->limitQRCodeBrightnessMax(');check('->isAppliedOverrideStrategy()Z' in update[max(0,site-2500):site])
req=read(F,'android.hardware.display.DisplayManagerInternal$DisplayBrightnessOverrideRequest');check('FIELD brightness F' in req and 'FIELD tag Ljava/lang/CharSequence;' in req)
reason=read(F,'com.android.server.display.brightness.BrightnessReason');check('METHOD getTag ()Ljava/lang/CharSequence;' in reason and 'METHOD getReason ()I' in reason)
for constant in ['REASON_OVERRIDE','REASON_MANUAL','REASON_AUTOMATIC']:check('FIELD '+constant+' I' in reason)
consumer=read(F,'com.android.server.display.brightness.DisplayBrightnessController');check('METHOD updateWindowManagerBrightnessOverride (Landroid/hardware/display/DisplayManagerInternal$DisplayBrightnessOverrideRequest;)Z' in consumer)
check('->updateWindowManagerBrightnessOverride(' in body(consumer,'updateWindowManagerBrightnessOverride'))
strategy=read(F,'com.android.server.display.brightness.strategy.OverrideBrightnessStrategy');accept=body(strategy,'updateWindowManagerBrightnessOverride');check('->mWindowManagerBrightnessOverrideTag Ljava/lang/CharSequence;' in accept and '->mWindowManagerBrightnessOverride F' in accept)
old_o=read(OLD,'com.android.server.display.DisplayPowerControllerImpl');old_d=read(OLD,'com.android.server.display.DisplayPowerController')
for name,src in [('requestBrightnessSync',old_o),('consumePendingBrightnessSync',old_o),('syncBrightnessToSettingAndUi',old_d)]:check('METHOD '+name+' ' not in src)
check('DisplayBrightnessOverrideRequest;' in old_d)
check('FIELD mUniqueDisplayId Ljava/lang/String;' in d)
for name,kind in [('mNotQRcodeComponentSet','Ljava/util/Set;'),('mIsCameraOpen','Z'),('mHbmController','Lcom/android/server/display/HighBrightnessModeController;'),('mDisplayDeviceConfig','Lcom/android/server/display/DisplayDeviceConfig;')]:check('FIELD '+name+' '+kind in o)
# Both sync call sites belong to the native positively classified non-QR branch.
check('->mNotQRcodeComponentSet Ljava/util/Set;' in scoped)
check(scoped.index('->mIsCameraOpen Z')<scoped.index('->requestBrightnessSync(F)V'))
check(scoped.index('->isHdrScene()Z')<scoped.index('->requestBrightnessSync(F)V'))
check(scoped.index('->contains(Ljava/lang/Object;)Z',scoped.index('->mNotQRcodeComponentSet '))<scoped.index('->requestBrightnessSync(F)V'))
check(scoped.rindex('->requestBrightnessSync(F)V')<scoped.index('->getQRCodeBrightnessMax(F F)F'))
check('->getNormalBrightnessMax()F' in body(o,'getNormalMaxBrightness'))
check('->getCurrentBrightnessMax()F' in body(o,'getCurrentMaxBrightness'))
check('->getGoodCurveMax(F Z)F' in body(o,'getQRCodeBrightnessMax'))
print(f'Application isolation firmware: {count} checks PASS; private consumers only, no Android execution')

