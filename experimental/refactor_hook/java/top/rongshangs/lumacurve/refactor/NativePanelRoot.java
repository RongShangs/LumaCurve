package top.rongshangs.lumacurve.refactor;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import android.system.Os;
import org.json.*;
/** Installs the tiny writer only on explicit raw-panel use, for the identified primary node. */
final class NativePanelRoot {
 static final File BIN=new File(RootControl.DATA,"hyperlux-main-panel");
 static int read(File f)throws Exception{return PanelNodeDiscovery.number(f);}
 static JSONObject status()throws Exception{
  JSONObject j=PanelNodeDiscovery.discover(new File("/sys"));
  if(!j.optBoolean("supported"))return j;
  File node=new File(j.getString("path"));
  File driverActual=new File(node.getParentFile(),"actual_brightness");if(driverActual.isFile())try{j.put("driver_actual",read(driverActual));}catch(Exception unavailable){j.put("driver_actual_error",unavailable.toString());}
  boolean guardMatches=false;
  try{JSONObject guard=NativePanelClient.call("STATUS");guardMatches=matches(j,guard);j.put("guard_running",guardMatches);if(guardMatches)j.put("guard",guard);else j.put("supported",false).put("reason","guard_node_mismatch").put("guard_error","guard_node_mismatch");}catch(Exception unavailable){j.put("guard_running",false).put("guard_error",unavailable.toString());}
  int mode=Os.stat(node.toString()).st_mode;
  if((mode&0222)==0&&!guardMatches)j.put("supported",false).put("reason","node_not_writable");
  return j;
 }
 static boolean matches(JSONObject node,JSONObject guard){return guard.optBoolean("ok")&&"raw06-health".equals(guard.optString("native_build"))&&node.optString("path").equals(guard.optString("path"))&&node.optString("canonical_path").equals(guard.optString("canonical_path"))&&node.optInt("maximum",-1)==guard.optInt("maximum",-2);}
 static void install()throws Exception{
  Os.chmod(RootControl.DATA.toString(),0700);
  String source=System.getenv("CLASSPATH");if(source==null||source.isEmpty())throw new IOException("未取得主屏守护所在的 APK 路径");
  {
   byte[] bytes=NativePanelAsset.read(new File(source));
   byte[] want=MessageDigest.getInstance("SHA-256").digest(bytes);
   if(BIN.isFile()&&java.util.Arrays.equals(want,MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(BIN.toPath())))){Os.chmod(BIN.toString(),0700);return;}
   try{NativePanelClient.require("STOP");}catch(Exception ignored){}
   if(BIN.isFile()){
    for(int i=0;i<20;i++){try{NativePanelClient.call("STATUS");Thread.sleep(50);}catch(Exception stopped){break;}}
    RootControl.process(BIN.toString(),"--recover");
   }
   File temp=new File(RootControl.DATA,"hyperlux-main-panel.new");Files.write(temp.toPath(),bytes);Os.chmod(temp.toString(),0700);Files.move(temp.toPath(),BIN.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
  }
 }
 static void start(JSONObject selected)throws Exception{
  install();
  JSONObject current=PanelNodeDiscovery.discover(new File("/sys"));
  if(!current.optBoolean("supported")||!selected.optString("path").equals(current.optString("path"))||!selected.optString("canonical_path").equals(current.optString("canonical_path"))||selected.optInt("maximum",-1)!=current.optInt("maximum",-2))throw new IOException("主屏节点已变化，请重新打开面板");
  JSONObject existing=null;try{existing=NativePanelClient.call("STATUS");}catch(Exception ignored){}
  if(existing!=null){if(matches(current,existing))return;throw new IOException("已有守护使用其他节点，请先恢复自动亮度");}
  File log=new File(RootControl.DATA,"main-panel.log");
  Process guard=new ProcessBuilder(BIN.toString(),"--daemon","--node",current.getString("path")).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.to(log)).start();
  String lastProbe="未取得就绪响应";long deadline=System.nanoTime()+1500000000L;
  while(System.nanoTime()<deadline){
   try{JSONObject reply=NativePanelClient.call("STATUS");if(matches(current,reply))return;lastProbe=reply.toString();}catch(Exception unavailable){lastProbe=unavailable.toString();}
   if(!guard.isAlive())throw startFailure(log,"exit="+guard.exitValue()+"；就绪查询："+lastProbe);
   if(System.nanoTime()<deadline)Thread.sleep(50);
  }
  throw startFailure(log,"就绪检查超时；就绪查询："+lastProbe);
 }
 static IOException startFailure(File log,String status){
  String detail="";try{byte[] data=Files.readAllBytes(log.toPath());detail=new String(data,0,Math.min(data.length,8192),java.nio.charset.StandardCharsets.UTF_8).trim();}catch(Exception ignored){}
  return new IOException("主屏节点守护启动失败（"+status+"）；未切换自动亮度"+(detail.isEmpty()?"":"\n"+detail));
 }
 static void stop()throws Exception{
  try{NativePanelClient.require("STOP");}catch(Exception ignored){}
  if(BIN.isFile()){for(int i=0;i<20;i++){try{RootControl.process(BIN.toString(),"--recover");return;}catch(Exception retry){Thread.sleep(50);}}throw new IOException("主屏节点权限尚未恢复，请导出分析包");}
 }
}
