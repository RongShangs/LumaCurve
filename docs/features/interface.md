# 界面与使用工具：长期需求

> 自动生成；这些交互项不伪装为系统兼容路线，原生能力仍由对应适配器决定。

### 欢迎与五页教程 · `welcome_tutorial`

| 项目 | 要求 |
| --- | --- |
| 触发 | 对应页面可见或用户明确触发。 |
| 需求与行为 | 首次必经欢迎和五页教程，重看也从欢迎开始；无返回和直接进入；欢迎期间不请求Root；完成状态不因图标切换丢失。 |
| 退出 / 失败 | 完成后保存观看状态；重看不重置亮度设置，离页结束欢迎动画。 |
| 默认 | `first_run` · 只控制对应界面或用户明确操作，不自动调整屏幕亮度。 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

### 快速设置 · `quick_setup`

| 项目 | 要求 |
| --- | --- |
| 触发 | 对应页面可见或用户明确触发。 |
| 需求与行为 | 邀请后回答六个预设问题；未选择不修改，答题过程不改亮度，最后确认统一保存；取消保留原配置。 |
| 退出 / 失败 | 取消不应用答案；最终保存失败保留原配置和可继续编辑的草稿。 |
| 默认 | `on_request` · 只控制对应界面或用户明确操作，不自动调整屏幕亮度。 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

### 设置搜索 · `settings_search`

| 项目 | 要求 |
| --- | --- |
| 触发 | 对应页面可见或用户明确触发。 |
| 需求与行为 | 功能关键词即时筛选，设置按控制逻辑分类，返回保留位置与草稿。 |
| 退出 / 失败 | 退出搜索恢复正常分组；返回二级页保留草稿和滚动位置。 |
| 默认 | `on_request` · 只控制对应界面或用户明确操作，不自动调整屏幕亮度。 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

### 状态页 · `fixed_status`

| 项目 | 要求 |
| --- | --- |
| 触发 | 对应页面可见或用户明确触发。 |
| 需求与行为 | 按光感、场景、曲线、输出保护、实际亮度排列，卡片不随文字改变高度；常规视口不滚动，极端视口防裁切；蓝字可点，过期不造0。 自动模式未知单独标明，不误判为手动；等待连接的后台刷新只显示页内状态，不持续Toast。 有效模式按owner字段、原生策略或整体缺策略的旧DPC实例字段分叉后统一读取，不以用户设置替代运行模式。 |
| 退出 / 失败 | 离页停止无用界面刷新；未知读数不以0或旧值占位。 |
| 默认 | `observe` · 只控制对应界面或用户明确操作，不自动调整屏幕亮度。 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

### 悬浮导航与滚动 · `floating_navigation`

| 项目 | 要求 |
| --- | --- |
| 触发 | 对应页面可见或用户明确触发。 |
| 需求与行为 | 紧凑圆角阴影，指示器平移、按压和收放缩放；设置/关于按滚动方向隐藏，触底不强制弹出；隐藏系统导航栏也保留底距。 |
| 退出 / 失败 | 切页、隐藏或尺寸变化结束旧动画，程序定位和触底不伪造滚动手势。 |
| 默认 | `native` · 只控制对应界面或用户明确操作，不自动调整屏幕亮度。 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

### 深浅色界面 · `interface_theme`

| 项目 | 要求 |
| --- | --- |
| 触发 | 对应页面可见或用户明确触发。 |
| 需求与行为 | 底栏、卡片、未采用路线、弹窗和弹幕统一可读；与深色独立亮度曲线分开，切换保留草稿和滚动。 |
| 退出 / 失败 | 主题切换不重建亮度配置或偏好；界面动画按生命周期停止。 |
| 默认 | `native` · 只控制对应界面或用户明确操作，不自动调整屏幕亮度。 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

### 检查更新与下载 · `update_notice`

| 项目 | 要求 |
| --- | --- |
| 触发 | 对应页面可见或用户明确触发。 |
| 需求与行为 | 每次进入前台仅在状态页自动弹一次；忽略后该版本不弹，关于保留提示；官网与GitHub并排下载；操作按钮完整、日志可滚动，网络失败不能冒充无更新。 |
| 退出 / 失败 | 忽略仅针对该版本；网络失败显示无法检查，下次进入按已保存忽略状态处理。 |
| 默认 | `on_request` · 只控制对应界面或用户明确操作，不自动调整屏幕亮度。 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

### 配置工具与恢复默认 · `configuration_tools`

| 项目 | 要求 |
| --- | --- |
| 触发 | 对应页面可见或用户明确触发。 |
| 需求与行为 | 配置导入导出用于个人备份，校验本机范围；保存等同revision确认，失败保留草稿并回退；恢复系统默认名称与功能一致。 |
| 退出 / 失败 | 提交失败回退此前配置并保留草稿；恢复默认需按本机实际支持的项目应用。 |
| 默认 | `on_request` · 只控制对应界面或用户明确操作，不自动调整屏幕亮度。 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

### 运行与兼容及诊断 · `compatibility_diagnostics`

| 项目 | 要求 |
| --- | --- |
| 触发 | 对应页面可见或用户明确触发。 |
| 需求与行为 | 独立自检页面按功能展示采用/未采用路线、依据和固定检测时间；查看展开只读；分析包导出确认后打开系统分享。 自动亮度模式读取失败独立记录，保留曲线、运行日志及其他诊断，不使整份状态消失。 |
| 退出 / 失败 | 查看/展开不执行控制；导出或分享失败保留原包，并明确不完整诊断范围。 |
| 默认 | `observe` · 只控制对应界面或用户明确操作，不自动调整屏幕亮度。 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

### 关于、感谢和贡献 · `about_credits`

| 项目 | 要求 |
| --- | --- |
| 触发 | 对应页面可见或用户明确触发。 |
| 需求与行为 | 方形大图标容器内慢速错落弹幕，关于资料紧凑；日常动态感谢以网站JSON为源，每次打包发布同步最新离线JSON，APK与源码核验一致；测试/代码贡献独立容器，昵称完整换行并保留来源链接；离页停止弹幕。 |
| 退出 / 失败 | 离页或图标区域离开视口即暂停弹幕；名单获取失败沿用可用缓存，不启动后台服务。 |
| 默认 | `on_request` · 只控制对应界面或用户明确操作，不自动调整屏幕亮度。 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

### 统一应用名单管理 · `application_list_management`

| 项目 | 要求 |
| --- | --- |
| 触发 | 进入应用独立亮度页面，点击三个块或分组的灰色管理入口。 窗口内容安装后才读取系统栏控制器，兼容PhoneWindow尚未创建DecorView及控制器暂为空。 |
| 需求与行为 | 页面上方介绍和跨锁屏/跨重启选项，下面暂停、独立、分组独立三个块；入口仅显示应用管理及数量。二级页面统一顶栏搜索、图标/名称/开关、已选计数和并排的选中视频应用/选中游戏应用；分类附加选择，未分类应用可手选，保留按包名添加及未安装条目。标明其他名单归属，选择其他名单已有应用时先弹窗，确认后移入并自动关闭旧处，取消不变；批选涉及迁移时统一确认一次，分组可新增/重命名/删除，最多3组。 所有名单合计沿用64个应用上限；移动已有应用不额外占用名额。 视频批选以系统分类及明确的视频应用包名补充，覆盖常用国内视频应用，仅选本机已安装条目，不按名称模糊匹配；迁移确认使用统一24dp圆角自适应弹窗。 迁移弹窗确认/取消按钮使用与其他弹窗相同的等权等宽48dp操作区、水平文字留白、14dp正文间隔，保留24dp外圆角与原有确认语义。 所有应用选择页复用其他二级页的返回箭头绘制与标题字号/留白；仅顶栏与搜索框固定，视频/游戏批选、计数、按包名入口和应用数据在同一个滚动区内；移除常驻分类/保存灰字。应用行共用一个连续大圆角背景，保留原生列表回收，不逐行画独立卡片或嵌套展开全量名单。底部导航/键盘安全间距置于滚动区内部，页面内容延伸到透明导航栏下。 应用名单页保留与主题一致的固定不透明窗口背景，系统整窗进入/返回动画关闭；仅内容执行与其他二级页面相同的12dp轻位移和180ms进入/120ms退出淡变，遵守系统关闭动画。快速返回取消进入动画，返回结果只提交一次；切到后台取消内容动画并复原透明度/位移，退出中被打断仍完成返回，旧完成回调不重放。分组删除和重命名的按钮仅持有稳定ID，在点击及确认时查询当前配置，不因同内容配置重新解析而操作失效对象；取消不改草稿，删除后仍需保存并应用。 |
| 退出 / 失败 | 返回携带名单草稿，保留搜索/选择的旋转状态；离页停止图标/分类后台工作，过期回调不得更新页面。 已确认迁移后再取消当前勾选，旧处仍保持关闭；旋转保留确认过的迁移记录，未确认弹窗离页取消。 |
| 默认 | `on_request` · 页面只编辑草稿，保存并应用后生效 |
| 配置键 | `manual_brightness_apps`、`independent_brightness_apps`、`independent_brightness_groups` |
| 依赖能力 | `apps`、`app_independent` |

### 旧版窗口亮度共享（已停用） · `app_brightness_share`

| 项目 | 要求 |
| --- | --- |
| 触发 | 不再启用；兼容保留历史字段，不执行任何窗口共享。 |
| 需求与行为 | 移除共享、保留期限及隔离名单UI。保留旧配置字段以免升级丢失原文，但不读取窗口值作为独立亮度偏好。 功能自检不再显示已停用条目。 |
| 退出 / 失败 | 无运行时共享状态或旧值重放；新的应用独立亮度单独配置和判断能力。 |
| 默认 | `off` · 新配置及旧配置缺字段均关闭 |
| 配置键 | `app_brightness_isolation_share` |
| 依赖能力 | `app_brightness_isolation` |

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [AboutPage.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AboutPage.java) | `20455e7425490514` |
| [AppBrightnessIsolation.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AppBrightnessIsolation.java) | `593b568b51dc57fe` |
| [ApplicationBrightnessSettings.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ApplicationBrightnessSettings.java) | `717f95f514b9a8df` |
| [ApplicationCategories.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ApplicationCategories.java) | `ebb67062fcd250e8` |
| [ApplicationListActivity.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ApplicationListActivity.java) | `d635c67d2d4abfbb` |
| [ApplicationSelection.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ApplicationSelection.java) | `5000cff5ae47ae03` |
| [AutomaticModeAccess.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AutomaticModeAccess.java) | `89cb3474888f3cb7` |
| [BrightnessControlOptions.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControlOptions.java) | `88d3df8b4bbe475f` |
| [ConfigurationFile.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ConfigurationFile.java) | `a45f2429bd244e74` |
| [FeatureCheckPage.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/FeatureCheckPage.java) | `c299b03f761fe6f0` |
| [FloatingNavigation.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/FloatingNavigation.java) | `4aedf6324f3f0d15` |
| [HookRuntime.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookRuntime.java) | `f1cdc6bfa6a1d706` |
| [MainActivity.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/MainActivity.java) | `277938475f735b22` |
| [QuickSetupPlan.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/QuickSetupPlan.java) | `ba3faf56456e4cc8` |
| [SettingsSearch.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SettingsSearch.java) | `e6d35527a7fccfc5` |
| [StatusPageLayout.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/StatusPageLayout.java) | `82c2f0e363a062f6` |
| [ThemePalette.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ThemePalette.java) | `3d2dc43cc46249cd` |
| [UpdateChecker.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/UpdateChecker.java) | `9ac55814cfc8a321` |
| [WelcomeGuide.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/WelcomeGuide.java) | `8bbd1cdffab767a0` |
