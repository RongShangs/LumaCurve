# PR #1 / #2 稳定性筛选审阅

审阅日期：2026-10-09（Asia/Shanghai）。按用户要求：保留本地 2.3.2-beta01；排除外观改版、新增产品需求、分支身份及发布内容；仅整理对现有功能与稳定性有价值的变更。

## 结论

不整包合并任何一个 PR。建议在 beta01 上选择性吸收 **5 项改进，涉及 4 个 Java 文件**：4 项来自 PR #1；PR #2 的独立价值保留为现有图片的尺寸采样解码。PR #2 中重复的 PR #1 修改只吸收一次。

两处需改写后吸收：恢复自动亮度的设置读取必须留在异常保护内；图片优化去掉强制 RGB_565，保持现有色彩格式。已生成对应隔离候选补丁并验证，尚未应用到 APP 工作文件、打包、合并、推送或部署。

## 审阅基线

| 项目 | 固定版本 |
| --- | --- |
| 主线正式版 | `bf839360ba1c6d2b47ae2a7fe3f3c500e7ade1c8`，2.3.1 |
| 当前保留的本地基线 | 2.3.2-beta01 / 23201，`beta-2.3.2-permission01`，原生 `raw07-openprobe` |
| [PR #1](https://github.com/RongShangs/LumaCurve/pull/1) | `2b1e75d87a0259e192a1a95cdc1ef933ac059e3a`，17 文件，+107 / -22 |
| [PR #2](https://github.com/RongShangs/LumaCurve/pull/2) | `feb9e341e2e57648b1dca2b095afdff11bd33854`，25 文件，+1474 / -47 |

PR #2 包含 PR #1 的两个提交；比较两者的实际源文件也确认恢复流程、节点校验、测试与构建适配相同。没有将这些重复计为新增功能。

beta01 的 Java/native 实际打开检查、节点身份校验、raw07 身份、错误诊断、原权限记录与恢复全部保留。两个 PR 均未提供 beta01 的节点权限预判修复，不能以 PR 源文件整包覆盖本地基线。

## 建议保留的修改

| 序号 | 来源与位置 | 现有问题及改善 | 边界与处理方式 |
| --- | --- | --- | --- |
| 1 | #1 / #2，`BrightnessControl.relinquish()` | 从自动模式进入 raw 接管后，停用功能、修改相关配置或暂停租约出错时，旧清理路径只恢复暗光锁定，可能遗留为手动模式。 | 在清理前保存恢复资格，仅当 raw 会话确实由自动模式转入且调用允许恢复时恢复。用户原本选择手动时保持手动。异常保护需按下文改写。 |
| 2 | #1 / #2，`BrightnessControl.start()` | 系统进程重启时，旧代码仅识别暗光所有权；由 raw 面板暂停的自动模式遗漏恢复。 | 在主用户、合法会话、`was_auto=true` 时恢复自动模式；不恢复旧 raw 目标或重新接管，不把用户手动模式变为自动。 |
| 3 | #1 / #2，`RootControl.stop()` | Hook 未连接时，“停用并恢复”的兜底只识别 dark 所有权；raw 会话的原自动模式遗漏恢复。 | 停止守护后，按已有所有权标记和原模式恢复；确认失败时保留失败提示，不假报恢复成功。原本手动、其他所有者和其他用户标记不触发恢复。 |
| 4 | #1 / #2，`NativePanelRoot.start()` | 旧顺序先安装/替换守护，再校验所选节点；过期选择可能先打断已有守护，然后才被拒绝。 | 安装前先核对 path、canonical_path、maximum；安装后再核对一次。只替换此方法，保留 beta01 的 status/probe、raw07 匹配和原生资产。 |
| 5 | #2，`MainActivity.image()` / `decodeAsset()` | 现有 1080×1080 头像以原尺寸解码，即使只显示 46dp，增加瞬时内存和解码负担。 | 仅提取按显示尺寸读取 bounds、采用二次幂 inSampleSize 的逻辑；保留原布局、图片、主题和默认色彩格式，不引入背景图片选择器。 |

图片优化属于内存优化，不是已证实的崩溃修复。以 3 倍密度、46dp 头像为例，候选采样为 4，解码尺寸约 270×270；若按每像素 4 字节估算，像素缓冲从约 4.45MiB 降至 0.28MiB。这是尺寸与缓冲估算，不是手机实际内存测量。

## 必须修正的 PR 问题

### [P2] 自动模式读取发生在清理和异常保护之前

两个 PR 的 `BrightnessControl.java:122` 均新增：

```java
boolean restoreAuto = restore && !auto() && (...);
```

该表达式在 `stopRaw()`、所有权释放、定时器取消以及后续 `try/catch` 之前求值。如果 Settings 读取抛出异常，清理提前中断。故障注入已在原 PR 代码复现 `IllegalStateException: settings unavailable`，栈位于 `auto → relinquish → stop`。

候选补丁只在前面计算不访问系统的恢复资格：

```java
boolean restoreAuto = restore &&
    (previous.equals("dark") || previous.equals("raw_panel") && rawWasAuto);
```

完成本地清理后，在原有异常保护内执行 `if (restoreAuto && !auto()) setAuto(true)`。同一故障注入下，候选逻辑能释放 owner、raw target、租约，并留下错误记录。没有声称系统服务不可用时仍能成功写入自动模式。

代码依据：[PR #1 固定提交的恢复方法](https://github.com/shisjsji/LumaCurve/blob/2b1e75d87a0259e192a1a95cdc1ef933ac059e3a/experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java#L121)。

### 图片缩小与色彩格式拆开处理

PR #2 强制使用 RGB_565，降低了色彩精度，且不保留 alpha。当前三张随包图片是 RGB JPEG，但该通用方法不必靠改变色彩格式来获得尺寸采样收益。候选补丁只保留 inSampleSize，去掉 RGB_565，避免附带外观变化。

代码依据：[PR #2 固定提交的解码方法](https://github.com/shisjsji/LumaCurve/blob/feb9e341e2e57648b1dca2b095afdff11bd33854/experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/MainActivity.java#L72)。

## 排除项及理由

| 修改 | 处理 | 理由 |
| --- | --- | --- |
| `GlassDockView.java`、两份 AGSL 着色器及相关生命周期、手势、渲染缓存 | 全部排除 | 新玻璃导航的实现与自身优化；当前产品没有这些组件，不能计为现有功能稳定性收益。 |
| 自定义背景选择、清除、持久 URI、后台解码与设置入口 | 全部排除 | 新需求；它的内存和生命周期处理只服务新增背景功能。 |
| 内容容器重排、浮动 Dock、100dp 底部空间、透明系统栏、Insets 改造、卡片透明度/阴影/边框 | 全部排除 | 外观与布局改版，且会改变状态页可用高度；不带入候选补丁。 |
| Dock 左右滑动切页、拖动指示器、图标/文字尺寸和标签改造 | 全部排除 | 新导航交互及外观，依赖玻璃容器。 |
| 设置返回的 `commitBack` 分支 | 本轮不保留 | PR 对所有子页面返回都直接取消原有过渡动画，没有区分预测性手势与普通返回；改变现有动画行为。若后续有实际闪动反馈，应独立定位和针对性修复。 |
| 分支作者头像、微信群、文案及对应翻译 | 全部排除 | 改变现有关于页内容与信息顺序。 |
| 2.4.0 / 24000 或 lsp.2 / 23107 身份、正式版展示、交接文档和 Release/SHA 清单 | 全部排除 | 属于分支发布，不用于当前 beta01 筛选；不能覆盖当前版本与原签名发布流程。 |
| 新签名的分支 APK 与卸载安装指引 | 不采用 | 本地保留原签名；筛选源码不需要采用分支二进制或重建密钥。 |
| `tests/native_host.py`、MSYS→WSL 编译/脚本检查回退 | 暂不吸收 | 改善构建机器兼容性，不改善 APP 运行稳定性；本机已有 MSYS 工具，可在独立构建维护任务中处理。 |
| row() 重载、常量替换、ripple() 等价改写 | 不吸收 | 服务新增布局或等价整理，无需增加补丁范围。 |

测试方面，PR #1 新增的 restart、stop、pause failure 回归有价值，应与功能修改一起保留。其 Root 断连恢复和节点顺序检查原本只是检查源码字符串，并未执行实际方法；本次审阅补充了实际方法的替身测试。

## 本轮验证

所有运行均位于忽略的 `build/pr-inspection/`，未在 APP 源码上合并 PR：

| 验证 | 结果 |
| --- | --- |
| PR 的控制器回归运行于当前 beta01 | 预期失败，case 62 复现 raw 重启恢复遗漏 |
| PR 的控制器回归运行于筛选候选 | 185 项通过 |
| 原有回归与本轮补充的手动模式、配置变更、重启、非法会话/用户、设置读取故障 | 197 项通过 |
| 原 PR 代码运行同一设置读取故障注入 | 预期失败；异常在清理之前逸出，确认上述 P2 |
| beta01 节点权限回归运行于筛选候选 | 130 项通过，包含 0444 实际打开和旧判断负对照 |
| 实际 Root stop、Native start 方法及图片解码方法的边界模型 | 88 项通过 |
| 筛选候选的全部 APP Java 源码 | 使用现有 SDK / Xposed API 编译通过 |

这些检查是主机模型与编译验证，不是 Android 实机验证。本轮没有完整重新构建 APK、重新签名或发布；已交付的 beta01 APK 保持原样。

## 可交接材料

- 候选补丁：`build/pr-inspection/stability-candidates.patch`。相对于当前本地 beta01，仅改上述 4 个 Java 文件；包含 P2 异常保护修正及去掉 RGB_565 的解码采样。
- 隔离候选源码：`build/pr-inspection/selected/experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/`。
- 可复跑的审阅验证：`build/pr-inspection/verify_candidates.py`、`verify_review_edges.py`；具体结果和故障负对照在同目录日志。
- 正式整合时，应将本轮故障注入、Root stop、Native start 的有效回归迁入项目测试；无需带入审阅用的完整临时副本。

用户对 beta01 的保留要求优先。后续如整合，应基于 beta01 应用选择性补丁、保留原签名和既有需求，再构建新测试包；不以合并整个 PR 代替选择性整合。
