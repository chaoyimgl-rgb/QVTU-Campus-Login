package cn.qvtu.campuslite;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** Only enumerated events and numeric fields: arbitrary URLs/errors/credentials cannot enter. */
final class RollingLog {
    enum Event { MONITOR_START, MONITOR_STOP, WIFI_AVAILABLE, WIFI_LOST, WIFI_RECOVERY,
        PROBE_START, PROBE_RESULT, PROBE_CANCELLED, OUTAGE_START, OUTAGE_RECOVERY,
        AUTH_START, AUTH_SUCCESS, AUTH_FAILURE, BACKOFF, RESOURCES_RELEASED, DROPPED, PORTAL_RESULT, MONITOR_STATE, SCHEDULED, AUTH_SUBMITTED }
    static final int LIMIT = 256 * 1024, COUNT = 3;
    static final String HEADER = "# campus-observation TSV v1; local_time includes zone; epoch_ms; elapsed_ms (boot clock); event; numeric_values\n"
            + "# PROBE_START: expected_elapsed,actual_interval,lateness,configured_interval; PROBE_RESULT: ok,status,duration,phase(0=regular,1=verify)\n"
            + "# OUTAGE_START: last_success_epoch,first_failure_epoch; OUTAGE_RECOVERY: last_success_epoch,first_failure_epoch,observed_failed_ms,onset_uncertainty_ms,max_start_gap_ms\n"
            + "# WIFI_RECOVERY: lost_epoch,observed_lost_ms; -1=unknown; process/session gaps cannot establish reboot; probe target connectivitycheck.gstatic.com/generate_204\n";
    private final File directory;
    RollingLog(File directory) throws IOException {
        this.directory = directory;
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("log directory");
    }
    private File file(int index) { return new File(directory, "events-" + index + ".tsv"); }
    static String line(Event event, long wall, long elapsed, long... values) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ROOT);
        StringBuilder s = new StringBuilder(f.format(new Date(wall))).append('\t').append(wall)
                .append('\t').append(elapsed).append('\t').append(event.name());
        for (long value : values) s.append('\t').append(value);
        return s.append('\n').toString();
    }
    synchronized void append(String line) throws IOException {
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > LIMIT - HEADER.length()) throw new IOException("record too large");
        if (file(0).length() + bytes.length > LIMIT) {
            if (file(COUNT - 1).exists() && !file(COUNT - 1).delete()) throw new IOException("rotation delete");
            for (int i = COUNT - 2; i >= 0; i--) if (file(i).exists() && !file(i).renameTo(file(i + 1))) throw new IOException("rotation rename");
        }
        boolean header = file(0).length() == 0;
        try (OutputStream out = new FileOutputStream(file(0), true)) {
            if (header) out.write(HEADER.getBytes(StandardCharsets.UTF_8));
            out.write(bytes);
        }
    }
    synchronized byte[] snapshot() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = COUNT - 1; i >= 0; i--) if (file(i).exists()) {
            try (InputStream in = new FileInputStream(file(i))) {
                byte[] buffer = new byte[4096]; int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            }
        }
        if (out.size() == 0) out.write(HEADER.getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }
}
