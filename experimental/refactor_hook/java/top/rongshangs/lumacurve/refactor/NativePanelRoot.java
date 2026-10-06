package top.rongshangs.lumacurve.refactor;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import android.system.Os;
import org.json.*;
/** Installs the tiny writer only on explicit raw-panel use. Never touches panel1. */
final class NativePanelRoot {
 static final File BIN=new File(RootControl.DATA,"hyperlux-main-panel");
 static final File NODE=new File("/sys/class/backlight/panel0-backlight/brightness"),MAX=new File(NODE.getParentFile(),"max_brightness");
 static int read(File f)throws Exception{String v=new String(Files.readAllBytes(f.toPath()),java.nio.charset.StandardCharsets.US_ASCII).trim();int n=Integer.parseInt(v);if(n<0||n>65535)throw new IOException("主屏节点数值无效");return n;}
 static JSONObject status()throws Exception{
  JSONObject j=new JSONObject().put("path",NODE.toString()).put("supported",NODE.isFile()&&MAX.isFile());
  if(!j.optBoolean("supported"))return j;
  int max=read(MAX);if(max<10)throw new IOException("主屏节点范围无效");j.put("minimum",10).put("maximum",max).put("actual",read(NODE));
  File driverActual=new File(NODE.getParentFile(),"actual_brightness");if(driverActual.isFile())try{j.put("driver_actual",read(driverActual));}catch(Exception unavailable){j.put("driver_actual_error",unavailable.toString());}
  try{j.put("guard",NativePanelClient.call("STATUS"));}catch(Exception unavailable){j.put("guard_running",false).put("guard_error",unavailable.toString());}
  return j;
 }
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
 static void start()throws Exception{
  install();
  try{if(NativePanelClient.call("STATUS").optBoolean("ok"))return;}catch(Exception ignored){}
  File log=new File(RootControl.DATA,"main-panel.log");
  Process guard=new ProcessBuilder(BIN.toString(),"--daemon").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.to(log)).start();
  String lastProbe="未取得就绪响应";long deadline=System.nanoTime()+1500000000L;
  while(System.nanoTime()<deadline){
   try{JSONObject reply=NativePanelClient.call("STATUS");if(reply.optBoolean("ok"))return;lastProbe=reply.toString();}catch(Exception unavailable){lastProbe=unavailable.toString();}
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
