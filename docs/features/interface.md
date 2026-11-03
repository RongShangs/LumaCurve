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
| 需求与行为 | 按光感、场景、曲线、输出保护、实际亮度排列，卡片不随文字改变高度；常规视口不滚动，极端视口防裁切；蓝字可点，过期不造0。 |
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
| 需求与行为 | 独立自检页面按功能展示采用/未采用路线、依据和固定检测时间；查看展开只读；分析包导出确认后打开系统分享。 |
| 退出 / 失败 | 查看/展开不执行控制；导出或分享失败保留原包，并明确不完整诊断范围。 |
| 默认 | `observe` · 只控制对应界面或用户明确操作，不自动调整屏幕亮度。 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

### 关于、感谢和贡献 · `about_credits`

| 项目 | 要求 |
| --- | --- |
| 触发 | 对应页面可见或用户明确触发。 |
| 需求与行为 | 方形大图标容器内慢速错落弹幕，关于资料紧凑；动态感谢仅网站JSON，测试/代码贡献独立容器，昵称完整换行并保留来源链接；离页停止弹幕。 |
| 退出 / 失败 | 离页或图标区域离开视口即暂停弹幕；名单获取失败沿用可用缓存，不启动后台服务。 |
| 默认 | `on_request` · 只控制对应界面或用户明确操作，不自动调整屏幕亮度。 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [AboutPage.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AboutPage.java) | `be37169affb0eacd` |
| [ConfigurationFile.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ConfigurationFile.java) | `a45f2429bd244e74` |
| [FeatureCheckPage.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/FeatureCheckPage.java) | `c299b03f761fe6f0` |
| [FloatingNavigation.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/FloatingNavigation.java) | `4aedf6324f3f0d15` |
| [QuickSetupPlan.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/QuickSetupPlan.java) | `ba3faf56456e4cc8` |
| [SettingsSearch.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SettingsSearch.java) | `e6d35527a7fccfc5` |
| [StatusPageLayout.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/StatusPageLayout.java) | `82c2f0e363a062f6` |
| [ThemePalette.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ThemePalette.java) | `3d2dc43cc46249cd` |
| [UpdateChecker.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/UpdateChecker.java) | `9ac55814cfc8a321` |
| [WelcomeGuide.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/WelcomeGuide.java) | `8bbd1cdffab767a0` |
