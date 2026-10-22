package top.rongshangs.lumacurve.refactor;

import org.json.*;

/** Pure, category-scoped presets. Answering never edits the live or saved configuration. */
final class QuickSetupPlan {
    static final int COUNT=6;
    static final String INVITATION_KEY="quick_setup_invitation_seen_v1";
    static final String[] TOPICS={"响应速度","日常亮度","记忆保持","暗光体验","户外高亮","温控限制"};
    static final String[] QUESTIONS={
        "光线变化时\n你希望亮度怎样响应？",
        "日常使用时\n你更喜欢怎样的亮度？",
        "你希望手动调过的亮度\n被记住多久？",
        "在暗处\n屏幕亮度会来回变化吗？",
        "在强光下\n你希望屏幕再亮一些吗？",
        "温度升高时\n你希望减少屏幕降亮吗？"};
    static final String[] NOTES={
        "灵敏更快跟上环境，温和可以减少小幅波动。只调整本机支持的光感门槛与确认时间。",
        "以本机原始曲线为起点调整，不套用其他机型的亮度配置。",
        "日常拖动控制中心亮度条，就能形成手动偏好。保持范围与自动亮度是否运行是两回事。",
        "暗光稳定让自动调节少些波动；暗光锁定会暂时保持亮度，环境变亮后恢复自动。",
        "已有 nit 上限继续有效；增强程度仍受设备能力、温控、电量与高亮时长限制，不保证达到固定 nit。",
        "仅调整显示层限亮，严重过热仍交回系统。放宽越多，可能发热和耗电越多。"};
    static final String[][] LABELS={
        {"未修改","系统默认","反应灵敏","温和少动"},
        {"未修改","系统默认","稍亮","柔和"},
        {"未修改","系统默认","跨锁屏保持","跨重启保持","永久保持"},
        {"未修改","没有遇到","暗光稳定","暗光锁定"},
        {"未修改","系统默认","温和增强","均衡增强","最大增强"},
        {"未修改","系统默认","略微放宽","更多放宽","最大放宽"}};
    static final String[][] DETAILS={
        {"沿用你已设置的响应方式。","沿用系统原有门槛与确认时间。","更快确认变化，让亮度及时跟上环境。","延长确认并提高变化门槛，减少频繁调节。"},
        {"保留当前基础曲线及自定义下限。","恢复本机原始蓝色基础曲线。","中低光下适当提亮，高照度端保持原范围。","中低光下柔和一些，高照度端保持原范围。"},
        {"保留已设置的保持范围与时效。","保持与重判沿用系统，不额外跨锁屏或重启恢复。","锁屏后，在相近光线下恢复本次使用中的偏好。","锁屏、重启后，在相近光线下恢复已保存的偏好。","跨自动亮度开关、锁屏和重启保持，不因时间或照度过期。"},
        {"保留已设置的稳定、锁定及夜间范围。","不额外启用暗光稳定或暗光锁定。","使用“暗光更稳”预设，继续自动调节亮度。","使用“暗处保持”：≤10 lux 持续1分钟锁定，≥50 lux 持续1秒恢复自动。"},
        {"保留你的增强开关、力度及上限。","关闭本模块户外增强，沿用系统高亮。","使用50%增强力度，优先保持平稳。","使用85%增强力度，兼顾户外可读性。","使用100%力度并开放本机支持的高亮范围；不会主动开启强制节点增强。"},
        {"保留你的温控开关及温度门槛。","沿用系统显示温控，不额外放宽。","电池温度达到43℃时交回系统，冷却后再尝试。","电池温度达到46℃时交回系统，冷却后再尝试。","电池温度达到50℃时交回系统；严重过热仍提前退让。"}};
    static int[] empty(){return new int[COUNT];}
    static int[] checked(int[] answers,boolean complete){
        if(answers==null||answers.length!=COUNT)throw new IllegalArgumentException("快速设置进度无效");int[] copy=answers.clone();
        for(int i=0;i<COUNT;i++){if(copy[i]<-1||copy[i]>=LABELS[i].length)throw new IllegalArgumentException("快速设置进度无效");if(copy[i]==-1)copy[i]=0;}return copy;
    }
    static String unavailable(int question,int choice,JSONObject caps){
        try{if(question<0||question>=COUNT||choice<0||choice>=LABELS[question].length)throw new IllegalArgumentException("未知选项");patch(question,choice,caps);return "";}
        catch(Exception unavailable){return unavailable.getMessage()==null?"本机接口尚未就绪":unavailable.getMessage();}
    }
    static JSONObject patch(int question,int choice,JSONObject caps)throws Exception{
        JSONObject p=new JSONObject();if(choice==0)return p;if(caps==null)throw new IllegalArgumentException("连接设备后可选择此项");int mode=choice-1;
        switch(question){
        case 0:
            boolean any=false;
            String[] groups={"response","threshold_override","assist_override"};
            for(String group:groups){String key=group.equals("response")?"response_supported":group+"_supported";
                if(mode==0||caps.optBoolean(key)){int preset=group.equals("threshold_override")?(mode==1?2:mode==2?1:0):mode;p=SettingsPresets.merge(p,SettingsPresets.patch(group,preset,caps));any=true;}}
            if(!any)throw new IllegalArgumentException("本机光感确认与变化门槛接口尚未就绪");return p;
        case 1:
            float[] original=SavedCurve.numbers(caps.getJSONArray("factory_logical_nit"));float min=(float)caps.getDouble("min_logical_nit"),max=(float)caps.getDouble("max_logical_nit");
            float[] wanted=mode==1?new float[]{1.2f,1.15f,1.05f,1}:mode==2?new float[]{.7f,.8f,.95f,1}:new float[]{1,1,1,1};
            p.put("factors",CurvePlan.encode(CurveEditor.preset(wanted,original,min,max))).put("curve_floor_nit",0);return p;
        case 2:
            if(mode>0)SettingsPresets.require(caps,"memory_persist_supported",true);
            if(mode==3){SettingsPresets.require(caps,"memory_forever_supported",true);SettingsPresets.require(caps,"memory_mode_supported",true);}
            p.put("memory_hold_schema",1).put("memory_hold_forever",mode==3).put("memory_hold_switch",mode==3).put("memory_restore_unlock",mode>0).put("memory_persist",mode>=2);
            if(mode==0)p.put("memory_reset_override",false).put("memory_timeout_override",false);return p;
        case 3:
            p.put("dark_lock_enabled",mode==2);
            if(mode==1)return SettingsPresets.merge(p,SettingsPresets.patch("low",2,caps));
            p.put("low_light_stability",false).put("low_light_threshold",false).put("low_light_assist_gate",false);
            if(mode==2){SettingsPresets.require(caps,"dark_lock_supported",true);p.put("dark_lock_sunset_only",false).put("dark_lock_manual_exit",false).put("dark_lock_enter_lux",10).put("dark_lock_exit_lux",50).put("dark_lock_minutes",1).put("dark_lock_exit_seconds",1);}return p;
        case 4:
            p=SettingsPresets.patch("outdoor",mode,caps);p.remove(OutdoorNodeTarget.KEY);return p;
        case 5:return SettingsPresets.patch("thermal",mode,caps);
        default:throw new IllegalArgumentException("未知问题");
        }
    }
    static JSONObject build(JSONObject draft,JSONObject caps,int[] answers)throws Exception{
        int[] selected=checked(answers,true);JSONObject next=new JSONObject(draft.toString());
        for(int i=0;i<COUNT;i++)if(selected[i]>0){next=SettingsPresets.merge(next,patch(i,selected[i],caps));if(i==1)next.remove(SavedCurve.KEY);}
        if(selected[2]>=2&&next.optDouble("memory_strength",1)<=0)next.put("memory_strength",1);
        // Validate the entire composition before handing a new draft to the Activity.
        MemoryOptions.parse(next);BrightnessControlOptions.parseStored(next);OutdoorOptions.parse(next);AdvancedOptions.parse(next);LowLightThresholds.parse(next);
        ThermalPolicy.validate((float)next.optDouble("thermal_ceiling",43));ThermalPolicy.validateCooling((float)next.optDouble("thermal_cooling",1));
        return next;
    }
}
