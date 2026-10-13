package cn.qvtu.campuslite;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Pure Java policy, shared by WebView and host-side boundary tests. */
final class PortalPolicy {
    static boolean gateway(String value) {
        try {
            URI uri = new URI(value);
            int port = uri.getPort();
            return "192.168.120.156".equals(uri.getHost()) && uri.getRawUserInfo() == null
                    && (("http".equals(uri.getScheme()) && (port == -1 || port == 80))
                    || ("https".equals(uri.getScheme()) && (port == -1 || port == 443)));
        } catch (Exception e) { return false; }
    }
    static boolean sso(String value) {
        try {
            URI u = new URI(value);
            return "https".equals(u.getScheme()) && "sid.qvtu.edu.cn".equals(u.getHost())
                    && u.getRawUserInfo() == null && (u.getPort() == -1 || u.getPort() == 443);
        } catch (Exception e) { return false; }
    }
    static boolean transition(String value) {
        try {
            URI u = new URI(value);
            return "http".equals(u.getScheme()) && "123.123.123.123".equals(u.getHost())
                    && u.getRawUserInfo() == null && (u.getPort() == -1 || u.getPort() == 80)
                    && "/".equals(u.getRawPath());
        } catch (Exception e) { return false; }
    }
    static boolean credentialPage(String value) {
        try { return sso(value) && "/cas/login".equals(new URI(value).getRawPath()); }
        catch (Exception e) { return false; }
    }
    static boolean success(String value, boolean experimental) {
        if (!gateway(value)) return false;
        try {
            URI uri = new URI(value);
            String path = uri.getRawPath();
            if ("/portal/entry/pc/finish".equals(path) || "/portal/entry/phone/finish".equals(path)) return true;
            if (experimental && "/portal/entry/mobile/finish".equals(path)) return true;
            if (!"/portal/portal-main".equals(path) || uri.getRawQuery() == null) return false;
            int count = 0;
            boolean online = false;
            for (String pair : uri.getRawQuery().split("&")) {
                String[] kv = pair.split("=", 2);
                if ("userOnline".equals(URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name()))) {
                    count++;
                    online = kv.length == 2 && "true".equals(URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()));
                }
            }
            return count == 1 && online;
        } catch (Exception e) { return false; }
    }
}
