package top.rongshangs.lumacurve.refactor;

import android.database.ContentObserver;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.*;

/** Explicit v3 gestures use the existing raw-panel owner, lease and wake/lock cleanup. */
final class ControlCenterNodes {
    static final String REQUEST="hyperlux_control_center_node_request_v3";
    final ControlCenterEngine engine;final HookRuntime s;
    boolean registered;ContentObserver gestures,modes;String lastId="",error="";long accepted,rejected,lastGesture,autoEpoch;int confirmations;
    final Runnable confirm=()->confirm();
    final JSONArray confirmationTrace=new JSONArray();
    void trace(String event){try{if(confirmationTrace.length()>=8)confirmationTrace.remove(0);confirmationTrace.put(new JSONObject().put("event",event).put("elapsed_ms",SystemClock.elapsedRealtime()).put("session",s.brightnessControl.session).put("target",s.brightnessControl.rawTarget).put("attempt",confirmations).put("error",error));}catch(JSONException ignored){}}
    ControlCenterNodes(ControlCenterEngine engine){this.engine=engine;s=engine.s;}
    String physical(){try{Object id=HookEntry.get(HookEntry.get(s.owner,"mDisplayPowerController"),"mUniqueDisplayId");return id instanceof String?(String)id:"";}catch(Throwable unavailable){return "";}}
    boolean supported(){return registered&&engine.supported()&&RawPanelOutputHooks.supports(s.owner.getClass())&&!physical().isEmpty();}
    void start(){
        gestures=new ContentObserver(s.handler){public void onChange(boolean self){request();}};
        modes=new ContentObserver(s.handler){public void onChange(boolean self){if(s.brightnessControl.auto())autoEpoch++;engine.publishTables();}};
        try{s.context.getContentResolver().registerContentObserver(Settings.Global.getUriFor(REQUEST),false,gestures);s.context.getContentResolver().registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),false,modes);registered=true;}
        catch(Throwable failure){close();error=failure.toString();}
        // Do not consume a gesture left over from a previous injection/boot.
    }
    static int validate(JSONObject request,JSONObject generation,long now,long autoEnabledAt)throws Exception{
        for(String key:new String[]{"pid","user_serial","maximum","target","elapsed"}){Object value=request.opt(key);if(!(value instanceof Number)||!Double.isFinite(((Number)value).doubleValue())||((Number)value).doubleValue()!=Math.rint(((Number)value).doubleValue()))throw new IllegalArgumentException("节点请求数值格式无效");}
        long at=request.getLong("elapsed"),age=now-at;
        if(age<0||age>3000||autoEnabledAt>at)throw new IllegalArgumentException("操作已过期或自动亮度已重新开启");
        if(generation.has("auto_epoch")&&request.optLong("auto_epoch",-1)!=generation.getLong("auto_epoch"))throw new IllegalArgumentException("自动亮度已重新开启，忽略排队操作");
        if(generation.has("mode")&&request.optInt("mode",-1)!=generation.getInt("mode"))throw new IllegalArgumentException("节点控制模式已改变");
        for(String key:new String[]{"build","process_start","boot_count","revision","physical"})if(generation.optString(key).isEmpty()||!generation.getString(key).equals(request.optString(key)))throw new IllegalArgumentException("节点映射代次已变化");
        if(request.optInt("pid",-1)!=generation.getInt("pid")||request.optInt("user_serial",-1)!=0||generation.optInt("user_serial",-1)!=0||request.optInt("maximum",-1)!=generation.getInt("maximum"))throw new IllegalArgumentException("节点映射身份不一致");
        String id=request.getString("id"),token=request.getString("session");
        if(!id.matches("[A-Za-z0-9_-]{1,80}")||!token.matches("cc_[A-Za-z0-9_-]{1,72}"))throw new IllegalArgumentException("节点会话无效");
        int maximum=generation.getInt("maximum"),target=request.getInt("target");
        if(maximum<=10||maximum>65535||request.getDouble("target")!=target||target<10||target>maximum)throw new IllegalArgumentException("节点目标超出设备范围");
        return target;
    }
    JSONObject generation()throws Exception{return new JSONObject().put("build",AppBuild.BUILD).put("pid",android.os.Process.myPid()).put("process_start",HookRuntime.processStart()).put("boot_count",Settings.Global.getString(s.context.getContentResolver(),"boot_count")).put("revision",engine.revision).put("user_serial",engine.user).put("physical",physical()).put("maximum",engine.maximum).put("mode",engine.mode).put("auto_epoch",autoEpoch);}
    void request(){
        if(s.closed)return;
        try{
            String raw=Settings.Global.getString(s.context.getContentResolver(),REQUEST);if(raw==null||raw.length()>4096)return;
            JSONObject request=new JSONObject(raw);String id=request.getString("id");if(id.equals(lastId))return;lastId=id;
            BrightnessControl control=s.brightnessControl;
            int target=validate(request,generation(),SystemClock.elapsedRealtime(),control.lastAutoEnabledAt);
            long at=request.getLong("elapsed");if(at<lastGesture)throw new IllegalArgumentException("忽略较早的节点操作");
            if(!engine.enabled||control.options.nodeSliderMode<3||!control.enabled()||!control.awake()||!control.unlocked()||!control.mainDisplay()||!(Boolean.FALSE.equals(s.hdrActive())||Boolean.TRUE.equals(s.hdrActive())&&control.owner.equals("raw_panel")&&control.rawConfirmed&&control.rawIdentityCurrent()&&!control.auto())||s.thermalSeverity<0||s.thermalSeverity>=3||!supported())throw new IllegalStateException("当前主屏状态不允许节点控制");
            // Raw-panel ownership releases dark/app locks and disables auto exactly once.
            // Further slider events update the same lease instead of recreating a writer.
            String token=request.getString("session");
            boolean owned=control.owner.equals("raw_panel")&&control.session.equals(token)&&control.rawIdentityCurrent()&&!control.auto();
            String action=request.optString("action","raw");
            boolean previous=control.owner.equals("raw_panel")&&control.rawIdentityCurrent()&&!control.auto()&&control.session.equals(request.optString("previous_session"));
            if(control.options.nodeSliderMode==4){
                JSONObject partition=engine.partition;if(partition==null)throw new IllegalStateException("普通亮度区间尚未确认");
                int boundary=(int)Math.ceil(partition.getDouble("node")*engine.maximum);
                if(action.equals("release")){
                    if(target>boundary)throw new IllegalArgumentException("尚未返回普通亮度区间");
                    if(owned||previous){control.relinquish("control_center_normal_range",false);if(control.options.partitionRestoreAuto)control.setAuto(true);}
                    lastGesture=at;accepted++;error="";s.handler.removeCallbacks(confirm);engine.publishTables();s.queueControlPublish();return;
                }
                int upper=ControlCenterPartition.upper(control.options.partitionMaximum,engine.maximum,partition.getDouble("node"));
                if(target>upper)throw new IllegalArgumentException("节点目标超过用户设置的上限");
                boolean admitted=ControlCenterPartition.Gate.proof(request);
                if(!owned&&!previous&&request.optBoolean("partition_automatic_high")){
                    // A displayed high-zone thumb alone cannot grant ownership. Re-read the existing
                    // primary display interface at admission; no polling or extra node writer.
                    double actual=control.auto()&&control.display!=null?control.display.read().optDouble("actual",Double.NaN):Double.NaN;
                    admitted=admitted&&Double.isFinite(actual)&&actual>=0&&actual<=1&&engine.calibration!=null&&ControlCenterCurve.read(engine.calibration.getJSONObject("standard")).toNode(actual)>partition.getDouble("node");
                }
                if(!action.equals("raw")||target<=boundary||!owned&&!previous&&!admitted)throw new IllegalArgumentException("请在提示后三秒内再次向上滑动");
            }else if(!action.equals("raw"))throw new IllegalArgumentException("当前模式不接受分区操作");
            if(owned){
                control.rawTarget=target;control.writeRawLease(true);
            }else{control.armRaw(token,target,physical());trace("arm");}
            lastGesture=at;accepted++;error="";
            confirmations=0;s.handler.removeCallbacks(confirm);s.handler.postDelayed(confirm,100);
            engine.publishTables();s.queueControlPublish();
        }catch(Throwable failure){rejected++;error=failure.toString();engine.publishTables();s.queueControlPublish();}
    }
    void confirm(){
        BrightnessControl control=s.brightnessControl;
        if(s.closed||!control.owner.equals("raw_panel")||!control.session.startsWith("cc_")||control.auto())return;
        try{if(RawPanelLease.healthy(control.session)){error="";if(!control.rawConfirmed)trace("confirmed");control.rawConfirmed=true;engine.publishTables();s.queueControlPublish();return;}error="节点保持程序健康状态尚未就绪或已过期";}
        catch(Throwable failure){error=failure.toString();}
        if(confirmations==0)trace("waiting");
        if(++confirmations<6){s.handler.postDelayed(confirm,200);return;}
        trace("timeout");
        boolean restore=control.rawWasAuto;control.relinquish("control_center_writer_missing",false);if(restore)try{control.setAuto(true);}catch(Throwable failure){error=failure.toString();}engine.publishTables();s.queueControlPublish();
        // Normal lease renewal owns subsequent health/lock/display checks; no idle polling.
    }
    JSONObject status()throws JSONException{
        BrightnessControl c=s.brightnessControl;
        boolean owned=c.owner.equals("raw_panel");
        return new JSONObject().put("session",owned?c.session:"").put("auto_epoch",autoEpoch).put("active",owned&&!c.auto()&&!c.rawPaused&&!c.rawScreenOff&&c.awake()&&c.rawIdentityCurrent()).put("confirmed",owned&&c.rawConfirmed).put("target",owned?c.rawTarget:-1).put("physical",physical()).put("accepted",accepted).put("rejected",rejected).put("error",error).put("confirmation_trace",new JSONArray(confirmationTrace.toString()));
    }
    void deactivate(){deactivate(false);}
    void deactivate(boolean restore){s.handler.removeCallbacks(confirm);BrightnessControl c=s.brightnessControl;if(c.owner.equals("raw_panel")&&c.session.startsWith("cc_"))c.relinquish("control_center_disabled",restore);}
    void close(){registered=false;deactivate(true);for(ContentObserver observer:new ContentObserver[]{gestures,modes})if(observer!=null)try{s.context.getContentResolver().unregisterContentObserver(observer);}catch(Throwable ignored){}gestures=null;modes=null;}
}
