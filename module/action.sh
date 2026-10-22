#!/system/bin/sh
# KernelSU 模块“执行”按钮：断场复位壳子 + 配 7A，并留 40 秒窗口给 App 进入 ReaderMode。
# 点完这个按钮后，切到 QPollLab 点「发送当前预览到墨水屏」→「确认并开始刷新」。
MODDIR=${0%/*}
exec sh "$MODDIR/prep.sh" 2000 40000
