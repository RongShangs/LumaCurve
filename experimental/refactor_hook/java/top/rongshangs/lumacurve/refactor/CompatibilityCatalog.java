package top.rongshangs.lumacurve.refactor;

import java.util.*;

/** Declarative names and route identities only. No probing, hooks, settings or Android I/O.
 * Runtime adapters own selection; FeatureCompatibility explains their actual evidence.
 * Maintenance contract: docs/architecture/compatibility-policy.md.
 */
final class CompatibilityCatalog {
    private CompatibilityCatalog(){}
    private static final String[] FEATURES={"curve","manual_memory","saved_curve","persistent_memory","memory_mode","memory_forever","memory_reset","memory_timeout","main_sensor","assist_sensor","response","small_response","low_light","low_threshold","threshold_override","assist_gate","assist_override","animation_override","sunlight_override","touch_override","scene_night_driving","scene_step","scene_touch","scene_proximity","scene_stream_proximity","scene_reflection","scene_night_wake","scene_manual_sunlight","scene_game","dark_lock","dark_lock_schedule","manual_panel","control_center_slider","apps","thermal","outdoor","hbm","outdoor_range","opr","nit_limit","node_output","main_panel_node","root_storage","root_identity","root_settings","root_process_identity","stored_config","outdoor_node_device","native_payload","launcher","preferences","app_files"};
    static String[] features(){return FEATURES.clone();}
    static final class Route {
        final String id,name,condition;
        Route(String id,String name,String condition){this.id=id;this.name=name;this.condition=condition;}
    }
    static Route route(String id,String name,String condition){return new Route(id,name,condition);}
    static Route[] curveRoutes(){return new Route[]{
        route("refactor","Refactor 逻辑曲线","使用本机 RefactorNitController 的逻辑锚点、手动点与曲线重建接口。"),
        route("refactor_coefficients","Refactor 分段系数曲线","旧式9项系数与22项状态完整；保留原生手动学习和输出，尚不提供虚拟拟合点的跨模式 / 锁屏 / 重启恢复或记忆转基础。"),
        route("miui_physical","小米传统物理映射","实际映射器为 MiuiPhysicalBrightnessMappingStrategy，保留完整设备曲线与应用修正。"),
        route("aosp_physical","系统标准物理映射","实际映射器为 PhysicalMappingStrategy，使用其亮度 / nit 转换与短期模型。")};}
    static Route[] catalogue(String id){
        if(Arrays.asList("curve","manual_memory","saved_curve","persistent_memory","memory_mode","memory_forever","memory_reset","memory_timeout").contains(id))return curveRoutes();
        switch(id){
        case "main_sensor":return new Route[]{route("oem_main","系统标准主光感","读取当前主屏 ABC 选用的 TYPE_LIGHT 光感，沿用原适配。"),route("oem_selected_secondary","系统选用的折叠光感","当前主屏 ABC 直接选用已验证的私有照度光感；拒绝原始、工厂、流式及其他屏幕候选。"),route("oem_selected_fov","系统 FOV 融合光感","沿用主屏原生光感选择器返回的 FOV 源和照度解析；不混用 ABC 保留的物理光感槽。")};
        case "assist_sensor":return new Route[]{
            route("native_auxiliary","系统原生辅助光感","原生辅助槽已有传感器时优先使用，继续沿用系统采样和融合。"),
            route("ported_auxiliary","移植系统辅助补位","原生槽为空；唯一 zte.sensor.light_second 候选通过类型、单位、权限、线程及主屏检查后，补入 OEM 槽并同步双光感能力缓存。")};
        case "apps":return new Route[]{route("visible_tasks","可见任务路径（含小窗 / 分屏）","可见任务接口完整；任一名单应用仍可见时保持暂停。"),route("focus_fallback","前台焦点回退路径","没有完整的可见任务能力时按系统焦点判断，并提示小窗限制。")};
        case "hbm":return new Route[]{route("timed_budget","HBM 照度＋时间预算","时间窗口、最大时长与最短时长均通过预算校验；复制本机数据后调整。"),route("ambient_only","HBM 仅照度门槛（原生零计时）","三个时间字段全部为零且接口完整；只调整照度，保留三个零计时值。"),route("enabled_field","控制器：原生启用字段","读取 mHbmControllerIsEnabled；存在此字段时始终优先，不回退绕过错误。"),route("native_support_method","控制器：原生能力方法","没有启用字段且旧结构完整；调用 deviceSupportsHbm 判断实际支持，继续保留照度、时间与省电条件。")};
        case "opr":case "nit_limit":return new Route[]{route("privacy_aware","带隐私显示判断的新式 SDR 路径","匹配 shouldUsePrivacyOprBrightness，保留隐私显示、HDR、温控与电量判断；额外放宽遵守完整原生OPR表的最高档。"),route("legacy_opr","传统 OPR / SDR 路径","不存在隐私显示分支且旧版灰阶 / nit 接口完整；不将残缺的新式接口误当旧接口。")};
        case "outdoor_range":return new Route[]{route("hbm_range","HBM 与峰值范围","实际 HBM 最大亮度与峰值范围接口均已接入，仍受原生允许条件限制。"),route("dynamic_range","照度动态范围补充","额外匹配 BrightnessRangeControllerImpl；仅该设备实际使用动态范围时参与。")};
        case "dark_lock_schedule":return new Route[]{route("system_twilight","系统日出日落服务","复用实际 TwilightManager 与原生时间戳；只在夜间允许暗光锁定。缺时间等待，不以深色模式或固定钟点代替。")};
        case "dark_lock":return new Route[]{route("abc_sensor_manager","自动亮度控制器的光感服务","复用本机 ABC 的 SensorManager；按传感器类型和句柄识别，锁定期间低频确认退出条件。")};
        case "manual_panel":return new Route[]{route("raw_primary_output","主屏手动节点接管","主屏手动输出接口配合实际节点识别、所有权租约与系统接管退让；不把节点值视为 nit。")};
        case "node_output":return new Route[]{route("raw_primary_output","原生策略节点协作","优先使用实际输出策略，普通自动输出到顶后协作，保留原生保护。"),route("automatic_strategy2_node","独立自动对象与原因协作","仅原生策略入口缺失时核验独立自动对象、同屏归属和双份亮度原因，不替换手动面板路径。")};
        case "control_center_slider":return new Route[]{route("coordinate_v2","双向设备坐标映射","使用本机亮度到背光的转换表；可可靠单调反解才启用。本版入口隐藏，旧选择保留。")};
        case "thermal":return new Route[]{route("display_thermal","显示层温控接口","仅调整显示亮度限制；严重温控、电池温度门槛及其他系统温控继续有效。")};
        case "response":case "small_response":case "low_light":case "low_threshold":case "threshold_override":case "assist_gate":case "assist_override":return new Route[]{
            route("native_driving","原生确认与夜间驾驶保护","使用已验证的主辅光感确认和阈值；保留原生夜间驾驶判断及附加等待。"),
            route("native_without_driving","传统确认与场景保护","完整旧场景结构没有夜间驾驶接口；使用原生确认窗口，保留步行、遮挡、HDR 等判断，不额外采样。")};
        case "animation_override":return new Route[]{route("oem_animation","Refactor 原生时长过渡","调整已验证的原生过渡时长倍率，保持系统动画及主副屏隔离。"),route("traditional_ramp","传统自动亮度速率过渡","原生 Refactor 策略关闭且时长对象为空；匹配主屏双 RampAnimator、三参或四参目标入口及速率原因，只调整普通自动亮度速率，保留系统时长上限及特殊过渡。")};
        case "sunlight_override":return new Route[]{route("manual_sunlight","系统手动模式阳光屏","调整 SunlightController 的进入 / 退出等待，不代替自动 HBM。")};
        case "touch_override":return new Route[]{route("touch_release","系统触摸遮挡释放等待","调整 TouchCoverProtectionHelper 的移开等待，继续保留遮挡保护。")};
        case "outdoor":return new Route[]{route("override_target","系统覆盖目标入口","通过 getOverrideLimitBrightness 增强自动目标，保留原生手动接管与下游保护。"),route("legacy_custom_target","传统物理曲线目标入口","没有覆盖目标接口且旧调用结构完整；在原生自定义曲线目标返回后增强，仍经场景否决、范围裁剪及温控输出。")};
        case "main_panel_node":return new Route[]{route("fold_default_display","折叠屏当前主屏节点","原生折叠布局允许不同物理屏作为默认屏；根据实时默认屏地址、成对背光与唯一点亮 DSI 选择节点；显示端口与内核编号不一致时独立核验默认屏点亮状态，切屏释放旧接管。"),route("backlight_node","主屏 backlight 节点","扫描 backlight，验证主屏身份、真实路径及 brightness / max_brightness 配对。"),route("display_led_node","显示专用 leds 节点","仅接受明确标识为主屏背光的 leds 节点，排除通知灯、手电与背屏。")};
        case "root_storage":return new Route[]{route("root_private_storage","Root 私有数据目录","只检查配置 / 分析目录的读写能力，不安装或启动输出程序。"),route("root_temporary_storage","Root 独立操作目录","原目录受限时使用受验证的私有目录；不迁移原生输出程序或用户设置。")};
        case "root_identity":return new Route[]{route("root_actual_identity","实际进程权限信息","记录实际UID、Capabilities及SELinux上下文，不以Root管理器名称判定权限。")};
        case "root_settings":return new Route[]{route("external_provider","原生设置接口","优先使用原有设置服务；写入仍需系统授权与读回确认。"),route("cmd_settings","系统设置命令服务","原入口不可读时核验cmd设置服务；不重放失败的写入。"),route("settings_cli","系统设置命令入口","前两条不可读时核验系统settings入口；仍遵守系统权限。")};
        case "root_process_identity":return new Route[]{route("root_proc_identity","系统进程有效身份","核验当前系统进程、启动标识和构建；权限不足时不跳过校验。")};
        case "stored_config":return new Route[]{route("stored_json","原始配置 JSON","读取原始配置；自检与兼容裁剪不会将默认值写回用户设置。")};
        case "outdoor_node_device":return new Route[]{route("primary_node_profile","实际主屏节点与框架输出组合","按实际节点上限、曲线映射和输出能力核验，不按机型名单放行。"),route("fold_bound_outdoor","折叠主屏节点协作绑定","当前物理主屏、原生布局和背光配对完整；用独立物理租约核验切屏，不跨屏继承目标。")};
        case "native_payload":return new Route[]{route("apk_native_asset","APK 内原生程序校验","核对当前 APK 的主屏程序资产完整性，不启动守护、不试写亮度。")};
        case "launcher":return new Route[]{route("launcher_alias","桌面入口别名","使用 PackageManager 的独立桌面别名，保留模块设置入口和欢迎引导完成状态。")};
        case "preferences":return new Route[]{route("app_preferences","应用本地偏好存储","读取设置草稿与欢迎记录，不清空、不改变开关。")};
        case "app_files":return new Route[]{route("app_files","应用私有文件目录","检查配置导入导出所用目录的可访问性。")};
        default:
            if(id.startsWith("scene_")&&Arrays.asList("night_driving","step","touch","proximity","stream_proximity","reflection","night_wake","manual_sunlight","game").contains(id.substring(6))){String scene=id.substring(6);return new Route[]{route("oem_"+scene,sceneName(scene),sceneCondition(scene))};}
            return new Route[]{route("unreported","路线未报告","当前版本没有报告此项的具体接口，不推测兼容成功。")};
        }
    }
    static String sceneName(String id){switch(id){case "proximity":return "普通距离策略";case "stream_proximity":return "流式距离策略";case "night_driving":return "系统夜间驾驶策略";case "night_wake":return "系统夜间唤醒策略";case "step":return "系统步行延迟策略";case "touch":return "系统触摸遮挡策略";case "reflection":return "系统反射 / AON 策略";case "game":return "系统游戏防误降亮策略";case "manual_sunlight":return "系统手动阳光屏策略";default:return "未知场景接口";}}
    static String sceneCondition(String id){switch(id){case "proximity":return "匹配 dropAmbientLuxIfNeeded 和普通距离开关；系统未开启时不强制启动。流式距离是另一项独立策略，不混用设置。";case "stream_proximity":return "匹配 SceneDetector 的流式确认与跳过亮度变化接口；与普通距离分别设置。";case "step":return "使用 ABC 原生步行额外降亮确认接口。";case "touch":return "使用 TouchCoverProtectionHelper 的实时遮挡判断。";case "night_wake":return "使用 ABC 的 updateNightWakeMode，不伪造夜间唤醒条件。";case "manual_sunlight":return "使用 SunlightController 原生状态与条件。";default:return "匹配本机该场景的原生消费者，仅限制参与条件，不强制进入场景。";}}
    static int group(String id){if(Arrays.asList("curve","manual_memory","saved_curve","persistent_memory","memory_mode","memory_forever","memory_reset","memory_timeout").contains(id))return 0;if(id.startsWith("scene_")||Arrays.asList("main_sensor","assist_sensor","response","small_response","low_light","low_threshold","assist_gate","threshold_override","assist_override","animation_override","sunlight_override","touch_override").contains(id))return 1;if(Arrays.asList("outdoor","hbm","outdoor_range","opr","nit_limit","node_output","thermal","dark_lock","dark_lock_schedule").contains(id))return 2;return 3;}
}
