# HyperLux 2.1.0 · 正式版

构建 `release-2.1.0`，版本代码 **21000**，APK 为 `HyperLux-2.1.0.apk`。保持原包名、图标和签名，可覆盖此前正式版及 2.1.0 测试版。LSPosed 保持「系统框架」作用域，更新后重启一次。

新增默认关闭的户外高亮增强、按能力开放的 HBM 预算和高亮范围调整、最低照度节点上移、配置导入导出及六组独立设置子页面。参数直接显示，关闭自定义时置灰；未保存修改以红字提示并跨页面保留。首页说明手动亮度与采样暂停，页签和子页面加入遵循系统设置的轻量过渡。

完整说明见 [项目 README](../../README.md)，正式更新说明见 [2.1.0](../../docs/releases/2.1.0.md)。研究依据见 [户外研究](../../docs/releases/2.1.0-test01.md) 和 [配置迁移](../../docs/releases/2.1.0-test02.md)。本地逻辑与固件静态检查不能代替所有设备的实机测试。

完整包 `LumaCurve-2.1.0.zip` 包含 APK、源码、恢复脚本与 helper，**不是 KSU 模块**。安装 APK 后允许 Root，在 LSPosed 勾选系统框架并重启。保持系统自动亮度开启，进入应用核对连接；新增高亮选项默认关闭，建议逐项启用并保存应用。异常时在设置首页「停用并恢复」，或取消 LSPosed 勾选并重启。终端恢复可在解压目录执行：

```sh
su
sh ./restore_refactor_hook_android.sh
```

构建：`python tools/build_refactor_hook_test.py`；无本机私有固件测试资料时加 `--skip-device-fixtures`，运行时兼容检测仍保留。JDK 17 / Android SDK 37 / Xposed API 82。自行构建使用自己的签名；公开源码不含固件、采集包或签名私钥。
