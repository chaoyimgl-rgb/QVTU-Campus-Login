package cn.qvtu.campuslite;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        Settings settings = new Settings(context);
        if (!settings.enabled()) return;
        try { context.startForegroundService(new Intent(context, GuardService.class).setAction(GuardService.START)); }
        catch (RuntimeException e) { settings.status("系统限制开机恢复，请打开应用手动开始守护"); }
    }
}
