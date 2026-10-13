package cn.qvtu.campuslite;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;

public final class MainActivity extends Activity {
    private cn.qvtu.campuslite.Settings config;
    private EditText account, password;
    private TextView status;
    private Spinner interval;
    private CheckBox experimental;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener listener = (p, key) -> {
        if ("status".equals(key) || "enabled".equals(key)) refresh();
    };
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        config = new cn.qvtu.campuslite.Settings(this);
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                && ("portal.diagnose".equals(getIntent().getAction()) || "portal.testOnce".equals(getIntent().getAction()))) {
            config.enabled(false);
            stopService(new Intent(this, GuardService.class));
            TextView diagnosticStatus = new TextView(this);
            boolean once = "portal.testOnce".equals(getIntent().getAction());
            diagnosticStatus.setText(once ? "单次认证测试：保留退避，不自动重试" : "正在运行无凭据门户诊断；不改变网络或保存设置");
            setContentView(diagnosticStatus);
            new android.os.Handler(getMainLooper()).postDelayed(() ->
                    startForegroundService(new Intent(this, GuardService.class).setAction(once ? GuardService.TEST_ONCE : GuardService.DIAGNOSE)), 1000);
            return;
        }
        getWindow().setStatusBarColor(Color.rgb(243, 246, 250));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(243, 246, 250));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20), dp(24), dp(20), dp(24)); scroll.addView(root);
        TextView title = text("校园网轻守护", 28); title.setTypeface(null, Typeface.BOLD); root.addView(title);
        root.addView(text("原生 Java · 系统 WebView · Wi-Fi 专用探测", 14));
        LinearLayout credentials = card(root);
        credentials.addView(text("账户设置", 20));
        account = new EditText(this); account.setHint("校园网账号"); account.setSingleLine(); account.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD); account.setText(config.account());
        account.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO); credentials.addView(account);
        password = new EditText(this); password.setHint("密码（留空保留已保存密码）"); password.setSingleLine(); password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO); password.setSaveEnabled(false); credentials.addView(password);
        credentials.addView(text("密码由 Android Keystore AES/GCM 加密保存，不回填、不输出日志。", 13));
        credentials.addView(text("探测间隔（秒）", 14));
        interval = new Spinner(this);
        interval.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"60", "120", "300"}));
        interval.setSelection(config.interval() == 60 ? 0 : config.interval() == 300 ? 2 : 1); credentials.addView(interval);
        experimental = new CheckBox(this); experimental.setText("试验：接受 /portal/entry/mobile/finish（未经真机验证）"); experimental.setChecked(config.experimental()); credentials.addView(experimental);
        button(credentials, "保存设置", () -> save(true));
        LinearLayout actions = card(root); actions.addView(text("连接守护", 20));
        status = text("", 15); actions.addView(status);
        button(actions, "开始守护", () -> {
            if (!GuardService.isRunning() && !save(false)) return;
            requestNotification();
            config.enabled(true);
            launch(GuardService.START);
        });
        button(actions, "停止守护", () -> {
            config.enabled(false);
            stopService(new Intent(this, GuardService.class));
            config.status("已停止守护"); refresh();
        });
        button(actions, "立即认证", () -> { if (GuardService.isRunning() || save(false)) { requestNotification(); launch(GuardService.NOW); } });
        LinearLayout system = card(root); system.addView(text("系统与限制", 20));
        button(system, "通知权限", this::requestNotification);
        button(system, "电池优化设置", () -> open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        button(system, "应用设置", () -> open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
        system.addView(text("不会自动连接 Wi-Fi 或改变热点。Wi-Fi 与热点能否并行取决于手机硬件。普通前台服务不保证 Doze 中及时检测；厂商可能限制后台、重启恢复。仅手动开启过守护才尝试开机恢复。尚未真机门户验证，不承诺半夜不断线。", 14));
        setContentView(scroll); refresh();
    }
    private TextView text(String value, int size) { TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(Color.rgb(32, 43, 61)); view.setPadding(0, dp(6), 0, dp(6)); return view; }
    private LinearLayout card(LinearLayout root) {
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(18), dp(14), dp(18), dp(14));
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.WHITE); background.setCornerRadius(dp(20)); panel.setBackground(background); panel.setElevation(dp(1));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2); layout.topMargin = dp(18); root.addView(panel, layout); return panel;
    }
    private void button(LinearLayout parent, String label, Runnable action) { Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setOnClickListener(v -> action.run()); parent.addView(b, new LinearLayout.LayoutParams(-1, -2)); }
    private boolean save(boolean message) {
        if (GuardService.isRunning()) {
            toast("请先停止守护，再修改或保存设置"); return false;
        }
        try {
            String name = account.getText().toString().trim();
            if (name.isEmpty()) { toast("请输入账号"); return false; }
            config.save(name, password.getText().toString(), Integer.parseInt(interval.getSelectedItem().toString()), experimental.isChecked());
            password.setText("");
            if (config.password().isEmpty()) { toast("请输入密码并保存"); return false; }
            if (message) toast("设置已保存"); return true;
        } catch (IllegalArgumentException e) { toast("更换账号时必须重新输入密码"); return false; }
        catch (Exception e) { toast("保存或解密失败，请重新输入密码；不显示敏感错误细节"); return false; }
    }
    private void launch(String action) {
        try { startForegroundService(new Intent(this, GuardService.class).setAction(action)); }
        catch (RuntimeException e) { config.enabled(false); config.status("系统拒绝启动前台服务，请检查应用设置"); refresh(); }
    }
    private void refresh() { if (status != null) status.setText((config.enabled() ? "守护已启用\n" : "守护未启用\n") + config.status()); }
    private void requestNotification() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        else toast("通知权限已具备；请允许显示常驻通知");
    }
    private void open(Intent intent) { try { startActivity(intent); } catch (RuntimeException e) { toast("系统未提供该设置入口"); } }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_LONG).show(); }
    @Override protected void onResume() { super.onResume(); config.prefs.registerOnSharedPreferenceChangeListener(listener); refresh(); }
    @Override protected void onPause() { config.prefs.unregisterOnSharedPreferenceChangeListener(listener); super.onPause(); }
}
