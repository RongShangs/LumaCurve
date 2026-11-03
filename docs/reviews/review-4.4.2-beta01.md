# SimpleMappingStrategy 曲线兼容审阅

## 问题与样本

用户确认：**Redmi K50 电竞版 /21121210C /ingres，笙歌醉梦红尘制作的 HyperOS 4.0.5.0 移植包**。公开 4.4.2 已进入 system_server，但读取 `mNits` 失败。与其他用户的内核/SELinux 注入失败问题不同。

私有固件及运行转储证明当前活动对象是 `BrightnessMappingStrategy$SimpleMappingStrategy`，使用照度和框架亮度数组；`convertToNits` 返回无效值，物理 nit 表缺失。不能改字段名后按物理映射处理，更不能使用配置中的 donor nit 数组冒充屏幕标定。

## 路线契约

| 项 | 实现 |
| --- | --- |
| 功能 / 路线 | 曲线与手动记忆组；追加 `simple_mapping`，保留所有旧路线 |
| 边界 | `CurveBackendFactory` 按活动对象选择 `SimpleCurveAdapter`，失败不另选物理后端 |
| 必需 ABI | 实际 Simple 类；非静态 float[] mLux/mBrightness、float 用户/调整字段、普通 getMode；computeSpline、getAdjustedCurve、getUnadjustedBrightness、学习/清除方法及 native Spline 完整 |
| 对象与线程 | 主屏 0、普通模式 0、ABC 默认映射对象和 owner 对象一致；回调只在显示 Handler、匹配运行实例与用户时参与 |
| 坐标 | `framework_brightness` / `brightness_spline`；0—1，不作为 nit；图表仍按实际普通手动上限显示百分比 |
| 学习与转存 | 原数组和值不变；重建参数和未调整亮度查询共用完整基线，原生 gamma / 插点 / 平滑继续执行；转存读取实际 Linear/MonotoneCubic 样条节点，恢复全部节点 |
| 默认与升级 | 中性配置不重建、不清原生记忆；已有键、身份隔离和原值保留，无升级重置 |
| 保护 | 不接管传感器、背光或最终输出；主屏/用户、HDR、场景、温控、电量及 HBM 仍由原控制层处理；nit 专用能力独立失败 |
| 安装 | 与共享映射观察 Hook 同组安装，任一步失败逆序撤销，不发布半套能力；缓存以实际 Class 为键 |
| 回滚 | 配置和记忆操作快照样条、用户字段及短期模型；同 Handler 失败回滚；基准被其他所有者替换时撤销自己的基线，不覆盖新数组 |
| 运行成本 | 只随原生重建、用户调整和原有状态发布工作，无新进程、线程、传感器监听或定时器 |

## 验证

- 主机执行生产工厂、适配器及共享/新增 Hook：中性保持、编辑、残留调整清除、原生学习、强度控制、完整新增节点转存、恢复、外部基准变化、非主屏/模式/用户/线程、安装失败及恢复失败回滚。
- 原 Xiaomi/AOSP 物理曲线回归继续运行；曲线契约、图表标尺、自检路线及英文说明同时验证。
- 私有固件核对真实构造与消费链路、Simple 比例坐标、原生插点和平滑、ABC 模型及样条布局，并回放样本 108 个节点。
- 完整编译、维护契约、全部登记测试、签名与旧产物保护结果见 `build/beta-4.4.2-beta01/`。原始包与反汇编仅存私有 diagnostics，不进入 APK、Git 或公开源码包。
- Android/ART 真机、长测、功耗：**未测**。本地 Beta 包只能表示实现和静态/主机验证完成，不能写成用户设备已验证成功。
