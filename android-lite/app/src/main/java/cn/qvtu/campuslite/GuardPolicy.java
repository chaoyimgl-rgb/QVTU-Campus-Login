package cn.qvtu.campuslite;

/** Pure decisions shared with deterministic fake-network regression tests. */
final class GuardPolicy {
    static final int UNKNOWN = 0, ONLINE = 1, LOGIN = 2;
    static int portal(String url) {
        if (PortalPolicy.credentialPage(url)) return LOGIN;
        // A finish page alone can be stale. Only a fresh root redirect with explicit online state counts.
        if (PortalPolicy.gateway(url) && url.contains("/portal/portal-main?") && PortalPolicy.success(url, false)) return ONLINE;
        return UNKNOWN;
    }
    static boolean needsPortal(boolean https204, int portal) {
        return !https204 || portal != ONLINE;
    }
    static boolean verified(boolean https204, boolean trustedSuccessPage) {
        return https204 && trustedSuccessPage;
    }
    static boolean valid(boolean alive, boolean busy, int generation, int token, boolean selected) {
        return alive && busy && generation == token && selected;
    }
    static long retryDelay(int failures) {
        return Math.min(900000L, 60000L * (1L << Math.min(Math.max(failures - 1, 0), 4)));
    }
    static long nextDelay(boolean enabled, boolean oneShot, boolean credentialError, long interval) {
        return enabled && !oneShot && !credentialError ? interval : -1;
    }
}
