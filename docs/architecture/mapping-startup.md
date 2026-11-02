# 控制中心映射开机恢复

4.4.0 Beta 01 起，补充既有控制中心兼容路线的启动连接。旧文档中“需要打开APP重新连接”描述的是此前行为；坐标、手势和节点路线本身不替换。

## 职责

- `MappingStartup` 只读取原始配置作为任务调度提示，记录本地开启状态和恢复结果。只有主用户已保存启用映射才调度；读取失败不清除已保存选择。
- 系统开机、用户解锁、包被覆盖更新时，由私有接收器提交一次性 `JobScheduler` 任务。主用户开机广播在解锁后到达；不在解锁前访问凭据加密存储或启动Root。
- `MappingRecoveryService` 复用 `RootPanelBridge` 与原Root启动路线，只发送 `resume-mapping`。工作在线程中执行，每次连接8秒上限；不是前台服务，不额外分进程或运行APP常驻服务。
- `ControlCenterRoot.recover` 读取原配置，并复用 `reconcile`。须通过真实引擎、配置revision、PID/启动标识、boot_count、用户、设备节点和标定核验后，才发布当前PROFILE，恢复既有原生闲置监听。
- SystemUI继续按原PROFILE与TABLES共同核验，不降低代次要求。额外观察boot_count改变，避免早期读取时序导致缓存未刷新。

## 有界恢复和所有权

只有明确报告 `pending` 的就绪等待才重试，四次延迟依次3、10、30、60秒；不承诺系统精确按该秒数执行，平台可能延后调度。Root拒绝、超时、身份或节点变更等失败停止自动尝试，保留设置及原因供诊断。同一构建/开机代次的重复广播不会在终止后重复请求授权。缺少可读boot_count时只能做任务在途去重，不伪造开机身份。

关闭映射取消任务。系统停止Job时按jobId和attempt匹配任务，取消连接；不得依赖经Binder传来的JobParameters对象引用相等。旧工作线程晚返回时不得覆盖新任务记录或结束新任务。

恢复不重新保存配置、不修改revision、不重新生成记忆、不写旧亮度、不回放NODE_REQUEST、不自动进入增强区。原生监听仍需新的、通过校验的控制中心手势才能取得节点租约；现有手动面板保持不能被抢占。Root能力受限时只影响这项Root输出，曲线等LSPosed功能继续独立运行。

安卓强行停止应用、厂商后台自启动限制或Root不可用仍可能阻止任务执行；不得绕过这些限制、安装额外启动脚本或使用Root管理器名称判断能力。APP原inspect/apply重连继续保留，便于正常恢复和诊断。

## 作用域提醒

页面打开只静默读取。模式、分区节点上限或返回普通区行为实际变化时才按需提示；无关设置保存、重复选中和同一草稿不重复弹。未知作用域数据库与当前已验证注入分开：已核验当前或旧版SystemUI加载时，不以数据库不可读误报未添加。已知作用域缺失仍可在相关修改时提示。状态说明和手动核对入口保留。

## 自检与验证

功能自检新增 `mapping_startup / android_boot_job` 独立路线，显示恢复状态、次数与原因；仅读取报告，不在自检时启动Root。分析包增加 `control-center/startup.json` 和只读作用域/注入检查结果。

`tests/refactor_mapping_startup.py` 执行生产任务调度和异步生命周期，覆盖默认关闭、旧配置升级提示、四次等待、重复广播、拒绝停止、取消和旧线程晚返回。`refactor_control_center_root.py` 执行真实恢复适配器的新旧代次及所有权负向检查。`refactor_system_ui_setup.py` 和设置卡片测试覆盖真实相关修改与去重。主机通过不等于设备重启或功耗实测。

## Beta 02：独立Root启动补充

实际包确认Android Job直到用户打开APP后才执行，因此不得把Job自报restored解释为开机无需APP已成功。增加RootMappingBoot管理/data/adb/service.d/hyperlux-mapping.sh；只在已保存映射开启时通过现有Root连接登记，关闭后撤回。仅覆盖受管标记与登记/当前哈希相符的文件，拒绝链接和未知内容；原子替换，owner-only执行。入口配置不代表所有Root实现都支持执行，必须分开显示。

脚本有限等待sys.boot_completed（最多120次、2秒），再按0/3/10/30秒尝试当前安装APK的boot-resume-mapping，每次timeout8秒。APK从系统包管理当前主用户查询，拒绝多路径、未知路径和链接；卸载后不启动旧APK。只有pending重试，不启动Activity、不su、不恢复旧亮度。Root命令复用当前配置和既有reconcile，旧Android Job继续保留。

Root配置键、Root实际执行结果键与短日志分别导出，避免APP晚到的任务覆盖开机证据。自检仅readOnly inspect；不写目录或安装脚本。新增目录/脚本不新增长期APP进程；启动脚本执行结束后退出。
