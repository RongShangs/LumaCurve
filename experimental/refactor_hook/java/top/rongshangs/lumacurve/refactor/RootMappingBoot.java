package top.rongshangs.lumacurve.refactor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import org.json.*;

/** Optional Root startup entry. Own file only; no manager names, scope changes or node writes. */
final class RootMappingBoot {
    static final String KEY="hyperlux_mapping_boot_entry_v1",NAME="hyperlux-mapping.sh";
    static final File DIRECTORY=new File("/data/adb/service.d");
    static final String SCRIPT="#!/system/bin/sh\n# HyperLux owned mapping startup v1\numask 077\ncd /\n"+
        "[ -L /data/adb/service.d/hyperlux-mapping-boot.log ] && exit 1\nexec > /data/adb/service.d/hyperlux-mapping-boot.log 2>&1\n"+
        "echo HYPERLUX_BOOT_ENTRY\ni=0\nwhile [ \"$(/system/bin/getprop sys.boot_completed)\" != 1 ]; do\n i=$((i+1)); [ $i -ge 120 ] && exit 1; /system/bin/sleep 2\ndone\n"+
        "[ -x /system/bin/timeout ] || { echo timeout_unavailable; exit 1; }\n"+
        "for delay in 0 3 10 30; do\n /system/bin/sleep $delay\n"+
        " apk=$(/system/bin/timeout 3 /system/bin/cmd package path --user 0 top.rongshangs.lumacurve 2>/dev/null)\n"+
        " case \"$apk\" in *'\n'*) echo ambiguous_package_path; exit 1;; package:/data/app/*/base.apk) apk=${apk#package:};; *) echo package_unavailable; continue;; esac\n"+
        " [ -f \"$apk\" ] && [ ! -L \"$apk\" ] || exit 1\n"+
        " result=$(CLASSPATH=\"$apk\" /system/bin/timeout 8 /system/bin/app_process /system/bin top.rongshangs.lumacurve.refactor.RootControl boot-resume-mapping)\n echo \"$result\"\n"+
        " case \"$result\" in *'\"state\":\"pending\"'*) ;; *) exit 0;; esac\ndone\n";
    static String hash(byte[] data)throws Exception{StringBuilder b=new StringBuilder();for(byte n:MessageDigest.getInstance("SHA-256").digest(data))b.append(String.format(java.util.Locale.ROOT,"%02x",n&255));return b.toString();}
    static boolean wanted(JSONObject config){return BrightnessControlOptions.mappingRecoveryWanted(config);}
    static JSONObject inspect(RootSettings settings)throws Exception{return inspect(settings,DIRECTORY);}
    static JSONObject inspect(RootSettings settings,File directory)throws Exception{
        JSONObject result=new JSONObject().put("route","root_service_directory").put("installed",false);String raw=settings.get(KEY);if(raw!=null)result=new JSONObject(raw);
        File file=new File(directory,NAME);boolean valid=!Files.isSymbolicLink(directory.toPath())&&!Files.isSymbolicLink(file.toPath())&&file.isFile()&&file.canExecute()&&file.length()<32768&&hash(Files.readAllBytes(file.toPath())).equals(result.optString("sha256"));
        String last=settings.get(ControlCenterRoot.RECOVERY+"_root");if(last!=null)try{JSONObject run=new JSONObject(last);result.put("last_result",run).put("current_boot",String.valueOf(settings.get("boot_count")).equals(run.optString("boot_count"))&&AppBuild.BUILD.equals(run.optString("build")));}catch(JSONException unavailable){}
        File log=new File(directory,"hyperlux-mapping-boot.log");boolean present=!Files.isSymbolicLink(log.toPath())&&log.isFile();
        return result.put("route","root_service_directory").put("installed",valid).put("path",file.toString()).put("entry_modified_ms",file.lastModified()).put("log_present",present).put("log_readable",present&&log.canRead()).put("log_bytes",present?log.length():0).put("log_modified_ms",present?log.lastModified():0);
    }
    static JSONObject configure(RootSettings settings,String raw){try{return configure(settings,raw,DIRECTORY);}catch(Exception failure){try{return new JSONObject().put("route","root_service_directory").put("installed",false).put("detail",failure.toString());}catch(JSONException impossible){throw new IllegalStateException(impossible);}}}
    static JSONObject configure(RootSettings settings,String raw,File directory)throws Exception{
        JSONObject config=raw==null?null:new JSONObject(raw);boolean enabled=wanted(config);Path dir=directory.toPath(),file=dir.resolve(NAME);String old=settings.get(KEY);JSONObject previous=old==null?new JSONObject():new JSONObject(old);
        if(Files.isSymbolicLink(dir)||Files.isSymbolicLink(directory.getParentFile().toPath())||Files.isSymbolicLink(file))throw new IOException("启动入口路径不可确认");
        byte[] bytes=SCRIPT.getBytes(StandardCharsets.UTF_8);String digest=hash(bytes);
        if(Files.exists(file)){if(!Files.isRegularFile(file)||Files.size(file)>32768)throw new IOException("启动入口不是受管文件");byte[] saved=Files.readAllBytes(file);String current=hash(saved);if(!new String(saved,StandardCharsets.UTF_8).startsWith("#!/system/bin/sh\n# HyperLux owned mapping startup v1\n")||!current.equals(digest)&&!current.equals(previous.optString("sha256")))throw new IOException("保留已有未知启动文件");}
        if(enabled){Files.createDirectories(dir);if(!directory.getCanonicalFile().equals(directory.getAbsoluteFile()))throw new IOException("启动目录不可确认");
            if(!Files.exists(file)||!hash(Files.readAllBytes(file)).equals(digest)){Path temp=Files.createTempFile(dir,".hyperlux-mapping-",".tmp");try{Files.write(temp,bytes);if(!temp.toFile().setReadable(true,true)||!temp.toFile().setWritable(true,true)||!temp.toFile().setExecutable(true,true))throw new IOException("启动脚本权限未确认");Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}
            if(!file.toFile().setExecutable(true,true))throw new IOException("启动脚本执行权限未确认");
        }else Files.deleteIfExists(file);
        JSONObject result=new JSONObject().put("route","root_service_directory").put("installed",enabled).put("sha256",digest).put("build",AppBuild.BUILD).put("path",file.toString()).put("detail",enabled?"已配置Root开机入口，执行结果单独记录":"未开启映射，启动入口已撤回");
        if(!result.toString().equals(old)&&!settings.put(KEY,result.toString()))throw new IOException("启动入口记录未写入");return result;
    }
}
