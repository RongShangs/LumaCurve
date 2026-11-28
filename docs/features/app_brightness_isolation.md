# 应用临时亮度隔离：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 应用临时亮度隔离 · `app_brightness_isolation`

| 项目 | 要求 |
| --- | --- |
| 触发 | 用户打开统一隔离开关并保存；Beta19旧两项设置中任一已开即继承开启。运行副本先合并意图，再分别按同步与小窗能力裁剪。 |
| 需求与行为 | 页面只保留“隔离应用临时亮度”一个开关，统一阻止窗口临时亮度同步成为系统亮度偏好，并按窗口状态处理请求：正常视频小窗保留临时亮度；最小浮窗或贴边挂起时忽略该窗口请求，交回系统或其他有效窗口；关闭或完全后台由原生撤销；控制中心主动调整优先。保留非控制中心操作、视频播放和付款码说明，不记忆、延续或重放临时亮度。保存的统一意图与系统原生状态分开：原生已隔离时明确说明原生功能仍在，开关控制模块补充处理，不假称可以关闭系统原生隔离。两项能力独立检查、独立失败；缺一项保留另一项并显示部分能力未就绪，不用统一开关代替兼容检测。独立亮度租约仍保护系统偏好。 四种状态以窗口状态/处理方式两列表格展示，不写成长段文字；表格共用一个14dp圆角背景并裁剪外部四角，内部只用细分隔线，正文可换行且两列按宽度分配，统一浅深色语义底色与文字；保留简短视频/付款码说明。 页面保留简短视频/付款码示例与圆角规则表格；不显示重复的“不记忆或延续”和正常启用确认。原生隔离未关闭及能力未就绪提示保留，隐藏行不占高度。 无旧同步入口但存在完整原生亮度保持交接时，阻止经确认的当前窗口将临时亮度交给系统自动保持；按物理屏、当前用户、配置和合法调用帧匹配，保留窗口的即时亮度与控制中心主动输入，不回写或归一化原生亮度。 |
| 退出 / 失败 | 关闭统一开关同时撤销模块同步隔离与最小/挂起请求过滤；原生自带隔离继续保留。缺接口不改保存原文；所有旧路线、主动调节、HDR及其他控制权规则保留。 |
| 默认 | `off` · 模块统一开关默认关闭；已有任一显式隔离选择继承开启，原生行为不变 |
| 配置键 | `app_brightness_isolation`、`suspended_window_brightness` |
| 依赖能力 | 由本项实际接口独立判断 |

## 分叉与收束

### 选择边界

按实际类型、方法与来源选路；完整同步ABI时仅在同一主屏/用户/窗口调用帧内拦截偏好同步；三个同步入口全缺时确认原生保持交接链路，完整则阻止应用亮度写入保持策略，保持交接标记也缺失时沿用原生隔离，残缺或类型错误不回退，不按OS版本推断。原生隔离作为实际状态说明保留；页面唯一开关控制模块同步隔离与最小/挂起处理，不假称能关闭原生行为。旧版窗口共享已停用，历史路线与保存字段留档。独立亮度持有应用租约时同样阻止窗口写入全局偏好。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `per_feature` |
| 统一输出 | 来源限定同步隔离、保持交接隔离或原生隔离的有效状态；保存的模块选择单独保留 |
| 坐标与身份 | 应用覆盖与控制中心主动输入分开 |

### 清理及回滚

同步5个、保持交接4个必要Hook事务回滚；ThreadLocal按栈恢复；用户主动调节、HDR、非主屏/用户、无匹配来源或不完整ABI沿用系统。仅阻止同步，不记忆或重放窗口值。页面只提供一个统一隔离开关；Beta19任一旧选择已开即继承。原生状态不重写用户保存配置。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `app_override_scoped_sync` · 应用覆盖来源限定同步隔离 | 完整窗口来源、亮屏原因及同步ABI匹配；只拦截同一主屏窗口导致的偏好同步，控制中心主动调整优先。 |
| `app_override_native_handoff` · 应用窗口亮度保持交接隔离 | 三项同步入口全缺且原生双控制器保持链路完整，只拦截同物理屏、同用户、来源已确认的窗口保持启动。 |
| `native_window_no_sync` · 原生窗口覆盖，无所针对的同步链路 | 原生窗口接口完整，且同步与保持交接入口均不存在时沿用原生。 |

路线内部 ABI：`sensor_identity`、`sensor_freshness`、`manual_node_hold_policy`、`app_override_native_handoff_abi`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`app_brightness_isolation.profile`、`app_brightness_isolation.native_isolated`、`app_brightness_isolation.enabled`、`app_brightness_isolation.module_requested`、`app_brightness_isolation.blocked_syncs`、`app_brightness_isolation.window_active`、`app_brightness_isolation.share_enabled`、`app_brightness_isolation.share_detail`、`app_brightness_isolation.blocked_handoffs`。

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
| [AppBrightnessIsolation.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AppBrightnessIsolation.java) | `ac83a75b791ac3ca` |
| [AppIsolationSettings.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AppIsolationSettings.java) | `390011a16a79bd1b` |
| [BrightnessControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java) | `e7797c2cfeb536ce` |
| [BrightnessControlOptions.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControlOptions.java) | `e5078bc085530fa2` |
| [HookRuntime.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookRuntime.java) | `36596cadff1071b5` |
