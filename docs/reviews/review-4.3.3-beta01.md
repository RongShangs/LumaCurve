# 4.3.3 Beta 01 更新弹窗复审

用户截图仍显示最后一排按钮背景底部被裁切。旧测试简化了嵌套加权行，并只验证预测高度等于最终高度的情况，不能证明OEM渲染正常。本轮移除更新按钮区域的嵌套加权行；新UpdateDialogActions在展示前创建四按钮，实际自然高度后同排EXACT测量，自身计算完整两排高度和底部留白，不使用固定文字高度。交互仍为原四个按钮，下载/忽略/单次提示规则不变。

ResponsiveDialog固定区域先测量，内容单独滚动；onLayout按最终实际底部定位actions，标题及内容只占剩余空间。系统EXACT二次测量保留分配高度，避免再应用首轮期望比例。普通详情弹窗也执行最终底部约束，正常尺寸的内边距和操作规则不变。

5347项主机布局测试包括实际分配缩短、EXACT第二遍、短/横屏、大字号/两种语言和键盘；旧生产onLayout、onMeasure分别反向替换后触发断言，见私有layout-negative-controls.json。123项更新生命周期保持通过。旧程序的布局缺陷已在限定模型复现，不声称确定了用户OEM截图的唯一成因，也不将主机文字度量当成Android像素验证。

仅167份Java中的AppBuild、MainActivity、ResponsiveDialog及新UpdateDialogActions变化；原生raw10、协议、Root helper、HDR/节点/记忆/采样无功能改动。新类与ResponsiveDialog责任登记source-map；兼容路线目录不增加UI伪路线。保护此前APK/ZIP及全部保存设置，原签名与完整源码核验后仅本地打包，不发布/安装。最终回执build/beta-4.3.3-beta01/。
