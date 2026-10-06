package top.rongshangs.lumacurve.refactor;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;
/** Exists only while the popup is visible. The native writer survives independently. */
final class RootPanelBridge implements AutoCloseable {
 final Context context;Process process;BufferedReader reader;BufferedWriter writer;
 RootPanelBridge(Context context){this.context=context;}
 synchronized JSONObject request(JSONObject request)throws Exception{
  if(process==null||!process.isAlive()){
   close();String path=context.getApplicationInfo().sourceDir;
   String cmd="CLASSPATH='"+path.replace("'","'\\''")+"' /system/bin/app_process /system/bin top.rongshangs.lumacurve.refactor.RootControl serve";
   process=new ProcessBuilder("su","-c",cmd).redirectErrorStream(true).start();
   reader=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8));writer=new BufferedWriter(new OutputStreamWriter(process.getOutputStream(),StandardCharsets.UTF_8));
  }
  final Process live=process;Thread timeout=new Thread(()->{try{Thread.sleep(15000);live.destroyForcibly();}catch(InterruptedException ignored){}});timeout.setDaemon(true);timeout.start();
  try{
   String payload=java.util.Base64.getEncoder().encodeToString(request.toString().getBytes(StandardCharsets.UTF_8));
   writer.write(new JSONObject().put("command","raw-panel").put("payload",payload).toString());writer.newLine();writer.flush();
   String line;while((line=reader.readLine())!=null)if(line.startsWith("LUMA_RESULT=")){JSONObject response=new JSONObject(line.substring(12));if(!response.optBoolean("ok"))throw new IOException(response.optString("message",response.optString("reason")));return response;}
   throw new IOException("未取得 Root 响应，请确认授权");
  }finally{timeout.interrupt();}
 }
 public synchronized void close(){try{if(writer!=null)writer.close();}catch(Exception ignored){}if(process!=null)process.destroy();process=null;reader=null;writer=null;}
}
