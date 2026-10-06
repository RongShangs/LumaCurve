package top.rongshangs.lumacurve.refactor;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.nio.charset.StandardCharsets;
import java.io.*;
import org.json.*;
/** Small authenticated local socket, no shell and no sysfs access in system_server. */
final class NativePanelClient {
 static JSONObject call(String command)throws Exception {
  try(LocalSocket socket=new LocalSocket()){
   // LocalSocket creates its descriptor on connect; setSoTimeout does not create it.
   socket.connect(new LocalSocketAddress("hyperlux.main.panel.raw04",LocalSocketAddress.Namespace.ABSTRACT));socket.setSoTimeout(350);
   socket.getOutputStream().write((command+"\n").getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();
   ByteArrayOutputStream out=new ByteArrayOutputStream();int v;while((v=socket.getInputStream().read())>=0&&v!='\n'){if(out.size()>2048)throw new IOException("守护响应过长");out.write(v);}
   if(out.size()==0)throw new IOException("主屏节点守护未响应");return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
  }
 }
 static void require(String command)throws Exception{JSONObject j=call(command);if(!j.optBoolean("ok"))throw new IOException("主屏节点守护："+j.optString("reason"));}
}
