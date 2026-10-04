"""Verify memory consumers against local OEM disassembly, never ship firmware."""
from pathlib import Path
R=Path(__file__).resolve().parents[1]
count=0
def check(value):
    global count
    assert value
    count+=1
def body(text,name):return text.split('METHOD '+name+' ',1)[1].split('\nMETHOD ',1)[0]
for directory in [R/'build/research-203/os41',R/'build/research-203/os28',R/'build/device-reports/k90pm-20261003/disassembly']:
    def read(name):return (directory/('com.android.server.display.'+name+'.txt')).read_text(encoding='utf-8')
    abc=read('AutomaticBrightnessController');impl=read('AutomaticBrightnessControllerImpl');ref=read('RefactorNitController')
    for name,kind in [('mAmbientLux','F'),('mAmbientLuxValid','Z'),('mLightSensorEnabled','Z'),('mDisplayPolicy','I')]:check('FIELD '+name+' '+kind in abc)
    for signature in ['setDisplayPolicy (I)Z','isInteractivePolicy (I)Z','isInIdleMode ()Z','resetShortTermModel ()V']:check('METHOD '+signature in abc)
    policy=body(abc,'setDisplayPolicy');check('->getShortTermModelTimeout()J' in policy);check('->removeMessages(I)V' in policy)
    reset=body(abc,'resetShortTermModel');check('->clearUserDataPoints()V' in reset);check('->resetDefaultSpline()V' in reset)
    rule=body(impl,'isNeedResetShortTermModel');check('1800000' in rule and '360000' in rule and '1101004800' in rule)
    for signature in ['needResetShortTermModelPolicy ()Z','isNeedResetShortTermModel (F J)Z']:check('METHOD '+signature in impl)
    good=body(ref,'shouldUseGoodCurve');check('->mIsHaveGoodCurve Z' in good);check('->mGoodAnchorLux [F' in good and '->mAnchorLux [F' in good)
    # Inner OEM curve update precedes outer short-term-model update.
    manual=abc.split('METHOD setScreenBrightnessByUser (F F)Z',1)[1].split('\nMETHOD ',1)[0];check(manual.index('->setScreenBrightnessByUser(')<manual.index('->-$$Nest$msetUserBrightness('))
    model_path=directory/'com.android.server.display.AutomaticBrightnessController$ShortTermModel.txt'
    if model_path.is_file():
        model=model_path.read_text(encoding='utf-8')
        for name,kind in [('mAnchor','F'),('mBrightness','F'),('mIsValid','Z')]:check('FIELD '+name+' '+kind in model)
        check('METHOD setUserBrightness (F F)V' in model);check('METHOD invalidate ()V' in model)
        check('->shouldResetShortTermModel(' in body(model,'maybeReset'))
print(f'Memory firmware: {count} checks PASS; 3 collected firmware ABIs/consumers, no device execution')
