#!/system/bin/sh
# 准备刷图：发送 ST 0x13 轮询帧配置（7A），成功后返回 1300。
# 用法：su -c 'sh /data/adb/modules/nfc-odm17/prep.sh'
MODDIR=${0%/*}
JAR="$MODDIR/q13.jar"
CLASSPATH="$JAR" /system/bin/app_process /system/bin dev.probe.Q13
