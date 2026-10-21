# 协议笔记（实测）

## 厂商命令

经 `NfcAdapter.sendVendorNciMessage(gid, oid, sub, payload)` 下发，
框架会拼成 NCI `2F <gid> ...` 厂商包。

| 用途 | 调用 | 15 Pro 原厂 | 15 Pro + pudding 栈 |
| --- | --- | --- | --- |
| 配置 7A 轮询帧 | `(1, 0x0f, 0x13, [01 2A 02 0A 7A])` | `1305` | **`1300`** ✅ |
| 读取轮询帧 | `(1, 0x0f, 0x15, [..])` | `150B` | `150B`（不需要） |
| QTag 开 | `(1, 0x0f, 0x70, [31 01])` | `05` | `05`（Xiaomi 17 上是 `00`） |

`0x13` 的 payload 含义（来自 ink-nfc-lab `Protocol.polling7A()`）：

```
01       帧数 = 1
2A       帧配置（标准响应 / NFC-A / 短帧 / 无 CRC / 等待 10）
02 0A    延时 = 10
7A       实际发送的 7-bit 帧
```

配置成功后控制器按 `0x7A` 寻卡，壳子应答，HAL 会持续上送厂商通知：

```
6f 02 0d 31 20 00 48 08 01 00 01 7a ...
                  └─ 轮询帧 0x7A
```

## 激活与失败点

配好 `0x13` 后：

```
NFA_ACTIVATED_EVT
discoverTechnologies (activation): index=0; tech=0x3 ... index=1; tech=0x1; nfc type=0x4
NFA_SELECT_RESULT_EVT: status = 0
NFA_SELECT_RESULT_EVT: status = 3   ← 失败
onTagRfDiscovered: false
```

换成 pudding 的 `Nfc_st.apk` 后，这个阶段能通过。

## MIFARE/NDEF 抢占

系统把壳子当 Type-2 卡读 NDEF：

```
rw_t2t_process_timeout: T2T timeout. state=READ_NDEF
NDEF detection timed out
```

随后 deactivate，App 的 ISO-DEP 会话被打断（`TagLostException`）。

拦截方法（`mifare.js`）：

```js
// libstnfc_nci_jni.so
NfcTag::createNativeNfcTag(tNFA_ACTIVATED *activated)
  activation = hex(activated, 17)
  if (activation.startsWith('010400040004') && activation.endsWith('00000000000028')
      && getNumDiscNtfE(this) === 1)
      setNumDiscNtfE(this, 0)   // 系统不再创建 Tag
```

- 前缀 `010400040004`：NFC-A + ATQA `04:00`
- 后缀 `28`：SAK `0x28`（ISO-DEP）
- `getNumDiscNtfE/setNumDiscNtfE`：待处理发现通知计数

## 壳子被系统读出的信息

```
类型 NDEF / ID `XX:XX:XX:XX`（4 字节，随批次不同）/ 1024 B / 16 扇区
协议 ISO/IEC 14443-3 Type A
ATQA 04:00   SAK 28
```

SAK `0x28` 表示支持 ISO-DEP（ISO 14443-4），刷图走的就是这条通道。

## 刷图协议

由 App 完成：`D1`（设备信息）→ `D8` 认证 → `D3` 上传 → `D4` 刷新 → `DE` 状态轮询。

- `DE` 返回 `009000`：刷新完成；
- `SW=68CA`：屏端低电压/供能不足（贴合不稳或移动）；
- `SW=6502`：同样属于供电问题。
