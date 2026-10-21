#!/system/bin/sh
# Late-start service: inject the MIFARE compatibility hook into com.android.nfc.
MODDIR=${0%/*}
LOG=/data/local/tmp/nfc-odm17.log
exec >> "$LOG" 2>&1
echo "=== $(date) service ==="

i=0
while [ -z "$(pidof com.android.nfc)" ] && [ $i -lt 180 ]; do sleep 1; i=$((i + 1)); done
sleep 5

if [ -x /data/local/tmp/frida-inject ]; then
  /data/local/tmp/frida-inject -n com.android.nfc -s "$MODDIR/mifare.js" -e
  echo "hook inject rc=$?"
else
  echo "frida-inject missing at /data/local/tmp/frida-inject"
fi

echo "nfc=$(pidof com.android.nfc)"
ls -l /odm/lib64/nfc_nci.st21nfc.st.so /odm/firmware/st54l_fw.bin /system/app/Nfc_st/Nfc_st.apk
