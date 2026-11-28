package top.rongshangs.lumacurve.refactor;

import java.io.*;
import java.nio.file.*;
import android.system.Os;
import org.json.*;

/** Optional root watcher. Installed/started only after an explicit saved opt-in. */
final class OutdoorNodeRoot {
    static File healthFile(){return healthFile(RootWorkspace.directory());}
    private static File healthFile(File dir){return new File(dir,"outdoor-node-health.json");}
    static JSONObject discover()throws Exception{return PanelNodeDiscovery.directAccess(ActivePanelNode.discover());}
    static String foldIds(JSONObject node)throws Exception{
        if(!ActivePanelNode.PROFILE.equals(node.optString("discovery")))return "";
        JSONArray ids=node.getJSONArray("fold_physical_ids");if(ids.length()<2||ids.length()>8)throw new IOException("折叠物理屏清单不完整");
        java.util.SortedSet<Long> sorted=new java.util.TreeSet<>();for(int i=0;i<ids.length();i++){long id=ids.getLong(i);if(id<=0||!sorted.add(id))throw new IOException("折叠物理屏身份冲突");}
        if(!sorted.contains(Long.parseLong(node.getString("physical_display_id").substring(6))))throw new IOException("当前物理屏不在折叠布局中");
        StringBuilder text=new StringBuilder();for(long id:sorted){if(text.length()>0)text.append(',');text.append(id);}return text.toString();
    }
    static boolean accepts(JSONObject state,JSONObject node)throws Exception{
        if(!node.optBoolean("supported")||!node.optString("direct_write_block").isEmpty()||!OutdoorNodeProfile.matches(state.optString("curve_backend"),state.optDouble("max_logical_nit"),node.optInt("maximum"),state.optBoolean("manual_panel_supported"),state.optBoolean("outdoor_range_supported")))return false;
        if(!foldIds(node).isEmpty()&&state.optLong("outdoor_node_physical_id",0)!=Long.parseLong(node.getString("physical_display_id").substring(6)))return false;
        return true;
    }
    static java.util.List<String> command(JSONObject node,File dir)throws Exception{
        java.util.List<String> args=new java.util.ArrayList<>(java.util.Arrays.asList(NativePanelRoot.bin(dir).toString(),"--outdoor","--node",node.getString("path"),NativePanelRoot.WORKSPACE+dir.getAbsolutePath()));String ids=foldIds(node);if(!ids.isEmpty())args.add("--fold-ids="+ids);return args;
    }
    static boolean supported(JSONObject state)throws Exception{return state!=null&&state.optBoolean("outdoor_node_supported")&&supported(state,discover());}
    private static boolean supported(JSONObject state,JSONObject node)throws Exception{
        if(state==null||!state.optBoolean("outdoor_node_supported"))return false;
        JSONObject outdoor=state.optJSONObject("outdoor");if(outdoor==null||outdoor.optDouble("device_mapping_max",-1)!=1)return false;
        String profile=outdoor.optString("sdr_compatibility_profile");if(!profile.equals("privacy_aware")&&!profile.equals("legacy_opr"))return false;
        if(!accepts(state,node))return false;
        NativePanelRoot.probe(node);return true;
    }
    static boolean requested(JSONObject state){
        JSONObject outdoor=state==null?null:state.optJSONObject("outdoor");
        JSONObject options=outdoor==null?null:outdoor.optJSONObject("options");
        return options!=null&&options.optBoolean("outdoor_node_boost");
    }
    static JSONObject testPauseBinding(JSONObject state)throws Exception{
        if(!requested(state))return null;
        long physical=state.optLong("outdoor_node_physical_id",0);
        String revision=state.optString("revision");int user=state.optInt("user_serial",-1);
        if(physical<=0||revision.isEmpty()||user<0||!"active".equals(state.optString("phase")))
            throw new IOException("系统亮度状态尚未就绪，请刷新后重试");
        JSONObject node=discover();
        if(node.optBoolean("supported")&&ActivePanelNode.PROFILE.equals(node.optString("discovery"))
                &&!("local:"+physical).equals(node.optString("physical_display_id")))
            throw new IOException("当前屏幕已切换，请刷新后重试");
        if(supported(state,node))return null;
        return new JSONObject().put("physical",physical).put("revision",revision).put("user_serial",user);
    }
    static JSONObject health(){return health(RootWorkspace.directory());}
    static JSONObject health(File dir){
        try{
            RootWorkspace.secure(dir,false);
            Path path=healthFile(dir).toPath();
            if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))return new JSONObject();
            try(InputStream input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){
                byte[] raw=new byte[2049];int size=0;
                while(size<raw.length){int count=input.read(raw,size,raw.length-size);if(count<0)break;size+=count;}
                if(size<=2048)return new JSONObject(new String(raw,0,size,java.nio.charset.StandardCharsets.UTF_8));
            }
        }catch(Exception missing){/* A missing or unverified health record gives no process identity. */}
        return new JSONObject();
    }
    private static void saveHealth(JSONObject status)throws Exception{
        File dir=RootWorkspace.directory();
        RootWorkspace.secure(dir,false);
        Files.write(healthFile(dir).toPath(),status.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
    }
    static int running(){return running(RootWorkspace.directory());}
    /** Only this workspace's verified binary, node route and optional workspace argument. */
    static int running(File dir){try{int pid=health(dir).optInt("pid",-1);if(pid<=0)return -1;String cmd=new String(Files.readAllBytes(Paths.get("/proc/"+pid+"/cmdline")),java.nio.charset.StandardCharsets.UTF_8);
        String prefix=NativePanelRoot.bin(dir)+"\0--outdoor\0--node\0";if(!cmd.startsWith(prefix)||!cmd.endsWith("\0"))return -1;String[] args=cmd.substring(prefix.length(),cmd.length()-1).split("\0",-1);if(args.length<1||args.length>3||!args[0].startsWith("/sys/class/"))return -1;
        int index=1;
        // A pre-workspace guard has no --workspace argument and belongs to its old directory.
        if(index<args.length&&args[index].startsWith(NativePanelRoot.WORKSPACE)){if(!args[index].equals(NativePanelRoot.WORKSPACE+dir.getAbsolutePath()))return -1;index++;}
        if(index<args.length&&!args[index].matches("--fold-ids=[0-9]{1,19}(,[0-9]{1,19}){1,7}"))return -1;
        if(index+1<args.length)return -1;return pid;
    }catch(Exception missing){return -1;}}
    static void start(JSONObject state)throws Exception{
        JSONObject selected=discover();if(!supported(state,selected))throw new IOException("主屏节点高亮路径尚未就绪");String ids=foldIds(selected);
        File dir=NativePanelRoot.install();
        boolean reuse=false;
        for(File candidate:RootWorkspace.candidates()){
            int pid=running(candidate);if(pid<=0)continue;
            if(candidate.equals(dir)){
                JSONObject current=health(candidate);
                reuse=current.optInt("pid",-1)==pid&&current.optBoolean("fold_route")==!ids.isEmpty()&&current.optString("fold_ids").equals(ids);
                if(reuse)continue;
            }
            stopProcess(pid);
        }
        if(reuse)return;
        JSONObject node=discover();
        if(!accepts(state,node)||!selected.getString("canonical_path").equals(node.getString("canonical_path"))||selected.getInt("maximum")!=node.getInt("maximum")||!ids.equals(foldIds(node)))throw new IOException("启动时折叠主屏发生变化");
        NativePanelRoot.probe(node);
        Files.deleteIfExists(healthFile(dir).toPath());
        Process guard=new ProcessBuilder(command(node,dir)).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(new File(dir,"outdoor-node.log"))).start();
        for(int i=0;i<30;i++){if(running(dir)>0)return;if(!guard.isAlive())throw new IOException("节点高亮守护未能启动："+guard.exitValue());Thread.sleep(50);}
        guard.destroy();throw new IOException("节点高亮守护启动未确认");
    }
    /** A selection flip can leave the watcher in the other verified directory. */
    static int anyRunning(){for(File candidate:RootWorkspace.candidates()){int pid=running(candidate);if(pid>0)return pid;}return -1;}
    static void stop()throws Exception{for(File candidate:RootWorkspace.candidates()){int pid=running(candidate);if(pid>0)stopProcess(pid);}}
    private static void stopProcess(int pid)throws Exception{Os.kill(pid,15);for(int i=0;i<30&&alive(pid);i++)Thread.sleep(50);if(alive(pid))throw new IOException("节点高亮守护尚未退出，请稍后重试");}
    private static boolean alive(int pid){try{Os.kill(pid,0);return true;}catch(Exception gone){return false;}}
    static void reconcile(JSONObject state,String raw)throws Exception{
        reconcile(state,raw,false);
    }
    static void reconcileAfterApply(JSONObject state,String raw)throws Exception{
        reconcile(state,raw,true);
    }
    private static void reconcile(JSONObject state,String raw,boolean explicitApply)throws Exception{
        JSONObject test=state==null?null:state.optJSONObject("outdoor_light_test");
        if(test!=null&&(test.optBoolean("preparing")||test.optBoolean("active"))){stop();return;}
        if(raw!=null&&state!=null){JSONObject config=new JSONObject(raw);if(config.optBoolean("enabled")&&config.optBoolean("outdoor_node_boost")&&requested(state)&&"active".equals(state.optString("phase"))&&config.optString("revision").equals(state.optString("revision"))){
            if(!discover().optString("direct_write_block").isEmpty()){stop();return;}
            String revision=config.optString("revision");JSONObject previous=health();
            if(!explicitApply&&revision.equals(previous.optString("failure_revision")))return;
            String reason=previous.optString("reason");
            if(!explicitApply&&anyRunning()<0&&(reason.equals("write_failed")||reason.equals("node_changed"))){saveHealth(previous.put("failure_revision",revision));return;}
            try{start(state);}catch(Exception failure){
                try{saveHealth(health().put("failure_revision",revision).put("reason","start_failed").put("detail",failure.toString()));}
                catch(Exception unavailable){failure.addSuppressed(unavailable);}
                throw failure;
            }return;}}

        stop();
    }
    static void annotate(JSONObject state){if(state==null)return;state.remove("outdoor_node_maximum");try{
        boolean requested=requested(state);
        state.put("outdoor_test_node_conflict",requested);
        JSONObject node=state.optBoolean("outdoor_node_supported")?discover():new JSONObject();boolean available=supported(state,node);
        if(available)state.put("outdoor_node_maximum",node.getInt("maximum")).put("outdoor_node_device_profile",foldIds(node).isEmpty()?"primary_node":"fold_bound_node");
        if(node.has("direct_write_block"))state.put("outdoor_node_detail","主屏节点直写已暂停，沿用系统亮度输出（"+node.getString("direct_write_block")+"）");
        state.put("outdoor_node_supported",available).put("outdoor_test_node_conflict",requested&&available);
        JSONObject status=health();status.put("running",anyRunning()>0);state.put("outdoor_node_guard",status);}catch(Exception unavailable){try{state.put("outdoor_node_supported",false).put("outdoor_node_detail",unavailable.toString());}catch(JSONException ignored){}}}
}
