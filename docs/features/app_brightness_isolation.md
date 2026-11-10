# 应用临时亮度隔离：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 应用临时亮度隔离 · `app_brightness_isolation`

| 项目 | 要求 |
| --- | --- |
| 触发 | 隔离已启用，原生窗口覆盖同步来源、用户及屏幕可证明，且无更高优先级控制者。 |
| 需求与行为 | 允许原生窗口临时调亮/调暗，只阻止其同步成为系统亮度偏好。页面和入口统一命名应用临时亮度隔离，仅保留一个隔离开关及说明；原生已隔离时显示开启和系统说明，不提供伪关闭，不修改已保存模块选择。独立亮度接管期间同样保护全局偏好。 简介以阻止同步到系统亮度偏好为主语，不写允许应用调亮；保留非控制中心操作、视频播放与付款码例子，以及控制中心主动调整不受影响的说明。 |
| 退出 / 失败 | 窗口撤销或退出后交回系统，不延续窗口值；主动调节、失配、HDR或其他控制权优先。原生已隔离状态只读显示。 |
| 默认 | `off` · 新配置关闭 |
| 配置键 | `app_brightness_isolation` |
| 依赖能力 | 由本项实际接口独立判断 |

## 分叉与收束

### 选择边界

按实际类型、方法与来源选路；完整同步ABI时仅在同一主屏/用户/窗口调用帧内拦截偏好同步；三个同步入口全缺且窗口消费者完整时沿用原生隔离，不按OS版本推断。原生已隔离显示开启且不可伪关闭，不改保存的模块选择。旧版窗口共享已停用，历史路线与保存字段留档。独立亮度持有应用租约时同样阻止窗口写入全局偏好。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `per_feature` |
| 统一输出 | 来源限定同步隔离或原生隔离的有效状态；保存的模块选择单独保留 |
| 坐标与身份 | 应用覆盖与控制中心主动输入分开 |

### 清理及回滚

5个必要Hook事务回滚；ThreadLocal按栈恢复；用户主动调节、HDR、非主屏/用户、无匹配来源或不完整ABI沿用系统。仅阻止同步，不记忆或重放窗口值。页面不再提供共享和名单；不借原生状态重写用户保存配置。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `app_override_scoped_sync` · 应用覆盖来源限定同步隔离 | 完整窗口来源、亮屏原因及同步ABI匹配；只拦截同一主屏窗口导致的偏好同步，控制中心主动调整优先。 |
| `native_window_no_sync` · 原生窗口覆盖，无所针对的同步链路 | 原生窗口接口完整，三个同步入口均不存在；不安装隔离Hook，不代表拦截应用直接写系统设置。 |

路线内部 ABI：`sensor_identity`、`sensor_freshness`、`manual_node_hold_policy`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`app_brightness_isolation.profile`、`app_brightness_isolation.native_isolated`、`app_brightness_isolation.enabled`、`app_brightness_isolation.module_requested`、`app_brightness_isolation.blocked_syncs`、`app_brightness_isolation.window_active`、`app_brightness_isolation.share_enabled`、`app_brightness_isolation.share_detail`。

验证入口：

- [refactor_app_isolation.py](../../tests/refactor_app_isolation.py)
- [refactor_feature_self_check.py](../../tests/refactor_feature_self_check.py)
- [refactor_feature_compatibility.py](../../tests/refactor_feature_compatibility.py)
- [refactor_ui_navigation.py](../../tests/refactor_ui_navigation.py)
- [refactor_app_isolation_firmware.py](../../tests/refactor_app_isolation_firmware.py)
- [refactor_app_share.py](../../tests/refactor_app_share.py)
- [refactor_brightness_control.py](../../tests/refactor_brightness_control.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [AppBrightnessIsolation.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AppBrightnessIsolation.java) | `593b568b51dc57fe` |
| [AppIsolationSettings.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AppIsolationSettings.java) | `41b486e1f20ececf` |
| [BrightnessControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java) | `f957288314b2835a` |
| [BrightnessControlOptions.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControlOptions.java) | `88d3df8b4bbe475f` |
| [HookRuntime.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookRuntime.java) | `f1cdc6bfa6a1d706` |
