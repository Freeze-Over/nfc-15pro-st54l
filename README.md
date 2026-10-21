# nfc-15pro-st54l

在**小米 15 Pro（haotian / ST54L）** 上驱动 **华为 552×768 四色墨水屏手机壳** 的 KernelSU/Magisk 方案。

> 目标设备：Xiaomi 15 Pro（2410DPN6CC，Android 16，KernelSU）
> 结果：**已打通**——传图 476 包 + `DE` 刷新确认，壳子正常显示。

![成功刷图](docs/success.jpg)

---

## 1. 为什么 15 Pro 原厂不行

壳子只响应 **7-bit 短帧 `0x7A`**（华为私有寻卡），普通 NFC 读卡器发的 `WUPA(0x52)` 它不理。
NXP 用 **Q-Poll**（NCI tech `0x71`）发这个短帧；ST 的方案是 **QTag + 自定义轮询帧**。

15 Pro 的 ST 栈缺了关键引擎：

| 文件 | 15 Pro 原厂 | 小米 17（pudding） |
| --- | --- | --- |
| `odm/lib64/nfc_nci.st21nfc.st.so` | 200,560 B，**无 `stpropnci`** | 283,656 B，**含 30 个 `stpropnci_*` 符号** |
| `odm/firmware/st54l_fw.bin` | 250,917 B | 260,157 B |
| `odm/etc/libnfc-hal-st.conf` | 6,282 B | 7,177 B |
| `odm/etc/st54l_conf.txt` | 6,774 B | 6,771 B |
| `odm/bin/hw/android.hardware.nfc-service-st` | 86,376 B | 86,400 B |
| `system/app/Nfc_st/Nfc_st.apk` | 15,388,724 B | 15,905,312 B |

实测（15 Pro 原厂 HAL，`sendVendorNciMessage`）：

```
0x13（配轮询帧） → 1305（参数被拒）
0x15（读轮询帧） → 150B（不支持）
0x70 + 31 01（QTag 开） → 05（被拒）
```

换 pudding 的 HAL/固件后，`0x13` 返回 `1300`、壳子能激活，但框架侧 SELECT 仍失败
（`NFA_SELECT_RESULT_EVT error: status = 3`）。**原因是 `Nfc_st.apk`（框架）也必须是 pudding 版。**

---

## 2. 方案：移植小米 17 的整套 NFC 栈

不做分区修改，全部用 **KernelSU 模块 + 运行时 bind mount**：

```
开机 post-fs-data
 ├─ 建 suid tmpfs（/mnt/nfcmod）
 ├─ 把 pudding 的 HAL / 固件 / 配置 / service 拷进去并打 SELinux 标签
 ├─ bind mount 覆盖 /odm/...
 └─ bind mount Nfc_st.apk 覆盖 /system/app/Nfc_st/Nfc_st.apk

开机 late_start service
 └─ 等 com.android.nfc 启动后，用 frida-inject 注入 mifare.js
```

### 2.1 为什么必须用 suid tmpfs

直接 bind mount `/data/adb/modules/...` 会被 SELinux 拒绝：

```
avc: denied { execute_no_trans } ... hal_nfc_default_exec
avc: denied { nosuid_transition } ... init -> hal_nfc_default
```

`/data` 是 nosuid，init 无法从 nosuid 挂载点 transition 到 HAL 域。
把文件放进 `mount -t tmpfs -o suid,dev` 的 tmpfs 再 bind，即可。

### 2.2 为什么要 frida hook

即使 HAL/框架都对了，系统仍会把壳子当普通 NFC 标签自动读取（Type-2 NDEF），
和刷图 App 的 ISO-DEP 会话抢卡：

```
rw_t2t_process_timeout: T2T timeout. state=READ_NDEF
NDEF detection timed out
```

`mifare.js` 在 `libstnfc_nci_jni.so` 里 hook `NfcTag::createNativeNfcTag`，
当激活参数是 NFC-A + SAK `0x28` 且待处理发现通知为 1 时，把计数清零，
让系统不再创建 Tag，把会话留给 App 的 ReaderMode。

### 2.3 为什么还要配 0x13

ST 控制器默认不会发 `0x7A` 轮询帧。刷图前用 `q13.jar` 发送：

```
sendVendorNciMessage(gid=1, oid=0x0f, sub=0x13, payload = 01 2A 02 0A 7A)
→ 1300（成功）
```

之后控制器用 `0x7A` 寻卡，壳子才会应答。

---

## 3. 目录结构

```
module/          模块源码（纯文本，可直接提交）
  module.prop
  post-fs-data.sh   挂载 HAL/固件/APK
  service.sh        注入 frida hook
  prep.sh           配 0x13（刷图前运行）
  mifare.js         MIFARE/NDEF 拦截脚本
src/
  q13/Q13.java      0x13 探针源码
  qtag/Qtag.java    QTag(0x70) 探针源码
tools/
  payload.py        Android OTA payload.bin 解析/提取
  lpunpack.py       super.img（liblp）解析/提取
  sparse_unpack.py  sparse image → raw
blobs/            小米/ST 专有文件（.gitignore，不提交）
docs/             截图与协议说明
build.ps1         组装 nfc-odm17.zip
```

---

## 4. 准备 blobs

仓库**不包含**厂商二进制（版权原因）。从你自己的 OTA 里提取：

1. 下载小米 17（pudding）**HyperOS OS3.x（Android 16）** 完整包；
2. 解出 `payload.bin`，用 `tools/payload.py` 提取 `odm` 和 `system`：

```bash
python tools/payload.py payload.bin odm system
```

3. 把文件按下面路径放进 `blobs/`（挂载到设备后可复制）：

```
blobs/odm/lib64/nfc_nci.st21nfc.st.so                 283,656 B
blobs/odm/firmware/st54l_fw.bin                       260,157 B
blobs/odm/etc/libnfc-hal-st.conf                        7,177 B
blobs/odm/etc/st54l_conf.txt                            6,771 B
blobs/odm/bin/hw/android.hardware.nfc-service-st       86,400 B
blobs/system/app/Nfc_st/Nfc_st.apk                 15,905,312 B
```

校验和（SHA-256 前缀）：

| 文件 | SHA-256 |
| --- | --- |
| `nfc_nci.st21nfc.st.so` | `e3ea02c60951c227…` |
| `st54l_fw.bin` | `22a9e85d…` |
| `Nfc_st.apk` | 见你本地提取结果 |

> 把 Android 镜像挂到手机上取文件的方法：`losetup -r` + `mount -t erofs`，
> 需要先 `setenforce 0`，见 `docs/extract.md`。

---

## 5. 构建与安装

```powershell
# 组装模块（需要 blobs/ 已就位）
.\build.ps1
# 产物：build/nfc-odm17.zip
```

安装：

1. 把 `/data/local/tmp/frida-inject` 放好（Frida 17.24.0 android-arm64，约 84 MB）；
2. KernelSU/Magisk 安装 `nfc-odm17.zip`；
3. 重启；
4. 验证：

```sh
adb shell su -c 'cat /data/local/tmp/nfc-odm17.log'
# 期望：apk overlaid / hook inject rc=0
```

---

## 6. 刷图流程

1. 壳子贴住手机 NFC 线圈，压稳；
2. 配轮询帧：

```sh
adb shell su -c 'sh /data/adb/modules/nfc-odm17/prep.sh'
# SEND 13012A020A7A send=0 resp=1300  resume=ok
```

3. 打开 QPollLab（`org.nfcresearch.qpolllab`）→「发送当前预览到墨水屏」→「确认并开始刷新」；
4. 保持贴合，直到提示「刷新周期已结束」。

---

## 7. 回滚

**常规**（模块只做运行时挂载，不动真实分区）：

```sh
adb shell su -c 'rm -rf /data/adb/modules/nfc-odm17 && reboot'
```

**进不了系统**：KernelSU 安全模式（开机按住音量下）或 recovery 删除该目录。

**完整镜像恢复**：刷同版本官方 fastboot 包的 `super`（或 `system`/`odm`）即可；
本模块不写分区，正常情况下用不到。

---

## 8. 已知限制

- 传输中途手机震动/位移会报 `SW=68CA`（低电压/供能不足），重贴重发即可；
- `0x13` 配置在 NFC 服务重启后可能丢失，需重跑 `prep.sh`；
- 模块启用期间普通 NFC（门禁/公交）行为未验证；
- 依赖 `/data/local/tmp/frida-inject`，重启后仍在，但被清理需重新放；
- 仅验证 haotian + pudding OS3.0.319 提取的 blobs，其它版本请自行核对校验和。

---

## 9. 合规

- 本仓库代码：MIT。
- `blobs/` 中的 HAL、固件、APK 版权归 Xiaomi / STMicroelectronics 所有，
  **不在本仓库分发**；请从你合法持有的设备固件中提取，仅供个人研究。
- 参考项目：
  - [ZhaoGY-N/huawei-nfc-eink-android-app](https://github.com/ZhaoGY-N/huawei-nfc-eink-android-app)
  - [wiwiwei/ink-nfc-lab](https://github.com/wiwiwei/ink-nfc-lab)
