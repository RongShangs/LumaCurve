# 4.3.1 Beta 03 作用域与系统界面重启复审

作用域配置与实际注入分开：按需Root读取本模块/主用户/精确SystemUI的LSPosed SQL记录，独立消费当前构建、精确进程名、PID及启动标识证明的UI状态。没有将无注入、暂缺Root或数据库损坏等价成缺少作用域。SQLite只读，无事务/DDL/更新，显式禁用默认损坏删除处理器；候选目录有界，冲突不选择一个猜测。

Root重启只由按钮二次确认触发。读取真实PM包UID，筛选精确com.android.systemui，读取启动标识并在signal前再次核验；其他包、冒用进程名但UID不符、system_server、子进程、PID复用及未知身份都拒绝。无killall、无作用域写入、无进程轮询服务。模块作用域已知缺失时先提示添加；权限不全无法重启时保留设置并提示错误。启动检查与按钮不自动保存草稿。

维护：新增适配器及展示协调器分别登记source-map，内部ABI集中登记compatibility-manifest，复用原Root worker，不在各页面复制LSPosed判断。保留原接口和全部设置；原生raw09字节应与Beta02一致。新测试执行生产DB/进程保护与UI协调器：只读flags、自定义损坏处理、缺失/禁用/拒读/类型错/多库冲突、旧/伪造UI状态、PID/UID/启动标识变化、未确认不得发命令和后台不弹窗等。

官方LSPosed表结构与默认路径依据：[ConfigManager](https://github.com/LSPosed/LSPosed/blob/master/daemon/src/main/java/org/lsposed/lspd/service/ConfigManager.java)、[ConfigFileManager](https://github.com/LSPosed/LSPosed/blob/master/daemon/src/main/java/org/lsposed/lspd/service/ConfigFileManager.java)。仅核对接口事实，自身实现，无复制第三方源码。框架变体未知时可手动核对，不写库适配。

没有在线ADB设备，未做实际进程终止或Android布局实测。完整编译、回归、原签名/原生/源码/历史产物核验回执见build/beta-4.3.1-beta03/；不发布或安装。
