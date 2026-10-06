package top.rongshangs.lumacurve.refactor;
import org.json.*;
/** A remembered paused target is distinct from actively holding the panel. */
final class RawPanelTilePolicy {
 static boolean owns(JSONObject r){if(r==null||!"active".equals(r.optString("phase"))||r.optBoolean("auto_mode",true))return false;JSONObject c=r.optJSONObject("brightness_control");return c!=null&&"raw_panel".equals(c.optString("owner"))&&c.optBoolean("raw_confirmed")&&c.optInt("raw_target",-1)>=10&&!c.optBoolean("auto",true);}
 static boolean active(JSONObject r){return owns(r)&&!r.optJSONObject("brightness_control").optBoolean("raw_paused",true);}
 static boolean paused(JSONObject r){return owns(r)&&r.optJSONObject("brightness_control").optBoolean("raw_paused",true);}
}
