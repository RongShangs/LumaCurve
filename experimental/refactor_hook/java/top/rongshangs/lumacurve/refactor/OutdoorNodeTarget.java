package top.rongshangs.lumacurve.refactor;
import org.json.*;
/** A raw device node target. Zero follows the currently discovered hardware maximum. */
final class OutdoorNodeTarget {
    static final String KEY="outdoor_node_target";
    static final int MINIMUM=10,MAXIMUM=65535;
    static int parse(JSONObject config){
        if(!config.has(KEY))return 0;
        Object raw=config.opt(KEY);if(!(raw instanceof Number))throw new IllegalArgumentException("增强目标节点须为整数");
        double value=((Number)raw).doubleValue();
        if(!Double.isFinite(value)||value!=Math.rint(value)||value!=0&&(value<MINIMUM||value>MAXIMUM))throw new IllegalArgumentException("增强目标节点超出范围");
        return (int)value;
    }
    static boolean known(int maximum){return maximum>MINIMUM&&maximum<=MAXIMUM;}
    static int resolve(int target,int maximum){
        if(!known(maximum))throw new IllegalArgumentException("尚未读取设备真实节点上限");
        if(target!=0&&(target<MINIMUM||target>MAXIMUM))throw new IllegalArgumentException("增强目标节点超出范围");
        return target==0?maximum:Math.min(target,maximum);
    }
    static int stored(int target,int maximum){int actual=resolve(target,maximum);return actual==maximum?0:actual;}
}
