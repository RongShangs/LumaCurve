# 辅助光感：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 辅助光感 · `assist_sensor`

| 项目 | 要求 |
| --- | --- |
| 触发 | 原生辅助槽有效，或槽缺失且存在唯一通过身份/权限/方向等检查的备用辅助源。 |
| 需求与行为 | 仅缺原生槽时按唯一可信辅助源补位，保留原生采样；没有辅助不拖停基础曲线和可用的单光感功能 |
| 退出 / 失败 | 补位失败回滚槽位与本次缓存；未接入或未采样独立显示，已有主输入继续工作。 |
| 默认 | `observe` · 原生槽优先 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

## 分叉与收束

### 选择边界

原生有效槽优先；空槽才考虑唯一且类型 / 单位 / 权限 / 线程 / 主屏均合格的移植候选，并同步当前 ABC 构造缓存。 已确认本次辅助槽为空时显示未接入，不把初始化未知或缺读数当作硬件不存在；保留补位条件和旧路线。 保存时只对同设备 / 用户 / 基准、原已开启且参数未改的辅助选择使用独立验证副本，原始选项和能力不变；新开启或改不支持参数仍拒绝。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `exclusive` |
| 统一输出 | 原生槽优先，唯一验证辅助源补位；双/单源运行状态 |
| 坐标与身份 | lux与槽/屏幕身份；不将无辅助源补成0 |

### 清理及回滚

补位失败回滚传感器槽、启用状态和本次能力缓存；其他所有者已替换时不覆盖。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `native_auxiliary` · 系统原生辅助光感 | 原生辅助槽已有传感器时优先使用，继续沿用系统采样和融合。 |
| `ported_auxiliary` · 移植系统辅助补位 | 原生槽为空；唯一 zte.sensor.light_second 候选通过类型、单位、权限、线程及主屏检查后，补入 OEM 槽并同步双光感能力缓存。 |

路线内部 ABI：`dark_primary_only`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`assist_compatibility.binding_kind`、`assist_compatibility.state`、`assist_compatibility.sensor_string_type`。

验证入口：

- [refactor_assist_compat.py](../../tests/refactor_assist_compat.py)
- [refactor_assist_compat_firmware.py](../../tests/refactor_assist_compat_firmware.py)
- [refactor_feature_self_check.py](../../tests/refactor_feature_self_check.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [AssistSensorCompat.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AssistSensorCompat.java) | `3e7b4778ae5bdcf5` |
| [AuxiliarySensorIdentity.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AuxiliarySensorIdentity.java) | `4ddea2e5f1214051` |
| [ConfigurationCompatibility.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ConfigurationCompatibility.java) | `a416969a83343312` |
| [HookRuntime.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookRuntime.java) | `f1cdc6bfa6a1d706` |
| [RootControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RootControl.java) | `6cf40b35ec4446c5` |
| [RuntimeFeatureChecks.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RuntimeFeatureChecks.java) | `5f155cb5b31f27e0` |
