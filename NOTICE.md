# Third-party notices

## Frida

`mifare.js` 通过 Frida 注入运行。仓库不分发 frida-inject / frida-gadget，
需自行从 Frida 官方 release 获取（17.24.x android-arm64）。

Frida 以 LGPL-3.0 发布，详见 https://github.com/frida/frida#license

## 厂商二进制（不在本仓库）

以下文件版权归 Xiaomi / STMicroelectronics，**未包含在仓库内**，
请从你自己设备的官方固件中提取，仅用于个人研究：

- `nfc_nci.st21nfc.st.so`
- `st54l_fw.bin`
- `libnfc-hal-st.conf` / `st54l_conf.txt`
- `android.hardware.nfc-service-st`
- `Nfc_st.apk`

## 参考项目

- ZhaoGY-N/huawei-nfc-eink-android-app（PolyForm Noncommercial 1.0.0）
- wiwiwei/ink-nfc-lab（PolyForm Noncommercial 1.0.0）

本仓库未复制上述项目代码，仅参考其公开的协议结论（NCI 命令、QTag 用法）。
