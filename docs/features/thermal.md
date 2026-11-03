# 显示层温控：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 显示层温控调整 · `thermal`

| 项目 | 要求 |
| --- | --- |
| 触发 | 用户明确开启显示温控调整，实际屏幕限亮策略调用处于允许修改的温度与保护条件。 |
| 需求与行为 | 只调整屏幕限亮，不影响整机其他温控；严重温控、电量等保护保留；实际温度限亮时数值及单位标红 |
| 退出 / 失败 | 每次调用或异常后恢复仍持有的临时显示限亮字段，不写整机温控。 |
| 默认 | `off` · 新配置关闭 |
| 配置键 | `thermal_relax`、`thermal_ceiling`、`thermal_cooling` |
| 依赖能力 | 由本项实际接口独立判断 |

## 分叉与收束

### 选择边界

显示层 HDR / SDR 消费者完整，严重温控与电池门槛继续有效；不改 SoC / 充电温控。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `exclusive` |
| 统一输出 | 温控策略结果与本次覆盖所有权 |
| 坐标与身份 | 电池摄氏温度；输出上限沿本机坐标 |

### 清理及回滚

调用结束或异常时由FieldOverride逆序恢复仍持有的显示限亮字段；停止后沿用系统控制。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `display_thermal` · 显示层温控接口 | 仅调整显示亮度限制；严重温控、电池温度门槛及其他系统温控继续有效。 |

路线内部 ABI：`module_loader_discovery`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`thermal_supported`、`thermal_skipped`。

验证入口：

- [refactor_advanced_hooks.py](../../tests/refactor_advanced_hooks.py)
- [refactor_hook_firmware.py](../../tests/refactor_hook_firmware.py)
- [refactor_review_safety.py](../../tests/refactor_review_safety.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [FieldOverride.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/FieldOverride.java) | `6b47448ffaf6ac29` |
| [HookEntry.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookEntry.java) | `54d97a0f8f874090` |
| [HookRuntime.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookRuntime.java) | `4e3f51d5e9508d0c` |
| [ThermalPolicy.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ThermalPolicy.java) | `673d032fdaea9f5b` |
