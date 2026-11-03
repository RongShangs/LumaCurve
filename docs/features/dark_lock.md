# 暗光锁定：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 暗光锁定 · `dark_lock`

| 项目 | 要求 |
| --- | --- |
| 触发 | 用户明确启用，亮屏自动模式进入所设暗光范围且持续满足进入确认。 |
| 需求与行为 | 达到暗光确认后取得可恢复的自动亮度控制权；可选手动后继续锁定新值或退出；光线达到退出阈值后自动恢复 |
| 退出 / 失败 | 达到退出照度并确认后恢复本模块取得的自动状态；手动调整按所选方案；停用/切用户及时释放。 |
| 默认 | `off` · 新配置关闭，手动后退出默认关闭 |
| 配置键 | `dark_lock_enabled`、`dark_lock_manual_exit`、`dark_lock_enter_lux`、`dark_lock_exit_lux`、`dark_lock_minutes`、`dark_lock_exit_seconds` |
| 依赖能力 | 由本项实际接口独立判断 |

## 分叉与收束

### 选择边界

新配置默认关闭；用户明确启用后，ABC SensorManager / 实际句柄识别；退出低频确认独立于 OEM 辅助采样；需接管 / 恢复模式权限。 绑定 ABC、双光感策略、SensorManager、实际句柄及可读物理屏身份；私有折叠主光感直出 lux；FOV 覆盖按真实选择器、驾驶状态及倍率解析，不对已换算照度重复放大。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `exclusive` |
| 统一输出 | DarkLockPolicy决策 + 已验证亮度控制能力/所有权 |
| 坐标与身份 | 策略输入lux/ms；模式与节点写入由BrightnessControl收束 |

### 清理及回滚

锁屏 / 熄屏 / 切用户 / 停用注销模块监听，只有模块取得的自动状态可恢复。 光感、管理器、控制器、倍率、面板配置或物理屏变化时释放旧监听和已拥有的锁定，清空采样及确认计时；代际监听拒绝旧队列回调。状态查看只读。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `abc_sensor_manager` · 自动亮度控制器的光感服务 | 复用本机 ABC 的 SensorManager；按传感器类型和句柄识别，锁定期间低频确认退出条件。 |

路线内部 ABI：`sensor_identity`、`sensor_freshness`、`main_light_identity`、`manual_node_hold_policy`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`brightness_control.owner`、`brightness_control.watch_main_lux`、`brightness_control.watch_assist_lux`、`brightness_control.watch_binding_active`、`brightness_control.watch_physical_display_id`、`brightness_control.watch_main_profile`。

验证入口：

- [refactor_brightness_control.py](../../tests/refactor_brightness_control.py)
- [refactor_display_scheduling.py](../../tests/refactor_display_scheduling.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [BrightnessControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java) | `8e91d605934d21fc` |
| [BrightnessSensorAccess.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessSensorAccess.java) | `c008c2c248c52e33` |
| [DarkLockPolicy.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/DarkLockPolicy.java) | `c5f8a32a3c033eb6` |
| [HookRuntime.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookRuntime.java) | `4639a1d9ec1322dc` |
