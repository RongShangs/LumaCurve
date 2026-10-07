"""Exercise production node discovery against filesystem fixtures and native record parsing."""
from pathlib import Path
import json, os, subprocess, tempfile
import re

R=Path(__file__).resolve().parents[1]
S=R/'experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor'
O=R/'build/panel-node-tests';O.mkdir(parents=True,exist_ok=True)
F=Path(tempfile.mkdtemp(prefix='fixtures-',dir=O))
J=R/'build/refactor-diagnostics/json-20240303.jar'
fixtures=[]
def case(name,nodes,expected,selected=None):
    root=F/name
    for group in ['backlight','leds']:(root/'class'/group).mkdir(parents=True,exist_ok=True)
    (root/'devices').mkdir(exist_ok=True)
    for i,item in enumerate(nodes):
        group,label,maxval,actual,*flags=item
        identity=flags[0] if flags else str(i)
        device=root/'devices'/identity;device.mkdir(exist_ok=True)
        if maxval is not None:(device/'max_brightness').write_text(str(maxval)+'\n',encoding='ascii')
        if actual is not None:(device/'brightness').write_text(str(actual)+'\n',encoding='ascii')
        alias=root/'class'/group/label
        if os.name=='nt':
            ps="New-Item -ItemType Junction -Path '"+str(alias).replace("'","''")+"' -Target '"+str(device).replace("'","''")+"' | Out-Null"
            subprocess.run(['powershell','-NoProfile','-Command',ps],check=True,capture_output=True)
        else:alias.symlink_to(device,target_is_directory=True)
    fixtures.append({'root':str(root),'expected':expected,'name':selected or ''})

case('qcom',[('backlight','panel0-backlight',16383,1225),('backlight','panel1-backlight',16383,477)],'identified_primary','panel0-backlight')
case('mtk',[('leds','lcd-backlight',4095,300),('leds','torch',255,0),('leds','red',255,0)],'identified_primary','lcd-backlight')
case('only-secondary',[('backlight','panel1-backlight',16383,20)],'no_primary_node')
case('only-lights',[('leds','notification',255,30),('leds','kbd-backlight',255,100)],'no_primary_node')
case('empty',[],'no_primary_node')
case('duplicate',[('backlight','panel0-backlight',16383,0,'same'),('leds','lcd-backlight',16383,0,'same')],'identified_primary','panel0-backlight')
case('two-displays',[('backlight','panel0-backlight',16383,20),('leds','lcd-backlight',4095,30)],'ambiguous_primary_nodes')
case('generic',[('backlight','vendor-bl',2047,30)],'unique_backlight','vendor-bl')
case('two-generic',[('backlight','vendor-a',2047,30),('backlight','vendor-b',2047,30)],'ambiguous_primary_nodes')
case('missing-maximum',[('leds','lcd-backlight',None,20)],'no_primary_node')
case('range-overflow',[('backlight','panel0-backlight',65536,20)],'no_primary_node')
case('current-overflow',[('backlight','panel0-backlight',255,256)],'no_primary_node')
case('tiny-range',[('leds','lcd-backlight',10,2)],'no_primary_node')
case('parse-error',[('leds','lcd-backlight','255 garbage',20)],'no_primary_node')
case('long-read',[('leds','lcd-backlight','1'*80,20)],'no_primary_node')
case('zero-current',[('leds','lcd-backlight',255,0)],'identified_primary','lcd-backlight')
case('65535',[('backlight','panel0-backlight',65535,65535)],'identified_primary','panel0-backlight')
case('numbered-secondary',[('backlight','display_10-backlight',65535,4)],'no_primary_node')
case('scan-cap',[('leds','led'+str(i),255,0) for i in range(65)],'scan_incomplete')

manifest=O/'fixtures.json';manifest.write_text(json.dumps(fixtures),encoding='utf-8')
test=O/'PanelNodeTest.java'
test.write_text(r'''package top.rongshangs.lumacurve.refactor;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import org.json.*;
public class PanelNodeTest{
 static int cases;static void check(boolean ok){if(!ok)throw new AssertionError("case "+cases);cases++;}
 public static void main(String[] args)throws Exception{
  JSONArray fixtures=new JSONArray(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8));
  for(int i=0;i<fixtures.length();i++){
   JSONObject f=fixtures.getJSONObject(i),out=PanelNodeDiscovery.discover(new File(f.getString("root")));
   String expected=f.getString("expected");if(!expected.equals(out.optString("reason")))throw new AssertionError(f+" got "+out);
   check(out.getString("reason").equals(expected));boolean support=expected.equals("identified_primary")||expected.equals("unique_backlight");check(out.getBoolean("supported")==support);
   if(support){check(out.getString("name").equals(f.getString("name")));check(out.getInt("maximum")>10);check(out.getInt("actual")>=0&&out.getInt("actual")<=out.getInt("maximum"));check(out.getString("canonical_path").contains("devices"));}
   else check(!out.has("path"));
  }
  check(!PanelNodeDiscovery.excluded("lcd-backlight"));check(PanelNodeDiscovery.excluded("Panel2-backlight"));check(!PanelNodeDiscovery.primary("torch"));
  System.out.println("Panel node discovery: "+cases+" cases PASS; host filesystem fixtures, Android not tested");
 }
}''',encoding='utf-8')
classes=O/'classes';classes.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','--release','8','-cp',str(J),'-d',str(classes),str(S/'PanelNodeDiscovery.java'),str(test)],check=True)
java_result=subprocess.run(['java','-cp',str(classes)+os.pathsep+str(J),'top.rongshangs.lumacurve.refactor.PanelNodeTest',str(manifest)],check=True,capture_output=True,text=True)
print(java_result.stdout,end='');java_cases=int(re.search(r'(\d+) cases PASS',java_result.stdout).group(1))

c=O/'node-test.c'
c.write_text(r'''
#include "panel_node.h"
static int cases;
#define CHECK(x) do {if(!(x)){fprintf(stderr,"case %d\n",cases);return 1;}cases++;}while(0)
int main(void){
 const char *good[]={"/sys/class/backlight/panel0-backlight/brightness","/sys/class/leds/lcd-backlight/brightness","/sys/class/backlight/vendor-bl/brightness","/sys/class/leds/mtk-lcd/brightness"};
 for(unsigned i=0;i<sizeof(good)/sizeof(good[0]);i++)CHECK(panel_class_path(good[i]));
 const char *bad[]={"/sys/class/backlight/panel1-backlight/brightness","/sys/class/leds/torch/brightness","/sys/class/leds/white/brightness","/sys/class/leds/button-backlight/brightness","/sys/class/backlight/display_10-backlight/brightness","/sys/class/backlight/../brightness","/data/brightness","/sys/class/backlight/panel0-backlight/actual_brightness","/sys/class/backlight/panel0-backlight/brightness/extra","/sys/class/leds/lcd-backlight/../../brightness"};
 for(unsigned i=0;i<sizeof(bad)/sizeof(bad[0]);i++)CHECK(!panel_class_path(bad[i]));
 struct panel_permissions r={.mode=0644,.device=23,.inode=456},parsed;strcpy(r.boot,"5a30f9e3-021b-4ec1-8131-47f151ebb7aa");strcpy(r.path,good[1]);strcpy(r.real,"/sys/devices/platform/leds/lcd-backlight/brightness");char text[1200];
 CHECK(panel_permissions_format(text,sizeof(text),&r)>0);CHECK(panel_permissions_parse(text,&parsed));CHECK(parsed.mode==0644&&parsed.device==23&&parsed.inode==456&&!parsed.legacy);CHECK(!strcmp(parsed.path,r.path)&&!strcmp(parsed.real,r.real)&&!strcmp(parsed.boot,r.boot));
 CHECK(!panel_permissions_new_boot(&parsed,r.boot));CHECK(panel_permissions_new_boot(&parsed,"5a30f9e3-021b-4ec1-8131-47f151ebb7ab"));
 CHECK(panel_permissions_matches(&parsed,r.real,23,456));CHECK(!panel_permissions_matches(&parsed,r.real,24,456));CHECK(!panel_permissions_matches(&parsed,r.real,23,457));CHECK(!panel_permissions_matches(&parsed,"/sys/devices/other/brightness",23,456));CHECK(!panel_permissions_matches(&parsed,"/data/brightness",23,456));
 CHECK(panel_permissions_parse("664\n",&parsed)&&parsed.legacy&&parsed.mode==0664&&!strcmp(parsed.path,PANEL_LEGACY_NODE));
 CHECK(!panel_permissions_new_boot(&parsed,r.boot));CHECK(panel_permissions_matches(&parsed,"/sys/devices/panel0/brightness",0,0));CHECK(!panel_permissions_matches(&parsed,"/data/brightness",0,0));
 CHECK(!panel_permissions_parse("644 garbage",&parsed));CHECK(!panel_permissions_parse("v2\n644\n23 456\n",&parsed));CHECK(!panel_permissions_parse("1000\n",&parsed));
 strcat(text,"extra");CHECK(!panel_permissions_parse(text,&parsed));
 strcpy(r.path,bad[0]);panel_permissions_format(text,sizeof(text),&r);CHECK(!panel_permissions_parse(text,&parsed));
 strcpy(r.path,good[1]);strcpy(r.real,"/data/brightness");panel_permissions_format(text,sizeof(text),&r);CHECK(!panel_permissions_parse(text,&parsed));
 strcpy(r.real,"/sys/devices/brightness");strcpy(r.boot,"not-a-boot");panel_permissions_format(text,sizeof(text),&r);CHECK(!panel_permissions_parse(text,&parsed));
 CHECK(!panel_boot_token(""));CHECK(!panel_boot_token("5a30f9e3-021b-4ec1-8131-47f151ebb7ax"));
 printf("Panel node native validation: %d cases PASS; namespace and permission records, Android not tested\n",cases);return 0;
}
''',encoding='utf-8')
exe=O/'node-test.exe'
subprocess.run(['C:/msys64/mingw64/bin/gcc.exe','-std=c11','-static','-Wall','-Wextra','-Werror','-Wno-misleading-indentation','-I',str(R/'experimental/refactor_hook/native'),str(c),'-o',str(exe)],check=True)
native_result=subprocess.run([str(exe)],check=True,capture_output=True,text=True)
print(native_result.stdout,end='');native_cases=int(re.search(r'(\d+) cases PASS',native_result.stdout).group(1))
print('Panel node validation total:',java_cases+native_cases,'cases PASS')
