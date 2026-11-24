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
 static final String WORKSPACE="--workspace=";
 static File bin(File dir){return new File(dir,"hyperlux-main-panel");}
 static File logFile(File dir){return new File(dir,"main-panel.log");}
 static int read(File f)throws Exception{return PanelNodeDiscovery.number(f);}
 static JSONObject status()throws Exception{
  JSONObject j=ActivePanelNode.discover();
  if(!j.optBoolean("supported"))return j;
  File node=new File(j.getString("path"));
  File driverActual=new File(node.getParentFile(),"actual_brightness");if(driverActual.isFile())try{j.put("driver_actual",read(driverActual));}catch(Exception unavailable){j.put("driver_actual_error",unavailable.toString());}
  boolean guardMatches=false;
  try{JSONObject guard=NativePanelClient.call("STATUS");guardMatches=matches(j,guard);j.put("guard_running",guardMatches);j.put("guard_workspace",guard.optString("workspace"));if(guardMatches)j.put("guard",guard);else if(ActivePanelNode.PROFILE.equals(j.optString("discovery")))j.put("guard_binding_stale",true);else return j.put("supported",false).put("reason","guard_node_mismatch").put("guard_error","guard_node_mismatch");}catch(Exception unavailable){j.put("guard_running",false).put("guard_error",unavailable.toString());}
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
 static boolean matches(JSONObject node,JSONObject guard){return (!ActivePanelNode.PROFILE.equals(node.optString("discovery"))||node.optInt("fold_port",-2)==guard.optInt("fold_port",-3))&&guard.optBoolean("ok")&&("raw08-slider".equals(guard.optString("native_build"))||"raw09-slider".equals(guard.optString("native_build"))||"raw10-slider".equals(guard.optString("native_build"))||"raw11-slider".equals(guard.optString("native_build"))||"raw12-slider".equals(guard.optString("native_build"))||"raw13-slider".equals(guard.optString("native_build")))&&node.optString("path").equals(guard.optString("path"))&&node.optString("canonical_path").equals(guard.optString("canonical_path"))&&node.optInt("maximum",-1)==guard.optInt("maximum",-2);}
 /** A pre-workspace guard reports no directory; it is still ours for this node. */
 static boolean workspaceMatches(File dir,JSONObject guard){String running=guard.optString("workspace");return dir!=null&&(running.isEmpty()||dir.getAbsolutePath().equals(running));}
 static boolean sameSelection(JSONObject selected,JSONObject current){
  boolean fold=ActivePanelNode.PROFILE.equals(selected.optString("discovery"));
  return current.optBoolean("supported")&&fold==ActivePanelNode.PROFILE.equals(current.optString("discovery"))&&selected.optString("path").equals(current.optString("path"))&&selected.optString("canonical_path").equals(current.optString("canonical_path"))&&selected.optInt("maximum",-1)==current.optInt("maximum",-2)&&(!fold||!selected.optString("physical_display_id").isEmpty()&&selected.optString("physical_display_id").equals(current.optString("physical_display_id"))&&selected.optInt("fold_port",-2)==current.optInt("fold_port",-3));
 }
 static File install()throws Exception{return install(RootWorkspace.requireNative());}
 static File install(File dir)throws Exception{
  RootWorkspace.secure(dir,true);
  Os.chmod(dir.toString(),0700);
  String source=System.getenv("CLASSPATH");if(source==null||source.isEmpty())throw new IOException("未取得主屏守护所在的 APK 路径");
  File binary=bin(dir);
  {
   byte[] bytes=NativePanelAsset.read(new File(source));
   byte[] want=MessageDigest.getInstance("SHA-256").digest(bytes);
   if(binary.isFile()&&java.util.Arrays.equals(want,MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(binary.toPath())))){Os.chmod(binary.toString(),0700);return dir;}
   try{NativePanelClient.require("STOP");}catch(Exception ignored){}
   if(binary.isFile())for(int i=0;i<20;i++){try{NativePanelClient.call("STATUS");Thread.sleep(50);}catch(Exception stopped){break;}}
   File temp=Files.createTempFile(dir.toPath(),"hyperlux-main-panel-",".new").toFile();
   try{
    Files.write(temp.toPath(),bytes);Os.chmod(temp.toString(),0700);
    // The verified recovery code handles old private-mode residue in this workspace.
    RootControl.process(temp.toString(),"--recover",WORKSPACE+dir.getAbsolutePath());
    Files.move(temp.toPath(),binary.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
   }finally{Files.deleteIfExists(temp.toPath());}
  }
  return dir;
 }
 static void start(JSONObject selected)throws Exception{start(selected,false,0);}
 static void start(JSONObject selected,boolean follow,int enginePid)throws Exception{
  if(follow&&enginePid<=1)throw new IOException("系统进程身份无效");
  JSONObject current=ActivePanelNode.discover();
  if(!sameSelection(selected,current))throw new IOException("主屏节点已变化，请重新打开面板");
  File dir=install();
  current=ActivePanelNode.discover();
  if(!sameSelection(selected,current))throw new IOException("主屏节点已变化，请重新打开面板");
  JSONObject existing=null;try{existing=NativePanelClient.call("STATUS");}catch(Exception ignored){}
  if(existing!=null){
   boolean sameNode=matches(current,existing);
   boolean bound=!follow||existing.optBoolean("control_center_listener")&&existing.optInt("control_center_pid")==enginePid;
   if(sameNode&&workspaceMatches(dir,existing)&&bound)return;
   // Same node in another verified workspace (or a pre-workspace build) is ours to replace.
   if(sameNode||ActivePanelNode.PROFILE.equals(current.optString("discovery"))&&existing.optInt("fold_port",-1)>=0)stop();
   else throw new IOException("已有守护使用其他节点，请先恢复自动亮度");
  }
  File log=logFile(dir);
  // Retain one bounded previous run before ProcessBuilder replaces the current log.
  if(log.isFile())try(RandomAccessFile old=new RandomAccessFile(log,"r")){int size=(int)Math.min(old.length(),65536);byte[] tail=new byte[size];old.seek(old.length()-size);old.readFully(tail);Files.write(new File(dir,"main-panel.previous.log").toPath(),tail);}catch(IOException unavailable){/* Optional evidence must not prevent startup. */}
  Process guard=new ProcessBuilder(command(current,follow,enginePid,dir)).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.to(log)).start();
  String lastProbe="未取得就绪响应";long deadline=System.nanoTime()+1500000000L;
  while(System.nanoTime()<deadline){
   try{JSONObject reply=NativePanelClient.call("STATUS");if(matches(current,reply)&&workspaceMatches(dir,reply)&&(!follow||reply.optBoolean("control_center_listener")&&reply.optInt("control_center_pid")==enginePid))return;lastProbe=reply.toString();}catch(Exception unavailable){lastProbe=unavailable.toString();}
   if(!guard.isAlive())throw startFailure(log,"exit="+guard.exitValue()+"；就绪查询："+lastProbe);
   if(System.nanoTime()<deadline)Thread.sleep(50);
  }
  throw startFailure(log,"就绪检查超时；就绪查询："+lastProbe);
 }
 static java.util.List<String> command(JSONObject current,boolean follow,int enginePid,File dir)throws Exception{
  java.util.List<String> command=new java.util.ArrayList<>(java.util.Arrays.asList(bin(dir).toString(),"--daemon","--node",current.getString("path"),WORKSPACE+dir.getAbsolutePath()));
  if(ActivePanelNode.PROFILE.equals(current.optString("discovery"))){int port=current.getInt("fold_port");if(port<0||port>7)throw new IOException("折叠屏节点端口无效");command.add("--active-fold="+port);}
  if(follow){if(enginePid<=1)throw new IOException("系统进程身份无效");command.add("--control-center="+enginePid);}return command;
 }
 static IOException startFailure(java.io.File log,String status){
  String detail="";try{byte[] data=Files.readAllBytes(log.toPath());detail=new String(data,0,Math.min(data.length,8192),java.nio.charset.StandardCharsets.UTF_8).trim();}catch(Exception ignored){}
  return new IOException("主屏节点守护启动失败（"+status+"）；未切换自动亮度"+(detail.isEmpty()?"":"\n"+detail));
 }
 static void stop()throws Exception{
  boolean stopped=false;try{NativePanelClient.require("STOP");stopped=true;}catch(Exception ignored){}
  Exception failure=null;boolean stranded=false;
  for(File dir:RootWorkspace.candidates()){
   File binary=bin(dir);if(!binary.isFile())continue;
   try{RootWorkspace.secure(dir,false);}
   catch(Exception denied){
    // An untrusted directory cannot run recovery. A live guard already restored through STOP;
    // a leftover record without STOP is reported instead of claiming success.
    if(new File(dir,"main-panel-permissions").exists())stranded=true;
    continue;
   }
   try{recover(dir);}catch(Exception denied){failure=denied;}
  }
  if(failure!=null)throw new IOException("主屏节点权限尚未恢复，请导出分析包",failure);
  if(!stopped&&stranded)throw new IOException("主屏节点守护未确认退出，且其工作目录当前不可执行恢复；请重启后导出分析包");
 }
 /** Restore sysfs permissions recorded by a guard in this workspace, old or new build. */
 static void recover(File dir)throws Exception{
  File binary=bin(dir);if(!binary.isFile())return;
  boolean workspace=supportsWorkspace(binary);
  for(int i=0;i<20;i++){
   try{if(workspace)RootControl.process(binary.toString(),"--recover",WORKSPACE+dir.getAbsolutePath());else RootControl.process(binary.toString(),"--recover");return;}
   catch(Exception retry){Thread.sleep(50);}
  }
  throw new IOException("主屏节点权限尚未恢复，请导出分析包");
 }
 private static final java.util.Map<String,Boolean> WORKSPACE_SUPPORT=new java.util.HashMap<>();
 /** A pre-workspace build exits 2 here; the legacy recovery keeps its fixed directory. */
 static boolean supportsWorkspace(File binary){
  String key=binary.getAbsolutePath()+"|"+binary.length()+"|"+binary.lastModified();
  Boolean known=WORKSPACE_SUPPORT.get(key);if(known!=null)return known;
  boolean supported=false;
  try{supported=RootControl.process(binary.toString(),"--version").contains("workspace-v1");}catch(Exception old){supported=false;}
  WORKSPACE_SUPPORT.put(key,supported);return supported;
 }
}
