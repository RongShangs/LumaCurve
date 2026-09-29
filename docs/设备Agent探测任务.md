# 手机 root Agent 探测任务：主屏 sysfs 写入是否被模块自锁

本轮不要安装 test02，不写新守护脚本，不修改模块源码，不切换 SELinux 模式。
只检查 `/sys/class/backlight/panel0-backlight/brightness`，不操作 panel1 背屏。
请保留命令、stdout、stderr、退出码、测试前后数值，最后恢复原来的运行/暂停状态。

## 待验证假设

LumaCurve 接管时执行 `chmod(..., 0x124)`，即 0444。
执行器每帧又通过 open(O_WRONLY) → write → close 写同一个 sysfs 节点。
Android kernfs 的额外 open 权限检查可能拒绝所有写权限位均清除的属性，
即使调用方有 CAP_DAC_OVERRIDE，也不代表能绕过该检查。
因此 0444 下 root 写失败不能证明缺少驱动 store 回调。

源码依据：
https://android.googlesource.com/kernel/common/+/7dcbebc5490b10bb5b9e136918fa56bf9dcfed32/fs/kernfs/file.c
检查 kernfs_fop_open 的 KERNFS_ROOT_EXTRA_OPEN_PERM_CHECK 分支。
本机内核是否走同一分支，仍需本轮探测确认。

## 执行顺序

1. 记录 SELinux 状态、内核版本、主屏节点 realpath、mode/uid/gid、当前值、最大值。
   记录模块暂停文件是否存在，以及 daemon PID、/proc/PID/attr/current、CapEff。
   在未暂停的现状下保存 `--check-install` 输出与退出码。不要重复写 12000。

2. 使用已安装模块的控制脚本正常暂停：

   ```sh
   sh /data/adb/modules/luma_curve/luma_curvectl.sh pause
   ```

   确认 daemon 已退出、暂停标志存在，服务没有重新拉起它。
   立即重新记录主屏节点权限。旧核心正常退出会尝试恢复 0644，
   如果从 0444 变回 0644，这是模块主动改变权限的证据。

3. 若节点仍为 0444，先记录，再执行 `chmod 0644`，记录退出码与实际权限。
   在 SELinux Enforcing 下重新运行同一二进制的 `--check-install`。
   然后向节点写入刚读到的当前整数值，立即读回。
   这一步先验证写通道，不应直接提高到曲线中的 60% 或 100%。
   若写当前值成功，再做一次最多增加最大背光 1% 的小幅测试，随后恢复测试前值。
   记录系统自动亮度是否立即覆盖，并区分写命令成功与读回值被覆盖。

4. 若 0644 下可写，验证已打开 FD 能否跨越 chmod 保持写入。
   在同一个 shell 中操作，用刚读到的当前值，不做大幅亮度变化：

   ```sh
   NODE=/sys/class/backlight/panel0-backlight/brightness
   chmod 0644 "$NODE"
   VALUE=$(cat "$NODE")
   exec 9>"$NODE"
   chmod 0444 "$NODE"
   printf '%s\n' "$VALUE" >&9
   cat "$NODE"
   exec 9>&-
   chmod 0644 "$NODE"
   ```

   每一步分别记录成功或失败；不要在 `exec 9>` 失败时继续。
   在 0444 时另起一次普通写入，比较它是否失败，而 FD 9 写入成功。
   无论中途何处失败，都关闭测试 FD 并恢复 0644。
   此测试用于判断“接管前打开并保留写 FD”的修复是否适用。

5. 若暂停后、0644 下仍不可写，才继续查 inode/挂载层与驱动路径：
   记录 realpath、mountinfo、SELinux 标签、内核日志中的相关拒绝；
   若系统有 strace，捕获对这个节点的 openat/write 的准确 errno。
   不要仅凭二进制中的错误字符串、节点当前 mode 或 daemon fd 列表推断没有 store。
   当前核心每次写完会 close，采样 fd 列表通常看不到它。

6. 收尾：关闭测试 FD，恢复测试前亮度与系统设置，不改 panel1。
   如果测试前引擎在运行，使用控制脚本 resume；若本来暂停，保持暂停。
   记录恢复后的节点权限与 daemon 状态。请不要删除原日志、配置或学习文件。

## 报告格式

| 测试阶段 | mode / uid / gid | open/写命令退出码 | 写前 / 写后 / 延迟读回 | 错误文本或 errno |
| --- | --- | --- | --- | --- |
| 引擎运行 | | | | |
| 引擎暂停后 | | | | |
| 0644 写当前值 | | | | |
| 0644 小幅变化并恢复 | | | | |
| 先打开 FD 再改 0444 | | | | |
| 0444 重新打开 | | | | |

优先给原始结果，结论标明是已验证还是推测。
若仅修改 mode 就使同一主屏节点可写，优先修复 LumaCurve 的接管/写入机制；
无需直接改成 settings 通道。settings put 成功只证明系统设置影响亮度，
不能证明它在自动亮度模式下能持续接管，也不是 DRM 调用路径的直接证据。

安装时的报告已有 `actual_brightness_write=unverified`，不能当作实际控制通过。
但“安装时 open 成功、运行时 open 失败”也可能由模块自己设置 0444 引起，
需要先完成上面的前后对照，再决定是否改安装探测流程。
