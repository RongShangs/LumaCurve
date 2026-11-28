# 灰阶放宽与物理 nit 限制：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 强光下放宽画面限亮 · `opr`

| 项目 | 要求 |
| --- | --- |
| 触发 | 户外目标、范围和画面放宽开关生效，处于持续强光、普通自动SDR且保护允许。 |
| 需求与行为 | 仅强光、自动SDR、户外范围已开放且保护允许时放宽已验证OPR路径；安全包络、温控、HDR与隐私保护保留；OPR 入口优先 (brightness, reason)，完整两参缺失才用旧三参 |
| 退出 / 失败 | 每次调用结束恢复ThreadLocal帧，只移除模块引入的原因位，系统保护继续执行。 |
| 默认 | `off` · 新配置关闭 |
| 配置键 | `outdoor_opr_relax` |
| 依赖能力 | `outdoor`、`outdoor_range` |

### 户外nit上限 · `nit_limit`

| 项目 | 要求 |
| --- | --- |
| 触发 | 户外会话生效，上限非0，设备有可靠nit与背光换算。 |
| 需求与行为 | 仅有可靠设备标定才换算；不能外推或套用捐赠机型数据，不能支持时独立报告；范围取值随已验证 HBM 数据身份变体 |
| 退出 / 失败 | 无可靠标定时独立拒绝nit限制；取消上限不改变其他户外选项或设备映射。 |
| 默认 | `native` · 0表示不额外限制 |
| 配置键 | `outdoor_max_nit` |
| 依赖能力 | `outdoor` |

### 放宽后高亮曲线 · `opr_curve`

| 项目 | 要求 |
| --- | --- |
| 触发 | 用户在高亮控制单选中显式选择自定义曲线，保存2至16个照度—物理nit节点，户外/范围/画面放宽生效且本机有可靠nit映射；当前同用户主屏自动SDR帧实际执行了已验证OPR消费者。与强制节点高亮互斥，开启新控制先确认后关闭旧控制。 |
| 需求与行为 | 独立编辑放宽后高亮上限包络，按照度线性插值、端点外保持端点值；节点照度递增、nit非递减。在完整原生SDR返回后仅降低高于普通上限的输出，避免OPR后画面修正与目标合并抬回；不抬高任何既有温控/电量/峰值等较低结果，不重写原生照度曲线、不制造面板标定。普通上限以下节点按本机普通上限处理，静态户外nit上限仍可进一步限亮。；统一nit图内拖动曲线点，纵向调整联动前后点以维持非递减；添加为显式按钮，在最大照度间隔插入插值点，空白触摸不误加点。统一nit上限以橙色水平线展示和拖动，松手提交草稿；与节点控制冲突先确认，取消保持旧控制。旧2—16点完整读取，取消/配置变化/离开释放手势。 |
| 退出 / 失败 | 关闭或接口失效沿用原画面放宽结果，默认旧配置关闭；HDR、隐私显示、手动/窗口接管、熄屏、换用户/线程和无真实OPR调用的帧不应用。ThreadLocal和旧配置帧退出清理；能力丢失只暂停运行副本，存档和节点保留，预设不清空节点。 |
| 默认 | `off` · 默认关闭、节点为空，不改变旧画面放宽路径 |
| 配置键 | `outdoor_opr_curve_enabled`、`outdoor_opr_curve` |
| 依赖能力 | `opr`、`nit_limit` |

## 分叉与收束

### 选择边界

完整隐私判断优先；完全没有隐私 / UHBM 残片且旧 OPR 完整才用传统路径。实际物理换算用于 nit 上限。调用失败不降级。 OPR额外放宽独立读取完整原生灰阶/nit包络，最多到该表最高档；不存在整组字段时保留旧映射路线，部分缺失/坏表/换算失败保留原生限亮。 OPR 入口优先 (brightness, reason)，仅完整两参缺失才用旧三参；SDR 入口四参优先、五参回退。 高亮曲线单独要求实际OPR自动SDR帧及可靠物理nit转换，复用已核验两/三参OPR和四/五参SDR接口，不增加机型白名单。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `exclusive` |
| 统一输出 | OutdoorSdrScope统一限定范围、优先级、保护及撤回 |
| 坐标与身份 | nit/brightness转换仅在适配器；HDR/系统保护不被通用增强覆盖 |

### 清理及回滚

ThreadLocal 帧按调用恢复；只移除本次引入的 OPR 原因位；温控、电量、HDR 与系统接管继续优先。 放宽后曲线在完整SDR原生调用返回后只缩小高亮输出，保持普通上限和原生较低安全结果；无OPR消费者、旧配置帧、HDR/隐私/手动/换用户不应用。默认关闭，能力消失只裁剪运行开关，导入与预设保留节点；Root工具须带OutdoorOptions内嵌曲线类型。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `privacy_aware` · 带隐私显示判断的新式 SDR 路径 | 匹配 shouldUsePrivacyOprBrightness，保留隐私显示、HDR、温控与电量判断；额外放宽遵守完整原生OPR表的最高档。 |
| `legacy_opr` · 传统 OPR / SDR 路径 | 不存在隐私显示分支且旧版灰阶 / nit 接口完整；不将残缺的新式接口误当旧接口。 |

路线内部 ABI：`opr_relaxation_envelope`、`opr_signature`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`outdoor.sdr_compatibility_profile`、`outdoor.sdr_compatibility_detail`、`outdoor.opr_envelope_profile`、`outdoor.opr_envelope_max_nit`、`outdoor.opr_envelope_max_brightness`、`outdoor.opr_envelope_detail`、`outdoor.opr_curve_enabled`、`outdoor.opr_curve_nodes`、`outdoor.opr_curve_limits`、`outdoor.opr_curve_error`、`outdoor.opr_curve_requested_nit`、`outdoor.opr_curve_delivered`、`outdoor.mapping_normal_nit`、`outdoor.preview_mapping`、`outdoor.preview_detail`。

验证入口：

- [refactor_outdoor_compatibility.py](../../tests/refactor_outdoor_compatibility.py)
- [refactor_outdoor_compatibility_firmware.py](../../tests/refactor_outdoor_compatibility_firmware.py)
- [refactor_opr_envelope.py](../../tests/refactor_opr_envelope.py)
- [refactor_opr_envelope_firmware.py](../../tests/refactor_opr_envelope_firmware.py)
- [refactor_opr_curve_ui.py](../../tests/refactor_opr_curve_ui.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [NitBrightnessLimit.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/NitBrightnessLimit.java) | `891702d7d41da6d6` |
| [OutdoorController.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorController.java) | `6cb4592f934055d4` |
| [OutdoorCurvePreview.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorCurvePreview.java) | `c764bc12c6488d8f` |
| [OutdoorOpr.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorOpr.java) | `4a83d2359d1de02f` |
| [OutdoorOprCurveSettings.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorOprCurveSettings.java) | `695a9d340e0bac0b` |
| [OutdoorOprEnvelope.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorOprEnvelope.java) | `9653d6d8496b4be3` |
| [OutdoorSdrScope.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorSdrScope.java) | `a207dd5f0fb872a2` |
