# 独立高亮目标曲线：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 独立高亮目标曲线 · `outdoor_curve`

| 项目 | 要求 |
| --- | --- |
| 触发 | 本机物理nit映射与原生自动目标入口完整，用户明确操作后保存并同revision确认；沿用已有强光准入、会话、手动接管及系统保护。 |
| 需求与行为 | 与普通亮度曲线和学习保持分开，目标使用本机物理nit。新编辑固定四个lux/nit点，保留旧2—16点协议和旧OPR/节点路线；旧非四点存档确认简化才替换，刷新不改写；恢复默认经确认仅恢复当前方式曲线，保留准入/最高亮度限制等设置。页面使用既有设置页的18sp功能标题、标准卡片内外边距和控件尺寸；主页最上方保留独立激发测试卡片；高亮增强卡片内部依次为预设、增强状态、详细设置入口。手动模式阳光屏作为独立卡片留在主页，展开按钮沿用其他设置页的全宽48dp和14sp，不单独缩小。四排增强方式移入二级页顶部且不展开介绍，与曲线、触发门槛、时长冷却和最高亮度限制共用同一草稿和保存协议。二级页使用既有顶栏/页面过渡/返回/透明导航安全间距；返回不提交，旋转后按布局完成恢复位置，过期导航取消旧回调。搜索按目标打开所属页面并滚动。移除主页的浮动状态及误指向全局诊断的查看准入与保护条件入口，增强状态归入主页高亮增强卡片，不再单独放在二级页。增强状态使用带内边距的独立浅色容器，上方状态说明、下方三行两列参数：当前方式、当前亮度、最高亮度限制、系统高亮门槛、增强或冷却剩余及HBM预算估值。只读15秒内、非未来且引擎活动的运行快照，不引用未保存草稿；缺失、失效或类型错误显示未知，不把目标当实际输出；直接节点控制保留原读数边界。HBM预算仅显示原生快照估值，不伪造持续倒计时。详细设置入口使用浅蓝底和蓝字，整块点击反馈保留；最高亮度限制入口删除底部灰色说明。准入展开后为带内边距的名称/数值分栏和滑块，保持全部既有参数。曲线四种模式共用0至本机映射最大nit纵轴及相同照度坐标，不按当前光照、HBM进入状态、原生瞬时范围、HDR或快照新旧缩放/涂灰。只有常规增强将普通上限以上标灰，图例写模式未开放；开放高亮范围和放宽画面限亮不画该模式灰区；沿用系统时曲线可见只读且说明不参与调节。蓝线始终是所存目标，橙线为最高亮度限制，切换不自动裁写目标；实际输出仍由原生准入及保护决定。图高235dp与基础曲线编辑页一致，缩减图内边距和多余尾部，常显有色图例，保留小型恢复按钮。普通曲线蓝色基础/绿色学习含义、深浅色独立曲线不变。 本页四排方式、设置行、最高亮度限制整块区域及详细入口，各使用唯一外层点击与同尺寸涟漪容器；内部文字按钮只展示，不另绘制小涟漪。禁用时同步禁用外层点击和反馈。 |
| 退出 / 失败 | 关闭增强、手动/窗口/场景接管、屏幕/用户/配置变化、严重温控、省电、HBM预算不足或标定错误时沿用原生保护；不改普通曲线、原生学习或控制中心映射。 |
| 默认 | `off` · 旧配置默认沿用既有增强或上限/节点行为；明确选择增强方式、预设或编辑后使用目标曲线 |
| 配置键 | `outdoor_target_curve_enabled`、`outdoor_target_curve` |
| 依赖能力 | `outdoor`、`nit_limit` |

## 分叉与收束

### 选择边界

同主屏自动输出目标入口及本机物理nit双向映射完整；新曲线只在明确选择增强方式或编辑后启用，既有增强、OPR上限和节点路线继续保留。接口缺失只停用新目标，不伪装nit，不按型号放行。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `per_feature` |
| 统一输出 | 原生框架目标在本机标定边界转为物理nit，目标曲线计算后反向映射并经过当前允许范围及全部后续保护。 |
| 坐标与身份 | lux与设备标定物理nit，区别于普通逻辑nit、窗口比例及原始节点；图上目标不冒充实测输出。 |

### 清理及回滚

强光会话准入、退出、单次时长、冷却、控制权和原生保护仍由已有控制器负责；目标在原生目标后、后续保护前接入，可调亮或压低高亮目标，不改普通曲线与学习。旧OPR/节点改用曲线需确认且保留数值；不因查看、缺能力或读取失败清保存设置。手势绑定映射、revision、物理屏、模式及草稿所有权；取消只恢复仍拥有的数据。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `physical_outdoor_target` · 独立高亮物理目标曲线 | 本机物理nit映射和自动目标入口完整；强光会话内按曲线设置目标，保留范围、画面、温控、电量及HBM限制。 |

运行证据：`outdoor.target_curve_supported`、`outdoor.options.outdoor_target_curve_enabled`、`outdoor.options.outdoor_target_curve`、`outdoor.preview_mapping`、`outdoor.target_adjustments`、`outdoor.limit_trace`。

验证入口：

- [refactor_outdoor_target.py](../../tests/refactor_outdoor_target.py)
- [refactor_outdoor.py](../../tests/refactor_outdoor.py)
- [refactor_opr_curve_ui.py](../../tests/refactor_opr_curve_ui.py)
- [refactor_configuration.py](../../tests/refactor_configuration.py)
- [refactor_outdoor_light_test.py](../../tests/refactor_outdoor_light_test.py)
- [refactor_outdoor_details.py](../../tests/refactor_outdoor_details.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [NitBrightnessLimit.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/NitBrightnessLimit.java) | `891702d7d41da6d6` |
| [OutdoorController.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorController.java) | `04002b346d81ba8d` |
| [OutdoorCurvePreview.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorCurvePreview.java) | `c764bc12c6488d8f` |
| [OutdoorDetailsPage.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorDetailsPage.java) | `3e4ce57a4d14ca47` |
| [OutdoorOprCurveSettings.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorOprCurveSettings.java) | `695a9d340e0bac0b` |
| [OutdoorSettings.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorSettings.java) | `8d26ce76f27d7383` |
| [OutdoorTargetCurve.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorTargetCurve.java) | `25d282852b9e4e40` |
