package top.rongshangs.lumacurve.refactor;
import android.app.PendingIntent;
import android.content.*;
import android.database.ContentObserver;
import android.os.*;
import android.provider.Settings;
import android.service.quicksettings.*;
import java.lang.ref.WeakReference;
import java.util.Locale;
import org.json.*;
/** Observes the actual system owner; never starts Root or a brightness writer. */
public final class BrightnessTileService extends TileService {
 static WeakReference<BrightnessTileService> visible=new WeakReference<>(null);
 final Handler ui=new Handler(Looper.getMainLooper());boolean registered;
 final ContentObserver observer=new ContentObserver(ui){public void onChange(boolean self){refresh();}};
 static void publish(JSONObject runtime){BrightnessTileService tile=visible.get();if(tile!=null)tile.render(runtime);}
 @Override public void onStartListening(){super.onStartListening();visible=new WeakReference<>(this);if(!registered){
  for(String key:new String[]{HookRuntime.STATUS,BrightnessControl.OWNER})getContentResolver().registerContentObserver(Settings.Global.getUriFor(key),false,observer);
  getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),false,observer);registered=true;
 }refresh();}
 @Override public void onStopListening(){release();super.onStopListening();}
 @Override public void onDestroy(){release();super.onDestroy();}
 void release(){if(registered)getContentResolver().unregisterContentObserver(observer);registered=false;if(visible.get()==this)visible.clear();}
 void refresh(){try{
  String text=Settings.Global.getString(getContentResolver(),HookRuntime.STATUS);JSONObject runtime=text==null?null:new JSONObject(text);
  String ownership=Settings.Global.getString(getContentResolver(),BrightnessControl.OWNER);JSONObject actualOwner=ownership==null?null:new JSONObject(ownership),control=runtime==null?null:runtime.optJSONObject("brightness_control");
  if(actualOwner==null||control==null||!"raw_panel".equals(actualOwner.optString("owner"))||!actualOwner.optString("session").equals(control.optString("session")))runtime=null;
  if(runtime!=null)runtime.put("auto_mode",Settings.System.getInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,1)==1);
  render(runtime);
 }catch(Throwable unavailable){render(null);}}
 void render(JSONObject runtime){Tile tile=getQsTile();if(tile==null)return;boolean active=RawPanelTilePolicy.active(runtime),paused=RawPanelTilePolicy.paused(runtime),en=!Locale.getDefault().getLanguage().equals("zh");
  tile.setState(active?Tile.STATE_ACTIVE:Tile.STATE_INACTIVE);tile.setLabel(UiText.translate("主屏亮度",en));tile.setSubtitle(UiText.translate(active?"手动保持中":paused?"解锁后恢复":"滑动接管",en));
  try{tile.setActivityLaunchForClick(panelIntent());}catch(Throwable optional){}tile.updateTile();
 }
 PendingIntent panelIntent(){return PendingIntent.getActivity(this,2,new Intent(this,BrightnessPanelActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
 @Override public void onClick(){super.onClick();unlockAndRun(()->startActivityAndCollapse(panelIntent()));}
}
