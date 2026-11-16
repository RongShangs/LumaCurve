# 挂起小窗亮度隔离：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 挂起小窗亮度隔离 · `suspended_window_brightness`

| 项目 | 要求 |
| --- | --- |
| 触发 | 跟随页面唯一的应用临时亮度隔离开关；先以旧两项布尔OR合并用户意图，随后单独检查窗口能力。 |
| 需求与行为 | 正常视频小窗保留原生临时亮度；最小浮窗或贴边挂起时只将该窗口从原生亮度候选中移除，其他有效窗口/系统继续按原生选择。关闭/完全后台沿用原生可见性撤销。恢复正常小窗或模式变化发一次窗口遍历，不重放历史窗口值。与同步隔离共用一个用户开关，能力独立；系统原生隔离不因关闭模块补充处理而消失；原生手动亮度记录、付款码/摄像/HDR等原判定优先，不把原生false改true。只处理主用户主屏的应用窗口，不使用焦点或面积猜最小状态，不修改LayoutParams、全局亮度或模式，不增加轮询。 |
| 退出 / 失败 | 停用、配置变化或运行对象关闭移除规则并合并一次WM重评估；原生决定新目标。缺能力/类型异常/未知状态保留原生、保留已保存开关，不影响同步隔离或独立亮度。 |
| 默认 | `off` · 不再提供独立开关；统一隔离关闭时不主动筛选窗口，旧任一隔离已开则继承 |
| 配置键 | `app_brightness_isolation`、`suspended_window_brightness` |
| 依赖能力 | 由本项实际接口独立判断 |

## 分叉与收束

### 选择边界

完整WindowManagerServiceImpl窗口亮度门禁、Task根身份、主屏/主用户、原生最小小窗与贴边谓词和模式事件均已验证时独立安装；只筛除原生已允许的应用窗口请求，不按OS、型号、焦点或窗口面积猜测。 用户界面与同步隔离合并为一个开关；保存原文不改，运行副本先合并两旧键的OR意图，再独立按能力裁剪，禁止裁剪后再次OR而绕过缺失能力。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `exclusive` |
| 统一输出 | 输出窗口是否为已证实的主用户主屏最小/挂起应用窗口及事件重评估操作；共同过滤器只将原生true收束为false，不放宽原生false。 |
| 坐标与身份 | 窗口资格布尔、Task根身份、主用户ID0和主显示ID0；无照度/nit/节点转换。缺字段或调用异常均为未知，保留原生。 |

### 清理及回滚

两个必要Hook事务安装，残缺ABI拒绝。异常保留原生判定，不走不完整备用路线。配置关闭/停用/对象结束移除所属运行规则，并在WM handler合并为一次重评估；不保存窗口值、不改LayoutParams、不写亮度、模式或用户记录，不安装轮询。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `wm_mini_pin_gate` · 窗口最小化/贴边亮度筛选 | 窗口服务完整门禁和原生小窗谓词匹配，仅筛除主用户主屏最小或挂起应用窗口；正常小窗与原生拒绝保留。 |

路线内部 ABI：`sensor_identity`、`sensor_freshness`、`manual_node_hold_policy`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`suspended_window_brightness.profile`、`suspended_window_brightness.enabled`、`suspended_window_brightness.blocked_requests`、`suspended_window_brightness.reevaluation_queued`、`suspended_window_brightness.detail`。

验证入口：

- [refactor_window_brightness.py](../../tests/refactor_window_brightness.py)
- [refactor_window_brightness_firmware.py](../../tests/refactor_window_brightness_firmware.py)
- [refactor_brightness_control.py](../../tests/refactor_brightness_control.py)
- [refactor_configuration.py](../../tests/refactor_configuration.py)
- [refactor_feature_self_check.py](../../tests/refactor_feature_self_check.py)
- [refactor_window_brightness_ui.py](../../tests/refactor_window_brightness_ui.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [AppIsolationSettings.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AppIsolationSettings.java) | `390011a16a79bd1b` |
| [AppWindowBrightness.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AppWindowBrightness.java) | `5fc742f78b24d9f4` |
| [BrightnessControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java) | `7f6e8cc411da5e30` |
| [BrightnessControlOptions.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControlOptions.java) | `e5078bc085530fa2` |
| [ConfigurationCompatibility.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ConfigurationCompatibility.java) | `17ae92efb1760f94` |
| [WindowBrightnessAccess.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/WindowBrightnessAccess.java) | `82a709dff433a51b` |
