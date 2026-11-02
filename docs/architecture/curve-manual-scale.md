# 普通手动曲线标尺

图表100%定义为设备原生普通手动亮度上限。Refactor/系数ABI使用sliderToLogicalBrightness(1)的逻辑坐标；物理映射的两条ABI使用DDC普通HBM边界（或已验证normal controller）经brightness→backlight→nit的原机纯转换。未知/残缺结构不猜值，保留旧全域并报告unconfirmed。

curve_display_range独立声明backend、coordinate、profile、state、maximum_nit。APP只采用匹配当前后端/坐标且在原设备范围内的值；三处图、编辑输入与拖动共用。临时动态限亮/温控、当前屏幕亮度、用户磁贴/节点增强上限不能作为固定轴依据。

max_logical_nit、基础曲线、记忆数值和配置身份不改。图高于普通区的曲线饱和显示在顶部，原有高亮点仍可点击，数值输入允许大于100%表达原设备全域。不得通过截断或倍率拉伸实际记忆假装拉满到顶，不改变普通手势的系统学习行为。受临时限亮时，100%目标也不承诺最终实测输出不受保护。

元数据由既有配置标定工作读取，不增传感器采样或UI轮询；显示配置更换才重新取稳定范围。自检/分析可读取元数据，不主动试写。测试refactor_curve_scale.py、refactor_curve_percentage.py及原分区/曲线/记忆回归共同验证。
