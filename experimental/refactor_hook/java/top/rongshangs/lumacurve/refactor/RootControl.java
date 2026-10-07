package top.rongshangs.lumacurve.refactor;

import android.content.*;
import android.os.Build;
import android.provider.Settings;
import org.json.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;

/** A short-lived root command, not a brightness output service. */
public final class RootControl implements AutoCloseable {
    static final String CONFIG="lumacurve_refactor_config_v1",STATUS="lumacurve_refactor_status_v1",REFRESH="lumacurve_refactor_refresh_v1",ACK="lumacurve_refactor_ack_v1",BUILD=AppBuild.BUILD;
    static final File DATA=new File("/data/adb/luma_curve_refactor_test"),PAUSE_OWNED=new File(DATA,"old-pause-owned");
    static final String OLD="/data/adb/modules/luma_curve/luma_curvectl.sh";
    final RootSettings settings;
    RootControl()throws Exception {
        settings=new RootSettings();
    }
    public void close()throws Exception{settings.close();}
    static String process(String...args)throws Exception {
        File temp=File.createTempFile("root-command-",".txt",DATA);
        try {
            Process proc=new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(temp).start();
            if(!proc.waitFor(30,TimeUnit.SECONDS)){proc.destroyForcibly();throw new IOException("操作超时："+args[0]);}
            if(temp.length()>8*1024*1024)throw new IOException("命令输出过长");
            String text=new String(Files.readAllBytes(temp.toPath()),StandardCharsets.UTF_8);
            if(proc.exitValue()!=0)throw new IOException("操作失败："+String.join(" ",args)+"\n"+text);
            return text.trim();
        } finally {temp.delete();}
    }
    static String startFor(int pid)throws Exception {
        String text=new String(Files.readAllBytes(Paths.get("/proc/"+pid+"/stat")),StandardCharsets.UTF_8);
        return text.substring(text.lastIndexOf(')')+2).split(" +")[19];
    }
    JSONObject live() {
        JSONObject state=validated(STATUS);return state==null?null:StatusTransport.restore(state,settings::get);
    }
    JSONObject validated(String key) {
        try {
            String text=settings.get(key);if(text==null)return null;
            JSONObject status=new JSONObject(text);if(!BUILD.equals(status.getString("build")))return null;
            int pid=status.getInt("pid");
            String cmd=new String(Files.readAllBytes(Paths.get("/proc/"+pid+"/cmdline")),StandardCharsets.UTF_8);
            if(!cmd.replace('\0',' ').trim().equals("system_server") || !startFor(pid).equals(status.getString("process_start")))return null;
            if(!Build.FINGERPRINT.equals(status.getString("fingerprint")))return null;
            return status;
        }catch(Exception unavailable){return null;}
    }
    JSONObject refreshedLive()throws Exception {
        JSONObject state=live();
        if(state!=null){
            long before=state.optLong("elapsed_ms");
            settings.put(REFRESH,UUID.randomUUID().toString());
            for(int i=0;i<10;i++){JSONObject newer=live();if(newer!=null&&newer.optLong("elapsed_ms")>before){state=newer;break;}Thread.sleep(30);}
        }
        return state;
    }
    JSONObject inspect()throws Exception {
        JSONObject state=refreshedLive();
        String raw=settings.get(CONFIG);
        JSONObject out=new JSONObject().put("ok",true).put("connected",state!=null).put("fingerprint",Build.FINGERPRINT);
        SystemVersion.read().put(out);
        JSONObject injection=validated("lumacurve_refactor_injection_v1");out.put("lsp_loaded",state!=null||injection!=null);
        if(injection!=null){out.put("injection",injection);if(state==null)out.put("hook_issue",injection.optString("stage")).put("hook_message",injection.optString("message"));}
        JSONArray legacy=new JSONArray();for(String[] module:LegacyModules.scan(new File("/data/adb/modules"),new File("/data/adb/modules_update")))legacy.put(new JSONObject().put("id",module[0]).put("name",module[1]).put("state",module[2]));out.put("legacy_modules",legacy);
        if(state==null){
            try{JSONObject cached=new JSONObject(settings.get(STATUS));int pid=cached.getInt("pid");
                if(startFor(pid).equals(cached.getString("process_start"))&&Build.FINGERPRINT.equals(cached.getString("fingerprint"))&&!BUILD.equals(cached.optString("build")))
                    out.put("hook_issue","restart_required");
            }catch(Exception ignored){}
        }
        if(state!=null)out.put("runtime",state).put("state_age_ms",Math.max(0,android.os.SystemClock.elapsedRealtime()-state.optLong("elapsed_ms")));
        if(raw!=null)try{out.put("config",new JSONObject(raw));}catch(JSONException ignored){}
        return out;
    }
    boolean waitFor(String revision,boolean active)throws Exception {
        for(int attempt=0;attempt<60;attempt++) {
            // A missed/coalesced config notification gets an idempotent reread on
            // the display thread. Do not resend the configuration or reset anchors.
            if(attempt%20==0)settings.put(REFRESH,UUID.randomUUID().toString());
            JSONObject state=validated(ACK);
            if(state==null||!revision.equals(state.optString("revision")))state=validated(STATUS);
            if(state!=null && revision.equals(state.optString("revision")) && state.optString("phase").equals(active?"active":"attached"))return true;
            if(state!=null && revision.equals(state.optString("revision")) && state.optString("phase").equals("error"))throw new IOException(state.optString("message"));
            Thread.sleep(100);
        }
        return false;
    }
    String confirmationDetail(){
        JSONObject ack=validated(ACK),state=live();
        if(ack==null&&state==null)return "未取得当前系统进程的有效确认，请核对 LSPosed 并重启。";
        JSONObject last=ack!=null?ack:state;
        return "最后确认状态："+last.optString("phase","unknown")+"\n"+last.optString("message","");
    }
    static boolean exists(String path){return new File(path).exists();}
    static void progress(String message){System.out.println("LUMA_PROGRESS="+message);}
    void prepare()throws Exception {
        // Restore the ineffective standard-API test before touching the predecessor.
        File session=new File("/data/adb/luma_curve_official_test/session.properties");
        if(session.isFile()) {
            Properties props=new Properties();try(InputStream in=new FileInputStream(session)){props.load(in);}
            if(Arrays.asList("active","prepared").contains(props.getProperty("phase"))) {
                progress("恢复上一轮标准曲线测试…");
                String recovery="/data/adb/luma_curve_official_test/recovery/ctl.sh";
                if(!exists(recovery))throw new IOException("请先在官方曲线 test01 中恢复原配置");
                process("sh",recovery,"restore");
            }
        }
        if(!exists(OLD)||exists("/data/adb/modules/luma_curve/disable")||exists("/data/adb/modules/luma_curve/remove"))return;
        progress("暂停旧版亮度引擎…");
        if(!exists("/data/local/tmp/luma_curve.paused"))Files.write(PAUSE_OWNED.toPath(),new byte[]{'1'});
        process("sh",OLD,"pause");
        for(int i=0;i<100;i++) {
            String processes=process("ps","-A","-o","ARGS");
            if(!processes.contains("LumaFrameworkOutputBroker") && !processes.contains("luma_curve_daemon") && !exists("/data/local/tmp/luma-framework-owner.lock"))return;
            Thread.sleep(100);
        }
        throw new IOException("旧引擎仍在退出，尚未启用新曲线");
    }
    void resumeOld()throws Exception {
        if(!PAUSE_OWNED.exists())return;
        if(exists(OLD)&&!exists("/data/adb/modules/luma_curve/disable")&&!exists("/data/adb/modules/luma_curve/remove"))process("sh",OLD,"resume");
        PAUSE_OWNED.delete();
    }
    JSONObject apply(String encoded)throws Exception {
        JSONObject state=refreshedLive();if(state==null)throw new IOException("尚未连接 Hook：请在 LSPosed 启用本模块，勾选系统框架并重启");
        int user=((Number)Class.forName("android.app.ActivityManager").getMethod("getCurrentUser").invoke(null)).intValue();
        int serial=state.optInt("user_serial",-1);
        if(user!=0||serial>0)throw new IOException("本应用暂只支持主用户，请切回主用户后重试");
        if(!ForegroundUser.canApply(user,serial))throw new IOException("系统亮度的用户身份尚未就绪，请稍后再点保存并应用");
        if(encoded.length()>8192)throw new IOException("参数过长");
        String decoded=new String(Base64.getDecoder().decode(encoded),StandardCharsets.UTF_8);
        JSONObject options=decoded.startsWith("{")?new JSONObject(decoded):new JSONObject().put("factors",decoded);
        String factors=options.getString("factors");
        BrightnessControlOptions controls=BrightnessControlOptions.parseStored(options);controls.verify(state);
        AdvancedOptions advanced=AdvancedOptions.parse(options);advanced.verify(state);
        OutdoorOptions outdoor=OutdoorOptions.parse(options);outdoor.verify(state);
        boolean thermal=options.optBoolean("thermal_relax",false);float ceiling=(float)options.optDouble("thermal_ceiling",43);ThermalPolicy.validate(ceiling);
        float memory=(float)options.optDouble("memory_strength",1);MemoryPolicy.validate(memory);
        long memoryMs=options.optLong("memory_window",1500);float memoryRange=(float)options.optDouble("memory_lux_range",.3);MemoryPolicy.validateGrouping(memoryMs,memoryRange);
        MemoryOptions memoryOptions=MemoryOptions.parse(options).fit(state.optInt("memory_point_capacity",0));memoryOptions.verify(state);
        float cooling=(float)options.optDouble("thermal_cooling",1);ThermalPolicy.validateCooling(cooling);
        boolean response=options.optBoolean("response_override",false);long bright=options.optLong("brighten_delay",1500),dark=options.optLong("darken_delay",5000);DelayPolicy.validate(bright,dark);
        boolean small=options.optBoolean("small_brighten_override",false);long smallMs=options.optLong("small_brighten_delay",5000);DelayPolicy.validateSmall(smallMs);
        if(response&&!state.optBoolean("response_supported"))throw new IOException("此系统的确认时间接口暂未兼容");
        if(small&&!state.optBoolean("small_response_supported"))throw new IOException("此系统的微小变亮接口暂未兼容");
        boolean lowLight=options.optBoolean("low_light_stability",false);
        float lowLimit=(float)options.optDouble("low_light_limit",50);long lowBright=options.optLong("low_light_brighten",3000),lowDark=options.optLong("low_light_darken",4000);LowLightPolicy.validate(lowLimit,lowBright,lowDark);
        if(lowLight&&!state.optBoolean("low_light_supported"))throw new IOException("暗光稳定接口尚未完整兼容");
        LowLightThresholds lowThresholds=LowLightThresholds.parse(options);lowThresholds.verify(lowLight,state.optBoolean("low_light_threshold_supported"));
        LowLightAssistGate assistGate=new LowLightAssistGate();assistGate.configure(options);if(lowLight&&assistGate.enabled&&!state.optBoolean("low_light_assist_gate_supported"))throw new IOException("辅助光感暗光闸门接口暂未兼容");
        if(thermal&&!state.optBoolean("thermal_supported"))throw new IOException("此固件温控亮度接口尚未兼容，不能启用该选项");
        float[] f=CurvePlan.factors(factors);
        float floor=CurvePlan.floor(options.has("curve_floor_nit")?options.opt("curve_floor_nit"):null);
        if(f[3]!=1f)throw new IOException("高照度端暂保持官方上限，请保持第四个参数为 100%");
        new CurvePlan(numbers(state.getJSONArray("factory_lux")),numbers(state.getJSONArray("factory_logical_nit")),
            (float)state.getDouble("min_logical_nit"),(float)state.getDouble("max_logical_nit"),f,floor);
        String previous=settings.get(CONFIG);
        boolean wasActive=state.optString("phase").equals("active");
        String revision=UUID.randomUUID().toString();
        JSONObject config=new JSONObject().put("schema",1).put("enabled",true).put("revision",revision)
            .put("fingerprint",Build.FINGERPRINT).put("user_serial",0).put("factors",CurvePlan.encode(f)).put("thermal_relax",thermal).put("thermal_ceiling",ceiling).put("memory_strength",memory);
        config.put("curve_backend",state.getString("curve_backend")).put("baseline_id",state.getString("baseline_id"));
        config.put("memory_window",memoryMs).put("memory_lux_range",memoryRange).put("thermal_cooling",cooling).put("response_override",response).put("brighten_delay",bright).put("darken_delay",dark);
        memoryOptions.put(config);
        config.put("small_brighten_override",small).put("small_brighten_delay",smallMs);
        config.put("low_light_stability",lowLight).put("low_light_limit",lowLimit).put("low_light_brighten",lowBright).put("low_light_darken",lowDark);
        lowThresholds.put(config);
        assistGate.put(config);
        advanced.put(config);outdoor.put(config);controls.put(config);config.put("curve_floor_nit",floor);
        try {
            prepare();
            progress("提交小米基础曲线并等待系统确认…");
            if(!settings.put(CONFIG,config.toString()))throw new IOException("配置提交失败");
            if(!waitFor(revision,true))throw new IOException("系统未在 6 秒内确认本次配置。\n"+confirmationDetail());
            return new JSONObject().put("ok",true).put("message","已接入小米曲线。系统手动保持仍可能影响当前输出。");
        }catch(Exception failure){
            String rollbackMessage="未能确认恢复此前配置，请导出分析包；不要连续点击保存。";
            try{
                if(!settings.put(CONFIG,previous))throw new IOException("恢复请求写入失败");
                String previousRevision=previous==null?"":new JSONObject(previous).optString("revision","");
                if(waitFor(previousRevision,wasActive)){
                    rollbackMessage="已恢复此前配置；你在界面中的修改仍保留，尚未保存。";
                    if(!wasActive)resumeOld();
                }
            }catch(Exception rollback){failure.addSuppressed(rollback);}
            String detail=failure.getMessage()+"\n\n"+rollbackMessage;
            try{Files.write(new File(DATA,"last-control-error.txt").toPath(),(new Date()+"\n"+detail).getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}
            throw new IOException(detail,failure);
        }
    }
    JSONObject stop()throws Exception {
        NativePanelRoot.stop();boolean connected=live()!=null;
        if(!connected){String marker=settings.get("hyperlux_brightness_owner_v1");if(marker!=null){JSONObject ownership=new JSONObject(marker);
            if("dark".equals(ownership.optString("owner"))&&ownership.optInt("user",-1)==0&&ownership.optBoolean("was_auto")&&"0".equals(settings.getSystem("screen_brightness_mode")))
                if(!settings.putSystem("screen_brightness_mode","1"))throw new IOException("未能恢复暗光锁定前的自动亮度");
            settings.put("hyperlux_brightness_owner_v1",null);
        }}
        String revision=UUID.randomUUID().toString();
        JSONObject disabled=new JSONObject().put("schema",1).put("enabled",false).put("revision",revision);
        if(!settings.put(CONFIG,disabled.toString()))throw new IOException("关闭请求保存失败");
        if(connected && !waitFor(revision,false))throw new IOException("关闭请求已保存，但系统尚未确认恢复；请重启，暂不恢复旧引擎");
        if(connected)resumeOld();
        return new JSONObject().put("ok",true).put("message",connected?"已恢复官方基础曲线，并恢复由本应用暂停的旧模块":"关闭请求已保存；请重启后再点停用，确认恢复并解除旧模块暂停");
    }
    String export()throws Exception {
        String name="LumaCurve-analysis-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date())+"-"+UUID.randomUUID().toString().substring(0,6)+".zip";
        File file=new File("/sdcard",name),partial=new File("/sdcard",name+".partial");
        try {
            try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(partial))) {
                zip.setLevel(Deflater.BEST_SPEED);
                progress("1/8 收集曲线、运行状态与本次系统进程日志…");
                JSONObject state=inspect();write(zip,"state.json",state.toString(2));
                JSONObject runtime=state.optJSONObject("runtime");JSONArray logs=runtime==null?null:runtime.optJSONArray("logs");StringBuilder text=new StringBuilder();
                if(logs!=null)for(int i=0;i<logs.length();i++)text.append(logs.getString(i)).append('\n');write(zip,"logs.txt",text.toString());
                if(runtime!=null){
                    JSONArray pipeline=runtime.optJSONArray("pipeline_trace"),output=runtime.optJSONArray("output_trace");
                    JSONObject outdoorState=runtime.optJSONObject("outdoor");write(zip,"outdoor-state.json",outdoorState==null?"{}":outdoorState.toString(2));write(zip,"outdoor-limit-trace.json",outdoorState==null?"[]":outdoorState.optJSONArray("limit_trace")==null?"[]":outdoorState.getJSONArray("limit_trace").toString(2));
                    try{write(zip,"main-panel-node.json",NativePanelRoot.status().toString(2));}catch(Exception unavailable){write(zip,"main-panel-node.json",new JSONObject().put("error",unavailable.toString()).toString());}write(zip,"brightness-control-state.json",runtime.optJSONObject("brightness_control")==null?"{}":runtime.getJSONObject("brightness_control").toString(2));write(zip,"brightness-control-owner.json",String.valueOf(settings.get("hyperlux_brightness_owner_v1")));write(zip,"brightness-control-ack.json",String.valueOf(settings.get("hyperlux_brightness_ack_v1")));
                    write(zip,"pipeline-trace.json",pipeline==null?"[]":pipeline.toString(2));write(zip,"output-trace.json",output==null?"[]":output.toString(2));
                    write(zip,"trace-readme.txt","Pipeline records group stages from ONE updateAutoBrightness call. Values are framework brightness coordinates (0..1), not nit or percent. Missing fields mean unobserved, not disabled.\nOutput records are separate later calls, not automatically attributed to a calculation. Use uptime_ms within this boot, or unix_ms for wall time. Sensor readings are OEM filtered readings, not raw samples.\nBounded history: latest 24 calculations and 16 output calls.\n");
                }
                write(zip,"config.json",String.valueOf(settings.get(CONFIG)));write(zip,"manual-memory.json",String.valueOf(settings.get(PersistentMemory.KEY)));write(zip,"stored-hook-status.json",String.valueOf(settings.get(STATUS)));write(zip,"configuration-ack.json",String.valueOf(settings.get(ACK)));File controlError=new File(DATA,"last-control-error.txt");if(controlError.isFile()&&controlError.length()<=16384)write(zip,"last-control-error.txt",new String(Files.readAllBytes(controlError.toPath()),StandardCharsets.UTF_8));write(zip,"injection.json",String.valueOf(settings.get("lumacurve_refactor_injection_v1")));for(String f:new String[]{"main-panel-permissions","main-panel.log","last-panel-error.txt"}){optionalFile(zip,f,new File(DATA,f),65536);}
                for(String f:new String[]{"hyperlux-main-panel-lease","hyperlux-main-panel-health"}){optionalFile(zip,f,new File("/data/system",f),1024);}
                write(zip,"build.txt",BUILD+"\n"+Build.FINGERPRINT+"\n");
                new DiagnosticCollector(zip,settings::getSystem).collect();
                progress("8/8 完成压缩并校验分析包…");
            }
            // Do not advertise a partial archive after cancellation, disk full or a write error.
            try(ZipFile check=new ZipFile(partial)){byte[] buffer=new byte[65536];Enumeration<? extends ZipEntry> entries=check.entries();while(entries.hasMoreElements()){ZipEntry entry=entries.nextElement();CRC32 crc=new CRC32();long size=0;try(InputStream in=check.getInputStream(entry)){int count;while((count=in.read(buffer))!=-1){crc.update(buffer,0,count);size+=count;}}if(crc.getValue()!=entry.getCrc()||size!=entry.getSize())throw new IOException("分析包校验失败："+entry.getName());}}
            Files.move(partial.toPath(),file.toPath());return file.toString();
        }catch(Exception failure){partial.delete();throw failure;}
    }
    static void optionalFile(ZipOutputStream zip,String name,File source,long limit)throws IOException{
        String content;
        try{if(!source.isFile()||source.length()>limit)return;content=new String(Files.readAllBytes(source.toPath()),StandardCharsets.UTF_8);}
        catch(IOException disappeared){content="Unavailable during capture: "+disappeared;}
        write(zip,name,content);
    }
    static void write(ZipOutputStream zip,String name,String data)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(data.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
    static float[] numbers(JSONArray array)throws JSONException {float[] out=new float[array.length()];for(int i=0;i<out.length;i++)out[i]=(float)array.getDouble(i);return out;}
    JSONObject legacyPreferences()throws Exception {
        JSONObject result=new JSONObject().put("ok",true);
        File file=new File("/data/user/0/top.rongshangs.lumacurve.refactor.test/shared_prefs/top.rongshangs.lumacurve.refactor.MainActivity.xml");
        if(!file.isFile())return result;
        if(file.length()>65536)throw new IOException("旧预设文件过大");
        org.xmlpull.v1.XmlPullParser parser=android.util.Xml.newPullParser();
        try(InputStream input=new FileInputStream(file)){
            parser.setInput(input,"UTF-8");int type;
            while((type=parser.next())!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT){
                if(type==org.xmlpull.v1.XmlPullParser.START_TAG&&"string".equals(parser.getName())){
                    String name=parser.getAttributeValue(null,"name");
                    if("draft".equals(name)){String value=parser.nextText();CurvePlan.factors(value);result.put(name,value);}
                    else if("presets".equals(name)){String value=parser.nextText();new JSONObject(value);result.put(name,value);}
                }
            }
        }
        return result;
    }
    JSONObject brightness(String payload)throws Exception {
        JSONObject state=live();if(state==null)throw new IOException("尚未连接系统亮度接口，请检查 LSPosed 并重启");
        if(payload.length()>2048)throw new IOException("亮度请求过长");JSONObject request=new JSONObject(new String(Base64.getDecoder().decode(payload),StandardCharsets.UTF_8));
        String id=UUID.randomUUID().toString();request.put("id",id).put("build",BUILD).put("process_start",state.getString("process_start")).put("elapsed",android.os.SystemClock.elapsedRealtime());
        if(!settings.put("hyperlux_brightness_request_v1",request.toString()))throw new IOException("亮度请求提交失败");
        for(int i=0;i<120;i++){String raw=settings.get("hyperlux_brightness_ack_v1");if(raw!=null){JSONObject ack=new JSONObject(raw);if(id.equals(ack.optString("id")))return ack;}Thread.sleep(25);}
        throw new IOException("系统尚未确认亮度请求，请重新打开面板");
    }
    JSONObject rawPanel(String payload)throws Exception{
        String step="读取主屏状态";
        try{
        JSONObject request=new JSONObject(new String(Base64.getDecoder().decode(payload),StandardCharsets.UTF_8));
        String action=request.getString("action");
        if(action.equals("status"))return new JSONObject().put("ok",true).put("node",NativePanelRoot.status()).put("runtime",refreshedLive());
        String token=request.getString("session");if(!token.matches("[A-Za-z0-9_-]{1,80}"))throw new IOException("面板会话无效");
        if(action.equals("restore")){
            step="停止守护并释放节点";NativePanelRoot.stop();
            if(((Number)Class.forName("android.app.ActivityManager").getMethod("getCurrentUser").invoke(null)).intValue()!=0)throw new IOException("请切回主用户");
            request.put("action","raw_restore");try{brightness(Base64.getEncoder().encodeToString(request.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception unavailable){}
            if(!settings.putSystem("screen_brightness_mode","1"))throw new IOException("系统未确认自动亮度模式");
            return new JSONObject().put("ok",true).put("node",NativePanelRoot.status()).put("runtime",refreshedLive());
        }
        int value=request.getInt("value");if(request.getDouble("value")!=value)throw new IOException("请输入整数节点值");
        JSONObject node=NativePanelRoot.status();if(!node.optBoolean("supported"))throw new IOException("主屏节点尚不可用："+node.optString("reason","unknown"));if(value<10||value>node.getInt("maximum"))throw new IOException("主屏节点值超出范围");
        if(action.equals("begin")){step="启动主屏原生守护";NativePanelRoot.start(node);}else if(!action.equals("set"))throw new IOException("未知节点操作");
        request.put("action",action.equals("begin")?"raw_begin":"raw_validate");
        step=action.equals("begin")?"关闭自动亮度并取得系统许可":"确认当前接管会话";
        JSONObject ack;
        try{ack=brightness(Base64.getEncoder().encodeToString(request.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(Exception unconfirmed){
            // A lost ACK can follow a successful mode switch. Roll back only this
            // session; a stale request must never stop a newer owner's writer.
            request.put("action","raw_abort");try{brightness(Base64.getEncoder().encodeToString(request.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception ignored){}
            try{NativePanelClient.require("STOP "+token);}catch(Exception ignored){}throw unconfirmed;
        }
        if(!ack.optBoolean("ok"))throw new IOException(ack.optString("message","系统未确认接管"));
        try{
            if(!"0".equals(settings.getSystem("screen_brightness_mode")))throw new IOException("系统自动亮度仍开启，未写节点");
            step="传递节点接管许可";
            if(action.equals("begin")){
                NativePanelClient.require("ARM "+token+" "+live().getInt("pid"));
                request.put("action","raw_ready");JSONObject ready=brightness(Base64.getEncoder().encodeToString(request.toString().getBytes(StandardCharsets.UTF_8)));
                if(!ready.optBoolean("ok"))throw new IOException(ready.optString("message","系统未确认输出隔离"));ack=ready;
            }
            step="写入主屏节点并读回";
            JSONObject result=NativePanelClient.call("SET "+token+" "+value);
            if(!result.optBoolean("ok"))throw new IOException("主屏节点写入失败："+result.optString("reason")+" errno="+result.optInt("errno"));
            JSONObject snapshot=live();if(snapshot!=null&&ack.optJSONObject("state")!=null){JSONObject control=ack.getJSONObject("state");snapshot.put("brightness_control",control).put("auto_mode",control.optBoolean("auto"));}
            return new JSONObject().put("ok",true).put("node",NativePanelRoot.status()).put("runtime",snapshot);
        }catch(Exception failure){
            request.put("action","raw_abort");try{brightness(Base64.getEncoder().encodeToString(request.toString().getBytes(StandardCharsets.UTF_8)));}catch(Exception ignored){}
            try{NativePanelClient.require("STOP "+token);}catch(Exception ignored){}throw failure;
        }
        }catch(Exception failure){
            String detail="主屏操作失败（"+step+"）："+failure.getMessage();
            try{Files.write(new File(DATA,"last-panel-error.txt").toPath(),(new Date()+"\n"+detail+"\n"+failure).getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}
            throw new IOException(detail,failure);
        }
    }
    JSONObject execute(String command,String payload)throws Exception{
        if(command.equals("inspect"))return inspect();
        if(command.equals("raw-panel")&&payload!=null)return rawPanel(payload);
        if(command.equals("brightness")&&payload!=null)return brightness(payload);
        if(command.equals("export-config")&&payload!=null){byte[] raw=Base64.getDecoder().decode(payload);if(raw.length>ConfigurationFile.LIMIT)throw new IOException("配置文件过大");JSONObject document=new JSONObject(new String(raw,StandardCharsets.UTF_8));if(!ConfigurationFile.FORMAT.equals(document.optString("format"))||document.getInt("schema")!=2||!document.has("options"))throw new IOException("不是兼容的 HyperLux 配置文件");byte[] bytes=document.toString(2).getBytes(StandardCharsets.UTF_8);if(bytes.length>ConfigurationFile.LIMIT)throw new IOException("配置文件过大");File file=new File("/sdcard","HyperLux-config-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date())+"-"+UUID.randomUUID().toString().substring(0,8)+".json");Files.createFile(file.toPath());try(FileOutputStream output=new FileOutputStream(file)){output.write(bytes);output.getFD().sync();}catch(Exception failure){file.delete();throw failure;}return new JSONObject().put("ok",true).put("path",file.getAbsolutePath());}
        if(command.equals("legacy-preferences"))return legacyPreferences();
        if(command.equals("export"))return new JSONObject().put("ok",true).put("path",export());
        try(FileChannel lock=FileChannel.open(new File(DATA,"control.lock").toPath(),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock held=lock.tryLock()){
            if(held==null)throw new IOException("另一项操作还在执行");
            if(command.equals("apply")&&payload!=null)return apply(payload);
            if(command.equals("stop"))return stop();
            if(command.equals("reset-memory")){
                JSONObject state=live();if(state==null||!state.has("factory_lux"))throw new IOException("尚未连接兼容的系统曲线");
                String raw=settings.get(CONFIG);JSONObject config=raw==null?new JSONObject().put("schema",1).put("enabled",false):new JSONObject(raw);
                String revision=UUID.randomUUID().toString();config.put("revision",revision).put("reset_anchors",UUID.randomUUID().toString());
                if(!settings.put(CONFIG,config.toString())||!waitFor(revision,config.optBoolean("enabled")))throw new IOException("系统尚未确认锚点清除，请重新读取状态");
                return new JSONObject().put("ok",true).put("message","已清除当前与已保存的手动记忆；系统保存的最近滑块位置保持原样");
            }
            throw new IOException("未知操作");
        }
    }
    public static void main(String[] args) {
        try {
            if(android.os.Process.myUid()!=0)throw new IOException("需要 Root 权限");
            if(!DATA.isDirectory()&&!DATA.mkdirs())throw new IOException("状态目录创建失败");
            try(RootControl ctl=new RootControl()) {
                String command=args.length==0?"inspect":args[0];
                if(command.equals("serve")){
                    try(BufferedReader input=new BufferedReader(new InputStreamReader(System.in,StandardCharsets.UTF_8))){
                        String line;while((line=input.readLine())!=null){
                            try{
                                if(line.length()>65536)throw new IOException("请求过长");JSONObject request=new JSONObject(line);if(!"export-config".equals(request.optString("command"))&&line.length()>4096)throw new IOException("请求过长");
                                System.out.println("LUMA_RESULT="+ctl.execute(request.getString("command"),request.optString("payload",null)));
                            }catch(Throwable error){System.out.println("LUMA_RESULT="+new JSONObject().put("ok",false).put("message",error.toString()));}
                            System.out.flush();
                        }
                    }
                }else System.out.println("LUMA_RESULT="+ctl.execute(command,args.length==2?args[1]:null));
            }
        }catch(Throwable error){
            try{System.out.println("LUMA_RESULT="+new JSONObject().put("ok",false).put("message",error.toString()));}catch(JSONException ignored){}
            System.exit(1);
        }
    }
}
