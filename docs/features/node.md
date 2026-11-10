# 真实主屏节点：需求与兼容契约

> 自动生成；更新需求JSON、兼容清单及代码后，运行 `python tools/verify_compatibility.py --write-doc`。本页无版本更新日志。

## 功能需求

### 主屏节点发现及读数 · `main_panel_node`

| 项目 | 要求 |
| --- | --- |
| 触发 | Root只读检查或状态注释需要当前物理屏的节点与真实上限。 |
| 需求与行为 | 实际默认内部屏与backlight/明确显示led或折叠端口唯一绑定；读真实节点与上限；缺nit显示节点，普通模式可退到实际系统比例 |
| 退出 / 失败 | 切屏或身份不一致时丢弃旧节点/nit读数；只读发现不启动写入或改权限。 |
| 默认 | `observe` · 只读发现不试写 |
| 配置键 | 无独立持久化亮度配置键 |
| 依赖能力 | 由本项实际接口独立判断 |

## 分叉与收束

### 选择边界

backlight 或明确标识显示的 leds；真实 sysfs 路径与上限配对且唯一主屏，不按机型写死上限。 新增手动面板折叠路线：实际原生布局至少两个不同默认物理屏；实时默认内部屏地址与布局、成对同控制器背光、唯一 DSI 亮屏端口一致，选当前端口，扫描后再确认物理屏未变化。无切换布局证据时原发现路径不变；有折叠证据但不完整则等待。 兼容实际 Physical / StablePhysical 地址对象，均直接调用原生 getPort；StablePhysical 的物理 ID 与端口独立，按内核成对 panelN 清单验证，不解码该 ID。 状态读数单独只读实际节点；可验证本机DisplayDeviceConfig背光标定时换算nit，不可换算时原生接管仅显示节点值，不复用框架旧亮度。 无nit但有已验证节点时，常规模式也展示真实节点；无节点时可显示DisplayPowerState实际输出比例，直接节点接管不复用该比例。 保存选择的折叠配对复用由ActivePanelNode收束：当前节点须在实时验证的候选中，保存路径也在该集合且所有上限相同；普通单屏仍精确匹配。

| 契约 | 内容 |
| --- | --- |
| 组合方式 | `exclusive` |
| 统一输出 | 已绑定当前物理屏的节点、实际max、读回及换算来源 |
| 坐标与身份 | 节点整数/physical nit分别标记；拒绝跨屏与失效绑定；display_power_state比例仅为系统输出，不冒充物理nit或请求值。 |

### 清理及回滚

发现与权限检查只读，不启动写入器；实际租约写入另由原生事务负责，拒绝副屏 / 指示灯 / 手电。 折叠专用原生选项与 v3 权限恢复记录仅允许绑定端口节点；v2 / 旧节点拒绝规则不变。关闭、熄屏、DRM 改变结束旧接管；租约和框架输出隔离绑定物理屏，晚到命令拒绝，不复用旧目标。户外新增显式折叠协作分支，使用独立物理租约和原生 DSI 核验；控制中心动态折叠监听仍须独立核验，不能复用户外通过结论。 读数不启动写入器、不修改节点或权限；物理屏或标定不一致时不显示旧nit。 启动安装前后比较物理屏、折叠端口、路径及上限，任何改变均拒绝启动。

## 已实现的兼容路线

| 路线 | 条件 |
| --- | --- |
| `backlight_node` · 主屏 backlight 节点 | 扫描 backlight，验证主屏身份、真实路径及 brightness / max_brightness 配对。 |
| `display_led_node` · 显示专用 leds 节点 | 仅接受明确标识为主屏背光的 leds 节点，排除通知灯、手电与背屏。 |
| `fold_default_display` · 折叠屏当前主屏节点 | 原生折叠布局允许不同物理屏作为默认屏；根据实时默认屏地址、成对背光与唯一点亮 DSI 选择节点；显示端口与内核编号不一致时独立核验默认屏点亮状态，切屏释放旧接管。 |

路线内部 ABI：`native_payload_refresh`、`fold_physical_address`、`fold_kernel_binding`、`screen_brightness_readback`、`optional_output_type_failure`；完整条件仅维护在 [ABI索引](../architecture/compatibility-index.md#abi-内部变体)。

运行证据：`main_panel_node.node_class`、`main_panel_node.node_path`、`main_panel_node.node_maximum`、`main_panel_node.node_discovery`、`main_panel_node.node_physical_display_id`、`main_panel_node.node_address_profile`、`main_panel_node.node_port_source`、`main_panel_node.node_detail`、`actual_nit_source`、`actual_node_source`、`screen_nit_calibration.route`、`brightness_read_error`、`actual_brightness`、`actual_brightness_source`、`native-storage.json`、`NATIVE_START_FAIL.private_stage`、`NATIVE_START_FAIL.uid/euid`。

验证入口：

- [refactor_panel_nodes.py](../../tests/refactor_panel_nodes.py)
- [refactor_panel_access.py](../../tests/refactor_panel_access.py)
- [refactor_fold_panel.py](../../tests/refactor_fold_panel.py)
- [refactor_screen_brightness.py](../../tests/refactor_screen_brightness.py)
- [refactor_native_storage.py](../../tests/refactor_native_storage.py)

## 实现追溯

| 文件 | 当前源码 SHA-256 前16位 |
| --- | --- |
| [ActivePanelNode.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ActivePanelNode.java) | `ea6abe78d6c2a152` |
| [BrightnessControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/BrightnessControl.java) | `f957288314b2835a` |
| [ControlCenterRoot.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ControlCenterRoot.java) | `49074e9977d153a4` |
| [NativePanelRoot.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/NativePanelRoot.java) | `90df6884aec4f7ad` |
| [PanelNodeDiscovery.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/PanelNodeDiscovery.java) | `85b38c1512206283` |
| [RootControl.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/RootControl.java) | `6cf40b35ec4446c5` |
| [ScreenBrightnessReading.java](../../experimental/refactor_hook/java/top/rongshangs/lumacurve/refactor/ScreenBrightnessReading.java) | `d907847857002944` |
