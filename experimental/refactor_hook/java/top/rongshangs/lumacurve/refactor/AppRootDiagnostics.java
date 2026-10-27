package top.rongshangs.lumacurve.refactor;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

/** Explicit export fallback, using the APP's own Downloads access; never asks for broader root. */
final class AppRootDiagnostics {
 static JSONObject unavailable(Throwable failure)throws JSONException{
  FeatureSelfCheck checks=new FeatureSelfCheck();String detail="Root 辅助进程未完成检查，不能据此判断 LSPosed 或设备不兼容。 "+String.valueOf(failure);if(detail.length()>1024)detail=detail.substring(0,1024);
  for(String[] feature:new String[][]{{"root_identity","Root 进程身份与限制"},{"root_settings","系统设置访问"},{"root_process_identity","系统进程身份核验"},{"root_storage","私有操作目录"},{"stored_config","配置读取"},{"main_panel_node","主屏节点与打开权限"},{"outdoor_node_device","节点高亮设备条件"},{"native_payload","主屏控制程序完整性"}}){checks.check(feature[0],feature[1],null,true,()->false);checks.explain(feature[0],detail);}
  return checks.report(android.os.SystemClock.elapsedRealtime(),"app-observed-root-failure").put("mode","app_observed_failure");
 }
 static byte[] archive(JSONObject last,Throwable failure)throws Exception{
  JSONObject state=new JSONObject().put("partial_root_report",true).put("build",AppBuild.BUILD).put("model",Build.MODEL).put("fingerprint",Build.FINGERPRINT).put("root_error",String.valueOf(failure)).put("note","Root 收集未完成；本包仅包含APP侧错误和最近读取的状态，不代表实时系统状态。");
  if(last!=null)state.put("last_known_state",new JSONObject(last.toString()));
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry("app-root-diagnostics.json"));zip.write(state.toString(2).getBytes(StandardCharsets.UTF_8));zip.closeEntry();zip.putNextEntry(new ZipEntry("analysis-readme.txt"));zip.write("Partial APP-side report: privileged collection was unavailable. Root error and last known state only; no fresh firmware or system logs.\n".getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
  return bytes.toByteArray();
 }
 static JSONObject export(Context context,JSONObject last,Throwable failure)throws Exception{
  byte[] bytes=archive(last,failure);String name="HyperLux-root-analysis-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date())+"-"+UUID.randomUUID().toString().substring(0,6)+".zip";
  ContentResolver resolver=context.getContentResolver();ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);values.put(MediaStore.MediaColumns.MIME_TYPE,"application/zip");values.put(MediaStore.MediaColumns.RELATIVE_PATH,"Download/HyperLux");values.put(MediaStore.MediaColumns.IS_PENDING,1);
  Uri uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);if(uri==null)throw new IOException("无法创建权限分析包");
  try{
   try(OutputStream out=resolver.openOutputStream(uri,"w")){if(out==null)throw new IOException("无法写入权限分析包");out.write(bytes);}
   ContentValues done=new ContentValues();done.put(MediaStore.MediaColumns.IS_PENDING,0);if(resolver.update(uri,done,null,null)!=1)throw new IOException("权限分析包保存未确认");
   return new JSONObject().put("ok",true).put("path","Download/HyperLux/"+name).put("uri",uri.toString()).put("partial_root_report",true).put("message","已导出权限分析包；Root系统日志未能收集，包含错误原因和最近状态");
  }catch(Exception error){try{resolver.delete(uri,null,null);}catch(Exception cleanup){error.addSuppressed(cleanup);}throw error;}
 }
}
