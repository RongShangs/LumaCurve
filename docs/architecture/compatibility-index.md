# 兼容路线索引（自动生成）

来源：`CompatibilityCatalog.java` ＋ `compatibility-manifest.json` ＋ `source-map.json`。修改来源后运行 `python tools/verify_compatibility.py --write-doc`；普通校验会拒绝过期索引。

此索引说明已实现和验证责任，不代表所有功能已在所有设备启用或实测。

共 55 项功能、22 组适配、163 份生产 Java。

## 曲线与手动记忆 · curve

功能：`curve`、`manual_memory`、`saved_curve`、`persistent_memory`、`memory_mode`、`memory_forever`、`memory_reset`、`memory_timeout`。

选择：ABC 活动策略为准；Refactor 未就绪不得因物理映射对象存在而抢用，未知活动标记才按已验证对象选择。 Refactor内部分段锚点优先；任一旧锚点标记存在时不得回退绕过残缺旧结构；仅旧锚点整体缺失且9/22系数布局、实例字段与原生消费者签名完整时采用系数路径。 记忆转基础保持现有路径；仅对新鲜物理映射采样的4个float ULP以内边界/平台舍入误差归一，仍严格校验全节点、坐标与范围；Refactor锚点及持久化输入不放宽。 原机完整单调曲线超出真实标定范围时，只对已验证系统物理映射及双向原生样条追加端点裁剪分支；其他范围内旧路径不增加探测要求，残缺标定不回退绕过。；可选深色曲线复用同一已选后端和实际设备约束，仅按系统uiMode选择独立基础配置；不开启时旧配置原样选择。 深浅色模式记忆策略独立选择清除/共用/分开；独立作用域按本机用户与基础曲线隔离，仅启用跨重启保持才保存两份磁盘存档。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `refactor` · Refactor 逻辑曲线 | 使用本机 RefactorNitController 的逻辑锚点、手动点与曲线重建接口。 |
| `refactor_coefficients` · Refactor 分段系数曲线 | 旧式9项系数与22项状态完整；保留原生手动学习和输出，尚不提供虚拟拟合点的跨模式 / 锁屏 / 重启恢复或记忆转基础。 |
| `miui_physical` · 小米传统物理映射 | 实际映射器为 MiuiPhysicalBrightnessMappingStrategy，保留完整设备曲线与应用修正。 |
| `aosp_physical` · 系统标准物理映射 | 实际映射器为 PhysicalMappingStrategy，使用其亮度 / nit 转换与短期模型。 |
| `aosp_physical_clamped` · 系统物理映射：标定端点裁剪 | 原机曲线超出标定范围，且实际双向标定样条及端点裁剪通过验证；按有效范围编辑，默认保持原曲线对象与原生保护。 |

实现：[BackendSelection.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BackendSelection.java)、[CurveBackend.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/CurveBackend.java)、[RefactorAdapter.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RefactorAdapter.java)、[TraditionalAdapter.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/TraditionalAdapter.java)、[TraditionalHooks.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/TraditionalHooks.java)、[MemoryLifecycle.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/MemoryLifecycle.java)、[PersistentMemory.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/PersistentMemory.java)、[MemoryPersistence.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/MemoryPersistence.java)、[SavedCurve.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SavedCurve.java)、[RefactorProfiles.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RefactorProfiles.java)、[RefactorCoefficientsAdapter.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RefactorCoefficientsAdapter.java)、[PhysicalCurveCalibration.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/PhysicalCurveCalibration.java)、[ThemeCurveOptions.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ThemeCurveOptions.java)、[ThemeMemoryStore.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ThemeMemoryStore.java)。

证据：`curve_backend`、`physical_mapper_class`、`baseline_id`、`user_serial`、`curve_route`、`physical_baseline_route`、`physical_calibration_min_nit`、`physical_calibration_max_nit`、`physical_original_min_nit`、`physical_original_max_nit`、`physical_clamped_points`、`theme_curve_enabled`、`theme_curve_dark`、`base_curve_name`、`theme_memory_mode`、`theme_curve_dark`。

退出 / 回滚：关闭 / 故障 / OTA 基准或用户变化恢复本次应用的曲线；只按所选保持范围恢复匹配记忆。 系数路线事务快照同时保存数组内容与身份；拒绝其他所有者替换的基线，未验证的虚拟拟合参考点不冒充可移植用户锚点。 转存前校验失败不调用配置应用，不清除当前手动点；精度归一不修改原始状态快照或其他已保存设置。 新分支中性参数保持原配置对象，编辑只提交有效标定范围内的节点；停用恢复未裁剪原始对象，元数据/记忆清理及失败回滚沿用旧事务。；启用独立曲线后真实主题切换在显示Handler事务内撤销待保存/恢复、清除活跃与归档记忆；永久保持不能复活旧主题记忆。关闭功能不删除两条基础配置。

验证：[refactor_traditional.py](../../tests/refactor_traditional.py)、[refactor_curve_integrity.py](../../tests/refactor_curve_integrity.py)、[refactor_configuration.py](../../tests/refactor_configuration.py)、[refactor_saved_curve_transaction.py](../../tests/refactor_saved_curve_transaction.py)、[refactor_persistent_memory.py](../../tests/refactor_persistent_memory.py)、[refactor_memory_lifecycle.py](../../tests/refactor_memory_lifecycle.py)、[refactor_coefficient_curve.py](../../tests/refactor_coefficient_curve.py)、[refactor_coefficient_firmware.py](../../tests/refactor_coefficient_firmware.py)、[refactor_saved_curve.py](../../tests/refactor_saved_curve.py)、[refactor_physical_calibration.py](../../tests/refactor_physical_calibration.py)、[refactor_physical_calibration_firmware.py](../../tests/refactor_physical_calibration_firmware.py)、[refactor_theme_curve.py](../../tests/refactor_theme_curve.py)。

## 主光感 · main_input

功能：`main_sensor`。

选择：优先保留当前默认逻辑屏 ABC 的 TYPE_LIGHT；新增 ABC 已直接选中的 xiaomi.sensor.light_secondary 非唤醒、按变化、无权限且量程 / 精度有效的照度源。不枚举，不按私有类型编号或机型选择，不将原始 / 工厂 / 流式通道当作 lux。 配置槽非标准且 OEM 开启 FOV 时，核验其默认屏标识、真实 FOV 对象和 Sensor→Sensor 选择器结果，仅使用选择器实际返回的源；虚拟 FOV 的 values[0] 已为 lux，确认驾驶数据包才读取 values[2] 并用原生倍率。优先接口残缺 / 抛错拒绝，不回落物理槽；标准 TYPE_LIGHT 优先原路径，不受新 FOV 判定影响。 当前未接入辅助源时独立沿用已确认主光感，状态读取 ABC 已接受照度；不伪造辅助样本，也不把未接入等同没有硬件。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `oem_main` · 系统标准主光感 | 读取当前主屏 ABC 选用的 TYPE_LIGHT 光感，沿用原适配。 |
| `oem_selected_secondary` · 系统选用的折叠光感 | 当前主屏 ABC 直接选用已验证的私有照度光感；拒绝原始、工厂、流式及其他屏幕候选。 |
| `oem_selected_fov` · 系统 FOV 融合光感 | 沿用主屏原生光感选择器返回的 FOV 源和照度解析；不混用 ABC 保留的物理光感槽。 |

实现：[RuntimeFeatureChecks.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RuntimeFeatureChecks.java)、[HookRuntime.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookRuntime.java)、[SensorState.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SensorState.java)、[BrightnessSensorAccess.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessSensorAccess.java)。

证据：`sensor_status`、`sensor_sampling_enabled`、`ambient_lux_valid`、`main_fast_lux`、`main_input_compatibility`、`assist_source_present`、`main_reading_source`。

退出 / 回滚：只读观察，不拥有 OEM 监听。

验证：[refactor_feature_self_check.py](../../tests/refactor_feature_self_check.py)、[refactor_status_presentation.py](../../tests/refactor_status_presentation.py)、[refactor_brightness_control.py](../../tests/refactor_brightness_control.py)、[refactor_fold_sensor_firmware.py](../../tests/refactor_fold_sensor_firmware.py)、[refactor_single_sensor_firmware.py](../../tests/refactor_single_sensor_firmware.py)。

## 辅助光感 · assist_input

功能：`assist_sensor`。

选择：原生有效槽优先；空槽才考虑唯一且类型 / 单位 / 权限 / 线程 / 主屏均合格的移植候选，并同步当前 ABC 构造缓存。 已确认本次辅助槽为空时显示未接入，不把初始化未知或缺读数当作硬件不存在；保留补位条件和旧路线。 保存时只对同设备 / 用户 / 基准、原已开启且参数未改的辅助选择使用独立验证副本，原始选项和能力不变；新开启或改不支持参数仍拒绝。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `native_auxiliary` · 系统原生辅助光感 | 原生辅助槽已有传感器时优先使用，继续沿用系统采样和融合。 |
| `ported_auxiliary` · 移植系统辅助补位 | 原生槽为空；唯一 zte.sensor.light_second 候选通过类型、单位、权限、线程及主屏检查后，补入 OEM 槽并同步双光感能力缓存。 |

实现：[AssistSensorCompat.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AssistSensorCompat.java)、[AuxiliarySensorIdentity.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AuxiliarySensorIdentity.java)、[RuntimeFeatureChecks.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RuntimeFeatureChecks.java)、[ConfigurationCompatibility.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ConfigurationCompatibility.java)、[RootControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RootControl.java)。

证据：`assist_compatibility.binding_kind`、`assist_compatibility.state`、`assist_compatibility.sensor_string_type`。

退出 / 回滚：补位失败回滚传感器槽、启用状态和本次能力缓存；其他所有者已替换时不覆盖。

验证：[refactor_assist_compat.py](../../tests/refactor_assist_compat.py)、[refactor_assist_compat_firmware.py](../../tests/refactor_assist_compat_firmware.py)、[refactor_feature_self_check.py](../../tests/refactor_feature_self_check.py)。

## 主辅确认与暗光策略 · response

功能：`response`、`small_response`、`low_light`、`low_threshold`、`threshold_override`、`assist_gate`、`assist_override`。

选择：完整驾驶接口优先；没有任何驾驶残片且完整旧场景 ABI 才允许无驾驶路径；残缺 / 类型错误 / 调用失败不降级。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `native_driving` · 原生确认与夜间驾驶保护 | 使用已验证的主辅光感确认和阈值；保留原生夜间驾驶判断及附加等待。 |
| `native_without_driving` · 传统确认与场景保护 | 完整旧场景结构没有夜间驾驶接口；使用原生确认窗口，保留步行、遮挡、HDR 等判断，不额外采样。 |

实现：[ResponseTuning.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ResponseTuning.java)、[LowLightTuning.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/LowLightTuning.java)、[AdvancedTuning.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AdvancedTuning.java)、[LowLightAssistEvidence.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/LowLightAssistEvidence.java)。

证据：`response_scene_profile`。

退出 / 回滚：只改变已验证期限 / 阈值；停用释放闸门、恢复本次缓存及动画参数，不重置 OEM 环形缓冲。

验证：[refactor_advanced_hooks.py](../../tests/refactor_advanced_hooks.py)、[refactor_os3_compatibility.py](../../tests/refactor_os3_compatibility.py)、[refactor_advanced_firmware.py](../../tests/refactor_advanced_firmware.py)。

## 原生亮度过渡 · animation

功能：`animation_override`。

选择：原 Refactor 双时长对象完整时沿用 oem_animation；仅原生 Refactor 策略明确关闭、两时长槽为空且主屏双 RampAnimator / RampRateController / 原生原因记录完整时采用 traditional_ramp。读取原生自动亮度原因常量，仅普通慢速自动过渡可调；保留 HDR、用户、主屏、手动确认及下游时长上限。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `oem_animation` · Refactor 原生时长过渡 | 调整已验证的原生过渡时长倍率，保持系统动画及主副屏隔离。 |
| `traditional_ramp` · 传统自动亮度速率过渡 | 原生 Refactor 策略关闭且时长对象为空；匹配主屏双 RampAnimator、三参或四参目标入口及速率原因，只调整普通自动亮度速率，保留系统时长上限及特殊过渡。 |

实现：[AdvancedTuning.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AdvancedTuning.java)、[TraditionalAnimation.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/TraditionalAnimation.java)。

证据：`animation_override_supported`、`animation_compatibility_profile`。

退出 / 回滚：旧时长和传统速率均只恢复仍等于本次应用值的字段；原生限时裁剪和后来系统更新优先，目标与帧调度不改。

验证：[refactor_advanced_hooks.py](../../tests/refactor_advanced_hooks.py)、[refactor_traditional_animation.py](../../tests/refactor_traditional_animation.py)、[refactor_traditional_animation_firmware.py](../../tests/refactor_traditional_animation_firmware.py)。

## 手动阳光屏 · sunlight

功能：`sunlight_override`。

选择：完整 SunlightController 及已验证时间语义，仅手动模式阳光屏；不与自动 HBM 混用。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `manual_sunlight` · 系统手动模式阳光屏 | 调整 SunlightController 的进入 / 退出等待，不代替自动 HBM。 |

实现：[AdvancedTuning.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AdvancedTuning.java)。

证据：`sunlight_override_supported`。

退出 / 回滚：返回原生期限；不写传感器数据。

验证：[refactor_advanced_hooks.py](../../tests/refactor_advanced_hooks.py)、[refactor_scene_hooks.py](../../tests/refactor_scene_hooks.py)。

## 触摸遮挡释放 · touch

功能：`touch_override`。

选择：核验触摸保护消费者及 debounce 字段；保留普通资格和遮挡保护。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `touch_release` · 系统触摸遮挡释放等待 | 调整 TouchCoverProtectionHelper 的移开等待，继续保留遮挡保护。 |

实现：[AdvancedTuning.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AdvancedTuning.java)、[FieldOverride.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/FieldOverride.java)。

证据：`touch_override_supported`。

退出 / 回滚：每次调用后恢复临时字段，包括原生异常。 临时字段统一由FieldOverride按当前值归属逆序恢复；系统在调用期间更新后不回写旧值。

验证：[refactor_advanced_hooks.py](../../tests/refactor_advanced_hooks.py)、[refactor_review_safety.py](../../tests/refactor_review_safety.py)。

## 原生场景参与 · scenes

功能：`scene_night_driving`、`scene_step`、`scene_touch`、`scene_proximity`、`scene_stream_proximity`、`scene_reflection`、`scene_night_wake`、`scene_manual_sunlight`、`scene_game`。

选择：各场景独立实际消费者；普通 / 流式距离分别检测。OEM 未启用普通距离时报告 inactive，不强开传感器；只能约束现有场景。

组合关系：`per_feature`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `oem_night_driving` · 系统夜间驾驶策略 | 匹配本机该场景的原生消费者，仅限制参与条件，不强制进入场景。 |
| `oem_step` · 系统步行延迟策略 | 使用 ABC 原生步行额外降亮确认接口。 |
| `oem_touch` · 系统触摸遮挡策略 | 使用 TouchCoverProtectionHelper 的实时遮挡判断。 |
| `oem_proximity` · 普通距离策略 | 匹配 dropAmbientLuxIfNeeded 和普通距离开关；系统未开启时不强制启动。流式距离是另一项独立策略，不混用设置。 |
| `oem_stream_proximity` · 流式距离策略 | 匹配 SceneDetector 的流式确认与跳过亮度变化接口；与普通距离分别设置。 |
| `oem_reflection` · 系统反射 / AON 策略 | 匹配本机该场景的原生消费者，仅限制参与条件，不强制进入场景。 |
| `oem_night_wake` · 系统夜间唤醒策略 | 使用 ABC 的 updateNightWakeMode，不伪造夜间唤醒条件。 |
| `oem_manual_sunlight` · 系统手动阳光屏策略 | 使用 SunlightController 原生状态与条件。 |
| `oem_game` · 系统游戏防误降亮策略 | 匹配本机该场景的原生消费者，仅限制参与条件，不强制进入场景。 |

实现：[SceneTuning.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SceneTuning.java)、[SceneController.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SceneController.java)、[SceneCatalog.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SceneCatalog.java)、[FieldOverride.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/FieldOverride.java)。

证据：`system_scene`、`scene_options`、`feature_self_check`。

退出 / 回滚：临时场景字段按调用恢复；停止取消本次确认，恢复自己的步行 / 夜间设置。 临时字段统一由FieldOverride按当前值归属逆序恢复；系统在调用期间更新后不回写旧值。

验证：[refactor_scenes.py](../../tests/refactor_scenes.py)、[refactor_scene_hooks.py](../../tests/refactor_scene_hooks.py)、[refactor_scene_settings.py](../../tests/refactor_scene_settings.py)、[refactor_scene_firmware.py](../../tests/refactor_scene_firmware.py)、[refactor_review_safety.py](../../tests/refactor_review_safety.py)。

## 暗光锁定 · dark_lock

功能：`dark_lock`。

选择：新配置默认关闭；用户明确启用后，ABC SensorManager / 实际句柄识别；退出低频确认独立于 OEM 辅助采样；需接管 / 恢复模式权限。 绑定 ABC、双光感策略、SensorManager、实际句柄及可读物理屏身份；私有折叠主光感直出 lux；FOV 覆盖按真实选择器、驾驶状态及倍率解析，不对已换算照度重复放大。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `abc_sensor_manager` · 自动亮度控制器的光感服务 | 复用本机 ABC 的 SensorManager；按传感器类型和句柄识别，锁定期间低频确认退出条件。 |

实现：[BrightnessControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java)、[DarkLockPolicy.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/DarkLockPolicy.java)、[BrightnessSensorAccess.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessSensorAccess.java)。

证据：`brightness_control.owner`、`brightness_control.watch_main_lux`、`brightness_control.watch_assist_lux`、`brightness_control.watch_binding_active`、`brightness_control.watch_physical_display_id`、`brightness_control.watch_main_profile`。

退出 / 回滚：锁屏 / 熄屏 / 切用户 / 停用注销模块监听，只有模块取得的自动状态可恢复。 光感、管理器、控制器、倍率、面板配置或物理屏变化时释放旧监听和已拥有的锁定，清空采样及确认计时；代际监听拒绝旧队列回调。状态查看只读。

验证：[refactor_brightness_control.py](../../tests/refactor_brightness_control.py)、[refactor_display_scheduling.py](../../tests/refactor_display_scheduling.py)。

## 暂停自动亮度名单 · applications

功能：`apps`。

选择：完整可见任务接口优先；原生焦点事件为必要前提，不支持多窗口时明确焦点回退；禁止把运行进程当可见任务。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `visible_tasks` · 可见任务路径（含小窗 / 分屏） | 可见任务接口完整；任一名单应用仍可见时保持暂停。 |
| `focus_fallback` · 前台焦点回退路径 | 没有完整的可见任务能力时按系统焦点判断，并提示小窗限制。 |

实现：[ForegroundApps.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ForegroundApps.java)、[BrightnessControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java)、[ApplicationBrightnessSettings.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ApplicationBrightnessSettings.java)。

证据：`brightness_control.app_multiwindow_supported`。

退出 / 回滚：按代次丢弃过期查询；停用 / 熄屏解除订阅，惰性线程空闲退出，名单设置保留。

验证：[refactor_brightness_control.py](../../tests/refactor_brightness_control.py)、[refactor_foreground_firmware.py](../../tests/refactor_foreground_firmware.py)。

## HBM 控制器与计时 · hbm

功能：`hbm`。

选择：控制器与计时为两个独立维度：启用字段优先；缺字段且完整旧结构才用原生能力方法。合法有限计时 / 三零计时互斥；不能制造预算。

组合关系：`two_dimensions`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `enabled_field` · 控制器：原生启用字段 | 读取 mHbmControllerIsEnabled；存在此字段时始终优先，不回退绕过错误。 |
| `native_support_method` · 控制器：原生能力方法 | 没有启用字段且旧结构完整；调用 deviceSupportsHbm 判断实际支持，继续保留照度、时间与省电条件。 |
| `timed_budget` · HBM 照度＋时间预算 | 时间窗口、最大时长与最短时长均通过预算校验；复制本机数据后调整。 |
| `ambient_only` · HBM 仅照度门槛（原生零计时） | 三个时间字段全部为零且接口完整；只调整照度，保留三个零计时值。 |

实现：[HbmAccess.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HbmAccess.java)、[OutdoorController.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorController.java)。

证据：`outdoor.hbm_controller_profile`、`outdoor.hbm_tuning_profile`。

退出 / 回滚：克隆 HBM 数据，不改共享配置；只在字段仍指向本次 clone 时恢复原对象，系统替换优先。

验证：[refactor_outdoor.py](../../tests/refactor_outdoor.py)、[refactor_os3_compatibility.py](../../tests/refactor_os3_compatibility.py)。

## 户外增强目标 · outdoor

功能：`outdoor`。

选择：覆盖目标入口优先；明确缺入口且旧调用所有权完整时走传统自定义目标，残缺覆盖接口不回退。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `override_target` · 系统覆盖目标入口 | 通过 getOverrideLimitBrightness 增强自动目标，保留原生手动接管与下游保护。 |
| `legacy_custom_target` · 传统物理曲线目标入口 | 没有覆盖目标接口且旧调用结构完整；在原生自定义曲线目标返回后增强，仍经场景否决、范围裁剪及温控输出。 |

实现：[OutdoorTuning.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorTuning.java)、[OutdoorController.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorController.java)。

证据：`outdoor.target_compatibility_profile`。

退出 / 回滚：必需 Hook 成组事务安装，失败整组撤销；可选范围 / 跟踪失败独立，不撤销已经提交的核心。

验证：[refactor_outdoor.py](../../tests/refactor_outdoor.py)、[refactor_os3_compatibility.py](../../tests/refactor_os3_compatibility.py)、[refactor_hook_transactions.py](../../tests/refactor_hook_transactions.py)。

## 高亮范围 · range

功能：`outdoor_range`。

选择：HBM getter 与峰值基础范围；只有本机实际启用的动态范围对象才补充，不同时添加第二个目标增强器。

组合关系：`additive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `hbm_range` · HBM 与峰值范围 | 实际 HBM 最大亮度与峰值范围接口均已接入，仍受原生允许条件限制。 |
| `dynamic_range` · 照度动态范围补充 | 额外匹配 BrightnessRangeControllerImpl；仅该设备实际使用动态范围时参与。 |

实现：[OutdoorTuning.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorTuning.java)、[OutdoorController.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorController.java)。

证据：`outdoor.dynamic_range_supported`、`outdoor.dynamic_range_enabled`。

退出 / 回滚：按本次增强会话退出，范围 getter 恢复原生返回；下游保护仍有效。

验证：[refactor_outdoor.py](../../tests/refactor_outdoor.py)、[refactor_hook_transactions.py](../../tests/refactor_hook_transactions.py)。

## 灰阶放宽与物理 nit 限制 · sdr

功能：`opr`、`nit_limit`。

选择：完整隐私判断优先；完全没有隐私 / UHBM 残片且旧 OPR 完整才用传统路径。实际物理换算用于 nit 上限。调用失败不降级。 OPR额外放宽独立读取完整原生灰阶/nit包络，最多到该表最高档；不存在整组字段时保留旧映射路线，部分缺失/坏表/换算失败保留原生限亮。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `privacy_aware` · 带隐私显示判断的新式 SDR 路径 | 匹配 shouldUsePrivacyOprBrightness，保留隐私显示、HDR、温控与电量判断；额外放宽遵守完整原生OPR表的最高档。 |
| `legacy_opr` · 传统 OPR / SDR 路径 | 不存在隐私显示分支且旧版灰阶 / nit 接口完整；不将残缺的新式接口误当旧接口。 |

实现：[OutdoorSdrScope.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorSdrScope.java)、[OutdoorOpr.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorOpr.java)、[NitBrightnessLimit.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/NitBrightnessLimit.java)、[OutdoorOprEnvelope.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorOprEnvelope.java)。

证据：`outdoor.sdr_compatibility_profile`、`outdoor.sdr_compatibility_detail`、`outdoor.opr_envelope_profile`、`outdoor.opr_envelope_max_nit`、`outdoor.opr_envelope_max_brightness`、`outdoor.opr_envelope_detail`。

退出 / 回滚：ThreadLocal 帧按调用恢复；只移除本次引入的 OPR 原因位；温控、电量、HDR 与系统接管继续优先。

验证：[refactor_outdoor_compatibility.py](../../tests/refactor_outdoor_compatibility.py)、[refactor_outdoor_compatibility_firmware.py](../../tests/refactor_outdoor_compatibility_firmware.py)、[refactor_opr_envelope.py](../../tests/refactor_opr_envelope.py)、[refactor_opr_envelope_firmware.py](../../tests/refactor_opr_envelope_firmware.py)。

## 显示层温控 · thermal

功能：`thermal`。

选择：显示层 HDR / SDR 消费者完整，严重温控与电池门槛继续有效；不改 SoC / 充电温控。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `display_thermal` · 显示层温控接口 | 仅调整显示亮度限制；严重温控、电池温度门槛及其他系统温控继续有效。 |

实现：[HookEntry.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookEntry.java)、[ThermalPolicy.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ThermalPolicy.java)、[HookRuntime.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookRuntime.java)、[FieldOverride.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/FieldOverride.java)。

证据：`thermal_supported`、`thermal_skipped`。

退出 / 回滚：每次调用恢复暂存显示限亮字段，停止恢复系统控制。 临时字段统一由FieldOverride按当前值归属逆序恢复；系统在调用期间更新后不回写旧值。

验证：[refactor_advanced_hooks.py](../../tests/refactor_advanced_hooks.py)、[refactor_hook_firmware.py](../../tests/refactor_hook_firmware.py)、[refactor_review_safety.py](../../tests/refactor_review_safety.py)。

## 主屏输出隔离 · raw_output

功能：`manual_panel`、`node_output`。

选择：主屏输出 / 电源状态对象完整；租约、解锁、模式、屏幕状态均通过才隔离正亮度帧，第一帧留给系统。 户外协作独立匹配原生策略或完整旧自动对象 / 双原因，原手动面板检查不代替此检查。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `raw_primary_output` · 原生策略节点协作 | 优先使用实际输出策略，普通自动输出到顶后协作，保留原生保护。 |
| `automatic_strategy2_node` · 独立自动对象与原因协作 | 仅原生策略入口缺失时核验独立自动对象、同屏归属和双份亮度原因，不替换手动面板路径。 |

实现：[RawPanelOutputHooks.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RawPanelOutputHooks.java)、[BrightnessControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java)、[OutdoorNodeBridge.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorNodeBridge.java)、[RawPanelLease.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RawPanelLease.java)、[OutdoorOutputAccess.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorOutputAccess.java)。

证据：`brightness_control.owner`、`outdoor_node_supported`、`outdoor_output_profile`、`outdoor_node_physical_id`。

退出 / 回滚：熄屏先撤销控制权，租约失联自动归还，系统改写优先。

验证：[refactor_raw_output.py](../../tests/refactor_raw_output.py)、[refactor_raw_panel_transaction.py](../../tests/refactor_raw_panel_transaction.py)、[refactor_outdoor_node.py](../../tests/refactor_outdoor_node.py)、[refactor_outdoor_fold.py](../../tests/refactor_outdoor_fold.py)。

## 真实主屏节点 · node

功能：`main_panel_node`。

选择：backlight 或明确标识显示的 leds；真实 sysfs 路径与上限配对且唯一主屏，不按机型写死上限。 新增手动面板折叠路线：实际原生布局至少两个不同默认物理屏；实时默认内部屏地址与布局、成对同控制器背光、唯一 DSI 亮屏端口一致，选当前端口，扫描后再确认物理屏未变化。无切换布局证据时原发现路径不变；有折叠证据但不完整则等待。 兼容实际 Physical / StablePhysical 地址对象，均直接调用原生 getPort；StablePhysical 的物理 ID 与端口独立，按内核成对 panelN 清单验证，不解码该 ID。 状态读数单独只读实际节点；可验证本机DisplayDeviceConfig背光标定时换算nit，不可换算时原生接管仅显示节点值，不复用框架旧亮度。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `backlight_node` · 主屏 backlight 节点 | 扫描 backlight，验证主屏身份、真实路径及 brightness / max_brightness 配对。 |
| `display_led_node` · 显示专用 leds 节点 | 仅接受明确标识为主屏背光的 leds 节点，排除通知灯、手电与背屏。 |
| `fold_default_display` · 折叠屏当前主屏节点 | 原生折叠布局允许不同物理屏作为默认屏；根据实时默认屏地址、成对背光与唯一点亮 DSI 选择节点；显示端口与内核编号不一致时独立核验默认屏点亮状态，切屏释放旧接管。 |

实现：[PanelNodeDiscovery.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/PanelNodeDiscovery.java)、[NativePanelRoot.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/NativePanelRoot.java)、[ActivePanelNode.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ActivePanelNode.java)、[ScreenBrightnessReading.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ScreenBrightnessReading.java)。

证据：`main_panel_node.node_class`、`main_panel_node.node_path`、`main_panel_node.node_maximum`、`main_panel_node.node_discovery`、`main_panel_node.node_physical_display_id`、`main_panel_node.node_address_profile`、`main_panel_node.node_port_source`、`main_panel_node.node_detail`、`actual_nit_source`、`actual_node_source`、`screen_nit_calibration.route`、`brightness_read_error`。

退出 / 回滚：发现与权限检查只读，不启动写入器；实际租约写入另由原生事务负责，拒绝副屏 / 指示灯 / 手电。 折叠专用原生选项与 v3 权限恢复记录仅允许绑定端口节点；v2 / 旧节点拒绝规则不变。关闭、熄屏、DRM 改变结束旧接管；租约和框架输出隔离绑定物理屏，晚到命令拒绝，不复用旧目标。户外新增显式折叠协作分支，使用独立物理租约和原生 DSI 核验；隐藏映射仍不启用此分支。 读数不启动写入器、不修改节点或权限；物理屏或标定不一致时不显示旧nit。

验证：[refactor_panel_nodes.py](../../tests/refactor_panel_nodes.py)、[refactor_panel_access.py](../../tests/refactor_panel_access.py)、[refactor_fold_panel.py](../../tests/refactor_fold_panel.py)、[refactor_screen_brightness.py](../../tests/refactor_screen_brightness.py)。

## Root 存储与原生工具 · root_tools

功能：`root_storage`、`stored_config`、`outdoor_node_device`、`native_payload`、`root_identity`、`root_settings`、`root_process_identity`。

选择：逐项检查私有存储、原始配置、当前 APK 资产和组合接口；不试写亮度，不擅自启动服务。 Root设置三入口互斥，原外部Provider优先；只读探测拒绝时尝试权限等价的cmd settings/settings CLI，不绕过系统权限。写入失败不重放，UID0不等于全权限。私有目录严格身份检查后才回退，原生节点不换目录。

组合关系：`per_feature`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `root_private_storage` · Root 私有数据目录 | 只检查配置 / 分析目录的读写能力，不安装或启动输出程序。 |
| `stored_json` · 原始配置 JSON | 读取原始配置；自检与兼容裁剪不会将默认值写回用户设置。 |
| `primary_node_profile` · 实际主屏节点与框架输出组合 | 按实际节点上限、曲线映射和输出能力核验，不按机型名单放行。 |
| `apk_native_asset` · APK 内原生程序校验 | 核对当前 APK 的主屏程序资产完整性，不启动守护、不试写亮度。 |
| `fold_bound_outdoor` · 折叠主屏节点协作绑定 | 当前物理主屏、原生布局和背光配对完整；用独立物理租约核验切屏，不跨屏继承目标。 |
| `root_temporary_storage` · Root 独立操作目录 | 原目录受限时使用受验证的私有目录；不迁移原生输出程序或用户设置。 |
| `root_actual_identity` · 实际进程权限信息 | 记录实际UID、Capabilities及SELinux上下文，不以Root管理器名称判定权限。 |
| `external_provider` · 原生设置接口 | 优先使用原有设置服务；写入仍需系统授权与读回确认。 |
| `cmd_settings` · 系统设置命令服务 | 原入口不可读时核验cmd设置服务；不重放失败的写入。 |
| `settings_cli` · 系统设置命令入口 | 前两条不可读时核验系统settings入口；仍遵守系统权限。 |
| `root_proc_identity` · 系统进程有效身份 | 核验当前系统进程、启动标识和构建；权限不足时不跳过校验。 |

实现：[RootControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RootControl.java)、[RootFeatureChecks.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RootFeatureChecks.java)、[NativePanelAsset.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/NativePanelAsset.java)、[OutdoorNodeRoot.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorNodeRoot.java)、[RootSettings.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RootSettings.java)、[RootWorkspace.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RootWorkspace.java)、[RootCommand.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RootCommand.java)、[RootLaunch.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RootLaunch.java)、[RootAccess.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RootAccess.java)、[AppRootDiagnostics.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AppRootDiagnostics.java)。

证据：`feature_self_check`、`root_feature_self_check`、`outdoor_node_device_profile`、`outdoor_node_guard.write_attempts`、`outdoor_node_guard.write_errno`、`outdoor_node_guard.write_stage`、`root_access`、`settings_route`、`settings_attempts`、`workspace_route`、`workspace_attempts`。

退出 / 回滚：短命 Root 桥和有界 IPC；诊断不写回用户配置，失败只禁本项。 节点写入错误保留自动停用锁存；只有用户主动保存并获引擎确认后，才经过原有完整校验重试启动，不重置用户设置。 Root自检只读；启动初始化操作目录。已确认核心与可选节点工具分别报告，节点失败不回滚已确认曲线。导出失败转APP Downloads并明确标记非实时不完整包。 自检显示选中目录当前失败原因和各备用目录只读结果。

验证：[refactor_feature_self_check.py](../../tests/refactor_feature_self_check.py)、[refactor_native_asset.py](../../tests/refactor_native_asset.py)、[refactor_outdoor_node_settings.py](../../tests/refactor_outdoor_node_settings.py)、[refactor_outdoor_fold.py](../../tests/refactor_outdoor_fold.py)、[refactor_outdoor_write_retry.py](../../tests/refactor_outdoor_write_retry.py)、[refactor_root_access.py](../../tests/refactor_root_access.py)。

## APP 本地工具 · app_tools

功能：`launcher`、`preferences`、`app_files`。

选择：独立 Launcher alias / 原偏好存储 / 私有文件权限；不使用启动组件名重新建立偏好。

组合关系：`per_feature`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `launcher_alias` · 桌面入口别名 | 使用 PackageManager 的独立桌面别名，保留模块设置入口和欢迎引导完成状态。 |
| `app_preferences` · 应用本地偏好存储 | 读取设置草稿与欢迎记录，不清空、不改变开关。 |
| `app_files` · 应用私有文件目录 | 检查配置导入导出所用目录的可访问性。 |

实现：[MainActivity.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/MainActivity.java)、[LauncherVisibility.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/LauncherVisibility.java)、[UiPreferences.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/UiPreferences.java)、[NavigationPolicy.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/NavigationPolicy.java)、[FloatingNavigation.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/FloatingNavigation.java)、[ImmersiveLayout.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ImmersiveLayout.java)、[SettingsSearchIndex.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SettingsSearchIndex.java)、[SettingsSearch.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SettingsSearch.java)、[LogCompatibilityState.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/LogCompatibilityState.java)、[LogCompatibilityPanel.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/LogCompatibilityPanel.java)、[ScrollElasticity.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ScrollElasticity.java)、[ElasticScrollView.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ElasticScrollView.java)、[DiagnosticShareFiles.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/DiagnosticShareFiles.java)、[DiagnosticShareProvider.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/DiagnosticShareProvider.java)、[DiagnosticSharing.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/DiagnosticSharing.java)、[AboutMarquee.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AboutMarquee.java)、[AboutHeroView.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AboutHeroView.java)、[AboutPage.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AboutPage.java)。

证据：`APP.appFeatureReport`、`APP.settings_search_query`、`APP.independent_log_connection_stages`。

退出 / 回滚：别名切换保留设置、欢迎记录；APP 销毁取消 UI 回调。 悬浮底栏只响应设置/关于的实际滚动手势，离开时结束动画；搜索只筛选和跳转，不改配置或调用Root；日志兼容分区只消费既有报告，不探测接口。 越界回弹只平移滚动内容，归位不产生滚动事件；隐藏/尺寸变化/程序定位/移除时取消。诊断分享只授予单个压缩包临时只读权限，分享失败不重做导出或改动原包。 关于图标容器的弹幕只在前台、当前页且位于滚动视口内时推进；切页、教程、隐藏、销毁及关闭动画时停止，不建立后台服务。

验证：[refactor_ui_preferences.py](../../tests/refactor_ui_preferences.py)、[refactor_app_preferences.py](../../tests/refactor_app_preferences.py)、[refactor_welcome.py](../../tests/refactor_welcome.py)、[refactor_floating_search.py](../../tests/refactor_floating_search.py)、[refactor_log_summary.py](../../tests/refactor_log_summary.py)、[refactor_scroll_elasticity.py](../../tests/refactor_scroll_elasticity.py)、[refactor_diagnostic_sharing.py](../../tests/refactor_diagnostic_sharing.py)、[refactor_about_page.py](../../tests/refactor_about_page.py)。

## 控制中心节点映射 · control_center

功能：`control_center_slider`、`control_center_raw`、`control_center_partition`。

选择：原mode=0/1/2保持原义：全区间严格单调坐标优先；端点平台仅在实际标定数组、原生双向样条和端点裁剪证明完整时使用有效域，逻辑101点表独立核验。独立mode=3/raw_node_v3复用标准坐标进行回显，并核验主屏输出隔离、物理ID、Root实际可写节点及监听进程；不按机型或版本放行，未知动态折叠监听不套用。只有显式拖动才接管完整节点，不在升级时迁移旧选择。 mode=4追加分区路线，读取DDC原始HBM transitionPoint或控制器普通亮度上限，在真实标定中转成节点比例；未知或无额外区间拒绝分区，不影响完整节点控制。 分区呈现普通75%/增强25%；普通物理边界仍读原机接口，独立上界默认真实max_brightness。配置上界只约束用户节点目标，不修改自动HBM范围。

组合关系：`per_feature`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `coordinate_v2` · 双向设备坐标映射 | 使用本机亮度到背光的转换表；可可靠单调反解才启用。保留自动亮度、手动记忆与暗光锁定的原有控制权。 |
| `coordinate_v2_calibrated` · 标定有效区间映射 | 仅完整原生标定与双向样条一致、端点裁剪和有效区间可反解时，排除最低或最高端的平台；中段异常及额外低亮度标定不套用。 |
| `raw_node_v3` · 完整主屏节点与手动保持 | 独立选择开启；按实际 max_brightness 比例下发节点目标。拖动退出暗光锁定并关闭自动亮度，复用主屏节点租约；重开自动亮度立即结束。需要实际可写节点与系统界面接口；未知折叠监听布局不套用。 |
| `partition_node_v4` · 普通亮度与节点分区控制 | 使用本机普通亮度边界划分区间；普通区间保留自动亮度与原学习。到达边界松手后，三秒内再次向上滑动才使用共用节点保持；返回普通区间可恢复自动或保持手动。 |
| `partition_ddc_hbm` · 原机配置普通上限 | 优先读取原始HBM过渡点，按本机标定换算普通区间；类型或数据错误不降级绕过。 |
| `partition_hbm_normal` · 控制器普通上限 | 原机配置没有该入口或合法无HBM数据时，读取实际普通亮度上限；不按固定比例猜测。 |

实现：[ControlCenterCalibration.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterCalibration.java)、[ControlCenterEngine.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterEngine.java)、[ControlCenterHooks.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterHooks.java)、[ControlCenterRoot.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterRoot.java)、[BrightnessControlOptions.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControlOptions.java)、[BrightnessControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java)、[ControlCenterCurve.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterCurve.java)、[ControlCenterNodes.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterNodes.java)、[ControlCenterPartition.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterPartition.java)、[ControlCenterDemo.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterDemo.java)、[ControlCenterDemoState.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterDemoState.java)、[ControlCenterSettings.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterSettings.java)、[ControlCenterStatus.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterStatus.java)。

证据：`control_center_slider.kind`、`control_center_slider.error`、`hyperlux_control_center_tables_v2.enabled`、`hyperlux_control_center_tables_v2.revision`、`hyperlux_control_center_tables_v2.user_serial`、`control_center_slider.calibration_profile`、`control_center_slider.compatibility_state`、`control_center_slider.oem_v1_ready`、`control_center_raw_supported`、`control_center_slider.raw_ready`、`control_center_slider.raw_control`、`control_center_node_detail`、`control_center_partition_supported`、`control_center_slider.partition`、`control_center_slider.partition_error`、`hyperlux_control_center_tables_v2.partition_maximum`。

退出 / 回滚：旧坐标路线不拥有模式/输出；停用、用户、配置、进程或对象变化撤销旧profile。mode=3拖动经既有raw_panel退出暗光锁定并关闭自动亮度，共用原生程序/租约/健康检查/锁屏暂停和解锁恢复；重开自动亮度清租约，auto_epoch拒绝此前排队手势。同节点且只更新其他设置时保留保持；关闭引擎复用原始所有权恢复。Root暂缺保留原配置，不阻止保存其他设置；不清理其他弹窗的会话。 普通段调用原系统回调以保留开启中的自动亮度和学习，超出段共用raw_panel且不学习；三秒内需先结束首笔再上滑。返回普通段按独立保存选项恢复自动或继续手动，超时/代次/范围/自动模式变化清确认。 首笔同步源滑块，在mExternalChange保护下setValue(position,null)封顶；系统自动反馈不进手势门禁、不写节点。自动高亮已在增强区时，明确新手势可直接接管，系统端以原FrameworkBrightness实际值和标定二次确认；失效证据不放行。动画仅当前可见设置页运行，暂停/切页/移除停止，不新增采样或守护。

验证：[refactor_control_center.py](../../tests/refactor_control_center.py)、[refactor_control_center_root.py](../../tests/refactor_control_center_root.py)、[refactor_brightness_control.py](../../tests/refactor_brightness_control.py)、[refactor_feature_self_check.py](../../tests/refactor_feature_self_check.py)、[refactor_control_center_calibration.py](../../tests/refactor_control_center_calibration.py)、[refactor_control_center_nodes.py](../../tests/refactor_control_center_nodes.py)、[refactor_control_center_partition.py](../../tests/refactor_control_center_partition.py)、[refactor_control_center_settings.py](../../tests/refactor_control_center_settings.py)、[refactor_control_center_demo.py](../../tests/refactor_control_center_demo.py)。

## 日落后暗光锁定 · dark_lock_schedule

功能：`dark_lock_schedule`。

选择：可选、默认关闭。实际 LocalServices / TwilightManager / TwilightState / Listener 签名完整，时间戳覆盖当前时刻才参与；原有全天锁定独立保留。缺数据或调用失败等待，不按型号、深色模式或固定时钟猜测。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `system_twilight` · 系统日出日落服务 | 复用实际 TwilightManager 与原生时间戳；只在夜间允许暗光锁定。缺时间等待，不以深色模式或固定钟点代替。 |

实现：[DarkLockSchedule.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/DarkLockSchedule.java)、[BrightnessControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java)。

证据：`brightness_control.dark_lock_schedule.profile`、`brightness_control.dark_lock_schedule.known`、`brightness_control.dark_lock_schedule.sunrise_ms`、`brightness_control.dark_lock_schedule.sunset_ms`、`brightness_control.dark_lock_schedule.night`、`brightness_control.reason`。

退出 / 回滚：显示 Handler 复用系统 twilight 通知与一个下次边界回调；系统时钟变化重读。熄屏 / 停用 / 切用户注销本次 Listener、取消重试与边界计时；旧代际回调无效。注册异常事务撤销，最多三次就绪补试。日出仅恢复本模块取得的自动亮度，用户手动关闭不恢复。自检只查接口，不注册监听或申请定位。

验证：[refactor_brightness_control.py](../../tests/refactor_brightness_control.py)、[refactor_feature_self_check.py](../../tests/refactor_feature_self_check.py)、[refactor_configuration.py](../../tests/refactor_configuration.py)。

## 熄屏前降亮与息屏显示协调 · dim_aod

功能：`dim_aod`。

选择：实际完整SDR入口、DIM子阶段、主屏DPC/电源请求/原因及全屏AOD策略同时匹配；不按机型或版本放行。子阶段仅在当前自动DIM调用及活动用户和显示线程内参与，缺接口或异常独立跳过，不回退改写其他阶段。

组合关系：`additive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `automatic_dim_aod_cap` · 自动降亮与息屏目标协调 | 主屏仍亮且处于自动DIM降亮时，阻止全屏AOD目标重新抬高已降低的亮度；真正息屏、背屏、手动及HDR沿用系统。 |

实现：[DimAodCompatibility.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/DimAodCompatibility.java)。

证据：`dim_aod_compatibility`、`feature_self_check`。

退出 / 回滚：只在当前同步调用中限制AOD子阶段抬高DIM输入，原生后续保护完整执行；外层调用无论正常或异常都释放嵌套上下文，安装失败撤销本组全部钩子。不改用户设置、不留节点或模式接管。

验证：[refactor_dim_aod.py](../../tests/refactor_dim_aod.py)。

## ABI 内部变体

以下是功能路线内部的字段、签名、协议或迁移选择；不应全部变成 APP 开关。相关测试表示验证入口，具体主机 / 固件 / 真机覆盖以测试输出与复审记录为准。

| 变体与实现 | 已有分支 | 选择及保护 | 相关验证 |
| --- | --- | --- | --- |
| `active_backend` · `BackendSelection.java` | `known_refactor` / `known_physical` / `unknown_flag` | 已知活动标记只等待对应路径；缺标记时按实际就绪对象选择，不按类名存在抢占。 | [refactor_traditional.py](../../tests/refactor_traditional.py) |
| `mapper_memory_coordinates` · `TraditionalAdapter.java` | `mShortTermModelUserLux` / `mUserLux_with_adjusted_spline` | 仅匹配两类已知映射器；AOSP 手动点为修正背光坐标，不能当作物理 nit。 | [refactor_traditional.py](../../tests/refactor_traditional.py) |
| `mapper_drag_signature` · `TraditionalHooks.java` | `addUserDataPoint(float,float,String)` / `addUserDataPoint(float,float)` | 完整三参数签名缺失才用二参数，返回值必须 void。 | [refactor_traditional.py](../../tests/refactor_traditional.py) |
| `inherited_mapper_members` · `TraditionalAdapter.java` | `declared_member` / `superclass_member` | 只沿同一对象真实继承链查找相同签名，非任意别名猜测。 | [refactor_traditional.py](../../tests/refactor_traditional.py) |
| `sensor_identity` · `BrightnessControl.java` | `same_object` / `same_type_and_native_handle` | 对象一致优先；包装对象只允许同类型同系统句柄，缺句柄不按名称猜配。 | [refactor_brightness_control.py](../../tests/refactor_brightness_control.py) |
| `sensor_freshness` · `BrightnessControl.java` | `on_change` / `continuous_timeout` | on-change 保留最近值，连续采样超过窗口无效；0 lux 是有效值，不伪装等待。 | [refactor_brightness_control.py](../../tests/refactor_brightness_control.py) |
| `dark_primary_only` · `RuntimeFeatureChecks.java` | `dual_policy_present` / `dual_policy_absent` | 无辅助槽或双策略不存在时保留实际主光感独立确认；真实双源继续共同确认，坏类型 / 单位拒绝。 | [refactor_brightness_control.py](../../tests/refactor_brightness_control.py) |
| `foreground_user_field` · `ForegroundApps.java` | `mUserId` / `userId` | 只在旧字段缺失时选择另一字段，类型必须为非静态 int。 | [refactor_brightness_control.py](../../tests/refactor_brightness_control.py) |
| `task_visibility_field` · `ForegroundApps.java` | `isVisibleRequested` / `isVisible` | 已请求可见优先；字段缺失才用旧可见字段，均需非静态 boolean。 | [refactor_brightness_control.py](../../tests/refactor_brightness_control.py) |
| `control_center_feedback` · `ControlCenterHooks.java` | `synthetic_updateSlider` / `direct_updateSlider` | 匹配合成 / 直接回显签名，两者属于同一坐标映射，不替换系统自动亮度策略；入口隐藏。 | [refactor_control_center.py](../../tests/refactor_control_center.py) |
| `control_center_minimum` · `ControlCenterHooks.java` | `getMin` / `zero_minimum` | 未暴露 getMin 时使用该滑块的零起点 ABI，其他量程仍从实际 getMax 读取。 | [refactor_control_center.py](../../tests/refactor_control_center.py) |
| `control_center_conversion` · `ControlCenterCalibration.java` | `standard` / `oem_v1` | 标准物理转换必需，V1 为单独校准；单调且可逆才用，不以通用曲线冒充设备曲线。 | [refactor_control_center.py](../../tests/refactor_control_center.py) |
| `root_attribution` · `RootSettings.java` | `explicit_pid` / `builder_default_pid` | 仅 setPid 方法缺失可沿用 Builder 默认；授权 / provider 错误不降级为未校验读写。 | [refactor_configuration.py](../../tests/refactor_configuration.py) |
| `state_protocol` · `StatusTransport.java` | `legacy_inline` / `schema1_chunks` | 旧单段状态继续可读；分片必须同一 snapshot，缺段只标缺失，不跨快照补齐。 | [refactor_status_transport.py](../../tests/refactor_status_transport.py) |
| `ui_preferences_migration` · `UiPreferences.java` | `stable_store` / `legacy_alias_merge_once` | 原存储优先，别名只迁移一次；保留配置、须知旧键及独立欢迎标记。 | [refactor_ui_preferences.py](../../tests/refactor_ui_preferences.py) |
| `platform_description` · `SystemVersion.java` | `system_properties` / `bounded_getprop` | 仅诊断版本描述；超时受限，不用版本状态阻止功能。 | [refactor_system_version.py](../../tests/refactor_system_version.py) |
| `module_loader_discovery` · `HookEntry.java` | `initial_loader` / `bounded_startup_loader_watch` | 启动找不到显示类才监听加载，找到后撤销，45 秒封顶；不轮询亮度。 | [refactor_hook_firmware.py](../../tests/refactor_hook_firmware.py) |
| `native_payload_refresh` · `NativePanelRoot.java` | `same_asset_hash` / `atomic_asset_replace` | 相同程序仅验证；显式节点使用时才停止旧租约并原子换程序，不因自检启动输出器。 | [refactor_native_asset.py](../../tests/refactor_native_asset.py) |
| `main_light_identity` · `BrightnessSensorAccess.java` | `standard_type_light` / `oem_selected_scalar_secondary` / `native_fov_selector` | 沿用系统当前默认逻辑屏 ABC 的真实主槽和 SensorManager；标准路径不变；私有折叠光感需精确语义身份与有效元数据，暗光监听不猜测 FOV / 非标单位。其他屏幕、同类多候选与原始通道不参与选择。 FOV 路线由已启用字段、实际 FOV 对象、同屏标识和非静态选择器返回值共同确认；正常值与驾驶替代值沿用固件消费者单位。 | [refactor_brightness_control.py](../../tests/refactor_brightness_control.py) |
| `traditional_animation_reason` · `TraditionalAnimation.java` | `native_auto_slow_no_modifier` / `native_special_or_immediate` | 只读取本机原生自动原因常量；普通慢速自动过渡且没有特殊 modifier 时调整调用速率，温控 / HDR / 手动 / 唤醒 / 即时原样保留。 | [refactor_traditional_animation.py](../../tests/refactor_traditional_animation.py) |
| `fold_physical_address` · `ActivePanelNode.java` | `physical` / `stable_physical` | 按实际默认内部屏的地址对象分支：Physical 与 StablePhysical 是同父类下的独立类型。严格校验非静态 long getPhysicalDisplayId / int getPort 与 uniqueId；StablePhysical 使用独立端口，不从 ID 低位推导。绑定布局中的默认物理 ID、成对内核 panel 端口及唯一点亮 DSI；未知地址或残缺接口等待，不将错误降级旧类型。 | [refactor_fold_panel.py](../../tests/refactor_fold_panel.py) |
| `traditional_animation_target_signature` · `TraditionalAnimation.java` | `target_with_lux` / `target_without_lux` | 四参目标入口存在时严格沿用；只有签名不存在才选择三参入口。返回值、实例方法、下游限时消费者、实际双 RampAnimator 与原生原因门控均完整才安装；残缺优先接口不降级。 | [refactor_traditional_animation.py](../../tests/refactor_traditional_animation.py) |
| `fold_kernel_binding` · `ActivePanelNode.java` | `native_port_pair` / `exclusive_internal_dsi` | 小编号显示端口继续使用原生成对端口绑定；非内核编号的显示端口不做固定偏移换算，只在当前逻辑默认内部屏明确点亮、布局身份有效、成对内核节点完整、唯一活动 DSI 与非零背光一致且二次读取身份状态不变时采用活动节点。双屏同亮、熄屏、未知及切换中拒绝写入。 | [refactor_fold_panel.py](../../tests/refactor_fold_panel.py) |
| `outdoor_output_provenance` · `OutdoorOutputAccess.java` | `selected_strategy` / `automatic_strategy2_reason` | 户外控制专用：优先 getLastSelectedStrategy，存在但错误或调用失败不降级；仅该方法缺失且独立 AutomaticBrightnessStrategy2 主屏归属、同一 ABC、启用/使用/已应用三个状态及已提交和当前计算的原生自动原因都完整时采用旧 ABI。仅允许已验证的 BCBC/OPR/PEAK 修饰，不绕开临时、手动、HDR、温控及其他修饰。能力检查不依赖当前是否已启用自动模式。 | [refactor_outdoor_fold.py](../../tests/refactor_outdoor_fold.py) |
| `outdoor_root_panel_binding` · `OutdoorNodeRoot.java` | `primary_node` / `fold_bound_node` | 无折叠证据保留旧主屏节点；折叠设备复用统一 ActivePanelNode 只读发现，要求实时框架物理身份与所选节点一致、布局物理 ID 清单完整，启动前后重新验证节点/最大值/身份，显式传递折叠清单。动态切屏由原生协作路径核验，不复用手动面板接管或权限修改。 | [refactor_outdoor_fold.py](../../tests/refactor_outdoor_fold.py) |
| `refactor_curve_layout` · `RefactorProfiles.java` | `anchors_7` / `coefficients_9_22` | 旧式锚点标记优先且不回退；真实9项系数、22项状态、9项拟合数据与实例方法全部通过才选择独立系数路线。系数路径的基准端点从设备实际系数与逻辑上限反解，不按型号、OS或固定照度推算。保留原生拟合 / 物理输出，跨模式、锁屏、重启恢复及记忆转基础暂不支持，保存配置不重置。 | [refactor_coefficient_curve.py](../../tests/refactor_coefficient_curve.py) |
| `opr_relaxation_envelope` · `OutdoorOprEnvelope.java` | `mapping_only` / `native_opr_envelope` / `native_limit_retained` | 灰阶/nit表字段整组不存在才沿用原映射范围；实际对象上的完整表最高档经物理nit换算限定本模块增加量，不能把换算末端当原生OPR高亮档；字段部分存在、非实例/类型错、顺序/形状错、未启用或换算失败均保留原生结果。不写原生灰阶、色彩、映射表或用户设置。未触发额外放宽前保持unconfirmed。 | [refactor_opr_envelope.py](../../tests/refactor_opr_envelope.py) |
| `physical_baseline_calibration` · `PhysicalCurveCalibration.java` | `in_range_original_route` / `aosp_verified_endpoint_clamp` | 只有实际默认曲线越过标定边界才检查双向原生LinearSpline/MonotoneCubicSpline、坐标对应、未调整的范围、纯换算端点；严格拒绝错误、动态标定、非单调或不匹配对象。中性保留原始曲线，编辑使用标定端点，不扩大硬件上限。 | [refactor_physical_calibration.py](../../tests/refactor_physical_calibration.py) |
| `slider_calibration_domain` · `ControlCenterCalibration.java` | `full_unit_domain` / `verified_native_endpoints` | 原全域校验不新增接口要求；端点平台分支仅匹配完整本机标定，排除中间平台、残缺结构和活动额外低亮度标定。范围随转换表传输，不扩展硬件或自动亮度允许范围。 | [refactor_control_center_calibration.py](../../tests/refactor_control_center_calibration.py) |
| `partition_manual_ceiling` · `ControlCenterPartition.java` | `ddc_hbm_transition` / `hbm_normal_ceiling` | 优先原始DDC HBM过渡点；入口不存在或合法无HBM数据才用实际控制器普通上限。残缺/类型错/坏数据不回退，不用固定百分比或机型表；边界不能证明时只停分区。 | [refactor_control_center_partition.py](../../tests/refactor_control_center_partition.py) |
| `screen_brightness_readback` · `ScreenBrightnessReading.java` | `driver_actual_brightness` / `sysfs_brightness_readback` / `display_config_backlight_to_nit` / `node_readback_uncalibrated` / `driver_zero_conflicts_with_lit_output` | 保留有效驱动actual_brightness优先的原路径。亮屏、同设备brightness为正、未报告背光关闭，且独立框架实际输出为正时，将矛盾的驱动0标为不可用并采用该设备brightness读回；未知、实际熄屏或背光关闭不推断为假0。常规输出优先原有框架nit，直接节点接管才优先本机标定换算。记录驱动值、寄存器值、原框架nit及回退原因；范围外不外推，不使用请求目标或机型表。 | [refactor_screen_brightness.py](../../tests/refactor_screen_brightness.py) |
| `coefficient_terminal_domain` · `RefactorCoefficientsAdapter.java` | `native_maximum_terminal` / `edited_lower_terminal` | 仅最高点显式下调时，将本机已校验calculateBrt的实际三组系数数组限定到原最高照度域；其他数组、用户/线程/未启用/原始100%路径不改变。相邻点保持严格递增，避免零斜率拟合；不改光感照度或最终输出保护。 | [refactor_coefficient_curve.py](../../tests/refactor_coefficient_curve.py) |

## 原生协议与运行分支

| 分支与实现 | 已有路径 | 选择及保护 | 相关验证 |
| --- | --- | --- | --- |
| `native_output_modes` · `main_panel.c` | `manual_guard` / `outdoor_cooperative` | 共用主屏锁，手动程序需要短租约和明确所有权；户外只协作提高且系统改写即退出，不互相争用。 | [refactor_native_panel.py](../../tests/refactor_native_panel.py) |
| `outdoor_lease_protocol` · `outdoor_node_policy.h` | `v1_fraction` / `v2_raw_target` | 保留旧百分比租约与当前真实节点目标；均限制 PID、1500 ms 有效期和设备最大值。 | [refactor_outdoor_node.py](../../tests/refactor_outdoor_node.py) |
| `freezer_startup` · `main_panel.c` | `root_cgroup` / `uid_0_cgroup` / `dev_freezer` / `legacy_freezer` / `no_freezer_path` | 只在显式启动原生工具时尝试现有路径；发现但均无法迁出时拒绝启动，不把错误吞成就绪。 | [refactor_native_asset.py](../../tests/refactor_native_asset.py) |
| `native_primary_node` · `panel_node.h` | `backlight` / `display_labelled_leds` | 真实路径与上限配对，拒绝副屏 / 手电 / 通知灯，不能仅因 Java 曾发现就省略原生复核。 | [refactor_panel_nodes.py](../../tests/refactor_panel_nodes.py) |
| `native_slider_listener` · `control_center_policy.h` | `ordinary_manual_lease` / `explicit_control_center_pid` | 控制中心监听必须 Root 明确启用、匹配现有框架 PID 和 cc_ 会话；普通手动租约不自动获得监听权限。 | [refactor_control_center.py](../../tests/refactor_control_center.py) |
| `outdoor_physical_lease` · `outdoor_node_policy.h` | `legacy_v1_fraction` / `legacy_v2_raw` / `physical_v3_raw` | v1/v2 继续用于原主屏路径；新增 v3 携带正数物理 ID。折叠协作仅接受在显式根进程清单内的 v3，按唯一活动 DSI 与新旧物理身份共同确认切屏，两者只变一个时等待、不写旧屏。重新绑定重置稳定确认，不跨屏恢复旧目标；未知、双亮、熄屏、原生改写或手动接管时退让。 对可恢复I/O错误最多三次写入，间隔80/160毫秒，不持有手动锁等待。每次绑定原始租约请求、重新核验PID/目标/物理身份及许可；系统有效改写进入本场景抑制。竞争退让，恢复写入只试一次，不越过原生保护。 | [refactor_outdoor_fold.py](../../tests/refactor_outdoor_fold.py) |

## 源码职责索引

每份生产 Java / C / 头文件必须有明确归属；新增文件漏登记会使检查失败。

### adapters · 原生折叠布局、默认物理屏与背光节点绑定

`ActivePanelNode.java`、`AdvancedTuning.java`、`AssistSensorCompat.java`、`AuxiliarySensorIdentity.java`、`BrightnessSensorAccess.java`、`ControlCenterCalibration.java`、`ControlCenterHooks.java`、`ControlCenterNodes.java`、`ControlCenterPartition.java`、`DarkLockSchedule.java`、`DimAodCompatibility.java`、`FieldOverride.java`、`ForegroundApps.java`、`FrameworkBrightness.java`、`HbmAccess.java`、`LowLightTuning.java`、`OutdoorOpr.java`、`OutdoorOprEnvelope.java`、`OutdoorOutputAccess.java`、`OutdoorSdrScope.java`、`OutdoorTuning.java`、`PhysicalCurveCalibration.java`、`PipelineHooks.java`、`RawPanelOutputHooks.java`、`RefactorAdapter.java`、`RefactorCoefficientsAdapter.java`、`RefactorProfiles.java`、`ResponseTuning.java`、`SceneTuning.java`、`ScreenBrightnessReading.java`、`TraditionalAdapter.java`、`TraditionalAnimation.java`、`TraditionalHooks.java`。

### bootstrap · 入口与代次

`AppBuild.java`、`HookEntry.java`、`LegacyEntry.java`、`RootLaunch.java`。

### bridge · 控制中心坐标标定、进程/配置元数据及完整节点桥生命周期；同步接管/暂停快照，旧v1不执行

`ControlCenterEngine.java`、`ControlCenterRoot.java`、`LauncherVisibility.java`、`LegacyModules.java`、`NativePanelAsset.java`、`NativePanelClient.java`、`NativePanelRoot.java`、`OutdoorNodeBridge.java`、`OutdoorNodeRoot.java`、`PanelNodeDiscovery.java`、`RootCommand.java`、`RootControl.java`、`RootPanelBridge.java`、`RootSettings.java`、`RootWorkspace.java`、`TemporaryScreenOn.java`。

### configuration · 配置、保持与迁移

`AdvancedOptions.java`、`BrightnessControlOptions.java`、`ConfigurationCompatibility.java`、`ConfigurationFile.java`、`MemoryOptions.java`、`MemoryPersistence.java`、`OutdoorOptions.java`、`SavedCurve.java`、`SceneOptions.java`、`ThemeCurveOptions.java`、`UiPreferences.java`。

### diagnostics · Root导出不可用时APP侧Downloads权限分析包

`AppRootDiagnostics.java`、`DeferredStateWriter.java`、`DiagnosticCollector.java`、`DiagnosticShareFiles.java`、`DiagnosticShareProvider.java`、`FeatureSelfCheck.java`、`InjectionStatus.java`、`LiveLimitEvidence.java`、`PipelineHistory.java`、`RootAccess.java`、`RootFeatureChecks.java`、`RuntimeFeatureChecks.java`、`SensorState.java`、`StatusTransport.java`、`SystemVersion.java`。

### hook_backend · 框架桥与安装事务

`hooking/HookTransaction.java`、`hooking/XC_MethodHook.java`、`hooking/XposedBridge.java`。

### network · 更新与名单网络客户端

`ThanksFeed.java`、`UpdateChecker.java`。

### orchestration · 显示运行协调

`BrightnessControl.java`、`HookRuntime.java`、`MemoryLifecycle.java`、`OutdoorController.java`、`PersistentMemory.java`、`SceneController.java`。

### policy · 自适应高度带、独立速度与错落位置的弹幕模型、可见范围计算和预测防重叠

`AboutMarquee.java`、`AdvancedPolicy.java`、`BackendSelection.java`、`CompatibilityCatalog.java`、`ControlCenterCurve.java`、`ControlCenterDemoState.java`、`ControlCenterMapping.java`、`ControlCenterStatus.java`、`CurveBackend.java`、`CurveComparison.java`、`CurveIdentity.java`、`CurvePlan.java`、`DarkLockPolicy.java`、`DelayPolicy.java`、`ForegroundUser.java`、`LowLightAssistEvidence.java`、`LowLightAssistGate.java`、`LowLightPolicy.java`、`LowLightThresholds.java`、`MemoryPolicy.java`、`MemoryScene.java`、`NavigationPolicy.java`、`NitBrightnessLimit.java`、`OutdoorNodeProfile.java`、`OutdoorNodeTarget.java`、`OutdoorPolicy.java`、`OutputProtection.java`、`RawPanelLease.java`、`RawPanelTilePolicy.java`、`RelativeNodeGesture.java`、`ReleasePolicy.java`、`ScenePolicy.java`、`ScrollElasticity.java`、`ThemeMemoryStore.java`、`ThermalPolicy.java`、`TraditionalCurve.java`。

### presentation · 浅蓝底色的大圆角方形图标、容器内裁剪弹幕及仅可见时运行的绘制调度

`AboutHeroView.java`、`AboutPage.java`、`ApplicationBrightnessSettings.java`、`BranchReport.java`、`BrightnessPanelActivity.java`、`BrightnessTileService.java`、`ControlCenterDemo.java`、`ControlCenterSettings.java`、`CurveEditor.java`、`DiagnosticSharing.java`、`ElasticScrollView.java`、`FeatureCheckPage.java`、`FeatureCompatibility.java`、`FloatingNavigation.java`、`ImmersiveLayout.java`、`LogCompatibilityPanel.java`、`LogCompatibilityState.java`、`MainActivity.java`、`ManualBrightnessPanel.java`、`QuickSetupPage.java`、`QuickSetupPlan.java`、`RawBrightnessPanel.java`、`ResponsiveDialog.java`、`SceneCatalog.java`、`SceneDescriptions.java`、`SceneSettings.java`、`SettingsPresets.java`、`SettingsSearch.java`、`SettingsSearchIndex.java`、`StatusAdvice.java`、`StatusLimits.java`、`StatusPageLayout.java`、`StatusPresentation.java`、`ThemePalette.java`、`TutorialContent.java`、`UiText.java`、`WelcomeGuide.java`。

### native · 原生主屏工具

- `main_panel.c`：主屏 IPC、进程身份、权限恢复和手动租约输出
- `panel_node.h`：真实主屏节点、路径和权限恢复记录
- `panel_policy.h`：主屏手动租约协议与范围
- `outdoor_node.h`：户外协作输出、独占锁、有界I/O重试、系统改写退让和错误阶段健康状态
- `outdoor_node_policy.h`：户外 v1 / v2 租约和系统改写退让
- `control_center_policy.h`：隐藏映射监听的进程与租约许可
- `fold_panel.h`：显式折叠端口的唯一 DSI 亮屏核验
