package top.rongshangs.lumacurve.refactor;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.system.ErrnoException;
import org.json.*;
/** Installs the tiny writer only on explicit raw-panel use, for the identified primary node. */
final class NativePanelRoot {
 static final File BIN=new File(RootControl.DATA,"hyperlux-main-panel");
 static int read(File f)throws Exception{return PanelNodeDiscovery.number(f);}
 static JSONObject status()throws Exception{
  JSONObject j=ActivePanelNode.discover();
  if(!j.optBoolean("supported"))return j;
  File node=new File(j.getString("path"));
  File driverActual=new File(node.getParentFile(),"actual_brightness");if(driverActual.isFile())try{j.put("driver_actual",read(driverActual));}catch(Exception unavailable){j.put("driver_actual_error",unavailable.toString());}
  boolean guardMatches=false;
  try{JSONObject guard=NativePanelClient.call("STATUS");guardMatches=matches(j,guard);j.put("guard_running",guardMatches);if(guardMatches)j.put("guard",guard);else if(ActivePanelNode.PROFILE.equals(j.optString("discovery")))j.put("guard_binding_stale",true);else return j.put("supported",false).put("reason","guard_node_mismatch").put("guard_error","guard_node_mismatch");}catch(Exception unavailable){j.put("guard_running",false).put("guard_error",unavailable.toString());}
  j.put("node_mode",Integer.toOctalString(Os.lstat(node.toString()).st_mode&0777));
  // Root may open a 0444 attribute. Probe the actual access decision without
  // truncating, writing brightness, changing permissions or starting a guard.
  if(guardMatches)j.put("write_probe","guard_owns_descriptor");
  else try{probe(j);j.put("write_probe","open_ok");}
  catch(Exception denied){j.put("supported",false).put("reason","node_not_writable").put("write_probe","failed").put("write_error",denied.toString());if(denied instanceof ErrnoException)j.put("write_errno",((ErrnoException)denied).errno);}
  return j;
 }
 static void probe(JSONObject selected)throws Exception{
  File node=new File(selected.getString("path"));StructStat expected=Os.lstat(node.toString());
  FileDescriptor fd=Os.open(node.toString(),OsConstants.O_WRONLY|OsConstants.O_CLOEXEC|OsConstants.O_NOFOLLOW,0);
  try{StructStat opened=Os.fstat(fd);if(!OsConstants.S_ISREG(opened.st_mode)||expected.st_dev!=opened.st_dev||expected.st_ino!=opened.st_ino||!node.toPath().toRealPath().toString().equals(selected.getString("canonical_path")))throw new IOException("主屏节点在权限检查期间发生变化");}
  finally{Os.close(fd);}
 }
 static boolean matches(JSONObject node,JSONObject guard){return (!ActivePanelNode.PROFILE.equals(node.optString("discovery"))||node.optInt("fold_port",-2)==guard.optInt("fold_port",-3))&&guard.optBoolean("ok")&&"raw08-slider".equals(guard.optString("native_build"))&&node.optString("path").equals(guard.optString("path"))&&node.optString("canonical_path").equals(guard.optString("canonical_path"))&&node.optInt("maximum",-1)==guard.optInt("maximum",-2);}
 static void install()throws Exception{
  RootWorkspace.requireNative();
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
 static void start(JSONObject selected)throws Exception{start(selected,false,0);}
 static void start(JSONObject selected,boolean follow,int enginePid)throws Exception{
  if(follow&&ActivePanelNode.PROFILE.equals(selected.optString("discovery")))throw new IOException("折叠屏节点映射尚未启用");
  JSONObject current=ActivePanelNode.discover();
  if(!current.optBoolean("supported")||!selected.optString("path").equals(current.optString("path"))||!selected.optString("canonical_path").equals(current.optString("canonical_path"))||selected.optInt("maximum",-1)!=current.optInt("maximum",-2))throw new IOException("主屏节点已变化，请重新打开面板");
   install();
   current=ActivePanelNode.discover();
   if(!current.optBoolean("supported")||!selected.optString("path").equals(current.optString("path"))||!selected.optString("canonical_path").equals(current.optString("canonical_path"))||selected.optInt("maximum",-1)!=current.optInt("maximum",-2))throw new IOException("主屏节点已变化，请重新打开面板");
  JSONObject existing=null;try{existing=NativePanelClient.call("STATUS");}catch(Exception ignored){}
  if(existing!=null){if(matches(current,existing)&&(!follow||existing.optBoolean("control_center_listener")&&existing.optInt("control_center_pid")==enginePid))return;if(matches(current,existing)){stop();existing=null;}else if(ActivePanelNode.PROFILE.equals(current.optString("discovery"))&&existing.optInt("fold_port",-1)>=0){stop();existing=null;}else throw new IOException("已有守护使用其他节点，请先恢复自动亮度");}
  File log=new File(RootControl.DATA,"main-panel.log");
  java.util.List<String> command=new java.util.ArrayList<>(java.util.Arrays.asList(BIN.toString(),"--daemon","--node",current.getString("path")));if(ActivePanelNode.PROFILE.equals(current.optString("discovery")))command.add("--active-fold="+current.getInt("fold_port"));if(follow){if(enginePid<=1)throw new IOException("系统进程身份无效");command.add("--control-center="+enginePid);}
  Process guard=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.to(log)).start();
  String lastProbe="未取得就绪响应";long deadline=System.nanoTime()+1500000000L;
  while(System.nanoTime()<deadline){
   try{JSONObject reply=NativePanelClient.call("STATUS");if(matches(current,reply)&&(!follow||reply.optBoolean("control_center_listener")&&reply.optInt("control_center_pid")==enginePid))return;lastProbe=reply.toString();}catch(Exception unavailable){lastProbe=unavailable.toString();}
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
