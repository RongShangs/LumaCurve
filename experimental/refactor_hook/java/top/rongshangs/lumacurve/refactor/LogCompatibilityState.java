package top.rongshangs.lumacurve.refactor;

import org.json.*;

/** Human-facing summary of existing evidence, never a second compatibility detector. */
final class LogCompatibilityState {
    static final class Item {final String name,status;final int tone;Item(String n,String s,int t){name=n;status=s;tone=t;}}
    final Item[] items;final String title,detail;final int available,pending,unsupported,inactive;
    LogCompatibilityState(Item[] items,String title,String detail,int[] counts){this.items=items;this.title=title;this.detail=detail;available=counts[0];pending=counts[1];unsupported=counts[2];inactive=counts[3];}
    static String text(boolean en,String zh,String english){return en?english:zh;}
    static JSONObject object(JSONObject parent,String key){JSONObject out=parent==null?null:parent.optJSONObject(key);return out==null?new JSONObject():out;}
    static void count(JSONObject report,int[] counts){JSONArray rows=report==null?null:report.optJSONArray("features");if(rows==null)return;for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;String state=row.optString("state");counts[state.equals("available")?0:state.equals("unsupported")?2:state.equals("inactive")?3:1]++;}}
    static LogCompatibilityState read(JSONObject answer,JSONObject cachedRoot,JSONObject appReport,boolean english){
        JSONObject access=object(answer,"root_access"),settings=object(access,"settings"),runtime=object(answer,"runtime");String issue=answer==null?"":answer.optString("hook_issue"),phase=runtime.optString("phase");boolean connected=answer!=null&&answer.optBoolean("connected"),loaded=connected||answer!=null&&answer.optBoolean("lsp_loaded"),limited=issue.equals("root_settings_unavailable")||issue.equals("root_process_visibility");
        int[] counts=new int[4];count(appReport,counts);count(runtime.optJSONObject("feature_self_check"),counts);JSONObject root=answer==null?null:answer.optJSONObject("root_feature_self_check");count(root==null?cachedRoot:root,counts);
        Item rootItem=new Item(text(english,"Root 访问","Root access"),answer==null?text(english,"等待读取","Waiting"):limited?text(english,"部分受限","Restricted"):settings.optBoolean("write_confirmed")?text(english,"读写已确认","Read/write verified"):settings.optBoolean("readable")?text(english,"读取可用","Read access"):text(english,"桥接可用","Bridge available"),answer==null?0:limited?2:1);
        Item injection=new Item(text(english,"系统注入","Injection"),loaded?text(english,"已加载","Loaded"):issue.equals("restart_required")?text(english,"等待重载","Reload needed"):limited||answer==null?text(english,"待确认","Unconfirmed"):text(english,"尚未连接","Not connected"),loaded?1:issue.equals("restart_required")?2:0);
        boolean curveReady=connected&&runtime.has("factory_lux");
        boolean stale=answer!=null&&answer.optLong("state_age_ms")>15000;
        String engineStatus=!curveReady?(connected||loaded&&issue.equals("curve_incompatible"))?text(english,"接口待适配","Interface pending"):text(english,"待确认","Unconfirmed"):stale?text(english,"状态待刷新","Refresh needed"):phase.equals("error")?text(english,"运行异常","Needs attention"):phase.equals("active")?text(english,"自定义生效","Custom active"):text(english,"系统基准","System baseline");
        Item engine=new Item(text(english,"亮度引擎","Brightness"),engineStatus,curveReady&&!stale&&!phase.equals("error")?1:connected||issue.equals("curve_incompatible")?2:0);
        String title,detail;
        if(answer==null){title=text(english,"等待读取运行状态","Waiting for runtime status");detail=text(english,"连接后分别显示访问权限、系统注入和亮度引擎状态；单项未就绪不代表整机不兼容。","Access, injection and brightness are reported independently. A pending feature does not mean the device is incompatible.");}
        else if(limited){title=text(english,"Root 访问需要确认","Root access needs attention");detail=text(english,"辅助进程已启动，但部分访问未完成。本次无法确认引擎状态，不表示系统中的模块已经停止；原设置保留。","The helper started, but some access checks failed. The engine is unconfirmed, not presumed stopped. Saved settings are retained.");}
        else if(issue.equals("restart_required")){title=text(english,"等待加载新版系统钩子","Waiting for the updated module");detail=text(english,"APP 已更新，系统仍在使用旧版模块。请按所用 Root 和 LSPosed 方案重新加载，再确认运行状态。","The app is updated while the older module is still loaded. Reload it using your Root and LSPosed setup, then check again.");}
        else if(stale){title=text(english,"系统状态待刷新","Runtime status needs refreshing");detail=text(english,"最近状态超过15秒未更新，当前生效情况待确认。可刷新记录或查看功能自检，已有设置不会因此被重置。","The last status is over 15 seconds old. Refresh or open feature checks; saved settings will not be reset.");}
        else if(!curveReady){title=loaded?text(english,"系统已注入，曲线尚未就绪","Injected; curve not ready"):text(english,"系统连接尚未建立","System connection not established");detail=loaded?text(english,"模块已加载，曲线接口仍需确认。请查看各项自检与兼容路线，持续不可用时导出分析包。","The module is loaded; the curve interface still needs checking. Review individual routes and export an analysis archive if it stays unavailable."):text(english,"APP 的 Root 桥接已响应，但尚未确认系统注入。请核对模块启用状态与作用域；具体功能以各项自检为准。","The Root bridge responded, but injection is not confirmed. Check module activation and scope, then review individual feature results.");}
        else if(phase.equals("error")){title=text(english,"亮度引擎需要检查","The brightness engine needs attention");detail=text(english,"配置接入时出现异常。请结合运行记录和功能自检确认原因，原设置仍保留。","Configuration activation encountered an error. Review the log and feature checks; saved settings are retained.");}
        else{title=phase.equals("active")?text(english,"自定义亮度正在运行","Custom brightness is running"):text(english,"正在使用系统基础曲线","Using the system base curve");detail=counts[1]+counts[2]>0?text(english,"已读取本机曲线。部分项目仍需确认，可在自检中逐项查看；某项暂不可用不会代表所有功能都不支持。","The local curve is available. Some entries still need checking; one unavailable item does not mean all features are unsupported."):text(english,"已读取本机曲线。权限、接口支持和当前生效状态分别判断，可在功能自检中查看详细结果与采用的路线。","The local curve is available. Permissions, interface support and active behavior are separate; open feature checks for details and selected routes.");}
        return new LogCompatibilityState(new Item[]{rootItem,injection,engine},title,detail,counts);
    }
}
