"""Actual private reset-derived ABI evidence. No device execution or redistributed firmware."""
from pathlib import Path
import re
R=Path(__file__).resolve().parents[1];D=R/'build/analysis-20261103-182556-4a64f1'
ref=(D/'com.android.server.display.RefactorNitController.txt').read_text('utf-8');owner=(D/'com.android.server.display.DisplayPowerControllerImpl.txt').read_text('utf-8');util=(D/'com.android.server.display.RefactorBrightnessUtil.txt').read_text('utf-8')
cases=0
def check(b):
 global cases
 assert b,cases
 cases+=1
def method(text,name):return text.split('METHOD '+name,1)[1].split('\nMETHOD ',1)[0]
for n in ['mDefaultLuxToLogicalNit','mLuxSeg0','mLuxSeg1','mLuxMax','mAnchorLux','mAnchorNit','mDefNit0']:check('FIELD '+n+' ' not in ref)
for n in ['getDefaultLogicalBrt','calculateBrt','defaultAtLux']:check('METHOD '+n+' ' not in ref)
for n,t in [('mLuxToLogicalNit','[D'),('mGoodLuxToLogicalNit','[D'),('mLogicalCurve','[F'),('mGoodLogicalCurve','[F'),('mLogicalData','[F'),('mUserLux','F'),('mUserLogicalNit','F'),('mUseAutoBrightness','Z'),('mIsHaveGoodCurve','Z'),('mDisplayId','I'),('mUserSerial','I')]:check('FIELD '+n+' '+t in ref)
for s in ['getLogicalBrt (F)D flags=private','resetDefaultSpline ()V','getCurrentNit (F F Z)F','updateLogicalCurve (F F)V','shouldUseGoodCurve ()V','setAutoBrightnessState (Z)V']:check('METHOD '+s in ref)
reset=method(ref,'resetDefaultSpline ()V');read=method(ref,'getLogicalBrt (F)D');update=method(ref,'updateLogicalCurve (F F)V');output=method(ref,'getCurrentNit (F F Z)F')
check('const/16 v1, 22' in reset and 'const/16 v9, 9' in reset)
written=set(re.findall(r'iput(?:-object|-boolean)?[^\n]*->(\w+) ',reset));check(written=={'mLogicalCurve','mGoodLogicalCurve','mLogicalData','mGoodLuxToLogicalNit','mIsHaveGoodCurve','mUserLux','mUserLogicalNit'})
# Reset mutates the existing current coefficient array and allocates only native state arrays.
check('->mLuxToLogicalNit [D' in reset and 'aput-wide' in reset)
forbidden=['SharedPreferences','Settings;','setBrightness','setAutoBrightness','post(','registerListener','updateLogicalCurve']
for marker in forbidden:check(marker not in reset)
calls=re.findall(r'invoke-[^\n]* (L[^;]+;)->(\w+)\(',reset)
check(set(calls)<={('Ljava/util/Arrays;','copyOf'),('Ljava/util/Arrays;','toString'),('Ljava/lang/StringBuilder;','append'),('Ljava/lang/StringBuilder;','toString'),('Landroid/util/Slog;','i')})
check('->mLuxToLogicalNit [D' in read and '->min(F F)F' in read and '->max(F F)F' in read)
check('->getLogicalBrt(F)D' in output and '->mHighLuxScalingFactorCurve Landroid/util/Spline;' in output)
check('->mManualMaxBrightnessController' in output and '->mUseAutoBrightness Z' in output)
check('->updateCurvePrefBased([F [F F F)V' in update and '->mLogicalCurve [F' in update and '->mLuxToLogicalNit [D' in update)
good=method(ref,'shouldUseGoodCurve ()V');check('->mGoodLogicalCurve [F' in good and '->mGoodLuxToLogicalNit [D' in good and '->copyOf' in good)
check('FIELD MIN_NIT F' in util and 'FIELD MAX_NIT F' in util)
check('->getCurrentNit(F F Z)F' in method(owner,'getRefactorBrightness (F F Z Z)F'))
check('->updateLogicalCurve(F F)V' in owner)
print(f'Reset-derived firmware: {cases} checks PASS; supplied DEX consumers, no Android execution')
