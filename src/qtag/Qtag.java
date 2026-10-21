package dev.probe;

import android.content.Context;
import android.nfc.NfcAdapter;
import android.os.Looper;

import java.lang.reflect.Proxy;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** QTag enable/disable via vendor NCI (gid=1, oid=0x0f, sub=0x70, payload 31 xx). */
public final class Qtag {
    private static final LinkedBlockingQueue<byte[]> replies = new LinkedBlockingQueue<>();
    private static volatile int wantGid = 1, wantOid = 15, wantSub = -1;

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

        new Thread(() -> {
            int rc = 1;
            try {
                rc = work(adapter);
            } catch (Throwable t) {
                say("EXC " + t);
                t.printStackTrace(System.out);
            }
            System.exit(rc);
        }, "qtag-worker").start();
        Looper.loop();
    }

    private static int work(NfcAdapter adapter) throws Exception {
        Class<?> cbClass = Class.forName("android.nfc.NfcAdapter$NfcVendorNciCallback");
        Object cb = Proxy.newProxyInstance(cbClass.getClassLoader(), new Class<?>[]{cbClass}, (p, m, a) -> {
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("equals")) return p == a[0];
            if (m.getName().equals("toString")) return "QtagCallback";
            if (m.getName().equals("onVendorNciResponse")) {
                int gid = (Integer) a[0];
                int oid = (Integer) a[1];
                byte[] r = (byte[]) a[2];
                say("RESP gid=" + gid + " oid=" + oid + " " + hex(r));
                replies.offer(r == null ? new byte[0] : r.clone());
            }
            return null;
        });
        NfcAdapter.class.getMethod("registerNfcVendorNciCallback", Executor.class, cbClass)
                .invoke(adapter, Executors.newSingleThreadExecutor(), cb);
        say("callback registered");

        java.lang.reflect.Method send = NfcAdapter.class.getMethod("sendVendorNciMessage",
                int.class, int.class, int.class, byte[].class);

        Object ext = NfcAdapter.class.getMethod("getNfcOemExtension").invoke(adapter);
        java.lang.reflect.Method pause = ext.getClass().getMethod("pausePolling", long.class);
        say("pause=" + pause.invoke(ext, 60000L));
        for (int action : new int[]{1, 0, 1}) {
            replies.clear();
            byte[] payload = new byte[]{0x31, (byte) action};
            int r = (Integer) send.invoke(adapter, 1, 15, 112, payload);
            byte[] resp = replies.poll(2, TimeUnit.SECONDS);
            say("QTAG " + (action == 1 ? "ON " : "OFF") + " send=" + r + " resp="
                    + (resp == null ? "<timeout>" : hex(resp)));
            Thread.sleep(500);
        }
        try { ext.getClass().getMethod("resumePolling").invoke(ext); say("resume=ok"); } catch (Throwable t) { say("resume EXC " + t); }
        return 0;
    }

    private static String hex(byte[] b) {
        if (b == null) return "null";
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02X", x & 255));
        return s.toString();
    }
}
