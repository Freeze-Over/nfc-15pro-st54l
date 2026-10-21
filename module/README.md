# nfc-odm17 — 小米 15 Pro 上使用小米 17 的 ST NFC 栈

**实验模块。** 已在 haotian（小米 15 Pro，Android 16 + KernelSU）上验证可以刷华为墨水屏壳。

## 它做什么

开机时（`post-fs-data`）把小米 17（pudding）的 NFC 组件通过 **运行时 bind mount** 覆盖到系统上：

| 目标 | 来源 |
| --- | --- |
| `/odm/bin/hw/android.hardware.nfc-service-st` | pudding 86,400 B |
| `/odm/lib64/nfc_nci.st21nfc.st.so` | pudding 283,656 B（含 `stpropnci`） |
| `/odm/firmware/st54l_fw.bin` | pudding 260,157 B |
| `/odm/etc/libnfc-hal-st.conf` | pudding 7,177 B |
| `/odm/etc/st54l_conf.txt` | pudding 6,771 B |
| `/system/app/Nfc_st/Nfc_st.apk` | pudding 15,905,312 B |

挂载源放在**带 suid 的 tmpfs** 上——必需，否则 init 会因 `nosuid_transition` 被 SELinux 拒绝，HAL 起不来。

`service.sh` 在 NFC 服务起来后，用 frida-inject 把 `mifare.js` 注入 `com.android.nfc`（MIFARE/NDEF 兼容拦截，防止系统抢走 ISO-DEP 会话）。

**不改任何真实分区**，只做运行时挂载。

## 依赖

- KernelSU / Magisk + Zygisk（模块本身不依赖 Zygisk，但系统里要有 root）
- `/data/local/tmp/frida-inject`（17.24.0+，约 84 MB）——放在这里一次即可，`service.sh` 会用它
- Shizuku（App 侧发送厂商命令用）

## 安装

1. 确保 `/data/local/tmp/frida-inject` 存在；
2. KernelSU / Magisk「从本地安装」`nfc-odm17.zip`；
3. 重启；
4. 检查：

```sh
adb shell su -c 'cat /data/local/tmp/nfc-odm17.log'
# 应看到 "apk overlaid"、"hook inject rc=0"
```

## 刷图流程

1. 壳子贴到手机 NFC 线圈位置，压稳；
2. 配 7A 轮询帧（每次 NFC 重启后做一次）：

```sh
adb shell su -c 'sh /data/adb/modules/nfc-odm17/prep.sh'
# 输出 SEND 13012A020A7A send=0 resp=1300  resume=ok
```

3. 立刻在 QPollLab 里「发送当前预览到墨水屏」→「确认并开始刷新」；
4. 保持贴合直到提示「刷新周期已结束」。

## 开关

```sh
# 只换 HAL、不换 Nfc_st.apk（更保守，但刷图不可用）
adb shell su -c 'touch /data/adb/nfc_apk_off'

# 屏蔽旧 odex，强制重编译（默认关闭）
adb shell su -c 'touch /data/adb/nfc_mask_oat'
```

## 回滚

```sh
adb shell su -c 'rm -rf /data/adb/modules/nfc-odm17 && reboot'
```

挂载消失，系统恢复原厂。**不需要刷机。**

如果进不了系统（NFC 崩溃一般不会导致 bootloop）：KernelSU 安全模式（开机按住音量下）或 recovery 删除模块目录。

## 已验证 / 已知问题

- ✅ HAL 运行、`0x13` 配置返回 `1300`、`createNativeNfcTag` 拦截生效；
- ✅ QPollLab 完整刷图：476 包传输 + `DE` 刷新确认，壳子画面正确；
- ⚠️ 传输中途手机若震动导致壳子位移，会出现 `SW=68CA`（低电压/供能不足）；
- ⚠️ `0x13` 配置在 NFC 服务重启后可能丢失，需重新跑 `prep.sh`；
- ⚠️ 普通 NFC（门禁/公交）在模块启用期间未验证，可能受影响。
