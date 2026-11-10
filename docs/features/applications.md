# 应用独立亮度与暂停名单：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 暂停自动亮度名单 · `apps`

| 项目 | 要求 |
| --- | --- |
| 触发 | 当前默认屏上实际可见任务与用户名单匹配；缺多窗口支持时才按焦点判断。 |
| 需求与行为 | 主屏可见任务优先，含小窗分屏；仅没有多窗口接口才回退焦点；全部名单不可见才恢复；尊重用户主动开启自动亮度；新页面将原名单作为独立块接续，配置键和未安装包名保留。与独立名单、各分组互斥；用户确认移入另一个名单后原处同步移除，取消则保持两边原样。 |
| 退出 / 失败 | 所有匹配的可见应用消失才恢复本模块暂停的自动亮度；用户主动开启优先。 |
| 默认 | `off` · 名单为空 |
| 配置键 | `manual_brightness_apps` |
| 依赖能力 | 由本项实际接口独立判断 |

### 应用独立亮度 · `app_independent`

| 项目 | 要求 |
| --- | --- |
| 触发 | 默认主屏同用户已核验的焦点应用属于独立名单或分组，实际原生普通范围和手动输入接口完整；无更高优先级节点/手动面板接管。 自检只核对接口，不安装输入Hook；该观察延迟到首次实际独立接管，租约恢复也只读检查，不因闲置功能增加启动Hook。 |
| 需求与行为 | 进入后暂停自动亮度并使用逐应用或按稳定组ID保存的固定比例，普通页面也生效。每个应用只能在暂停名单、独立名单或一个分组中，最多3组、全部名单合计最多64个应用。只接受明确的原生手动亮度输入；即使播放窗口显示中，用户控制中心的普通调节仍可记忆，但窗口自身调用帧拒绝采集。窗口提亮、付款码、HDR、极端值和受保护范围不记忆；未记忆时使用系统首选值，不采集窗口实际亮度。同组切换不反复开关模式。 独立接管及退出恢复不得写入原生曲线或短期学习模型；恢复全局基线后先通过原生消费者排空待处理设置、清除临时亮度和用户更新标记，再恢复原模式；快速退出同样识别尚未合并的真实控制中心调节。 原生关闭自动亮度的异步交接可能覆盖首次写入；接管时仅在原生设置消费事件后，离开原生锁栈再检查实际手动模式并单次补交付记忆值，不轮询，不重复记忆；待处理真实手势及其他来源后写值优先。退出、锁屏、换组和代次变化撤销旧回调。 手动输入在延迟合并前快速退回桌面、切换独立应用或分组时，先按原租约与原记忆键补存最后一次有效调节；前台查询未完成时保留候选，不能记入新应用或被原生交接覆盖。仅补存明确输入，不从当前屏幕读数猜测偏好，不在离开时重写该输入。 |
| 退出 / 失败 | 离开后恢复进入前的模式及仍拥有的系统基线；原来手动就保持手动，用户主动开启自动亮度优先。锁屏撤销接管，默认清空记忆；显式保留后仅在对应应用回前台时使用。跨重启还需跨锁屏启用；按设备基线、物理屏和用户校验，旧异步结果不得重放。 系统进程重启只恢复同设备/用户/物理屏、首选值仍匹配租约时的基线，不覆盖后来写入的亮度。 锁屏默认仍清空；显式跨锁屏保留时，冻结待处理输入并在退出原生锁栈后验证、补存，重复屏幕事件不丢失或重放。待处理输入仍属于本租约时，先恢复基线及清理原生待处理设置再交回原模式。跨重启记忆读取被执行器拒绝不标记已加载，同一绑定最多提交3次，间隔250/1000毫秒；离页、锁屏、停用及代次失效撤销重试，实际失败不持续轮询，未读取的存档不被空快照覆盖。 |
| 默认 | `off` · 独立名单与分组为空，跨锁屏/跨重启保留均关闭；旧暂停名单不变 |
| 配置键 | `independent_brightness_apps`、`independent_brightness_groups`、`independent_brightness_keep_lock`、`independent_brightness_keep_reboot` |
| 依赖能力 | `apps` |

## 分叉与收束

### 选择边界

旧暂停名单继续使用manual_brightness_apps及可见任务优先/缺接口才焦点回退。独立应用仅匹配已核验的同用户默认屏焦点，逐应用或稳定组ID选择一个固定值；最多3组、名单互斥。普通范围、窗口覆盖读数及原生setTemporaryBrightness接口完整才安装独立输入观察；不从窗口或实际输出学习，不按机型版本判断。视频分类取ApplicationInfo.category并按精确包名补充常用国内视频应用，游戏另可取本机专用分类文件；混合影像名单不整份当作视频。 迁移先生成只读草稿，弹窗确认后记录原处关闭；取消不得修改任何旧选择。批选迁移一次确认，返回时名单和已确认移除记录原子提交。 手动输入按真实原生入口及调用帧识别；窗口处于显示状态不等于该输入来自窗口，用户普通控制中心输入可记忆，窗口自身调用帧排除。 独立亮度接口探测与观察安装分离：启动/自检只读，首次实际接管才安装输入观察；恢复旧租约不因此安装新观察。 独立接口还核验ABC原生用户学习入口及DBC设置消费/临时值清理入口，按物理对象和显示线程隔离独立会话学习。 独立手动交接观察复用已核验DBC设置消费者，只在当前物理主屏/显示线程发出有界单次任务；实际模式与亮度的读取、写入均延后到原生锁栈退出。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `per_feature` |
| 统一输出 | 可见暂停名单与焦点独立名单分别收束，唯一应用/组记忆键和模式租约统一管理 |
| 坐标与身份 | 默认主屏同用户；固定值为框架亮度比例，不伪造nit，不把窗口/HDR输出当用户偏好 |

### 清理及回滚

复用BrightnessControl应用租约，模式改动前持久记录恢复意图；退出恢复原模式及仍拥有的基线，用户主动开启自动亮度优先。暂停名单可见性规则保持；独立名单按焦点退出。屏幕事件立即撤销手动输入许可，清理延迟任务；默认锁屏清空，可显式保留锁屏/重启记忆。用户/物理屏/范围标定绑定变化拒绝旧值，异步读取按代次丢弃，写入合并为有界快照。列表页离开停止读取图标及分类，无新服务或持续轮询。 名单页先创建窗口内容再取系统栏控制器，控制器为空保留默认外观，不阻止打开页面。 退出仍拥有的独立值先恢复基线，原生消费待处理设置并清除临时值/用户更新标记后才恢复自动模式；不动外部后来写入的亮度。 等待原生模式确认的补交付按租约代次取消；先到的用户拖动与外部后来写入优先，不后台重试或轮询。 名单页只固定顶栏与搜索，批选/计数/名单共用列表滚动；连续圆角背景只画数据区域，行保持透明与回收。底部安全距离放滚动尾部，不缩短整个页面视口；导航栏透明，键盘出现时按实际遮挡预留。 分组页面缓存只缓存表现，删除/重命名回调按稳定ID解析当前草稿；确认前再次解析，已删除ID不复活或命中同名新组。名单Activity保持主题不透明窗口与静止背景，关闭双向系统整窗动画，仅内容淡变/轻位移，禁用系统动画时直接呈现。快速返回/离页取消旧内容动画并复原属性，退出被后台打断仍返回一次原草稿，不留旧完成回调。 快速交接先按原租约补存未合并的明确输入；跨锁屏候选在原生锁栈外处理，默认清空仍保留。存档提交被执行器拒绝不视为已加载，同一绑定最多3次、250/1000毫秒有界重试；锁屏/配置/停用撤销重试，过期回执拒绝，未读存档不被快照覆盖。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `visible_tasks` · 可见任务路径（含小窗 / 分屏） | 可见任务接口完整；任一名单应用仍可见时保持暂停。 |
| `focus_fallback` · 前台焦点回退路径 | 没有完整的可见任务能力时按系统焦点判断，并提示小窗限制。 |
| `focused_app_fixed_manual` · 应用或分组独立固定亮度 | 当前默认主屏同用户焦点应用匹配互斥名单，原生普通范围与手动输入接口完整；复用应用模式租约，退出恢复此前系统调节。 |

路线内部 ABI：`sensor_identity`、`sensor_freshness`、`foreground_user_field`、`task_visibility_field`、`manual_node_hold_policy`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`brightness_control.app_multiwindow_supported`、`app_independent_supported`、`brightness_control.app_independent.profile`、`brightness_control.app_independent.active`、`brightness_control.app_independent.key`、`brightness_control.app_independent.remembered`、`brightness_control.foreground_visibility_pending`、`brightness_control.app_independent.native_mode_pending`、`brightness_control.app_independent.native_reapply_queued`、`brightness_control.app_independent.memory_loaded`、`brightness_control.app_independent.memory_load_attempts`、`brightness_control.app_independent.memory_load_retry_queued`。

验证入口：

- [refactor_brightness_control.py](../../tests/refactor_brightness_control.py)
- [refactor_foreground_firmware.py](../../tests/refactor_foreground_firmware.py)
- [refactor_app_independent.py](../../tests/refactor_app_independent.py)
- [refactor_app_share.py](../../tests/refactor_app_share.py)
- [refactor_app_helper.py](../../tests/refactor_app_helper.py)
- [refactor_application_lifecycle.py](../../tests/refactor_application_lifecycle.py)
- [refactor_app_groups.py](../../tests/refactor_app_groups.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [AppIndependentAccess.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AppIndependentAccess.java) | `8b286b6e73e9c8e4` |
| [AppIndependentControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AppIndependentControl.java) | `d0de0f50fc1ea1bb` |
| [AppIndependentPolicy.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AppIndependentPolicy.java) | `bb280e32ae39556e` |
| [ApplicationBrightnessSettings.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ApplicationBrightnessSettings.java) | `717f95f514b9a8df` |
| [ApplicationCategories.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ApplicationCategories.java) | `ebb67062fcd250e8` |
| [ApplicationListActivity.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ApplicationListActivity.java) | `d635c67d2d4abfbb` |
| [ApplicationSelection.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ApplicationSelection.java) | `5000cff5ae47ae03` |
| [BrightnessControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java) | `f957288314b2835a` |
| [BrightnessControlOptions.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControlOptions.java) | `88d3df8b4bbe475f` |
| [DeferredStateWriter.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/DeferredStateWriter.java) | `a1e1a507466fbff5` |
| [ForegroundApps.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ForegroundApps.java) | `02d7d3249bc8938c` |
