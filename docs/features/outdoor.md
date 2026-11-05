# 户外增强目标：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 户外增强目标 · `outdoor`

| 项目 | 要求 |
| --- | --- |
| 触发 | 明确启用，持续强光完成确认，普通自动亮度、用户/屏幕和保护条件允许；驾驶能力确认缺失时按未驾驶判定，残缺或歧义仍保持关闭。 |
| 需求与行为 | 持续强光且普通自动亮度保护允许时提高目标；无HBM数据仅用原生普通范围；保留确认、强度、会话与冷却；HBM 数据身份按已验证变体选择。输出归属失败的写门保持严格（任一判定帧不通过即不写），仅退出对单帧 mBrightnessReasonTemp 失败容忍：连续 2 次或持续 50ms 才中止并冷却；受保护 modifier 与上游 block 立即退出 |
| 退出 / 失败 | 离开强光、手动、熄屏、过热、会话超时或停用退出，并遵守冷却；输出归属瞬态单帧不计入退出，正常帧恢复写入。 |
| 默认 | `off` · 新配置关闭 |
| 配置键 | `outdoor_enabled`、`outdoor_enter_lux`、`outdoor_full_lux`、`outdoor_enter_ms`、`outdoor_exit_ms`、`outdoor_exit_ratio`、`outdoor_strength`、`outdoor_session_ms`、`outdoor_cooldown_ms` |
| 依赖能力 | 由本项实际接口独立判断 |

## 分叉与收束

### 选择边界

覆盖目标入口优先；明确缺入口且旧调用所有权完整时走传统自定义目标，残缺覆盖接口不回退。 原生HBM数据确实为null且deviceSupportsHbm为false，普通范围和当前上限均有效时采用native_range_only；目标沿用同一自动SDR入口，限制到实时原生上限，不制造HBM配置或绕开原生下游保护。存在但残缺的数据仍拒绝。 HBM 数据身份按 HbmAccess 变体选择；两种身份之外的结构整体拒绝。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `exclusive` |
| 统一输出 | 户外目标、进入条件与可逆参数应用 |
| 坐标与身份 | 本机nit/逻辑坐标由曲线后端维护，不把百分比当nit；缺HBM配置时使用真实0—1框架亮度和实时允许上限，不推断nit。 |

### 清理及回滚

必需 Hook 成组事务安装，失败整组撤销；可选范围 / 跟踪失败独立，不撤销已经提交的核心。 无HBM分支不写数据、计时和范围；数据替换先校验后切换，配置恢复可回原路线。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `override_target` · 系统覆盖目标入口 | 通过 getOverrideLimitBrightness 增强自动目标，保留原生手动接管与下游保护。 |
| `legacy_custom_target` · 传统物理曲线目标入口 | 没有覆盖目标接口且旧调用结构完整；在原生自定义曲线目标返回后增强，仍经场景否决、范围裁剪及温控输出。 |
| `native_range_only` · 原生普通范围增强 | 原生明确没有 HBM 数据且支持谓词为否；在实际允许的亮度范围内提高强光目标，不新增 HBM 范围或预算。 |

路线内部 ABI：`outdoor_hbm_data_presence`、`outdoor_sdr_signature`、`hbm_data_identity`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`outdoor.target_compatibility_profile`、`outdoor.range_profile`、`outdoor.range_only`。

验证入口：

- [refactor_outdoor.py](../../tests/refactor_outdoor.py)
- [refactor_os3_compatibility.py](../../tests/refactor_os3_compatibility.py)
- [refactor_hook_transactions.py](../../tests/refactor_hook_transactions.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [HbmAccess.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HbmAccess.java) | `1b60fcde7b4ab2b7` |
| [OutdoorController.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorController.java) | `7c041c22158401f2` |
| [OutdoorPolicy.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorPolicy.java) | `4439f553657f8494` |
| [OutdoorTuning.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorTuning.java) | `84ee79e3de745ecc` |
