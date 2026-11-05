# 曲线与手动记忆：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 基础亮度曲线 · `curve`

| 项目 | 要求 |
| --- | --- |
| 触发 | 首次接入读取本机基线；用户修改蓝色曲线后保存并等待同revision应用确认。 |
| 需求与行为 | 蓝色基础曲线按设备范围编辑；相邻点单调联动，最高点可向下调；深色独立曲线提供切换清除、共用、独立记忆选项 |
| 退出 / 失败 | 停用或恢复系统默认时撤回自己的基线；其他控制者替换后不覆盖。 |
| 默认 | `native` · 本机原始曲线 |
| 配置键 | `factors`、`curve_floor_nit`、`saved_base_curve`、`dark_mode_curve` |
| 依赖能力 | 由本项实际接口独立判断 |

### 原生手动记忆 · `manual_memory`

| 项目 | 要求 |
| --- | --- |
| 触发 | 自动亮度及可学习普通区间内，用户主动调节控制中心亮度；完整节点与增强段不学习。 |
| 需求与行为 | 普通自动亮度区间主动调整后学习附近照度偏好，绿色只画真实实时曲线；学习容量和存档回放能力分别判断 |
| 退出 / 失败 | 按原生模型生命周期及用户保持选择重建；清除按钮只清当前主题记忆，不清基础曲线。 |
| 默认 | `native` · 强度100%，保留原生学习 |
| 配置键 | `memory_strength`、`memory_window`、`memory_lux_range` |
| 依赖能力 | 由本项实际接口独立判断 |

### 记忆转基础曲线 · `saved_curve`

| 项目 | 要求 |
| --- | --- |
| 触发 | 用户明确点击当前主题绿色曲线转存，且实时曲线、坐标和保存能力均已核验。 |
| 需求与行为 | 保存完整实际绿色节点到对应主题的基础曲线，不拟合四点；检查基准、单位和容量；清除记忆不清除基础曲线 |
| 退出 / 失败 | 完成后成为对应基础曲线；后续清除手动记忆不清除此保存结果。 |
| 默认 | `on_request` · 仅明确点击保存 |
| 配置键 | `saved_base_curve`、`dark_mode_curve` |
| 依赖能力 | `curve`、`manual_memory` |

### 保持记忆与跨重启存档 · `persistent_memory`

| 项目 | 要求 |
| --- | --- |
| 触发 | 用户选择对应保持范围，发生锁屏/亮屏、重启或主题切换，并满足原生点回放和当前环境确认。 |
| 需求与行为 | 分别支持锁屏、重启及主题保持；仅需要跨重启时落盘，校验设备、基准、用户和主题；期限点数按实际后端能力 |
| 退出 / 失败 | 停用对应保持时取消其待恢复任务；坏存档独立报告并保留，身份/基准不符时不回放。 |
| 默认 | `off` · 新配置关闭额外保持 |
| 配置键 | `memory_persist`、`memory_restore_unlock`、`memory_hold_theme`、`memory_retention_days`、`memory_max_points` |
| 依赖能力 | 由本项实际接口独立判断 |

### 跨自动亮度开关保持 · `memory_mode`

| 项目 | 要求 |
| --- | --- |
| 触发 | 选择跨开关保持后，用户重新开启自动亮度，当前用户/模型和环境已就绪。 |
| 需求与行为 | 通过原有模式观察器保留偏好，重新开启自动亮度时按同一记忆管理器恢复；自动亮度开关调用按旧 owner 布尔入口或新 facade 模式入口两种完整形状观察，残缺或跨屏入口视为不支持，不按机型放行 |
| 退出 / 失败 | 关闭跨开关保持后按原生模式生命周期处理；失败不清当前实时点或其他保持选择。 |
| 默认 | `off` · 新配置关闭 |
| 配置键 | `memory_hold_switch` |
| 依赖能力 | `persistent_memory` |

### 永久保持记忆 · `memory_forever`

| 项目 | 要求 |
| --- | --- |
| 触发 | 明确选择永久保持，后端回放能力可用，发生已支持的模式、锁屏或系统模型重建事件。 |
| 需求与行为 | 组合跨模式、锁屏、重启保持；仍校验身份和真实环境；无额外常驻进程 |
| 退出 / 失败 | 取消永久保持后恢复所选单项条件；仍拒绝过期身份或基准，不复活被主动清除的点。 |
| 默认 | `off` · 新配置关闭 |
| 配置键 | `memory_hold_forever` |
| 依赖能力 | `persistent_memory` |

### 记忆重置条件 · `memory_reset`

| 项目 | 要求 |
| --- | --- |
| 触发 | 用户明确启用原生重置条件覆盖，实际短期模型生命周期事件发生。 |
| 需求与行为 | 仅修改已验证原生模型重置门槛，不重写用户存档或基础曲线 |
| 退出 / 失败 | 停用覆盖后恢复仍属于本次修改的原生重置参数，不清存档和基础曲线。 |
| 默认 | `native` · 跟随系统 |
| 配置键 | `memory_reset_override`、`memory_reset_off_minutes`、`memory_reset_force_minutes`、`memory_reset_lux` |
| 依赖能力 | 由本项实际接口独立判断 |

### 记忆超时 · `memory_timeout`

| 项目 | 要求 |
| --- | --- |
| 触发 | 用户明确启用期限覆盖，实际原生短期模型进入或重置。 |
| 需求与行为 | 调整原生短期模型期限，与存档保存天数分别处理 |
| 退出 / 失败 | 停用覆盖后恢复仍属于本次修改的原生期限，跨重启存档策略保持独立。 |
| 默认 | `native` · 跟随系统 |
| 配置键 | `memory_timeout_override`、`memory_timeout_minutes` |
| 依赖能力 | 由本项实际接口独立判断 |

## 分叉与收束

### 选择边界

- 以ABC实际活动策略为入口；Refactor已选但未就绪时不抢用物理对象，活动标记未知时才按完整对象选择。
- Refactor锚点结构优先；只有锚点标记整体缺失且9项系数、22项状态及原生消费者签名完整时采用系数路线。
- 物理映射按真实类和双向标定选择；超出标定端点的完整原机曲线使用已验证裁剪分支，范围内旧路线不增加要求。
- Simple仅接完整的比例数组、样条和原生重建/学习接口，不作为物理接口残缺后的回退。
- 后端通过installMappingHooks装配自己的Hook；原生学习容量与存档回放能力独立声明。
- 主题、编辑、显示标尺和记忆转存使用同一已选后端；具体单位、约束及内部变体见下方契约与ABI索引。
- 独立默认字段/默认消费者整体缺失时，完整原生reset/getLogicalBrt及9/22布局才可采用重建系数；原默认接口任一残片存在时仍走旧校验，不降级。
- 物理后端模式编号及映射表标记均缺失时，按完整普通/休眠双对象与isForIdleMode校验；现代任一标记存在即要求完整现代ABI，不借旧接口降级。
- 有效自动模式优先owner的boolean字段；明确缺失时，按主屏DPC/策略/ABC归属读取原生shouldUseAutoBrightness；不采用设置缓存或最后输出原因。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `exclusive` |
| 统一输出 | CurveBackend能力方法（含installMappingHooks、独立学习容量/存档回放） + curve_contract v1 + Curves；显示标尺与原生载荷分开；原生私有当前消费者通过currentInputLux接收同坐标末段约束；有效自动模式通过相同get契约收束，读取错误独立进入状态，不伪造false或丢弃日志。 |
| 坐标与身份 | 物理nit/逻辑nit明确区分；归档value保持原生坐标，display_nit在适配器转换；framework_brightness为0—1框架亮度，按普通手动上限显示百分比，不冒充物理nit。 |

### 清理及回滚

- 停用、故障、基准或用户变化时，只撤回本次仍持有的曲线；保留原始对象与数组身份，系统已替换时不覆盖。
- 配置和记忆转存先验证再提交，失败恢复事务快照；校验失败不清当前点，浮点归一不修改原始状态或其他设置。
- 主题切换在显示Handler事务内取消旧待执行任务，按清除、共用或独立策略交接记忆；仅跨重启保持才落盘。关闭独立曲线不删除两条基础配置。
- Simple同步提交的临时用户许可在finally撤销；用户未知或中途变化时拒绝/回滚。清除记忆调用原生接口以清除残余调整。
- 必需原生Hook沿用事务安装器；默认后端不安装未知Hook。未经验证的拟合点或回放接口不声明为可移植记忆。
- 重建路线仅在同步显示事务内探测原生基线，结束时还原所有已核验数组的身份/内容和学习标记；读取不能把现有学习值当默认。修改前验证归属，重建失败完整回滚。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `refactor` · Refactor 逻辑曲线 | 使用本机 RefactorNitController 的逻辑锚点、手动点与曲线重建接口。 |
| `refactor_coefficients` · Refactor 分段系数曲线 | 旧式9项系数与22项状态完整；保留原生手动学习和输出，尚不提供虚拟拟合点的跨模式 / 锁屏 / 重启恢复或记忆转基础。 |
| `refactor_reset_coefficients` · Refactor 原生重建分段曲线 | 无独立默认系数字段且原生重建、私有曲线消费者及9/22状态完整；事务读取本机基线后还原现场，保留原生学习，暂不提供记忆转存与跨状态回放。 |
| `miui_physical` · 小米传统物理映射 | 实际映射器为 MiuiPhysicalBrightnessMappingStrategy，保留完整设备曲线与应用修正。 |
| `aosp_physical` · 系统标准物理映射 | 实际映射器为 PhysicalMappingStrategy，使用其亮度 / nit 转换与短期模型。 |
| `aosp_physical_clamped` · 系统物理映射：标定端点裁剪 | 原机曲线超出标定范围，且实际双向标定样条及端点裁剪通过验证；按有效范围编辑，默认保持原曲线对象与原生保护。 |
| `simple_mapping` · 系统亮度比例映射 | 实际使用 SimpleMappingStrategy 的照度/亮度比例数组及原生样条重建；保留手动学习与完整记忆转存，不将比例当作 nit。 |

路线内部 ABI：`active_backend`、`mapper_memory_coordinates`、`mapper_drag_signature`、`memory_mode_switch`、`inherited_mapper_members`、`refactor_curve_layout`、`physical_baseline_calibration`、`coefficient_terminal_domain`、`curve_manual_display_scale`、`curve_snapshot_contract`、`simple_native_baseline`、`simple_commit_user_scope`、`curve_hook_assembly`、`learning_replay_capabilities`、`reset_derived_baseline`、`physical_mapper_context`、`mapping_automatic_mode`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`curve_backend`、`physical_mapper_class`、`baseline_id`、`user_serial`、`curve_route`、`physical_baseline_route`、`physical_calibration_min_nit`、`physical_calibration_max_nit`、`physical_original_min_nit`、`physical_original_max_nit`、`physical_clamped_points`、`theme_curve_enabled`、`theme_curve_dark`、`base_curve_name`、`theme_memory_mode`、`curve_display_range`、`curve_contract`、`memory_live_error`、`simple_mapper_class`、`simple_mapping_active`、`simple_curve_points`、`simple_curve_nit_calibrated`、`calculated_brightness`、`simple_curve_last_error`、`refactor_curve_verification.profile`、`refactor_curve_verification.baseline_source`、`physical_context_route`、`physical_context_description`、`automatic_mode_route`、`automatic_mode_error`。

验证入口：

- [refactor_traditional.py](../../tests/refactor_traditional.py)
- [refactor_curve_integrity.py](../../tests/refactor_curve_integrity.py)
- [refactor_configuration.py](../../tests/refactor_configuration.py)
- [refactor_saved_curve_transaction.py](../../tests/refactor_saved_curve_transaction.py)
- [refactor_persistent_memory.py](../../tests/refactor_persistent_memory.py)
- [refactor_memory_lifecycle.py](../../tests/refactor_memory_lifecycle.py)
- [refactor_coefficient_curve.py](../../tests/refactor_coefficient_curve.py)
- [refactor_coefficient_firmware.py](../../tests/refactor_coefficient_firmware.py)
- [refactor_saved_curve.py](../../tests/refactor_saved_curve.py)
- [refactor_physical_calibration.py](../../tests/refactor_physical_calibration.py)
- [refactor_physical_calibration_firmware.py](../../tests/refactor_physical_calibration_firmware.py)
- [refactor_theme_curve.py](../../tests/refactor_theme_curve.py)
- [refactor_curve_editing.py](../../tests/refactor_curve_editing.py)
- [refactor_curve_percentage.py](../../tests/refactor_curve_percentage.py)
- [refactor_curve_scale.py](../../tests/refactor_curve_scale.py)
- [refactor_curve_boundary.py](../../tests/refactor_curve_boundary.py)
- [refactor_simple_mapping.py](../../tests/refactor_simple_mapping.py)
- [refactor_simple_mapping_firmware.py](../../tests/refactor_simple_mapping_firmware.py)
- [refactor_reset_curve.py](../../tests/refactor_reset_curve.py)
- [refactor_reset_firmware.py](../../tests/refactor_reset_firmware.py)
- [refactor_mapper_context.py](../../tests/refactor_mapper_context.py)
- [refactor_mapper_context_firmware.py](../../tests/refactor_mapper_context_firmware.py)
- [refactor_automatic_mode.py](../../tests/refactor_automatic_mode.py)
- [refactor_automatic_mode_firmware.py](../../tests/refactor_automatic_mode_firmware.py)
- [refactor_legacy_input_firmware.py](../../tests/refactor_legacy_input_firmware.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [AutomaticModeAccess.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AutomaticModeAccess.java) | `89cb3474888f3cb7` |
| [BackendSelection.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BackendSelection.java) | `f4b0414f4dde77fa` |
| [CurveBackend.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/CurveBackend.java) | `4425f4d11dac93f4` |
| [CurveBackendFactory.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/CurveBackendFactory.java) | `838b0409ccf67a75` |
| [CurveComparison.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/CurveComparison.java) | `f65cc96f5d4eb287` |
| [CurveCompatibility.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/CurveCompatibility.java) | `cce39709a12e65c6` |
| [CurveDisplayRange.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/CurveDisplayRange.java) | `372050d731634e9a` |
| [CurveEditor.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/CurveEditor.java) | `66423535b50b13ee` |
| [CurvePlan.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/CurvePlan.java) | `6064003310ae7433` |
| [HookRuntime.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookRuntime.java) | `f1cdc6bfa6a1d706` |
| [MainActivity.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/MainActivity.java) | `2c493852ffa1550e` |
| [MapperContextAccess.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/MapperContextAccess.java) | `778e604c1ff57198` |
| [MemoryLifecycle.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/MemoryLifecycle.java) | `ee4d215c8a92a43b` |
| [MemoryPersistence.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/MemoryPersistence.java) | `09c7bd896953ad71` |
| [PersistentMemory.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/PersistentMemory.java) | `0bd2ff3ec3bf3ac8` |
| [PhysicalCurveCalibration.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/PhysicalCurveCalibration.java) | `23a9952a5cbc182a` |
| [RefactorAdapter.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RefactorAdapter.java) | `373ea4cb064674e5` |
| [RefactorCoefficientsAdapter.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RefactorCoefficientsAdapter.java) | `1690edd32b5e27c7` |
| [RefactorProfiles.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RefactorProfiles.java) | `3e1f956a3a071894` |
| [RefactorResetAdapter.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RefactorResetAdapter.java) | `b168fcd324bf2f87` |
| [SavedCurve.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SavedCurve.java) | `3d8f7557f9e456a2` |
| [SimpleCurveAdapter.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SimpleCurveAdapter.java) | `baf692ac613d1830` |
| [ThemeCurveOptions.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ThemeCurveOptions.java) | `438f9376ed3ed9d5` |
| [ThemeMemoryStore.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ThemeMemoryStore.java) | `d6db61812d73f5d5` |
| [TraditionalAdapter.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/TraditionalAdapter.java) | `5537819db1b1ee6d` |
| [TraditionalCurve.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/TraditionalCurve.java) | `6d6dae27db1b9bf7` |
| [TraditionalHooks.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/TraditionalHooks.java) | `ce1e61e19bdab0d6` |
