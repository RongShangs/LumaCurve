"""Run actual fold resolver and native gates against filesystem fixtures, not a device."""
from pathlib import Path
import os,subprocess,tempfile
R=Path(__file__).resolve().parents[1];S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/fold-panel-tests';O.mkdir(exist_ok=True)
F=Path(tempfile.mkdtemp(prefix='sys-',dir=O));J=R/'build/refactor-diagnostics/json-20240303.jar'
for port in [0,1]:
 device=F/f'devices/controller/backlight/panel{port}-backlight';device.mkdir(parents=True)
 (device/'brightness').write_text('100');(device/'max_brightness').write_text('16383')
 alias=F/f'class/backlight/panel{port}-backlight';alias.parent.mkdir(parents=True,exist_ok=True)
 if os.name=='nt':subprocess.run(['powershell','-NoProfile','-Command',f"New-Item -ItemType Junction -Path '{alias}' -Target '{device}' | Out-Null"],check=True,capture_output=True)
 else:alias.symlink_to(device,target_is_directory=True)
 folder=F/f'class/drm/card0-DSI-{port+1}';folder.mkdir(parents=True)
 for key,value in [('status','connected'),('enabled','disabled'),('dpms','Off')]:(folder/key).write_text(value)
(F/'class/leds').mkdir()
stub=O/'android/view/DisplayAddress.java';stub.parent.mkdir(parents=True,exist_ok=True)
stub.write_text(r'''package android.view;
public abstract class DisplayAddress{
 public static class Physical extends DisplayAddress{final long id;public Physical(long id){this.id=id;}public long getPhysicalDisplayId(){return id;}public int getPort(){return (int)(id&255);}}
 public static class StablePhysical extends DisplayAddress{final long id;final int port;public static boolean fail;public StablePhysical(long id,int port){this.id=id;this.port=port;}public long getPhysicalDisplayId(){if(fail)throw new IllegalStateException("native getter failed");return id;}public int getPort(){return port;}}
 public static class Network extends DisplayAddress{public long getPhysicalDisplayId(){return 257;}public int getPort(){return 1;}}
}''',encoding='utf-8')
info=O/'android/view/DisplayInfo.java';info.write_text('package android.view;public class DisplayInfo{public int type=1,state=2;public String uniqueId;public Object address;public DisplayInfo(String id,Object a){uniqueId=id;address=a;}}',encoding='utf-8')
global_source=O/'android/hardware/display/DisplayManagerGlobal.java';global_source.parent.mkdir(parents=True,exist_ok=True);global_source.write_text('package android.hardware.display;public class DisplayManagerGlobal{public static android.view.DisplayInfo info;private static final DisplayManagerGlobal instance=new DisplayManagerGlobal();public static DisplayManagerGlobal getInstance(){return instance;}public android.view.DisplayInfo getDisplayInfo(int id){if(id!=0)throw new AssertionError();return info;}}',encoding='utf-8')
p=O/'FoldPanelTest.java'
p.write_text(r'''package top.rongshangs.lumacurve.refactor;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import org.json.*;
public class FoldPanelTest{
 static int cases;static File sys,layout;static void check(boolean b){if(!b)throw new AssertionError("case "+cases);cases++;}
 static void write(String path,String value)throws Exception{Files.write(new File(sys,path).toPath(),value.getBytes(StandardCharsets.UTF_8));}
 static JSONObject display(int port)throws Exception{return new JSONObject().put("physical_id",256+port).put("unique_id","local:"+(256+port)).put("port",port);}
 static void on(int port)throws Exception{for(int i=0;i<2;i++){write("class/drm/card0-DSI-"+(i+1)+"/enabled",i==port?"enabled":"disabled");write("class/drm/card0-DSI-"+(i+1)+"/dpms",i==port?"On":"Off");write("class/backlight/panel"+i+"-backlight/brightness",i==port?"912":"0");}}
 static void layouts(boolean fold)throws Exception{String xml="<layouts><layout><display enabled='true' defaultDisplay='true'><address>256</address></display></layout>"+(fold?"<layout><display enabled='true' defaultDisplay='true'><address>257</address></display></layout>":"")+"</layouts>";Files.write(layout.toPath(),xml.getBytes(StandardCharsets.UTF_8));}
 static JSONObject resolve(int port)throws Exception{return ActivePanelNode.discover(sys,layout,()->display(port));}
 public static void main(String[] args)throws Exception{
  sys=new File(args[0]);layout=new File(sys,"layout.xml");layouts(true);
  for(int port:new int[]{1,0,1,0}){on(port);JSONObject node=resolve(port);check(node.getBoolean("supported"));check(node.getInt("fold_port")==port&&node.getInt("maximum")==16383&&node.getInt("actual")==912);check(node.getString("physical_display_id").equals("local:"+(256+port)));check(node.getString("canonical_path").contains("panel"+port+"-backlight"));}
  on(1);check(!resolve(0).getBoolean("supported"));write("class/drm/card0-DSI-1/enabled","enabled");write("class/drm/card0-DSI-1/dpms","On");check(!resolve(1).getBoolean("supported"));
  on(1);write("class/backlight/panel1-backlight/brightness","0");check(!resolve(1).getBoolean("supported"));on(1);
  int[] reads={0};check(!ActivePanelNode.discover(sys,layout,()->display(reads[0]++==0?1:0)).getBoolean("supported"));
  check(!ActivePanelNode.discover(sys,layout,()->new JSONObject().put("physical_id",999).put("unique_id","local:999").put("port",1)).getBoolean("supported"));
  write("class/backlight/panel1-backlight/max_brightness","5");check(!resolve(1).getBoolean("supported"));write("class/backlight/panel1-backlight/max_brightness","16383");
  layouts(false);JSONObject legacy=ActivePanelNode.discover(sys,layout,()->{throw new AssertionError("ordinary route must not read fold display");});check(legacy.getString("name").equals("panel0-backlight"));
  check(ActivePanelNode.discover(sys,null,()->{throw new AssertionError();}).getString("name").equals("panel0-backlight"));
  Files.write(layout.toPath(),"<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///private'>]><layouts/>".getBytes(StandardCharsets.UTF_8));check(!resolve(1).getBoolean("supported"));
  // Exercise the real framework lookup, including sibling StablePhysical / Physical classes.
  layouts(true);
  for(int port:new int[]{0,1})for(boolean stable:new boolean[]{false,true}){
   on(port);android.view.DisplayAddress a=stable?new android.view.DisplayAddress.StablePhysical(256+port,port):new android.view.DisplayAddress.Physical(256+port);
   android.hardware.display.DisplayManagerGlobal.info=new android.view.DisplayInfo("local:"+(256+port),a);
   JSONObject actual=ActivePanelNode.discover(sys,layout,ActivePanelNode::display);
   check(actual.getBoolean("supported")&&actual.getInt("fold_port")==port);check(actual.getString("address_profile").equals(stable?"stable_physical":"physical"));
  }
  // Stable IDs deliberately do not encode the physical port in their low bits.
  Files.write(layout.toPath(),"<layouts><layout><display enabled='true' defaultDisplay='true'><address>123456</address></display></layout><layout><display enabled='true' defaultDisplay='true'><address>987654</address></display></layout></layouts>".getBytes(StandardCharsets.UTF_8));
  for(int port:new int[]{0,1}){on(port);long id=port==0?123456:987654;android.hardware.display.DisplayManagerGlobal.info=new android.view.DisplayInfo("local:"+id,new android.view.DisplayAddress.StablePhysical(id,port));JSONObject actual=ActivePanelNode.discover(sys,layout,ActivePanelNode::display);check(actual.getBoolean("supported")&&actual.getInt("fold_port")==port&&actual.getString("physical_display_id").equals("local:"+id));}
  for(Object a:new Object[]{null,new android.view.DisplayAddress.Network(),new Object()}){
   android.hardware.display.DisplayManagerGlobal.info=new android.view.DisplayInfo("local:987654",a);check(!ActivePanelNode.discover(sys,layout,ActivePanelNode::display).getBoolean("supported"));
  }
  android.hardware.display.DisplayManagerGlobal.info=new android.view.DisplayInfo("local:wrong",new android.view.DisplayAddress.StablePhysical(987654,1));check(!ActivePanelNode.discover(sys,layout,ActivePanelNode::display).getBoolean("supported"));
  android.hardware.display.DisplayManagerGlobal.info=new android.view.DisplayInfo("local:987654",new android.view.DisplayAddress.StablePhysical(987654,1));android.view.DisplayAddress.StablePhysical.fail=true;check(!ActivePanelNode.discover(sys,layout,ActivePanelNode::display).getBoolean("supported"));android.view.DisplayAddress.StablePhysical.fail=false;
  android.hardware.display.DisplayManagerGlobal.info.type=2;check(!ActivePanelNode.discover(sys,layout,ActivePanelNode::display).getBoolean("supported"));
  // Native composer ports 131 / 132 are not sysfs panel ordinals. No offset table.
  long[] physical={4630947024259405955L,4630947024259405956L};
  Files.write(layout.toPath(),("<layouts><layout><display enabled='true' defaultDisplay='true'><address>"+physical[0]+"</address></display></layout><layout><display enabled='true' defaultDisplay='true'><address>"+physical[1]+"</address></display></layout></layouts>").getBytes(StandardCharsets.UTF_8));
  for(int port:new int[]{1,0,1,0}){
   on(port);android.hardware.display.DisplayManagerGlobal.info=new android.view.DisplayInfo("local:"+physical[port],new android.view.DisplayAddress.Physical(physical[port]));
   JSONObject actual=ActivePanelNode.discover(sys,layout,ActivePanelNode::display);
   check(actual.getBoolean("supported")&&actual.getInt("fold_port")==port);check(actual.getInt("physical_port")==131+port&&actual.getString("node_binding").equals("exclusive_internal_dsi"));
   // Default display off/unknown cannot borrow another internal screen's live node.
   for(int state:new int[]{0,1,3,4}){android.hardware.display.DisplayManagerGlobal.info.state=state;check(!ActivePanelNode.discover(sys,layout,ActivePanelNode::display).getBoolean("supported"));}
   android.hardware.display.DisplayManagerGlobal.info.state=2;
  }
  on(1);write("class/drm/card0-DSI-1/enabled","enabled");write("class/drm/card0-DSI-1/dpms","On");check(!ActivePanelNode.discover(sys,layout,ActivePanelNode::display).getBoolean("supported"));
  on(1);write("class/backlight/panel1-backlight/brightness","0");check(!ActivePanelNode.discover(sys,layout,ActivePanelNode::display).getBoolean("supported"));on(1);
  int[] changing={0};check(!ActivePanelNode.discover(sys,layout,()->{JSONObject value=ActivePanelNode.display();if(changing[0]++>0)value.put("display_state",1);return value;}).getBoolean("supported"));
  changing[0]=0;check(!ActivePanelNode.discover(sys,layout,()->{int which=changing[0]++==0?1:0;return new JSONObject().put("physical_id",physical[which]).put("unique_id","local:"+physical[which]).put("port",131+which).put("display_state",2);}).getBoolean("supported"));
  System.out.println("Fold panel resolver: "+cases+" cases PASS; host filesystem, Android not tested");
 }
}''',encoding='utf-8')
C=O/'classes';C.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(J),'-d',str(C),str(S/'PanelNodeDiscovery.java'),str(S/'ActivePanelNode.java'),str(stub),str(info),str(global_source),str(p)],check=True)
subprocess.run(['java','-cp',str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.FoldPanelTest',str(F)],check=True)
c=O/'fold.c';c.write_text(r'''
#include "panel_node.h"
#include "fold_panel.h"
static int cases;
#define CHECK(v) do{if(!(v)){fprintf(stderr,"case %d\n",cases);return 1;}cases++;}while(0)
int main(int argc,char **argv){CHECK(argc==2);CHECK(fold_panel_live(argv[1],1));CHECK(!fold_panel_live(argv[1],0));CHECK(!fold_panel_live(argv[1],8));
 const char *path="/sys/class/backlight/panel1-backlight/brightness";CHECK(!panel_class_path(path));CHECK(panel_fold_path(path,1));CHECK(!panel_fold_path(path,0));CHECK(!panel_fold_path("/sys/class/leds/panel1-backlight/brightness",1));
 struct panel_permissions record={.mode=0664,.device=3,.inode=4,.fold_route=1,.fold_port=1},parsed;strcpy(record.boot,"5a30f9e3-021b-4ec1-8131-47f151ebb7aa");strcpy(record.path,path);strcpy(record.real,"/sys/devices/controller/panel1/brightness");char text[1200];
 CHECK(panel_permissions_format(text,sizeof(text),&record)>0);CHECK(panel_permissions_parse(text,&parsed));CHECK(parsed.fold_route&&parsed.fold_port==1&&!strcmp(parsed.path,path));CHECK(panel_permissions_matches(&parsed,record.real,3,4));CHECK(!panel_permissions_matches(&parsed,record.real,3,5));
 record.fold_port=0;panel_permissions_format(text,sizeof(text),&record);CHECK(!panel_permissions_parse(text,&parsed));record.fold_route=0;panel_permissions_format(text,sizeof(text),&record);CHECK(!panel_permissions_parse(text,&parsed));
 printf("Fold panel native gates: %d cases PASS; host policy, no kernel writes\n",cases);return 0;
}''',encoding='utf-8')
exe=O/'fold.exe';subprocess.run(['C:/msys64/mingw64/bin/gcc.exe','-std=c11','-static','-Wall','-Wextra','-Werror','-Wno-misleading-indentation','-I',str(R/'experimental/refactor_hook/native'),str(c),'-o',str(exe)],check=True)
subprocess.run([str(exe),str(F)],check=True)
# Freeze the previous Physical-only mistake: the real StablePhysical lookup must fail.
negative=O/'physical-only/ActivePanelNode.java';negative.parent.mkdir(exist_ok=True)
fixed=(S/'ActivePanelNode.java').read_text(encoding='utf-8');old=fixed.replace('boolean stable=name.equals("android.view.DisplayAddress$StablePhysical");','boolean stable=name.equals("android.view.DisplayAddress$StablePhysical");if(stable)throw new IOException("default_display_not_physical");');assert old!=fixed;negative.write_text(old,encoding='utf-8')
N=negative.parent/'classes';N.mkdir(exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(C)+os.pathsep+str(J),'-d',str(N),str(negative)],check=True)
failed=subprocess.run(['java','-cp',str(N)+os.pathsep+str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.FoldPanelTest',str(F)],capture_output=True,text=True)
assert failed.returncode and 'AssertionError' in failed.stderr,'Prior StablePhysical rejection was not reproduced'
print('Previous Physical-only regression reproduced: PASS')
reject=O/'AddressAbiReject.java';reject.write_text(r'''package top.rongshangs.lumacurve.refactor;
public class AddressAbiReject{public static void main(String[] args)throws Exception{
 android.view.DisplayInfo i=new android.view.DisplayInfo("local:257",new android.view.DisplayAddress.Physical(257));if(!ActivePanelNode.address(i).getString("address_profile").equals("physical"))throw new AssertionError();
 i=new android.view.DisplayInfo("local:987654",new android.view.DisplayAddress.StablePhysical(987654,1));try{ActivePanelNode.address(i);throw new AssertionError("Partial stable ABI accepted");}catch(java.io.IOException expected){if(!expected.getMessage().contains("address_abi"))throw expected;}
 System.out.println("Partial stable address rejected; legacy Physical preserved: PASS");}}
''',encoding='utf-8')
for mode,replacement in [('wrong-return','public String getPort(){return "1";}'),('static-port','public static int getPort(){return 1;}')]:
 bad=O/mode/'android/view/DisplayAddress.java';bad.parent.mkdir(parents=True,exist_ok=True)
 body=stub.read_text(encoding='utf-8');assert 'public int getPort(){return port;}' in body
 bad.write_text(body.replace('public int getPort(){return port;}',replacement),encoding='utf-8');B=O/mode/'classes';B.mkdir(exist_ok=True)
 subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',str(C)+os.pathsep+str(J),'-d',str(B),str(bad),str(reject)],check=True)
 subprocess.run(['java','-cp',str(B)+os.pathsep+str(C)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.AddressAbiReject'],check=True)
print('Fold panel compatibility: 5 execution groups PASS; host fixtures, target device pending')
