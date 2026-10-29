package top.rongshangs.lumacurve.refactor;

import java.text.Normalizer;
import java.util.*;

/** Searchable UI destinations only. No hidden features, compatibility probes or option writes. */
final class SettingsSearchIndex {
    static final String[] GROUP_NAMES={"亮度曲线","手动记忆","户外高亮","环境变化与确认","亮度过渡","场景判定与控制","运行状态与界面","暗光稳定与锁定","主屏手动亮度","温控与显示保护","暂停自动亮度名单"};
    static final String[] GROUP_DESCRIPTIONS={"基础亮度、暗处下限与曲线预设","偏好强度、持久保存与锁屏交接","强光增强、HBM 与手动阳光屏","主辅光感阈值与确认时间","分别设置变亮和变暗时长","场景的进入条件、生效效果与退出条件","兼容状态与界面刷新","减少暗光波动，或定时保持亮度","磁贴面板、直接写值与自动退让","显示温控、温度门槛与冷却恢复","指定应用前台时暂停，退出后恢复"};
    static final String[] GROUP_EN={"Brightness curve","Manual memory","Outdoor brightness","Ambient response","Brightness transitions","Scene controls","Runtime and interface","Low light stability and lock","Manual panel brightness","Thermal display protection","Pause auto brightness apps"};
    static final class Entry {
        final int group;final String title,description,enTitle,enDescription,target,words;
        Entry(int group,String title,String description,String enTitle,String enDescription,String target,String aliases){this.group=group;this.title=title;this.description=description;this.enTitle=enTitle;this.enDescription=enDescription;this.target=target;words=normalize(title+" "+description+" "+enTitle+" "+enDescription+" "+GROUP_NAMES[group]+" "+GROUP_DESCRIPTIONS[group]+" "+GROUP_EN[group]+" "+aliases);}
        String section(boolean english){return target.equals("logs")||target.equals("feature_checks")?(english?"Logs":"日志"):english?GROUP_EN[group]:GROUP_NAMES[group];}
        String title(boolean english){return english?enTitle:title;}String description(boolean english){return english?enDescription:description;}
    }
    static Entry e(int g,String t,String d,String en,String ed,String target,String aliases){return new Entry(g,t,d,en,ed,target,aliases);}
    static final List<Entry> ENTRIES=Collections.unmodifiableList(Arrays.asList(
        e(0,"基础亮度曲线","调整不同光线下的亮度起点与曲线预设","Base brightness curve","Adjust brightness targets for different light levels.","curve","蓝色 基础 稍亮 柔和 默认 预设 spline curve"),
        e(0,"深色模式基础曲线","单独调整深色模式亮度；可清除、保持或分开记忆","Dark mode base curve","A separate dark base curve with clear, shared or independent learned preferences.","dark_curve","深色模式 独立曲线 黑暗 夜间 dark theme"),
        e(0,"暗处亮度下限","调整基础曲线在暗处的最低亮度","Minimum brightness","Set the base curve's minimum brightness in darkness.","curve","最低亮度 暗处下限 curve floor minimum nit"),
        e(1,"手动记忆","调整记忆强度，保留亮度条带来的偏好","Manual memory","Control how much your brightness adjustments affect the curve.","memory","绿色 学习 控制中心 亮度条 memory strength learning"),
        e(1,"永久保持记忆","跨自动亮度开关、锁屏和重启保持偏好","Keep memory permanently","Keep preferences across auto mode, screen off and reboot.","memory","保持记忆 永久保持 跨开关 跨锁屏 跨重启 持久化 permanent persistence"),
        e(1,"保持记忆范围","分别选择跨开关、锁屏或重启保持","Memory retention","Choose when brightness preferences should be retained.","memory","记忆 保存 长期 恢复 retention reboot unlock"),
        e(1,"将绿色曲线保存为基础曲线","把当前学习结果保存为新的蓝色基础曲线","Save learned curve as base","Use the current learned curve as your new base curve.","memory","转存 绿色转蓝色 保存曲线 learned save base"),
        e(2,"户外高亮","选择强光下的增强强度与持续时间","Outdoor brightness","Choose the strength and duration of outdoor enhancement.","outdoor","阳光屏 强光 最大增强 outdoor sunlight strength"),
        e(2,"开放系统映射内的高亮范围","提高强光下可用的软件亮度上限","Unlock mapped brightness range","Allow more of the device's supported brightness range.","range","解锁高亮 高光范围 亮度上限 range unlock"),
        e(2,"强光下放宽画面限亮","减少画面内容造成的亮度限制","Relax content brightness limit","Reduce content-dependent dimming in strong light.","opr","画面限量 限亮 灰阶 OPR 内容 apl"),
        e(2,"强制达到节点上限","设置节点增强目标，仍保留系统保护","Node brightness target","Choose a node enhancement target with system protection retained.","outdoor","最高节点 真实节点 节点滑动杆 node maximum target"),
        e(2,"户外亮度上限","按 nit 设置户外增强的最高目标","Outdoor brightness ceiling","Set an outdoor brightness ceiling in nit.","outdoor","最高亮度 nit 2000 亮度封顶 ceiling cap"),
        e(2,"HBM 触发与预算","调整高亮触发照度及支持的时间预算","HBM trigger and budget","Adjust high brightness thresholds and supported time budgets.","outdoor","HBM 高亮模式 时间预算 budget trigger"),
        e(2,"手动阳光屏","设置手动亮度下阳光模式的确认条件","Manual sunlight mode","Adjust sunlight mode confirmation in manual brightness.","outdoor","手动阳光屏 sunlight manual"),
        e(3,"光感阈值与确认时间","调整环境变化多久才影响屏幕亮度","Ambient thresholds and confirmation","Choose how much and how long light must change.","","变亮 变暗 灵敏 温和 防抖 响应 阈值 delay hysteresis"),
        e(3,"辅助光感确认","调整辅助输入的变亮、变暗确认","Auxiliary light confirmation","Tune brightening and darkening confirmation for auxiliary input.","","双光感 辅助光线传感器 assist secondary sensor"),
        e(4,"亮度过渡速度","分别调整变亮、变暗的过渡倍率","Brightness transition speed","Adjust brightening and darkening transition speeds separately.","","平滑 动画 变亮倍率 变暗倍率 animation ramp transition"),
        e(5,"场景判定与控制","查看并设置现有场景的条件、效果和退出方式","Scene controls","Edit supported scenes, effects and entry or exit conditions.","","场景 模式 控制 scene condition effect"),
        e(5,"夜间驾驶","设置夜间驾驶场景的控制方式","Night driving","Configure the system's night driving brightness scene.","scene_night_driving","夜晚 开车 night driving"),
        e(5,"夜间唤醒","调整夜间亮屏相关的场景策略","Night wake","Configure brightness behavior when waking at night.","scene_night_wake","夜间开屏 唤醒 night wake"),
        e(5,"遮挡与距离感应","配置普通距离、流式距离和触摸遮挡","Occlusion and proximity","Configure proximity and touch occlusion scenes.","scene_proximity","背面 距离传感器 遮挡 桌面 触摸 proximity cover"),
        e(5,"反射与 AON 确认","查看反射或 AON 引起的场景限制","Reflection and AON","Configure supported reflection and AON scene behavior.","scene_reflection","反射 AON flare reflection"),
        e(5,"游戏场景","设置游戏场景对亮度变化的影响","Gaming scene","Configure brightness behavior while gaming.","scene_game","游戏 降亮 game gaming"),
        e(6,"界面刷新间隔","调整状态读数的显示刷新频率","Interface refresh interval","Choose how often displayed status readings refresh.","","刷新间隔 界面频率 refresh interval"),
        e(6,"再次查看教程","重新查看欢迎页与五页功能介绍","View tutorial again","Replay the welcome page and five tutorial steps.","","欢迎页 使用方法 新手 tutorial welcome guide"),
        e(7,"暗光稳定","减少暗光下频繁变亮、变暗的波动","Low light stability","Reduce frequent brightness fluctuations in dim light.","low_light","暗处不稳 乱动 变来变去 闪烁 stability flicker"),
        e(7,"暗光锁定","在持续暗光后保持亮度，环境变亮后恢复自动","Low light lock","Hold brightness in darkness and restore auto mode in brighter light.","dark_lock","暗光保持 暗处保持 锁亮度 lock hold"),
        e(7,"日落后锁定","仅在系统判定的日落后至日出前启用暗光锁定","Lock after sunset","Use low light lock only during the system's nighttime window.","dark_lock","日落 日出 夜间 定时 时间 sunset sunrise twilight"),
        e(7,"手动调整后退出锁定","选择调节亮度后继续锁定或维持手动模式","Manual adjustment while locked","Choose whether a manual adjustment continues or exits the lock.","dark_lock","手动后继续锁定 新亮度 manual exit"),
        e(8,"主屏手动亮度","设置磁贴面板与直接节点亮度调节","Manual panel brightness","Configure the tile panel and direct brightness node control.","","主屏节点 面板 磁贴 brightness tile panel"),
        e(8,"控制中心亮度条节点映射","按设备节点比例显示和调节，保留自动亮度与暗光锁定策略","Control center node mapping","Use device node coordinates while preserving auto brightness and low light lock policy.","node_mapping","控制中心节点映射 滑块 映射 控制中心 slider node mapping"),
        e(8,"临时常亮","在主屏亮度面板中开启，本次锁屏前生效","Temporary screen on","Enable it from the brightness panel until the next screen off.","","不熄屏 常亮 屏幕超时 keep awake timeout"),
        e(9,"温控与显示保护","调整显示层温度门槛和冷却恢复","Thermal display protection","Adjust display thermal thresholds and cooling recovery.","thermal","温度限亮 温控 高温 温控放宽 thermal temperature cooling"),
        e(10,"暂停自动亮度名单","名单应用可见时暂停，退出后恢复","Pause auto brightness apps","Pause auto brightness while selected apps remain visible.","","应用 白名单 黑名单 小窗 分屏 自动亮度 apps whitelist window"),
        e(6,"快速设置","通过六个问题选择适合自己的亮度预设","Quick setup","Choose brightness presets by answering six questions.","quick_setup","快速配置 新手 预设 quick setup"),
        e(6,"功能自检与兼容路线","在日志页查看接口、自检结果与采用的兼容路线","Feature checks and compatibility","Inspect feature availability and selected routes under Logs.","feature_checks","功能自检 兼容 权限 Root UID LSP compatibility diagnostics"),
        e(6,"日志与分析包","查看运行记录，或从日志页导出分析包","Logs and analysis archive","Read runtime records or export an analysis archive from Logs.","logs","导出分析包 故障反馈 诊断 日志 log analysis export"),
        e(6,"隐藏桌面图标","保留设置入口，按需隐藏启动图标","Hide launcher icon","Hide the launcher icon while retaining the module settings entry.","","隐藏桌面 应用图标 launcher icon")
    ));
    static String normalize(String text){return text==null?"":Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]+"," ").trim();}
    static List<Entry> find(String query){String q=normalize(query);if(q.isEmpty())return Collections.emptyList();String[] terms=q.split(" +");List<Entry> found=new ArrayList<>();for(Entry entry:ENTRIES){boolean match=true;for(String term:terms)if(!entry.words.contains(term)){match=false;break;}if(match)found.add(entry);}found.sort((a,b)->Integer.compare(rank(b,q),rank(a,q)));return found;}
    static int rank(Entry entry,String query){String title=normalize(entry.title),english=normalize(entry.enTitle);return title.equals(query)||english.equals(query)?3:title.contains(query)||english.contains(query)?2:1;}
}
