package cn.qvtu.campuslite;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;

public final class ObservationTest {
    static int checks;
    static void check(boolean ok) { checks++; if (!ok) throw new AssertionError("check " + checks); }
    public static void main(String[] args) throws Exception {
        Observation o = new Observation();
        check(o.probeStarted(1000, 120000) == -1);
        check(o.probe(true, 1000000, 1000, 120000) == null);
        check(o.probeStarted(121000, 120000) == 120000);
        long[] fail = o.probe(false, 1120000, 121000, 120000);
        check(fail[0] == 1000000 && fail[1] == 1120000);
        o.probeStarted(841000, 120000);
        check(o.probe(false, 1840000, 841000, 120000)[1] == 1120000);
        o.probeStarted(961000, 120000);
        long[] recovery = o.probe(true, 1960000, 961000, 120000);
        check(recovery[2] == 840000 && recovery[3] == 120000 && recovery[4] == 720000);
        check(o.firstFailureElapsed == -1 && o.lastSuccessWall == 1960000);
        check(Observation.lateness(841000, 241000) == 600000);
        check(Observation.lateness(100, -1) == -1);
        o.wifiLost(2000000, 1000000);
        long[] wifi = o.wifiAvailable(1100000);
        check(wifi[0] == 2000000 && wifi[1] == 100000);
        check(o.wifiAvailable(1200000) == null);
        Observation unknown = new Observation();
        check(unknown.probe(false, 1, 1, 120000)[0] == -1);
        check(unknown.probe(true, 2, 2, 120000)[3] == -1);
        String line = RollingLog.line(RollingLog.Event.PROBE_RESULT, 0, 42, 0, 503, 1000, 0);
        check(line.contains("\t0\t42\tPROBE_RESULT\t0\t503\t1000\t0"));
        check(line.matches("(?s)^.*(?:Z|[+-]\\d\\d:\\d\\d)\\t.*"));
        check(!line.contains("password") && !line.contains("cookie") && !line.contains("SSID") && !line.contains("?"));
        // API cannot accept any arbitrary string fields.
        check(RollingLog.class.getDeclaredMethod("line", RollingLog.Event.class, long.class, long.class, long[].class) != null);
        Path dir = Files.createTempDirectory("campus-log-test");
        try {
            RollingLog log = new RollingLog(dir.toFile());
            log.append(line);
            byte[] before = log.snapshot();
            check(new String(before, StandardCharsets.UTF_8).startsWith("# campus-observation TSV v1"));
            for (int i = 0; i < 18000; i++) log.append(RollingLog.line(RollingLog.Event.PROBE_RESULT, i, i, 1, 204, 99, 0));
            long total = 0; int files = 0;
            try (java.util.stream.Stream<Path> paths = Files.list(dir)) {
                for (Path p : (Iterable<Path>)paths::iterator) { files++; long size = Files.size(p); check(size <= RollingLog.LIMIT); total += size; }
            }
            check(files == 3 && total <= 3L * RollingLog.LIMIT);
            String snapshot = new String(log.snapshot(), StandardCharsets.UTF_8);
            check(snapshot.contains("\t17999\t17999\tPROBE_RESULT"));
            check(!snapshot.contains("\t0\t42\tPROBE_RESULT"));
            check(new String(before, StandardCharsets.UTF_8).contains("\t0\t42\tPROBE_RESULT"));
            Thread writer = new Thread(() -> { try { for (int i = 0; i < 100; i++) log.append(line); } catch (Exception e) { throw new RuntimeException(e); } });
            writer.start();
            for (int i = 0; i < 20; i++) check(new String(log.snapshot(), StandardCharsets.UTF_8).endsWith("\n"));
            writer.join();
        } finally { try (java.util.stream.Stream<Path> paths = Files.walk(dir)) { for (Path p : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(p); } }
        System.out.println("Observation / rolling log: " + checks + " checks passed");
    }
}
