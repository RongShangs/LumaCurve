package top.rongshangs.lumacurve.refactor;
import java.io.*;
import android.system.*;
import org.json.*;

/** Core transaction files may fall back; the verified native tool follows the same selection. */
final class RootWorkspace {
 static final File PRIMARY=new File("/data/adb/luma_curve_refactor_test");
 static File selected;static String selectionError="";
 static File temporary(){return new File("/data/local/.hyperlux-root-"+android.os.Process.myUid());}
 static File[] candidates(){return new File[]{PRIMARY,temporary()};}
 static File directory(){return selected==null?PRIMARY:selected;}
 static boolean isCandidate(File path){
  if(path==null)return false;
  for(File candidate:candidates())if(candidate.getAbsolutePath().equals(path.getAbsolutePath()))return true;
  return false;
 }
 static void secure(File path,boolean create)throws Exception{
  StructStat parent=Os.lstat(path.getParent());if(!OsConstants.S_ISDIR(parent.st_mode)||parent.st_uid!=android.os.Process.myUid()||(parent.st_mode&0022)!=0)throw new IOException("Root 工作目录的父目录不可信");
  if(!path.exists()&&create){try{Os.mkdir(path.toString(),0700);}catch(ErrnoException error){if(error.errno!=OsConstants.EEXIST)throw error;}}
  StructStat stat=Os.lstat(path.toString());
  if(!OsConstants.S_ISDIR(stat.st_mode)||stat.st_uid!=android.os.Process.myUid()||!path.getAbsolutePath().equals(path.toPath().toRealPath().toString()))throw new IOException("Root 工作目录身份不匹配");
  if((stat.st_mode&077)!=0){if(!create)throw new IOException("Root 工作目录尚未设置为私有");Os.chmod(path.toString(),0700);stat=Os.lstat(path.toString());if((stat.st_mode&077)!=0)throw new IOException("Root 工作目录权限未确认");}
  if(!path.canRead()||!path.canWrite()||!path.canExecute())throw new IOException("Root 工作目录不可读写");
 }
 static synchronized File require()throws Exception{
  if(selected!=null){secure(selected,false);return selected;}
  Exception first=null;for(File candidate:candidates())try{secure(candidate,true);selected=candidate;return candidate;}catch(Exception denied){selectionError=denied.toString();if(first==null)first=denied;else first.addSuppressed(denied);}
  throw new IOException("Root 权限不足，无法创建私有操作目录；已有设置未修改",first);
 }
 /** Explicit raw-panel/native use: both candidates are verified by the same rules as the C tool. */
 static synchronized File requireNative()throws Exception{
  if(android.os.Process.myUid()!=0)throw new IOException("直接控制屏幕节点需要 UID 0；其他功能按实际权限使用");
  Exception first=null;java.util.LinkedHashSet<File> order=new java.util.LinkedHashSet<>();if(selected!=null)order.add(selected);for(File candidate:candidates())order.add(candidate);
  for(File candidate:order)try{secure(candidate,true);selected=candidate;selectionError="";return candidate;}catch(Exception denied){selectionError=denied.toString();if(first==null)first=denied;else first.addSuppressed(denied);}
  throw new IOException("主屏节点需要受验证的 Root 私有目录；首选 /data/adb 与备用 /data/local 均未通过身份检查，已有设置未修改",first);
 }
 static void initialize(){try{require();}catch(Exception unavailable){selectionError=unavailable.toString();}}
 static JSONObject nativeEvidence()throws JSONException{
  JSONObject result=new JSONObject().put("uid",android.os.Process.myUid()).put("read_only",true).put("selected",directory().toString());JSONArray files=new JSONArray();
  for(File directory:candidates()){
   JSONObject row=new JSONObject().put("path",directory.toString());putStat(row,directory);files.put(row);
   for(String name:new String[]{"main-panel.lock","outdoor-node.lock","main-panel-permissions","hyperlux-main-panel"}){
    File file=new File(directory,name);JSONObject entry=new JSONObject().put("path",file.toString());putStat(entry,file);files.put(entry);
   }
  }
  return result.put("files",files);
 }
 private static void putStat(JSONObject row,File file)throws JSONException{
  try{StructStat st=Os.lstat(file.toString());row.put("uid",st.st_uid).put("mode",Integer.toOctalString(st.st_mode&07777)).put("regular",OsConstants.S_ISREG(st.st_mode)).put("directory",OsConstants.S_ISDIR(st.st_mode)).put("links",st.st_nlink).put("device",st.st_dev).put("inode",st.st_ino);}
  catch(Exception unavailable){row.put("error",unavailable.toString());}
 }
 static synchronized JSONObject report()throws JSONException{
  JSONObject out=new JSONObject().put("selected",selected==null?"":selected.toString()).put("native_directory",directory().toString());JSONArray routes=new JSONArray();boolean usable=false;String selectedError="";
  for(File path:candidates()){JSONObject row=new JSONObject().put("id",path.equals(PRIMARY)?"root_private_storage":"root_temporary_storage").put("path",path.toString()).put("selected",path.equals(selected));try{secure(path,false);if(path.equals(selected))usable=true;row.put("readable",true).put("writable",true);}catch(Exception missing){if(path.equals(selected))selectedError=missing.toString();row.put("readable",false).put("writable",false).put("detail",missing.toString());}routes.put(row);}
  return out.put("selected_usable",usable).put("selected_error",selectedError).put("routes",routes).put("selection_error",selectionError);
 }
}
