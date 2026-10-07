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
  if(data.length>256)return false;
  return healthMatches(new String(data,StandardCharsets.US_ASCII),token,android.os.SystemClock.uptimeMillis());
 }
 static boolean healthMatches(String record,String token,long now){
  String[] values=record.trim().split(" +");
  if(values.length!=4||!values[0].equals(token)||!values[1].equals("1")||!values[2].equals("0")||!values[3].matches("[0-9]{1,19}"))return false;
  try{long at=Long.parseLong(values[3]);return at<=now&&now-at<=5000;}catch(NumberFormatException invalid){return false;}
 }
 static void clear()throws Exception{write("none",0,0,0,-1);}
}
