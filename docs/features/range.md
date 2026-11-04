# 高亮范围：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 开放高亮范围 · `outdoor_range`

| 项目 | 要求 |
| --- | --- |
| 触发 | 户外会话与范围开关生效，本机HBM/动态范围接口和实际允许条件完整，且 HBM 数据身份变体匹配。 |
| 需求与行为 | 只开放设备映射支持且当前允许的范围；动态范围是补充，缺HBM配置不冒充支持；数据身份变体就绪后才启用 |
| 退出 / 失败 | 户外会话结束后getter返回原生范围，不保留额外目标增强器。 |
| 默认 | `off` · 新配置关闭 |
| 配置键 | `outdoor_range_unlock` |
| 依赖能力 | `outdoor` |

## 分叉与收束

### 选择边界

HBM getter 与峰值基础范围；只有本机实际启用的动态范围对象才补充，不同时添加第二个目标增强器。 HBM 数据身份变体就绪后才启用。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `additive` |
| 统一输出 | 本机映射高亮范围支持与应用状态 |
| 坐标与身份 | 保留本机标定范围与系统保护 |

### 清理及回滚

按本次增强会话退出，范围 getter 恢复原生返回；下游保护仍有效。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `hbm_range` · HBM 与峰值范围 | 实际 HBM 最大亮度与峰值范围接口均已接入，仍受原生允许条件限制。 |
| `dynamic_range` · 照度动态范围补充 | 额外匹配 BrightnessRangeControllerImpl；仅该设备实际使用动态范围时参与。 |

路线内部 ABI：`outdoor_sdr_signature`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`outdoor.dynamic_range_supported`、`outdoor.dynamic_range_enabled`。

验证入口：

- [refactor_outdoor.py](../../tests/refactor_outdoor.py)
- [refactor_hook_transactions.py](../../tests/refactor_hook_transactions.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [OutdoorController.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorController.java) | `2be9157b433579d7` |
| [OutdoorTuning.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorTuning.java) | `84ee79e3de745ecc` |
