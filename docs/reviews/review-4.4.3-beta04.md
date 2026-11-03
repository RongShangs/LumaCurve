# 4.4.3 Beta04 复审

## 输入与定位

Redmi K60 /mondrian /HyperOS1 /Android14的回访分析包确认Beta03曲线活动，原生感光和参考切换有日志，但模式读取失败于缺mAutomaticBrightnessStrategy，独立输入校验失败于缺mFovAmplifyFactor。与此前私有固件的services及miui-services内容一致；静态证据复用，不将文件匹配当真机通过。

## 兼容收束

- AutomaticModeAccess：owner字段优先；新原生策略其次；策略字段整体缺失才核验旧DPC boolean和归属。 present-null、错误类型、非主屏、对象替换均拒绝。原生字段读取统一服务物理/Simple后端，不在界面猜模式。
- BrightnessSensorAccess：保留单源、倍率和FOV；只有两个倍率均不存在且完整旧DualSensorPolicy单位lux ABI才采用新变体。现存空值/半套倍率不降级；不新增传感器订阅。
- 诊断展示照度换算及失败证据，兼容清单ABI变体、功能需求和生成文档同步。

## 保留与验证

旧配置键、默认、持久化协议、记忆行为均未修改；原生raw11载荷和所有APK assets保持Beta03，网站名单单独更新不改离线名单。既有交付完整保留，原包按机型私有归档。

验证入口：refactor_automatic_mode、refactor_legacy_input、refactor_legacy_input_firmware、refactor_brightness_control、refactor_feature_self_check；完整构建统一跑对应旧路径、固件静态、契约、生产编译和签名。结果与产物校验保存在本地build/beta-4.4.3-beta04，不公开私有固件。

## 未覆盖边界

响应确认缺两参数原生期限及场景ABI；部分场景、传统动画、温控、HBM归属和SystemUI坐标各缺不同接口。本轮没有伪装支持或改动这些路径，不宣称K60全功能适配。没有安装、重启、调亮度、功耗或长测结论。
