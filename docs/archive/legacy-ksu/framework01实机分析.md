# HyperOS 4 第一轮框架探测结论

输入：LumaCurve-probe01-inspect-output.zip，2026-09-29 21:08。
原始记录保存在 build/device-reports/framework01。以下来自本机记录，不能推广到所有小米固件。

设备 pandora / 25098PN5AC，Android 17（API 37），OS4.0.0.41.XBLCNXM。
主屏 logicalId=0，physicalId=4630946949513469331。另一个 display 是背屏。

## 已证实

- root app_process 成功调用 DisplayManagerGlobal.getBrightness(0)，返回 0.085594326。
- getBrightnessInfo(0) 成功；旧版仅打印对象地址，第二轮补读公开字段。
- 实际接口包含 setTemporaryBrightnessMode(int,int)、带单位参数的 get/setBrightness。
- 本机存在 MiuiRampAnimator、DisplayPowerControllerImpl、AutomaticBrightnessControllerImpl、
  MiuiPhysicalBrightnessMappingStrategy、ThermalBrightnessController 等小米实现。
- 主屏 dump 仍显示系统自动亮度启用；不能将 root 亮度读取成功当成控制权接管成功。

## 未证实

- setTemporaryBrightnessMode 的模式取值、释放方式、作用期限、调用权限。
- 小米渐变器是否采用感知域，以及 HAL/面板是否另有渐变或限制。
- 双侧感光的组合规则、手动亮度上限与系统热保护的调用关系。
- framework 请求能否稳定保持，以及模块停止后的可靠恢复。

Settings 读取异常为 `Unable to find app for caller ... when getting content provider settings`。
旧探测使用 ActivityThread.systemMain 构造 Context，但 app_process 客户端未被正常登记。
这是探测入口的问题，不能据此宣布设备不支持 Settings 或缺少亮度控制接口。
旧 control 路径依赖该入口，目前不应执行。

## 第二轮只读采集

复制本机 framework.jar、services.jar、miui-framework.jar、miui-services.jar 并逐份校验 SHA-256。
离线检查相关方法的字节码，先明确临时模式和恢复语义，再设计控制权试验。
不调用未知模式，不替换系统 JAR，不注入 system_server，不改变当前模块。
采集文件只用于本机适配，不包含在模块、源码公开交付或 Git 提交中。
