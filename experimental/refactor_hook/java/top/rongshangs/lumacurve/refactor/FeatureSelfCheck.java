package top.rongshangs.lumacurve.refactor;
import org.json.*;
import java.util.*;

/** Independent read-only probes. Failure of one entry never skips another. */
public final class FeatureSelfCheck {
    public interface Probe {boolean read()throws Exception;}
    private final JSONArray entries=new JSONArray();private final JSONObject caps=new JSONObject();
    public void check(String id,String name,String capability,boolean readiness,Probe probe){
        String state,detail;
        try{boolean ok=probe.read();state=ok?"available":readiness?"pending":"unsupported";detail=ok?"实际接口检查通过":readiness?"等待运行对象或服务就绪":"所需接口未完整兼容";}
        catch(Throwable error){state=error instanceof NoSuchFieldException||error instanceof NoSuchMethodException||error instanceof ClassNotFoundException||error instanceof LinkageError?"unsupported":"pending";detail=error.getClass().getSimpleName()+": "+String.valueOf(error.getMessage());}
        if(detail.length()>160)detail=detail.substring(0,160);
        try{entries.put(new JSONObject().put("id",id).put("name",name).put("state",state).put("detail",detail));if(capability!=null)caps.put(capability,state.equals("available")).put(capability+"_state",state);}catch(JSONException impossible){throw new IllegalStateException(impossible);}
    }
    public JSONObject capabilities(){return caps;}
    public JSONObject report(long elapsed,String request)throws JSONException{return new JSONObject().put("checked_elapsed_ms",elapsed).put("request",request).put("mode","actual_interfaces").put("features",entries);}
}
