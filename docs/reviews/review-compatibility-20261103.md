# 全项目兼容契约及文档复审

日期：2026-11-03；公开基线4.4.2，本地目标4.4.2 Beta04 /44204。本文只记录本次发现、修复与证据，不作为长期需求源。

## 结论与范围

按24组适配核对58项运行功能的选路、收束、所有权、坐标与旧路线保护；另整理10项界面需求。未删除既有路线，原配置/存档键和已保存值不迁移为默认。重点深审近期比例曲线、原生学习、Hook装配、输出读数及无HBM普通范围分支；其余组按实际边界、既有实现和完整回归核对。

当前约束保护129个功能/路线组合；80个登记测试入口必须连接总构建。数据登记、主机或固件核对不等于所有机型真机通过。

## 本次修正

| 发现 | 影响 | 处理及验证 |
| --- | --- | --- |
| 启动入口用具体适配器类型决定配置Hook | 新增后端可能再次向公共装配层扩散分支 | 后端通过统一installMappingHooks装配原安装器；旧物理/Simple回调及安装失败清理回归 |
| 边界检查只保护最早的后端类型/身份 | Simple等新增后端可绕过维护检查进入管理层 | 从实际子类与路线推导约束，补类判断/强转、UI getString及启动绕过负向 |
| 学习容量依赖存档回放方法 | 原生能学习但缺回放接口时误报整个手动记忆不可用 | 容量/观察与持久化回放独立；缺方法样本仍可读取真实手动点，回放继续拒绝 |
| 可选nit/遥测类型失败可能中断状态或坐标准备 | 无关功能被可选观测拖累 | 在可选边界隔离LinkageError；实际缺类和初始化错误注入；主标定坏数据仍拒绝 |
| 根入口不断追加版本状态，含冲突旧授权和过时数字 | 长期要求难查，交接可能误用旧状态 | 规则、项目设计、范围、状态、版本日志拆开；原入口、源码README及整理前兼容说明原字节归档 |
| 需求和兼容说明缺统一追溯 | 只改代码或日志后文档易漂移 | 需求JSON覆盖运行/界面功能，生成按功能文档；源码指纹、配置键和覆盖漂移纳入检查 |

## 统一契约检查

- 活动对象选定一个曲线后端；锚点、系数、物理映射和比例映射保留其实际单位、容量及原生载荷。CurveBackend/curve_contract/CurveCompatibility在绘图、配置、保存与记忆管理前收束。
- Hook装配、实时读取、学习容量、完整转存和存档回放各有能力语义；未知能力默认不承诺。Simple临时用户作用域只在同步提交事务内有效。
- HBM控制器/预算与无数据普通范围分别表达；保留原生范围、HDR、温控、电量、场景和控制权。Beta03普通范围增强按用户最新要求保留，不把它写成HBM附加项可用。
- 输出读数采用真实nit/节点/普通框架实际比例，不能以请求或计算目标替代；raw不能复用旧框架比例。原生节点绑定、配置代次和物理屏必须一致。
- Root权限与注入/接口独立；仅运行裁剪副本暂停缺能力，原保存设置保留。开机恢复共用代次与实际回执，不回放手势或旧亮度。

## 分组追溯

完整需求、选路和契约仅维护于对应功能页面；本表不复制其全文。

| 组 | 复审入口 | 路线数 |
| --- | --- | --- |
| `curve` | [曲线与手动记忆](../features/curve.md) | 6 |
| `main_input` | [主光感](../features/main_input.md) | 3 |
| `assist_input` | [辅助光感](../features/assist_input.md) | 2 |
| `response` | [主辅确认与暗光策略](../features/response.md) | 2 |
| `animation` | [原生亮度过渡](../features/animation.md) | 2 |
| `sunlight` | [手动阳光屏](../features/sunlight.md) | 1 |
| `touch` | [触摸遮挡释放](../features/touch.md) | 1 |
| `scenes` | [原生场景参与](../features/scenes.md) | 9 |
| `dark_lock` | [暗光锁定](../features/dark_lock.md) | 1 |
| `applications` | [暂停自动亮度名单](../features/applications.md) | 2 |
| `hbm` | [HBM 控制器与计时](../features/hbm.md) | 4 |
| `outdoor` | [户外增强目标](../features/outdoor.md) | 3 |
| `range` | [高亮范围](../features/range.md) | 2 |
| `sdr` | [灰阶放宽与物理 nit 限制](../features/sdr.md) | 2 |
| `thermal` | [显示层温控](../features/thermal.md) | 1 |
| `raw_output` | [主屏输出隔离](../features/raw_output.md) | 2 |
| `node` | [真实主屏节点](../features/node.md) | 3 |
| `root_tools` | [Root 存储与原生工具](../features/root_tools.md) | 11 |
| `app_tools` | [APP 本地工具](../features/app_tools.md) | 3 |
| `control_center` | [控制中心节点映射](../features/control_center.md) | 6 |
| `dark_lock_schedule` | [日落后暗光锁定](../features/dark_lock_schedule.md) | 1 |
| `dim_aod` | [熄屏前降亮与息屏显示协调](../features/dim_aod.md) | 1 |
| `app_brightness_isolation` | [应用临时亮度隔离](../features/app_brightness_isolation.md) | 2 |
| `mapping_startup` | [控制中心开机恢复](../features/mapping_startup.md) | 2 |

## 文档权威关系

全局规则：GLOBAL_RULES.md；稳定设计：PROJECT.md；功能需求：docs/requirements/feature-requirements.json；原生路线/ABI：CompatibilityCatalog和兼容清单；自动呈现：docs/features及兼容导航索引；状态：docs/work-status.md；版本变化：docs/releases。

功能页列当前相关源码指纹、实现与测试；普通检查拒绝漂移，系统场景默认说明直接引用生产常量。规范不再记录版本授权，日志不承担唯一长期要求。旧快照位于docs/archive/project-documents-20261103，哈希和原文件保存，旧相对链接可能只适用于原位置。

兼容清单也移除逐版拼接的重复叙述：选路、控制权清理和具体ABI分开呈现，证据键去重。修正“主题切换一律清除记忆”的旧说明，现按清除、共用或独立策略交接；移除映射入口仍隐藏的过时说法。逐项退出条件按功能描述，不重复大段通用失败模板；全局失败要求统一见GLOBAL_RULES。

## 尚未消除的组织债务

HookRuntime仍有历史原生状态采集，SceneController仍读取已注册场景事实，CurveBackend.get(name)还承担规范化状态门面；本轮没有把所有历史反射机械搬空。适配器间也存在已登记的共用反射/安装工具。新差异必须先进入对应边界，后续拆分以真实语义和旧负向回归为前提，不为目录美观重写成熟Hook。

自动文档检查保证结构、配置键和源码追溯，不能自动证明需求文字的全部语义。实际安装、系统启动、强光效果、物理测光和功耗依然需要相应设备证据；本轮无Android安装或功耗测量。

## 交付证据

完整检查、归档字节、旧474份APK/ZIP、原签名/raw10、保存设置和最终源码包对应核验见私有build/beta-4.4.2-beta04/。没有发布、网站版本切换或手机安装。官方版本仍4.4.2 /44200。
