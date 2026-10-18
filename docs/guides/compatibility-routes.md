# 兼容路线管理

兼容维护已统一为长期规范，避免随版本复制多套清单：

- [架构与维护入口](../architecture/README.md)：阅读顺序与校验命令。
- [完整路线索引](../architecture/compatibility-index.md)：48 项功能、20 组适配、18 类 ABI 内部分支及全部生产 Java 的负责人、条件、证据、退出与验证。
- [兼容开发规范](../architecture/compatibility-policy.md)：新增路径、回退拒绝、安装事务、线程 / 所有权、设置保留和验收要求。
- [适配记录模板](../architecture/compatibility-change-template.md)：以后每轮新增或修改适配的设计与验证依据。

产品说明为“兼容 HyperOS”，具体功能仍以当前设备实际接口为准。新增兼容保留旧路径，能力暂缺只暂停本次执行，不能清空原设置。路线目录只解释检测 / 运行证据，不能自行切换控制路线；证据未报告时保留“未确认”。

路线索引由 CompatibilityCatalog 与维护 manifest 生成；请修改来源，再运行 `python tools/verify_compatibility.py --write-doc`，不要在这里重新维护第三份列表。
