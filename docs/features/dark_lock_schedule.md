# 日落后暗光锁定：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 日落后暗光锁定 · `dark_lock_schedule`

| 项目 | 要求 |
| --- | --- |
| 触发 | 暗光锁定及日落选项已启用，系统Twilight给出有效夜间状态。 |
| 需求与行为 | 采用本机Twilight日出日落状态，作为暗光锁定准入，不凭固定时间推断；资料未知不能冒充夜间 |
| 退出 / 失败 | 日出释放本模块取得的锁定；停用/熄屏/切用户注销本次监听与边界任务，用户手动关闭不擅自恢复。 |
| 默认 | `off` · 新配置关闭 |
| 配置键 | `dark_lock_sunset_only` |
| 依赖能力 | `dark_lock` |

## 分叉与收束

### 选择边界

可选、默认关闭。实际 LocalServices / TwilightManager / TwilightState / Listener 签名完整，时间戳覆盖当前时刻才参与；原有全天锁定独立保留。缺数据或调用失败等待，不按型号、深色模式或固定时钟猜测。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `exclusive` |
| 统一输出 | 日落日出状态与是否允许锁定 |
| 坐标与身份 | 原生太阳时间/系统时区；无结果不伪造夜间 |

### 清理及回滚

显示 Handler 复用系统 twilight 通知与一个下次边界回调；系统时钟变化重读。熄屏 / 停用 / 切用户注销本次 Listener、取消重试与边界计时；旧代际回调无效。注册异常事务撤销，最多三次就绪补试。日出仅恢复本模块取得的自动亮度，用户手动关闭不恢复。自检只查接口，不注册监听或申请定位。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `system_twilight` · 系统日出日落服务 | 复用实际 TwilightManager 与原生时间戳；只在夜间允许暗光锁定。缺时间等待，不以深色模式或固定钟点代替。 |

路线内部 ABI：`sensor_identity`、`sensor_freshness`、`manual_node_hold_policy`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`brightness_control.dark_lock_schedule.profile`、`brightness_control.dark_lock_schedule.known`、`brightness_control.dark_lock_schedule.sunrise_ms`、`brightness_control.dark_lock_schedule.sunset_ms`、`brightness_control.dark_lock_schedule.night`、`brightness_control.reason`。

验证入口：

- [refactor_brightness_control.py](../../tests/refactor_brightness_control.py)
- [refactor_feature_self_check.py](../../tests/refactor_feature_self_check.py)
- [refactor_configuration.py](../../tests/refactor_configuration.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [BrightnessControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java) | `f957288314b2835a` |
| [DarkLockSchedule.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/DarkLockSchedule.java) | `5eb5ea9490286917` |
