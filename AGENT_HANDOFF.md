# HyperLux 项目交接文档

更新时间：2026-10-07（Asia/Shanghai）。当前交接范围：分支 `v2.3.1-lsp.2` APK 打包与发布。

## 当前状态：2.3.1 正式包（优先于下方 beta 历史）

- 用户已授权按既有需求审阅、修复、正式打包，并随后授权“推送部署”。本轮推进 GitHub main / v2.3.1 / 正式 Release，更新 D:/IOS/web 并生成部署包；用户随后提供私下连接信息并授权直接部署；服务器凭据仅用于本次连接，不写入项目、交付包或发布内容。
- 当前源码：AppBuild.BUILD=release-2.3.1-lsp.2，VERSION / ARTIFACT_VERSION=2.3.1，TEST=false；Manifest versionCode=23107。
- 分支产物路径：dist/HyperLux-2.3.1.apk、dist/LumaCurve-2.3.1-source.zip、dist/LumaCurve-2.3.1.zip。最终 APK SHA-256 及源码 hash 以 build/refactor-hook/verification.json 和合集内 build-info.json 为准；构建日志为 build/release-2.3.1-build.log。
- 详细审阅和新增修复：docs/review-2.3.1.md；说明：docs/releases/2.3.1.md。修复保存返回覆盖等待期新草稿、暗光锁定转手动失败的自动模式恢复、接管 ACK 丢失的会话回滚、守护健康记录长期有效。
- 新增 tests/refactor_draft_and_health.py，纳入正式构建；配置迁移 2294、控制器 177、Root 事务 49、草稿与健康方法 23、原生格式 62 项定向检查已通过，完整结果看正式构建日志。
- 原生身份现在是 raw06-health；socket 仍为 hyperlux.main.panel.raw04。健康记录增加单调时钟第四字段，每 2 秒更新、超过 5 秒不可采信；Java 与 C 同步更新，旧三字段不采信。
- 继承 beta05 暗光默认：开启锁定，10 lux / 1 min 进入、50 lux / 1 s 退出；旧用户显式设置及早期未配置开关的关闭状态保留。
- 原签名 keystore 不在本机。本次按用户确认生成了新签名；证书与此前 APK 不同，安装前需卸载旧同包 APP，可能清除本机设置和数据。新私钥仅保存在本机忽略目录 `build/refactor-hook/test-signing.p12`，不得提交或发布。更新后必须重启，详细读数确认 `release-2.3.1-lsp.2`。
- 没有手机连接，本版暗光改善及实际 Hook / 界面仍未实机验证。ueventd 物理源头与稳态对照待办不变，不宣称根治。
- 网站源与 D:/IOS/web 更新为 2.3.1，部署前已保留线上与部署副本的全部 13 项感谢名单，并合并到仓库网站源；随包离线名单不更新。线上网站是否生效应查询 /update.json，不凭本地同步判断。
- 本轮基线构建重新生成过 beta05 同名产物，下方 beta05 历史 APK hash 不再代表重建文件；不要据此误报 tampering。正式包独立命名，不覆盖 2.3.0。
- 工作区保留所有历史未提交修改与私有研究资料。源码 ZIP 现在携带本交接与 build-info.json；私钥和固件不随包发布。

以下各节保留截至 beta05 的历史需求、架构与排障记录；凡版本、当前产物、原生身份与授权描述冲突，以本节和实际源码为准。


这份文档供后续 agent 接手开发与排障。以用户后续指示、实际源码和新采集为准；这里的历史发布授权不等于授权发布当前 beta。路径以本机 Windows 为准。

## 1. 接手先看这几件事

1. 真正的 Git 仓库是 `D:/IOS/LumaCurve`，当前分支 `main`。**目前工作的 APP 源码在 `experimental/refactor_hook/`，虽然目录叫 experimental，它就是现用产品。** 根目录 C 工程、`module/` 和 `ios-brightness-dev` 是旧路线，不能当现用核心修改。
2. 本地正式版基线：`ac3942f79a4f46d2423b273c91cfd90d99c8b3b3`，提交标题 `Release HyperLux 2.3.0`，正式 APK 版本代码 **23004**。本轮没有重新联网审查 GitHub 的实时状态。
3. 当前源码身份：`AppBuild.BUILD=beta-2.3.1-dark05`，`VERSION=2.3.1`，`ARTIFACT_VERSION=2.3.1-beta05`，`TEST=true`；Manifest 版本代码 **23105**。
4. **beta01～beta05 尚未提交、push 或创建 Release，也没有替换官网正式下载。** 工作区有大量既有修改和研究文件，不能 reset、clean 或批量丢弃。
5. 最近两个问题：暗光定时锁定以前不计时，以及暗光中莫名升亮。用户已经确认新版的**定时锁定正常**，要求集中处理暗光稳定；随后又要求调整锁定默认值，已完成 beta05。
6. beta05 的新默认与“睡前保持”预设：**开启锁定；主辅均 ≤10 lux 持续 1 分钟进入；任一侧 ≥50 lux 持续 1 秒退出。** 已有显式设置保留，不强制覆盖。
7. beta04/beta05 的暗光稳定新修复已通过主机回归及固件静态核对，**尚无用户反馈证明它已在实机完全消除升亮**。不要把“已打包”写成“已根治”。
8. 最新交付：`dist/HyperLux-2.3.1-beta05/HyperLux-2.3.1-beta05.apk`。覆盖安装后必须重启，让 `system_server` 加载新版 Hook；仅重开 APP 不够。

### 最新产物与可核对值

| 项目 | 值 |
| --- | --- |
| beta05 APK | `dist/HyperLux-2.3.1-beta05/HyperLux-2.3.1-beta05.apk` |
| beta05 SHA-256 | `7938216fe70b18a2fc66d44061edd7139fbfa299e31d8737ab54a2e815a862b2` |
| 完整测试合集 | `dist/HyperLux-2.3.1-beta05/HyperLux-2.3.1-beta05.zip` |
| 源码合集 | `dist/HyperLux-2.3.1-beta05/HyperLux-2.3.1-beta05-source.zip` |
| 构建日志 | `build/beta05-build.log` |
| 最新构建核验 | `build/refactor-hook/verification.json`（下次构建会覆盖） |
| APK 证书 SHA-256 | `a4c4759841927acf432b887f0c9bfc16fb8e30c0885ae00e8a6175b90731182d` |
| beta04 APK SHA-256 | `4a1c40fad86faef1608c8423464b5d6a86bb332709933d4c9820304c4410f9b7` |
| beta03 APK SHA-256 | `e5b1dbed6ee37f18a77c4208d8b6e5ef425b48e6b8b68c924735e12a470ed6cd` |

本交接文档后于 beta05 打包生成，因此**现有 beta05 源码 ZIP 不包含本文**；下一次打包若需要携带本文，需明确加入构建脚本。本文新增后没有重打运行包。

## 2. 产品定位与用户长期需求

### 名称、环境和边界

- APP 显示名称 **HyperLux**；包名保持 `top.rongshangs.lumacurve`，桌面蓝色曲线图标不改。
- 仓库保持 `https://github.com/RongShangs/LumaCurve`；官网 `https://lc.rongshangs.top`；作者博客 `https://rongshangs.top`。
- 作者称呼按位置沿用现有界面；涉及酷安统一 **酷安@戎Shangs**，不要漏末尾 s。
- 当前是 **Root + LSPosed APP**，作用域为 Android 系统框架。不是要求用户再装老 KSU 模块。支持范围表述只写 **HyperOS 4**，不以机型白名单限定，也不保证任意 OS4 固件全功能兼容。
- OS3 等明确不支持时应直接说明；版本属性不明、Root 不可用、LSPosed 已注入但曲线不兼容，要区分原因。
- 继续检测旧亮度模块，提示卸载；不要未经说明删用户模块目录。
- GPL-3.0、开源免费。保留 LICENSE 和历史来源文件，不因 UI 去掉“原作者”区就丢弃许可证信息。
- 主要诉求是**户外强光下自动亮度够高，同时暗光稳定、手动调整可保持**。不是只做漂亮的监控页。

### 现行界面需求

- 四页：状态、设置、日志、关于；系统中英双语，深色/浅色跟随系统。
- 状态为完整流水线：主/辅光感 → 融合滤波与场景判定 → 当前曲线 → 温控 → 当前屏幕亮度。连线为流动虚线，传感器侧朝中间；变亮/变暗阈值靠近对应虚线两侧。
- 状态页尽量不滚动；曲线卡内放分支入口，留一致的内边距。日志页协调布局，详细读数/分支在日志页，不堆在状态页。
- 设置用独立子页面；各组预设靠上，主要开关/图表可见，详细参数默认折叠并记住展开状态。关闭自定义时相关参数置灰。
- 进入设置自动读取，但**不能覆盖未保存草稿**；“引擎控制”右侧红字提示未保存。跨页/深浅色变化保留草稿。按钮不带裁切阴影，按压反馈限制在按钮圆角内，弹窗渐显、圆角、横屏/键盘可用。
- 曲线编辑页：**灰色系统默认 + 蓝色基础曲线**；蓝点可拖动或点按输入。红字说明实际效果受手动记忆影响。
- 记忆页：蓝色已应用基础 + **浅绿色当前实际曲线**，手动点也用绿色。未恢复的存档不能冒充当前生效记忆。
- 状态页**只显示一条实际曲线**：系统默认灰色；自定义基础蓝色；有实时记忆且实际改形时浅绿色。线宽统一 2 dp，不加多余图例。
- 记忆关闭时红字提示：“不记忆将会导致在开启自动亮度时，您将无法手动调整屏幕亮度”。用户曾反馈亮度条被抢，后来确认是自己关了记忆，不要再当未解决 bug。
- 关于顺序：简介/官网/GitHub/协议 → QQ 群 → 打赏 → 感谢名单 → 作者。群 **314981836**、暗号 **1691**，点击只复制群号。捐赠备注“昵称：想说的话”，合计提示 30 字符；二维码下提醒备注；加入名单的措辞为“将会尽快更新到感谢名单”。
- 感谢名单跟随网站 feed；**名单增删不写更新日志**。日常只改网页名单，别自动刷入随包离线名单。
- 网站首页展示最新正式版本和更新说明，简介聚焦功能。不要把旧独立 broker 的内存/耗电测量当现架构数据，更不要编造“完全零占用”。

## 3. 目录地图与容易走错的入口

| 路径 | 用途/注意 |
| --- | --- |
| `experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/` | 当前 APP + LSPosed Hook 全部主要 Java 代码 |
| `experimental/refactor_hook/AndroidManifest.xml` | 包身份、版本代码、磁贴/弹窗 Activity、LSPosed 元数据 |
| `experimental/refactor_hook/assets/xposed_init` | Hook 入口声明 |
| `experimental/refactor_hook/native/` | 仅手动面板需要的小型主屏节点守护 |
| `experimental/refactor_hook/assets/hyperlux-main-panel` | 打进 APK 的 ARM64 原生可执行文件，构建时生成 |
| `experimental/refactor_hook/res/`、`assets/` | 主题、图标、头像、捐赠图等 |
| `tools/build_refactor_hook_test.py` | 当前正式/测试 APK、helper、源码、恢复合集构建入口 |
| `tests/refactor_*.py`、`experimental/refactor_hook/tests/` | 当前回归和私有固件静态核对 |
| `docs/releases/2.3.1-beta01.md`～`beta05.md` | 本轮局部改动、验证范围、使用说明 |
| `docs/review-2.3.0.md` | 正式版核心边界与发布审阅，部分内容已被 beta 覆盖 |
| `build/refactor-hook/` | 生成物、依赖、签名 keystore、最新核验数据；下次构建覆盖 |
| `build/research-203/os41/`、`os28/` | 私有 OS4 反编译文本，不能打进公开源码 |
| `dist/` | 构建交付，Git 忽略；beta 各有独立目录 |
| `website/` | 仓库内静态官网源文件 |
| `D:/IOS/web/` | 用户自行搬到服务器的部署副本，可能存在仅在该处添加的数据 |
| `output/diagnostics/` | 本轮分析包解压、研究脚本、分析结论；默认当私有排障资料 |
| `D:/QQ/` | 用户提供的日志/分析包/截图原件 |
| `csrc/`、`include/`、`module/`、根 `CMakeLists.txt` | 1.0.0 独立 C daemon/KSU 时代实现与打包，当前不是主线 |
| `experimental/official_curve/`、`tools/official_curve_probe/` | 向官方曲线迁移时的研究方案，不等于当前 APP |
| `D:/IOS/ios-brightness-dev/`、`reverse-engineering/` | 更早期开发/还原/归档资料，不要误当当前工程 |

`D:/IOS/README.md` 仍写早期模块和 highlevel C 构建方式，已经不适合作为当前开发入口。`experimental/refactor_hook/README.md` 顶部还留着 2.3.0 标题，而暗光默认值段已更新；**身份以 AppBuild + Manifest + APK 核验为准**。本文只记录这些不一致，本轮没有顺手改历史说明。

## 4. 现用架构：自动走系统，手动才直写

```text
APP UI ── RootControl / RootSettings ── SettingsProvider 配置与确认
                                          │
                              system_server / 原显示 Handler
                                          │
       主辅光感 → 系统滤波/迟滞/场景 → 实际正在使用的曲线后端
                       │                   │
               暗光确认与门槛       基础编辑 + 原生手动记忆
                                          │
                   户外目标/OPR/温控/范围保护 → 系统动画 → 显示输出

手动主屏磁贴 → 独立弹窗 → Root 启动小型 C 守护
                   system_server 会话/租约 → 确认主屏节点 → 持有 FD/按需补写
```

**不要把两条输出路线混合成自动模式不断写 sysfs。**

### Hook 入口和后端选择

- `HookEntry.java`：仅系统 UID、Android/system/system_server 进程，OS4 验证；发现 `DisplayPowerControllerImpl`。部分 ROM 延迟创建厂商 ClassLoader，因此启动阶段临时观察 `loadClass`，发现后移除，45 秒窗口结束也清理。
- `BackendSelection.java` / `HookEntry.attach`：读取当前控制器使用哪套机制。Refactor 类存在不代表当前正在使用；已明确使用某后端但接口尚未就绪时应等待，不能选错另一后端。
- `RefactorAdapter.java`：读设备本地 `RefactorNitController` 基础和当前锚点、逻辑亮度范围；修改基础字段，继续让 OEM 重置/插值执行。
- `TraditionalAdapter.java` / `TraditionalHooks.java`：传统 PhysicalMappingStrategy，保留完整本地数组、分类修正和元数据，使用物理 nit；四点编辑不是把原始密集曲线砍成四段。
- `CurvePlan.java` / `TraditionalCurve.java`：基础形状、单调性、边界和暗处亮度下限。
- `HookRuntime.java`：运行生命周期、配置应用、状态发布、能力门控；各实例只服务对应主屏、用户和显示线程。
- `PipelineHooks.java` / `PipelineHistory.java`：只读记录曲线/场景/手动保持等阶段；曲线计算记录与最终输出记录分开，不把一个阶段目标当实际面板亮度。

### 坐标、传感器和显示策略

- Refactor 的 logical nit、物理 nit、0～1 brightness、sysfs 原始整数、曲线百分比**不是同一单位**。旧 `17848 codes/float` 标定不能移植到所有机型。
- 主/辅助传感器由设备真实控制器决定，不固定把类型 5 叫“真实前置原始值”，不自行平均两路。
- 辅助可能是厂商类型 **33171055**。读取对应消费者实际使用的 `values[0]`，保留其放大系数与有效性判断。
- 有效 0 lux 可以是真暗/遮挡；缺值、未预热、无效、待重置不能伪装成 0。on-change 无事件也不能只凭年龄认定传感器坏了。
- 同型号传感器的 RGBC raw 值不能不看增益/积分时间/补偿就直接比较 lux 标定。此前“相近 raw、lux 相差 40 倍，因此前置标定错了”的判断**没有充分证据**。
- 夜间驾驶、反射、分段、应用指定亮度、临时/手动保持、HDR/杜比、OPR、HBM、温控等都可能改变输出；看 `system_scene`、pipeline/output trace，而非简单猜“有两个系统在抢”。

## 5. 功能与代码责任区

| 功能 | 主要类 | 核心约束 |
| --- | --- | --- |
| 基础曲线/图形 | `CurvePlan`、`CurveEditor`、`CurveComparison`、两套 Adapter | 四个基础编辑点；保存才生效；默认来自本机；最高端受原生上限与单调性限制 |
| 原生手动记忆 | `MemoryPolicy`、`MemoryOptions`、`MemoryLifecycle` | 手势合并、强度、原生模型重判；不把手动保持等同永久学习算法 |
| 记忆存档/恢复 | `MemoryPersistence`、`PersistentMemory`、`MemoryScene` | 固件/基准/用户隔离；新手动操作优先；一次且限时恢复 |
| 暗光稳定 | `LowLightPolicy`、`LowLightThresholds`、`LowLightTuning`、`ResponseTuning`、`AdvancedTuning` | 扩大照度迟滞和确认，绝不改造原始照度或直接锁物理输出 |
| 辅助闸门 | `LowLightAssistEvidence`、`LowLightAssistGate` | 有效且新鲜历史才能延长，观察有限，到时/缺证据/大变化放行 |
| 暗光定时锁定 | `BrightnessControl`、`DarkLockPolicy`、`BrightnessControlOptions` | system_server 事件监听与计时；临时关闭自动；自己监听恢复；不用 C |
| 自动户外增强 | `OutdoorController`、`OutdoorPolicy`、`OutdoorTuning`、`OutdoorOpr`、`HbmAccess` | 系统目标链内调整，保留条件和最终保护；不直写节点 |
| 温控 | `ThermalPolicy`、`HookEntry.installThermal` | 可选减少显示层温控限亮，电池阈值 38～50℃；严重过热等仍退让 |
| 手动弹窗/磁贴 | `BrightnessPanelActivity`、`RawBrightnessPanel`、`BrightnessTileService`、`RelativeNodeGesture` | 独立弹窗，相对滑动，真实操作才关闭自动，磁贴反映真实接管 |
| 手动节点后端 | `PanelNodeDiscovery`、`NativePanelRoot`、`NativePanelClient`、`RawPanelLease`、`RawPanelOutputHooks`、原生 C | 自动识别明确主屏、身份校验、会话租约，不能影响副屏/AOD/息屏 |
| 配置/Root | `RootControl`、`RootSettings`、`ConfigurationFile`、`ForegroundUser` | 工作线程执行，确认独立；不要混用用户 ID 和 serial |
| 诊断/状态 | `DiagnosticCollector`、`StatusTransport`、`InjectionStatus` | 有界、按需、只读采集，区分未取到/超时/不支持 |
| UI/网络 | `MainActivity`、`UiText`、`ThemePalette`、`ResponsiveDialog`、`UpdateChecker`、`ThanksFeed` | 草稿/双语/主题；检查正式 APK 更新；名单共享网站 feed |

### 记忆的关键细节

- Refactor 原生总锚点容量为 **7**，包括基础和手动点；传统后端通常只支持 **1** 个手动偏好点。不要盲目扩成 8 点或用存档容量冒充曲线节点数量。
- 默认记忆强度 100%，存档开启、30 天（可调 1～90），容量 0 为按设备能力自动。
- 锁屏重判、短期模型失效、跨重启存档、解锁恢复是四件事。锁屏或 reset 后系统可能重建曲线，不表示持久文件已删除。
- `MemoryPersistence` 解锁后等有效照度，默认等待 1500 ms、场景比例 50%、暗处至少 5 lux；恢复窗口有 20 秒上限，不无限覆盖系统。
- 原生 reset/time-out 自定义默认关闭。新手动操作取消待恢复；不同基础曲线、用户或固件不能混用旧存档。
- 保存纯温控/户外等参数时不要重置基础曲线或清手动锚点。配置重复通知、重读和主题切换不能“顺手重置”。

### 户外增强的关键细节

- 默认关；默认进入 10000 lux/3 s，全强度 30000 lux，强度 85%，单次 3 分钟、冷却 1 分钟；范围依 `OutdoorOptions`。
- “强光优先”预设强度 100%，支持时开放高亮范围、HBM 触发倍率 0.5，预算仍 1。没有接口时按能力拒绝/降级，不伪装成功。
- 实机曾出现自动 1060 nit、手动面板更高。原因需沿 OPR/画面灰阶限亮继续分析，而非把 raw 节点最大值硬塞到曲线。
- `OutdoorOpr` 只在已验证的主屏、原显示线程、自动 SDR、强光门控等条件下放宽本次 OPR，保留原方法执行、既有 modifier 和后续保护。
- 进入、退出、超时需主动请求系统重算，不能等一个 on-change 传感器再次发事件。
- 自动 HBM 时间预算和**手动模式阳光屏**进入/退出延时不是一套参数。

## 6. 本轮 beta 演进与哪些改法已撤回

| 版本 | 身份 | 实际变更/判断 |
| --- | --- | --- |
| beta01 | `beta-2.3.1-node01` / 23101 | 主屏节点自动发现；Java/C 双重验证；权限恢复记录绑定节点身份 |
| beta02 | 历史试验包 | 曾猜 MTK 显示链路并加框架输出桥；**已撤回，不要给用户安装或移回当前核心** |
| beta03 | `beta-2.3.1-dark03` / 23103 | 暗光监听对象/handle 修复；受保护 HBM 阈值访问修复；有效零辅助证据；撤掉 beta02 桥 |
| beta04 | `beta-2.3.1-dark04` / 23104 | 明确夜间驾驶场景也执行暗光保护，保留原生额外等待/亮度下限；补阈值诊断 |
| beta05 | `beta-2.3.1-dark05` / 23105 | 新锁定默认/预设 10 lux·1 min / 50 lux·1 s；旧配置保留；继承 beta04 |

### 坑 A：暗光锁定注册成功，却一个有效回调都没吃到

旧实现从 Context 取 SensorManager，按 Sensor 对象实例相等匹配。显示控制器可能单独创建 SensorManager，回调 Sensor 是另一实例，硬件同一但 `==` 不同，导致计时永远不开始。

beta03 改为实际 `abc.mSensorManager`，按**类型 + 硬件 handle**匹配；管理器/传感器/放大系数变化时释放并重绑。null、空事件、错 handle 不采信。等待有效输入时 `reason=waiting_sensor_data`，不能展示假计时。

诊断：`brightness_control.watch_main_samples`、`watch_assist_samples`、`watch_unmatched_samples`、`watch_main_age_ms`、`watch_assist_age_ms`、`watch_*_lux`、`countdown_left_ms`。用户确认这个问题已正常，不要继续无故改其状态机。

### 坑 B：暗光阈值显示开启，但调整计数为 0

`getMethod("getHbmData")` 只能找到 public 方法，实机 `HighBrightnessModeController.getHbmData` 是 protected；旧异常静默吞掉，整个阈值 Hook 退回原门槛。

beta03 改读已核对字段 `mHbmData`，保留 `minimumLux`/HBM 边界；异常进入 `threshold_error`、`threshold_skip_reason` 和日志。不要用“支持=true”或“开关=true”推断已生效。

### 坑 C：只延迟，挡不住 5 lux 的持续小波动；夜间分支还会绕过

最新原件 `D:/QQ/LumaCurve-analysis-20261007-014516-6461d5.zip`，解压和结论在 `output/diagnostics/analysis-20261007-014516/analysis.md`。

包内仍是旧 `release-2.3.0 / 23004`，这不是用户随后安装新包的效果报告。其暗光稳定/阈值已开、变亮最小差设 30 lux，却看到：

- 普通和小幅升亮门槛仍为 **2 lux**，暗光阈值调整次数 0。
- 系统 `driving=true`、`night_driving=true`。旧 `normalTuningAllowed`、`lowLightApplies` 和 Response 的提前返回均排除该分支。
- 已确认 lux 反复从 0 到 4.108/4.884/5.748，再回 0；曲线目标跟着变。0 lux 场景后目标 0.006043218，5.748 lux 为 0.015791846，归一化目标约 **2.61 倍**，不是物理 nit 倍数。
- `assist_reset_pending=true`；即使辅助数值为零也不能强行当可靠证据。
- 输出 trace 未显示最终层额外改写，因此本段不支持归咎 HDR 或某个常驻写节点进程。

beta04 的修法：

1. `LowLightPolicy.appliesInScene` + `HookRuntime.nightDrivingConfirmed`：只有明确读到 `SceneDetector.mIsNightDrivingMode=true` 才允许夜间暗光保护；未知驾驶、HDR、闲置、手动、熄屏、无效照度仍退让。
2. `AdvancedTuning` 不再因普通参数的 `normalTuningAllowed=false` 提前拒绝低光 guard；普通自定义参数仍受原门控。
3. `ResponseTuning` 移除重复驾驶早退；保留 `getNightDrivingDebounceConfig` 额外时间，并纳入历史窗口预算；`LowLightAssistEvidence` 同步门控。
4. 主辅及微小升亮都走暗光阈值；原生夜间**亮度下限不改**，不扁平化曲线，不伪造光感，不靠每帧写节点压亮度。
5. 状态记录 `last_low_light_threshold_lux` / `last_low_light_brightening_threshold` / `last_low_light_small_threshold` / `last_low_light_darkening_threshold`。

注意这些 last_* 是最近 Hook 计算记录，可能来自主或辅消费者，**不是当前主侧阈值的保证**。当前主侧用 `brightening_lux_threshold`，小幅用 `system_scene.small_brightening_lux_threshold`；计数是本次系统进程累计，也不能证明当前帧启用。

固件核对表明 `SceneDetector.getDrivingStatus()` 在已有两份固件返回夜间驾驶标记。这个标签不证明用户真的在开车。不要直接把 `normalTuningAllowed` 全局放开，以免其他高级功能也越过特殊场景。

物理上为何会有主光感波动尚未证实，脸部反光反馈只是用户假设，不能宣称已定位硬件坏或标定错误。

### 坑 D：新默认值不能覆盖旧用户配置

beta05 `BrightnessControlOptions` 构造/新草稿默认开启，10/50 lux、1 min/1 s；`bedtime()` 复用这套参数但保留手动面板开关。

`parseStored()` 用于 Hook、Root 提交、配置导入和已有设置加载：显式键保留；老配置根本没有 `dark_lock_enabled` 时按未启用处理。新草稿通过 `controls.put()` 带上显式 true。不要未来为了“统一默认”删掉这个区别。

## 7. 手动主屏后端的坑与边界

### 节点自动发现与临时 Root

- beta01 起不再只认 `/sys/class/backlight/panel0-backlight/brightness`，有界读取 backlight 与可信 leds 候选，明确主屏优先；排除副屏、闪光灯、键盘、RGB 等，歧义拒绝。
- `PanelNodeDiscovery` 负责选择，C `panel_node.h` 独立核对命名空间、canonical `/sys/devices/`、整数最大值和节点身份。
- 权限记录 v2 绑定 path、canonical path、boot ID、device/inode、原权限；旧单纯八进制记录只按历史 panel0 规则恢复。不能把旧权限恢复到另一节点或另一次开机重建的设备上。
- 原生身份为 **raw05-auto-node**，但 abstract socket **仍叫 `hyperlux.main.panel.raw04`**。不能看到名字旧就只改单侧；Java、C、恢复工具和诊断要一致。
- 用户报告“临时 Root 能滑但没效果”，后续明确是其环境**不能写 sysfs**，不是已证实节点路径或 MTK 问题。节点能发现、开读成功、滑块可动，都不证明写入权存在。
- beta02 的 MediaTek 框架输出桥已全部撤回；当前没有 `RawFrameworkOutput.java`，不要从旧产物提取回来。

### 0444 自锁与持有 FD

以前模块把节点 chmod 为 0444 后每帧重新 open，第一帧后把自己锁死。曾错误推断“驱动没有写接口”，实机对照已推翻：暂停模块恢复 0644 后可写；先 open、再 chmod 0444，旧 FD 仍能写。

当前 C 先持有写 FD 再锁权限，复用 FD。暂停/失联/退出恢复原权限；不是每帧临时解锁。不能回到“close FD + 保持 0444 + 下帧 open”。

### 原生启动就绪误报

曾看到日志 `NATIVE_READY` 但 Java 仍说 1.5 秒内未就绪。`LocalSocket` 延迟创建描述符，**先 connect 再 setSoTimeout**；不能在未创建 FD 时先设超时。别只无脑延长超时掩盖 API 顺序问题。

原生就绪/事务验证必须先成功，再关闭自动亮度；失败不能把自动关掉后留黑屏状态。

### 接管生命周期

- 只有用户实际滑动/输入才接管，打开面板不接管；相对手势记录增量，不把绝对手指位置当目标。
- 关闭弹窗继续保持；UI 查询连接应关闭。
- 锁屏/AOD 暂停并释放节点，解锁后正常恢复原目标；开启系统自动亮度、切用户、停用、故障结束接管。
- `RawPanelOutputHooks` 只有对应主屏实例/显示线程、会话确认、有效租约、非自动、亮屏解锁等条件全部满足才隔离正值输出。零值、息屏、唤醒首帧和副屏不能拦截。
- 无 wake lock，不延长屏幕超时。system_server 重启不得恢复旧高亮。
- C 约 250 ms 检查，只在实际值被覆盖时补写；稳态不应无意义反复写同值，更不能自动模式 60Hz 直写。
- 最大值取本机 `max_brightness`，不能越界；raw 整数到顶不等于物理 nit 一定到顶。

## 8. 系统内状态、配置与诊断协议

### SettingsProvider 键

| 键 | 内容 |
| --- | --- |
| `lumacurve_refactor_config_v1` | 当前 Hook 配置，含 enabled/revision/固件/用户/曲线/设置 |
| `lumacurve_refactor_status_v1` | 核心状态；长数组被拆到派生分片键 |
| `lumacurve_refactor_ack_v1` | 独立配置确认，不依赖日志/长状态是否发布成功 |
| `lumacurve_refactor_refresh_v1` | 查看/刷新请求 |
| `lumacurve_refactor_injection_v1` | LSPosed 注入与曲线接入阶段，二者分开 |
| `lumacurve_manual_memory_v1` | 有界手动记忆存档，8192 字符限制 |
| `hyperlux_brightness_request_v1` / `hyperlux_brightness_ack_v1` | 手动亮度请求与对应确认 |
| `hyperlux_brightness_owner_v1` | 本次接管所有权，用户手动关闭自动时不能误恢复 |

Root 数据目录保留历史名 `/data/adb/luma_curve_refactor_test`，不因正式 APP 名称就改路径。原生文件含 `hyperlux-main-panel`、`main-panel.log`、`main-panel.lock`、`main-panel-permissions`。

system_server 租约与健康文件：`/data/system/hyperlux-main-panel-lease`、`/data/system/hyperlux-main-panel-health`。单调时钟使用 uptime/CLOCK_MONOTONIC，不与墙钟/elapsed 的其他语义随便混用。

### 长状态导致“配置未确认”

2.1.0 曾在日志累积后 Settings 单字符串超长，连带确认卡住。2.1.1 拆出独立 ACK，并将历史分片，而不是削减用户记录容量来遮 bug。

`StatusTransport`：单项限制 30000、分块 12000 字符且检查 UTF-8 字节、最多 32 片/section；每批 snapshot UUID，**manifest 最后写**。读者跨批不能拼接混合数据，缺片标明 `status_missing_sections`。含 emoji 的分块不截断 surrogate pair。

### 配置保存/导入

- 导出格式 `hyperlux_config_v2`、schema 2，最大 32768；可包含未保存草稿，导出本身不应用。
- live 配置 schema 1 与导出 schema 2 不同，不要误判版本不兼容。
- `ConfigurationFile` 处理旧键补齐、范围限制、未知项、设备能力与基准差异；跨固件/后端不能移植 raw 标定和别人的默认曲线。
- Root 提交核对 revision、build、PID/process_start 等，防止旧 system_server 缓存当成功；失败保留草稿并尝试恢复此前配置。
- foreground user 的 ID 和 serial 可能不同，既有 `ForegroundUser` 已处理，不能总写 0/把 ID 当 serial。

### 分析包

- APP 导出日志包含状态；分析包包含 config/state/logs、pipeline/output trace、显示/电源/传感器、Settings 读取、属性、相关系统 JAR/资源及节点/原生运行资料等，导出到共享存储根目录 `/sdcard/`。
- `DiagnosticCollector` 按需只读，通常总时间预算 180 s，单文件 64 MiB，总复制 256 MiB，最多 256 文件；命令超时/不可读可留部分结果和 manifest，不要一项缺失就整包失败。
- Settings 曾因 shell 读取方法失效变空；已改现有 SettingsProvider reader，保留真 0，缺失/错误明确分类；见 `settings/system.json` 和逐项文件。
- UI 导出要显示进度。不要让普通状态页持续跑 dumpsys 或不断 Root 扫所有节点。
- 分析包包含固件与设备敏感数据，私有解压资料不随 GitHub push/source.zip 发布。

## 9. 排障建议：证据先于猜测

拿到用户包先按顺序看：

1. `app-package.txt` 的 versionCode、`state.json` 的 `runtime.build`、injection/build、fingerprint；确认 APP 和系统进程加载的是哪版。覆盖安装后没重启是常见原因。
2. OS4 检测、Root、injection 阶段、runtime.phase、曲线后端是否实际 attach。已注入不等于接口适配成功。
3. `config.json` 实际保存的开关，而非截图上的草稿。确认主机型/固件只用于定位样本，不作为新硬编码白名单。
4. 发生时间前后的 accepted lux、主/辅 fast lux、有效性/预热/reset、门槛、曲线/记忆/场景/override/最后输出。
5. 暗光稳定：普通门槛 + 小幅门槛 + 延时预算 + 当前场景 + 阈值错误/计数；单看任意一个不够。
6. 暗光锁定：是否收到匹配回调、主辅是否同时满足进入、计时是否连续；用户主动关闭自动必须退让。
7. 手动面板：确定主屏节点、权限、原生就绪与租约、写入/补写/读回，不把“UI 支持”当“Root 可写”。

### 最近资料

| 路径 | 价值 |
| --- | --- |
| `output/diagnostics/analysis-20261007-003323/` | 暗光计时不开始、protected HBM 阈值失效的分析 |
| `output/diagnostics/analysis-20261007-005433/` | 另一用户手动节点问题，后续确认临时 Root 限制，不能据此归咎 MTK |
| `output/diagnostics/analysis-20261007-014516/` | 0→4～6 lux 循环、夜间分支绕过；有 `analysis.md` |
| `output/diagnostics/uevent-current-20261006/` | 本机 uevent 排查提取资料 |
| `D:/QQ/HyperLux-uevent-current-20261006-175644-21912.tar.gz` | 原始 uevent 对照包 |

### ueventd CPU 高的结论不能扩大

用户曾测到 panel0 change 约 60/s（有更高波动）及 ueventd 20～25% 单核。strace 表明它被动处理 sysfs/SELinux 标签，不是其自身死循环。采集时亮度仍在变化，**正常 ramp 的按帧更新可能就是 60Hz**，不能仅凭频率判 bug。

uevent 不包含写入者，持有 FD 也不等于正在写；系统驱动通知不必来自用户态文件 write。采集时 HyperLux 原生守护未运行，只能排除那一刻它直写，不能排除 Hook 间接改变目标。

其他用户没有该现象。下一步应固定光线/页面/位置，比较现状、APP 停用、LSPosed 禁用并重启三组稳态数据（事件频率/亮度变化/CPU/进程）。不要 patch ueventd 或长期 strace 来掩盖源头。

## 10. 构建与验证

### 本机环境

- Windows PowerShell；Python 3.13：`C:/Users/28065/AppData/Local/Programs/Python/Python313/python.exe`。
- JDK 17.0.20.1（Microsoft）：`C:/Program Files/Microsoft/jdk-17.0.20.101-hotspot/`。
- Android SDK `D:/App/SDK`，platform 37 / build-tools 37.0.0。
- NDK `D:/App/SDK/ndk/28.2.13676358`，ARM64 静态 C、API 34。
- Xposed API 82 编译 jar 会核对固定 SHA，位于 `build/refactor-hook/deps/api-82.jar`。
- 主机 JSON 依赖 `build/refactor-diagnostics/json-20240303.jar`，固件测试读私有 fixture。
- Bash 语法核对使用 `C:/msys64/usr/bin/bash.exe`。
- 这是脚本驱动的 javac/aapt2/d8 构建，**不是 Gradle 项目**，不要为此另起一套工程。

### 常用命令

```powershell
cd D:/IOS/LumaCurve
python -u -X utf8 tools/build_refactor_hook_test.py *> build/next-build.log
```

完整脚本编译真实 Java/C，执行现有回归和固件核对，资源打包、签名、zipalign、检查最终 APK 原生资产，再生成 helper/source/recovery/bundle。失败后先看日志，不把残留旧 APK 当本次成功。

新环境没有用户私有固件时，可明确使用：

```powershell
python -u -X utf8 tools/build_refactor_hook_test.py --skip-device-fixtures
```

这只跳静态固件核对，仍做运行时兼容校验；必须注明跳过，不能说完整固件检查通过。

局部改变优先跑对应测试，必要时再完整打包：

```powershell
python -u -X utf8 tests/refactor_advanced_hooks.py
python -u -X utf8 tests/refactor_advanced_firmware.py
python -u -X utf8 tests/refactor_brightness_control.py
python -u -X utf8 tests/refactor_configuration.py
python -u -X utf8 tests/refactor_panel_nodes.py
```

版本必须同步 `AppBuild` 的 BUILD/VERSION/ARTIFACT_VERSION/TEST 和 Manifest 的 versionName/versionCode；同号重发也需提高 versionCode，避免覆盖安装/更新判断混乱。测试独立产物目录由 ARTIFACT_VERSION 决定。

签名 keystore 在本地 `build/refactor-hook/test-signing.p12`，历史文件名 test-signing **不意味着可以随意重建**，当前覆盖安装依赖相同证书；不要删除或提交私钥。签名参数在构建脚本，本文不复制凭据。

### beta05 已通过的代表性检查

| 检查 | 数量/范围 |
| --- | --- |
| Refactor 核心 host | 1005 |
| 高级 Hook/暗光回调模型 | 518；资格门控直接提取生产方法，含日志中的 0～6 lux 序列 |
| 高级固件静态检查 | 116，两套已有 OS4 固件 |
| 实际亮度控制器的服务替身回归 | 170，含 50 lux/1 s、两侧分别退出 |
| 配置迁移 | 2288 |
| 传统映射 | 2091；固件检查 59 |
| 户外 | 225；三套固件静态检查 180 |
| 节点发现/原生验证 | 116（78 Java + 38 native） |
| 原生 Root 事务 / 输出隔离 | 45 / 26 |
| 记忆存档 / 生命周期 / 固件 | 148 / 40 / 66 |
| 状态传输/确认 | 117 |
| APK 内原生资产 | 14，最终包读出校验 |

另有诊断、用户身份、系统版本、磁贴/相对手势、socket transport、图形比较等；完整结果看 `build/beta05-build.log`。这些为主机模型/静态核对，**不是 Android/ART Hook 实际执行或真实 UI 渲染测试**。

beta04 曾核对暗光锁定及原生手动输出与 beta03 一致；beta05 只新增锁定默认/预设和兼容加载区别，核心 DarkLockPolicy 没改。Java 源码 hash、APK hash、源码合集与 ZIP 完整性已核验。README 文案因构建时序在 beta05 文档合集内单独同步过，运行代码与核验 hash 对应。

### 实机安装与恢复

更新 APK 后重启，LSPosed 启用 Android 系统框架作用域；进详细读数看 runtime.build，不能只看 APP 页面版本。已保存配置不会自动切到新默认，想用 beta05 默认点“睡前保持”后保存。

恢复优先用面板“恢复自动”、APP“停用并恢复”；无法进 APP 时使用同包恢复工具。在**已解压目录**下 Root 终端执行：

```sh
sh ./restore_refactor_hook_android.sh
```

用户偏好手机命令用相对路径，不给依赖 PC 绝对路径的 Android 命令。不要把老 KSU 的 `luma_curvectl.sh resume` 当现 APP 的恢复方案。

## 11. 网站、感谢名单与发布流程

### 当前授权

用户历史上已将仓库改为公开并发布 2.3.0；但本轮明确是 **2.3.1 beta 测试，不推送，等实测确认**。不能用更早“推送正式版”的消息自动授权本次发布。

本文没有执行 git commit/push、创建 tag/Release、同步正式网站、部署服务器。后续用户明确批准发布后，才按正式流程操作。

### 版本/更新来源

- APP `UpdateChecker` 读 GitHub `releases/latest`，只认可信的本仓库 **APK** 资产，拒绝 KSU ZIP、draft/prerelease、异常 tag。beta 与同语义正式版的处理还取决于 AppBuild.TEST。
- 官网 `website/update.json` / `update.js` 是独立静态站元数据，`tools/sync_website.py` 从当前正式 Manifest/产物/说明生成。
- 根目录 `update.json` 仍是旧 1.0.0 KSU 元数据，并被 Git 忽略。**不要运行 `tools/release_metadata.py` 来发布当前 APP**：那个脚本读取 `module/module.prop`，会把 website 更新元数据改回 KSU ZIP。
- `tools/sync_website.py` 期望正式包在 dist 根目录，并要求网页版本、代码和链接一致；不能拿当前 beta 身份直接跑它，也不要临时改回版本仅为骗过校验。

### 感谢名单必须防止覆盖

通常数据源是 `website/thanks.json`，APP 在线从 `https://lc.rongshangs.top/thanks.json` 读取，网站通过 JSON/JS 离线回退。本次核对发现一个**已知差异**：

- `website/thanks.json` 当前 12 项。
- 用户此前明确要求仅加网页副本，因此 `D:/IOS/web/thanks.json` 当前 13 项，多了：**And：感谢大佬的制作，户外再也不怕摸黑了**。

不能为了“同步”直接将 12 项覆盖 13 项；下次网站发布先保留/合并这个差异，再按用户授权确定统一数据源。**本轮没有合并，也没有更新随包名单。**

名单包含戒戒、Starshine、H*P、*意、*弎、Albert_L、立秋、GIVEFnI、*🥜、Msk包子、吾心之铭、全民制作人；以 JSON 的确切昵称/留言为准。感谢名单变更不写版本更新说明。

### 用户批准正式发布之后

1. 等用户 beta 实测反馈，审查本轮 diff，保留其既有配置和未关联改动。
2. 统一正式版本身份和新的 versionCode，TEST=false；编写新 release note/README，不把静态测试写成全设备实测通过。
3. 修改 `website/index.html`、首页版本/摘要、下载链接和必要文案；先核对感谢名单差异。
4. 完整构建，检查 APK 版本、签名、资产、hash、source/recovery 匹配。
5. 正式身份及产物准备好后运行 `python tools/sync_website.py`，需要交给用户搬站时 `python tools/package_website.py`；包解压即站点根目录。
6. 仔细选择 git add 的文件，排除 keystore、用户分析包、固件 JAR、build/dist 等；用户明确授权后 commit/push/tag/Release，上传对应 APK/源码/恢复合集。
7. 网站服务器仍由用户自行搬入，别将本地 web 同步说成已上线。确认更新检查将指向正确 APK。

## 12. 工作区保护与目前待办

### 未提交工作

本轮修改含 AppBuild/Manifest、节点发现与 C 校验、Root 事务/弹窗、暗光监听/阈值/夜间门控、默认值/迁移、诊断、相关测试及构建脚本；新 docs/releases/beta01～05 尚未跟踪。

此外以下研究文件是此前已存在/并行历史资料，不能当本轮垃圾清掉：`experimental/official_curve/`、`tools/analyze_framework_firmware.py` 的既有修改、`tests/official_curve_*`、`tools/official_curve_probe/`、`tools/build_official_curve_test.py`、`tools/collect_official_curve_android.template.sh`、`tools/package_official_curve_collection.py`、`tools/collect_main_panel_nodes_android.sh`、`tools/package_uevent_churn_probe.py`、`tools/uevent_churn/` 和 `output/`。

不要执行清工作区的 git clean/reset；提交前自己看 `git status --short` 和 diff。这里的“未跟踪”不等于可删或可公开。

### 接下来最需要做

- 等用户 beta04/beta05 的暗光稳定实测；若仍波动，先核对新包已在 system_server 生效，再沿接受照度/阈值/场景/记忆/输出分析，不扩大成自动 raw 写节点方案。
- 实机验证 beta05 默认/睡前保持：10 lux 1 min 进入、任一侧 50 lux 1 s 退出、锁屏/用户手动/重新开启自动退让。
- 节点自动发现已在 host 文件系统模型验证，不承诺全部实际设备；临时 Root 写 sysfs 限制无法靠找路径绕过。
- 更早 ueventd 报告只部分定性，尚未完成同环境三组稳态 A/B；不要写成已解决的确定 bug。
- 出正式版前整理 README/实验目录说明的旧身份与旧默认、官网正式摘要，以及感谢名单源/部署副本差异。本轮只做交接文档，不替用户发布。
- `build/verification.json`、源码 ZIP、旧截图等可能不是最新；始终核对 build/code/hash。

## 13. 给下一个 agent 的工作方式

- 中文沟通，先说结论和接下来会解决什么；任务要持续做完，不反复让用户确认已经授权的可逆本地动作。
- 不擅自发布。本地修复、检查和打包可继续，最新 beta 发布仍待用户确认。
- 遇到涉及 system_server/显示链的改变，使用对应回归与既有固件，保持实例/线程/用户/场景边界；不要裸改静态全局阈值。
- 大部分参数默认沿用系统，新增能力按接口探测开放；单位、支持/启用/本次实际执行/实际面板值要分清。
- 在 PowerShell 里读写中文用 `python -X utf8`，复杂多行用 here-string；不要把敏感数据拼进可执行 shell 文本。搜代码优先 rg。
- 没有连上的 Android 设备；Root Agent 是用户手机上的另一程序，只能提供明确脚本/指令并等其输出，不能宣称已替用户在手机执行。
- 本文是交接资料，不是让下一位在没有新需求时继续大改系统逻辑的授权。
