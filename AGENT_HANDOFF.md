# 当前追加交接：3.0.0 正式发布（2026-10-10）

- 用户明确要求完成温度标红与测试贡献排列后正式推送。当前 release-3.0.0 / 30002，TEST=false、INTERNAL=false，原包名原签名；可覆盖 3.0.0 内部测试版。
- 温度阻止户外高亮与实际温控限亮均将有效温度数值/单位标红，下方状态词不变色；无效读数、过期证据及普通状态取消标红。测试贡献单独卡片，左对齐“勿忘 灭”，空格分隔。
- 保留 r17/r18 防护与场景设置，未移植 PR #3 的高频调度修改。K90PM/K80PM 偶发解锁卡死根因仍未确认，不把正式版本身份当成修复证明，本轮不操作手机。
- 内部 r2 产物备份在忽略目录 build/pre-3.0.0-release-30001/。正式发布检查 main/v3.0.0/Release、官网版本元数据与三份下载 hash、源码和恢复包、原签名一致性；感谢名单保留线上全部条目。
- 私有设备材料、固件、认证与签名私钥不提交、不分发。宣传海报单独生成 1:1，使用 imagegen 内置工具。

以下为历史交接：

# 当前追加交接：3.0.0 r2 界面修正与 PR #3 审阅（2026-10-10）

- 用户反馈r1场景文字裁切，改为融合三行分享实际可用高度、每行autoSize/includeFontPadding，融合88dp、保护108dp，总最小588dp保持。温控下方去“电池温度·”仅状态词；用户最新明确仅“正在限亮”且温度有效时数值和单位标红；“温度阻止高亮”和单独高温保护不标红，下方状态不标红。
- 关于页感谢名单、测试贡献、代码贡献各自独立卡片；测试贡献仅勿忘/灭并列昵称，去简介，不进JSON。当前internal-3.0.0-r2 /30001，原签名，未推送/发布/部署，未安装/操作手机。r1保存在build/pre-3.0.0-r2-30000/及原r1别名。
- 几何5191、文字978项通过；完整构建和归档核对以本版build-info/最终审计为准。仍无本版真机验证，不宣称故障机型问题已解决。
- PR #3审查固定head e98f99a02ee8528f786ddbdf7c124a38adaebc71，refs/review/pr-3只读获取；隔离原PR测试183、补充26项通过。有意义的是AOD边界与调度诊断，但250ms窗口被所有显示变化反复续期、停用无任务仍报告检查状态，不能整包合并。原3.0.0控制代码未移植PR。详细docs/reviews/pr-3-scheduling-20261010.md。

以下为历史交接：

# 当前追加交接：3.0.0 本地打包 30000（2026-10-10）

- 用户要求整理状态页、户外设置和关于页后打包为3.0.0，暂不推送。最新纠正栏目为“测试贡献”，勿忘：协助适配K90PM；灭：协助修复传统曲线设备漏洞，放代码贡献上方，静态内容不进JSON。
- 当前internal-3.0.0-r1 /30000，原包名、原签名，INTERNAL=true、TEST=false，未发布/推送/部署，未操作或安装连接手机。r18交付备份build/pre-3.0.0-24018/。
- 场景去独立背景/minHeight，融合80dp、主辅与屏幕读数80dp、保护116dp、普通间距16dp、阈值行32dp、最小588dp；固定几何随窗口/字号调整。温控仅“正在限亮”用主题红色；LocalText保留已翻译Spanned，修复样式被toString剥离。
- 两个高亮范围/画面限亮开关移至户外主开关下面，精简说明，未改依赖/默认/实际控制。r17/r18防护保留，无采样率或亮度设置变更。
- 几何5191、文字与LocalText样式770项通过；完整构建结果以本版build-info和最终归档审计为准，无3.0.0真机结果，不宣称K90/K80卡死已解决。报告docs/review-3.0.0.md，完整日志docs/releases/3.0.0.md。

以下为历史交接：

# 当前追加交接：K90 OTA 与 r18 恢复时序修复 24018（2026-10-10）

- 用户提供 D:/myron-ota_full-OS4.0.0.33.XPMCNXM-user-17.0-a49104c5c2.zip，并称K80PM也有类似故障。K90确认physical_mapping，K80仅症状反馈，无后端/栈，不能判定传统曲线全系有问题。用户不希望第三方频繁协助复现。
- 已本地选择解包8个分区；services.jar、miui-services.jar、framework.jar与故障包哈希相同。重点反汇编显示、记忆和Watchdog，未完成内核驱动逆向。工具、镜像、固件、反汇编均只在忽略的build/k90-ota-20261010/，不进源码或交付。OTA哈希1d91da34ad1b788f5255ff6f5dcc32bf5d13764d1b05b9c3afb05dab3b858926。
- 发现getter内同步恢复记忆、内层clear回调在外层reset结束前处理通知的调用顺序风险。r18新增合并排队恢复/通知，所有OEM恢复入口返回后再执行，保留原确认等待/期限/新手势取消；两后端共用，无采样/默认配置变更。传统2091、记忆168、OTA ABI59、诊断91项及全量构建/归档核对通过。
- 固件找到 /data/miuilog/stability/scout/watchdog 的watchdog_pid_/pre_watchdog_pid_文本，补入同次导出；/data/anr增加BinderTraces_pid前缀。不收hprof。Watchdog原生抓栈名单含vold，不能由此判定根因。
- 当前internal-2.4.0-r18 /24018，原签名INTERNAL=true/TEST=false，未发布或推送/部署，未操作或安装连接手机，当前手机仍r15，无r18实测。r17交付备份build/pre-ota-review-24017/，APK dist/HyperLux-2.4.0-internal-r18.apk。报告docs/reviews/2.4.0-k90-ota-20261010.md；不宣称K90/K80卡死已解决。

以下为历史交接：

# 当前追加交接：r17 状态写入隔离 24017（2026-10-10）

- 用户追问是否只做取证，已明确承认r16没有直接防卡死改动。r12/r13有预防修复但r15仍复现，不能称已解决。
- 本轮发现HookRuntime状态分片和配置确认同步写设置服务仍占用显示线程。新增DeferredStateWriter，将这两类遥测写入交给独立单线程，待写快照与确认各最多一份，完整替换快照、保留分片/core提交顺序，关闭取消待写；已进入服务的单次调用不能撤销。配置读取、记忆保存及控制写入保持原流程。
- 当前internal-2.4.0-r17 / 24017，原签名、INTERNAL=true、TEST=false。生产后台写入21项（真实阻塞与上万次合并），恢复100项，全量构建和归档审计通过。本轮未安装/重启/操作连接手机，不继承r15真机结果。减少显示线程等待风险，但尚未证实K90根因或卡死解决。
- r16完整备份build/pre-state-io-24016/；交付dist/HyperLux-2.4.0-internal-r17.apk，报告docs/reviews/2.4.0-deferred-state-20261010.md。未推送、发布或部署；不要求第三方反复复现。

以下为历史交接：

# 当前追加交接：K90 r15 再次故障与 r16 诊断修复 24016（2026-10-10）

- 用户明确故障来自其他用户，不要频繁要求其协助复现。后续用现有材料及本地分析推进，不要求切设置、主动复现、反复安装或 ADB 抓包；没有新问题待回复。
- 最新私有分析包 20261010-145029 确认 internal-2.4.0-r15 仍发生唤醒卡死；14:49:35 多进程 DeadSystemException，系统进程由5048变23909，uptime持续约79分钟。对方明确三四分钟后自行恢复，非手动重启。缺少故障栈，尚未确认 Watchdog 或死锁，更不能宣布修复。
- r15 DropBox 导出把三天前日期当下限，实际是精确搜索，五份日志漏查当天。r16 去掉该过滤，故障大输出保留尾部，新增最近72小时原始 ANR/DropBox 文件，分别最多6/10份，每份8MiB。仅按需导出，不改变亮度、采样、默认配置或原生组件。诊断主机86项，完整构建与归档审计通过。
- 当前交付 internal-2.4.0-r16 / 24016，仍为未发布内部测试版、原签名、TEST=false。连接的 pandora 手机保持 r15，本轮未安装、重启或操作；r16 无真机结果，不继承 r15 device_smoke。
- 专项报告 docs/reviews/2.4.0-k90-repro-20261010.md；完整更新日志 docs/releases/2.4.0.md；说明 docs/reviews/2.4.0-k90-user-guide-20261010.md。r15备份 build/pre-repro-diagnostics-24015/，新故障材料只在忽略的 build/k90-repro-20261010-1450/。
- 对外 APK dist/HyperLux-2.4.0-internal-r16.apk；未推送、发布或部署。签名、服务器认证和用户原始包/固件不进公开归档。

以下为历史交接：

# 当前追加交接：2.4.0 内部测试标识、实装与完整更新日志 24015（2026-10-10）

- r15 安装后的辅助光感专项只读排查见 docs/reviews/2.4.0-assist-table-20261010.md。保存70帧及4组display/sensorservice，配置原字符串保留；辅助策略零值约40秒、普通辅助事件间隔约43秒，主侧仍生效，夜间驾驶/重置等待均未生效。背屏ON与此前主照度降到1.2lux符合OEM插零条件，但没有确切置零回调日志，仍是待证实解释。用户随后称恢复正常；已结束本轮密集采集，不改代码/背屏/采样率、不再次安装。私有证据 build/assist-table-20261010/，当前交付档案未因这次排查重打包。
- 用户明确本版未发布，名称必须为“2.4.0 内部测试版”。当前 `internal-2.4.0-r15 / 24015`，沿用原签名；不得称为正式发布，不推送或部署。关于页、构建元数据与当前交付说明已统一。
- 使用独立 AppBuild.INTERNAL=true，保留 TEST=false 的原签名校验，避免混同曲线兼容性测试包。正式版本号仍为 2.4.0，内部版可接收未来同版本正式版更新；UI/真实 HTTP 解析本地回归共74项。
- 此轮 Java 仅改 AppBuild、MainActivity 的关于标签、UiText 和 UpdateChecker；亮度、采样、原生节点与配置逻辑保持 r14。完整构建和固件静态检查通过，日志 build/internal-2.4.0-r15-build.log。
- 已在连接的 pandora 手机上保留配置覆盖安装并重启一次，系统钩子 active，版本代码24015，实装 APK 与交付哈希一致，配置原字符串保留。结果 build/device-smoke-internal-2.4.0-r15.json。没有重复开关屏幕、调亮度或做压力测试；不继承 r14 的 UI/诊断与指纹验证结论。
- 从2.3.1之后的全部有效变更整理于 docs/releases/2.4.0.md、CHANGELOG.md；包括 beta01、PR筛选、状态/场景/设置、稳定性功耗、更新与诊断。K90根因仍未确认，旧分析包实际来自2.3.1，不能宣称已修复偶发卡死。
- 对外转发使用 dist/HyperLux-2.4.0-internal-r15.apk、dist/HyperLux-2.4.0-internal-r15-更新日志.md 和 dist/K90PM-2.4.0-internal-r15-排查说明.md。未发布到官网或GitHub。r14完整交付备份 build/pre-internal-channel-24014/。
- 本地只读观察器仍保留（build/device-observations/，每5分钟、最多7天）；只记录，不自动改手机或发布。私有分析包、固件、签名与认证资料只留忽略目录，不进归档。

以下为历史交接，历史“正式”措辞不代表本版已发布：

# 当前追加交接：K90 唤醒排查、真机功耗与排版收尾 24014（2026-10-10）

- 当前交付 `release-2.4.0-r14 / 24014`，正式签名，用户要求先收尾，优先发 K90 Pro Max 用户排查。未推送 GitHub、未部署网站；不得自行公开私有分析包或认证资料。
- 已收 K90/myron/OS4.0.0.33 分析包，实际 APK 2.3.1/23106、Hook release-2.3.1、传统 physical_mapping。确认故障附近 system_server 由 829 换为 19080，设备 uptime 连续，属于系统进程重启。缺失故障时线程栈，不能归因于模块或其他定位模块的重复 Hook 异常。包内固件接口另核对 59 项。详见 docs/reviews/2.4.0-k90-wake-20261010.md；可转发使用说明 docs/reviews/2.4.0-k90-user-guide-20261010.md。
- r13 保留额外光感 1 秒请求、非唤醒/按变化上报；合并稳定突发判定，但保留无效数据和亮暗确认中断的立即处理。暗光/户外关闭时跳过多余服务检查。亮度控制 241、户外 338、显示调度 22 项；r14 新增按需优先收集 8 类故障记录，诊断 60 项，读取有超时和输出限制，不新增手机后台轮询。
- 当前手机 pandora 已装最终 r14，实装 APK 与交付一致，配置原字符串保留；r14 钩子已重启加载，之后仅排版修订覆盖安装，钩子控制代码不变。r13 六次息屏/唤醒通过且注销监听、PID 不变。r14 系统诊断导出 8 项成功、ZIP CRC 完整；诊断测试属于最终排版微调前的 APK，实际 SHA256 在 device_smoke 中分别标明。未验证指纹认证或 K90 真机复现，不把局部 smoke 当完整设备验证。
- 状态页融合卡片 88 dp，上下内缩 12 dp，数字 21 sp、场景点击区保留；常规间距 14 dp、阈值行 30 dp。重分配图表最小高度，正常窗口内无滚动范围；短屏/大字号保留既有可读性兜底。布局 5191 项通过，真机 UI 树零 scrollable 节点，截图已核对。
- 用户反馈辅助光感先显示 0：不是缺样本。r10/r13 OEM 辅助订阅均请求 250ms；系统历史曾有约 218ms 的有效零值，无法重建用户描述的较长等待。用户随后反馈持续亮屏时亮度波动，只读观察约20秒输入160.6lux、输出82.1nit保持稳定，未复现，继续列为待查。未为此调整采样或改写用户设置。
- 原始证据在忽略目录 build/device-power-20261010/、build/reported-freeze-20261010/；r12/r13 交付备份分别 build/pre-power-iteration-24012/、build/pre-fault-diagnostics-24013/。只读观察器 tools/observe_device_runtime.py 已隐藏启动，每5分钟、最多7天，输出 build/device-observations/；创建 STOP 文件可停。工具12项通过，仅采集，不会在聊天结束后自行改代码/发布。
- 用户询问屏幕变化后，已结束主动打开应用、滑动、开关屏幕等操作，后续仅只读记录和本地收尾。下轮先读当前交接与证据，不重复做主动手机测试，除非新任务需要且已向用户说明。

以下为历史交接：

# 当前追加交接：息屏与指纹卡死风险排查 24012（2026-10-10）

- 当前 `release-2.4.0-r12 / 24012`，沿用正式签名。用户仅提供截图并估计是 2.4.0：有 mi15 息屏指纹盲解卡死，另一机型仅称“90pm”；没有精确系统、开关、故障包或线程栈。不得把代码风险当成已证明的故障根因，也不能宣称已解决所有机型。
- 修复：屏幕状态前置钩子立即放行输出、队列清理；无所有权不写清除标记；采样诊断排队；OEM 重算合并且防重入；注册表锁内不调用 OEM getter/运行时 close。既有需求、默认参数、场景和曲线保持，详见 docs/reviews/2.4.0-unlock-stability-20261010.md。
- 全量构建通过，日志 build/release-2.4.0-unlock-stability-build.log。亮度控制 224、输出钩子 26、新增显示调度 22 项通过。交付一致性见 build/2.4.0-final-audit.json 与 dist/LumaCurve-2.4.0-SHA256SUMS.txt。
- r11 原交付与验证信息备份在 build/pre-unlock-stability-24011/。当前 ADB 无设备；r12 未安装、未重启、未真机复现。既有覆盖安装/重启授权保留，重连后可继续，不需重复询问。下一步重点验证暗光锁定、AOD、指纹，以及手动节点模式在延迟暂停租约时的硬件交接窗口；完整矩阵在报告内。
- 本轮只交付本地修订包，未推送、未部署。认证信息、签名私钥、私有固件及设备数据不归档。

以下为历史交接：

# 当前追加交接：读数排版与页内场景设置 24011（2026-10-10）

- 当前 release-2.4.0-r11 / 24011，正式签名。主辅光感和实际屏幕亮度统一原生文字，标题/读数间隔 8 dp，行高 80 dp；融合行 88 dp、场景蓝字可点击，lux 与运行徽标为正文色。保留边距 18、常规间隔 12、阈值行 28 dp，页面不随状态改变高度。
- SceneSettings 改为九场景页内展开、单选参与方式、进入/效果/退出分区；触摸和阳光屏已有等待参数并入对应效果区。其余效果沿用系统，不新增未经验证的原生参数改写。
- SceneOptions 增加 confirm_enter / confirm_exit（整数毫秒 0–60000），缺省均回退旧 confirm。ScenePolicy 分别使用两个延迟，原未知值/OEM 兜底保持。草稿表单验证在提交前执行；刷新不覆盖输入，保存失败定位展开区；活动保存保留最后有效草稿。
- 新增表单 157 项回归；场景 575、Hook 88、配置 2352、布局 3967、文本 646、导航 109 项。新报告 docs/reviews/2.4.0-inline-scenes-20261010.md。完整构建日志 build/release-2.4.0-inline-scenes-build.log；审计 build/audit_final_24011.py。
- r10 原交付备份 build/pre-inline-scenes-24010/。ADB 当前无设备；r11 未真机安装或重启验证，不继承 r8 真机结论。已存在重连后覆盖安装/重启授权，不需重复询问。只交付本地文件，未推送部署；私钥、认证资料和私有设备数据不归档。

以下为历史交接：

# 当前追加交接：排版与返回位置 24010（2026-10-10）

- 当前 release-2.4.0-r10 / 24010。输出保护标题居中且统一颜色；三项浅蓝圆角卡片、内容居中、无箭头，温控显示电池温度；页边距 18 dp，一般间距 12 dp，阈值行 28 dp，外层子卡内缩 12 dp、子卡间距 8 dp。
- 修复设置列表返回位置：进入前捕获，页头/底栏恢复布局后定位，避免焦点与快速切换覆盖保存位置；保存活动状态时保留列表坐标。
- 现有场景的自定义条件一直保留，本轮明确标为“自定义条件”；仅不允许用户新增命名场景，场景控制后端未改动。
- r9 原交付备份 build/pre-status-spacing-24009/。完整日志 build/release-2.4.0-status-spacing-build.log；审计 build/audit_final_24010.py。
- 当前 ADB 未检测到设备，r10 尚未真机安装验证。已存在覆盖安装/重启验证授权，重连后不必重复请求。只交付本地文件，未推送部署；认证资料、私钥、完整 dump 与固件不得归档。
- 下方 r9、r8 与更早内容均为历史记录。

# 当前追加交接：场景精简 24009（2026-10-10）

- 当前 release-2.4.0-r9 / 24009，按用户最新要求删除自定义场景、简化场景弹窗并定位生效项、场景设置分组编辑、手动阳光屏移至户外、状态页固定三栏输出保护。
- r8 交付备份 build/pre-scene-simplification-24008/。本轮完整构建日志 build/release-2.4.0-scene-simplification-build.log。
- 当前 ADB 无设备，r9 尚未真机安装验证。下面 r8 与更早记录为历史，不继承其真机结论。原用户已授权覆盖安装并重启继续验证，手机重连后无需重复索要授权。
- 不推送/部署本轮修改；服务器认证资料不得输出或归档。私钥、配置与完整手机 dump 只留忽略目录。

# 当前追加交接：场景工程 24008（2026-10-10）

- 当前 release-2.4.0-r8 / 24008，最新需求为完整梳理亮度链路、场景与设置分类。以 docs/reviews/2.4.0-brightness-scenes-20261009.md 为本轮行为依据，下面 r7 及更早记录为历史。
- 新增 SceneOptions / ScenePolicy / SceneController / SceneTuning / SceneCatalog / SceneSettings / StatusLimits。九类 OEM 消费者默认跟随系统，可限制参与和附加条件；最多六条自定义阻止规则，连续确认进入/退出，数据缺失恢复系统；绝不强制场景开启。17 类目录中未证明的背面距离状态明确未知。
- 状态页场景在融合区域，曲线蓝块固定 32 dp 单行、无分支入口；下方高光/低光两项独立且始终有内容，固定卡片 116 dp。融合行 80 dp，主辅/输出行 64 dp，间距 14 dp，最小页高 540 dp（普通字体），剩余给图表。设置十个二级页，温控独立在 9 组，场景 5 组，导航不改草稿。
- 用户先允许 ADB 读手机，随后明确授权覆盖安装和重启继续真机验证。先读旧 r6 与 pandora OS4.0.0.41 框架，哈希吻合 os41 样本；最终 r8 已安装并重启，APK 哈希吻合交付。完成后台规则、实际 UI 草稿/顶部保存测试，原 CONFIG 已逐字恢复，系统进程在测试期间不变，相关 crash buffer 无记录。未穷尽物理触发/强光温控/长期验证，具体见 docs/reviews/2.4.0-device-smoke-20261010.md。完整 dump/固件在 ignored build/device-scenes-20261009/，不得归档；服务器认证信息不得输出或上传。
- r7 原交付已备份 build/pre-scene-engine-24007/。完整构建日志 build/release-2.4.0-scene-engine-build.log；审计 build/audit_final_24008.py；真机基本验证 build/device-smoke-release-2.4.0-r8.json 仅匹配准确 APK 哈希。完成后刷新源码/合集，不重建或替换已安装 APK。当前无推送、公开 Release 或网站部署。

---

# 当前追加交接：2.4.0 本地正式版（2026-10-09）

- 最新截图反馈部分区域空：r7 将蓝色信息块改为左右排布（52 dp），模式卡片收紧到 116 dp，共腾出 32 dp 给图表。继续固定高度、保留长文滚动与全部入口。旧 24006 包备份 build/pre-spacing-24006/，日志 build/release-2.4.0-spacing-build.log。
- 前轮用户要求：句子按实际曲线替换并并入图下蓝色圆角容器，整体重新协调；模式外显，亮度分支分区，解释夜间驾驶和辅助横线。当前 release-2.4.0-r7 / 24007：CurveComparison.kind 统一绘图和说明，蓝色→基础曲线、绿色→已学习/手动记忆、灰色→系统默认，无曲线不假称已学习。蓝容器固定高度 52 dp，含句内入口与右侧查看分支；白色区域不另占说明留白。读数固定 64 dp、纵向间距 14 dp、图表至少 96 dp、模式区固定 116 dp（字体变化单独扩展），最小页高 544 dp，其余全给图表，短窗口滚动。
- 新增 StatusPresentation / BranchReport：当前模式显示在状态卡片和夜间融合标题；辅助显示场景停用/重置等待/采样暂停/等待采样等。HookRuntime 只读追加辅助采样、重置值状态和夜间驾驶输入；不改 OEM 判定、事件丢弃、亮度下限或额外确认。分支七组区分当前、最近计算、已应用配置和历史累计。系统场景未取得时不说普通模式；旧快照不当实时值。
- 两份私有固件确认夜间驾驶入口为驾驶识别与系统昼夜条件，启用亮度策略时设置模式，进入后重置辅助值为 -1；监听器在夜间驾驶+主光感参考时丢弃辅助事件。不能由标签确认用户在开车；当前设备频繁触发的上游识别原因仍需新的运行包。新增模式/光感/分区 918 项和固件说明 52 项，富文本 823、状态建议 113、布局 2617 项纳入构建。旧 24005 包备份 build/pre-scenes-24005/。日志 build/release-2.4.0-scenes-build.log。未实机验证、未发布部署。
- 用户追加日常状态说明：普通自动亮度状态显示基础曲线和手动记忆介绍，按已应用 memory_strength 区分开启/关闭/未知；句内蓝色基础曲线、手动记忆跳转到 0/1 组并在布局后定位，不改草稿。实际原因及保护优先。最终为 release-2.4.0-r3 / 24003，旧 24002 备份 build/pre-guidance-24002/。状态建议 144、导航 61、实际富文本/点击回调 108 项检查纳入构建，无 Android 实机验证。
- 用户追加更新弹窗需求：忽略此版本 + 官网/GitHub 下载。已实现本机 ignored_update_version，仅影响相同版本的自动提醒；点击关于更新状态可主动检查。官网下载打开官网，GitHub 打开校验后的对应 APK。tests/refactor_app_updates.py 的 44 项实际 UI 方法/策略模型检查纳入构建。最终身份提升为 release-2.4.0-r2 / 24002；24001 旧包备份到忽略目录 build/pre-update-24001/。
- 后续用户要求完整复审。最终修复记忆延迟保存身份隔离、零亮度平台、配置更新时清除旧限亮证据、请求时间刷新、监听器注册/清理与旧回调、建议跳转预绘制时序、正式签名及网站交付预检。报告 docs/reviews/2.4.0-stability-20261009.md，定向新增导航/发布保护测试均纳入正式构建。网站内容不随本轮更新。
- 用户要求五项界面调整作为 2.4.0，并沿用先前要求：保留 beta01，筛选两份 PR 中对既有需求与稳定性有价值的内容，排除玻璃等外观功能及需求改变。
- 当前身份 release-2.4.0-r7 / 2.4.0 / 24007 / TEST=false；原生 raw07-openprobe。沿用原签名，升级保留配置；安装后重启。
- 已按筛选应用 BrightnessControl、RootControl、NativePanelRoot 的恢复与次序修复，以及 MainActivity 图片按显示大小采样。修复 PR 自身读取自动模式异常会跳过清理的问题。未整体合并 PR、未修改签名和构建环境。
- 五项完成：状态曲线右边距；新鲜限亮证据及蓝色建议跳转；两处曲线图例紧贴图像并着色；九个二级保存按钮移到顶部；感谢容器下固定代码贡献文字“凌乱的风W”，不改感谢 JSON。
- 新增 LiveLimitEvidence、StatusAdvice。实时观测最长 5 秒、整体状态最长 15 秒；跳转只展开/滚动现有设置，不直接应用、不覆盖草稿。既有控制默认值、驱动保护、配置迁移保持原样。
- 正式交付 dist/HyperLux-2.4.0.apk、dist/LumaCurve-2.4.0-source.zip、dist/LumaCurve-2.4.0.zip；构建记录 build/release-2.4.0-scenes-build.log，实际 hash 与测试计数以 build/refactor-hook/verification.json 为准。完整构建不跳过既有固件静态核对。
- 本轮未推送、未部署 2.4.0，官网和 GitHub 公开版本仍为 2.3.1；未连接手机或虚拟设备，不声称实机验证。凭据与私钥不得进入项目、日志或包。
- 更新说明 docs/releases/2.4.0.md；审阅 docs/review-2.4.0.md；PR 筛选 docs/reviews/pr-1-2-stability-20261009.md。保留原有 official_curve、uevent 等独立研究文件和改动。

---

# 当前追加交接：2.3.2-beta01 主屏节点权限（2026-10-08）

- 2.3.1 正式版已推送 main（bf83936）和 v2.3.1，GitHub 正式 Release、官网已发布；公网下载 hash 和更新元数据已核对。旧站点已备份。服务器凭据未写入项目或包。
- 用户随后提供 2026-10-07 19:11 分析包和主屏面板权限失败截图。实机为 OS4.0、physical_mapping，运行 release-2.3.1；主屏 panel0-backlight/brightness 模式 0444，读回 951、上限 16383。诊断没有主屏守护日志、权限记录或健康文件，状态是 node_not_writable + Connection refused。
- 不能把 0444 直接认定为 Root 实际不能写。旧 Java 状态检查先按模式位拒绝，面板按钮因此禁用，守护根本没启动；原生启动和 acquire 也有同类判断。不能声称是 SELinux 拒绝，包里没有实际打开错误证明。
- 当前本地测试身份：BUILD=beta-2.3.2-permission01，VERSION=2.3.2，ARTIFACT_VERSION=2.3.2-beta01，TEST=true；Manifest versionCode=23201。原生身份 raw07-openprobe，socket 名不变。官网和公开正式 Release 保持 2.3.1；本轮未推送或部署测试版。
- Java/native 改为实际 O_WRONLY 打开并验证 FD 的设备、inode、规范路径；查询探测不写亮度、不 chmod、不启守护、不改模式。原生真接管仍记录原权限（包括 0444），持有 FD、去写权限、结束恢复。真正写失败仍按原事务回滚。
- 新增 tests/refactor_panel_access.py：实际 Java 状态和原生 open/acquire/release 方法，78 + 52 项模型检查；负对照恢复旧判断会在首个 0444 样例复现失败。模型不证明手机上的 DAC/SELinux/驱动可写。
- 现有用户配置 memory_strength=0、dark_lock_enabled=false，这是已保存设置，不能改成默认或当作本轮权限问题的原因。
- 详细解释、验证和交付说明见 docs/releases/2.3.2-beta01.md；以下历史文档以本节和实际代码为准。保留既有研究文件与未提交修改。

---

# HyperLux 项目交接文档

更新时间：2026-10-07（Asia/Shanghai）。当前交接范围：本地 **2.3.1 正式打包**。

## 当前状态：2.3.1 正式包（优先于下方 beta 历史）

- 用户已授权按既有需求审阅、修复、正式打包，并随后授权“推送部署”。本轮推进 GitHub main / v2.3.1 / 正式 Release，更新 D:/IOS/web 并生成部署包；用户随后提供私下连接信息并授权直接部署；服务器凭据仅用于本次连接，不写入项目、交付包或发布内容。
- 当前源码：AppBuild.BUILD=release-2.3.1，VERSION / ARTIFACT_VERSION=2.3.1，TEST=false；Manifest versionCode=23106，高于 beta05。
- 正式产物路径：dist/HyperLux-2.3.1.apk、dist/LumaCurve-2.3.1-source.zip、dist/LumaCurve-2.3.1.zip。最终 APK SHA-256 及源码 hash 以 build/refactor-hook/verification.json 和合集内 build-info.json 为准；构建日志为 build/release-2.3.1-build.log。
- 详细审阅和新增修复：docs/review-2.3.1.md；说明：docs/releases/2.3.1.md。修复保存返回覆盖等待期新草稿、暗光锁定转手动失败的自动模式恢复、接管 ACK 丢失的会话回滚、守护健康记录长期有效。
- 新增 tests/refactor_draft_and_health.py，纳入正式构建；配置迁移 2294、控制器 177、Root 事务 49、草稿与健康方法 23、原生格式 62 项定向检查已通过，完整结果看正式构建日志。
- 原生身份现在是 raw06-health；socket 仍为 hyperlux.main.panel.raw04。健康记录增加单调时钟第四字段，每 2 秒更新、超过 5 秒不可采信；Java 与 C 同步更新，旧三字段不采信。
- 继承 beta05 暗光默认：开启锁定，10 lux / 1 min 进入、50 lux / 1 s 退出；旧用户显式设置及早期未配置开关的关闭状态保留。
- 签名 keystore 未重建，证书应保持 a4c4759841927acf432b887f0c9bfc16fb8e30c0885ae00e8a6175b90731182d。更新后必须重启，详细读数确认 release-2.3.1。
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
