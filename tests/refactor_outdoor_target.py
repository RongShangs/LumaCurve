"""Production target hook/controller with nonlinear physical conversion and protection handovers."""
from pathlib import Path
import ast,subprocess,os
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/outdoor-target-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
tree=ast.parse((R/'tests/refactor_outdoor.py').read_text('utf-8'));sources=ast.literal_eval(next(n.value for n in tree.body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='sources' for t in n.targets)))
B='top/rongshangs/lumacurve/refactor/'
original=sources[B+'OutdoorTest.java'];start=original.index(' public static void main(')
sources[B+'OutdoorTest.java']=original[:start]+r'''
 public static void main(String[] args)throws Exception{
  DisplayPowerControllerImpl owner=new DisplayPowerControllerImpl();HookRuntime s=new HookRuntime();s.owner=owner;HookEntry.states.put(owner,s);OutdoorTuning.install(owner.getClass());
  OutdoorOptions o=enabled();o.targetCurve.enabled=true;o.targetCurve.points=new OutdoorOptions.OprCurve(new double[]{20000,40000},new double[]{1500,1900});s.outdoor.configure(o);s.handler.advance(SystemClock.now+3000);check(s.outdoor.policy.active);
  float v=owner.mAutomaticBrightnessControllerImpl.getOverrideLimitBrightness(.94f,false,30000);check(v<.94f);check(Math.abs(owner.convertBrightnessToNit(v)-1700)<.01);float raised=owner.mAutomaticBrightnessControllerImpl.getOverrideLimitBrightness(.3f,false,30000);eq(v,raised);
  for(int nit=1000;nit<=1800;nit+=17){o.targetCurve.points=new OutdoorOptions.OprCurve(new double[]{20000,40000},new double[]{nit,nit});float x=s.outdoor.target(.99f);check(Math.abs(owner.convertBrightnessToNit(x)-nit)<.01);check(x<=owner.mHbmController.mBrightnessMax);}
  float protectedBase=.4f;for(int reason=0;reason<5;reason++){s.hdr=reason==0;s.power.low=reason==1;s.kernel.auto=reason!=2;s.scene=reason!=3;s.user=reason!=4;eq(s.outdoor.target(protectedBase),protectedBase);}s.hdr=false;s.power.low=false;s.kernel.auto=s.scene=s.user=true;
  s.outdoor.stop();s.outdoor.configure(o);s.handler.advance(SystemClock.now+3000);check(s.outdoor.policy.active);owner.mHbmController.mIsTimeAvailable=false;v=s.outdoor.target(.9f);check(v<=owner.mHbmController.mHbmData.transitionPoint);owner.mHbmController.mIsTimeAvailable=true;
  s.outdoor.manual(30000);eq(s.outdoor.target(.7f),.7f);check(s.outdoor.policy.reason.equals("manual"));s.outdoor.stop();s.outdoor.configure(o);s.handler.advance(SystemClock.now+3000);
  owner.nitScale=Float.NaN;eq(s.outdoor.target(.4f),.4f);owner.nitScale=2000;check(Float.isNaN(s.outdoor.target(Float.NaN)));
  s.outdoor.stop();OutdoorOptions legacy=enabled();s.outdoor.configure(legacy);s.handler.advance(SystemClock.now+3000);eq(s.outdoor.target(.97f),.97f);
  JSONObject config=new JSONObject();o.put(config);check(OutdoorOptions.parse(config).same(o));JSONObject before=new JSONObject(config.toString());JSONObject fit=ConfigurationCompatibility.fit(config,new JSONObject().put("outdoor_target_curve_supported",false),new JSONArray());check(!fit.getBoolean(OutdoorTargetCurve.ENABLED)&&config.toString().equals(before.toString()));check(OutdoorTargetCurve.parse(fit).points.same(o.targetCurve.points));
  try{OutdoorOptions.parse(new JSONObject(config.toString()).put(OutdoorOptions.OPR_CURVE_ENABLED,true).put(OutdoorOptions.OPR_CURVE,o.targetCurve.points.json()));throw new AssertionError();}catch(IllegalArgumentException expected){count++;}
  System.out.println("Outdoor target curve: "+count+" cases PASS; actual nonlinear target hook, downward control, guards and dormant configuration");
 }
}'''
paths=[]
for name,body in sources.items():p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body,'utf-8');paths.append(p)
names=['ConfigurationCompatibility','BrightnessControlOptions','MemoryOptions','OutdoorNodeTarget','OutdoorTargetCurve','OutdoorOptions','OutdoorPolicy','HbmAccess','OutdoorController','OutdoorTuning','OutdoorOpr','OutdoorOprEnvelope','OutdoorSdrScope','ThermalPolicy','AdvancedPolicy','LiveLimitEvidence','NitBrightnessLimit','OutdoorCurvePreview']
C=O/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),str(S/'hooking/HookTransaction.java'),*[str(S/(n+'.java')) for n in names],*[str(p) for p in paths]],check=True)
subprocess.run(['java','-cp',os.pathsep.join([str(C),str(J)]),'top.rongshangs.lumacurve.refactor.OutdoorTest'],check=True)
