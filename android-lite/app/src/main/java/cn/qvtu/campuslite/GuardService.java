package cn.qvtu.campuslite;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.*;
import android.net.http.SslError;
import android.os.*;
import android.webkit.*;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.concurrent.*;
import java.io.ByteArrayInputStream;

public final class GuardService extends Service {
    static final String START = "guard.start", STOP = "guard.stop", NOW = "guard.now", DIAGNOSE = "guard.diagnose";
    private boolean diagnostic, testOnce, verifying, authenticating;
    private EventLog log;
    private final Observation observation = new Observation();
    private long expectedTick = -1, probeStart = -1;
    private void event(RollingLog.Event e, long... v) { log.event(e, v); }
    private void selectedAvailable() {
        event(RollingLog.Event.WIFI_AVAILABLE);
        long[] recovery = observation.wifiAvailable(SystemClock.elapsedRealtime());
        if (recovery != null) event(RollingLog.Event.WIFI_RECOVERY, recovery);
    }
    private void probeResult(boolean ok, int code, long started, int phase) {
        long now = SystemClock.elapsedRealtime();
        event(RollingLog.Event.PROBE_RESULT, ok ? 1 : 0, code, now - started, phase);
        boolean first = observation.firstFailureElapsed < 0;
        long[] range = observation.probe(ok, System.currentTimeMillis(), now, settings.interval() * 1000L);
        if (range != null && (ok || first)) event(ok ? RollingLog.Event.OUTAGE_RECOVERY : RollingLog.Event.OUTAGE_START, range);
        probeStart = -1;
    }
    static final String TEST_ONCE = "guard.testOnce";
    private static String safeUrl(Uri uri) { return uri == null ? "unknown" : uri.getHost() + (uri.getPath() == null ? "" : uri.getPath().split(";", 2)[0]); }
    private void trace(String text) { if (BuildConfig.DEBUG && (diagnostic || testOnce)) android.util.Log.i("PortalDiag", text); }
    private boolean allowed(Uri uri) {
        return uri != null && (gateway(uri) || PortalPolicy.sso(uri.toString()));
    }
    private static volatile boolean running;
    static boolean isRunning() { return running; }
    private static final String HOST = "192.168.120.156", ENTRY = "http://192.168.120.156/";
    private static final int ID = 1;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LinkedHashSet<Network> wifi = new LinkedHashSet<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback callback;
    private Settings settings;
    private Network selected;
    private WebView web;
    private Presentation webWindow;
    private android.hardware.display.VirtualDisplay webDisplay;
    private android.graphics.SurfaceTexture webTexture;
    private android.view.Surface webSurface;
    private PowerManager.WakeLock wake;
    private volatile HttpURLConnection connection;
    private Future<?> work;
    private volatile boolean alive;
    private boolean busy, bound, submitted, oneShot, pendingForce;
    private volatile int generation;
    private int failures;
    private long nextAllowed;
    private Runnable tick, poll, authTimeout, waitTimeout;

    @Override public void onCreate() {
        super.onCreate();
        settings = new Settings(this);
        log = EventLog.get(this);
        event(RollingLog.Event.MONITOR_START, settings.interval());
        cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        failures = settings.prefs.getInt("failures", 0);
        long savedDelay = Math.max(0, Math.min(900000L, settings.prefs.getLong("retryAfter", 0) - System.currentTimeMillis()));
        nextAllowed = SystemClock.elapsedRealtime() + savedDelay;
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("guard", "校园网守护", NotificationManager.IMPORTANCE_LOW));
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification("正在启动"), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(ID, notification("正在启动"));
        alive = true;
        running = true;
    }
    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, GuardService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, "guard").setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("校园网轻守护").setContentText(text).setContentIntent(open)
                .setOngoing(true).addAction(new Notification.Action.Builder(null, "停止", stop).build()).build();
    }
    private void status(String text) {
        settings.status(text);
        if (alive) getSystemService(NotificationManager.class).notify(ID, notification(text));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? START : intent.getAction();
        if (!BuildConfig.DEBUG && (DIAGNOSE.equals(action) || TEST_ONCE.equals(action))) { shutdown("发布版不支持诊断入口"); return START_NOT_STICKY; }
        if (STOP.equals(action)) { settings.enabled(false); shutdown("已停止守护"); return START_NOT_STICKY; }
        if (START.equals(action) && !settings.enabled()) { shutdown("守护未启用"); return START_NOT_STICKY; }
        diagnostic = BuildConfig.DEBUG && DIAGNOSE.equals(action);
        testOnce = BuildConfig.DEBUG && TEST_ONCE.equals(action);
        trace("session start mode=" + (diagnostic ? "diagnose" : testOnce ? "testOnce" : "guard"));
        oneShot = diagnostic || testOnce || !settings.enabled();
        event(RollingLog.Event.MONITOR_STATE, settings.enabled() ? 1 : 0, oneShot ? 1 : 0,
                busy ? 1 : 0, Math.max(0, nextAllowed - SystemClock.elapsedRealtime()), settings.interval());
        if (NOW.equals(action)) pendingForce = true;
        if (callback == null) register();
        if (selected == null) {
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities c = cm.getNetworkCapabilities(n);
                if (c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) { wifi.add(n); selected = n; selectedAvailable(); break; }
            }
        }
        if (!alive) return START_NOT_STICKY;
        // A force request received during a read-only probe is consumed by its completion.
        if (busy && authenticating) pendingForce = false;
        if (NOW.equals(action)) {
            if (SystemClock.elapsedRealtime() < nextAllowed) {
                status("失败退避中，请稍后重试；不会绕过账号保护");
                if (!busy) {
                    if (oneShot) shutdown("单次操作未提交：仍在失败退避中");
                    else schedule(settings.interval() * 1000L);
                }
            }
            else if (!busy) check(true);
        } else if (!busy) check(false);
        return oneShot ? START_NOT_STICKY : START_STICKY;
    }
    private void register() {
        callback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                if (!alive) return;
                wifi.add(network);
                if (selected == null) { selected = network; selectedAvailable(); if (!busy) check(false); }
            }
            @Override public void onLost(Network network) {
                if (!alive) return;
                wifi.remove(network);
                if (network.equals(selected)) {
                    event(RollingLog.Event.WIFI_LOST);
                    observation.wifiLost(System.currentTimeMillis(), SystemClock.elapsedRealtime());
                    cancelCycle();
                    selected = wifi.isEmpty() ? null : wifi.iterator().next();
                    status("Wi-Fi 已丢失，等待可用 Wi-Fi（不自动连接）");
                    if (selected != null) { selectedAvailable(); schedule(1000); }
                    else if (oneShot) waitForWifi();
                }
            }
        };
        try {
            cm.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback, main);
        } catch (RuntimeException e) { settings.enabled(false); shutdown("无法监听 Wi-Fi，请检查系统限制后重新开始"); }
    }
    private void waitForWifi() {
        if (waitTimeout != null) main.removeCallbacks(waitTimeout);
        waitTimeout = () -> { if (alive && selected == null && oneShot) shutdown("未找到可用 Wi-Fi，立即认证已结束"); };
        main.postDelayed(waitTimeout, 45000);
    }
    private void schedule(long delay) {
        if (!alive) return;
        if (tick != null) main.removeCallbacks(tick);
        expectedTick = SystemClock.elapsedRealtime() + delay;
        event(RollingLog.Event.SCHEDULED, delay, expectedTick);
        tick = () -> check(false);
        main.postDelayed(tick, delay);
    }
    private void check(boolean forceAuth) {
        if (!alive || busy) return;
        if (selected == null) { status("等待 Wi-Fi；无需公网 VALIDATED 状态"); if (oneShot) waitForWifi(); return; }
        if (tick != null) main.removeCallbacks(tick);
        if (waitTimeout != null) main.removeCallbacks(waitTimeout);
        busy = true;
        forceAuth = forceAuth || pendingForce;
        pendingForce = false;
        int token = ++generation;
        Network network = selected;
        status(forceAuth ? "准备立即认证" : "通过所选 Wi-Fi 检测公网");
        if (forceAuth || diagnostic) { authenticate(token, network); return; }
        final long started = SystemClock.elapsedRealtime();
        probeStart = started;
        event(RollingLog.Event.PROBE_START, expectedTick, observation.probeStarted(started, settings.interval() * 1000L), Observation.lateness(started, expectedTick), settings.interval() * 1000L);
        expectedTick = -1;
        work = io.submit(() -> {
            int code = -1;
            boolean online = false;
            HttpURLConnection local = null;
            try {
                if (!alive || generation != token || Thread.currentThread().isInterrupted()) return;
                local = (HttpURLConnection) network.openConnection(new URL("https://connectivitycheck.gstatic.com/generate_204"));
                connection = local;
                if (!alive || generation != token || Thread.currentThread().isInterrupted()) return;
                local.setInstanceFollowRedirects(false);
                local.setConnectTimeout(8000); local.setReadTimeout(8000);
                local.setUseCaches(false);
                code = local.getResponseCode(); online = code == 204;
            } catch (Exception ignored) {
                // A failed HTTPS probe may mean a captive portal, or an unavailable probe endpoint.
            } finally {
                if (local != null) local.disconnect();
                if (connection == local) connection = null;
            }
            final boolean result = online;
            final int responseCode = code;
            final int portal = portalState(token, network);
            main.post(() -> {
                if (!valid(token, network)) return;
                work = null;
                event(RollingLog.Event.PORTAL_RESULT, portal);
                probeResult(result && portal != GuardPolicy.LOGIN, responseCode, started, 0);
                if (pendingForce || GuardPolicy.needsPortal(result, portal)) {
                    pendingForce = false;
                    authenticate(token, network);
                } else finish(true, "所选 Wi-Fi 204 与门户实时在线状态均通过；未提交凭据", false);
            });
        });
    }
    /** Bounded GET-only redirect walk; never log URLs, bodies or cookies, never send credentials. */
    private int portalState(int token, Network network) {
        String target = ENTRY;
        for (int hop = 0; hop < 5; hop++) {
            if (!alive || generation != token || Thread.currentThread().isInterrupted()) return GuardPolicy.UNKNOWN;
            if (!(PortalPolicy.gateway(target) || PortalPolicy.sso(target) || PortalPolicy.transition(target))) return GuardPolicy.UNKNOWN;
            int known = GuardPolicy.portal(target);
            if (known != GuardPolicy.UNKNOWN) return known;
            HttpURLConnection local = null;
            try {
                local = (HttpURLConnection) network.openConnection(new URL(target));
                connection = local;
                local.setInstanceFollowRedirects(false); local.setUseCaches(false);
                local.setRequestProperty("Cache-Control", "no-cache, no-store");
                local.setRequestProperty("Pragma", "no-cache");
                local.setConnectTimeout(4000); local.setReadTimeout(4000);
                int code = local.getResponseCode();
                if (code != 301 && code != 302 && code != 303 && code != 307 && code != 308) return GuardPolicy.UNKNOWN;
                String location = local.getHeaderField("Location");
                if (location == null) return GuardPolicy.UNKNOWN;
                target = new URL(new URL(target), location).toString();
            } catch (Exception ignored) { return GuardPolicy.UNKNOWN; }
            finally { if (local != null) local.disconnect(); if (connection == local) connection = null; }
        }
        return GuardPolicy.UNKNOWN;
    }
    private boolean valid(int token, Network network) { return GuardPolicy.valid(alive, busy, generation, token, network.equals(selected)); }
    static boolean gateway(Uri uri) { return uri != null && PortalPolicy.gateway(uri.toString()); }
    static boolean success(Uri uri, boolean experimental) { return uri != null && PortalPolicy.success(uri.toString(), experimental); }
    private void authenticate(int token, Network network) {
        if (!valid(token, network)) return;
        long remaining = diagnostic ? 0 : nextAllowed - SystemClock.elapsedRealtime();
        if (remaining > 0) {
            status("认证失败退避中；继续低频 Wi-Fi 探测，剩余约 " + ((remaining + 999) / 1000) + " 秒");
            event(RollingLog.Event.BACKOFF, remaining, nextAllowed);
            cancelCycle();
            if (oneShot) shutdown("单次操作未提交：仍在失败退避中"); else schedule(settings.interval() * 1000L);
            return;
        }
        final String account, password;
        try {
            account = diagnostic ? "" : settings.account(); password = diagnostic ? "" : settings.password();
            if (!diagnostic && (account.isEmpty() || password.isEmpty())) { fatal("请先保存账号和密码"); return; }
        } catch (Exception e) { fatal("密码解密失败，请重新输入并保存密码"); return; }
        authenticating = true;
        event(RollingLog.Event.AUTH_START);
        try {
            wake = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CampusLite:authentication");
            wake.acquire(60000);
            if (!cm.bindProcessToNetwork(network)) { finish(false, "无法短暂绑定所选 Wi-Fi", false); return; }
            bound = true;
            CookieManager.getInstance().setAcceptCookie(true);
            ServiceWorkerController.getInstance().getServiceWorkerWebSettings().setBlockNetworkLoads(true);
            webTexture = new android.graphics.SurfaceTexture(false);
            webTexture.setDefaultBufferSize(480, 800);
            webSurface = new android.view.Surface(webTexture);
            webDisplay = getSystemService(android.hardware.display.DisplayManager.class).createVirtualDisplay(
                    "CampusLitePortal", 480, 800, 160, webSurface,
                    android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                            | android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION);
            if (webDisplay == null) throw new IllegalStateException("No private display");
            webWindow = new Presentation(this, webDisplay.getDisplay());
            web = new WebView(webWindow.getContext());
            webWindow.setContentView(web);
            webWindow.show();
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
            WebSettings ws = web.getSettings();
            ws.setJavaScriptEnabled(true); ws.setDomStorageEnabled(true);
            ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
            ws.setAllowFileAccess(false); ws.setAllowContentAccess(false);
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            ws.setJavaScriptCanOpenWindowsAutomatically(false); ws.setSupportMultipleWindows(false);
            web.setWebViewClient(new WebViewClient() {
                private void reject() { main.post(() -> { if (valid(token, network)) finish(false, "已阻止非网关请求或跨主机跳转，未向其他主机提交凭据", false); }); }
                @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    if (!submitted && request.isForMainFrame() && "GET".equals(request.getMethod()) && PortalPolicy.transition(request.getUrl().toString())) return false;
                    if (!allowed(request.getUrl())) {
                        trace("blocked navigation main=" + request.isForMainFrame() + " " + safeUrl(request.getUrl()));
                        if (!diagnostic) reject();
                        return true;
                    } return false;
                }
                @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    if (diagnostic) trace("resource " + (request.isForMainFrame() ? "main " : "sub ") + safeUrl(request.getUrl()));
                    if (!allowed(request.getUrl()) && !( !submitted && request.isForMainFrame() && "GET".equals(request.getMethod()) && PortalPolicy.transition(request.getUrl().toString()))) {
                        trace("blocked " + (request.isForMainFrame() ? "main " : "resource ") + safeUrl(request.getUrl()));
                        if (!diagnostic || request.isForMainFrame()) reject();
                        return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", java.util.Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
                    }
                    return null;
                }
                @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                    trace("started " + safeUrl(Uri.parse(url)));
                    if (!allowed(Uri.parse(url)) && !(!submitted && PortalPolicy.transition(url))) reject();
                }
                @Override public void onPageFinished(WebView view, String url) {
                    trace("finished " + safeUrl(Uri.parse(url)));
                }
                @Override public void onReceivedHttpError(WebView view, WebResourceRequest req, WebResourceResponse response) {
                    trace("httpError status=" + response.getStatusCode() + " main=" + req.isForMainFrame() + " " + safeUrl(req.getUrl()));
                }
                @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                    handler.cancel(); main.post(() -> { if (valid(token, network)) finish(false, "网关 TLS 证书校验失败，未绕过证书验证", false); });
                }
                @Override public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError error) {
                    trace("loadError code=" + error.getErrorCode() + " main=" + req.isForMainFrame() + " " + safeUrl(req.getUrl()));
                    if (req.isForMainFrame() && valid(token, network)) finish(false, "网关页面加载失败", false);
                }
            });
            submitted = false;
            if (BuildConfig.DEBUG && diagnostic) readOnlyProbes(token, network);
            status("系统 WebView 正在加载；精确网关及学校 HTTPS SSO");
            authTimeout = () -> { if (valid(token, network)) finish(false, "认证超时；手机门户结构可能不同", false); };
            main.postDelayed(authTimeout, 45000);
            poll = new Runnable() {
                @Override public void run() {
                    if (!valid(token, network) || web == null) return;
                    String url = web.getUrl();
                    if (url != null && !allowed(Uri.parse(url)) && !(!submitted && PortalPolicy.transition(url))) { finish(false, "已阻止非可信页面", false); return; }
                    if (BuildConfig.DEBUG && diagnostic) {
                        web.evaluateJavascript("(function(){return JSON.stringify({host:location.hostname,knownOnline:" + "(location.hostname==='192.168.120.156'&&new URLSearchParams(location.search).getAll('userOnline').length===1&&new URLSearchParams(location.search).get('userOnline')==='true'),path:location.pathname.split(';')[0],title:document.title.slice(0,80),phase:document.readyState,forms:Array.from(document.forms).map(function(e){var u=new URL(e.action,location.href);return {host:u.hostname,path:u.pathname.split(';')[0],method:e.method};}),frames:Array.from(document.querySelectorAll('iframe')).map(function(e){var u=new URL(e.src,location.href);var r={host:u.hostname,path:u.pathname.split(';')[0]};try{r.inputs=Array.from(e.contentDocument.querySelectorAll('input')).map(function(x){return {id:x.id,type:x.type,placeholder:x.placeholder};});r.buttons=Array.from(e.contentDocument.querySelectorAll('button')).map(function(x){return x.innerText.slice(0,40);});}catch(ignore){r.inaccessible=true;}return r;}),inputs:Array.from(document.querySelectorAll('input')).map(function(e){return {id:e.id,type:e.type,placeholder:e.placeholder};}),buttons:Array.from(document.querySelectorAll('button')).map(function(e){return e.innerText.slice(0,40);})});})()", value -> {
                            if (!valid(token, network)) return;
                            trace("structure " + value);
                            main.postDelayed(this, 5000);
                        });
                        return;
                    }
                    if (url != null && success(Uri.parse(url), settings.experimental()) && !verifying) verifyOnline(token, network);
                    String script = "(function(){"
                            + "var t=(document.body?document.body.innerText:'');if(/密码错误|用户不存在|用户名或密码错误|账号或密码错误/.test(t))return 'credentials';if(/登录失败|认证失败/.test(t))return 'transient';"
                            + (submitted ? "return 'waiting';" :
                            "if(location.origin!=='https://sid.qvtu.edu.cn'&&location.origin!=='https://sid.qvtu.edu.cn:443')return 'waiting';if(location.pathname!=='/cas/login')return 'waiting';"
                            + "var n=document.querySelector('#nameInput'),p=document.querySelector('input[type=password]');"
                            + "var b=Array.from(document.querySelectorAll('button')).find(function(x){var t=x.innerText.replace(/\\s/g,'');return t==='立即登录'||t==='登录';});"
                            + "if(!n||!p||!b||b.disabled)return 'waiting';"
                            + "var f=b.form||n.form;if(!f||p.form!==f)return 'blocked';var u=new URL(f.action,location.href);if(u.origin!==location.origin||u.pathname!=='/cas/login'||u.username||u.password||f.method.toLowerCase()!=='post')return 'blocked';"
                            + "var m=document.createElement('meta');m.httpEquiv='Content-Security-Policy';m.content=\"default-src 'self' 'unsafe-inline' 'unsafe-eval' data:; connect-src 'self'; form-action 'self' http://192.168.120.156/cas-sso/login; frame-src 'none'; worker-src 'none'; object-src 'none'; base-uri 'self'\";document.head.appendChild(m);"
                            + "var s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;"
                            + "function put(e,v){s.call(e,v);e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));}"
                            + "put(n," + JSONObject.quote(account) + ");put(p," + JSONObject.quote(password) + ");b.click();return 'submitted';")
                            + "})()";
                    web.evaluateJavascript(script, value -> {
                        if (!valid(token, network)) return;
                        if ("\"credentials\"".equals(value)) { fatal("门户报告密码错误/用户不存在：已停止提交，请检查凭据后手动开始"); return; }
                        if ("\"transient\"".equals(value)) { finish(false, "门户报告登录或认证失败（未明确密码错误）", false); return; }
                        if ("\"blocked\"".equals(value)) { finish(false, "页面或表单目标不符合网关限制，已取消", false); return; }
                        if ("\"submitted\"".equals(value)) { submitted = true; event(RollingLog.Event.AUTH_SUBMITTED); trace("submitted once to sid.qvtu.edu.cn/cas/login"); }
                        main.postDelayed(this, 750);
                    });
                }
            };
            web.loadUrl(ENTRY);
            main.postDelayed(poll, 750);
        } catch (RuntimeException e) { finish(false, "系统 WebView 或网络绑定不可用", false); }
    }
    private void readOnlyProbes(int token, Network network) {
        work = io.submit(() -> {
            for (String endpoint : new String[]{"https://connectivitycheck.gstatic.com/generate_204", "https://www.microsoft.com/", "https://www.baidu.com/"}) {
                if (!alive || generation != token || Thread.currentThread().isInterrupted()) return;
                HttpURLConnection local = null;
                try {
                    local = (HttpURLConnection) network.openConnection(new URL(endpoint)); connection = local;
                    local.setInstanceFollowRedirects(false); local.setUseCaches(false);
                    local.setConnectTimeout(5000); local.setReadTimeout(5000);
                    int code = local.getResponseCode();
                    trace("readOnly wifi https " + safeUrl(Uri.parse(endpoint)) + " status=" + code);
                } catch (Exception e) {
                    trace("readOnly wifi https " + safeUrl(Uri.parse(endpoint)) + " error=" + e.getClass().getSimpleName());
                } finally { if (local != null) local.disconnect(); if (connection == local) connection = null; }
            }
            main.post(() -> {
                if (!valid(token, network)) return;
                work = null;
                NetworkCapabilities c = cm.getNetworkCapabilities(network);
                trace("readOnly WIFI=" + (c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) + " VALIDATED=" + (c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)));
            });
        });
    }
    private void verifyOnline(int token, Network network) {
        verifying = true;
        final long started = SystemClock.elapsedRealtime();
        probeStart = started;
        event(RollingLog.Event.PROBE_START, expectedTick, observation.probeStarted(started, settings.interval() * 1000L), Observation.lateness(started, expectedTick), settings.interval() * 1000L);
        expectedTick = -1;
        work = io.submit(() -> {
            int code = -1;
            boolean online = false;
            HttpURLConnection local = null;
            try {
                local = (HttpURLConnection) network.openConnection(new URL("https://connectivitycheck.gstatic.com/generate_204"));
                connection = local;
                local.setInstanceFollowRedirects(false); local.setUseCaches(false);
                local.setConnectTimeout(5000); local.setReadTimeout(5000);
                code = local.getResponseCode(); online = code == 204;
            } catch (Exception ignored) {
            } finally { if (local != null) local.disconnect(); if (connection == local) connection = null; }
            final boolean result = online;
            final int responseCode = code;
            main.post(() -> {
                if (!valid(token, network)) return;
                work = null;
                probeResult(result, responseCode, started, 1);
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                boolean validated = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                trace("post-submit wifi probe204=" + result + " VALIDATED=" + validated + " page=" + safeUrl(web == null ? null : Uri.parse(web.getUrl())));
                boolean confirmed = GuardPolicy.verified(result, web != null && success(Uri.parse(web.getUrl()), settings.experimental()));
                if (confirmed) finish(true, "所选 Wi-Fi 公网 204 与可信门户成功状态均通过；系统 VALIDATED=" + validated, false);
                else main.postDelayed(() -> { if (valid(token, network)) verifying = false; }, 3000);
            });
        });
    }
    private void fatal(String text) { if (authenticating) { event(RollingLog.Event.AUTH_FAILURE); authenticating = false; } settings.enabled(false); shutdown(text); }
    private void finish(boolean ok, String text, boolean unused) {
        if (diagnostic) { trace("diagnostic complete; no credentials submitted"); shutdown("脱敏诊断完成；未读取或提交凭据；自动认证暂停，设置保留"); return; }
        trace("session end submitted=" + submitted + " ok=" + ok + " page=" + safeUrl(web == null || web.getUrl() == null ? null : Uri.parse(web.getUrl())));
        if (authenticating) { event(ok ? RollingLog.Event.AUTH_SUCCESS : RollingLog.Event.AUTH_FAILURE); authenticating = false; }
        cancelCycle();
        if (ok) { failures = 0; nextAllowed = 0; }
        else {
            failures = Math.min(failures + 1, 10);
            long backoff = GuardPolicy.retryDelay(failures);
            nextAllowed = SystemClock.elapsedRealtime() + backoff;
            event(RollingLog.Event.BACKOFF, backoff, nextAllowed);
            text += "；失败退避 " + backoff / 1000 + " 秒";
        }
        settings.prefs.edit().putInt("failures", failures)
                .putLong("retryAfter", ok ? 0 : System.currentTimeMillis() + Math.max(0, nextAllowed - SystemClock.elapsedRealtime())).commit();
        status(text);
        long delay = GuardPolicy.nextDelay(settings.enabled(), oneShot, false, settings.interval() * 1000L);
        if (delay < 0) { shutdown(text); return; }
        // Transient network/portal failures retain the monitor and bounded backoff.
        // Only explicit credential errors (fatal above) disable future submissions.
        schedule(delay);
    }
    private void cancelCycle() {
        if (probeStart >= 0) { event(RollingLog.Event.PROBE_CANCELLED, SystemClock.elapsedRealtime() - probeStart); probeStart = -1; }
        if (authenticating) { event(RollingLog.Event.AUTH_FAILURE); authenticating = false; }
        generation++; busy = false; verifying = false;
        if (tick != null) main.removeCallbacks(tick);
        if (poll != null) main.removeCallbacks(poll);
        if (authTimeout != null) main.removeCallbacks(authTimeout);
        if (waitTimeout != null) main.removeCallbacks(waitTimeout);
        if (work != null) { work.cancel(true); work = null; }
        HttpURLConnection active = connection; if (active != null) active.disconnect();
        try {
            if (web != null) {
                WebView old = web; web = null;
                try { old.stopLoading(); old.setWebViewClient(new WebViewClient()); old.clearHistory(); }
                finally { old.destroy(); }
            }
        } finally {
            if (webWindow != null) { webWindow.dismiss(); webWindow = null; }
            if (webDisplay != null) { webDisplay.release(); webDisplay = null; }
            if (webSurface != null) { webSurface.release(); webSurface = null; }
            if (webTexture != null) { webTexture.release(); webTexture = null; }
            try { if (bound) cm.bindProcessToNetwork(null); }
            finally { bound = false; if (wake != null && wake.isHeld()) wake.release(); wake = null; }
        }
        event(RollingLog.Event.RESOURCES_RELEASED);
    }
    private void shutdown(String text) {
        event(RollingLog.Event.MONITOR_STOP);
        alive = false;
        running = false;
        cancelCycle();
        if (callback != null) { try { cm.unregisterNetworkCallback(callback); } catch (RuntimeException ignored) {} callback = null; }
        wifi.clear(); selected = null;
        main.removeCallbacksAndMessages(null);
        io.shutdownNow();
        settings.status(text);
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    @Override public void onDestroy() { if (alive) shutdown("服务已结束；系统可能回收服务"); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
