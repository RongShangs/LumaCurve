# 项目文档导航

每类信息只有一个维护位置，其他文档引用，不复制整段要求或版本状态。

| 需要了解 | 权威入口 | 维护方式 |
| --- | --- | --- |
| 必须长期遵守的规则 | [全局规则](../GLOBAL_RULES.md) | 手工维护长期约束，不写版本日志 |
| 项目需求与技术要点 | [项目设计](../PROJECT.md) | 只随需求/设计变化更新 |
| 哪些工程属于当前主线 | [任务边界](../PROJECT_SCOPE.md) | 不追加阶段性交接 |
| 每项功能要求与兼容路径 | [按功能目录](features/README.md) | 需求JSON+实际目录/清单生成 |
| 修改哪份需求源 | [结构化需求说明](requirements/README.md) | 不手改生成页面 |
| 适配器、坐标与收束契约 | [架构入口](architecture/README.md) | 稳定工程设计 |
| 当前在做什么、是否发布 | [工作状态](work-status.md) | 只保留本轮状态 |
| 某版改了什么 | [版本日志](releases/) | 每版独立累计说明 |
| 查出了什么、怎样验证 | [复审记录](reviews/) | 保留验证层级和证据 |
| 打包与推送规则 | [发布约定](release-policy.md) | 只记录流程规范 |
| 许可与来源 | [来源记录](来源与许可证.md) | 不因整理删除 |
| 旧文档和历史工程 | [历史归档](archive/README.md) | 冻结，不作为当前要求 |

生成/核对：`python tools/verify_compatibility.py --write-doc` 与 `python tools/verify_compatibility.py`。普通构建拒绝生成页面与实际代码/清单不一致。
