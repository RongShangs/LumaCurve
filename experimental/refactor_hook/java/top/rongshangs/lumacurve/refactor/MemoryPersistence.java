package top.rongshangs.lumacurve.refactor;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.*;

/** Display-handler state. One debounced save and one bounded wake restore per handover. */
final class MemoryPersistence {
 final HookRuntime state;MemoryOptions options=new MemoryOptions(false,30);
 boolean replaying,pendingRestore,asleep,wakeRestore,archiveLoaded,modelSynced;String pendingWrite,cachedRecord,status="尚无已保存的手动记忆",restoreCause="";
 int savedPoints;long restored,wakeReadyAt,restoreDeadline;float closeLux=Float.NaN,acceptedLux=Float.NaN;
 boolean restoreQueued,resetQueued;
 Boolean lastSettingAuto;
 final Runnable writer=()->flush(),restorer=()->{restoreQueued=false;maybeRestore();},expiry=()->expireWake(),resetter=()->{resetQueued=false;afterReset();};
 MemoryPersistence(HookRuntime state){this.state=state;}
 String scope()throws Exception{CurvePlan p=state.kernel.plan();if(p==null)throw new IllegalStateException("基础曲线未启用");return CurveIdentity.of(state.fingerprint+":"+state.baselineId()+":"+state.kernel.integer("mUserSerial"),p.lux(),p.nits(),state.kernel.min,state.kernel.max);}
 void configure(MemoryOptions next,boolean reset){
  MemoryOptions old=options;
  if(!next.enabled()){cancelRestore();state.handler.removeCallbacks(writer);pendingWrite=null;status="保持记忆已关闭";options=next;return;}
  if(!next.unlock&&wakeRestore||!next.modeSwitch&&restoreCause.equals("mode_switch")||!next.forever&&restoreCause.equals("system_reset"))cancelRestore();
  options=next;
  if(!old.persist&&next.persist&&pendingWrite==null&&(cachedRecord==null||cachedRecord.isEmpty()))archiveLoaded=false;
  if(!old.enabled()||!old.persist&&next.persist||!old.forever&&next.forever)try{if(snapshot(true))queueFlush();else if(!old.persist&&next.persist&&cachedRecord!=null&&!cachedRecord.isEmpty()){pendingWrite=cachedRecord;queueFlush();}}catch(Throwable error){status="记忆保存失败";state.log(status+"："+error.getMessage());}
  if(next.persist&&(reset||!old.persist))scheduleRestore();
  else if(next.forever&&!pendingRestore)scheduleRestore("system_reset");
 }
 void cancelRestore(){pendingRestore=false;restoreQueued=resetQueued=false;state.handler.removeCallbacks(restorer);state.handler.removeCallbacks(resetter);state.handler.removeCallbacks(expiry);wakeReadyAt=0;restoreDeadline=0;}
 // A mapper getter or an unfinished OEM reset must never restore/mutate its own model.
 void requestRestore(){queueRestore(0);}
 void queueRestore(long delay){if(state.closed||replaying||!pendingRestore||!options.enabled()||restoreQueued)return;restoreQueued=true;if(!state.handler.postDelayed(restorer,delay))restoreQueued=false;}
 void requestAfterReset(){if(state.closed||state.changing||replaying||!options.enabled()||resetQueued)return;resetQueued=true;if(!state.handler.post(resetter))resetQueued=false;}
 void expireWake(){if(pendingRestore&&wakeRestore&&!asleep&&restoreDeadline>0&&SystemClock.uptimeMillis()>=restoreDeadline){cancelRestore();status="亮屏确认超时，本次不恢复";state.queuePublish();}}
 void scheduleRestore(){if(options.persist)scheduleRestore("startup");}
 void scheduleRestore(String cause){if(!state.closed&&!replaying&&options.enabled()){pendingRestore=true;wakeRestore=false;restoreCause=cause;requestRestore();}}
 void modeSettingKnown(boolean automatic){if(lastSettingAuto==null)lastSettingAuto=automatic;}
 void modeSettingChanged(boolean automatic){
  if(lastSettingAuto!=null&&lastSettingAuto==automatic)return;lastSettingAuto=automatic;
  if(state.closed||state.changing||replaying||!state.phase.equals("active")||!state.appliesToUser()||state.memoryStrength<=0||!options.enabled())return;
  if(options.modeSwitch)state.memoryLifecycle.event("mode_switch");
  if(!automatic){if(!wakeRestore)cancelRestore();if(options.modeSwitch)status="偏好已保留，等待重新开启自动亮度";return;}
  if(options.modeSwitch){cancelRestore();scheduleRestore("mode_switch");}
 }
 void queueFlush(){state.handler.removeCallbacks(writer);state.handler.post(writer);}
 boolean snapshot(boolean includeModel)throws Exception{
  if(!options.enabled()||state.memoryStrength<=0||state.kernel.plan()==null||!state.kernel.persistentMemorySupported())return false;
  JSONArray points=trim(state.kernel.manualPoints());if(points.length()==0)return false;
  JSONObject record=PersistentMemory.encode(scope(),points,System.currentTimeMillis());
  if(includeModel){JSONObject model=state.memoryLifecycle.modelRecord();if(model!=null)for(int i=0;i<points.length();i++)if(Math.abs(points.getJSONObject(i).getDouble("lux")-model.getDouble("lux"))<.5){record.put("model",model);break;}}
  pendingWrite=record.toString();savedPoints=points.length();status="手动记忆待保存";return true;
 }
 void captureForHandover(){if(state.closed||state.changing||replaying||!state.phase.equals("active")||!state.appliesToUser())return;try{if(snapshot(true))queueFlush();}catch(Throwable error){status="记忆保存失败";state.log(status+"："+error.getMessage());}}
 void afterReset(){
  if(state.closed||state.changing||replaying||!options.enabled()||!state.phase.equals("active")||!state.appliesToUser())return;
  try{if(pendingRestore)return;if(state.power==null||!state.power.isInteractive()){screenOff();return;}
   if(options.forever&&Boolean.TRUE.equals(state.kernel.get("mUseAutoBrightness")))scheduleRestore("system_reset");
   else{status="系统场景变化已重建曲线，存档保留";state.memoryLifecycle.event("environment_reset");}
  }catch(Throwable unavailable){status="等待确认手动记忆恢复条件";}
 }
 void screenOff(){
  if(state.closed||!state.phase.equals("active")||!state.appliesToUser()||asleep)return;
  captureForHandover();closeLux=state.memoryLifecycle.ambientLux();asleep=true;cancelRestore();
  if(options.enabled()&&options.unlock){pendingRestore=true;wakeRestore=true;restoreCause="unlock";status="记忆已保留，等待亮屏确认";}
 }
 void screenOn(){if(state.closed||!asleep)return;asleep=false;acceptedLux=Float.NaN;
  if(options.enabled()&&options.unlock){pendingRestore=true;wakeRestore=true;restoreCause="unlock";restoreDeadline=options.forever?0:SystemClock.uptimeMillis()+20000;state.handler.removeCallbacks(expiry);if(restoreDeadline>0)state.handler.postDelayed(expiry,20000);status="等待亮屏后的有效照度";}
 }
 void ambientReady(float lux){if(state.closed||!pendingRestore||asleep||!Float.isFinite(lux)||lux<0)return;acceptedLux=lux;
  if(wakeRestore&&wakeReadyAt==0){wakeReadyAt=SystemClock.uptimeMillis()+options.settleMs;state.handler.removeCallbacks(restorer);restoreQueued=false;queueRestore(options.settleMs);}
  else requestRestore();
 }
 boolean ready()throws Exception{
  if(state.closed||asleep||state.changing||replaying||!state.phase.equals("active")||!state.appliesToUser()||!state.onDisplayThread()||state.memoryStrength==0||state.kernel.plan()==null||state.power==null||!state.power.isInteractive()||!Boolean.TRUE.equals(state.kernel.get("mUseAutoBrightness"))||Boolean.TRUE.equals(state.hdrActive()))return false;
  if(state.kernel instanceof TraditionalAdapter){TraditionalAdapter a=(TraditionalAdapter)state.kernel;if(TraditionalAdapter.read(a.abc,"mCurrentBrightnessMapper")!=a.mapper)return false;}
  return state.kernel.persistentMemorySupported();
 }
 int capacity(){int cap=state.kernel.manualPointCapacity();return options.maxPoints==0?cap:Math.min(cap,options.maxPoints);}
 JSONArray trim(JSONArray points)throws JSONException{JSONArray kept=new JSONArray();for(int i=Math.max(0,points.length()-capacity());i<points.length();i++)kept.put(points.getJSONObject(i));return kept;}
 void manualApplied(){
  if(state.closed||replaying||state.changing||!state.phase.equals("active")||!state.appliesToUser())return;
  cancelRestore(); // A new gesture always wins, even with persistence disabled.
  if(!options.enabled()||state.memoryStrength<=0||!state.kernel.persistentMemorySupported())return;
  try{if(!snapshot(false))return;state.handler.removeCallbacks(writer);state.handler.postDelayed(writer,1500);
  }catch(Throwable error){status="手动记忆保存失败";state.log(status+"："+error.getMessage());}
 }
 void flush(){if(pendingWrite==null)return;
  try{JSONObject record=new JSONObject(pendingWrite),model=null;JSONArray p=record.getJSONArray("points");
   // The debounced points may belong to the baseline/user that was just replaced.
   // Save those points, but never attach a short-term model from the new scope.
   try{if(record.getString("scope").equals(scope()))model=state.memoryLifecycle.modelRecord();}catch(Exception unavailable){}
   // The outer ABC callback updates its model after the inner curve hook returns.
   // Capture that model at save time, only if it belongs to a saved user anchor.
   if(model!=null)for(int i=0;i<p.length();i++)if(Math.abs(p.getJSONObject(i).getDouble("lux")-model.getDouble("lux"))<.5){record.put("model",model);break;}
   String raw=record.toString();if(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>PersistentMemory.LIMIT)throw new IllegalStateException("记忆数据过大");
   if(options.persist&&!Settings.Global.putString(state.context.getContentResolver(),PersistentMemory.KEY,raw))throw new IllegalStateException("记忆写入失败");cachedRecord=raw;archiveLoaded=true;pendingWrite=null;status=options.persist?"手动记忆已保存":"手动记忆已在本次运行中保留";
  }catch(Throwable error){status="手动记忆保存失败";state.log(status+"："+error.getMessage());}
 }
 String record(){if(pendingWrite!=null)return pendingWrite;if(!archiveLoaded){cachedRecord=options.persist?Settings.Global.getString(state.context.getContentResolver(),PersistentMemory.KEY):null;archiveLoaded=true;}return cachedRecord;}
 void maybeRestore(){expireWake();if(!pendingRestore||!options.enabled())return;
  try{
   if(!state.kernel.persistentMemorySupported()){cancelRestore();status="此设备的手动记忆恢复接口尚未兼容";return;}if(!ready())return;
   if(wakeRestore){long now=SystemClock.uptimeMillis();if(restoreDeadline>0&&now>restoreDeadline){cancelRestore();status="亮屏确认超时，本次不恢复";return;}if(!Float.isFinite(acceptedLux)||wakeReadyAt==0||now<wakeReadyAt)return;}
   boolean mode=restoreCause.equals("mode_switch"),replace=mode||wakeRestore&&(options.replaceOnUnlock||options.forever)||options.forever&&restoreCause.equals("system_reset");
   JSONArray live=state.kernel.manualPoints();if(live.length()>0&&!replace){cancelRestore();status="系统实时记忆优先，存档未覆盖";return;}
   String raw=record();JSONArray p=trim(PersistentMemory.read(raw,scope(),System.currentTimeMillis(),options.days,options.forever));savedPoints=p.length();
   float lux=state.memoryLifecycle.ambientLux();if(!Float.isFinite(lux)||lux<0){status="等待有效照度后恢复记忆";return;}
   if(options.sameScene&&!options.forever&&!mode){boolean archiveMatches=false;for(int i=0;i<p.length();i++)if(MemoryScene.near((float)p.getJSONObject(i).getDouble("lux"),lux,options.sceneRatio,options.sceneMinimum)){archiveMatches=true;break;}
    // A second lock in the new environment must not revive an unrelated old archive.
    boolean matches=archiveMatches&&(!wakeRestore||!Float.isFinite(closeLux)||MemoryScene.near(closeLux,lux,options.sceneRatio,options.sceneMinimum));
    if(p.length()>0&&!matches){cancelRestore();status="环境已变化，本次不恢复记忆";return;}
   }
   cancelRestore();if(p.length()==0){status="无匹配且未过期的手动记忆";return;}
   replaying=true;try{if(replace)state.kernel.replaceManualPoints(p);else state.kernel.restoreManualPoints(p);JSONObject model=PersistentMemory.model(raw);boolean belongs=false;if(model!=null)for(int i=0;i<p.length();i++)if(Math.abs(model.getDouble("lux")-p.getJSONObject(i).getDouble("lux"))<.5)belongs=true;modelSynced=state.kernel.name().equals("physical_mapping")||belongs&&state.memoryLifecycle.restoreModel(model);}finally{replaying=false;}
   restored++;status="手动记忆已恢复";state.memoryLifecycle.event("archive_restored");state.log(status+"："+p.length()+" 个节点");state.requestRecalculation();state.queuePublish();
  }catch(Throwable error){cancelRestore();replaying=false;status="手动记忆恢复未完成";state.log(status+"："+error.getMessage());}
 }
 void clear()throws Exception{state.handler.removeCallbacks(writer);pendingWrite=null;cancelRestore();if(!Settings.Global.putString(state.context.getContentResolver(),PersistentMemory.KEY,null))throw new IllegalStateException("已保存的手动记忆清除失败");cachedRecord="";archiveLoaded=true;savedPoints=0;modelSynced=false;status="手动记忆已清除";}
 void close(){state.handler.removeCallbacks(writer);flush();cancelRestore();}
 void put(JSONObject j)throws JSONException{
  options.put(j);j.put("memory_persist_supported",state.kernel.persistentMemorySupported()).put("memory_point_capacity",state.kernel.manualPointCapacity()).put("memory_effective_capacity",capacity()).put("memory_persist_status",status).put("memory_restore_count",restored).put("memory_save_pending",pendingWrite!=null).put("memory_restore_pending",pendingRestore).put("memory_restore_cause",restoreCause).put("memory_model_synced",modelSynced);
  try{JSONArray live=state.kernel.manualPoints(),saved=state.kernel.plan()==null?new JSONArray():trim(PersistentMemory.read(record(),scope(),System.currentTimeMillis(),options.days,options.forever));savedPoints=saved.length();j.put("memory_live_points",display(live)).put("memory_saved_anchors",display(saved)).put("memory_live_count",live.length());if(record()!=null&&!record().isEmpty())j.put("memory_saved_at",new JSONObject(record()).optLong("saved_at"));}catch(Throwable optional){j.put("memory_archive_error",optional.getClass().getSimpleName());}
  j.put("memory_saved_points",savedPoints);state.memoryLifecycle.put(j);
 }
 JSONArray display(JSONArray points)throws Exception{JSONArray out=new JSONArray();for(int i=0;i<points.length();i++){JSONObject p=new JSONObject(points.getJSONObject(i).toString());float value=state.kernel.manualDisplayValue((float)p.getDouble("value"));if(Float.isFinite(value)&&value>=0)p.put("display_nit",value);out.put(p);}return out;}
}
