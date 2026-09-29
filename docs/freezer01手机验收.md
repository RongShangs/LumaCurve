# freezer01 完整模块测试包

日期：2026-09-29。仍为 1.0.0 / 10000，仅本地测试，未推送 GitHub。
daemon 仍是已验证持有背光 FD 的 `20260929-test03`，未改曲线或渐变算法。
启动脚本标识：`launch_build=20260929-freezer01`（在启动日志中）。

## 本次根因已经确认

手机第二份包 `luma-freezer-probe.zip`，20:31:46 记录：

```text
daemon PID=26964
cgroup2=/apps/uid_10339/pid_11554
wchan=do_freezer_trap
父组 /apps/uid_10339: cgroup.freeze=1, cgroup.events frozen=1
子组 pid_11554: cgroup.freeze=0, cgroup.events frozen=1
背光写入 FD=8，仍在持有

service.sh PID=5002
cgroup2=/
wchan=__arm64_sys_nanosleep
```

父组冻结会作用于子组，子组自身 freeze=0 也不能豁免。
daemon 被留在应用控制组里，服务没有被冻结；这次不是驱动只读或 FD 丢失。
不能仅凭 uid_10339 数字认定具体应用，仍需包管理 UID 对照，但不影响修复归属问题。

## 具体改动

- service.sh 与 luma_curvectl.sh 共用 daemon_launcher.sh。
- launcher 在 exec daemon 之前，仅把自身迁入已核对的 cgroup2 根控制组，
  核验 /proc/PID/cgroup=0::/。调用者终端、管理器、其他应用均不迁移。
- 迁移失败/读回不符会阻止启动并写日志，不把不可靠的启动判为成功。
- 已在根组直接启动；没有 cgroup v2 的老系统检查 v1 freezer 是否处于根组。
- 保留原有 CPU background 与 renice 设置。本次没有改 memory v1 归属策略。
- 模块简介的核心标识新增“冻结”，不再仅因 PID 存活显示核心✔。
- 安装器显式提取新脚本；两份 update-binary 保持一致。
- 没有修改 core ELF、WebUI、温控、学习或曲线默认值。

## 安装测试

1. 在 KSU 安装 `luma_curve-1.0.0-freezer01.zip`，安装结束后重启手机。
   这是完整模块包，不需要额外运行热测试脚本。
2. 保留现有用户配置。之前的极端曲线仍可能保留在持久数据中：
   建议先使用温和曲线测试，不直接要求 60%～100% 背光。
3. 打开模块查看日志，应出现 `launch_build=20260929-freezer01 cgroup=already_root`。
4. 在 WebUI 设置里重启引擎，然后退出管理器/终端，锁屏之外保持屏幕亮起，
   等待 60～90 秒；再由手机 root Agent 采集，不只在管理器前台验收。
5. 采集控制组与背光时间序列（使用之前的只读探测脚本）：

```sh
su
sh ./probe_freezer_android.sh after_reboot
sh ./probe_display_android.sh after_reboot 60
```

## 通过条件与尚未完成

- daemon 属于 cgroup2 根组 `/`，没有 do_freezer_trap。
- 管理器退到后台后 updated_unix 持续变化，倒计时不再永久卡在同一值。
- 自动模式、屏幕亮起、唤醒观察结束后，actuator_write_attempts/successes 随调节增加，
  actuator_write_stage=readback_ok，目标与实际读回趋近。
- 亮度已经稳定时写入次数不增加是正常的，不能据此单独判冻结。
- 该修复在本地 14 项控制组测试（含删迁移的负向）、11 项核心状态测试和安装测试通过；
  本机实际迁移与后台持续运行尚待此次安装验收。
- 本机系统普通亮度当前上限约 37.49%、HBM off；raw sysfs 限制与光学输出映射尚待对照。
- 人眼感觉跳变与线性渐变优化尚未完成，本包先解决进程停止执行这一前置问题。

本次原理参照：[Linux cgroup v2 freezer](https://www.kernel.org/doc/html/v5.15/admin-guide/cgroup-v2.html)。
