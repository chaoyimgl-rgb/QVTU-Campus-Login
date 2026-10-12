#pragma once
#include <windows.h>
#include <string>

struct QvtuConfig {
    std::wstring username;
    std::wstring password;
    bool autostart = false;
    bool silentLogin = true;
    bool showSuccessPopup = false;
    bool showFailurePopup = true;
    bool connectCampusWifi = false;
    bool enableWifiRadio = true;
    bool fixedWifi = false;
    std::wstring fixedSsid;
};
std::wstring AppDir();
std::wstring LogPath();
void Log(const std::wstring& text);
bool LoadConfig(QvtuConfig& out);
bool SaveConfig(const QvtuConfig& config);
bool SetAutostart(bool enabled, const std::wstring& loginExe);
std::wstring LoginExePath();
std::wstring InstalledLoginExePath();
