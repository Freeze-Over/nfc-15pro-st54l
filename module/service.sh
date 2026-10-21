#!/system/bin/sh
# Late-start service: inject the MIFARE hook and configure the 7A polling frames.
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

# Configure the 0x7A polling frames. Retry: NFC may still be initialising, and the
# vendor command has to be sent as root through app_process.
prep() {
  CLASSPATH="$MODDIR/q13.jar" /system/bin/app_process /system/bin dev.probe.Q13 2>&1
}

n=0
while [ $n -lt 4 ]; do
  n=$((n + 1))
  OUT=$(prep)
  echo "$OUT"
  case "$OUT" in
    *resp=1300*) echo "prep ok (attempt $n)"; break ;;
  esac
  echo "prep attempt $n failed, retrying"
  sleep 10
done

echo "nfc=$(pidof com.android.nfc)"
ls -l /odm/lib64/nfc_nci.st21nfc.st.so /odm/firmware/st54l_fw.bin /system/app/Nfc_st/Nfc_st.apk
