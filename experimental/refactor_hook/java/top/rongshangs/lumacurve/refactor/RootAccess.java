package top.rongshangs.lumacurve.refactor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.json.*;

/** Capabilities and SELinux labels are evidence, never substitutes for actual access results. */
final class RootAccess {
 static boolean denied(Throwable failure){for(int i=0;failure!=null&&i<8;i++,failure=failure.getCause()){if(failure instanceof SecurityException||failure instanceof AccessDeniedException)return true;String message=String.valueOf(failure.getMessage()).toLowerCase(java.util.Locale.ROOT);if(message.contains("permission denied")||message.contains("operation not permitted")||message.contains("access denied"))return true;}return false;}
 static String read(String path){try{byte[] bytes=Files.readAllBytes(Paths.get(path));if(bytes.length>65536)return "";return new String(bytes,StandardCharsets.UTF_8).trim();}catch(Exception denied){return "unavailable: "+denied.getClass().getSimpleName();}}
 static JSONObject report(RootSettings settings)throws JSONException{
  JSONObject report=new JSONObject().put("uid",android.os.Process.myUid()).put("pid",android.os.Process.myPid()).put("root_uid",android.os.Process.myUid()==0).put("selinux_context",read("/proc/self/attr/current")).put("workspace",RootWorkspace.report()).put("settings",settings.report());
  JSONObject caps=new JSONObject();for(String line:read("/proc/self/status").split("\n")){int colon=line.indexOf(':');if(colon<0)continue;String key=line.substring(0,colon);if(key.equals("CapEff")||key.equals("CapBnd")||key.equals("CapPrm")||key.equals("NoNewPrivs")||key.equals("Seccomp"))caps.put(key,line.substring(colon+1).trim());}return report.put("process_permissions",caps);
 }
}
