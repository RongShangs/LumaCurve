# 控制中心节点映射：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 控制中心坐标基础 · `control_center_slider`

| 项目 | 要求 |
| --- | --- |
| 触发 | 保存的映射已启用，真实SystemUI注入、坐标接口与同代配置表已就绪。 |
| 需求与行为 | 标准与有效端点标定分别验证，提供同代坐标表；锁屏回原生，缺实际加载回执不假报已生效 |
| 退出 / 失败 | 锁屏退回原生坐标，配置或进程换代清旧profile；未知标定不回放旧坐标。 |
| 默认 | `off` · 新配置关闭 |
| 配置键 | `control_center_node_mode` |
| 依赖能力 | 由本项实际接口独立判断 |

### 完整节点控制 · `control_center_raw`

| 项目 | 要求 |
| --- | --- |
| 触发 | 完整节点模式下，已解锁用户主动拖动，实际物理节点及同代控制权已确认。 |
| 需求与行为 | 全条按真实节点比例显示，拖动关闭自动亮度并放开暗光锁定，复用面板保持，不学习；HDR保留系统图像处理 |
| 退出 / 失败 | 打开自动亮度结束保持；锁屏回原生亮度条，解锁按当前代次恢复映射。 |
| 默认 | `off` · 新配置关闭 |
| 配置键 | `control_center_node_mode`、`control_center_node_maximum`、`control_center_node_path` |
| 依赖能力 | `control_center_slider`、`main_panel_node` |

### 分区节点控制 · `control_center_partition`

| 项目 | 要求 |
| --- | --- |
| 触发 | 分区模式下，普通与额外节点边界已验证；首次触顶后3秒内再次上滑，或按真实自动高亮状态接续控制。 |
| 需求与行为 | 普通75%保留原生学习；首次顶住分界并提示，3秒内再次上滑才进入增强25%；实际节点上限可设；返回普通段可恢复自动或继续手动 |
| 退出 / 失败 | 回普通段按所选恢复自动或继续手动；锁屏回原生；过期和换代拒绝旧手势。 |
| 默认 | `off` · 新配置关闭 |
| 配置键 | `control_center_node_mode`、`control_center_partition_maximum`、`control_center_partition_restore_auto` |
| 依赖能力 | `control_center_slider`、`main_panel_node` |

## 分叉与收束

### 选择边界

- 旧mode=0/1/2保留坐标语义：严格单调全域优先；端点平台只有原始数组、双向样条与裁剪证据完整才采用有效域，逻辑101点表独立核验。
- 完整节点mode=3复用已验证坐标回显，并独立核验主屏隔离、物理ID、Root可写节点与监听进程；动态折叠监听未知时不套用。
- 分区mode=4额外读取设备普通HBM边界并经真实标定换算；没有可靠边界或额外区间则只拒绝分区。
- 分区普通段占75%、增强段占25%；普通V1使用原生逻辑输入全域，gamma使用物理普通边界；增强段及回显按实际节点标定。用户上界不修改自动HBM范围。
- 普通V1的延迟回显先将真实物理读数反解为本机逻辑进度，再映射到75%，动画比较使用同一逆变换。作用域、手势和控制权为独立检查。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `per_feature` |
| 统一输出 | 标定/分区坐标、同代profile、统一节点保持和作用域结果 |
| 坐标与身份 | UI进度/framework brightness/物理背光/节点分层；普通段0—.75，经逆变换回显 |

### 清理及回滚

- 停用或用户、配置、进程、对象变化时撤回旧profile。Root暂缺保留原配置和其他设置；同节点且无关设置变动不解除已有保持。
- 节点拖动复用raw_panel、原生程序及健康检查，关闭自动亮度并放开暗光锁定；重开自动亮度撤销租约及旧手势。锁屏输入/回显退回原生，解锁仅恢复当前代次。
- 分区普通段调用原生回调，保留开启中的自动亮度与学习；增强段不学习。首次在75%封顶，松手后三秒内再次上滑越界才接管；返回普通段按用户选择恢复自动或继续手动。
- 首次封顶同步源滑块并保护外部变化；系统自动反馈不进入手势门禁。自动高亮已在增强段时，需新手势且框架实际值/标定再次确认。超时、换代或未越界结束均撤销确认。
- 独立onStart/onStop通知为可选子组，失败不撤销既有进度路径。已确认的手动保持不因HDR退出，但保留图像处理、首笔HDR拒绝、严重温控与真实故障退让。
- 作用域只在相关配置确实改变时按实际证据提示；手动重启仅针对核验后的SystemUI主进程。动画离页停止，监听与日志有界，协议细节见ABI及原生变体。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `coordinate_v2` · 双向设备坐标映射 | 使用本机亮度到背光的转换表；可可靠单调反解才启用。保留自动亮度、手动记忆与暗光锁定的原有控制权。 |
| `coordinate_v2_calibrated` · 标定有效区间映射 | 仅完整原生标定与双向样条一致、端点裁剪和有效区间可反解时，排除最低或最高端的平台；中段异常及额外低亮度标定不套用。 |
| `raw_node_v3` · 完整主屏节点与手动保持 | 独立选择开启；按实际 max_brightness 比例下发节点目标。拖动退出暗光锁定并关闭自动亮度，复用主屏节点租约；重开自动亮度立即结束。需要实际可写节点与系统界面接口；未知折叠监听布局不套用。 |
| `partition_node_v4` · 普通亮度与节点分区控制 | 使用本机普通亮度边界划分区间；普通区间保留自动亮度与原学习。到达边界松手后，三秒内再次向上滑动才使用共用节点保持；返回普通区间可恢复自动或保持手动。 |
| `partition_ddc_hbm` · 原机配置普通上限 | 优先读取原始HBM过渡点，按本机标定换算普通区间；类型或数据错误不降级绕过。 |
| `partition_hbm_normal` · 控制器普通上限 | 原机配置没有该入口或合法无HBM数据时，读取实际普通亮度上限；不按固定比例猜测。 |

路线内部 ABI：`sensor_identity`、`sensor_freshness`、`control_center_feedback`、`control_center_minimum`、`control_center_conversion`、`slider_calibration_domain`、`partition_manual_ceiling`、`control_center_tracking_lifecycle`、`system_ui_scope_reader`、`system_ui_explicit_restart`、`manual_node_hold_policy`、`mapping_recovery_confirmation`、`control_center_lockscreen`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`control_center_slider.kind`、`control_center_slider.error`、`hyperlux_control_center_tables_v2.enabled`、`hyperlux_control_center_tables_v2.revision`、`hyperlux_control_center_tables_v2.user_serial`、`control_center_slider.calibration_profile`、`control_center_slider.compatibility_state`、`control_center_slider.oem_v1_ready`、`control_center_raw_supported`、`control_center_slider.raw_ready`、`control_center_slider.raw_control`、`control_center_node_detail`、`control_center_partition_supported`、`control_center_slider.partition`、`control_center_slider.partition_error`、`hyperlux_control_center_tables_v2.partition_maximum`、`control_center_slider.raw_control.confirmation_trace`、`main-panel.previous.log`、`main-panel.log.NATIVE_EXIT`、`control_center_setup.scope_state`、`control_center_setup.injection_state`、`hyperlux_control_center_ui_v1.pid`、`hyperlux_control_center_ui_v1.process_start`。

验证入口：

- [refactor_control_center.py](../../tests/refactor_control_center.py)
- [refactor_control_center_root.py](../../tests/refactor_control_center_root.py)
- [refactor_brightness_control.py](../../tests/refactor_brightness_control.py)
- [refactor_feature_self_check.py](../../tests/refactor_feature_self_check.py)
- [refactor_control_center_calibration.py](../../tests/refactor_control_center_calibration.py)
- [refactor_control_center_nodes.py](../../tests/refactor_control_center_nodes.py)
- [refactor_control_center_partition.py](../../tests/refactor_control_center_partition.py)
- [refactor_control_center_settings.py](../../tests/refactor_control_center_settings.py)
- [refactor_control_center_demo.py](../../tests/refactor_control_center_demo.py)
- [refactor_system_ui_setup.py](../../tests/refactor_system_ui_setup.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [BrightnessControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java) | `8e91d605934d21fc` |
| [BrightnessControlOptions.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControlOptions.java) | `13d1c8cee9669cac` |
| [ControlCenterCalibration.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterCalibration.java) | `ad750d057839e101` |
| [ControlCenterCurve.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterCurve.java) | `e0e3f8e8862e574b` |
| [ControlCenterDemo.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterDemo.java) | `f9df10326eab7cd1` |
| [ControlCenterDemoState.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterDemoState.java) | `1464b3f355f43df4` |
| [ControlCenterEngine.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterEngine.java) | `46016e14936d7f35` |
| [ControlCenterHooks.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterHooks.java) | `015741447ea3287a` |
| [ControlCenterNodes.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterNodes.java) | `863d074a2629b048` |
| [ControlCenterPartition.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterPartition.java) | `80a00ea929bf8538` |
| [ControlCenterRoot.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterRoot.java) | `5d398d8c5b193afb` |
| [ControlCenterScreen.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterScreen.java) | `38370628b9178c79` |
| [ControlCenterSettings.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterSettings.java) | `47eab653db15059f` |
| [ControlCenterSetup.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterSetup.java) | `daa3c13a325dc0c9` |
| [ControlCenterStatus.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterStatus.java) | `4751c6f78fffe8d1` |
| [ManualPanelLimitSettings.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ManualPanelLimitSettings.java) | `24a5ee33acf371d6` |
| [MappingRecoveryState.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/MappingRecoveryState.java) | `846a31654e6cb880` |
| [SystemUiAccess.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SystemUiAccess.java) | `9335400ed1723c1b` |
