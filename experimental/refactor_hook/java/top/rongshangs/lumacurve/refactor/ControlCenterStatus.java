package top.rongshangs.lumacurve.refactor;
import org.json.*;
/** Read-only selection facts. Saved-state banners belong to the ordinary page save action. */
final class ControlCenterStatus {
 static int[] visibleModes(){return new int[]{0,3,4};}
 static String label(int mode){return mode==4?"分区节点控制":mode==3?"完整节点控制":"关闭";}
 static int maximum(JSONObject runtime,JSONObject report){
  JSONObject slider=runtime==null?null:runtime.optJSONObject("control_center_slider");int max=slider==null?0:slider.optInt("maximum");if(max>10&&max<=65535)return max;
  JSONArray rows=report==null?null:report.optJSONArray("features");if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&"main_panel_node".equals(row.optString("id"))){max=row.optInt("node_maximum");if(max>10&&max<=65535)return max;}}
  max=runtime==null?0:runtime.optInt("outdoor_node_maximum");return max>10&&max<=65535?max:0;
 }
 static double ordinary(JSONObject runtime){JSONObject s=runtime==null?null:runtime.optJSONObject("control_center_slider"),p=s==null?null:s.optJSONObject("partition");double n=p==null?Double.NaN:p.optDouble("node",Double.NaN);return Double.isFinite(n)&&n>0&&n<1?n:Double.NaN;}
 static boolean selectable(JSONObject runtime,int mode,int current){return mode==0||mode==current||runtime!=null&&runtime.optBoolean(mode==4?"control_center_partition_supported":"control_center_raw_supported");}
}
