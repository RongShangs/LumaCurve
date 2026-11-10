# 机型与系统版本

本表仅记录已有明确反馈、确认适配的设备及对应系统版本。未列入表中不代表不适配，更多时候可能是用户使用正常，因此没有反馈。

按真实接口分别检测功能，不以同型号、相同系统版本或移植供体保证全部支持。

## 小米

| 机型 | 系统版本 |
| --- | --- |
| 小米17 | HyperOS 3、HyperOS 4 |
| 小米17 Pro | HyperOS 4.0.0.41.XBLCNXM、HyperOS 4.0.2.0.XBLCNXM、HyperOS 4.0.5.0.XBLCNXM、HyperOS 3.0.306.0.WBLCNXM |
| 小米17 Pro Max | HyperOS 3、HyperOS 4 |
| 小米17 Ultra | HyperOS 4.0.0.10.XPACNXM |
| 小米13 | HyperOS 4.0.0.26.XPCCNXM、HyperOS 3.0 |
| 小米14 | HyperOS 4.0.25.0.XEOCNXM（追梦官改，移植自小米18 Pro Max；已确认正常） |
| 小米12S Pro | HyperOS 3.0.4.0.VLECNXM |
| 小米18 Fold（用户确认，lhasa／2608BPX34C） | HyperOS 4.0、HyperOS 4.0.21.0.XPNCNXM、HyperOS 4.0.26.0.XPNCNXM（既有曲线已接入；4.0.26.0折叠完整/分区节点映射本地Beta适配，待新包设备验证） |
| 小米 MIX Fold 3 | HyperOS 3.0.304.0.WMVCNXM |
| 小米平板6 Pro | HyperOS 3.0.306.3.WMYCNXM |

## 红米

| 机型 | 系统版本 |
| --- | --- |
| Redmi K100 Pro Max | HyperOS 4.0.0.8.XGNCNXM |
| Redmi K90 | HyperOS 4.0 |
| Redmi K90 Pro Max | HyperOS 4.0.0.33.XPMCNXM、HyperOS 4.0.0.25.XPMCNXM、HyperOS 4.0.2.0.XPMCNXM、HyperOS 4.0.5.0.XPMCNXM、HyperOS 4.0.6.0.XPMCNXM |
| Redmi K90 至尊版 | HyperOS 4.0.0.9.XHPCNXM |
| Redmi K80 Pro | HyperOS 3.0.305.0.WOMCNXM |
| Redmi K80 至尊版 | HyperOS 4.0.0.8.XONCNXM、HyperOS 4.0.0.8.XONCNXM（莫离然然定制版） |
| Redmi K70 | HyperOS 4.0.0.12.XNKCNXM |
| Redmi K60 | HyperOS 1.0.14.0.UMNCNXM |
| Redmi K50 电竞版 | HyperOS 4.0.5.0.XPCCNXM（笙歌醉梦红尘移植版） |
| Redmi Turbo 4 Pro | HyperOS 4.0.25.0.XEOCNXM（追梦移植版） |

## 第三方设备

| 机型 | 系统版本 |
| --- | --- |
| 努比亚 Z90 Ultra | HyperOS（骨灰移植版，版本未记录） |
| 努比亚 Z80 Ultra | HyperOS 3.0.307.6.WPACNXM（骨灰移植版） |
| 努比亚 Z70 Ultra | HyperOS 4.0（移植版） |
| 红魔10 Pro | HyperOS 4.0.0.10.XPACNXM（移植版） |

## 待回访记录

| 设备标识 | 系统版本 | 当前边界 |
| --- | --- | --- |
| pandora／25098PN5AC（暂按分析包标识记录） | HyperOS 4.0.6.0.XBLCNXM | Beta06应用管理闪退已定位；Beta08本地修复窗口初始化、移除SystemUI启动线程同步注册等待，并延迟闲置独立亮度的Hook安装；Beta10回访确认接口、名单和组记忆可用，但原生手动交接覆盖首次目标，Beta11本地修复并调整迁移按钮布局；实际应用亮度、开机及节点恢复仍待新包回访；SystemUI多次启动超时的根因尚未确认。 |
