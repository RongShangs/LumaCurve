# 2.3.2 发布审阅

## 身份

- APP：HyperLux 2.3.2 / versionCode 23107。
- 构建标识：`release-2.3.2`。
- 运行边界：HyperOS 4、Root、LSPosed 系统框架作用域；包名保持 `top.rongshangs.lumacurve`。
- 许可：根目录 `LICENSE` 的 GNU GPL-3.0，源码包同时包含许可证和本 README。

## 本轮实现

- `BrightnessControl.SamplingPolicy` 只管理模块健康检查的生命周期和间隔。
- 光感输入继续采用系统显示控制器已经注册的事件回调；事件到达后立即运行原有暗光判断。
- 刚亮屏 / 显示策略切换后的 2 秒使用 250ms 健康检查，稳定亮屏使用 30 秒；连续型传感器使用 10 秒上限。
- `HookRuntime.screenOn()` 同时检查显示状态、交互状态和输出策略，识别 AOD / Doze / ScreenOff。
- 屏幕状态广播立即刷新状态发布，便于诊断监听是否释放和何时恢复。

## 验证

- Refactor Java / 服务双测试：1005 cases PASS。
- 诊断：49 host cases PASS；高级 Hook：518 cases PASS；传统曲线：2091 cases PASS；曲线比较：59 cases PASS；户外：225 cases PASS。
- 亮度控制：183 cases PASS，包含采样策略、事件监听、模式切换、AOD/息屏释放、手动面板回滚和租约恢复。
- 配置迁移：2294 cases PASS；状态传输：117 cases PASS；草稿 / 原生健康：23 cases PASS；持久化主题：148 cases PASS；原生生命周期：40 cases PASS。
- APK v3 签名和 4 dp 对齐验证通过；APK 原生资产读取：14 cases PASS。
- `LumaCurve-2.3.2-source.zip`、`LumaCurve-2.3.2.zip` 和官网归档均通过 ZIP 完整性检查。

## 未覆盖边界

- 本机构建环境没有项目历史依赖的 MSYS2 GCC，因此构建使用 `--skip-host-native`，两个主机 C 面板测试没有执行，`build-info.json` 已记录原因。
- 本轮没有私有 HyperOS 固件资料，因此固件静态检查按 `--skip-device-fixtures` 跳过。
- 本轮没有把 Android 实机 Hook、AOD/亮屏功耗 A/B 和长期曲线体验当作通过项；目标设备仍需观察 `sampling_phase`、`sampling_health_ms`、`sampling_event_driven` 与传感器事件年龄。
- Playwright 未安装，网页交互自动化检查未执行；静态网站已成功生成，下载文件和 SHA-256 已同步。

## 发布物

- APK：`dist/HyperLux-2.3.2.apk`；最终 SHA-256 以官网 `downloads/SHA256SUMS.txt` 和 GitHub Release asset digest 为准。
- 源码：`dist/LumaCurve-2.3.2-source.zip`。
- 完整包：`dist/LumaCurve-2.3.2.zip`。
- 静态官网包：`dist/LumaCurve-website-2.3.2.zip`。
