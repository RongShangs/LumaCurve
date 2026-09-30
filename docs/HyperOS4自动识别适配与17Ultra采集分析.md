# HyperOS 4 自动识别：adapt01 本地测试版

日期：2026-09-30。新安装包 `dist/luma_curve-1.0.0-local-framework-adapt01.zip`，核心标识 `20260930-framework-adapt01`。原正式安装包、官网和远端仓库未更新。新设备实际输出尚未验证。

## 17 Ultra 采集结论

设备：nezha / 2512BPNDAC，系统 `OS4.0.0.28.XPACNXM` / API 37。输入档案 `LumaCurve-device-info-20260930-153546-23076.tar.gz`，原始固件及反汇编只保存在工作目录的 reverse-engineering/device-collections 内，不进入 Git、源码交付包或官网。

- `framework.jar` SHA-256：`8c208265c50b11808605367893fb3f47e646612e406283a2b463a2483af8ee06`，与原验证设备不同。
- `services.jar` SHA-256：`ac53add4b7f559780affd6c7a614f405cefad18f2cb07cb769c7a1afbbae5c20`，与原验证设备相同。
- `miui-framework.jar` 相同；`miui-services.jar` 不同。
- 本地比较 DisplayManagerGlobal、IDisplayManager、其 Stub/Proxy、BrightnessInfo、DisplayInfo、BrightnessUtils 共 7 类，以及 miui-services 中 com.android.server.display 下 513 类：方法签名/指令摘要及比较到的字段一致。此比较不包含完整异常表、原生实现、资源与运行行为，不是全固件等价证明。
- 默认逻辑显示 ID=0，内部主屏 `local:4630946639341352083`；与原验证主屏不同。
- 主屏使用 SurfaceControl 背光通道；读回节点 `/sys/class/backlight/panel0-backlight/brightness`，最大值 16383，采集当前值 1244。
- 同期框架 brightness / adjustedBrightness 都是 0.07565393；按最大背光归一换算约 1239，接近当前读数。这是一个只读时刻的合理性证据，不能用一个点证明完整面板映射。
- 主屏专用 XML 提供 9 点单调 brightness/nits 映射，末端 value=1。框架实时可用最大值 0.37486267，HBM 转换点相同，配置最低阳光照度 4475 lux。必须持续遵从框架实时限幅，不主动解除系统 HBM/温控保护。
- 正面 tcs3760，背面 tcs3448，背面类型 33171055；现有 C 传感器选择能识别该类型。两侧都是 on-change，未持续上报不等于失效。
- Shell 的 settings/overlay 查询返回 Failed transaction。此现象不能解释为没有亮度权限；新安装预检实际使用已存在的 Java 外部 Settings provider 路径来验证读取能力。

## 已实现的自动识别

安装只读预检和每次后端启动重新发现默认内部主屏、物理标识、背光节点、最大背光，核对调用签名、gamma 往返、显示配置、实际读数和 Settings provider。整包框架哈希只记录变化，已经不再作为安装/启动白名单。

主屏 ID 仍用于在会话中确认控制的是同一块屏幕，但无需预先写死为某台手机的 ID。背光节点选择与 C 端一致：有效 panel0-backlight 优先，否则只能接受唯一有效节点，歧义时拒绝猜测。

原验证主屏保留 17848 的既有工程换算。其他主屏只有在具备可识别的单调归一显示配置、且当前框架值和背光读数相容时，才使用最大背光归一的候选换算。没有据单个低亮度读数自动推算全范围校准。

新 RPC 同步换算系数和最大背光到 C 状态发布端，兼容原 15 字段反馈。状态有效目标不再写死乘 17848。输出持续受框架 brightnessMinimum/brightnessMaximum 约束。

每帧核对主屏标识和框架状态。新候选换算在稳定请求和框架读回一致后，若物理背光持续 3 秒与预期坐标明显不符，释放控制。原有客户端租约、独立帧循环、手动滑块保持和系统释放机制保留。

## 系统更新

不保存长期可直接复用的适配通过标志；系统重启或引擎重启会重新发现设备能力。OTA 后整包哈希变化无需重新编译，但接口签名、主屏类型、显示配置或坐标不再满足条件时会拒绝接管。运行期间的 HBM/温控限幅直接取框架最新反馈。

原测量主屏的换算也受当前读数检查；若未来 OTA 改变其传递关系，需要重新校准，当前设计不会通过修改哈希自动认可它。

## 验证和边界

本地 Java 测试覆盖任意主屏 ID、不同背光范围、节点歧义、当前坐标明显不匹配、映射缺失/非单调/非归一和 DTD 拒绝。安装测试验证哈希改变可通过能力预检、真实预检失败仍拒绝。C 反馈测试验证动态系数/范围及非法数据拒绝。实际 17 Ultra XML 通过同一配置解析器。ARM64 模块独立编译通过。

这些验证不能替代设备输出验证。当前包是用于一次安装体验的本地候选，不能宣称所有 HyperOS 4 可用。接口或节点布局超出当前识别规则的设备会明确拒绝。仍需在目标手机验证首次启动、调节收敛、手动保持与连续锁屏解锁；不需再执行多个分散采集脚本。
