"""Production OPR envelope and nonlinear nit inversion; host tests, no colorimeter."""
from pathlib import Path
import subprocess

R=Path(__file__).resolve().parents[1]
S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/refactor-opr-envelope-tests'; O.mkdir(exist_ok=True)
stubs={
'HookEntry.java':'''package top.rongshangs.lumacurve.refactor;import java.lang.reflect.*;class HookEntry{static Field field(Class<?> c,String n)throws Exception{for(;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException absent){}throw new NoSuchFieldException(n);}}''',
'OutdoorSdrScope.java':'''package top.rongshangs.lumacurve.refactor;import java.lang.reflect.*;class OutdoorSdrScope{static Field typed(Class<?> c,String n,Class<?> t)throws Exception{Field f=HookEntry.field(c,n);if(f.getType()!=t||Modifier.isStatic(f.getModifiers()))throw new IllegalStateException(n);return f;}}''',
'OutdoorPolicy.java':'''package top.rongshangs.lumacurve.refactor;class OutdoorPolicy{static boolean valid(float v){return Float.isFinite(v)&&v>=0&&v<=1;}}''',
'EnvelopeTest.java':r'''package top.rongshangs.lumacurve.refactor;
import java.util.*;
public class EnvelopeTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static void eq(float a,float b){check(Math.abs(a-b)<.000002f);}
 public static class Panel{
  boolean mOprBrightnessControlAvailable=true;int[] mOprGrayscaleThreshold={30,38,43,53,63,76,86,94,119,130,140,155,170,191};
  int[] mOprNitThreshold={4500,4000,3500,3000,2600,2200,2100,2000,1800,1700,1600,1500,1400,1300,1200};
  boolean fail,reverse;float end=10000;
  public float convertBrightnessToNit(float b){if(fail)throw new IllegalStateException("OEM conversion failed");if(reverse)return 10000*(1-b);return b<=.9766207f?1+4499*b/.9766207f:4500+(end-4500)*(b-.9766207f)/(1-.9766207f);}
 }
 public static class NoTable{public float convertBrightnessToNit(float b){return b*3500;}}
 public static class Partial{int[] mOprNitThreshold={4500};}
 public static class Wrong{boolean mOprBrightnessControlAvailable=true;int[] mOprGrayscaleThreshold={30};float[] mOprNitThreshold={4500,1200};}
 public static class Static{static boolean mOprBrightnessControlAvailable=true;int[] mOprGrayscaleThreshold={30},mOprNitThreshold={4500,1200};}
 public static class Child extends Panel{}
 static void reject(Object o)throws Exception{try{new OutdoorOprEnvelope(o);throw new AssertionError("accepted bad ABI");}catch(IllegalStateException expected){cases++;}}
 public static void main(String[] args)throws Exception{
  Panel p=new Panel();OutdoorOprEnvelope e=new OutdoorOprEnvelope(p);
  check(e.profile.equals("unconfirmed"));eq(e.bound(1,.51880115f,.000366256f,1),.9766207f);
  check(e.profile.equals("native_opr_envelope"));eq(e.maximumNit,4500);
  check(p.mOprNitThreshold[0]==4500&&p.mOprGrayscaleThreshold[0]==30);
  eq(e.bound(.7f,.51880115f,.000366256f,1),.7f);
  eq(e.bound(1,.99f,.000366256f,1),.99f); // Never reduce already-higher OEM result.
  for(int i=0;i<=1000;i++){
   float nativeB=i/1000f,proposal=Math.max(nativeB,.999f);
   float actual=e.bound(proposal,nativeB,.000366256f,1);
   check(actual>=nativeB&&actual<=proposal);
   if(nativeB<=.9766207f)check(p.convertBrightnessToNit(actual)<=4500.01f);
  }
  int[] old=p.mOprNitThreshold;for(int peak:new int[]{1200,2000,3500,4500,10000}){
   p.mOprNitThreshold=new int[old.length];Arrays.fill(p.mOprNitThreshold,peak);
   float result=e.bound(1,.01f,.000366256f,1);check(p.convertBrightnessToNit(result)<=peak+.01f);check(result>=.01f);
  }
  p.mOprNitThreshold=old;
  for(int[] invalid:new int[][]{null,{},new int[]{4500},new int[]{4500,-1},new int[]{1200,4500}}){p.mOprNitThreshold=invalid;eq(e.bound(1,.5f,.000366256f,1),.5f);check(e.profile.equals("native_limit_retained"));}
  p.mOprNitThreshold=old;int[] oldX=p.mOprGrayscaleThreshold;
  for(int[] invalid:new int[][]{null,{},new int[]{30,30},new int[]{-1},new int[]{256}}){p.mOprGrayscaleThreshold=invalid;eq(e.bound(1,.5f,.000366256f,1),.5f);}
  p.mOprGrayscaleThreshold=oldX;
  p.mOprBrightnessControlAvailable=false;eq(e.bound(1,.5f,0,1),.5f);p.mOprBrightnessControlAvailable=true;
  p.fail=true;eq(e.bound(1,.5f,0,1),.5f);p.fail=false;
  p.reverse=true;eq(e.bound(1,.5f,0,1),.5f);p.reverse=false;
  eq(e.bound(1,.5f,Float.NaN,1),.5f);eq(e.bound(1,.5f,1,1),.5f);
  Arrays.fill(p.mOprNitThreshold,11000);eq(e.bound(1,.5f,0,1),.5f);p.mOprNitThreshold=old.clone();
  // Above fill mutates old too: restore the real table explicitly.
  p.mOprNitThreshold=new int[]{4500,4000,3500,3000,2600,2200,2100,2000,1800,1700,1600,1500,1400,1300,1200};
  eq(e.bound(1,.5f,.000366256f,1),.9766207f);check(e.error.isEmpty());
  OutdoorOprEnvelope other=new OutdoorOprEnvelope(new Child());eq(other.bound(1,.5f,.000366256f,1),.9766207f);
  NoTable n=new NoTable();OutdoorOprEnvelope absent=new OutdoorOprEnvelope(n);eq(absent.bound(1,.5f,0,1),1);check(absent.profile.equals("mapping_only"));
  reject(new Partial());reject(new Wrong());reject(new Static());
  check(other.owner!=e.owner);eq(absent.bound(.8f,.6f,0,1),.8f);
  System.out.println("OPR envelope: "+cases+" cases PASS");
 }
}'''
}
for name,body in stubs.items():(O/name).write_text(body,encoding='utf-8')
subprocess.run(['javac','-encoding','UTF-8','--release','8','-d',str(O),*[str(O/n) for n in stubs],str(S/'OutdoorOprEnvelope.java'),str(S/'NitBrightnessLimit.java')],check=True)
subprocess.run(['java','-cp',str(O),'top.rongshangs.lumacurve.refactor.EnvelopeTest'],check=True)
c=(S/'OutdoorController.java').read_text(encoding='utf-8')
assert 'oprEnvelope.bound(proposed,limited' in c
assert 'new OutdoorOprEnvelope(s.owner)' in c
assert 'opr_envelope_max_nit' in c
assert 'model' not in (S/'OutdoorOprEnvelope.java').read_text(encoding='utf-8').lower()
