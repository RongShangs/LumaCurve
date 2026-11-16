package top.rongshangs.lumacurve.refactor;
import org.json.*;

/** Explicit strong-light physical targets. Ordinary native curves and legacy caps stay separate. */
final class OutdoorTargetCurve {
    static final String ENABLED="outdoor_target_curve_enabled",POINTS="outdoor_target_curve";
    boolean enabled;OutdoorOptions.OprCurve points=new OutdoorOptions.OprCurve(new double[0],new double[0]);
    static OutdoorTargetCurve parse(JSONObject config){OutdoorTargetCurve c=new OutdoorTargetCurve();if(config.has(ENABLED)){Object flag=config.opt(ENABLED);if(!(flag instanceof Boolean))throw new IllegalArgumentException("高亮目标曲线开关无效");c.enabled=(Boolean)flag;}c.points=OutdoorOptions.OprCurve.parse(config.opt(POINTS));if(c.enabled&&c.points.lux.length<2)throw new IllegalArgumentException("高亮目标曲线至少需要2个节点");return c;}
    void put(JSONObject config)throws JSONException{config.put(ENABLED,enabled).put(POINTS,points.json());}
    boolean same(OutdoorTargetCurve other){return enabled==other.enabled&&points.same(other.points);}
    double target(float lux,double floor,double ceiling){if(!enabled||!Double.isFinite(floor)||!Double.isFinite(ceiling)||ceiling<floor)return Double.NaN;double value=points.at(lux);return Double.isFinite(value)?Math.max(floor,Math.min(ceiling,value)):Double.NaN;}
}
