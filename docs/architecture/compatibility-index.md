# 兼容路线索引（自动生成）

来源：`CompatibilityCatalog.java` ＋ `compatibility-manifest.json` ＋ `source-map.json`。修改来源后运行 `python tools/verify_compatibility.py --write-doc`；普通校验会拒绝过期索引。

此索引说明已实现和验证责任，不代表所有功能已在所有设备启用或实测。

共 48 项功能、20 组适配、122 份生产 Java。

## 曲线与手动记忆 · curve

功能：`curve`、`manual_memory`、`saved_curve`、`persistent_memory`、`memory_mode`、`memory_forever`、`memory_reset`、`memory_timeout`。

选择：ABC 活动策略为准；Refactor 未就绪不得因物理映射对象存在而抢用，未知活动标记才按已验证对象选择。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `refactor` · Refactor 逻辑曲线 | 使用本机 RefactorNitController 的逻辑锚点、手动点与曲线重建接口。 |
| `miui_physical` · 小米传统物理映射 | 实际映射器为 MiuiPhysicalBrightnessMappingStrategy，保留完整设备曲线与应用修正。 |
| `aosp_physical` · 系统标准物理映射 | 实际映射器为 PhysicalMappingStrategy，使用其亮度 / nit 转换与短期模型。 |

实现：[BackendSelection.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BackendSelection.java)、[CurveBackend.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/CurveBackend.java)、[RefactorAdapter.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RefactorAdapter.java)、[TraditionalAdapter.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/TraditionalAdapter.java)、[TraditionalHooks.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/TraditionalHooks.java)、[MemoryLifecycle.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/MemoryLifecycle.java)、[PersistentMemory.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/PersistentMemory.java)、[MemoryPersistence.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/MemoryPersistence.java)、[SavedCurve.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SavedCurve.java)。

证据：`curve_backend`、`physical_mapper_class`、`baseline_id`、`user_serial`。

退出 / 回滚：关闭 / 故障 / OTA 基准或用户变化恢复本次应用的曲线；只按所选保持范围恢复匹配记忆。

验证：[refactor_traditional.py](../../tests/refactor_traditional.py)、[refactor_curve_integrity.py](../../tests/refactor_curve_integrity.py)、[refactor_configuration.py](../../tests/refactor_configuration.py)、[refactor_saved_curve_transaction.py](../../tests/refactor_saved_curve_transaction.py)、[refactor_persistent_memory.py](../../tests/refactor_persistent_memory.py)、[refactor_memory_lifecycle.py](../../tests/refactor_memory_lifecycle.py)。

## 主光感 · main_input

功能：`main_sensor`。

选择：优先保留当前默认逻辑屏 ABC 的 TYPE_LIGHT；新增 ABC 已直接选中的 xiaomi.sensor.light_secondary 非唤醒、按变化、无权限且量程 / 精度有效的照度源。不枚举，不按私有类型编号或机型选择，不将原始 / 工厂 / 流式通道当作 lux。 配置槽非标准且 OEM 开启 FOV 时，核验其默认屏标识、真实 FOV 对象和 Sensor→Sensor 选择器结果，仅使用选择器实际返回的源；虚拟 FOV 的 values[0] 已为 lux，确认驾驶数据包才读取 values[2] 并用原生倍率。优先接口残缺 / 抛错拒绝，不回落物理槽；标准 TYPE_LIGHT 优先原路径，不受新 FOV 判定影响。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `oem_main` · 系统标准主光感 | 读取当前主屏 ABC 选用的 TYPE_LIGHT 光感，沿用原适配。 |
| `oem_selected_secondary` · 系统选用的折叠光感 | 当前主屏 ABC 直接选用已验证的私有照度光感；拒绝原始、工厂、流式及其他屏幕候选。 |
| `oem_selected_fov` · 系统 FOV 融合光感 | 沿用主屏原生光感选择器返回的 FOV 源和照度解析；不混用 ABC 保留的物理光感槽。 |

实现：[RuntimeFeatureChecks.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RuntimeFeatureChecks.java)、[HookRuntime.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookRuntime.java)、[SensorState.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SensorState.java)、[BrightnessSensorAccess.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessSensorAccess.java)。

证据：`sensor_status`、`sensor_sampling_enabled`、`ambient_lux_valid`、`main_fast_lux`、`main_input_compatibility`。

退出 / 回滚：只读观察，不拥有 OEM 监听。

验证：[refactor_feature_self_check.py](../../tests/refactor_feature_self_check.py)、[refactor_status_presentation.py](../../tests/refactor_status_presentation.py)、[refactor_brightness_control.py](../../tests/refactor_brightness_control.py)、[refactor_fold_sensor_firmware.py](../../tests/refactor_fold_sensor_firmware.py)。

## 辅助光感 · assist_input

功能：`assist_sensor`。

选择：原生有效槽优先；空槽才考虑唯一且类型 / 单位 / 权限 / 线程 / 主屏均合格的移植候选，并同步当前 ABC 构造缓存。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `native_auxiliary` · 系统原生辅助光感 | 原生辅助槽已有传感器时优先使用，继续沿用系统采样和融合。 |
| `ported_auxiliary` · 移植系统辅助补位 | 原生槽为空；唯一 zte.sensor.light_second 候选通过类型、单位、权限、线程及主屏检查后，补入 OEM 槽并同步双光感能力缓存。 |

实现：[AssistSensorCompat.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AssistSensorCompat.java)、[AuxiliarySensorIdentity.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AuxiliarySensorIdentity.java)。

证据：`assist_compatibility.binding_kind`、`assist_compatibility.state`、`assist_compatibility.sensor_string_type`。

退出 / 回滚：补位失败回滚传感器槽、启用状态和本次能力缓存；其他所有者已替换时不覆盖。

验证：[refactor_assist_compat.py](../../tests/refactor_assist_compat.py)、[refactor_assist_compat_firmware.py](../../tests/refactor_assist_compat_firmware.py)。

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

选择：验证双动画对象、字段及原生 updatePerceptualDuration；保留 HDR、主屏与用户资格。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `oem_animation` · 系统亮度过渡接口 | 调整已验证的原生过渡倍率，保持系统动画及主副屏隔离。 |

实现：[AdvancedTuning.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AdvancedTuning.java)。

证据：`animation_override_supported`。

退出 / 回滚：只恢复本次仍由模块拥有的时长，不覆盖系统已改写的值。

验证：[refactor_advanced_hooks.py](../../tests/refactor_advanced_hooks.py)。

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

实现：[AdvancedTuning.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AdvancedTuning.java)。

证据：`touch_override_supported`。

退出 / 回滚：每次调用后恢复临时字段，包括原生异常。

验证：[refactor_advanced_hooks.py](../../tests/refactor_advanced_hooks.py)。

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

实现：[SceneTuning.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SceneTuning.java)、[SceneController.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SceneController.java)、[SceneCatalog.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SceneCatalog.java)。

证据：`system_scene`、`scene_options`、`feature_self_check`。

退出 / 回滚：临时场景字段按调用恢复；停止取消本次确认，恢复自己的步行 / 夜间设置。

验证：[refactor_scenes.py](../../tests/refactor_scenes.py)、[refactor_scene_hooks.py](../../tests/refactor_scene_hooks.py)、[refactor_scene_settings.py](../../tests/refactor_scene_settings.py)、[refactor_scene_firmware.py](../../tests/refactor_scene_firmware.py)。

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

选择：完整隐私判断优先；完全没有隐私 / UHBM 残片且旧 OPR 完整才用传统路径。实际物理换算用于 nit 上限。调用失败不降级。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `privacy_aware` · 带隐私显示判断的新式 SDR 路径 | 匹配 shouldUsePrivacyOprBrightness，保留隐私显示、HDR、温控与电量判断。 |
| `legacy_opr` · 传统 OPR / SDR 路径 | 不存在隐私显示分支且旧版灰阶 / nit 接口完整；不将残缺的新式接口误当旧接口。 |

实现：[OutdoorSdrScope.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorSdrScope.java)、[OutdoorOpr.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorOpr.java)、[NitBrightnessLimit.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/NitBrightnessLimit.java)。

证据：`outdoor.sdr_compatibility_profile`、`outdoor.sdr_compatibility_detail`。

退出 / 回滚：ThreadLocal 帧按调用恢复；只移除本次引入的 OPR 原因位；温控、电量、HDR 与系统接管继续优先。

验证：[refactor_outdoor_compatibility.py](../../tests/refactor_outdoor_compatibility.py)、[refactor_outdoor_compatibility_firmware.py](../../tests/refactor_outdoor_compatibility_firmware.py)。

## 显示层温控 · thermal

功能：`thermal`。

选择：显示层 HDR / SDR 消费者完整，严重温控与电池门槛继续有效；不改 SoC / 充电温控。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `display_thermal` · 显示层温控接口 | 仅调整显示亮度限制；严重温控、电池温度门槛及其他系统温控继续有效。 |

实现：[HookEntry.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookEntry.java)、[ThermalPolicy.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ThermalPolicy.java)、[HookRuntime.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookRuntime.java)。

证据：`thermal_supported`、`thermal_skipped`。

退出 / 回滚：每次调用恢复暂存显示限亮字段，停止恢复系统控制。

验证：[refactor_advanced_hooks.py](../../tests/refactor_advanced_hooks.py)、[refactor_hook_firmware.py](../../tests/refactor_hook_firmware.py)。

## 主屏输出隔离 · raw_output

功能：`manual_panel`、`node_output`。

选择：主屏输出 / 电源状态对象完整；租约、解锁、模式、屏幕状态均通过才隔离正亮度帧，第一帧留给系统。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `raw_primary_output` · 主屏原生节点输出 | 主屏输出接口配合实际节点识别、所有权租约与系统接管退让；不把节点值视为 nit。 |

实现：[RawPanelOutputHooks.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RawPanelOutputHooks.java)、[BrightnessControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java)、[OutdoorNodeBridge.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorNodeBridge.java)、[RawPanelLease.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RawPanelLease.java)。

证据：`brightness_control.owner`、`outdoor_node_supported`。

退出 / 回滚：熄屏先撤销控制权，租约失联自动归还，系统改写优先。

验证：[refactor_raw_output.py](../../tests/refactor_raw_output.py)、[refactor_raw_panel_transaction.py](../../tests/refactor_raw_panel_transaction.py)、[refactor_outdoor_node.py](../../tests/refactor_outdoor_node.py)。

## 真实主屏节点 · node

功能：`main_panel_node`。

选择：backlight 或明确标识显示的 leds；真实 sysfs 路径与上限配对且唯一主屏，不按机型写死上限。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `backlight_node` · 主屏 backlight 节点 | 扫描 backlight，验证主屏身份、真实路径及 brightness / max_brightness 配对。 |
| `display_led_node` · 显示专用 leds 节点 | 仅接受明确标识为主屏背光的 leds 节点，排除通知灯、手电与背屏。 |

实现：[PanelNodeDiscovery.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/PanelNodeDiscovery.java)、[NativePanelRoot.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/NativePanelRoot.java)。

证据：`main_panel_node.node_class`、`main_panel_node.node_path`、`main_panel_node.node_maximum`。

退出 / 回滚：发现与权限检查只读，不启动写入器；实际租约写入另由原生事务负责，拒绝副屏 / 指示灯 / 手电。

验证：[refactor_panel_nodes.py](../../tests/refactor_panel_nodes.py)、[refactor_panel_access.py](../../tests/refactor_panel_access.py)。

## Root 存储与原生工具 · root_tools

功能：`root_storage`、`stored_config`、`outdoor_node_device`、`native_payload`。

选择：逐项检查私有存储、原始配置、当前 APK 资产和组合接口；不试写亮度，不擅自启动服务。

组合关系：`per_feature`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `root_private_storage` · Root 私有数据目录 | 只检查配置 / 分析目录的读写能力，不安装或启动输出程序。 |
| `stored_json` · 原始配置 JSON | 读取原始配置；自检与兼容裁剪不会将默认值写回用户设置。 |
| `primary_node_profile` · 实际主屏节点与框架输出组合 | 按实际节点上限、曲线映射和输出能力核验，不按机型名单放行。 |
| `apk_native_asset` · APK 内原生程序校验 | 核对当前 APK 的主屏程序资产完整性，不启动守护、不试写亮度。 |

实现：[RootControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RootControl.java)、[RootFeatureChecks.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RootFeatureChecks.java)、[NativePanelAsset.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/NativePanelAsset.java)、[OutdoorNodeRoot.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorNodeRoot.java)。

证据：`feature_self_check`、`root_feature_self_check`。

退出 / 回滚：短命 Root 桥和有界 IPC；诊断不写回用户配置，失败只禁本项。

验证：[refactor_feature_self_check.py](../../tests/refactor_feature_self_check.py)、[refactor_native_asset.py](../../tests/refactor_native_asset.py)、[refactor_outdoor_node_settings.py](../../tests/refactor_outdoor_node_settings.py)。

## APP 本地工具 · app_tools

功能：`launcher`、`preferences`、`app_files`。

选择：独立 Launcher alias / 原偏好存储 / 私有文件权限；不使用启动组件名重新建立偏好。

组合关系：`per_feature`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `launcher_alias` · 桌面入口别名 | 使用 PackageManager 的独立桌面别名，保留模块设置入口和欢迎引导完成状态。 |
| `app_preferences` · 应用本地偏好存储 | 读取设置草稿与欢迎记录，不清空、不改变开关。 |
| `app_files` · 应用私有文件目录 | 检查配置导入导出所用目录的可访问性。 |

实现：[MainActivity.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/MainActivity.java)、[LauncherVisibility.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/LauncherVisibility.java)、[UiPreferences.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/UiPreferences.java)。

证据：`APP.appFeatureReport`。

退出 / 回滚：别名切换保留设置、欢迎记录；APP 销毁取消 UI 回调。

验证：[refactor_ui_preferences.py](../../tests/refactor_ui_preferences.py)、[refactor_app_preferences.py](../../tests/refactor_app_preferences.py)、[refactor_welcome.py](../../tests/refactor_welcome.py)。

## 控制中心节点映射（入口隐藏） · control_center

功能：`control_center_slider`。

选择：实际双向单调转换、进程代次、显示身份与对应 UI ABI 同时通过；本版不展示入口，保留原选择。

组合关系：`exclusive`；互斥、独立功能、双维度和补充组件分别处理。

| 路线 | 条件 |
| --- | --- |
| `coordinate_v2` · 双向设备坐标映射 | 使用本机亮度到背光的转换表；可可靠单调反解才启用。本版入口隐藏，旧选择保留。 |

实现：[ControlCenterCalibration.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterCalibration.java)、[ControlCenterEngine.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterEngine.java)、[ControlCenterHooks.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterHooks.java)、[ControlCenterRoot.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterRoot.java)。

证据：`control_center_slider.kind`、`control_center_slider.error`。

退出 / 回滚：进程 / 用户 / 模式 / 失联变化丢弃旧 profile，不借映射改变系统自动模式。

验证：[refactor_control_center.py](../../tests/refactor_control_center.py)、[refactor_control_center_root.py](../../tests/refactor_control_center_root.py)。

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
| `dark_primary_only` · `RuntimeFeatureChecks.java` | `dual_policy_present` / `dual_policy_absent` | 没有双光感策略可用主光感；存在但缩放或传感器格式异常不伪造双光感。 | [refactor_feature_self_check.py](../../tests/refactor_feature_self_check.py) |
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

## 原生协议与运行分支

| 分支与实现 | 已有路径 | 选择及保护 | 相关验证 |
| --- | --- | --- | --- |
| `native_output_modes` · `main_panel.c` | `manual_guard` / `outdoor_cooperative` | 共用主屏锁，手动程序需要短租约和明确所有权；户外只协作提高且系统改写即退出，不互相争用。 | [refactor_native_panel.py](../../tests/refactor_native_panel.py) |
| `outdoor_lease_protocol` · `outdoor_node_policy.h` | `v1_fraction` / `v2_raw_target` | 保留旧百分比租约与当前真实节点目标；均限制 PID、1500 ms 有效期和设备最大值。 | [refactor_outdoor_node.py](../../tests/refactor_outdoor_node.py) |
| `freezer_startup` · `main_panel.c` | `root_cgroup` / `uid_0_cgroup` / `dev_freezer` / `legacy_freezer` / `no_freezer_path` | 只在显式启动原生工具时尝试现有路径；发现但均无法迁出时拒绝启动，不把错误吞成就绪。 | [refactor_native_asset.py](../../tests/refactor_native_asset.py) |
| `native_primary_node` · `panel_node.h` | `backlight` / `display_labelled_leds` | 真实路径与上限配对，拒绝副屏 / 手电 / 通知灯，不能仅因 Java 曾发现就省略原生复核。 | [refactor_panel_nodes.py](../../tests/refactor_panel_nodes.py) |
| `native_slider_listener` · `control_center_policy.h` | `ordinary_manual_lease` / `explicit_control_center_pid` | 控制中心监听必须 Root 明确启用、匹配现有框架 PID 和 cc_ 会话；普通手动租约不自动获得监听权限。 | [refactor_control_center.py](../../tests/refactor_control_center.py) |

## 源码职责索引

每份生产 Java / C / 头文件必须有明确归属；新增文件漏登记会使检查失败。

### adapters · 设备接口与所有权

`AdvancedTuning.java`、`AssistSensorCompat.java`、`BrightnessSensorAccess.java`、`ControlCenterCalibration.java`、`ControlCenterHooks.java`、`ForegroundApps.java`、`FrameworkBrightness.java`、`HbmAccess.java`、`LowLightTuning.java`、`OutdoorOpr.java`、`OutdoorSdrScope.java`、`OutdoorTuning.java`、`PipelineHooks.java`、`RawPanelOutputHooks.java`、`RefactorAdapter.java`、`ResponseTuning.java`、`SceneTuning.java`、`TraditionalAdapter.java`、`TraditionalHooks.java`。

### bootstrap · 入口与代次

`AppBuild.java`、`HookEntry.java`、`LegacyEntry.java`。

### bridge · Root 与原生程序桥

`ControlCenterEngine.java`、`ControlCenterRoot.java`、`LauncherVisibility.java`、`LegacyModules.java`、`NativePanelAsset.java`、`NativePanelClient.java`、`NativePanelRoot.java`、`OutdoorNodeBridge.java`、`OutdoorNodeRoot.java`、`PanelNodeDiscovery.java`、`RootControl.java`、`RootPanelBridge.java`、`RootSettings.java`、`TemporaryScreenOn.java`。

### configuration · 配置、保持与迁移

`AdvancedOptions.java`、`BrightnessControlOptions.java`、`ConfigurationCompatibility.java`、`ConfigurationFile.java`、`MemoryOptions.java`、`MemoryPersistence.java`、`OutdoorOptions.java`、`SavedCurve.java`、`SceneOptions.java`、`UiPreferences.java`。

### diagnostics · 只读证据与诊断

`DeferredStateWriter.java`、`DiagnosticCollector.java`、`FeatureSelfCheck.java`、`InjectionStatus.java`、`LiveLimitEvidence.java`、`PipelineHistory.java`、`RootFeatureChecks.java`、`RuntimeFeatureChecks.java`、`SensorState.java`、`StatusTransport.java`、`SystemVersion.java`。

### hook_backend · 框架桥与安装事务

`hooking/HookTransaction.java`、`hooking/XC_MethodHook.java`、`hooking/XposedBridge.java`。

### network · 更新与名单网络客户端

`ThanksFeed.java`、`UpdateChecker.java`。

### orchestration · 显示运行协调

`BrightnessControl.java`、`HookRuntime.java`、`MemoryLifecycle.java`、`OutdoorController.java`、`PersistentMemory.java`、`SceneController.java`。

### policy · 策略与算子

`AdvancedPolicy.java`、`AuxiliarySensorIdentity.java`、`BackendSelection.java`、`CompatibilityCatalog.java`、`ControlCenterCurve.java`、`ControlCenterMapping.java`、`CurveBackend.java`、`CurveComparison.java`、`CurveIdentity.java`、`CurvePlan.java`、`DarkLockPolicy.java`、`DelayPolicy.java`、`ForegroundUser.java`、`LowLightAssistEvidence.java`、`LowLightAssistGate.java`、`LowLightPolicy.java`、`LowLightThresholds.java`、`MemoryPolicy.java`、`MemoryScene.java`、`NitBrightnessLimit.java`、`OutdoorNodeProfile.java`、`OutdoorNodeTarget.java`、`OutdoorPolicy.java`、`OutputProtection.java`、`RawPanelLease.java`、`RawPanelTilePolicy.java`、`RelativeNodeGesture.java`、`ReleasePolicy.java`、`ScenePolicy.java`、`ThermalPolicy.java`、`TraditionalCurve.java`。

### presentation · 界面与只读说明

`ApplicationBrightnessSettings.java`、`BranchReport.java`、`BrightnessPanelActivity.java`、`BrightnessTileService.java`、`CurveEditor.java`、`FeatureCheckPage.java`、`FeatureCompatibility.java`、`MainActivity.java`、`ManualBrightnessPanel.java`、`RawBrightnessPanel.java`、`ResponsiveDialog.java`、`SceneCatalog.java`、`SceneDescriptions.java`、`SceneSettings.java`、`SettingsPresets.java`、`StatusAdvice.java`、`StatusLimits.java`、`StatusPageLayout.java`、`StatusPresentation.java`、`ThemePalette.java`、`TutorialContent.java`、`UiText.java`、`WelcomeGuide.java`。

### native · 原生主屏工具

- `main_panel.c`：主屏 IPC、进程身份、权限恢复和手动租约输出
- `panel_node.h`：真实主屏节点、路径和权限恢复记录
- `panel_policy.h`：主屏手动租约协议与范围
- `outdoor_node.h`：户外协作输出、锁和健康状态
- `outdoor_node_policy.h`：户外 v1 / v2 租约和系统改写退让
- `control_center_policy.h`：隐藏映射监听的进程与租约许可
