# 4.2.0 Beta 01：物理曲线标定端点兼容复审

2026-10-27。用户授权适配私有dd9327分析包并出Beta；4.2.0-beta01 / 42001 / beta-4.2.0-beta01，只本地打包，不发布、部署或安装。公开基线4.2.0 / 42000保持。

## 证据与原因

- liuqin / 23046RP50C的注入记录为curve_incompatible，Root与设置桥正常。旧CurvePlan校验拒绝原始节点，附件完整108点曲线末端1000，而本机三点物理标定上限550。
- services.jar的PhysicalMappingStrategy.computeSpline先经mAdjustedNitsToBrightnessSpline将配置目标换算为亮度坐标，再应用原生调整，随后经mBrightnessToAdjustedNitsSpline回到曲线输出坐标。LinearSpline与MonotoneCubicSpline的实际字节码均在端点外直接返回端点值；配置构造器及全部元数据字段与旧接口一致。
- 原包与124份展开文件按实际上报机型归档于私有diagnostics目录，SHA-256/CRC和Windows文件名映射已记录，未进入公开产物。

## 实现边界

- 新增PhysicalCurveCalibration适配器，范围内旧路线直接返回，不增加ABI要求；仅原始曲线真实越界时，要求已验证的标准物理映射、单调标定、坐标吻合的原生双向样条、未调整范围，以及纯转换端点一致。
- 原始TraditionalCurve先做完整校验，避免掩盖下降、NaN或坏节点。只对已证明原生裁剪的基线建立有效编辑坐标，min/max仍为真实标定，CurvePlan与保存文件校验不改。
- 100%/无亮度下限/未加载自定义基础曲线时保留原配置对象；正常编辑仍复制原元数据。有效蓝色基线在报告中按标定端点解释；停用恢复原始未裁剪配置，故障与外部基线替换沿用旧所有权和回滚检查。
- CompatibilityCatalog保留全部旧路线并追加aosp_physical_clamped；FeatureCompatibility只消费引擎证据，不重复探测，完整展示原曲线/标定范围。新类及ABI变体已登记。
- 主机专项152项；私有样本全108节点集成回放合计387项；实际固件35项核对；传统旧回归2377项与自检1519项通过。完整检查/编译、原签名与原资产、旧包保留及源码一致性以build/beta-4.2.0-beta01/final-audit.json为准。
- 无本轮Android真机/功耗验证，不承诺全部场景接口可用；不修改原生节点控制程序、采样率、保护或保存设置。用户后续添加的测试贡献/网络感谢名单按维护策略保留，不写入版本更新日志。
