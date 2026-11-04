# 原生亮度过渡：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 亮度过渡 · `animation_override`

| 项目 | 要求 |
| --- | --- |
| 触发 | 开启过渡倍率且匹配已验证时长/传统速率ABI，系统执行允许调整的自动过渡。 |
| 需求与行为 | 原生时长与传统速率路线互斥，分别调整变亮变暗倍率，保留原生上限与特殊过渡；传统路线在 Refactor 策略标记与双动画槽整体缺失时按无 Refactor 策略处理，双 ramp 入口按 facade / owner 两种完整形状选择，自动策略名支持最后选择 getter 或归属一致的原生策略槽 |
| 退出 / 失败 | 离开普通慢速自动过渡时按系统处理；停用只恢复仍等于模块应用值的参数。 |
| 默认 | `native` · 跟随系统 |
| 配置键 | `animation_override`、`animation_brighten`、`animation_darken` |
| 依赖能力 | 由本项实际接口独立判断 |

## 分叉与收束

### 选择边界

原 Refactor 双时长对象完整时沿用 oem_animation；仅原生 Refactor 策略明确关闭、两时长槽为空且主屏双 RampAnimator / RampRateController / 原生原因记录完整时采用 traditional_ramp。读取原生自动亮度原因常量，仅普通慢速自动过渡可调；保留 HDR、用户、主屏、手动确认及下游时长上限。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `exclusive` |
| 统一输出 | 独立变亮/变暗倍率及原生过渡接口能力 |
| 坐标与身份 | 原生时长/速率只在适配内部处理；共用倍率为无量纲 |

### 清理及回滚

旧时长和传统速率均只恢复仍等于本次应用值的字段；原生限时裁剪和后来系统更新优先，目标与帧调度不改。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `oem_animation` · Refactor 原生时长过渡 | 调整已验证的原生过渡时长倍率，保持系统动画及主副屏隔离。 |
| `traditional_ramp` · 传统自动亮度速率过渡 | 原生 Refactor 策略关闭且时长对象为空；匹配主屏双 RampAnimator、三参或四参目标入口及速率原因，只调整普通自动亮度速率，保留系统时长上限及特殊过渡。 |

路线内部 ABI：`traditional_animation_entry`、`animation_strategy_selection`、`traditional_animation_reason`、`traditional_animation_target_signature`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`animation_override_supported`、`animation_compatibility_profile`。

验证入口：

- [refactor_advanced_hooks.py](../../tests/refactor_advanced_hooks.py)
- [refactor_traditional_animation.py](../../tests/refactor_traditional_animation.py)
- [refactor_traditional_animation_firmware.py](../../tests/refactor_traditional_animation_firmware.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [AdvancedTuning.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/AdvancedTuning.java) | `99787f6a9676e36d` |
| [HookRuntime.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/HookRuntime.java) | `818d117cff24ad7d` |
| [TraditionalAnimation.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/TraditionalAnimation.java) | `d58d63a8ada93c3e` |
