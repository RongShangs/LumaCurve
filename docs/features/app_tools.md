# APP 本地工具：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 桌面图标显示 · `launcher`

| 项目 | 要求 |
| --- | --- |
| 触发 | 用户明确切换桌面图标显示。 |
| 需求与行为 | 仅切换Launcher alias，保留LSPosed入口、欢迎完成状态和所有亮度设置 |
| 退出 / 失败 | 组件切换不重建偏好，隐藏后仍可从LSPosed入口进入；操作失败保留原选择。 |
| 默认 | `native` · 默认显示图标 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

### 界面偏好 · `preferences`

| 项目 | 要求 |
| --- | --- |
| 触发 | APP启动恢复本地界面偏好，或用户修改刷新/主题/图标等偏好。 |
| 需求与行为 | 欢迎、刷新、图标等独立保存，不因配置导入或切组件清空 |
| 退出 / 失败 | 组件切换、页面重建和配置导入不清个人界面偏好；销毁时取消UI回调。 |
| 默认 | `native` · 跟随系统及本地选择 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

### 文件及分享 · `app_files`

| 项目 | 要求 |
| --- | --- |
| 触发 | 用户主动导出分析包或文件，再明确确认分享。 |
| 需求与行为 | 保留原始分析包，使用只读受控URI打开系统分享；取消分享不删除原包，不扩大文件访问范围 |
| 退出 / 失败 | 取消或分享失败保留已经导出的包；URI仅授予该文件临时只读权限。 |
| 默认 | `on_request` · 用户主动生成 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

## 分叉与收束

### 选择边界

独立 Launcher alias / 原偏好存储 / 私有文件权限；不使用启动组件名重新建立偏好。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `per_feature` |
| 统一输出 | 界面偏好、窗口可用尺寸和分析包分享URI |
| 坐标与身份 | 像素/Insets只在布局边界；不触碰引擎设置 |

### 清理及回滚

别名切换保留设置、欢迎记录；APP 销毁取消 UI 回调。 悬浮底栏只响应设置/关于的实际滚动手势，离开时结束动画；搜索只筛选和跳转，不改配置或调用Root；日志兼容分区只消费既有报告，不探测接口。 越界回弹只平移滚动内容，归位不产生滚动事件；隐藏/尺寸变化/程序定位/移除时取消。诊断分享只授予单个压缩包临时只读权限，分享失败不重做导出或改动原包。 关于图标容器的弹幕只在前台、当前页且位于滚动视口内时推进；切页、教程、隐藏、销毁及关闭动画时停止，不建立后台服务。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `launcher_alias` · 桌面入口别名 | 使用 PackageManager 的独立桌面别名，保留模块设置入口和欢迎引导完成状态。 |
| `app_preferences` · 应用本地偏好存储 | 读取设置草稿与欢迎记录，不清空、不改变开关。 |
| `app_files` · 应用私有文件目录 | 检查配置导入导出所用目录的可访问性。 |

路线内部 ABI：`ui_preferences_migration`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`APP.appFeatureReport`、`APP.settings_search_query`、`APP.independent_log_connection_stages`。

验证入口：

- [refactor_ui_preferences.py](../../tests/refactor_ui_preferences.py)
- [refactor_app_preferences.py](../../tests/refactor_app_preferences.py)
- [refactor_welcome.py](../../tests/refactor_welcome.py)
- [refactor_floating_search.py](../../tests/refactor_floating_search.py)
- [refactor_log_summary.py](../../tests/refactor_log_summary.py)
- [refactor_scroll_elasticity.py](../../tests/refactor_scroll_elasticity.py)
- [refactor_diagnostic_sharing.py](../../tests/refactor_diagnostic_sharing.py)
- [refactor_about_page.py](../../tests/refactor_about_page.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [AboutHeroView.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AboutHeroView.java) | `dd2a925b8c7b0f2d` |
| [AboutMarquee.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AboutMarquee.java) | `fc965fe937837380` |
| [AboutPage.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AboutPage.java) | `be37169affb0eacd` |
| [DiagnosticShareFiles.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/DiagnosticShareFiles.java) | `7bb6de497bb904bf` |
| [DiagnosticShareProvider.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/DiagnosticShareProvider.java) | `94a085e0b46f3046` |
| [DiagnosticSharing.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/DiagnosticSharing.java) | `f3671777989d4585` |
| [ElasticScrollView.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ElasticScrollView.java) | `c2d5a21139df36a7` |
| [FloatingNavigation.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/FloatingNavigation.java) | `4aedf6324f3f0d15` |
| [ImmersiveLayout.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ImmersiveLayout.java) | `a096074377bc8f7b` |
| [LauncherVisibility.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/LauncherVisibility.java) | `781a468b055306f9` |
| [LogCompatibilityPanel.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/LogCompatibilityPanel.java) | `c3db0d25c6f3b8f2` |
| [LogCompatibilityState.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/LogCompatibilityState.java) | `9943269d0f7fe58c` |
| [MainActivity.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/MainActivity.java) | `44f528ce0c67eac7` |
| [NavigationPolicy.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/NavigationPolicy.java) | `0c2ee299f2b23e4e` |
| [ScrollElasticity.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ScrollElasticity.java) | `8beee69c1016aebb` |
| [SettingsSearch.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SettingsSearch.java) | `e6d35527a7fccfc5` |
| [SettingsSearchIndex.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/SettingsSearchIndex.java) | `ba6610e487e74876` |
| [UiPreferences.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/UiPreferences.java) | `58fbe72b1e8b46b0` |
