package cn.qvtu.campuslite;

/** Monotonic intervals within one service lifetime; no claim of router reboot detection. */
final class Observation {
    long lastSuccessWall = -1, lastSuccessElapsed = -1;
    long firstFailureWall = -1, firstFailureElapsed = -1;
    long previousProbe = -1, maxGap;
    long wifiLostWall = -1, wifiLostElapsed = -1;
    long probeStarted(long elapsed, long interval) {
        long gap = previousProbe < 0 ? -1 : elapsed - previousProbe;
        previousProbe = elapsed;
        if (firstFailureElapsed >= 0 && gap >= 0) maxGap = Math.max(maxGap, gap);
        return gap;
    }
    long[] probe(boolean ok, long wall, long elapsed, long interval) {
        if (!ok) {
            if (firstFailureElapsed < 0) { firstFailureWall = wall; firstFailureElapsed = elapsed; maxGap = 0; }
            return new long[]{lastSuccessWall, firstFailureWall, -1, -1, -1};
        }
        long[] recovery = firstFailureElapsed < 0 ? null : new long[]{lastSuccessWall, firstFailureWall,
                elapsed - firstFailureElapsed, lastSuccessElapsed < 0 ? -1 : firstFailureElapsed - lastSuccessElapsed, maxGap};
        lastSuccessWall = wall; lastSuccessElapsed = elapsed;
        firstFailureWall = firstFailureElapsed = -1; maxGap = 0;
        return recovery;
    }
    void wifiLost(long wall, long elapsed) { wifiLostWall = wall; wifiLostElapsed = elapsed; }
    long[] wifiAvailable(long elapsed) {
        if (wifiLostElapsed < 0) return null;
        long[] result = {wifiLostWall, elapsed - wifiLostElapsed};
        wifiLostWall = wifiLostElapsed = -1;
        return result;
    }
    static long lateness(long now, long expected) { return expected < 0 ? -1 : Math.max(0, now - expected); }
}
