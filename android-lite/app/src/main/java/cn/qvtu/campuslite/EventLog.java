package cn.qvtu.campuslite;

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.concurrent.*;

/** Application-scoped, bounded queue, single file writer. No Activity/View retained. */
final class EventLog {
    @android.annotation.SuppressLint("StaticFieldLeak") // singleton retains application context only, never Activity
    private static EventLog instance;
    static synchronized EventLog get(Context context) {
        if (instance == null) instance = new EventLog(context.getApplicationContext());
        return instance;
    }
    private final Context app;
    private final ThreadPoolExecutor io;
    private final ArrayDeque<String> recent = new ArrayDeque<>();
    private RollingLog store;
    private volatile boolean failed;
    private long dropped;
    private EventLog(Context app) {
        this.app = app;
        io = new ThreadPoolExecutor(0, 1, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(128));
    }
    synchronized void event(RollingLog.Event event, long... values) {
        String line = RollingLog.line(event, System.currentTimeMillis(), SystemClock.elapsedRealtime(), values);
        // Enum + numeric fields only, including release; no remote diagnostic/credential entry point.
        android.util.Log.i("CampusGuard", line.trim());
        if (recent.size() == 20) recent.removeFirst();
        recent.addLast(line);
        long lost = dropped;
        try {
            io.execute(() -> {
                try {
                    if (store == null) store = new RollingLog(new File(app.getFilesDir(), "observations"));
                    if (lost > 0) store.append(RollingLog.line(RollingLog.Event.DROPPED, System.currentTimeMillis(), SystemClock.elapsedRealtime(), lost));
                    store.append(line);
                } catch (IOException e) { failed = true; }
            });
            dropped = 0;
        } catch (RejectedExecutionException e) { dropped++; failed = true; }
    }
    synchronized String recent() {
        StringBuilder s = new StringBuilder(failed ? "日志写入失败或队列丢弃；导出可能不完整\n" : "最近 20 条（本次进程；完整历史请导出）\n");
        for (String line : recent) s.append(line);
        return s.toString();
    }
    void export(Uri uri, java.util.function.Consumer<Boolean> done) {
        try {
            io.execute(() -> {
                boolean ok = false;
                try {
                    if (store == null) store = new RollingLog(new File(app.getFilesDir(), "observations"));
                    byte[] snapshot = store.snapshot(); // immutable snapshot on the same writer queue
                    try (OutputStream out = app.getContentResolver().openOutputStream(uri, "wt")) {
                        if (out == null) throw new IOException("no output");
                        out.write(snapshot); ok = true;
                    }
                } catch (IOException | RuntimeException e) { failed = true; }
                done.accept(ok);
            });
        } catch (RejectedExecutionException e) { done.accept(false); }
    }
}
