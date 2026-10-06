"""Production HyperOS preflight parsing; no Android properties or permissions simulated."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[1]
src=root/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
out=root/'build/refactor-system-version';out.mkdir(exist_ok=True)
test=out/'SystemVersionTest.java'
test.write_text('''package top.rongshangs.lumacurve.refactor;
import org.json.*;
public class SystemVersionTest {
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static SystemVersion.Result read(String name,String incremental){return SystemVersion.inspect(key->key.equals(SystemVersion.NAME)?name:incremental);}
 public static void main(String[] args)throws Exception{
  for(String value:new String[]{"OS4","OS4.0.0.41.XBLCNXM","OS4.0.0.28.XPACNXM","4.0.0.41","HyperOS 4"," os4.0.0.41 "}){check(SystemVersion.major(value)==4);check(read(value,"").status.equals("supported"));}
  for(String value:new String[]{"OS3","OS3.0.0.1","OS2.0.0.23","OS5.0.0.1","OS40"}){check(SystemVersion.major(value)!=4);check(read(value,"").unsupported());}
  for(String value:new String[]{"","V14","Android 16","Android 17","OS","OS4beta","garbage OS4",null}){check(SystemVersion.major(value)==-1);check(!read(value,"").unsupported());check(read(value,"").status.equals("unknown"));}
  check(read("","OS3.0.0.1").unsupported());check(read(null,"OS4.0.0.41").major==4);check(read("OS4","OS4.0.0.41").major==4);
  check(read("OS4","OS3.0.0.41").status.equals("unknown"));check(!read("OS4","OS3.0.0.41").unsupported());
  check(SystemVersion.inspect(key->{throw new java.io.IOException();}).status.equals("unknown"));
  JSONObject j=new JSONObject();read("OS3.0.0.1","").put(j);check(j.getString("system_compatibility").equals("unsupported"));check(j.getInt("system_major")==3);check(j.getString("system_version").equals("OS3.0.0.1"));
  System.out.println("HyperOS platform preflight: "+cases+" cases PASS; parsing only, Android properties not tested");
 }
}''',encoding='utf-8')
json=root/'build/refactor-diagnostics/json-20240303.jar'
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(json),'-d',str(out),str(src/'SystemVersion.java'),str(test)],check=True)
subprocess.run(['java','-cp',str(out)+';'+str(json),'top.rongshangs.lumacurve.refactor.SystemVersionTest'],check=True)
