# 4.4.3 Beta 02 · 自动亮度模式与运行状态复审

## 证据和故障

小米12S Pro用户包记录unicorn/2206122SC、HyperOS3.0.4.0、Android15，模块4.4.3。LSPosed日志只有一次physical_mapping连接，但持续记录状态发布缺mUseAutoBrightness；ACK与注入记录存在，完整状态与日志为空。APP每次轮询未取得状态，重复显示同一连接Toast。

实际固件的OEM mAutoBrightnessEnable只是用户自动亮度设置缓存，不能替代有效使用状态。DPC从真实AutomaticBrightnessStrategy2.shouldUseAutoBrightness取值并传给原生SDR处理，具备可验证的主屏和ABC归属。

## 修复边界

- AutomaticModeAccess优先保留owner上的boolean状态；仅字段明确不存在时选择原生策略接口，校验字段静态性、屏幕、方法签名及DPC/策略/ABC对象归属。优先字段损坏或原方法调用失败不回退，不用系统设置或最后输出原因冒充有效模式。
- 物理与Simple适配器统一输出原get契约，其余管理器不新增机型、ROM或系统版本判断。Refactor原有字段读取保持不变。
- 模式发布单独捕获异常并保留明确错误；不伪造false，不丢失曲线、日志和其他诊断。缺模式时界面显示待确认，依赖其已知状态的功能继续按原保护拒绝操作。
- renderDisconnected将未完成接入保持为页内状态，自动刷新不弹Toast。真实曲线不兼容、缺注入和旧Hook提示仍走原有去重入口。
- 无配置迁移或默认值修改，无新采样/进程/节点程序；累计保留Beta01 K60上下文路径和全部旧路线。

## 核验

tests/refactor_automatic_mode.py运行实际模式边界、生产物理适配器及实际未连接渲染方法，覆盖新旧模式、原生有效使用与设置值区别、优先字段异常不回退、类型/静态签名、对象更换、状态部分失败以及连续100次刷新不弹Toast。

tests/refactor_automatic_mode_firmware.py静态核对所附实际固件中的有效模式消费者、所有权及配置缓存差别。私有固件、日志和反汇编不分发；这不是Android/ART或该设备实测。

完整检查、原签名、源码一致性、历史交付保护和资产未改核验保存在build/beta-4.4.3-beta02。仅本地交付，未授权发布、安装或重启设备；该用户安装后的结果仍待确认。
