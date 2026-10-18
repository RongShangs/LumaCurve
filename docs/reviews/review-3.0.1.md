# 3.0.1 本地交付审阅

范围：以正式版 3.0.0 为基线，合入户外 nit 上限、桌面图标开关与首次设置须知。身份为 `release-3.0.1 / 30005`，`TEST=false`、`INTERNAL=false`；用户已授权正式推送源码、GitHub Release、LSPosed 分发与官网；不操作手机。

## 户外上限

- 使用主屏 `DisplayPowerControllerImpl.convertBrightnessToNit(float)` 的设备显示映射求逆，不使用可编辑的照度曲线或按最大值简单等比例换算。三份既有固件的 SDR 路径也使用此 nit 换算接口。
- 验证有限值、设备范围及采样单调性，采用有界二分求解并检查最终换算值。按用户值、坐标上下限及 nit 端点缓存；输出时复核缓存点，避免重复完整求逆。不可靠换算保留系统控制并报告不可用。
- 在户外目标与 SDR 输入、输出处限制，只降不升；温控、电量、省电、画面及其他系统限亮可继续降低结果。手动、HDR、隐私、其他用户/线程、锁屏、特殊场景及输出归属变化时不接管上限。
- 回归覆盖非线性 3500 nit 映射、2000 nit 上限、50% / 100% 增强、OPR 放宽、系统保护、异常转换、设备最小值、关闭功能及配置迁移。预设保留独立上限，旧配置补 0。

## 桌面入口

- `MainActivity` 始终启用并保留 `MODULE_SETTINGS` / `INFO` 入口；新增仅承载 `LAUNCHER` 的别名，通过 PackageManager 切换该别名。
- 使用 `DONT_KILL_APP`；不禁用主 Activity、应用、服务或系统钩子。读取组件实际状态显示开关，恢复前台时重新同步；失败不伪报成功。
- LSPosed 的设置入口选择逻辑按其 [AppHelper 源码](https://github.com/LSPosed/LSPosed/blob/master/app/src/main/java/org/lsposed/manager/adapters/AppHelper.java)核对：优先模块设置分类，其次 INFO，再其次 LAUNCHER。

## 首次设置须知

- 本机独立确认键，只有点击已解锁的确定按钮时才写入；不纳入配置导入导出。10 秒通过单调时钟计算，重复回调、延迟回调和提前直接调用按钮任务均不能绕过。
- 倒计时结束只解锁按钮，不自动关闭；返回键与外部点击不关闭。销毁/重建但未确认时仍需确认，暂停时移除计时回调，返回前台后根据实际经过时间刷新。
- 使用既有响应式弹窗，正文滚动、标题和操作栏固定；长文与大字体使用现有窗口测量机制。更新和旧模块提醒等待须知结束；权限提示延后显示。
- 主机测试运行真实弹窗生命周期、按钮回调与组件开关代码，核对持久化、暂停/恢复、销毁、不可关闭、入口保留和英文文案。

## 验证边界

完整构建结果记录在本地生成的 `build/refactor-hook/verification.json` 与交付包 `build-info.json`。主机模型和固件静态核对不代表本版 Android UI 或亮度真机实测；当前 ADB 无设备，本轮不安装或重启。已发布 3.0.0 APK 保持原哈希；3.0.1 发布时核对官网、原仓库与 LSPosed 使用同一 APK。

## 正式发布补充复审

- 复现重复须知原因：Android 的 `Activity.getPreferences()` 使用启动组件的本地类名。从 `Launcher` 别名与 `refactor.MainActivity` 设置入口启动会读取不同文件。行为按 [AOSP Activity 源码](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/app/Activity.java)核对。
- `UiPreferences` 固定沿用原 `refactor.MainActivity` 存储；一次合并旧 Launcher 等入口中缺失的键，原主入口的设置优先。已确认状态取各旧入口的并集；既有确认保留，不被旧 false 覆盖。迁移只执行一次，不回灌旧值，不导入导出须知确认状态。
- 增加真实 Activity 偏好覆盖方法的回归：先重现旧版双入口不一致，再验证入口切换、反复重建、旧确认迁移、配置类型、预设与刷新偏好保留。清除应用数据后仍需重新确认。
- 红米卡死按作者明确提供的用户后续测试反馈记为修复完成，不把主机检查当作亲自完成目标设备长期实测。历史排查记录保留原时间点的判断。
