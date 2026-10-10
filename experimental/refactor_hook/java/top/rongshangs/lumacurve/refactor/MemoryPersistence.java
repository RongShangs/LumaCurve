package top.rongshangs.lumacurve.refactor;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.*;

/** Display-handler state. One debounced save and one bounded wake restore per handover. */
final class MemoryPersistence {
 final HookRuntime state;MemoryOptions options=new MemoryOptions(true,30);
 boolean replaying,pendingRestore,asleep,wakeRestore,archiveLoaded,modelSynced;String pendingWrite,cachedRecord,status="尚无已保存的手动记忆",restoreCause="";
 int savedPoints;long restored,wakeReadyAt,restoreDeadline;float closeLux=Float.NaN,acceptedLux=Float.NaN;
 boolean restoreQueued,resetQueued;
 final Runnable writer=()->flush(),restorer=()->{restoreQueued=false;maybeRestore();},expiry=()->expireWake(),resetter=()->{resetQueued=false;afterReset();};
 MemoryPersistence(HookRuntime state){this.state=state;}
 String scope()throws Exception{CurvePlan p=state.kernel.plan();if(p==null)throw new IllegalStateException("基础曲线未启用");return CurveIdentity.of(state.fingerprint+":"+state.baselineId()+":"+state.kernel.integer("mUserSerial"),p.lux(),p.nits(),state.kernel.min,state.kernel.max);}
 void configure(MemoryOptions next,boolean reset){
  if(!next.persist){cancelRestore();state.handler.removeCallbacks(writer);pendingWrite=null;status="记忆持久化已关闭";}
  if(!next.unlock&&wakeRestore)cancelRestore();options=next;if(next.persist&&reset){flush();scheduleRestore();}
 }
 void cancelRestore(){pendingRestore=false;restoreQueued=resetQueued=false;state.handler.removeCallbacks(restorer);state.handler.removeCallbacks(resetter);state.handler.removeCallbacks(expiry);wakeReadyAt=0;restoreDeadline=0;}
 // A mapper getter or an unfinished OEM reset must never restore/mutate its own model.
 void requestRestore(){queueRestore(0);}
 void queueRestore(long delay){if(state.closed||replaying||!pendingRestore||!options.persist||restoreQueued)return;restoreQueued=true;if(!state.handler.postDelayed(restorer,delay))restoreQueued=false;}
 void requestAfterReset(){if(state.closed||state.changing||replaying||!options.persist||resetQueued)return;resetQueued=true;if(!state.handler.post(resetter))resetQueued=false;}
 void expireWake(){if(pendingRestore&&wakeRestore&&!asleep&&restoreDeadline>0&&SystemClock.uptimeMillis()>=restoreDeadline){cancelRestore();status="亮屏确认超时，本次不恢复";state.queuePublish();}}
 void scheduleRestore(){if(!state.closed&&!replaying&&options.persist){pendingRestore=true;wakeRestore=false;restoreCause="startup";requestRestore();}}
 void afterReset(){
  if(state.closed||state.changing||replaying||!options.persist||!state.phase.equals("active")||!state.appliesToUser())return;
  try{if(pendingRestore)return;if(state.power==null||!state.power.isInteractive()){screenOff();return;}if(!Boolean.TRUE.equals(state.kernel.get("mUseAutoBrightness")))scheduleRestore();
   else{status="系统场景变化已重建曲线，存档保留";state.memoryLifecycle.event("environment_reset");}
  }catch(Throwable unavailable){status="等待确认手动记忆恢复条件";}
 }
 void screenOff(){
  if(state.closed||!state.phase.equals("active")||!state.appliesToUser()||asleep)return;
  closeLux=state.memoryLifecycle.ambientLux();asleep=true;flush();cancelRestore();
  if(options.persist&&options.unlock){pendingRestore=true;wakeRestore=true;restoreCause="unlock";status="记忆已保留，等待亮屏确认";}
 }
 void screenOn(){if(state.closed||!asleep)return;asleep=false;acceptedLux=Float.NaN;
  if(options.persist&&options.unlock){pendingRestore=true;wakeRestore=true;restoreCause="unlock";restoreDeadline=SystemClock.uptimeMillis()+20000;state.handler.removeCallbacks(expiry);state.handler.postDelayed(expiry,20000);status="等待亮屏后的有效照度";}
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
  if(!options.persist||state.memoryStrength<=0||!state.kernel.persistentMemorySupported())return;
  try{JSONArray p=trim(state.kernel.manualPoints());if(p.length()==0)return;pendingWrite=PersistentMemory.encode(scope(),p,System.currentTimeMillis()).toString();savedPoints=p.length();status="手动记忆待保存";state.handler.removeCallbacks(writer);state.handler.postDelayed(writer,1500);
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
   if(!Settings.Global.putString(state.context.getContentResolver(),PersistentMemory.KEY,raw))throw new IllegalStateException("记忆写入失败");cachedRecord=raw;archiveLoaded=true;pendingWrite=null;status="手动记忆已保存";
  }catch(Throwable error){status="手动记忆保存失败";state.log(status+"："+error.getMessage());}
 }
 String record(){if(pendingWrite!=null)return pendingWrite;if(!archiveLoaded){cachedRecord=Settings.Global.getString(state.context.getContentResolver(),PersistentMemory.KEY);archiveLoaded=true;}return cachedRecord;}
 void maybeRestore(){expireWake();if(!pendingRestore||!options.persist)return;
  try{
   if(!state.kernel.persistentMemorySupported()){cancelRestore();status="此设备的手动记忆恢复接口尚未兼容";return;}if(!ready())return;
   if(wakeRestore){long now=SystemClock.uptimeMillis();if(restoreDeadline>0&&now>restoreDeadline){cancelRestore();status="亮屏确认超时，本次不恢复";return;}if(!Float.isFinite(acceptedLux)||wakeReadyAt==0||now<wakeReadyAt)return;}
   JSONArray live=state.kernel.manualPoints();if(live.length()>0&&(!wakeRestore||!options.replaceOnUnlock)){cancelRestore();status="系统实时记忆优先，存档未覆盖";return;}
   String raw=record();JSONArray p=trim(PersistentMemory.read(raw,scope(),System.currentTimeMillis(),options.days));savedPoints=p.length();
   float lux=state.memoryLifecycle.ambientLux();if(!Float.isFinite(lux)||lux<0){status="等待有效照度后恢复记忆";return;}
   if(options.sameScene){boolean archiveMatches=false;for(int i=0;i<p.length();i++)if(MemoryScene.near((float)p.getJSONObject(i).getDouble("lux"),lux,options.sceneRatio,options.sceneMinimum)){archiveMatches=true;break;}
    // A second lock in the new environment must not revive an unrelated old archive.
    boolean matches=archiveMatches&&(!wakeRestore||!Float.isFinite(closeLux)||MemoryScene.near(closeLux,lux,options.sceneRatio,options.sceneMinimum));
    if(p.length()>0&&!matches){cancelRestore();status="环境已变化，本次不恢复记忆";return;}
   }
   boolean replace=wakeRestore&&options.replaceOnUnlock;cancelRestore();if(p.length()==0){status="无匹配且未过期的手动记忆";return;}
   replaying=true;try{if(replace)state.kernel.replaceManualPoints(p);else state.kernel.restoreManualPoints(p);JSONObject model=PersistentMemory.model(raw);boolean belongs=false;if(model!=null)for(int i=0;i<p.length();i++)if(Math.abs(model.getDouble("lux")-p.getJSONObject(i).getDouble("lux"))<.5)belongs=true;modelSynced=state.kernel.name().equals("physical_mapping")||belongs&&state.memoryLifecycle.restoreModel(model);}finally{replaying=false;}
   restored++;status="手动记忆已恢复";state.memoryLifecycle.event("archive_restored");state.log(status+"："+p.length()+" 个节点");state.requestRecalculation();state.queuePublish();
  }catch(Throwable error){cancelRestore();replaying=false;status="手动记忆恢复未完成";state.log(status+"："+error.getMessage());}
 }
 void clear()throws Exception{state.handler.removeCallbacks(writer);pendingWrite=null;cancelRestore();if(!Settings.Global.putString(state.context.getContentResolver(),PersistentMemory.KEY,null))throw new IllegalStateException("已保存的手动记忆清除失败");cachedRecord="";archiveLoaded=true;savedPoints=0;modelSynced=false;status="手动记忆已清除";}
 void close(){state.handler.removeCallbacks(writer);flush();cancelRestore();}
 void put(JSONObject j)throws JSONException{
  options.put(j);j.put("memory_persist_supported",state.kernel.persistentMemorySupported()).put("memory_point_capacity",state.kernel.manualPointCapacity()).put("memory_effective_capacity",capacity()).put("memory_persist_status",status).put("memory_restore_count",restored).put("memory_save_pending",pendingWrite!=null).put("memory_restore_pending",pendingRestore).put("memory_restore_cause",restoreCause).put("memory_model_synced",modelSynced);
  try{JSONArray live=state.kernel.manualPoints(),saved=state.kernel.plan()==null?new JSONArray():trim(PersistentMemory.read(record(),scope(),System.currentTimeMillis(),options.days));savedPoints=saved.length();j.put("memory_live_points",display(live)).put("memory_saved_anchors",display(saved)).put("memory_live_count",live.length());if(record()!=null&&!record().isEmpty())j.put("memory_saved_at",new JSONObject(record()).optLong("saved_at"));}catch(Throwable optional){j.put("memory_archive_error",optional.getClass().getSimpleName());}
  j.put("memory_saved_points",savedPoints);state.memoryLifecycle.put(j);
 }
 JSONArray display(JSONArray points)throws Exception{JSONArray out=new JSONArray();for(int i=0;i<points.length();i++){JSONObject p=new JSONObject(points.getJSONObject(i).toString());float value=state.kernel.manualDisplayValue((float)p.getDouble("value"));if(Float.isFinite(value)&&value>=0)p.put("display_nit",value);out.put(p);}return out;}
}
