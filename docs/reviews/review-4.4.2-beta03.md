# 空 HBM 配置与亮度读数适配复审

日期：2026-11-03。公开基线4.4.2，累计Beta01—03；本轮4.4.2-beta03 /44203仅本地，不发布、不操作手机。

## 证据

笙歌醉梦红尘的 Redmi K50 电竞版 /21121210C /ingres HyperOS4.0.5.0移植包回传：运行Beta02，phase=active，有已应用revision，原生手动锚点更新多次。55份随包JAR与首次样本相同。实际节点275 /4095，driver_actual=0，亮屏且独立框架实际输出约0.066979684；无actual_nit和标定表。户外明确HBM data unavailable，并非用户设置错误；配置中户外开关未启用是能力拒绝后的状态。

实际固件 deviceSupportsHbm 同时要求mHbmData与metadata；hbmControllerEnabled包含该谓词；普通上限在不支持HBM时返回mBrightnessMax。mHbmData与metadata均为null；不能套捐赠机型nit或预算。分析包141条目按机型私有归档，CRC及展开SHA核对。

## 实现与边界

ScreenBrightnessReading集中读取并选择输出单位，MainActivity和BranchReport共享显示；节点读数不再仅在raw接管时展示，非raw无节点/无nit才用真实DisplayPowerState比例。保留既有有效nit路径和零/熄屏/换屏规则，不使用计算目标。

HbmAccess新增缺数据能力分支，要求字段类型和实际原生否定谓词及有效native getter；非null仍原验证，数据替换先验证后更新，copyable随实际数据重新判断。null不能因与未使用的applied引用相同而被误当作模块持有的clone。普通范围不改数据、预算或范围，OutdoorController用同一状态机和目标入口，纯策略在当前允许范围内按强度与照度插值；range/OPR能力保持不可用，RuntimeFeatureChecks同步区分能力，目录增加具体路线。

旧有配置HBM、传统入口、Refactor/物理/比例曲线、Root和保存设置均保留。没有新增模型表、用户配置键、线程/进程、传感器监听或轮询。关于感谢名单独立网站更新，不合入APK离线名单或更新日志。

## 核验

主机覆盖旧HBM计时与零计时、无配置目标强度/上限、屏幕/用户/HDR/电量/温控/手动/临时输出/光感/场景否决，缺配置与有配置替换、原生支持矛盾和无效范围拒绝、停用清理。读数覆盖普通节点显示、缺失或异常nit转换、框架比例和目标隔离、raw禁止旧框架比例、旧nit及零反馈。另对本次实际275/4095报告回放，核对同固件原生方法和108节点Simple样本。

完整构建/交付结果以build/beta-4.4.2-beta03/verification.json与local-audit.json为准；本轮无Android/ART、强光实测、物理测光或功耗结论。不能承诺驱动能达到捐赠机型的nit或额外HBM峰值。
