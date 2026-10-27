package top.rongshangs.lumacurve.refactor;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Prepare off the UI thread; the chooser opens only after the user confirms export. */
final class DiagnosticSharing {
    static File directory(Context context)throws IOException{File cache=context.getExternalCacheDir();if(cache==null)throw new IOException("分享缓存不可用");File dir=new File(cache,"diagnostic-shares");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建分享缓存");return dir.getCanonicalFile();}
    static String payload(Context context){try{File dir=directory(context);File[] old=dir.listFiles();long before=System.currentTimeMillis()-7L*24*60*60*1000;if(old!=null)for(File file:old)if(DiagnosticShareFiles.name(file.getName())&&file.isFile()&&file.lastModified()<before)file.delete();return Base64.getEncoder().encodeToString(new JSONObject().put("share_directory",dir.toString()).toString().getBytes(StandardCharsets.UTF_8));}catch(Exception optional){return null;}}
    static void prepare(Context context,JSONObject result){try{
        if(result.optBoolean("partial_root_report")){Uri uri=Uri.parse(result.getString("uri"));if(!"content".equals(uri.getScheme())||!"media".equals(uri.getAuthority()))throw new IOException("权限分析包地址无效");result.put("share_uri",uri.toString());return;}
        File dir=directory(context),source=new File(result.getString("path")),target=DiagnosticShareFiles.file(dir,source.getName());if(!target.isFile())DiagnosticShareFiles.copy(source,dir);
        try(FileInputStream check=new FileInputStream(target)){if(check.read()<0)throw new IOException("分享文件为空");}
        Uri uri=new Uri.Builder().scheme("content").authority(context.getPackageName()+".diagnostics").appendPath("zip").appendPath(target.getName()).build();result.put("share_uri",uri.toString());
    }catch(Exception optional){try{result.put("share_error",optional.getMessage()==null?optional.toString():optional.getMessage());}catch(JSONException ignored){}}}
    static void share(MainActivity activity,JSONObject result){String value=result.optString("share_uri");if(value.isEmpty()){activity.show("分享准备未完成，文件仍保存在："+result.optString("path")+"\n"+result.optString("share_error"));return;}try{
        Uri uri=Uri.parse(value);Intent send=new Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);send.setClipData(ClipData.newRawUri("HyperLux diagnostics",uri));Intent chooser=Intent.createChooser(send,activity.tr("分享分析包"));chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);chooser.setClipData(send.getClipData());activity.startActivity(chooser);
    }catch(ActivityNotFoundException unavailable){activity.show("没有可接收压缩包的应用；文件仍保存在："+result.optString("path"));}catch(SecurityException denied){activity.show("系统分享访问未获允许；文件仍保存在："+result.optString("path"));}}
}
