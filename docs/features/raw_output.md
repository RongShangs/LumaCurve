# 主屏输出隔离：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 主屏手动节点面板 · `manual_panel`

| 项目 | 要求 |
| --- | --- |
| 触发 | 用户点击磁贴或面板并拖动/输入目标，当前物理屏、权限与真实节点可验证。 |
| 需求与行为 | 磁贴拖动关闭自动亮度并复用统一节点保持；读取真实上限，可设独立最高节点；临时常亮仅本次锁屏前 |
| 退出 / 失败 | 打开自动亮度结束保持；锁屏暂停节点、解锁按租约恢复；临时常亮锁屏即清。 |
| 默认 | `on_request` · 允许打开，本身不自动接管 |
| 配置键 | `manual_panel_enabled`、`manual_panel_maximum` |
| 依赖能力 | 由本项实际接口独立判断 |

### 户外节点高亮入口 · `node_output`

| 项目 | 要求 |
| --- | --- |
| 触发 | 用户显式启用节点增强及其范围/OPR依赖，原生自动输出已达到可验证上限，温控电量等保护允许；亮度钳制上限优先读取控制器内部 float 字段，旧访问器仅在字段整体缺失时采用，类型残缺拒绝启用。 |
| 需求与行为 | 户外、范围、OPR及实际框架输出条件全部满足后，复用节点租约保持；真实上限单独设置，时长跟随户外会话；钳制上限自检与写入判定共用同一选择器；户外/HBM 探测失败时仅降级本入口，主屏节点路径独立保持可用 |
| 退出 / 失败 | 会话退出、熄屏、手动接管或系统有效改写后释放户外租约，冷却仍遵循户外会话。 |
| 默认 | `off` · 仅显式启用 |
| 配置键 | `outdoor_node_boost`、`outdoor_node_target` |
| 依赖能力 | `outdoor`、`outdoor_range`、`opr`、`main_panel_node` |

## 分叉与收束

### 选择边界

主屏输出 / 电源状态对象完整；租约、解锁、模式、屏幕状态均通过才隔离正亮度帧，第一帧留给系统。 户外协作独立匹配原生策略或完整旧自动对象 / 双原因，原手动面板检查不代替此检查。 节点上限优先读取钳制器内部 float 字段，旧访问器仅在字段整体缺失时使用。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `exclusive` |
| 统一输出 | RawPanelLease统一优先级、代次、屏幕身份及输出原因 |
| 坐标与身份 | 真实背光节点整数与framework brightness分开 |

### 清理及回滚

熄屏先撤销控制权，租约失联自动归还，系统改写优先。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `raw_primary_output` · 原生策略节点协作 | 优先使用实际输出策略，普通自动输出到顶后协作，保留原生保护。 |
| `automatic_strategy2_node` · 独立自动对象与原因协作 | 仅原生策略入口缺失时核验独立自动对象、同屏归属和双份亮度原因，不替换手动面板路径。 |

路线内部 ABI：`sensor_identity`、`sensor_freshness`、`outdoor_output_provenance`、`manual_node_hold_policy`、`brightness_clamp_cap`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`brightness_control.owner`、`outdoor_node_supported`、`outdoor_output_profile`、`outdoor_node_physical_id`。

验证入口：

- [refactor_raw_output.py](../../tests/refactor_raw_output.py)
- [refactor_raw_panel_transaction.py](../../tests/refactor_raw_panel_transaction.py)
- [refactor_outdoor_node.py](../../tests/refactor_outdoor_node.py)
- [refactor_outdoor_fold.py](../../tests/refactor_outdoor_fold.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [BrightnessControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java) | `8e91d605934d21fc` |
| [OutdoorNodeBridge.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorNodeBridge.java) | `252acb2737b9ada9` |
| [OutdoorOutputAccess.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorOutputAccess.java) | `6da5e08f75bcc90b` |
| [RawPanelLease.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RawPanelLease.java) | `5e6cd2ff95956c28` |
| [RawPanelOutputHooks.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RawPanelOutputHooks.java) | `14f2657c98efda0b` |
