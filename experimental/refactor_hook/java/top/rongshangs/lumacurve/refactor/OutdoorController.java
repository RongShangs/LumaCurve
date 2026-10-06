package top.rongshangs.lumacurve.refactor;

import android.os.SystemClock;
import org.json.*;
import java.util.*;

/** One instance on the primary display thread. Timers run only at state deadlines. */
final class OutdoorController {
    final HookRuntime s;final OutdoorPolicy policy=new OutdoorPolicy();final ThermalPolicy thermal=new ThermalPolicy();
    OutdoorOptions options=new OutdoorOptions();HbmAccess access;boolean ticking,failed,probing;String error="",lastReason="";
    long deadline=-1,boosts,rangeRaises,dynamicRaises,oprRaises,goalAt=-1;float goal=Float.NaN,nativeRange=Float.NaN,deliveredRange=Float.NaN,nativeDynamic=Float.NaN,deliveredDynamic=Float.NaN;
    final ArrayDeque<JSONObject> history=new ArrayDeque<>();final Map<String,JSONObject> lastStages=new HashMap<>();
    final Runnable wake;
    OutdoorController(HookRuntime s){this.s=s;wake=()->{deadline=-1;if(s.closed)return;tick();s.requestRecalculation();s.queuePublish();};}
    HbmAccess binding(){try{Object hbm=HookEntry.get(s.owner,"mHbmController");if(access==null||access.controller!=hbm){if(access!=null)access.restore();access=new HbmAccess(hbm);}access.observeReplacement();return access;}catch(Throwable unavailable){error=unavailable.toString();return null;}}
    boolean supported(){return OutdoorTuning.owners.contains(s.owner.getClass())&&binding()!=null;}
    boolean rangeSupported(){return supported()&&OutdoorTuning.ranges.contains(access.controller.getClass())&&OutdoorTuning.peaks.contains(s.owner.getClass());}
    boolean oprSupported(){return rangeSupported()&&OutdoorOpr.owners.contains(s.owner.getClass());}
    boolean oprAllowed(){return options.flags[3]&&rangeAllowed()&&Float.isFinite(lux())&&lux()>=options.values[0]*options.values[4];}
    float opr(float input,float limited){
        if(!oprAllowed()||!OutdoorPolicy.valid(input)||!OutdoorPolicy.valid(limited)||limited>=input)return limited;
        try{float allowed=access.allowedMax(),mapping=access.max();
            if(!OutdoorPolicy.valid(allowed)||!OutdoorPolicy.valid(mapping))return limited;
            return Math.max(limited,Math.min(input,Math.min(allowed,mapping)));
        }catch(Throwable unknown){return limited;}
    }
    void oprApplied(float input,float limited,float delivered){oprRaises++;stage("outdoor_opr_native",input,limited);stage("outdoor_opr_relax",limited,delivered);}
    void configure(OutdoorOptions next){if(!options.same(next)){policy.reset();goal=Float.NaN;}options=next;failed=false;error="";tick();}
    void stop(){s.handler.removeCallbacks(wake);deadline=-1;goal=Float.NaN;options=new OutdoorOptions();policy.step(options,Float.NaN,SystemClock.uptimeMillis(),null);failed=false;
        if(access!=null)try{boolean changed=access.restore();if(changed)access.reevaluate(lux());}catch(Throwable failed){error=failed.toString();s.log("户外增强恢复异常："+error);}}
    float lux(){try{Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl"),abc=HookEntry.get(impl,"mAutomaticBrightnessController");if(!Boolean.TRUE.equals(HookRuntime.optionalBoolean(abc,"mAmbientLuxValid")))return Float.NaN;return HookRuntime.optionalNumber(abc,"mAmbientLux");}catch(Throwable unknown){return Float.NaN;}}
    String block(){
        if(s.closed||!s.phase.equals("active")||!s.appliesToUser())return "inactive";
        if(!s.screenOn()||Boolean.TRUE.equals(HookRuntime.optionalBoolean(access.controller,"mIsAutoBrightnessOffByState")))return "screen_off";
        try{
            if(!Boolean.TRUE.equals(s.kernel.get("mUseAutoBrightness")))return "manual_mode";
            if(!Boolean.FALSE.equals(s.hdrActive())||!Boolean.FALSE.equals(HookRuntime.optionalBoolean(access.controller,"mIsHdrLayerPresent"))||!Boolean.FALSE.equals(HookRuntime.optionalBoolean(access.controller,"mDolbyEnable")))return "hdr";
            if(s.power==null||s.power.isPowerSaveMode()||!Boolean.FALSE.equals(HookRuntime.optionalBoolean(access.controller,"mIsBlockedByLowPowerMode")))return "power_save";
            thermal.configure(s.thermalCooling);if(!thermal.evaluate(true,true,s.thermalSeverity,s.batteryTemperature,s.thermalCeiling))return "temperature";
            Object impl=HookEntry.get(s.owner,"mAutomaticBrightnessControllerImpl"),abc=HookEntry.get(impl,"mAutomaticBrightnessController");
            if(Boolean.TRUE.equals(HookRuntime.optionalBoolean(impl,"mIsOverrideDragPolicy")))return "manual";
            if(!s.normalTuningAllowed(abc,impl))return "scene";
            if(access.managed()&&!Boolean.TRUE.equals(HookRuntime.optionalBoolean(access.controller,"mIsAutoBrightnessEnabled")))return "auto_not_ready";
            String strategy=s.outputStrategy(HookEntry.get(s.owner,"mDisplayPowerController"));
            if(!"AutomaticBrightnessStrategy".equals(strategy))return "output_override";
            return null;
        }catch(Throwable unknown){return "unknown";}
    }
    void manual(float lux){if(!options.flags[0])return;policy.manual(lux,SystemClock.uptimeMillis());tick();schedule();s.queuePublish();}
    void tick(){
        if(ticking||failed||!s.onDisplayThread())return;ticking=true;
        try{
            HbmAccess h=binding();String blocked=h==null?"unsupported":block();float lux=lux();
            boolean before=policy.active;policy.step(options,lux,SystemClock.uptimeMillis(),blocked);if(!policy.active)goal=Float.NaN;
            boolean dataChanged=h!=null&&h.configure(options,blocked==null&&policy.active);
            if(dataChanged)h.reevaluate(lux);
            // Entry/expiry timers also change the target/range without any new sensor event.
            // Recompute through the OEM pipeline so activation and release take effect promptly.
            if(dataChanged||before!=policy.active)s.requestRecalculation();
            if(before!=policy.active||!lastReason.equals(policy.reason)){lastReason=policy.reason;s.log("户外增强："+description(policy.reason));s.queuePublish();}
            schedule();
        }catch(Throwable failure){failed=true;error=failure.toString();policy.reset();policy.reason="error";try{if(access!=null)access.restore();}catch(Throwable ignored){}s.handler.removeCallbacks(wake);deadline=-1;s.queuePublish();}
        finally{ticking=false;}
    }
    void schedule(){long next=options.flags[0]?policy.next:-1;if(next==deadline)return;s.handler.removeCallbacks(wake);deadline=next;if(next>=0)s.handler.postDelayed(wake,Math.max(1,next-SystemClock.uptimeMillis()));}
    boolean rangeAllowed(){
        if(!options.flags[0]||!options.flags[2]||!policy.active||access==null||block()!=null)return false;
        try{return !access.managed()||access.allowed();}catch(Throwable unknown){return false;}
    }
    float range(float original){nativeRange=original;float result=original;
        if(rangeAllowed())try{float max=access.max();if(OutdoorPolicy.valid(original)&&OutdoorPolicy.valid(max))result=Math.max(original,max);}catch(Throwable ignored){}
        deliveredRange=result;if(result>original&&!probing)rangeRaises++;return result;
    }
    float target(float base){
        tick();if(!policy.active||access==null||block()!=null)return base;
        try{float max=access.allowedMax(),normal=access.normal();goal=OutdoorPolicy.target(options,lux(),base,Math.min(normal,max),max);if(goal>base){boosts++;goalAt=SystemClock.uptimeMillis();}return goal;}
        catch(Throwable failed){error=failed.toString();return base;}
    }
    float dynamicRange(float original){nativeDynamic=original;float result=original;
        if(rangeAllowed())try{float max=access.max();if(OutdoorPolicy.valid(original)&&OutdoorPolicy.valid(max))result=Math.max(original,max);}catch(Throwable ignored){}
        deliveredDynamic=result;if(result>original&&!probing){dynamicRaises++;stage("outdoor_dynamic_range",original,result);}return result;
    }
    void stage(String name,float before,float after){
        if(!OutdoorPolicy.valid(before)||!OutdoorPolicy.valid(after))return;
        try{JSONObject old=lastStages.get(name);if(old!=null&&Float.compare(before,(float)old.optDouble("before"))==0&&Float.compare(after,(float)old.optDouble("after"))==0)return;
            JSONObject row=new JSONObject().put("stage",name).put("before",before).put("after",after).put("uptime_ms",SystemClock.uptimeMillis()).put("unix_ms",System.currentTimeMillis()).put("limited",after<before-1e-5f);
            if(history.size()>=32)history.removeFirst();history.addLast(row);lastStages.put(name,row);s.queuePublish();
        }catch(JSONException ignored){}
    }
    JSONObject status(){JSONObject j=new JSONObject();try{
        HbmAccess h=binding();j.put("supported",supported()).put("hbm_tuning_supported",h!=null&&h.tunable()).put("range_supported",rangeSupported()).put("opr_supported",oprSupported());
        j.put("enabled",options.flags[0]).put("active",policy.active).put("reason",policy.reason).put("text",description(policy.reason)).put("target_adjustments",boosts).put("range_adjustments",rangeRaises).put("dynamic_range_adjustments",dynamicRaises).put("opr_adjustments",oprRaises).put("opr_enabled",options.flags[3]);
        JSONObject config=new JSONObject();options.put(config);j.put("options",config).put("limit_trace",new JSONArray(history));
        long now=SystemClock.uptimeMillis();if(deadline>=0)j.put("next_transition_ms",Math.max(0,deadline-now));j.put("cooldown_left_ms",Math.max(0,policy.cooldownUntil-now));
        if(policy.active)j.put("session_left_ms",Math.max(0,(long)options.values[6]-(now-policy.started)));
        if(Float.isFinite(goal))j.put("requested_brightness",goal).put("request_age_ms",Math.max(0,now-goalAt));if(OutdoorPolicy.valid(nativeRange))j.put("native_range_max",nativeRange);if(OutdoorPolicy.valid(deliveredRange))j.put("effective_range_max",deliveredRange);
        if(!error.isEmpty())j.put("interface_detail",error);
        if(h!=null){probing=true;try{h.allowedMax();}finally{probing=false;}if(OutdoorPolicy.valid(nativeRange))j.put("native_range_max",nativeRange);if(OutdoorPolicy.valid(deliveredRange))j.put("effective_range_max",deliveredRange);
            j.put("controller_managed",h.managed()).put("hbm_allowed",h.allowed()).put("device_mapping_max",h.max()).put("normal_max",h.normal());
            j.put("original_minimum_lux",h.originalLux()).put("original_time_window_ms",h.originalLong("timeWindowMillis")).put("original_time_max_ms",h.originalLong("timeMaxMillis")).put("original_time_min_ms",h.originalLong("timeMinMillis"));
            Object data=h.dataField.get(h.controller);j.put("data_overridden",data==h.applied);for(String name:new String[]{"minimumLux","timeWindowMillis","timeMaxMillis","timeMinMillis"})j.put("effective_"+name,HookEntry.get(data,name));
            try{Object dpc=HookEntry.get(s.owner,"mDisplayPowerController"),range=HookEntry.get(dpc,"mBrightnessRangeController"),clamp=HookEntry.get(dpc,"mBrightnessClamperController");
                try{Object dynamic=HookEntry.get(range,"mBrightnessRangeControllerImpl");j.put("dynamic_range_supported",OutdoorTuning.dynamicRanges.contains(dynamic.getClass())).put("dynamic_range_enabled",HookEntry.get(dynamic,"mSupportDynamicBrightnessRange"));
                    float dynamicMax=HookRuntime.optionalNumber(dynamic,"mMaxBrightness");if(OutdoorPolicy.valid(dynamicMax))j.put("dynamic_native_max",dynamicMax);}catch(Throwable absent){}
                java.lang.reflect.Method rangeMax=HbmAccess.method(range.getClass(),"getCurrentBrightnessMax",float.class),clampMax=HbmAccess.method(clamp.getClass(),"getMaxBrightness",float.class);float r,c;probing=true;try{r=((Number)rangeMax.invoke(range)).floatValue();c=((Number)clampMax.invoke(clamp)).floatValue();}finally{probing=false;}if(OutdoorPolicy.valid(r))j.put("display_range_max",r);if(OutdoorPolicy.valid(c))j.put("clamper_max",c);if(OutdoorPolicy.valid(deliveredDynamic))j.put("dynamic_effective_max",deliveredDynamic);
            }catch(Throwable absent){}
            try{Object dpc=HookEntry.get(s.owner,"mDisplayPowerController"),display=dpc.getClass().getMethod("getDisplayPowerState").invoke(dpc);float actual=((Number)display.getClass().getMethod("getScreenBrightness").invoke(display)).floatValue();if(OutdoorPolicy.valid(actual))j.put("actual_brightness",actual);}catch(Throwable absent){}
            long remaining=h.remaining(now);if(remaining>=0)j.put("hbm_remaining_estimate_ms",remaining);
            for(String name:new String[]{"mIsTimeAvailable","mIsInAllowedAmbientRange","mIsBlockedByLowPowerMode","mIsHdrLayerPresent"})j.put(name,HookEntry.get(h.controller,name));
            if(policy.active){String limiter=block();if(limiter==null&&h.managed()&&!h.allowed())limiter=Boolean.TRUE.equals(HookRuntime.optionalBoolean(h.controller,"mIsTimeAvailable"))?"hbm_lux":"hbm_budget";if(limiter!=null)j.put("blocking_condition",limiter);}
        }
    }catch(Throwable unavailable){try{j.put("diagnostic_error",unavailable.toString());}catch(JSONException ignored){}}return j;}
    static String description(String reason){switch(reason){case "active":return "强光增强中";case "enter_confirm":return "正在确认持续强光";case "exit_confirm":return "正在确认离开强光";case "low_lux":return "照度未达到户外条件";case "cooldown":return "本次增强结束，冷却中";case "manual":return "保留你的手动亮度";case "screen_off":return "熄屏，沿用系统";case "manual_mode":return "手动模式，沿用系统";case "hdr":return "HDR 或杜比场景，沿用系统";case "power_save":return "省电限制生效";case "temperature":return "温度条件不满足，沿用系统保护";case "disabled":return "户外增强未开启";case "inactive":return "请先保存并应用";case "unsupported":return "户外接口尚未兼容";case "auto_not_ready":return "等待系统自动亮度就绪";case "scene":return "特殊场景，沿用系统";case "output_override":return "临时或应用指定亮度，沿用系统";case "hbm_lux":return "系统 HBM 照度条件未满足";case "hbm_budget":return "系统 HBM 时间预算不足";default:return "未取得完整条件，沿用系统";}}
}
