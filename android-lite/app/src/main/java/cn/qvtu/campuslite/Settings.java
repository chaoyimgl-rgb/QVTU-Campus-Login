package cn.qvtu.campuslite;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class Settings {
    private static final String ALIAS = "campus-lite-password-v1";
    final SharedPreferences prefs;
    Settings(Context context) { prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE); }
    String account() { return prefs.getString("account", ""); }
    int interval() { int n = prefs.getInt("interval", 120); return n == 60 || n == 300 ? n : 120; }
    boolean enabled() { return prefs.getBoolean("enabled", false); }
    boolean experimental() { return prefs.getBoolean("experimental", false); }
    void enabled(boolean value) { prefs.edit().putBoolean("enabled", value).commit(); }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
    String password() throws Exception {
        String encoded = prefs.getString("cipher", "");
        if (encoded.isEmpty()) return "";
        byte[] iv = Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
    void save(String account, String newPassword, int interval, boolean experimental) throws Exception {
        SharedPreferences.Editor edit = prefs.edit();
        if (!newPassword.isEmpty()) {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            edit.putString("cipher", Base64.encodeToString(cipher.doFinal(newPassword.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP));
            edit.putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
        } else if (!account.equals(account())) {
            throw new IllegalArgumentException("更换账号时必须重新输入密码");
        }
        if (!edit.putString("account", account).putInt("interval", interval)
                .putBoolean("experimental", experimental).commit()) throw new Exception("保存失败");
    }
    void status(String text) { prefs.edit().putString("status", text).apply(); }
    String status() { return prefs.getString("status", "尚未开启守护"); }
}
