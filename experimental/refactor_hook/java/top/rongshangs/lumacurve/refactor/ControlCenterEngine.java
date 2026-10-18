package top.rongshangs.lumacurve.refactor;
import org.json.*;
/** Read-only coordinate metadata; never owns brightness or consumes raw gestures. */
final class ControlCenterEngine {
 static final String REQUEST="hyperlux_control_center_request_v1",UI="hyperlux_control_center_ui_v1",TABLES="hyperlux_control_center_tables_v2";
 static final java.util.concurrent.ThreadPoolExecutor IO=new java.util.concurrent.ThreadPoolExecutor(0,1,10,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<>(2),r->{Thread t=new Thread(r,"HyperLux-slider-calibration");t.setDaemon(true);return t;},new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
 final HookRuntime s;volatile boolean registered,closed;boolean queued;int maximum;String path="";volatile String error="";long accepted,rejected;volatile JSONObject calibration;Object sourceConfig;
 ControlCenterEngine(HookRuntime s){this.s=s;}
 void start(){registered=s.brightnessControl.mainDisplay();if(registered)calibrate();}
 synchronized void calibrate(){if(closed||!registered||queued)return;queued=true;try{IO.execute(()->{try{if(closed)return;Object current=ControlCenterCalibration.configuration(s.owner);JSONObject sampled=sourceConfig==current?calibration:null;if(sampled==null)sampled=ControlCenterCalibration.read(s.owner);if(ControlCenterCalibration.configuration(s.owner)!=current)throw new IllegalStateException("显示配置正在变化，请重试自检");JSONObject snapshot=new JSONObject().put("build",AppBuild.BUILD).put("pid",android.os.Process.myPid()).put("process_start",HookRuntime.processStart()).put("boot_count",android.provider.Settings.Global.getString(s.context.getContentResolver(),"boot_count")).put("calibration",sampled);synchronized(this){if(closed)return;sourceConfig=current;calibration=sampled;error="";s.stateWriter.submit("slider_tables",java.util.Collections.singletonMap(TABLES,snapshot.toString()));}}catch(Throwable unavailable){synchronized(this){calibration=null;sourceConfig=null;error=unavailable.toString();if(!closed)s.stateWriter.submit("slider_tables",java.util.Collections.singletonMap(TABLES,null));}}finally{synchronized(this){queued=false;}}});}catch(java.util.concurrent.RejectedExecutionException unavailable){queued=false;error=unavailable.toString();}}
 boolean supported(){calibrate();return registered&&calibration!=null;}
 void configure(JSONObject config){maximum=config.optInt("control_center_node_maximum",0);path=config.optString("control_center_node_path","");}
 // Already queued v1 gestures must never switch mode or arm a raw lease.
 void request(){}
 JSONObject status()throws JSONException{return new JSONObject().put("mode",s.brightnessControl.options.nodeSliderMode).put("maximum",maximum).put("registered",registered).put("coordinate_ready",calibration!=null).put("kind","coordinate_v2").put("error",error).put("accepted",accepted).put("rejected",rejected);}
 synchronized void close(){closed=true;registered=false;}
}
