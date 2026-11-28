# 主屏输出隔离：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 主屏手动亮度面板 · `manual_panel`

| 项目 | 要求 |
| --- | --- |
| 触发 | 用户打开磁贴或面板并拖动或输入目标。当前主屏身份、实际节点直写能力和运行接口均已确认。 |
| 需求与行为 | 按背光节点调节，关闭自动亮度并复用统一节点保持，可设独立最高节点。主屏节点无法直写时禁用操作，保留已有设置。 |
| 退出 / 失败 | 锁屏暂停，解锁后复核同屏租约。恢复自动时验证节点权限释放与系统确认。临时常亮仅在此节点工具可用时提供。 |
| 默认 | `on_request` · 允许打开，本身不自动接管 |
| 配置键 | `manual_panel_enabled`、`manual_panel_maximum` |
| 依赖能力 | 由本项实际接口独立判断 |

### 户外节点高亮入口 · `node_output`

| 项目 | 要求 |
| --- | --- |
| 触发 | 用户显式启用节点增强及其范围/OPR依赖，原生自动输出已达到可验证上限，温控电量等保护允许；亮度钳制上限优先读取控制器内部 float 字段，旧访问器仅在字段整体缺失时采用，类型残缺拒绝启用。 |
| 需求与行为 | 户外、范围、OPR及实际框架输出条件全部满足后，复用节点租约保持；真实上限单独设置，时长跟随户外会话；钳制上限自检与写入判定共用同一选择器；户外/HBM 探测失败时仅降级本入口。主屏当前不可直写时退出节点增亮，保留设置和原生户外输出，状态显示实际沿用的增强方式。已有节点开关与目标节点值原样保留时，普通户外照度、时长和 HBM 参数可按各自的接口校验并保存。新增或改动节点目标仍需确认直写能力。 |
| 退出 / 失败 | 会话退出、熄屏、手动接管或系统有效改写后释放户外租约，冷却仍遵循户外会话。 |
| 默认 | `off` · 仅显式启用 |
| 配置键 | `outdoor_node_boost`、`outdoor_node_target` |
| 依赖能力 | `outdoor`、`outdoor_range`、`opr`、`main_panel_node` |

## 分叉与收束

### 选择边界

主屏磁贴、控制中心节点映射和户外节点增亮分别验证各自的原生接口和节点租约，共用主屏背光模式证据。识别到真实 MediaTek 主屏使用 DRM 原子调光、工厂模式或模式读数损坏时，拒绝节点直写，保存原设置。自动亮度、自动高亮和系统阳光屏由各自的原生接口决定。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `exclusive` |
| 统一输出 | 控制器统一优先级、模式代次与屏幕身份；节点写入由 RawPanelLease 管理，框架写入由系统显示链路执行。 |
| 坐标与身份 | 真实背光节点整数与framework brightness分开 |

### 清理及回滚

节点路线保留熄屏前暂停、租约失联恢复、恢复自动时的权限释放和映射重绑。折叠屏端口及控制中心监听参数独立校验。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `raw_primary_output` · 原生策略节点协作 | 优先使用实际输出策略，普通自动输出到顶后协作，保留原生保护。 |
| `automatic_strategy2_node` · 独立自动对象与原因协作 | 仅原生策略入口缺失时核验独立自动对象、同屏归属和双份亮度原因，不替换手动面板路径。 |

路线内部 ABI：`sensor_identity`、`sensor_freshness`、`outdoor_output_provenance`、`manual_node_hold_policy`、`brightness_clamp_cap`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`brightness_control.owner`、`manual_panel_direct_block`、`main-panel-node.json:mtk_led_type`、`main-panel-route.json`、`outdoor_node_supported`、`outdoor_output_profile`、`outdoor_node_physical_id`。

验证入口：

- [refactor_raw_output.py](../../tests/refactor_raw_output.py)
- [refactor_raw_panel_transaction.py](../../tests/refactor_raw_panel_transaction.py)
- [refactor_brightness_control.py](../../tests/refactor_brightness_control.py)
- [refactor_outdoor_node.py](../../tests/refactor_outdoor_node.py)
- [refactor_outdoor_fold.py](../../tests/refactor_outdoor_fold.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [BrightnessControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java) | `e7797c2cfeb536ce` |
| [FrameworkBrightness.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/FrameworkBrightness.java) | `110f02cfc0460e0d` |
| [OutdoorNodeBridge.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorNodeBridge.java) | `4ac19727d9bfa689` |
| [OutdoorOutputAccess.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorOutputAccess.java) | `9614cc8e9651a7e5` |
| [RawPanelLease.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RawPanelLease.java) | `5e6cd2ff95956c28` |
| [RawPanelOutputHooks.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RawPanelOutputHooks.java) | `14f2657c98efda0b` |
