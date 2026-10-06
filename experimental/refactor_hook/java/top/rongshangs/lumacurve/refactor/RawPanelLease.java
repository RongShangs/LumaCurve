package top.rongshangs.lumacurve.refactor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import android.system.Os;
/** system_server owns this lease; Root reads it. No system_server-to-su socket permission needed. */
final class RawPanelLease {
 static final String PATH="/data/system/hyperlux-main-panel-lease";
 static void write(String token,int pid,long until,int active,int target)throws Exception{
  File temp=new File(PATH+".new");
  try(FileOutputStream out=new FileOutputStream(temp)){out.write((token+" "+pid+" "+until+" "+active+" "+target+"\n").getBytes(StandardCharsets.US_ASCII));}
  Os.chmod(temp.toString(),0600);Os.rename(temp.toString(),PATH);
 }
 static boolean healthy(String token)throws Exception{
  byte[] data=java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("/data/system/hyperlux-main-panel-health"));
  if(data.length>256)return false;String[] values=new String(data,StandardCharsets.US_ASCII).trim().split(" +");
  return values.length==3&&values[0].equals(token)&&values[1].equals("1")&&values[2].equals("0");
 }
 static void clear()throws Exception{write("none",0,0,0,-1);}
}
