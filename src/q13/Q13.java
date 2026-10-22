package dev.probe;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 配置 ST 0x13 的 0x7A 轮询帧，可选先做一次“断场复位”。
 *
 * 用法（root）：
 *   CLASSPATH=q13.jar app_process /system/bin dev.probe.Q13 [dwellMs] [holdMs]
 *
 *   dwellMs  关场保持时间，让无源壳子掉电复位；默认 0（只配 7A）
 *   holdMs   配好 7A 后、开场前再等多久，给你时间去 App 点“开始寻卡”；默认 0
 *
 * 纯反射实现：不依赖 android.jar 编译，运行时用系统框架类。
 * 输出保持兼容 service.sh 的 *resp=1300* 判断。
 */
public final class Q13 {
    private static final int[] PAUSE_CANDIDATES = {10000, 5000, 3000, 2000, 1000};
    private static final LinkedBlockingQueue<byte[]> replies = new LinkedBlockingQueue<>();
    private static volatile int expectedOid = -1;

    private static void say(String s) {
        System.out.println(s);
        System.out.flush();
    }

    public static void main(String[] args) throws Exception {
        final long dwell = args.length > 0 ? Long.parseLong(args[0]) : 0L;
        final long hold = args.length > 1 ? Long.parseLong(args[1]) : 0L;
        Class<?> looperClass = Class.forName("android.os.Looper");
        looperClass.getMethod("prepareMainLooper").invoke(null);

        Class<?> managerClass = Class.forName("android.nfc.NfcServiceManager");
        Class<?> initializer = Class.forName("android.nfc.NfcFrameworkInitializer");
        if (initializer.getMethod("getNfcServiceManager").invoke(null) == null) {
            initializer.getMethod("setNfcServiceManager", managerClass)
                    .invoke(null, managerClass.getConstructor().newInstance());
        }
        Class<?> threadClass = Class.forName("android.app.ActivityThread");
        Object thread = threadClass.getMethod("systemMain").invoke(null);
        Object context = threadClass.getDeclaredMethod("getSystemContext").invoke(thread);

        final Class<?> adapterClass = Class.forName("android.nfc.NfcAdapter");
        final Object adapter = adapterClass.getMethod("getDefaultAdapter", Class.forName("android.content.Context"))
                .invoke(null, context);
        say("adapter=" + adapter + " enabled=" + adapterClass.getMethod("isEnabled").invoke(adapter));
        if (adapter == null) throw new IllegalStateException("no NfcAdapter");

        Thread worker = new Thread(() -> {
            int rc = 1;
            try {
                work(adapterClass, adapter, dwell, hold);
                rc = 0;
            } catch (Throwable t) {
                say("EXC " + t);
                t.printStackTrace(System.out);
            }
            System.exit(rc);
        }, "q13-worker");
        worker.start();
        looperClass.getMethod("loop").invoke(null);
    }

    private static void work(Class<?> adapterClass, Object adapter, long dwell, long hold) throws Exception {
        try {
            Class<?> cbClass = Class.forName("android.nfc.NfcAdapter$NfcVendorNciCallback");
            Object cb = Proxy.newProxyInstance(cbClass.getClassLoader(), new Class<?>[]{cbClass}, (p, m, a) -> {
                if (m.getName().equals("hashCode")) return System.identityHashCode(p);
                if (m.getName().equals("equals")) return p == a[0];
                if (m.getName().equals("toString")) return "Q13Callback";
                if (m.getName().equals("onVendorNciResponse")) {
                    int gid = (Integer) a[0];
                    int oid = (Integer) a[1];
                    byte[] r = (byte[]) a[2];
                    if (gid == 15 && oid == expectedOid) {
                        replies.offer(r == null ? new byte[0] : r.clone());
                    }
                }
                return null;
            });
            Executor exec = Executors.newSingleThreadExecutor();
            adapterClass.getMethod("registerNfcVendorNciCallback", Executor.class, cbClass)
                    .invoke(adapter, exec, cb);
            say("callback registered");
        } catch (Throwable t) {
            say("callback unavailable: " + t);
        }

        Object ext = adapterClass.getMethod("getNfcOemExtension").invoke(adapter);
        Method pause = ext.getClass().getMethod("pausePolling", long.class);
        Method resume = ext.getClass().getMethod("resumePolling");

        long paused = -1;
        for (int ms : PAUSE_CANDIDATES) {
            try {
                Object r = pause.invoke(ext, (long) ms);
                paused = ms;
                say("pause " + ms + " -> " + r + " (accepted)");
                break;
            } catch (Throwable t) {
                say("pause " + ms + " rejected: " + t);
            }
        }
        if (paused < 0) {
            say("pause unavailable; field not cut");
        } else if (dwell > 0) {
            say("field OFF for " + dwell + " ms");
            Thread.sleep(dwell);
        }

        Method send = adapterClass.getMethod("sendVendorNciMessage", int.class, int.class, int.class, byte[].class);
        byte[] cmd = new byte[]{(byte) 0x13, 0x01, 0x2A, 0x02, 0x0A, 0x7A};
        expectedOid = 1;
        int r = (Integer) send.invoke(adapter, 1, 15, 1, cmd);
        byte[] resp = replies.poll(2, TimeUnit.SECONDS);
        say("SEND " + hex(cmd) + " send=" + r + " resp=" + (resp == null ? "<timeout>" : hex(resp)));

        if (hold > 0) {
            say("HOLD " + hold + " ms; start the reader session in the app now");
            long end = System.currentTimeMillis() + hold;
            while (System.currentTimeMillis() < end) {
                long slice = Math.min(3000L, end - System.currentTimeMillis());
                if (slice > 0) Thread.sleep(slice);
                if (System.currentTimeMillis() >= end) break;
                // pausePolling 是“暂停 N 毫秒后自动恢复”，单次最多约 10 s；
                // 长窗口必须周期性续期，否则场会提前回来被系统抢卡。
                try { pause.invoke(ext, PAUSE_CANDIDATES[0]); }
                catch (Throwable t) { say("renew pause failed: " + t); }
            }
        }

        try {
            resume.invoke(ext);
            say("resume=ok");
        } catch (Throwable t) {
            say("resume EXC " + t);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02X", x & 255));
        return s.toString();
    }
}
