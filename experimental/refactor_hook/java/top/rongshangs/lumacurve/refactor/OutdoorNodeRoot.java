package top.rongshangs.lumacurve.refactor;

import java.io.*;
import java.nio.file.*;
import android.system.Os;
import org.json.*;

/** Optional root watcher. Installed/started only after an explicit saved opt-in. */
final class OutdoorNodeRoot {
    static final File HEALTH=new File(RootControl.DATA,"outdoor-node-health.json");
    static JSONObject discover()throws Exception{return ActivePanelNode.discover();}
    static String foldIds(JSONObject node)throws Exception{
        if(!ActivePanelNode.PROFILE.equals(node.optString("discovery")))return "";
        JSONArray ids=node.getJSONArray("fold_physical_ids");if(ids.length()<2||ids.length()>8)throw new IOException("折叠物理屏清单不完整");
        java.util.SortedSet<Long> sorted=new java.util.TreeSet<>();for(int i=0;i<ids.length();i++){long id=ids.getLong(i);if(id<=0||!sorted.add(id))throw new IOException("折叠物理屏身份冲突");}
        if(!sorted.contains(Long.parseLong(node.getString("physical_display_id").substring(6))))throw new IOException("当前物理屏不在折叠布局中");
        StringBuilder text=new StringBuilder();for(long id:sorted){if(text.length()>0)text.append(',');text.append(id);}return text.toString();
    }
    static boolean accepts(JSONObject state,JSONObject node)throws Exception{
        if(!node.optBoolean("supported")||!OutdoorNodeProfile.matches(state.optString("curve_backend"),state.optDouble("max_logical_nit"),node.optInt("maximum"),state.optBoolean("manual_panel_supported"),state.optBoolean("outdoor_range_supported")))return false;
        if(!foldIds(node).isEmpty()&&state.optLong("outdoor_node_physical_id",0)!=Long.parseLong(node.getString("physical_display_id").substring(6)))return false;
        return true;
    }
    static java.util.List<String> command(JSONObject node)throws Exception{
        java.util.List<String> args=new java.util.ArrayList<>(java.util.Arrays.asList(NativePanelRoot.BIN.toString(),"--outdoor","--node",node.getString("path")));String ids=foldIds(node);if(!ids.isEmpty())args.add("--fold-ids="+ids);return args;
    }
    static boolean supported(JSONObject state)throws Exception{
        if(state==null||!state.optBoolean("outdoor_node_supported"))return false;
        JSONObject outdoor=state.optJSONObject("outdoor");if(outdoor==null||outdoor.optDouble("device_mapping_max",-1)!=1)return false;
        String profile=outdoor.optString("sdr_compatibility_profile");if(!profile.equals("privacy_aware")&&!profile.equals("legacy_opr"))return false;
        JSONObject node=discover();if(!accepts(state,node))return false;
        NativePanelRoot.probe(node);return true;
    }
    static JSONObject health(){try{byte[] raw=Files.readAllBytes(HEALTH.toPath());if(raw.length>2048)return new JSONObject();return new JSONObject(new String(raw,java.nio.charset.StandardCharsets.UTF_8));}catch(Exception missing){return new JSONObject();}}
    static int running(){try{int pid=health().optInt("pid",-1);if(pid<=0)return -1;String cmd=new String(Files.readAllBytes(Paths.get("/proc/"+pid+"/cmdline")),java.nio.charset.StandardCharsets.UTF_8);
        String prefix=NativePanelRoot.BIN+"\0--outdoor\0--node\0";if(!cmd.startsWith(prefix)||!cmd.endsWith("\0"))return -1;String[] args=cmd.substring(prefix.length(),cmd.length()-1).split("\0",-1);if(args.length<1||args.length>2||!args[0].startsWith("/sys/class/"))return -1;
        if(args.length==2&&!args[1].matches("--fold-ids=[0-9]{1,19}(,[0-9]{1,19}){1,7}"))return -1;return pid;
    }catch(Exception missing){return -1;}}
    static void start(JSONObject state)throws Exception{
        if(!supported(state))throw new IOException("此固件的节点高亮路径尚未就绪");JSONObject selected=discover();String ids=foldIds(selected);
        if(running()>0){JSONObject current=health();if(current.optBoolean("fold_route")==!ids.isEmpty()&&current.optString("fold_ids").equals(ids))return;stop();}
        NativePanelRoot.install();NativePanelRoot.probe(selected);JSONObject node=discover();
        if(!accepts(state,node)||!selected.getString("canonical_path").equals(node.getString("canonical_path"))||selected.getInt("maximum")!=node.getInt("maximum")||!ids.equals(foldIds(node)))throw new IOException("启动时折叠主屏发生变化");
        Files.deleteIfExists(HEALTH.toPath());
        Process guard=new ProcessBuilder(command(node)).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(new File(RootControl.DATA,"outdoor-node.log"))).start();
        for(int i=0;i<30;i++){if(running()>0)return;if(!guard.isAlive())throw new IOException("节点高亮守护未能启动："+guard.exitValue());Thread.sleep(50);}
        guard.destroy();throw new IOException("节点高亮守护启动未确认");
    }
    static void stop()throws Exception{int pid=running();if(pid>0){Os.kill(pid,15);for(int i=0;i<30&&running()==pid;i++)Thread.sleep(50);if(running()==pid)throw new IOException("节点高亮守护尚未退出，请稍后重试");}}
    static void reconcile(JSONObject state,String raw)throws Exception{
        reconcile(state,raw,false);
    }
    static void reconcileAfterApply(JSONObject state,String raw)throws Exception{
        reconcile(state,raw,true);
    }
    private static void reconcile(JSONObject state,String raw,boolean explicitApply)throws Exception{
        if(raw!=null&&state!=null){JSONObject config=new JSONObject(raw);if(config.optBoolean("enabled")&&config.optBoolean("outdoor_node_boost")&&state.optJSONObject("outdoor")!=null&&state.getJSONObject("outdoor").optJSONObject("options")!=null&&state.getJSONObject("outdoor").getJSONObject("options").optBoolean("outdoor_node_boost")&&"active".equals(state.optString("phase"))&&config.optString("revision").equals(state.optString("revision"))){
            String revision=config.optString("revision");JSONObject previous=health();
            if(!explicitApply&&revision.equals(previous.optString("failure_revision")))return;
            String reason=previous.optString("reason");
            if(!explicitApply&&running()<0&&(reason.equals("write_failed")||reason.equals("node_changed"))){previous.put("failure_revision",revision);Files.write(HEALTH.toPath(),previous.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));return;}
            try{start(state);}catch(Exception failure){JSONObject failed=health();failed.put("failure_revision",revision).put("reason","start_failed").put("detail",failure.toString());Files.write(HEALTH.toPath(),failed.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));throw failure;}return;}}

        stop();
    }
    static void annotate(JSONObject state){if(state==null)return;state.remove("outdoor_node_maximum");try{boolean available=supported(state);if(available){JSONObject node=discover();if(!accepts(state,node))throw new IOException("主屏节点在读取上限时发生变化");state.put("outdoor_node_maximum",node.getInt("maximum")).put("outdoor_node_device_profile",foldIds(node).isEmpty()?"primary_node":"fold_bound_node");}state.put("outdoor_node_supported",available);JSONObject status=health();status.put("running",running()>0);state.put("outdoor_node_guard",status);}catch(Exception unavailable){try{state.put("outdoor_node_supported",false).put("outdoor_node_detail",unavailable.toString());}catch(JSONException ignored){}}}
}
