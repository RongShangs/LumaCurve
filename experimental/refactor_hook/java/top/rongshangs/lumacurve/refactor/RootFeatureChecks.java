package top.rongshangs.lumacurve.refactor;
import java.io.*;
import org.json.*;
/** Short-lived helper checks; never starts a writer, installs a payload or changes a node. */
final class RootFeatureChecks {
    static JSONObject run(JSONObject state,String raw)throws JSONException{
        FeatureSelfCheck f=new FeatureSelfCheck();
        f.check("root_storage","配置备份与分析包目录",null,true,()->RootControl.DATA.isDirectory()&&RootControl.DATA.canRead()&&RootControl.DATA.canWrite());
        f.check("stored_config","配置读取",null,false,()->{if(raw!=null)new JSONObject(raw);return true;});
        f.check("main_panel_node","主屏节点与打开权限",null,true,()->{JSONObject node=PanelNodeDiscovery.discover(new File("/sys"));if(!node.optBoolean("supported"))return false;NativePanelRoot.probe(node);return true;});
        f.check("outdoor_node_device","节点高亮设备条件",null,true,()->OutdoorNodeRoot.supported(state));
        f.check("native_payload","主屏控制程序完整性",null,true,()->{String source=System.getenv("CLASSPATH");if(source==null||source.isEmpty())return false;NativePanelAsset.read(new File(source));return true;});
        return f.report(android.os.SystemClock.elapsedRealtime(),"root-start");
    }
}
