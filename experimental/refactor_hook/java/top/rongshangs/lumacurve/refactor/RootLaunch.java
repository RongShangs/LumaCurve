package top.rongshangs.lumacurve.refactor;
import java.io.*;

/** One authorization path, shared by the APP and panel; never retry a denied authorization. */
final class RootLaunch {
 interface Starter{Process start(String[] command)throws IOException;}
 static Process open(String apk)throws IOException{return open(apk,command->new ProcessBuilder(command).redirectErrorStream(true).start());}
 static Process open(String apk,Starter starter)throws IOException{
  if(apk==null||!apk.startsWith("/")||apk.indexOf('\n')>=0||apk.indexOf('\0')>=0)throw new IOException("APK 路径无效");
  String command="umask 077; CLASSPATH='"+apk.replace("'","'\\''")+"' exec /system/bin/app_process /system/bin top.rongshangs.lumacurve.refactor.RootControl serve";
  IOException first=null;
  for(String su:new String[]{"su","/system/bin/su","/system/xbin/su","/sbin/su","/data/adb/ksu/bin/su","/data/adb/magisk/su"})try{return starter.start(new String[]{su,"-c",command});}catch(IOException error){if(first==null)first=error;else first.addSuppressed(error);String reason=String.valueOf(error.getMessage());if(!(reason.contains("error=2")||reason.contains("ENOENT")))throw error;}
  throw new IOException("未找到可执行的 su，请先恢复本次开机的 Root 并授权 HyperLux",first);
 }
}
