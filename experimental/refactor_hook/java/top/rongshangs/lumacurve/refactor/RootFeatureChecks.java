package top.rongshangs.lumacurve.refactor;
import java.io.*;
import org.json.*;
/** Short-lived helper checks; never starts a writer, installs a payload or changes a node. */
final class RootFeatureChecks {
    static JSONObject run(JSONObject state,String raw,RootSettings settings)throws JSONException{
        FeatureSelfCheck f=new FeatureSelfCheck();final JSONObject[] primary={null};
        JSONObject access=RootAccess.report(settings),storage=access.getJSONObject("workspace"),setting=access.getJSONObject("settings");
        f.check("root_identity","Root 进程身份与限制",null,false,()->true);f.explain("root_identity","UID="+access.optInt("uid")+" · "+access.optString("selinux_context")+"\n"+access.optJSONObject("process_permissions"));
        f.check("root_settings","系统设置访问",null,true,settings::available);f.explain("root_settings",setting.optString("route")+" · "+(setting.optBoolean("write_confirmed")?"已由实际操作确认写入："+setting.optString("write_confirmed_key"):"写入权限待用户实际操作确认；自检不试写")+"\n"+setting.optString("last_error"));
        f.check("root_process_identity","系统进程身份核验",null,true,()->state!=null);f.explain("root_process_identity",state==null?"未取得可验证的当前系统进程状态；不跳过身份检查":"系统进程PID、启动标识、构建与指纹检查通过");
        f.check("root_storage","私有操作目录",null,true,()->storage.optBoolean("selected_usable"));f.explain("root_storage",storage.optBoolean("selected_usable")?storage.optString("selected"):(storage.optString("selected_error").isEmpty()?storage.optString("selection_error"):storage.optString("selected_error")));
        f.check("stored_config","配置读取",null,true,()->{if(!settings.available())return false;if(raw!=null)new JSONObject(raw);return true;});
        JSONObject boot=null;try{boot=RootMappingBoot.inspect(settings);}catch(Exception unavailable){}final JSONObject entry=boot;f.check("mapping_boot","Root开机映射恢复",null,true,()->entry!=null&&entry.optBoolean("installed"));f.explain("mapping_boot",entry==null?"启动入口尚未确认":entry.optString("detail"));
        f.check("main_panel_node","主屏节点与打开权限",null,true,()->{JSONObject node=PanelNodeDiscovery.directAccess(ActivePanelNode.discover());primary[0]=node;if(!node.optBoolean("supported")||node.has("direct_write_block"))return false;NativePanelRoot.probe(node);return true;});
        f.check("outdoor_node_device","节点高亮设备条件",null,true,()->OutdoorNodeRoot.supported(state));
        f.check("native_payload","主屏控制程序完整性",null,true,()->{String source=System.getenv("CLASSPATH");if(source==null||source.isEmpty())return false;NativePanelAsset.read(new File(source));return true;});
        JSONObject[] systemUi={null};
        f.check("control_center_system_ui","SystemUI 亮度条接口",null,true,()->{
            systemUi[0]=SystemUiAccess.coordinateCheck(settings);
            return systemUi[0].optString("state").equals("available");
        });
        JSONObject report=f.report(android.os.SystemClock.elapsedRealtime(),"root-start");
        JSONArray rows=report.getJSONArray("features");
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);
            switch(row.optString("id")){
                case "mapping_boot":
                    if(entry!=null)row.put("startup",entry);
                    break;
                case "root_settings":
                    row.put("settings_route",setting.optString("route")).put("settings_attempts",setting.getJSONArray("routes"));
                    break;
                case "root_storage":
                    String selected=storage.optString("selected");
                    row.put("workspace_attempts",storage.optJSONArray("routes"))
                        .put("workspace_route",RootWorkspace.PRIMARY.toString().equals(selected)?"root_private_storage":selected.isEmpty()?"":"root_temporary_storage");
                    break;
                case "main_panel_node":
                    JSONObject node=primary[0];
                    if(node!=null)row.put("node_address_profile",node.optString("address_profile"))
                        .put("node_port_source",node.optString("port_source")).put("node_binding",node.optString("node_binding"))
                        .put("node_physical_port",node.optInt("physical_port",-1)).put("node_kernel_port",node.optInt("fold_port",-1))
                        .put("node_detail",node.optString("direct_write_block",node.optString("detail")))
                        .put("node_discovery",node.optString("discovery")).put("node_physical_display_id",node.optString("physical_display_id"))
                        .put("node_class",node.optString("class")).put("node_path",node.optString("canonical_path")).put("node_maximum",node.optInt("maximum"));
                    break;
                case "control_center_system_ui":
                    JSONObject coordinates=systemUi[0];
                    if(coordinates!=null)for(String key:new String[]{"detail","system_ui_route","system_ui_report_detail"})
                        if(coordinates.has(key))row.put(key,coordinates.get(key));
                    break;
            }
        }
        return report;
    }
}
