#pragma once
#include <string>
#include <vector>
struct WifiChoice { std::wstring ssid; int signal = 0; };
std::vector<WifiChoice> ListQvtuWifi(bool savedOnly);
bool IsWifiConnected(std::wstring& currentSsid);
bool EnableWifiRadio(std::wstring& error);
bool PrepareQvtuWifi(const std::wstring& fixedSsid, bool& connected, std::wstring& error);
