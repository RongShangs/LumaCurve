# 手机 root Agent：核心集成前的只读接口验证

framework04 的一分钟控制测试已通过，无需再做同类亮度升降实验。
本次仅解决可靠模式/偏好读取和曲线单位，属于正式核心适配的必要输入。

解压 LumaCurve-framework-probe05.zip，在解压目录执行：

```sh
su
sh ./probe_framework05_android.sh
```

无需安装、重启或做任何亮度/光感动作，约 10～40 秒。
不暂停或启动模块，不写亮度、Settings、模式或 sysfs。
尝试用外部 provider token 读取本机当前用户的三项设置：
screen_brightness_mode、screen_auto_brightness_adj、screen_brightness。
同时读取框架亮度、单位 1/2 的保存值以及主屏 displayconfig XML。
不复制完整用户设置文件，也不会上传其他设置。

将整个 luma-framework05-时间戳-PID 文件夹压缩回传。
即使接口失败也请回传，重点是 settings-bridge.txt 和完整异常。
读取失败不能被解释成 mode=0；输出适配层会保守拒绝接管，避免覆盖手动调节。

本次并未将框架后端接入正式核心，也没有更新模块、官网或 GitHub。
