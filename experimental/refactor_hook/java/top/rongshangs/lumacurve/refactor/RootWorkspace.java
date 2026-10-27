package top.rongshangs.lumacurve.refactor;
import java.io.*;
import android.system.*;
import org.json.*;

/** Core transaction files may fall back; native output keeps its original private home. */
final class RootWorkspace {
 static final File PRIMARY=new File("/data/adb/luma_curve_refactor_test");
 static File selected;static String selectionError="";
 static File temporary(){return new File("/data/local/.hyperlux-root-"+android.os.Process.myUid());}
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
  Exception first=null;for(File candidate:new File[]{PRIMARY,temporary()})try{secure(candidate,true);selected=candidate;return candidate;}catch(Exception denied){selectionError=denied.toString();if(first==null)first=denied;else first.addSuppressed(denied);}
  throw new IOException("Root 权限不足，无法创建私有操作目录；已有设置未修改",first);
 }
 static void requireNative()throws Exception{if(android.os.Process.myUid()!=0)throw new IOException("直接控制屏幕节点需要 UID 0；其他功能按实际权限使用");secure(PRIMARY,true);}
 static void initialize(){try{require();}catch(Exception unavailable){selectionError=unavailable.toString();}}
 static synchronized JSONObject report()throws JSONException{
  JSONObject out=new JSONObject().put("selected",selected==null?"":selected.toString()).put("native_directory",PRIMARY.toString());JSONArray routes=new JSONArray();boolean usable=false;String selectedError="";
  for(File path:new File[]{PRIMARY,temporary()}){JSONObject row=new JSONObject().put("id",path.equals(PRIMARY)?"root_private_storage":"root_temporary_storage").put("path",path.toString()).put("selected",path.equals(selected));try{secure(path,false);if(path.equals(selected))usable=true;row.put("readable",true).put("writable",true);}catch(Exception missing){if(path.equals(selected))selectedError=missing.toString();row.put("readable",false).put("writable",false).put("detail",missing.toString());}routes.put(row);}
  return out.put("selected_usable",usable).put("selected_error",selectedError).put("routes",routes).put("selection_error",selectionError);
 }
}
