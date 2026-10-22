# 4.0.2 Beta 02 折叠取址回归复审

2026-10-20。根据新分析包继续修复，仅本地 Beta 打包，不发布或操作手机。保留 [Beta 01](review-4.0.2-beta01.md) 的原生输出和会话隔离逻辑，补正取址分支。

## 已确认的原因

Beta 01 的 main-panel-node 报 fold_binding_pending / default_display_not_physical。实际同版本 framework.jar 存在 Physical 与 StablePhysical 两个直接继承 DisplayAddress 的兄弟类型；工厂按实际显示结构返回其一，StablePhysical 不是 Physical 子类。旧代码用 Physical.isInstance 拒绝了有效的 StablePhysical，导致原可用内屏也被新路径阻断。上一轮主机测试覆盖了节点选择，却用 JSON 替身跳过实际 default display 反射入口，未发现此回归。

## 修复与规范

仍由 ActivePanelNode 集中处理实际默认内部显示；根据收到的对象选择已验证的两个地址 ABI，严格验证非静态 long getPhysicalDisplayId / int getPort、uniqueId 和实际返回值。未知地址、缺失、错误签名与调用异常等待，不伪造成功、不切换到另一类型绕过错误。

StablePhysical 的 ID 与端口分开存储，因此不再从 ID 低位计算它或其他未显示物理屏的端口。默认物理 ID 必须在原生可切换布局中；通过实际成对 panelN 内核节点清单、当前原生端口、同控制器、有效范围及唯一点亮 DSI 共同绑定。没有新增显示枚举接口依赖，不要求系统返回已禁用逻辑屏的地址，避免为了获取未使用的另一屏而阻断当前屏。旧 Physical 及普通直屏 / LED 发现路线保留。

新增内部 ABI 登记，状态及自检展示实际地址类型、端口来源和失败细节。默认、保存设置、主屏 / 用户控制权、原生亮屏保护、切屏释放与权限记录不改。

## 验证

实际用户 framework.jar 的 StablePhysical / Physical 方法、各自构造器和工厂字节码完成静态核对；私有输入留在 build/fold-panel-6a5ef1，不进入交付。

主机执行实际 display() 反射入口及后续选择器，模拟同父类下两个独立地址类型；分别验证内外屏，另用稳定物理 ID 低位与端口不一致的样本，验证无数值推断。负向覆盖未知 / 空地址、非内部屏、身份冲突、原生 getter 失败、静态 getter 与错误返回类型。冻结 Physical-only 错误作为负向对照，确认能复现旧回归；修复后通过。原物理地址在新式地址 ABI 错误时仍独立可用。

完整旧 / 新 / 负向回归、原签名、版本代码、源码及旧产物保留校验以私有 build/beta-4.0.2-beta02/ 的最终记录为准。目标折叠机尚未执行新版，不声称内外屏真机已验证。
