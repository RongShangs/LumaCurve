package top.rongshangs.lumacurve.refactor;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.system.Os;
import android.system.OsConstants;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.json.*;

/** Read only our own LSPosed scope; an explicit restart targets verified SystemUI only. */
final class SystemUiAccess {
    static final String PACKAGE="com.android.systemui",MODULE="top.rongshangs.lumacurve";
    static File[] databases(File adb){
        LinkedHashSet<File> files=new LinkedHashSet<>();files.add(new File(adb,"lspd/config/modules_config.db"));
        File[] children=adb.listFiles();if(children!=null){Arrays.sort(children,Comparator.comparing(File::getName));for(int i=0;i<Math.min(64,children.length);i++)if(children[i].isDirectory())files.add(new File(children[i],"config/modules_config.db"));}
        return files.toArray(new File[0]);
    }
    static JSONObject scopes(File[] candidates)throws JSONException{
        JSONArray attempts=new JSONArray();Boolean selected=null,enabled=null;String source="";boolean conflict=false,sawSchema=false,failed=false;
        for(File file:candidates){
            if(!file.isFile())continue;
            // Do not let Android's default corruption handler delete another app's DB.
            try(SQLiteDatabase db=SQLiteDatabase.openDatabase(file.toString(),null,SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS,broken->{});
                Cursor row=db.rawQuery("SELECT m.enabled, EXISTS(SELECT 1 FROM scope s WHERE s.mid=m.mid AND s.app_pkg_name=? AND s.user_id=?) FROM modules m WHERE m.module_pkg_name=? LIMIT 2",new String[]{PACKAGE,"0",MODULE})){
                sawSchema=true;attempts.put(new JSONObject().put("path",file.toString()).put("state","readable"));
                if(!row.moveToFirst())continue;
                if(row.getType(0)!=Cursor.FIELD_TYPE_INTEGER||row.getType(1)!=Cursor.FIELD_TYPE_INTEGER)throw new IOException("作用域字段类型未知");
                int active=row.getInt(0),scope=row.getInt(1);if((active!=0&&active!=1)||(scope!=0&&scope!=1)||row.moveToNext())throw new IOException("作用域记录格式未知");
                if(selected!=null&&(selected!=(scope==1)||enabled!=(active==1)))conflict=true;
                selected=scope==1;enabled=active==1;source=file.toString();
            }catch(Exception|LinkageError unavailable){failed=true;attempts.put(new JSONObject().put("path",file.toString()).put("state","unavailable").put("detail",unavailable.toString()));}
        }
        String state=conflict||failed?"unknown":selected!=null?(selected?"present":"missing"):sawSchema?"module_missing":"unknown";
        return new JSONObject().put("scope_state",state).put("module_enabled",enabled==null||conflict||failed?JSONObject.NULL:enabled).put("scope_source",conflict||failed?"":source).put("scope_route","lsposed_sqlite_readonly").put("scope_attempts",attempts).put("scope_conflict",conflict);
    }
    static final class Identity {
        final int pid,uid;final String name,start;
        Identity(int pid,int uid,String name,String start){this.pid=pid;this.uid=uid;this.name=name;this.start=start;}
    }
    static Identity identity(File proc,int pid)throws Exception{
        File dir=new File(proc,String.valueOf(pid));byte[] cmd=Files.readAllBytes(new File(dir,"cmdline").toPath());if(cmd.length>4096)throw new IOException("进程名过长");
        String name=new String(cmd,StandardCharsets.UTF_8).replace('\0',' ').trim();
        String stat=new String(Files.readAllBytes(new File(dir,"stat").toPath()),StandardCharsets.US_ASCII);int bracket=stat.lastIndexOf(')');if(bracket<1)throw new IOException("进程启动标识无效");String[] fields=stat.substring(bracket+1).trim().split("\\s+");if(fields.length<20)throw new IOException("进程启动标识不完整");String start=fields[19];
        int uid=-1;for(String line:Files.readAllLines(new File(dir,"status").toPath(),StandardCharsets.US_ASCII))if(line.startsWith("Uid:")){String[] values=line.substring(4).trim().split("\\s+");if(values.length!=4)break;uid=Integer.parseInt(values[0]);for(String value:values)if(Integer.parseInt(value)!=uid)throw new IOException("进程身份不一致");}
        if(pid<=1||uid<0||!start.matches("[0-9]+"))throw new IOException("进程身份无效");return new Identity(pid,uid,name,start);
    }
    static JSONObject injection(JSONObject report,String raw,File proc)throws JSONException{
        String state="unconfirmed";JSONObject status=null;
        if(raw!=null&&raw.length()<=262144)try{
            status=new JSONObject(raw);int pid=status.optInt("pid",-1);Identity live=identity(proc,pid);
            if(PACKAGE.equals(live.name)&&live.start.equals(status.optString("process_start")))state=AppBuild.BUILD.equals(status.optString("build"))?"current":"old_build";
        }catch(Exception ignored){}
        report.put("injection_state",state);if(state.equals("current"))report.put("coordinate_abi",status.optBoolean("coordinate_abi")).put("profile_ready",status.optBoolean("profile_ready")).put("injection_detail",status.optString("detail"));return report;
    }
    static JSONObject inspect(RootSettings settings)throws JSONException{
        JSONObject report=scopes(databases(new File("/data/adb")));String raw=null;
        try{raw=settings.get(ControlCenterEngine.UI);}catch(Exception unavailable){report.put("injection_read_error",unavailable.toString());}
        return injection(report,raw,new File("/proc")).put("checked_elapsed_ms",android.os.SystemClock.elapsedRealtime());
    }
    interface Processes {Identity read(int pid)throws Exception;void terminate(int pid)throws Exception;}
    static boolean restartTarget(Processes processes,Identity expected,int packageUid)throws Exception{
        if(expected==null||expected.pid<=1||packageUid<0||expected.uid!=packageUid||!PACKAGE.equals(expected.name)||expected.start.isEmpty())return false;
        Identity current=processes.read(expected.pid);
        if(current.pid!=expected.pid||current.uid!=packageUid||!PACKAGE.equals(current.name)||!expected.start.equals(current.start))return false;
        processes.terminate(current.pid);return true;
    }
    static int packageUid(String output)throws IOException{
        for(String line:output.split("\n"))if(line.trim().matches("package:com\\.android\\.systemui uid:[0-9]+"))try{return Integer.parseInt(line.substring(line.lastIndexOf(':')+1).trim());}catch(NumberFormatException invalid){break;}
        throw new IOException("无法确认系统界面应用身份，未执行重启");
    }
    static JSONObject restart(RootSettings settings)throws Exception{
        JSONObject report=inspect(settings);
        if("missing".equals(report.optString("scope_state"))||"module_missing".equals(report.optString("scope_state"))||report.has("module_enabled")&&!report.isNull("module_enabled")&&!report.optBoolean("module_enabled"))throw new IOException("请先在 LSPosed 启用 HyperLux 并勾选系统界面作用域");
        int uid=packageUid(RootCommand.run(5000,"/system/bin/cmd","package","list","packages","--user","0","-U",PACKAGE));
        File proc=new File("/proc");File[] entries=proc.listFiles();if(entries==null)throw new IOException("Root 无法读取系统界面进程，未执行重启");
        Processes processes=new Processes(){public Identity read(int pid)throws Exception{return identity(proc,pid);}public void terminate(int pid)throws Exception{Os.kill(pid,OsConstants.SIGKILL);}};
        int stopped=0;for(File entry:entries){if(!entry.getName().matches("[0-9]{1,9}"))continue;Identity before;try{before=identity(proc,Integer.parseInt(entry.getName()));}catch(Exception absent){continue;}if(!PACKAGE.equals(before.name)||before.uid!=uid)continue;if(restartTarget(processes,before,uid))stopped++;}
        if(stopped==0)throw new IOException("未找到可核验的系统界面进程，未执行重启");
        return new JSONObject().put("ok",true).put("terminated",stopped).put("message","已请求重启系统界面，状态栏和控制中心会短暂消失");
    }
}
