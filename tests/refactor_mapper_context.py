"""Normal/idle ABI convergence plus old physical-adapter transactions; synthetic host only."""
from pathlib import Path
import runpy,subprocess,os
R=Path(__file__).resolve().parents[1]
base=runpy.run_path(str(R/'tests/refactor_traditional.py'))
O=R/'build/mapper-context-tests';O.mkdir(exist_ok=True)
sources={k:v for k,v in base['sources'].items() if not k.endswith('/TraditionalHostTest.java')}
for name in ['com/android/server/display/MiuiPhysicalBrightnessMappingStrategy.java','com/android/server/display/BrightnessMappingStrategy.java']:
    assert 'public int getMode(){return mode;}' in sources[name]
    sources[name]=sources[name].replace('public int getMode(){return mode;}','public boolean isForIdleMode(){return mode!=0;}')
sources['top/rongshangs/lumacurve/refactor/MapperContextTest.java']=r'''package top.rongshangs.lumacurve.refactor;
import java.util.*;import org.json.*;import android.hardware.display.BrightnessConfiguration;
import com.android.server.display.MiuiPhysicalBrightnessMappingStrategy;
import com.android.server.display.BrightnessMappingStrategy.PhysicalMappingStrategy;
import top.rongshangs.lumacurve.refactor.hooking.XposedBridge;
public class MapperContextTest {
 static int cases;interface Work{void run()throws Exception;}
 static void check(boolean v){if(!v)throw new AssertionError("context "+cases);cases++;}
 static void near(float a,float b){check(Math.abs(a-b)<.001f);}
 static void bad(Work w)throws Exception{try{w.run();throw new AssertionError("invalid context accepted "+cases);}catch(ReflectiveOperationException|IllegalArgumentException|IllegalStateException expected){cases++;}}
 static Object read(Object o,String n)throws Exception{return TraditionalAdapter.read(o,n);}
 static void set(Object o,String n,Object v)throws Exception{TraditionalAdapter.find(o.getClass(),n).set(o,v);}
 static Object call(Object o,String n,Class<?>[] t,Object...a){return XposedBridge.call(o,n,t,a);}
 public static class Owner{int mDisplayId;boolean mUseAutoBrightness=true;Object mBrightnessMapper;}
 public static class Model{float anchor=-1;boolean valid;}
 public static class LegacyABC {
  Object mCurrentBrightnessMapper,mInteractiveModeBrightnessMapper,mIdleModeBrightnessMapper;Model mShortTermModel=new Model();int resets;
  public void resetShortTermModel(){resets++;call(mCurrentBrightnessMapper,"clearUserDataPoints",new Class<?>[]{});mShortTermModel.anchor=-1;mShortTermModel.valid=false;}
  public boolean setScreenBrightnessByUser(float lux,float b){call(mCurrentBrightnessMapper,"addUserDataPoint",new Class<?>[]{float.class,float.class},lux,b);mShortTermModel.anchor=lux;mShortTermModel.valid=true;return true;}
 }
 public static class LegacyMapper{boolean idle;int calls;boolean fail;public boolean isForIdleMode(){calls++;if(fail)throw new IllegalStateException("native mode failure");return idle;}}
 public static class ModernMapper extends LegacyMapper{int mode;boolean broken;public int getMode(){if(broken)throw new IllegalStateException("modern mode failure");return mode;}}
 public static class WrongMode extends LegacyMapper{public String getMode(){return "0";}}
 public static class StaticMode extends LegacyMapper{public static int getMode(){return 0;}}
 public static class Map0{Object mapper;public Object get(int i){return i==0?mapper:null;}}
 public static class ModernABC extends LegacyABC{Map0 mBrightnessMappingStrategyMap=new Map0();}
 public static class StaticMap extends LegacyABC{static Map0 mBrightnessMappingStrategyMap=new Map0();}
 static LegacyABC abc(Owner o,Object m){LegacyABC a=new LegacyABC();a.mCurrentBrightnessMapper=a.mInteractiveModeBrightnessMapper=o.mBrightnessMapper=m;return a;}
 static BrightnessConfiguration config(){return new BrightnessConfiguration(new float[]{0,1,30,600,5000},new float[]{2,3,30,120,1000},new HashMap<>(),new HashMap<>(),"native metadata",true,300000,.6f,.8f);}
 static void integration(boolean aosp)throws Exception {
  Owner o=new Owner();BrightnessConfiguration original=config();Object m=aosp?new PhysicalMappingStrategy(original):new MiuiPhysicalBrightnessMappingStrategy(original);
  LegacyABC abc=abc(o,m);TraditionalAdapter a=new TraditionalAdapter(o,m,abc,()->0);
  check(a.context.route.equals("interactive_idle")&&a.memoryContextActive());check(read(m,"mConfig")==original&&abc.resets==0);
  check(a.manualPointCapacity()==1&&a.persistentMemorySupported());check(a.fullLux().length==5&&a.fullNit()[4]==1000);
  a.configure(new float[]{1,1,1,1});check(read(m,"mConfig")==original);a.configure(new float[]{1,2,2,1});near(a.currentAt(30),60);check(read(m,"mConfig")!=original);
  BrightnessConfiguration changed=(BrightnessConfiguration)read(m,"mConfig");check(changed.mDescription.equals(original.mDescription)&&changed.mShortTermModelTimeout==original.mShortTermModelTimeout);
  HookRuntime state=new HookRuntime();state.kernel=a;state.owner=o;state.memoryStrength=1;HookEntry.states.put(m,state);a.installMappingHooks(m.getClass());
  Class<?>[] drag=aosp?new Class<?>[]{float.class,float.class}:new Class<?>[]{float.class,float.class,String.class};
  call(m,"addUserDataPoint",drag,aosp?new Object[]{30f,.8f}:new Object[]{30f,.8f,"video"});check(state.events==1&&a.manualPoints().length()==1);
  near(a.manualDisplayValue(.8f),800);a.clearMemory();check(a.manualPoints().length()==0);
  a.restoreManualPoints(new JSONArray().put(new JSONObject().put("lux",30).put("value",.6)));check(abc.mShortTermModel.anchor==30&&abc.mShortTermModel.valid);near(a.manualDisplayValue((float)a.manualPoints().getJSONObject(0).getDouble("value")),600);
  Object sleep=aosp?new PhysicalMappingStrategy(original):new MiuiPhysicalBrightnessMappingStrategy(original);set(sleep,"mode",1);abc.mIdleModeBrightnessMapper=abc.mCurrentBrightnessMapper=sleep;
  check(!a.memoryContextActive());int resets=abc.resets,events=state.events;a.configure(new float[]{1,1.5f,1.5f,1});check(abc.resets==resets&&abc.mCurrentBrightnessMapper==sleep);
  call(m,"addUserDataPoint",drag,aosp?new Object[]{30f,.7f}:new Object[]{30f,.7f,"video"});check(state.events==events);
  bad(()->a.restoreManualPoints(new JSONArray().put(new JSONObject().put("lux",30).put("value",.5))));
  abc.mCurrentBrightnessMapper=m;check(a.memoryContextActive());JSONObject status=new JSONObject();a.putCurveState(status);check(status.getString("physical_context_route").equals("interactive_idle"));
  Object before=read(m,"mConfig");set(m,"fail",true);bad(()->a.configure(new float[]{1,.7f,.8f,1}));check(read(m,"mConfig")==before);set(m,"fail",false);
  a.configure(null);check(read(m,"mConfig")==original&&a.plan()==null);check(a.fullNit()[4]==1000);
  o.mBrightnessMapper=sleep;check(!a.memoryContextActive());bad(()->new TraditionalAdapter(o,m,abc,()->0));
 }
 public static void main(String[] args)throws Exception {
  Owner o=new Owner();LegacyMapper m=new LegacyMapper();LegacyABC a=abc(o,m);MapperContextAccess c=new MapperContextAccess(o,m,a);check(c.active()&&c.legacy);
  LegacyMapper idle=new LegacyMapper();idle.idle=true;a.mIdleModeBrightnessMapper=a.mCurrentBrightnessMapper=idle;check(!c.active());check(new MapperContextAccess(o,m,a).legacy);
  a.mCurrentBrightnessMapper=m;idle.idle=false;bad(()->new MapperContextAccess(o,m,a));idle.idle=true;
  a.mIdleModeBrightnessMapper=m;bad(()->new MapperContextAccess(o,m,a));a.mIdleModeBrightnessMapper=idle;
  a.mCurrentBrightnessMapper=new Object();bad(()->new MapperContextAccess(o,m,a));a.mCurrentBrightnessMapper=m;
  a.mInteractiveModeBrightnessMapper=idle;bad(()->new MapperContextAccess(o,m,a));a.mInteractiveModeBrightnessMapper=m;
  o.mBrightnessMapper=idle;bad(()->new MapperContextAccess(o,m,a));o.mBrightnessMapper=m;
  o.mDisplayId=1;bad(()->new MapperContextAccess(o,m,a));o.mDisplayId=0;m.fail=true;bad(()->new MapperContextAccess(o,m,a));m.fail=false;
  ModernMapper modern=new ModernMapper();ModernABC ma=new ModernABC();ma.mCurrentBrightnessMapper=ma.mInteractiveModeBrightnessMapper=o.mBrightnessMapper=modern;ma.mBrightnessMappingStrategyMap.mapper=modern;
  MapperContextAccess mc=new MapperContextAccess(o,modern,ma);check(mc.route.equals("mode_map")&&mc.active()&&modern.calls==0);
  modern.mode=1;bad(()->new MapperContextAccess(o,modern,ma));modern.mode=0;modern.broken=true;bad(()->new MapperContextAccess(o,modern,ma));check(modern.calls==0);modern.broken=false;
  ma.mBrightnessMappingStrategyMap.mapper=m;bad(()->new MapperContextAccess(o,modern,ma));ma.mBrightnessMappingStrategyMap=null;bad(()->new MapperContextAccess(o,modern,ma));
  bad(()->new MapperContextAccess(o,modern,abc(o,modern))); // Mode marker alone is not a legacy profile.
  ma.mBrightnessMappingStrategyMap=new Map0();ma.mBrightnessMappingStrategyMap.mapper=m;ma.mCurrentBrightnessMapper=ma.mInteractiveModeBrightnessMapper=o.mBrightnessMapper=m;
  bad(()->new MapperContextAccess(o,m,ma)); // Map marker alone must not bypass a missing modern method.
  WrongMode wrong=new WrongMode();ModernABC wa=new ModernABC();wa.mBrightnessMappingStrategyMap.mapper=wa.mCurrentBrightnessMapper=wrong;bad(()->new MapperContextAccess(o,wrong,wa));check(wrong.calls==0);
  StaticMode stat=new StaticMode();wa.mBrightnessMappingStrategyMap.mapper=wa.mCurrentBrightnessMapper=stat;bad(()->new MapperContextAccess(o,stat,wa));
  StaticMap sm=new StaticMap();bad(()->new MapperContextAccess(o,modern,sm));
  integration(false);integration(true);
  System.out.println("Mapper context: "+cases+" cases PASS; old/new ABI, idle, ownership, learning and rollback; host only");
 }
}'''
paths=[]
for name,value in sources.items():
    p=O/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(value,'utf-8');paths.append(str(p))
C=O/'classes';C.mkdir(exist_ok=True);cp=str(base['C'])+os.pathsep+str(base['J'])
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',cp,'-d',str(C),*paths],check=True)
subprocess.run(['java','-cp',str(C)+os.pathsep+cp,'top.rongshangs.lumacurve.refactor.MapperContextTest'],check=True)
