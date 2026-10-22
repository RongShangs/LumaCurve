"""Private supplied firmware consumer checks; files never included in public source."""
from pathlib import Path
import re
R=Path(__file__).resolve().parents[1];D=R/'build/z80u-20261022/miui-disassembly'
ref=(D/'com.android.server.display.RefactorNitController.txt').read_text(encoding='utf-8');owner=(D/'com.android.server.display.DisplayPowerControllerImpl.txt').read_text(encoding='utf-8');util=(D/'com.android.server.display.RefactorBrightnessUtil.txt').read_text(encoding='utf-8')
cases=0
def check(b):
 global cases
 assert b
 cases+=1
def method(name):return ref.split('METHOD '+name,1)[1].split('\nMETHOD ',1)[0]
for name in ['mLuxSeg0','mLuxSeg1','mLuxMax','mAnchorLux','mDefNit0']:check('FIELD '+name+' ' not in ref)
check('METHOD defaultAtLux ' not in ref)
for n,t in [('mDefaultLuxToLogicalNit','[D'),('mLuxToLogicalNit','[D'),('mGoodLuxToLogicalNit','[D'),('mLogicalCurve','[F'),('mGoodLogicalCurve','[F'),('mLogicalData','[F'),('mDisplayId','I'),('mUserSerial','I'),('mUserLux','F'),('mUserLogicalNit','F'),('mUseAutoBrightness','Z'),('mIsHaveGoodCurve','Z')]:check('FIELD '+n+' '+t in ref)
for signature in ['calculateBrt (F [D)D','getDefaultLogicalBrt (F)D','getCurrentNit (F F Z)F','updateLogicalCurve (F F)V','resetDefaultSpline ()V','shouldUseGoodCurve ()V','setAutoBrightnessState (Z)V']:check('METHOD '+signature in ref)
reset=method('resetDefaultSpline ()V');current=method('getCurrentNit (F F Z)F');fit=method('updateLogicalCurve (F F)V');calc=method('calculateBrt (F [D)D')
check('22\n' in reset and 'new-array' in reset);check('->mLogicalCurve [F' in reset and '->mGoodLogicalCurve [F' in reset and '->mLogicalData [F' in reset)
check('->mLuxToLogicalNit [D' in reset and '->mGoodLuxToLogicalNit [D' in reset)
check('->calculateBrt(F [D)D' in method('getLogicalBrt (F)D'))
check('->mDefaultLuxToLogicalNit [D' in method('getDefaultLogicalBrt (F)D'))
check('->calculateBrt(F [D)D' in method('getDefaultLogicalBrt (F)D'))
check('->getLogicalBrt(F)D' in current and '->mHighLuxScalingFactorCurve Landroid/util/Spline;' in current)
check('->getFirstFactor(F)D' in current and '->getSecondFactor(F)D' in current)
check('->mManualMaxBrightnessController' in current and '->mUseAutoBrightness Z' in current)
check('->updateCurvePrefBased([F [F F F)V' in fit and '->saveLogicalNitToPreferences(F Z)V' in fit)
check('->mLogicalCurve [F' in fit and '->mLuxToLogicalNit [D' in fit)
check('->mGoodLogicalCurve [F' in method('shouldUseGoodCurve ()V') and '->mLuxToLogicalNit [D' in method('shouldUseGoodCurve ()V'))
check('->min(F F)F' in calc and '->max(F F)F' in calc)
check('FIELD MIN_NIT F' in util and 'FIELD MAX_NIT F' in util)
check('METHOD getRefactorBrightness (F F Z Z)F' in owner)
body=owner.split('METHOD getRefactorBrightness ',1)[1].split('\nMETHOD ',1)[0]
check('->post(Ljava/lang/Runnable;)Z' in body and '->getCurrentNit(F F Z)F' in body)
memory=owner.split('METHOD lambda$getRefactorBrightness$5 ',1)[1].split('\nMETHOD ',1)[0]
check('->updateLogicalCurve(F F)V' in memory)
check('->adjustBrightnessByThermal' in owner and '->adjustBrightnessByBattery' in owner)
print(f'Refactor coefficient firmware: {cases} checks PASS; actual supplied DEX consumers, static only')
