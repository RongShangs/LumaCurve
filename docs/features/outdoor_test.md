# 短时强光输入模拟：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 主动强光激发测试 · `outdoor_test`

| 项目 | 要求 |
| --- | --- |
| 触发 | 明确点击测试并确认；同用户、同物理主屏、同显示Handler，自动亮度已就绪且解锁，无HDR、特殊窗口、独立亮度或节点接管。逐项核验ABC字段类型、真实输入通知、计算方法及输出证据，缺接口只停用测试；沿用系统模式也可测试，不自动打开户外增强。 |
| 需求与行为 | 最长20秒：仅在原生updateAutoBrightness计算调用内用FieldOverride临时替换照度为100000lux，finally按仍持有值恢复；原生setAmbientLux及阈值、样本缓冲和学习始终使用真实照度，不依赖可被内联的私有重置Hook保证安全。只向原生范围/场景消费者通知模拟照度，并让模块户外策略使用相同测试输入，保留原生目标、OPR、温控、电量及HBM预算。逐帧检查计算输出证据，原生输出变化而未经过测试计算时结束；不循环覆盖输出。仅记录实际display_power_state亮度及可靠device_backlight_mapping换算nit，缺标定只显示系统比例，不把目标或节点值称为实测峰值。默认无测试Hook，显式开始才事务安装。；范围/策略照度通知允许已验证的非public或继承声明，按精确签名查找；探测异常直接作为具体自检原因，不能把接口匹配失败笼统吞成等待。 页面最上方独立卡片，显示100000lux模拟照度、剩余时长、当前实际亮度和本次/上次最高；运行中更新剩余时长，结束说明原因。当前值仅使用15秒内、时间戳完整且不在未来的可靠设备nit映射或display_power_state比例；直接节点接管、缺换算、读取过期不能显示目标或旧框架值，保留未知。最高值使用服务端实际采集结果，不从目标或曲线推算；已有定期刷新复用，不新增采样、轮询或Hook。 界面统一18sp标题与标准卡片；四项参数有浅色底，两列显示；参数固定两行四格：模拟照度、剩余时长、当前亮度、本次或上次最高。真实照度不在界面显示；运行中只更新原时长格，不新增行。未开始显示可用20秒，运行中按快照余量显示，结束显示0秒。开始/停止按钮位于参数下方，使用标准全宽48dp；状态和结束原因放在标题旁，去掉底部常驻灰字说明。测试后端、确认、20秒期限、原生保护和现有刷新保持不变。 进入详细设置视为离开测试界面，按原会话停止测试并保留已有采集结果；配置确认、20秒期限和真实输入保护不变。 |
| 退出 / 失败 | 停止、20秒、离页/后台、锁屏、手动调光、用户/屏幕/控制权/配置变化、原生异常或计算路径变化时结束；先关闭计算字段作用域，恢复原户外策略绝对时间及真实照度的范围/场景通知，再按当前真实照度原生重算；不覆盖系统后写值、不重置HBM预算。移除临时Hook，失败句柄保留且最多250/1000ms两次重试。Root请求沿用进程/构建/时间/令牌校验，停止须匹配会话，不增加服务或采样循环。 |
| 默认 | `off` · 仅用户在已应用设置后确认启动；不保存测试状态，不开机重放 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

## 分叉与收束

### 选择边界

核验当前ABC实例、物理主屏和同显示Handler；mAmbientLux/mRawScreenAutoBrightness为非静态可写float，原生计算、真实照度通知、双源入口及手动退出接口类型完整。缺失只停用测试，不影响旧户外功能；不按型号或系统版本开启。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `exclusive` |
| 统一输出 | 原生计算作用域照度租约、真实输入与输出路径证据 |
| 坐标与身份 | 计算内临时lux，计算外真实lux；结果只来自实际显示比例和本机物理nit换算，不当仪器实测 |

### 清理及回滚

默认无Hook；显式开始事务安装临时Hook，照度仅在计算作用域内修改并按仍持有值恢复。真实setAmbientLux/阈值/样本/学习保持原生，避免内联重置风险。20秒或保护接管结束时通知当前真实照度并重算，不覆盖后写值，不重置HBM预算；残留句柄有界重试。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `abc_native_lux_lease` · 短时原生强光输入 | 核验主屏 ABC 的照度、重算、短期模型保护及手动退出接口；显式启动20秒模拟，不改驱动采样或保存设置。 |

路线内部 ABI：`outdoor_test_notification_visibility`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`outdoor_light_test.profile`、`outdoor_light_test.active`、`outdoor_light_test.reason`、`outdoor_light_test.recorded_peak_nit`、`outdoor_light_test.error`。

验证入口：

- [refactor_outdoor_light_test.py](../../tests/refactor_outdoor_light_test.py)
- [refactor_outdoor_light_test_firmware.py](../../tests/refactor_outdoor_light_test_firmware.py)
- [refactor_opr_curve_ui.py](../../tests/refactor_opr_curve_ui.py)
- [refactor_brightness_control.py](../../tests/refactor_brightness_control.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [OutdoorLightTest.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorLightTest.java) | `986e32861cf2455f` |
| [OutdoorLightTestAccess.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/OutdoorLightTestAccess.java) | `343564c5dd17c6c1` |
