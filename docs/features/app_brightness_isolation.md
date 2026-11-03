# 应用临时亮度隔离：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 应用亮度隔离 · `app_brightness_isolation`

| 项目 | 要求 |
| --- | --- |
| 触发 | 隔离已启用，原生窗口覆盖同步来源、用户及屏幕可证明，且无更高优先级控制者。 |
| 需求与行为 | 仅隔离可证明来源的应用窗口亮度同步；应用退出后回系统调节，原生无同步不重复Hook；不等于暂停自动亮度 |
| 退出 / 失败 | 应用退出、主动调节、用户或物理屏变化后清理本次来源限定，不回放旧亮度。 |
| 默认 | `off` · 新配置关闭 |
| 配置键 | `app_brightness_isolation` |
| 依赖能力 | 由本项实际接口独立判断 |

## 分叉与收束

### 选择边界

独立默认关闭。按实际Class/加载器核验窗口消费者、请求来源、亮度原因与同步消费者；完整新ABI才安装5个必要Hook。三个同步入口全部缺失且窗口接口完整时采用原生无同步路径、0个Hook；残缺新结构拒绝，不按OS/机型回退。仅匹配当前主屏DPC、当前用户、已接受且正在生效的同标签窗口覆盖、同一次调用及相同亮度参数；控制中心真实调节优先至窗口释放/来源变更。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `exclusive` |
| 统一输出 | 同屏同用户窗口来源限定的同步隔离/原生无同步能力 |
| 坐标与身份 | 应用覆盖与控制中心主动输入分开 |

### 清理及回滚

HookTransaction全组回滚；ThreadLocal调用栈在返回/异常时恢复；弱引用同屏状态不持有控制器。停用/切用户/非活动/非亮屏/其他控制者/HDR时沿用系统，不写缓存亮度、不改自动模式、不全局拦截Settings。接口暂缺只裁剪运行副本，原保存开关保留。无新增服务/传感器/轮询；不承诺拦截应用直接写系统设置。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `app_override_scoped_sync` · 应用覆盖来源限定同步隔离 | 完整窗口来源、亮屏原因及同步ABI匹配；只拦截同一主屏窗口导致的偏好同步，控制中心主动调整优先。 |
| `native_window_no_sync` · 原生窗口覆盖，无所针对的同步链路 | 原生窗口接口完整，三个同步入口均不存在；不安装隔离Hook，不代表拦截应用直接写系统设置。 |

运行证据：`app_brightness_isolation.profile`、`app_brightness_isolation.detail`、`app_brightness_isolation.window_active`、`app_brightness_isolation.user_adjustment_priority`、`app_brightness_isolation.blocked_syncs`、`app_brightness_isolation.state`、`app_brightness_isolation_supported`。

验证入口：

- [refactor_app_isolation.py](../../tests/refactor_app_isolation.py)
- [refactor_feature_self_check.py](../../tests/refactor_feature_self_check.py)
- [refactor_feature_compatibility.py](../../tests/refactor_feature_compatibility.py)
- [refactor_ui_navigation.py](../../tests/refactor_ui_navigation.py)
- [refactor_app_isolation_firmware.py](../../tests/refactor_app_isolation_firmware.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [AppBrightnessIsolation.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AppBrightnessIsolation.java) | `cb53f0aa3c98d6ca` |
| [AppIsolationSettings.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AppIsolationSettings.java) | `1f2516aca56bba3f` |
| [BrightnessControlOptions.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControlOptions.java) | `13d1c8cee9669cac` |
| [HookRuntime.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookRuntime.java) | `4e3f51d5e9508d0c` |
