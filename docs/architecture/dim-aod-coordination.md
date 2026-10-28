# DIM / AOD 亮度协调

## 边界与接口

DimAodCompatibility集中负责可选修复，不把判断拆到UI、Root和输出协调器。当前只有automatic_dim_aod_cap路线，基于实际Class/类加载器解析完整五参adjustSdrBrightness、两参adjustBrightnessWhenDimming、DPC电源请求、屏幕状态、原生原因常量和Doze策略接口。不按机型或OS号放行。旧固件没有这些接口时继续原路线。

两个钩子使用HookTransaction统一安装，能力只在整组成功后发布。外层ThreadLocal链记录本次调用的实例、原因、自动和DIM参数；内层只处理相同实例和本次上下文，外层无论原生正常返回还是抛出异常都释放/恢复上下文。实际引擎/用户、显示线程和主屏DPC归属再核验，避免跨实例或后台用户接管。

## 输出约束

仅引擎active、有效计划、实际自动模式、主屏ID0、当前ON且请求POLICY_DIM、原因REASON_AUTOMATIC及MODIFIER_DIMMED、原生全屏AOD与对应Doze策略均确认、HDR为false时参与。对有限(0,1]的已降亮输入，只将子阶段比它更高的有效AOD返回值压回输入。不改更低目标，不补正未知/无效坐标。原生后续画面/温控/电量保护及动画仍执行，正常BRIGHT和真正Doze/熄屏均不进入。暗光锁定、应用暂停和手动接管因自动条件不符而排除。

没有系统设置写入、节点写入、AOD状态修改、采样改动、唤醒锁、定时轮询或新进程。默认配置及所有已有用户设置不写回；停用引擎立即失去参与资格。原生/反射异常保留原结果，不转用其他输出路线。

## 证据与测试

runtime.dim_aod_compatibility记录profile、安装/运行错误、本进程纠正次数、last_elapsed_ms及最近一次输入和原生AOD结果；计数仅是历史，不标注当前正在运行。功能自检dim_aod及兼容目录说明实际采用和未采用情况。

tests/refactor_dim_aod.py执行生产适配器和钩子事务，覆盖137项自动DIM、其他模式/原因/屏幕/用户/线程排除、无外层上下文、嵌套、原生异常、反射错误、旧/残缺ABI和不同类加载器安装失败回滚。完整主线回归同时验证旧路径和设置保留。实际私有固件静态核验留在build/beta-4.2.0-beta03，不将固件或分析包放入公开源码；并未进行本次真机复现或功耗测试。
