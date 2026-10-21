#!/system/bin/sh
# post-fs-data: overlay Xiaomi 17 (pudding) NFC stack onto haotian.
# Everything is a runtime bind mount; removing the module + reboot restores stock.

MODDIR=${0%/*}
LOG=/data/local/tmp/nfc-odm17.log
exec >> "$LOG" 2>&1
echo "=== $(date) boot ==="

i=0
while [ ! -d /odm/lib64 ] && [ $i -lt 200 ]; do sleep 0.1; i=$((i + 1)); done

TMP=/mnt/nfcmod
mkdir -p "$TMP"
# suid+dev is required: init refuses to exec the HAL service from a nosuid mount
mount -t tmpfs -o size=512m,mode=0755,suid,dev tmpfs "$TMP" || { echo "tmpfs failed"; exit 1; }

put() {
  cp -f "$1" "$2" || return 1
  chmod "$3" "$2"
  chcon "$4" "$2" 2>/dev/null
}

put "$MODDIR/odm/bin/hw/android.hardware.nfc-service-st" "$TMP/service" 755 u:object_r:hal_nfc_default_exec:s0
put "$MODDIR/odm/lib64/nfc_nci.st21nfc.st.so" "$TMP/hal.so" 644 u:object_r:vendor_file:s0
put "$MODDIR/odm/firmware/st54l_fw.bin" "$TMP/fw.bin" 644 u:object_r:vendor_file:s0
put "$MODDIR/odm/etc/libnfc-hal-st.conf" "$TMP/hal.conf" 644 u:object_r:vendor_configs_file:s0
put "$MODDIR/odm/etc/st54l_conf.txt" "$TMP/st54l.txt" 644 u:object_r:vendor_configs_file:s0

mount --bind "$TMP/service" /odm/bin/hw/android.hardware.nfc-service-st
mount --bind "$TMP/hal.so" /odm/lib64/nfc_nci.st21nfc.st.so
mount --bind "$TMP/fw.bin" /odm/firmware/st54l_fw.bin
mount --bind "$TMP/hal.conf" /odm/etc/libnfc-hal-st.conf
mount --bind "$TMP/st54l.txt" /odm/etc/st54l_conf.txt

if [ ! -f /data/adb/nfc_apk_off ]; then
  put "$MODDIR/system/app/Nfc_st/Nfc_st.apk" "$TMP/Nfc_st.apk" 644 u:object_r:system_file:s0
  mount --bind "$TMP/Nfc_st.apk" /system/app/Nfc_st/Nfc_st.apk && echo "apk overlaid"
  if [ -f /data/adb/nfc_mask_oat ]; then
    mkdir -p "$TMP/oat"
    mount --bind "$TMP/oat" /system/app/Nfc_st/oat 2>/dev/null && echo "oat masked"
  fi
else
  echo "apk overlay disabled by /data/adb/nfc_apk_off"
fi

echo "done"
