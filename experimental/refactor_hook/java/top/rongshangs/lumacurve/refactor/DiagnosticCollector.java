package top.rongshangs.lumacurve.refactor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import org.json.*;

/** On-demand, bounded read-only diagnostics. No sensor registration or brightness writes. */
final class DiagnosticCollector {
    interface SettingsReader {String get(String name)throws Exception;}
    final ZipOutputStream zip;final JSONArray manifest=new JSONArray();long copied;int files;
    final SettingsReader settingsReader;
    final long deadline;
    static final long FILE_LIMIT=64L*1024*1024,TOTAL_LIMIT=256L*1024*1024;
    DiagnosticCollector(ZipOutputStream zip){this(zip,null,180000);}
    DiagnosticCollector(ZipOutputStream zip,SettingsReader reader){this(zip,reader,180000);}
    DiagnosticCollector(ZipOutputStream zip,long budgetMillis){this(zip,null,budgetMillis);}
    DiagnosticCollector(ZipOutputStream zip,SettingsReader reader,long budgetMillis){this.zip=zip;settingsReader=reader;deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(Math.max(1,Math.min(180000,budgetMillis)));}
    long remainingMillis(){return Math.max(0,TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()));}
    void note(String path,String status,String detail)throws JSONException{manifest.put(new JSONObject().put("path",path).put("status",status).put("detail",detail));}
    void collectSettings()throws Exception {
        JSONObject entries=new JSONObject();
        long collectedAt=System.currentTimeMillis();
        for(String key:new String[]{"screen_brightness_mode","screen_brightness","screen_auto_brightness_adj"}){
            String status="ok",value=null,error=null;
            if(remainingMillis()<=0){status="skipped_budget";error="total collection time budget exhausted";}
            else if(settingsReader==null){status="unavailable";error="Settings provider reader unavailable";}
            else try{value=settingsReader.get(key);if(value==null)status="missing";}
            catch(Exception failure){status="error";error=failure.toString();}
            JSONObject entry=new JSONObject().put("status",status).put("value",value==null?JSONObject.NULL:value);
            if(error!=null)entry.put("error",error);
            entries.put(key,entry);
            String path="settings/"+key+".txt";
            // Preserve zero and the provider's exact value; absence/errors are never fake numeric defaults.
            RootControl.write(zip,path,status.equals("ok")?value+"\n":"["+status+"] "+(error==null?"Setting not present for user 0":error)+"\n");
            note(path,status,"source=SettingsProvider; namespace=system; user=0"+(error==null?"":"; "+error));
        }
        RootControl.write(zip,"settings/system.json",new JSONObject().put("source","SettingsProvider").put("namespace","system")
            .put("user",0).put("collected_unix_ms",collectedAt).put("entries",entries).toString(2));
        note("settings/system.json","ok","per-setting statuses included; primary user 0; sequential read-only snapshot");
    }
    void command(String name,int seconds,String...args)throws Exception{
        long remaining=remainingMillis();if(remaining<=0){note(name,"skipped_budget","total collection time budget exhausted");return;}
        File temp=File.createTempFile("diagnostic-",".txt",RootControl.DATA);Process process=null;
        byte[] output=null;
        try{
            process=new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(temp).start();
            boolean done=process.waitFor(Math.min(seconds*1000L,remaining),TimeUnit.MILLISECONDS);
            if(!done){process.destroyForcibly();process.waitFor(2,TimeUnit.SECONDS);}
            // Include partial output and mark timeout/nonzero, rather than aborting the archive.
            long size=temp.length();int cap=2*1024*1024;byte[] data=new byte[(int)Math.min(size,cap)];
            boolean tail=name.startsWith("faults/")&&size>cap;
            try(RandomAccessFile input=new RandomAccessFile(temp,"r")){if(tail)input.seek(size-cap);int pos=0,n;while(pos<data.length&&(n=input.read(data,pos,data.length-pos))>0)pos+=n;if(pos!=data.length)data=Arrays.copyOf(data,pos);}
            output=data;
            note(name,!done?"timeout":process.exitValue()!=0?"nonzero":size>cap?"truncated":"ok",String.join(" ",args)+"; bytes="+size+"; retained="+(tail?"tail":"head")+"; exit="+(done?process.exitValue():"timeout"));
        }catch(IOException unavailable){note(name,"unavailable",unavailable.toString());}
        finally{if(process!=null&&process.isAlive())process.destroyForcibly();temp.delete();}
        // Archive write failures must abort export; they are not a missing diagnostic source.
        if(output!=null)RootControl.write(zip,name,new String(output,StandardCharsets.UTF_8));
    }
    void copy(File source,String destination)throws Exception{
        if(remainingMillis()<=0){note(destination,"skipped_budget","total collection time budget exhausted");return;}
        if(!source.isFile()||!source.canRead()){note(destination,"unavailable",source.toString());return;}
        long size=source.length();if(size>FILE_LIMIT||copied+size>TOTAL_LIMIT||files>=256){note(destination,"skipped_limit",source+"; bytes="+size);return;}
        InputStream input;try{input=new FileInputStream(source);}catch(IOException unavailable){note(destination,"unavailable",unavailable.toString());return;}
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long count=0;String error=null;
        zip.putNextEntry(new ZipEntry(destination));
        try(InputStream in=input){byte[] buffer=new byte[65536];int n;
            while(true){if(remainingMillis()<=0){error="total collection time budget exhausted";break;}try{n=in.read(buffer);}catch(IOException unreadable){error=unreadable.toString();break;}if(n==-1)break;
                if(count+n>FILE_LIMIT||copied+n>TOTAL_LIMIT){error="file grew beyond collection limit";break;}
                zip.write(buffer,0,n);digest.update(buffer,0,n);count+=n;copied+=n;
            }
        }finally{zip.closeEntry();}
        files++;StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));
        manifest.put(new JSONObject().put("path",destination).put("source",source.toString()).put("status",error!=null?"partial":count==size?"ok":"changed_size")
            .put("bytes",count).put("sha256",hash.toString()).put("detail",error==null?"hash of archived bytes":error));
    }
    void tree(File directory,String destination,int depth)throws Exception{
        if(remainingMillis()<=0){note(destination,"skipped_budget","total collection time budget exhausted");return;}
        if(depth>5||!directory.isDirectory()){note(destination,"unavailable",directory.toString());return;}
        File[] children=directory.listFiles();if(children==null){note(destination,"unavailable",directory.toString());return;}Arrays.sort(children);
        String canonical=directory.getCanonicalPath()+File.separator;
        for(File child:children){if(!child.getCanonicalPath().startsWith(canonical)){note(destination+"/"+child.getName(),"skipped_symlink","outside collection directory");continue;}
            if(files>=256){note(destination,"skipped_limit","file count limit");break;}
            if(child.isDirectory())tree(child,destination+"/"+child.getName(),depth+1);else copy(child,destination+"/"+child.getName());
        }
    }
    void collectFaults()throws Exception{
        // Capture short-lived evidence before slow dumps and optional firmware copies.
        // DropBox survives some process/device restarts; absence is never proof of no fault.
        command("faults/crash-log.txt",6,"logcat","-b","crash","-d","-v","threadtime");
        command("faults/system-log.txt",6,"logcat","-b","system","-b","main","-d","-v","threadtime",
            "Watchdog:V","AndroidRuntime:V","ActivityManager:V","ActivityTaskManager:V","WindowManager:V","InputDispatcher:V","SurfaceFlinger:V","SystemUI:V","DEBUG:V","LumaCurve:V","LSPosed-Bridge:V","*:S");
        command("faults/process-events.txt",6,"logcat","-b","events","-d","-v","threadtime",
            "am_crash:V","am_anr:V","am_proc_died:V","am_proc_start:V","am_kill:V","boot_progress_system_run:V","*:S");
        for(String tag:FAULT_TAGS)
            command("faults/dropbox-"+tag+".txt",6,dropboxArguments(tag));
    }
    static final String[] FAULT_TAGS={"system_server_watchdog","system_server_crash","system_server_anr","system_app_crash","system_app_anr"};
    static final String[] ANR_PREFIXES={"anr_","traces","BinderTraces_pid"},MIUI_WATCHDOG_PREFIXES={"watchdog_pid_","pre_watchdog_pid_"};
    static String[] dropboxArguments(String tag){
        if(!Arrays.asList(FAULT_TAGS).contains(tag))throw new IllegalArgumentException("Unknown fault tag");
        // dumpsys treats dates as exact search terms, not a lower time bound.
        return new String[]{"dumpsys","-t","4","dropbox","--print",tag};
    }
    void recentFaultFiles(File directory,String destination,String[] prefixes,int maximum,long now)throws Exception{
        if(remainingMillis()<=0){note(destination,"skipped_budget","total collection time budget exhausted");return;}
        File[] candidates=directory.listFiles();if(candidates==null){note(destination,"unavailable","fault directory absent or inaccessible");return;}
        Arrays.sort(candidates,Comparator.comparingLong(File::lastModified).reversed().thenComparing(File::getName));
        File canonical=directory.getCanonicalFile();int selected=0;
        for(File file:candidates){
            if(remainingMillis()<=0){note(destination,"skipped_budget","total collection time budget exhausted");break;}
            String name=file.getName();boolean match=false;for(String prefix:prefixes)if(name.startsWith(prefix)){match=true;break;}
            if(!match||!file.isFile()||file.lastModified()<now-3L*24*60*60*1000)continue;
            String path=destination+"/"+name;
            if(Files.isSymbolicLink(file.toPath())||!canonical.equals(file.getCanonicalFile().getParentFile())){note(path,"skipped_symlink","outside fault directory or symbolic link");continue;}
            if(selected++>=maximum){note(destination,"skipped_limit","newest "+maximum+" matching files selected");break;}
            if(file.length()>8L*1024*1024){note(path,"skipped_limit","fault file exceeds 8 MiB; not archived as a partial compressed file");continue;}
            copy(file,path);
            if(remainingMillis()<=0)break;
        }
    }
    void collect()throws Exception{
        RootControl.progress("2/8 优先收集故障记录，再读取显示、光感与电源状态…");
        collectFaults();
        long faultTime=System.currentTimeMillis();
        recentFaultFiles(new File("/data/anr"),"faults/anr",ANR_PREFIXES,6,faultTime);
        recentFaultFiles(new File("/data/miuilog/stability/scout/watchdog"),"faults/miui-watchdog",MIUI_WATCHDOG_PREFIXES,6,faultTime);
        String[] rawTags=new String[FAULT_TAGS.length];for(int i=0;i<FAULT_TAGS.length;i++)rawTags[i]=FAULT_TAGS[i]+"@";
        recentFaultFiles(new File("/data/system/dropbox"),"faults/dropbox-files",rawTags,10,faultTime);
        for(String service:new String[]{"display","sensorservice","power","thermalservice","battery"})command(service+".txt",12,"dumpsys",service);
        command("thermal-zones.txt",5,"sh","-c","for n in /sys/class/thermal/thermal_zone*; do [ -d \"$n\" ] || continue; echo \"ZONE $n\"; for f in type temp; do [ -r \"$n/$f\" ] && { echo \"$f\"; cat \"$n/$f\"; }; done; done; exit 0");
        RootControl.progress("3/8 收集显示接口、资源覆盖与近期系统记录…");
        command("display-command-help.txt",5,"cmd","display","help");command("resource-overlays.txt",5,"cmd","overlay","list");
        command("surface-displays.txt",8,"dumpsys","SurfaceFlinger","--display-id");
        command("app-package.txt",5,"dumpsys","package","top.rongshangs.lumacurve");
        command("system-display-log.txt",8,"logcat","-b","system","-b","main","-d","-t","1500","-v","threadtime",
            "DisplayPowerController:V","DisplayPowerControllerImpl:V","AutomaticBrightnessController:V","AutomaticBrightnessControllerImpl:V","DualSensorPolicy:V","RefactorNitController:V","SunlightController:V","HighBrightnessModeController:V","LumaCurve:V","LSPosed-Bridge:V","*:S");
        command("processes.txt",5,"ps","-A","-o","PID,PPID,UID,NAME,ARGS");
        command("system-server-process.txt",5,"sh","-c","p=$(pidof system_server); case \"$p\" in ''|*[!0-9]*) exit 1;; esac; cat /proc/$p/status /proc/$p/cgroup");
        File[] hookLogs=new File("/data/adb/lspd/log").listFiles((directory,name)->name.startsWith("modules")&&name.endsWith(".log"));
        if(hookLogs==null)note("lsposed","unavailable","module log directory absent or inaccessible");
        else{Arrays.sort(hookLogs,(a,b)->Long.compare(b.lastModified(),a.lastModified()));for(int i=0;i<Math.min(3,hookLogs.length);i++)command("lsposed/"+hookLogs[i].getName(),3,"tail","-c","524288",hookLogs[i].toString());}
        RootControl.progress("4/8 收集固件标识、亮度资源和背光只读信息…");
        String[] props={"ro.product.model","ro.product.device","ro.product.board","ro.product.cpu.abi","ro.build.fingerprint","ro.build.version.sdk","ro.build.version.release","ro.build.version.incremental","ro.mi.os.version.name","ro.mi.os.version.incremental"};
        for(String prop:props)command("properties/"+prop+".txt",2,"getprop",prop);
        command("environment.txt",5,"sh","-c","date; id; getenforce; cat /proc/uptime /proc/sys/kernel/random/boot_id; printf '\\nBOOTCLASSPATH=%s\\nSYSTEMSERVERCLASSPATH=%s\\n' \"$BOOTCLASSPATH\" \"$SYSTEMSERVERCLASSPATH\"");
        collectSettings();
        for(String resource:new String[]{"config_screenBrightnessNits","config_screenBrightnessBacklight","config_screenBrightnessBacklightFloat","config_autoBrightnessLevels","config_autoBrightnessDisplayValuesNits"})command("resources/"+resource+".txt",3,"cmd","overlay","lookup","android","android:array/"+resource);
        command("backlight.txt",5,"sh","-c","for n in /sys/class/backlight/*; do [ -d \"$n\" ] || continue; echo \"BACKLIGHT $n\"; readlink -f \"$n\"; ls -l \"$n/brightness\"; for f in brightness actual_brightness max_brightness bl_power type; do [ -r \"$n/$f\" ] && { echo \"$f\"; cat \"$n/$f\"; }; done; done; for n in /sys/class/drm/*; do [ -d \"$n\" ] || continue; echo \"DRM $n\"; for f in status enabled dpms; do [ -r \"$n/$f\" ] && { echo \"$f\"; cat \"$n/$f\"; }; done; done; exit 0");
        command("led-brightness-nodes.txt",5,"sh","-c","for n in /sys/class/leds/*; do [ -d \"$n\" ] || continue; echo \"LED $n\"; readlink -f \"$n\"; ls -l \"$n/brightness\"; for f in brightness max_brightness actual_brightness; do [ -r \"$n/$f\" ] && { echo \"$f\"; head -c 128 \"$n/$f\"; echo; }; done; done; exit 0");
        RootControl.progress("5/8 收集显示配置文件…");
        for(String base:new String[]{"/system","/system_ext","/product","/vendor","/odm"})tree(new File(base+"/etc/displayconfig"),"display-config"+base+"/etc/displayconfig",0);
        RootControl.progress("6/8 提取系统与小米显示框架，计算校验值…");
        for(String base:new String[]{"/system/framework","/system_ext/framework","/product/framework","/vendor/framework"}){
            File[] jars=new File(base).listFiles();if(jars==null){note("firmware"+base,"unavailable","directory missing");continue;}Arrays.sort(jars);
            for(File jar:jars){String name=jar.getName().toLowerCase(Locale.ROOT);
                if(name.endsWith(".jar")&&(name.equals("framework.jar")||name.contains("services")||name.contains("miui")||name.contains("xiaomi")||name.contains("display")||name.contains("brightness")))copy(jar,"firmware"+base+"/"+jar.getName());
            }
        }
        for(String path:new String[]{"/system/framework/framework-res.apk","/system/framework/miui-framework-res.apk","/system_ext/framework/miui-framework-res.apk"})copy(new File(path),"firmware"+path);
        RootControl.progress("7/8 收集旧模块诊断并再次采集显示快照…");
        for(String path:new String[]{"/data/local/tmp/luma_curve_state","/data/local/tmp/luma_curve.conf","/data/adb/modules/luma_curve/module.prop","/data/adb/modules/luma_curve/build-info.json","/data/adb/modules/luma_curve/compatibility-report.txt"})copy(new File(path),"legacy/"+new File(path).getName());
        if(new File("/data/local/tmp/luma_curve.log").isFile())command("legacy/recent-log.txt",3,"tail","-n","250","/data/local/tmp/luma_curve.log");
        command("display-second.txt",12,"dumpsys","display");
        RootControl.write(zip,"collection-manifest.json",new JSONObject().put("format",1).put("app_build",AppBuild.BUILD).put("copied_bytes",copied).put("file_limit_bytes",FILE_LIMIT).put("total_file_limit_bytes",TOTAL_LIMIT).put("entries",manifest).toString(2));
        RootControl.write(zip,"analysis-readme.txt","Read-only, on-demand collection; no brightness writes or sensor registrations. Snapshots are sequential, not simultaneous.\nstate.json/config.json/logs.txt are app/Hook snapshots. pipeline-trace.json and output-trace.json are bounded histories.\nsettings/system.json and settings/*.txt read the three brightness System settings directly through SettingsProvider for primary user 0. Values are exact strings; missing/error/skipped_budget are distinct from zero. No settings shell command or numeric fallback.\ncollection-manifest.json records command errors, timeouts, truncation, unavailable files and SHA-256 hashes of copied files. Partial entries are not complete firmware.\nfaults/ contains bounded crash/system/event logs (latest 2 MiB when truncated) and retained system-server/system-app DropBox tag entries without a CLI date filter. Raw ANR/DropBox files from the last 72 hours are collected newest first, at most 6/10 files and 8 MiB per file; up to 6 recent MIUI Scout watchdog text files and ANR BinderTraces are also selected without heap dumps, before slow display dumps and firmware. DropBox retention is OEM-dependent; empty/missing records do not prove no crash. Full reboot generally loses in-memory logcat and runtime histories; exported archives remain on disk.\nFirmware and display resources are for private diagnostic analysis, not redistribution in public source, APKs or the website.\n");
    }
}
