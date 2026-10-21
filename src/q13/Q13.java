package dev.probe;

import android.content.Context;
import android.nfc.NfcAdapter;
import android.os.Looper;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Send ST polling config (0x13 + 7A frame) and leave polling paused for a reader-mode app. */
public final class Q13 {
    private static final LinkedBlockingQueue<byte[]> replies = new LinkedBlockingQueue<>();
    private static volatile int expectedOid = -1;

    private static void say(String s) {
        System.out.println(s);
        System.out.flush();
    }

    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        Class<?> managerClass = Class.forName("android.nfc.NfcServiceManager");
        Class<?> initializer = Class.forName("android.nfc.NfcFrameworkInitializer");
        if (initializer.getMethod("getNfcServiceManager").invoke(null) == null) {
            initializer.getMethod("setNfcServiceManager", managerClass)
                    .invoke(null, managerClass.getConstructor().newInstance());
        }
        Class<?> threadClass = Class.forName("android.app.ActivityThread");
        Object thread = threadClass.getMethod("systemMain").invoke(null);
        Context context = (Context) threadClass.getDeclaredMethod("getSystemContext").invoke(thread);
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(context);
        say("adapter=" + adapter + " enabled=" + (adapter != null && adapter.isEnabled()));
        if (adapter == null) throw new IllegalStateException("no NfcAdapter");

        final Object[] result = new Object[1];
        Thread worker = new Thread(() -> {
            try {
                result[0] = work(adapter);
            } catch (Throwable t) {
                say("EXC " + t);
                t.printStackTrace(System.out);
                result[0] = 1;
            }
            System.exit((Integer) result[0]);
        }, "q13-worker");
        worker.start();
        Looper.loop();
    }

    private static int work(NfcAdapter adapter) throws Exception {
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
        NfcAdapter.class.getMethod("registerNfcVendorNciCallback", Executor.class, cbClass)
                .invoke(adapter, exec, cb);
        say("callback registered");

        Object ext = NfcAdapter.class.getMethod("getNfcOemExtension").invoke(adapter);
        Method pause = ext.getClass().getMethod("pausePolling", long.class);
        say("pause=" + pause.invoke(ext, 60000L));

        Method send = NfcAdapter.class.getMethod("sendVendorNciMessage", int.class, int.class, int.class, byte[].class);
        byte[] cmd = new byte[]{(byte) 0x13, 0x01, 0x2A, 0x02, 0x0A, 0x7A};
        expectedOid = 1;
        int r = (Integer) send.invoke(adapter, 1, 15, 1, cmd);
        byte[] resp = replies.poll(2, TimeUnit.SECONDS);
        say("SEND " + hex(cmd) + " send=" + r + " resp=" + (resp == null ? "<timeout>" : hex(resp)));
        try { ext.getClass().getMethod("resumePolling").invoke(ext); say("resume=ok"); } catch (Throwable t) { say("resume EXC " + t); }
        return 0;
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02X", x & 255));
        return s.toString();
    }
}
