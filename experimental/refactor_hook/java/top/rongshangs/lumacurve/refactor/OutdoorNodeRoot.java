package top.rongshangs.lumacurve.refactor;

import java.io.*;
import java.nio.file.*;
import android.system.Os;
import org.json.*;

/** Optional root watcher. Installed/started only after an explicit saved opt-in. */
final class OutdoorNodeRoot {
    static final File HEALTH=new File(RootControl.DATA,"outdoor-node-health.json");
    static boolean supported(JSONObject state)throws Exception{
        if(state==null||!state.optBoolean("outdoor_node_supported"))return false;
        JSONObject outdoor=state.optJSONObject("outdoor");if(outdoor==null||outdoor.optDouble("device_mapping_max",-1)!=1)return false;
        String profile=outdoor.optString("sdr_compatibility_profile");if(!profile.equals("privacy_aware")&&!profile.equals("legacy_opr"))return false;
        JSONObject node=PanelNodeDiscovery.discover(new File("/sys"));
        if(!node.optBoolean("supported")||!OutdoorNodeProfile.matches(state.optString("curve_backend"),state.optDouble("max_logical_nit"),node.optInt("maximum"),state.optBoolean("manual_panel_supported"),state.optBoolean("outdoor_range_supported")))return false;
        NativePanelRoot.probe(node);return true;
    }
    static JSONObject health(){try{byte[] raw=Files.readAllBytes(HEALTH.toPath());if(raw.length>2048)return new JSONObject();return new JSONObject(new String(raw,java.nio.charset.StandardCharsets.UTF_8));}catch(Exception missing){return new JSONObject();}}
    static int running(){try{int pid=health().optInt("pid",-1);if(pid<=0)return -1;String cmd=new String(Files.readAllBytes(Paths.get("/proc/"+pid+"/cmdline")),java.nio.charset.StandardCharsets.UTF_8);
        String prefix=NativePanelRoot.BIN+"\0--outdoor\0--node\0";if(!cmd.startsWith(prefix)||!cmd.endsWith("\0"))return -1;String path=cmd.substring(prefix.length(),cmd.length()-1);return path.startsWith("/sys/class/")&&path.indexOf(0)<0?pid:-1;
    }catch(Exception missing){return -1;}}
    static void start(JSONObject state)throws Exception{
        if(!supported(state))throw new IOException("此固件的节点高亮路径尚未就绪");if(running()>0)return;
        NativePanelRoot.install();JSONObject selected=PanelNodeDiscovery.discover(new File("/sys"));NativePanelRoot.probe(selected);Files.deleteIfExists(HEALTH.toPath());
        JSONObject node=PanelNodeDiscovery.discover(new File("/sys"));
        Process guard=new ProcessBuilder(NativePanelRoot.BIN.toString(),"--outdoor","--node",node.getString("path")).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(new File(RootControl.DATA,"outdoor-node.log"))).start();
        for(int i=0;i<30;i++){if(running()>0)return;if(!guard.isAlive())throw new IOException("节点高亮守护未能启动："+guard.exitValue());Thread.sleep(50);}
        guard.destroy();throw new IOException("节点高亮守护启动未确认");
    }
    static void stop()throws Exception{int pid=running();if(pid>0){Os.kill(pid,15);for(int i=0;i<30&&running()==pid;i++)Thread.sleep(50);if(running()==pid)throw new IOException("节点高亮守护尚未退出，请稍后重试");}}
    static void reconcile(JSONObject state,String raw)throws Exception{
        if(raw!=null&&state!=null){JSONObject config=new JSONObject(raw);if(config.optBoolean("enabled")&&config.optBoolean("outdoor_node_boost")&&state.optJSONObject("outdoor")!=null&&state.getJSONObject("outdoor").optJSONObject("options")!=null&&state.getJSONObject("outdoor").getJSONObject("options").optBoolean("outdoor_node_boost")&&"active".equals(state.optString("phase"))&&config.optString("revision").equals(state.optString("revision"))){
            String revision=config.optString("revision");JSONObject previous=health();
            if(revision.equals(previous.optString("failure_revision")))return;
            String reason=previous.optString("reason");
            if(running()<0&&(reason.equals("write_failed")||reason.equals("node_changed"))){previous.put("failure_revision",revision);Files.write(HEALTH.toPath(),previous.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));return;}
            try{start(state);}catch(Exception failure){JSONObject failed=health();failed.put("failure_revision",revision).put("reason","start_failed").put("detail",failure.toString());Files.write(HEALTH.toPath(),failed.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));throw failure;}return;}}

        stop();
    }
    static void annotate(JSONObject state){if(state==null)return;state.remove("outdoor_node_maximum");try{boolean available=supported(state);state.put("outdoor_node_supported",available);if(available){JSONObject node=PanelNodeDiscovery.discover(new File("/sys"));if(!node.optBoolean("supported"))throw new IOException("主屏节点在读取上限时发生变化");state.put("outdoor_node_maximum",node.getInt("maximum"));}JSONObject status=health();status.put("running",running()>0);state.put("outdoor_node_guard",status);}catch(Exception unavailable){try{state.put("outdoor_node_supported",false).put("outdoor_node_detail",unavailable.toString());}catch(JSONException ignored){}}}
}
