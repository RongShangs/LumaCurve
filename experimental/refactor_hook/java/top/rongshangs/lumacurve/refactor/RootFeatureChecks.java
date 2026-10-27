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
        f.check("main_panel_node","主屏节点与打开权限",null,true,()->{JSONObject node=ActivePanelNode.discover();primary[0]=node;if(!node.optBoolean("supported"))return false;NativePanelRoot.probe(node);return true;});
        f.check("outdoor_node_device","节点高亮设备条件",null,true,()->OutdoorNodeRoot.supported(state));
        f.check("native_payload","主屏控制程序完整性",null,true,()->{String source=System.getenv("CLASSPATH");if(source==null||source.isEmpty())return false;NativePanelAsset.read(new File(source));return true;});
        JSONObject report=f.report(android.os.SystemClock.elapsedRealtime(),"root-start");JSONArray rows=report.getJSONArray("features");for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);if(row.optString("id").equals("root_settings"))row.put("settings_route",setting.optString("route")).put("settings_attempts",setting.getJSONArray("routes"));if(row.optString("id").equals("root_storage"))row.put("workspace_attempts",storage.optJSONArray("routes")).put("workspace_route",RootWorkspace.PRIMARY.toString().equals(storage.optString("selected"))?"root_private_storage":storage.optString("selected").isEmpty()?"":"root_temporary_storage");if(primary[0]!=null&&row.optString("id").equals("main_panel_node"))row.put("node_address_profile",primary[0].optString("address_profile")).put("node_port_source",primary[0].optString("port_source")).put("node_binding",primary[0].optString("node_binding")).put("node_physical_port",primary[0].optInt("physical_port",-1)).put("node_kernel_port",primary[0].optInt("fold_port",-1)).put("node_detail",primary[0].optString("detail")).put("node_discovery",primary[0].optString("discovery")).put("node_physical_display_id",primary[0].optString("physical_display_id")).put("node_class",primary[0].optString("class")).put("node_path",primary[0].optString("canonical_path")).put("node_maximum",primary[0].optInt("maximum"));}return report;
    }
}
