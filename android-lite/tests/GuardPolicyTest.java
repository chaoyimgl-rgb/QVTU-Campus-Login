package cn.qvtu.campuslite;

/** Fake network/time only: never connects or reads credentials. */
public final class GuardPolicyTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    public static void main(String[] args) {
        check(GuardPolicy.needsPortal(true, GuardPolicy.LOGIN)); // whitelisted Google
        check(GuardPolicy.needsPortal(true, GuardPolicy.UNKNOWN)); // HTML/JS redirect needs WebView
        check(!GuardPolicy.needsPortal(true, GuardPolicy.ONLINE));
        check(GuardPolicy.needsPortal(false, GuardPolicy.ONLINE));
        check(!GuardPolicy.verified(true, false)); // POST still pending or credentials rejected
        check(!GuardPolicy.verified(false, true));
        check(GuardPolicy.verified(true, true));
        check(GuardPolicy.portal("https://sid.qvtu.edu.cn/cas/login") == GuardPolicy.LOGIN);
        check(GuardPolicy.portal("http://192.168.120.156/portal/portal-main?userOnline=true") == GuardPolicy.ONLINE);
        check(GuardPolicy.portal("http://192.168.120.156/portal/entry/phone/finish") == GuardPolicy.UNKNOWN);
        check(GuardPolicy.portal("http://evil/portal/portal-main?userOnline=true") == GuardPolicy.UNKNOWN);
        check(GuardPolicy.portal("http://192.168.120.156/portal/portal-main?userOnline=true&userOnline=false") == GuardPolicy.UNKNOWN);
        // Fake transient post-submit failures keep scheduling, increasing bounded backoff.
        long now = 0;
        for (int failures = 1; failures <= 10; failures++) {
            long retry = now + GuardPolicy.retryDelay(failures);
            long next = GuardPolicy.nextDelay(true, false, false, 120000);
            check(next == 120000);
            check(retry > now && retry - now <= 900000);
            now += next;
        }
        check(GuardPolicy.retryDelay(1) == 60000);
        check(GuardPolicy.retryDelay(2) == 120000);
        check(GuardPolicy.retryDelay(5) == 900000);
        check(GuardPolicy.nextDelay(true, false, true, 120000) == -1); // explicit password error stops
        check(GuardPolicy.nextDelay(true, true, false, 120000) == -1);
        check(GuardPolicy.nextDelay(false, false, false, 120000) == -1);
        // Lost Wi-Fi invalidates the old callback; reconnect accepts only the new generation.
        check(!GuardPolicy.valid(true, true, 8, 7, true));
        check(!GuardPolicy.valid(true, true, 8, 8, false));
        check(GuardPolicy.valid(true, true, 9, 9, true));
        check(!GuardPolicy.valid(false, true, 9, 9, true));
        check(!GuardPolicy.valid(true, false, 9, 9, true));
        System.out.println("GuardPolicy: " + checks + " checks passed (fake network/time; no credentials)");
    }
}
