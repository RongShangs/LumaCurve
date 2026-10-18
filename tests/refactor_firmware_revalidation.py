"""Exercise OTA baseline checks using production code, including the reported package."""
from pathlib import Path
import subprocess,json
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor';O=R/'build/firmware-revalidation-tests';O.mkdir(exist_ok=True);J=R/'build/refactor-diagnostics/json-20240303.jar'
p=O/'FirmwareTest.java';p.write_text(r'''package top.rongshangs.lumacurve.refactor;
import org.json.*;
public class FirmwareTest{
 static int cases;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static void reject(JSONObject c,String fp,String backend,String base){try{ConfigurationCompatibility.revalidate(c,fp,backend,base);throw new AssertionError("accepted");}catch(IllegalArgumentException expected){cases++;}}
 public static void main(String[] args)throws Exception{
  JSONObject c=new JSONObject().put("fingerprint","Vendor/product/device:17/old/build:user/keys").put("baseline_id","baseline").put("curve_backend","physical_mapping");
  check(!ConfigurationCompatibility.revalidate(c,c.getString("fingerprint"),"physical_mapping","baseline"));
  for(String version:new String[]{"new","changed-version","next-ota","custom-port"})check(ConfigurationCompatibility.revalidate(c,"Vendor/product/device:17/"+version+"/build:user/keys","physical_mapping","baseline"));
  reject(c,"Vendor/product/device:17/new/build:user/keys","physical_mapping","changed");reject(c,"Vendor/product/device:17/new/build:user/keys","refactor","baseline");reject(c,"Vendor/product/other:17/new/build:user/keys","physical_mapping","baseline");reject(c,"malformed","physical_mapping","baseline");
  JSONObject actual=new JSONObject().put("curve_backend","physical_mapping").put("baseline_id","baseline").put("user_serial",0);
  c.put("memory_persist",false).put("memory_restore_unlock",false).put("user_serial",0);
  String ota="Vendor/product/device:17/new/build:user/keys";
  check(ConfigurationCompatibility.canReuse(c,actual,ota));check(!c.getBoolean("memory_persist")&&!c.getBoolean("memory_restore_unlock"));
  check(ConfigurationCompatibility.canReuse(c,actual,c.getString("fingerprint")));
  for(String key:new String[]{"baseline_id","curve_backend","user_serial"}){JSONObject changed=new JSONObject(actual.toString());changed.put(key,key.equals("user_serial")?1:"changed");check(!ConfigurationCompatibility.canReuse(c,changed,ota));}
  check(!ConfigurationCompatibility.canReuse(null,actual,ota));check(!ConfigurationCompatibility.canReuse(c,null,ota));
  c.remove("baseline_id");reject(c,"Vendor/product/device:17/new/build:user/keys","physical_mapping","baseline");
  System.out.println("Firmware revalidation: "+cases+" cases PASS; actual policy, Android not tested");
 }
}''',encoding='utf-8')
c=O/'classes';c.mkdir(exist_ok=True);subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(c),str(p),str(S/'ConfigurationCompatibility.java'),str(S/'MemoryOptions.java')],check=True)
subprocess.run(['java','-cp',str(c)+';'+str(J),'top.rongshangs.lumacurve.refactor.FirmwareTest'],check=True)
fixture=R/'build/analysis-20261015-131612-99130d/state.json'
if fixture.exists():
 data=json.loads(fixture.read_text(encoding='utf-8'));s=data['runtime'];old=data['config']
 assert s['fingerprint']!=old['fingerprint'] and s['baseline_id']==old['baseline_id'] and s['curve_backend']==old['curve_backend'] and s['user_serial']==old['user_serial']
 print('Supplied OTA package: same baseline/backend/user; old fingerprint-only rejection reproduced')
