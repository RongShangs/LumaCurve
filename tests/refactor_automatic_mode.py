"""Actual mode adapters and disconnected refresh rendering; host doubles, never Android claims."""
from pathlib import Path
import runpy,subprocess,os
R=Path(__file__).resolve().parents[1];base=runpy.run_path(str(R/'tests/refactor_traditional.py'));S=base['S'];J=base['J'];O=R/'build/automatic-mode-tests';O.mkdir(exist_ok=True)
def method(text,signature):
    start=text.index(signature);i=text.index('{',start)+1;depth=1
    while depth:
        depth+=(text[i]=='{')-(text[i]=='}');i+=1
    return text[start:i]
main=(S/'MainActivity.java').read_text('utf-8');hook=(S/'HookRuntime.java').read_text('utf-8')
assert 'AutomaticModeAccess.put(status,kernel);' in method(hook,'    void publish(')
assert '.put("auto_mode",kernel.get(' not in method(hook,'    void publish(')
assert 'renderDisconnected(answer);return;' in method(main,'    void render(JSONObject answer)')
sources={
'com/android/server/display/brightness/strategy/AutomaticBrightnessStrategy2.java':'''package com.android.server.display.brightness.strategy;public class AutomaticBrightnessStrategy2{public int mDisplayId;public Object mAutomaticBrightnessController;public boolean active,enabled,fail;public int calls;public boolean shouldUseAutoBrightness(){calls++;if(fail)throw new IllegalStateException("native mode failure");return active;}public boolean isAutoBrightnessEnabled(){return enabled;}}''',
'com/android/server/display/brightness/strategy/AutomaticBrightnessStrategy.java':'''package com.android.server.display.brightness.strategy;public class AutomaticBrightnessStrategy extends AutomaticBrightnessStrategy2{}''',
'top/rongshangs/lumacurve/refactor/AutomaticModeTest.java':r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;import java.util.*;import android.hardware.display.BrightnessConfiguration;import com.android.server.display.MiuiPhysicalBrightnessMappingStrategy;
import com.android.server.display.brightness.strategy.AutomaticBrightnessStrategy2;
import com.android.server.display.brightness.strategy.AutomaticBrightnessStrategy;
public class AutomaticModeTest {
 static int cases;interface Work{void run()throws Exception;}static void check(boolean v){if(!v)throw new AssertionError("mode "+cases);cases++;}
 static void bad(Work w)throws Exception{try{w.run();throw new AssertionError("invalid mode accepted "+cases);}catch(ReflectiveOperationException|IllegalArgumentException|IllegalStateException expected){cases++;}}
 public static class Direct {boolean mUseAutoBrightness;}
 public static class Owner {int mDisplayId;boolean mAutoBrightnessEnable=true;Object mDisplayPowerController;}
 public static class WrongPrimary extends Owner {String mUseAutoBrightness="true";}
 public static class StaticPrimary extends Owner {static boolean mUseAutoBrightness=true;}
 public static class DPC {int mDisplayId;Object mDisplayPowerControllerImpl,mAutomaticBrightnessController,mAutomaticBrightnessStrategy;}
 public static class LegacyDPC {int mDisplayId;boolean mUseAutoBrightness;Object mDisplayPowerControllerImpl,mAutomaticBrightnessController;}
 public static class BadLegacyDPC {int mDisplayId;String mUseAutoBrightness="true";Object mDisplayPowerControllerImpl,mAutomaticBrightnessController;}
 public static class BothDPC extends DPC {boolean mUseAutoBrightness=true;}
 public static class Map0 {Object m;public Object get(int i){return i==0?m:null;}}
 public static class ABC {Map0 mBrightnessMappingStrategyMap=new Map0();Object mCurrentBrightnessMapper,mShortTermModel=new Object();public void resetShortTermModel(){}}
 static DPC bind(Owner o,Object abc,AutomaticBrightnessStrategy2 s){DPC d=new DPC();o.mDisplayPowerController=d;d.mDisplayPowerControllerImpl=o;d.mAutomaticBrightnessController=s.mAutomaticBrightnessController=abc;d.mAutomaticBrightnessStrategy=s;return d;}
 static class BadBackend extends CurveBackend{Object mode;Exception error;BadBackend(){super(1,1000);}public Object get(String n)throws Exception{if(error!=null)throw error;return mode;}public CurvePlan plan(){return null;}public void configure(float[] f){}public void clearMemory(){}public float currentAt(float lux){return 0;}public String name(){return "test";}}
 static class Widget {String text="";boolean enabled=true;int invalidations;void setText(String s){text=s;}void setEnabled(boolean b){enabled=b;}void invalidate(){invalidations++;}}
 static class Screen {
  Object runtime=new Object(),factoryLux=new Object(),factoryNit=new Object();boolean permissionBlocked;String permissionNotice="";int toasts,prompts;String prompt="";
  Widget lowLightSwitch=new Widget(),badge=new Widget(),stateTitle=new Widget(),luxReading=new Widget(),nitReading=new Widget(),apply=new Widget(),thermalSwitch=new Widget(),memorySwitch=new Widget(),stateGraph=new Widget(),pipeline=new Widget();
  void drawAdvanced(){}void drawOutdoor(){}void drawControls(){}void show(String s){toasts++;}void permissionPrompt(String id,String title,String message){prompts++;prompt=id;}
'''+method(main,'    void renderDisconnected(JSONObject answer)')+r'''}
 public static void main(String[] args)throws Exception {
  Direct old=new Direct();AutomaticModeAccess direct=new AutomaticModeAccess(old,null);check(direct.route.equals("owner_boolean")&&!direct.read());old.mUseAutoBrightness=true;check(direct.read());
  Owner o=new Owner();Object abc=new Object();AutomaticBrightnessStrategy2 s=new AutomaticBrightnessStrategy2();DPC d=bind(o,abc,s);
  if(Boolean.getBoolean("invalid")){bad(()->new AutomaticModeAccess(o,abc));System.out.println("Automatic mode invalid ABI: "+cases+" cases PASS");return;}
  AutomaticModeAccess a=new AutomaticModeAccess(o,abc);check(a.route.equals("native_automatic_strategy"));check(!a.read());s.active=true;check(a.read());s.enabled=false;check(a.read());s.active=false;s.enabled=true;check(!a.read()); // Setting/enabled is not effective use.
  for(int i=0;i<100;i++){s.active=i%2==0;check(a.read()==s.active);}check(s.calls==104);
  s.fail=true;bad(()->a.read());s.fail=false; // Never use mAutoBrightnessEnable as a fallback.
  WrongPrimary wrong=new WrongPrimary();bind(wrong,abc,s);bad(()->new AutomaticModeAccess(wrong,abc));StaticPrimary stat=new StaticPrimary();bind(stat,abc,s);bad(()->new AutomaticModeAccess(stat,abc));
  o.mDisplayId=1;bad(()->new AutomaticModeAccess(o,abc));o.mDisplayId=0;d.mDisplayId=1;bad(()->new AutomaticModeAccess(o,abc));d.mDisplayId=0;s.mDisplayId=1;bad(()->new AutomaticModeAccess(o,abc));s.mDisplayId=0;
  d.mDisplayPowerControllerImpl=new Object();bad(()->new AutomaticModeAccess(o,abc));d.mDisplayPowerControllerImpl=o;
  s.mAutomaticBrightnessController=new Object();bad(()->a.read());s.mAutomaticBrightnessController=abc;
  d.mAutomaticBrightnessController=new Object();bad(()->a.read());d.mAutomaticBrightnessController=abc;
  d.mAutomaticBrightnessStrategy=new Object();bad(()->a.read());bad(()->new AutomaticModeAccess(o,abc));d.mAutomaticBrightnessStrategy=s;
  o.mDisplayPowerController=new DPC();bad(()->a.read());o.mDisplayPowerController=d;
  d.mAutomaticBrightnessStrategy=null;bad(()->new AutomaticModeAccess(o,abc));d.mAutomaticBrightnessStrategy=s;
  AutomaticBrightnessStrategy alternate=new AutomaticBrightnessStrategy();bind(o,abc,alternate);check(!new AutomaticModeAccess(o,abc).read());alternate.active=true;check(new AutomaticModeAccess(o,abc).read());
  // Older framework owns effective mode directly on DPC, not on the OEM owner.
  Owner legacyOwner=new Owner();LegacyDPC legacy=new LegacyDPC();legacyOwner.mDisplayPowerController=legacy;legacy.mDisplayPowerControllerImpl=legacyOwner;legacy.mAutomaticBrightnessController=abc;
  AutomaticModeAccess legacyAccess=new AutomaticModeAccess(legacyOwner,abc);check(legacyAccess.route.equals("display_controller_boolean")&&!legacyAccess.read());legacy.mUseAutoBrightness=true;check(legacyAccess.read());
  for(int i=0;i<100;i++){legacy.mUseAutoBrightness=i%2==0;check(legacyAccess.read()==legacy.mUseAutoBrightness);}
  legacy.mDisplayId=1;bad(()->new AutomaticModeAccess(legacyOwner,abc));legacy.mDisplayId=0;
  legacyOwner.mDisplayId=1;bad(()->new AutomaticModeAccess(legacyOwner,abc));legacyOwner.mDisplayId=0;
  legacy.mAutomaticBrightnessController=new Object();bad(()->legacyAccess.read());legacy.mAutomaticBrightnessController=abc;
  legacy.mDisplayPowerControllerImpl=new Object();bad(()->new AutomaticModeAccess(legacyOwner,abc));legacy.mDisplayPowerControllerImpl=legacyOwner;
  legacyOwner.mDisplayPowerController=new LegacyDPC();bad(()->legacyAccess.read());legacyOwner.mDisplayPowerController=legacy;
  BadLegacyDPC badLegacy=new BadLegacyDPC();badLegacy.mDisplayPowerControllerImpl=legacyOwner;badLegacy.mAutomaticBrightnessController=abc;legacyOwner.mDisplayPowerController=badLegacy;bad(()->new AutomaticModeAccess(legacyOwner,abc));
  BothDPC both=new BothDPC();both.mDisplayPowerControllerImpl=legacyOwner;both.mAutomaticBrightnessController=abc;legacyOwner.mDisplayPowerController=both;
  bad(()->new AutomaticModeAccess(legacyOwner,abc)); // Present but null strategy cannot fall back to the valid legacy boolean.
  both.mAutomaticBrightnessStrategy=new Object();bad(()->new AutomaticModeAccess(legacyOwner,abc));
  // Production physical backend uses the same boundary; no constructor mode dependency.
  Owner actual=new Owner();ABC controller=new ABC();AutomaticBrightnessStrategy2 actualStrategy=new AutomaticBrightnessStrategy2();bind(actual,controller,actualStrategy);
  BrightnessConfiguration conf=new BrightnessConfiguration(new float[]{0,30,600,5000},new float[]{2,30,120,1000},new HashMap<>(),new HashMap<>(),"local",true,300000,.6f,.8f);
  MiuiPhysicalBrightnessMappingStrategy mapper=new MiuiPhysicalBrightnessMappingStrategy(conf);controller.mBrightnessMappingStrategyMap.m=controller.mCurrentBrightnessMapper=mapper;
  TraditionalAdapter kernel=new TraditionalAdapter(actual,mapper,controller,()->0);check(mapper.mConfig==conf&&kernel.fullNit()[3]==1000);actualStrategy.active=true;check(Boolean.TRUE.equals(kernel.get("mUseAutoBrightness")));actualStrategy.active=false;check(Boolean.FALSE.equals(kernel.get("mUseAutoBrightness")));
  JSONObject status=new JSONObject().put("logs",new JSONArray().put("running")).put("factory_lux",new JSONArray().put(0));AutomaticModeAccess.put(status,kernel);check(!status.getBoolean("auto_mode")&&status.getJSONArray("logs").length()==1);
  actualStrategy.fail=true;AutomaticModeAccess.put(status,kernel);check(!status.has("auto_mode")&&status.optString("automatic_mode_error").contains("native mode failure")&&status.getJSONArray("logs").length()==1);actualStrategy.fail=false;actualStrategy.active=true;AutomaticModeAccess.put(status,kernel);check(status.getBoolean("auto_mode")&&!status.has("automatic_mode_error"));
  BadBackend unknown=new BadBackend();AutomaticModeAccess.put(status,unknown);check(!status.has("auto_mode")&&status.has("automatic_mode_error"));unknown.mode=1;AutomaticModeAccess.put(status,unknown);check(!status.has("auto_mode"));unknown.error=new NoSuchFieldException("mUseAutoBrightness");AutomaticModeAccess.put(status,unknown);check(status.getJSONArray("logs").length()==1&&status.has("factory_lux"));
  JSONObject published=new JSONObject();kernel.putCurveState(published);check(published.getString("automatic_mode_route").equals("native_automatic_strategy"));
  Screen ui=new Screen();JSONObject pending=new JSONObject().put("connected",false).put("lsp_loaded",true).put("hook_issue","attached").put("hook_message","已读取设备本地曲线：physical_mapping");
  for(int i=0;i<100;i++){ui.renderDisconnected(pending);check(ui.toasts==0&&ui.prompts==0&&ui.runtime==null&&ui.factoryLux==null&&ui.factoryNit==null&&!ui.apply.enabled&&ui.stateTitle.text.equals("等待读取系统运行状态"));}
  pending.put("hook_issue","curve_incompatible");ui.renderDisconnected(pending);check(ui.prompt.equals("abi")&&ui.prompts==1);pending.put("lsp_loaded",false).put("hook_issue","restart_required");ui.renderDisconnected(pending);check(ui.prompt.equals("lsp")&&ui.prompts==2&&ui.toasts==0);
  System.out.println("Automatic mode: "+cases+" cases PASS; old/native mode, provenance, partial status and repeated refresh; host only");
 }
}'''}
paths=[]
for n,s in sources.items():
 p=O/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s,'utf-8');paths.append(str(p))
C=O/'classes';C.mkdir(exist_ok=True);cp=str(base['C'])+os.pathsep+str(J)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',cp,'-d',str(C),*paths],check=True)
subprocess.run(['java','-cp',str(C)+os.pathsep+cp,'top.rongshangs.lumacurve.refactor.AutomaticModeTest'],check=True)
# Compile same-name ABI doubles separately so wrong/static preferred methods cannot bypass validation.
strategy=O/'com/android/server/display/brightness/strategy/AutomaticBrightnessStrategy2.java'
for mode,old,new in [('boxed','public boolean shouldUseAutoBrightness()','public Boolean shouldUseAutoBrightness()'),('static','public boolean shouldUseAutoBrightness(){calls++;if(fail)throw new IllegalStateException("native mode failure");return active;}','public static boolean shouldUseAutoBrightness(){return true;}')]:
 strategy.write_text(sources['com/android/server/display/brightness/strategy/AutomaticBrightnessStrategy2.java'].replace(old,new),'utf-8');V=O/('invalid-'+mode);V.mkdir(exist_ok=True)
 subprocess.run(['javac','--release','8','-encoding','UTF-8','-d',str(V),str(strategy)],check=True)
 subprocess.run(['java','-Dinvalid=true','-cp',str(V)+os.pathsep+str(C)+os.pathsep+cp,'top.rongshangs.lumacurve.refactor.AutomaticModeTest'],check=True)
