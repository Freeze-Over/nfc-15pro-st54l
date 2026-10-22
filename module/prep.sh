#!/system/bin/sh
# 准备刷图：发送 ST 0x13 轮询帧配置（7A），成功后返回 1300。
#
# 用法：
#   su -c 'sh /data/adb/modules/nfc-odm17/prep.sh'            只配 7A
#   su -c 'sh /data/adb/modules/nfc-odm17/prep.sh 2000'       先断场 2 s 复位壳子，再配 7A
#   su -c 'sh /data/adb/modules/nfc-odm17/prep.sh 2000 8000'  复位后留 8 s 开场窗口，
#                                                            期间到 App 里点“开始寻卡”
MODDIR=${0%/*}
JAR="$MODDIR/q13.jar"
CLASSPATH="$JAR" /system/bin/app_process /system/bin dev.probe.Q13 "$@"
