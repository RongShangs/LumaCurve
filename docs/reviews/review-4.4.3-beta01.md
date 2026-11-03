# 4.4.3 Beta 01 · 普通与休眠映射兼容复审

## 输入与结论

Redmi K60 用户包记录 Android14/API34、HyperOS1.0.14.0，模块4.4.2已注入，失败为曲线初始化缺少getMode。私有原包、固件及反汇编按机型保存，不进入源码或APK。

实际固件同时缺getMode与mBrightnessMappingStrategyMap，使用isForIdleMode及mInteractiveModeBrightnessMapper/mIdleModeBrightnessMapper。物理曲线配置构造、原生样条、双向nit换算、原生学习及重置消费者与既有契约对应，适合增加上下文ABI变体。

## 边界审阅

- MapperContextAccess只做主屏、普通／休眠归属和活动状态检查，不改变系统模式。现代任一标记存在即要求完整现代ABI，未就绪、损坏、签名错误和原方法异常均不能降级。
- 差异留在TraditionalAdapter的上下文边界；公共绘图、配置、记忆和控制管理无需增加机型判断。物理nit与原生framework brightness坐标不变。
- 原模式编号／映射表、Refactor各布局、Simple及物理标定端点路线均保留。Simple仍维持自己已验证的ABI，本版未宣称所有旧式比例后端均已支持。
- 编辑只写已验证普通曲线；休眠活动期间不重置休眠模型、不回放普通记忆。元数据、原曲线恢复、提交失败回滚及原生Hook事务复用现有实现。
- 设置未改键、未加迁移或默认值写回；不新增采样、服务、Root守护或输出接管。

## 验证入口与限制

运行tests/refactor_mapper_context.py覆盖新旧和残缺ABI、现代优先、异常不回退、普通／休眠归属、曲线提交、原生学习、回放及失败回滚；其复用的旧物理适配器回归继续执行。

tests/refactor_mapper_context_firmware.py仅静态核对用户实际固件的模式、映射构造、配置、短期模型和原生消费者。原固件未分发；没有私有输入时可按既有构建选项显式跳过并记录。

完整构建、APK身份及原签名、源码对应、旧交付与未改原生程序的审计结果保存在本地build/analysis-20261103-215433-8b4142与build/beta-4.4.3-beta01。未安装设备、未做强光或功耗测试；本次未授权正式发布。
