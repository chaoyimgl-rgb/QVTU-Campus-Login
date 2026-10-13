package cn.qvtu.campuslite;

public final class PortalPolicyTest {
    private static int checks;
    private static void expect(boolean result, boolean expected) {
        checks++;
        if (result != expected) throw new AssertionError("Policy case " + checks + " failed");
    }
    public static void main(String[] args) {
        String root = "http://192.168.120.156";
        expect(PortalPolicy.gateway(root + "/"), true);
        expect(PortalPolicy.gateway("https://192.168.120.156:443/"), true);
        for (String url : new String[]{"http://192.168.120.156.evil/", "http://192.168.120.156@evil/", "http://evil@192.168.120.156/", "file:///192.168.120.156", "javascript:alert(1)", "http://192.168.120.156:8080/", "http://192.168.120.157/", "http://192.168.120.156\\@evil/", "http://192.168.120.156./", "http://3232266396/", "http://[::1]/", "intent://192.168.120.156/", "http://192.168.120.156:443/"}) expect(PortalPolicy.gateway(url), false);
        expect(PortalPolicy.success(root + "/portal/entry/pc/finish", false), true);
        expect(PortalPolicy.success(root + "/portal/portal-main?userOnline=true", false), true);
        for (String path : new String[]{"/finish", "/portal/entry/unknown/finish", "/portal/portal-main", "/portal/portal-main?userOnline=false", "/portal/portal-main?userOnline=true&userOnline=false", "/portal/entry/pc/finish/", "/portal/entry/%70c/finish", "/portal/entry/mobile/finish"}) expect(PortalPolicy.success(root + path, false), false);
        expect(PortalPolicy.success(root + "/portal/entry/mobile/finish", true), true);
        expect(PortalPolicy.success(root + "/portal/entry/random/finish", true), false);
        expect(PortalPolicy.success("http://evil/portal/entry/pc/finish", true), false);
        expect(PortalPolicy.success(root + "/portal/entry/phone/finish", false), true);
        for (String url : new String[]{root + "/portal/entry/phone/finish/", root + "/portal/entry/%70hone/finish", "https://sid.qvtu.edu.cn/portal/entry/phone/finish", "http://123.123.123.123/portal/entry/phone/finish"}) expect(PortalPolicy.success(url, false), false);
        expect(PortalPolicy.sso("https://sid.qvtu.edu.cn/cas/login"), true);
        expect(PortalPolicy.sso("https://sid.qvtu.edu.cn:443/cas/login"), true);
        for (String url : new String[]{"http://sid.qvtu.edu.cn/cas/login", "https://sid.qvtu.edu.cn:444/cas/login", "https://u@sid.qvtu.edu.cn/cas/login", "https://sid.qvtu.edu.cn.evil/cas/login", "https://sid.qvtu.edu.cn./cas/login", "javascript:alert(1)"}) expect(PortalPolicy.sso(url), false);
        expect(PortalPolicy.credentialPage("https://sid.qvtu.edu.cn/cas/login"), true);
        for (String url : new String[]{"https://sid.qvtu.edu.cn/cas/login/", "http://123.123.123.123/", root + "/cas/login", "https://sid.qvtu.edu.cn/cas/%6cogin"}) expect(PortalPolicy.credentialPage(url), false);
        expect(PortalPolicy.transition("http://123.123.123.123/"), true);
        expect(PortalPolicy.transition("http://123.123.123.123:80/"), true);
        for (String url : new String[]{"https://123.123.123.123/", "http://123.123.123.123:8080/", "http://u@123.123.123.123/", "http://123.123.123.123/cas/login", "http://123.123.123.123.evil/"}) expect(PortalPolicy.transition(url), false);
        System.out.println("PASS: " + checks + " gateway, SSO, transition and success policy checks");
    }
}
