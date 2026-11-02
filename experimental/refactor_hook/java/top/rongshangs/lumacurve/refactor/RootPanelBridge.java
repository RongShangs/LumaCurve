package top.rongshangs.lumacurve.refactor;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;
/** Bounded root connection shared by the popup and one-shot mapping recovery. */
final class RootPanelBridge implements AutoCloseable {
 final Context context;volatile Process process;BufferedReader reader;BufferedWriter writer;
 RootPanelBridge(Context context){this.context=context;}
 JSONObject request(JSONObject request)throws Exception{return command("raw-panel",java.util.Base64.getEncoder().encodeToString(request.toString().getBytes(StandardCharsets.UTF_8)),15000);}
 synchronized JSONObject command(String command,String payload,int timeoutMs)throws Exception{
  if(timeoutMs<1000||timeoutMs>15000)throw new IllegalArgumentException("Root request timeout");
  if(process==null||!process.isAlive()){
   close();process=RootLaunch.open(context.getApplicationInfo().sourceDir);
   reader=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8));writer=new BufferedWriter(new OutputStreamWriter(process.getOutputStream(),StandardCharsets.UTF_8));
  }
  final Process live=process;Thread timeout=new Thread(()->{try{Thread.sleep(timeoutMs);live.destroyForcibly();}catch(InterruptedException ignored){}});timeout.setDaemon(true);timeout.start();
  try{
   writer.write(new JSONObject().put("command",command).put("payload",payload==null?JSONObject.NULL:payload).toString());writer.newLine();writer.flush();
   String line;while((line=reader.readLine())!=null)if(line.startsWith("LUMA_RESULT=")){JSONObject response=new JSONObject(line.substring(12));if(!response.optBoolean("ok"))throw new IOException(response.optString("message",response.optString("reason")));return response;}
   throw new IOException("未取得 Root 响应，请确认授权");
  }finally{timeout.interrupt();}
 }
 void cancel(){Process live=process;if(live!=null)live.destroyForcibly();}
 public synchronized void close(){try{if(writer!=null)writer.close();}catch(Exception ignored){}if(process!=null)process.destroy();process=null;reader=null;writer=null;}
}
