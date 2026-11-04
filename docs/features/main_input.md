# 主光感：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 主光感 · `main_sensor`

| 项目 | 要求 |
| --- | --- |
| 触发 | 系统为当前默认内部屏选定并注册实际主输入；不以传感器存在推断正在采样。 |
| 需求与行为 | 标准主光感、已选私有主屏输入和FOV分别验证解码；真实0 lux与未采样分开，不为补适配另建采样器 旧版无倍率双光感仅在单位lux及同源ABI完整时接入，不用默认倍率掩盖错误接口。 |
| 退出 / 失败 | 光感或主屏对象变化后重新核验；失去读数显示未确认，不保留旧采样作为当前值。 |
| 默认 | `observe` · 沿用系统实际选择 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

## 分叉与收束

### 选择边界

优先保留当前默认逻辑屏 ABC 的 TYPE_LIGHT；新增 ABC 已直接选中的 xiaomi.sensor.light_secondary 非唤醒、按变化、无权限且量程 / 精度有效的照度源。不枚举，不按私有类型编号或机型选择，不将原始 / 工厂 / 流式通道当作 lux。 配置槽非标准且 OEM 开启 FOV 时，核验其默认屏标识、真实 FOV 对象和 Sensor→Sensor 选择器结果，仅使用选择器实际返回的源；虚拟 FOV 的 values[0] 已为 lux，确认驾驶数据包才读取 values[2] 并用原生倍率。优先接口残缺 / 抛错拒绝，不回落物理槽；标准 TYPE_LIGHT 优先原路径，不受新 FOV 判定影响。 当前未接入辅助源时独立沿用已确认主光感，状态读取 ABC 已接受照度；不伪造辅助样本，也不把未接入等同没有硬件。 旧DualSensorPolicy没有两项倍率时，按已验证的同主屏标准光感、实例float照度、原生消费方法及owner/manager归属采用单位lux；残缺倍率仍失败。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `exclusive` |
| 统一输出 | 已验证的当前主屏Sensor与照度解码、可用性证据 |
| 坐标与身份 | 照度lux；真实0、未采样、无接口分别表示 |

### 清理及回滚

只读观察，不拥有 OEM 监听。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `oem_main` · 系统标准主光感 | 读取当前主屏 ABC 选用的 TYPE_LIGHT 光感，沿用原适配。 |
| `oem_selected_secondary` · 系统选用的折叠光感 | 当前主屏 ABC 直接选用已验证的私有照度光感；拒绝原始、工厂、流式及其他屏幕候选。 |
| `oem_selected_fov` · 系统 FOV 融合光感 | 沿用主屏原生光感选择器返回的 FOV 源和照度解析；不混用 ABC 保留的物理光感槽。 |

路线内部 ABI：`dark_primary_only`、`main_light_identity`、`main_input_lux_scale`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`sensor_status`、`sensor_sampling_enabled`、`ambient_lux_valid`、`main_fast_lux`、`main_input_compatibility`、`assist_source_present`、`main_reading_source`、`main_input_compatibility.lux_scale_route`。

验证入口：

- [refactor_feature_self_check.py](../../tests/refactor_feature_self_check.py)
- [refactor_status_presentation.py](../../tests/refactor_status_presentation.py)
- [refactor_brightness_control.py](../../tests/refactor_brightness_control.py)
- [refactor_fold_sensor_firmware.py](../../tests/refactor_fold_sensor_firmware.py)
- [refactor_single_sensor_firmware.py](../../tests/refactor_single_sensor_firmware.py)
- [refactor_legacy_input.py](../../tests/refactor_legacy_input.py)
- [refactor_legacy_input_firmware.py](../../tests/refactor_legacy_input_firmware.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [BrightnessControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java) | `8e91d605934d21fc` |
| [BrightnessSensorAccess.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessSensorAccess.java) | `fdfeca59df5414c8` |
| [HookRuntime.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookRuntime.java) | `4e3f51d5e9508d0c` |
| [RuntimeFeatureChecks.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RuntimeFeatureChecks.java) | `5cdff234d6de8d09` |
| [SensorState.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SensorState.java) | `793f6219eced1bd4` |
