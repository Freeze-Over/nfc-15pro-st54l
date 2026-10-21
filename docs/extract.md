# 从固件提取 blobs

以小米 17（pudding）**OS3.0.319.0（Android 16）** 为例。

## 1. 解 payload.bin

小米完整包目录里有 `payload.bin`（约 8 GB）和 `META-INF/`。

```bash
python tools/payload.py payload.bin odm system
# 产出 odm.img（约 3.2 GB）、system.img（约 0.9 GB）
```

脚本按 OTA manifest 的 op 列表解压（REPLACE / REPLACE_BZ / REPLACE_XZ），
输出是分区的完整镜像（EROFS）。

## 2. 在手机上只读挂载

镜像不能在 Windows 上直接读，用一台 root 手机挂载（只读）：

```sh
adb push odm.img /data/local/tmp/odm.img
adb shell su -c 'setenforce 0'
adb shell su -c 'mkdir -p /data/local/tmp/mnt'
adb shell su -c 'losetup -r /dev/block/loop42 /data/local/tmp/odm.img'
adb shell su -c 'mount -t erofs -o ro /dev/block/loop42 /data/local/tmp/mnt'
```

注意：

- 有些内核的 loop minor 是 `序号 × 8`，节点不存在时手动 `mknod`；
- `/data` 为 f2fs + `inline_data`/压缩的机型，loop 读取可能报 I/O error，
  换另一台 `/data` 为 ext4 的机器更稳；
- 挂载完记得 `setenforce 1`。

## 3. 复制文件

```sh
adb shell su -c 'cp /data/local/tmp/mnt/lib64/nfc_nci.st21nfc.st.so /data/local/tmp/'
adb shell su -c 'cp /data/local/tmp/mnt/firmware/st54l_fw.bin /data/local/tmp/'
adb shell su -c 'cp /data/local/tmp/mnt/etc/libnfc-hal-st.conf /data/local/tmp/'
adb shell su -c 'cp /data/local/tmp/mnt/etc/st54l_conf.txt /data/local/tmp/'
adb shell su -c 'cp /data/local/tmp/mnt/bin/hw/android.hardware.nfc-service-st /data/local/tmp/'
adb pull /data/local/tmp/nfc_nci.st21nfc.st.so blobs/odm/lib64/
# ... 其余同理
```

`Nfc_st.apk` 在 `product/pangu/system/app/Nfc_st/`，需要再解 `product` 分区；
或在已刷入 puding 移植包的设备上直接从 `/system/app/Nfc_st/` 取。

## 4. 校验

```bash
sha256sum blobs/odm/lib64/nfc_nci.st21nfc.st.so   # 期望 e3ea02c6…
sha256sum blobs/odm/firmware/st54l_fw.bin         # 期望 22a9e85d…
```

## 5. super.img（可选）

如果拿到的是 `super.img`（fastboot 包常见）：

```bash
python tools/lpunpack.py super.img            # 列出分区
python tools/lpunpack.py super.img odm_a odm.img
```

若是 sparse 格式，先：

```bash
python tools/sparse_unpack.py super.img super_raw.img
```
