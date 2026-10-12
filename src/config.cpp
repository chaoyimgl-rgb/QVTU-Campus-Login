#include "config.h"
#include <shlobj.h>
#include <wincrypt.h>
#include <fstream>
#include <filesystem>
#include <vector>
#include <algorithm>
#include <shobjidl.h>
#include <wrl.h>
using Microsoft::WRL::ComPtr;
#pragma comment(lib, "crypt32.lib")
#pragma comment(lib, "ole32.lib")
namespace fs = std::filesystem;
static std::wstring ConfigPath() { return AppDir() + L"\\config.dat"; }
static std::wstring B64(const BYTE* data, DWORD size) { DWORD n=0; CryptBinaryToStringW(data,size,CRYPT_STRING_BASE64|CRYPT_STRING_NOCRLF,nullptr,&n); std::wstring s(n,L'\0'); CryptBinaryToStringW(data,size,CRYPT_STRING_BASE64|CRYPT_STRING_NOCRLF,s.data(),&n); s.resize(n); return s; }
static std::vector<BYTE> UnB64(const std::wstring& s) { DWORD n=0; CryptStringToBinaryW(s.c_str(),0,CRYPT_STRING_BASE64,nullptr,&n,nullptr,nullptr); std::vector<BYTE> b(n); if(!b.empty()) CryptStringToBinaryW(s.c_str(),0,CRYPT_STRING_BASE64,b.data(),&n,nullptr,nullptr); b.resize(n); return b; }
static std::wstring Protect(const std::wstring& value) { DATA_BLOB in{(DWORD)(value.size()*sizeof(wchar_t)),(BYTE*)value.data()}, out{}; if(!CryptProtectData(&in,L"QVTU",nullptr,nullptr,nullptr,0,&out)) return {}; auto s=B64(out.pbData,out.cbData); LocalFree(out.pbData); return s; }
static std::wstring Unprotect(const std::wstring& value) { auto b=UnB64(value); DATA_BLOB in{(DWORD)b.size(),b.data()}, out{}; if(!CryptUnprotectData(&in,nullptr,nullptr,nullptr,nullptr,0,&out)) return {}; std::wstring s((wchar_t*)out.pbData,out.cbData/sizeof(wchar_t)); LocalFree(out.pbData); return s; }
std::wstring AppDir(){PWSTR p=nullptr;SHGetKnownFolderPath(FOLDERID_RoamingAppData,0,nullptr,&p);std::wstring d=p?std::wstring(p):L".";if(p)CoTaskMemFree(p);d+=L"\\QVTU校园网-cpp";fs::create_directories(d);return d;}
std::wstring LogPath(){return AppDir()+L"\\autologin.log";}
void Log(const std::wstring& text){std::wofstream f(LogPath(),std::ios::app);f<<text<<L"\n";}
static bool Flag(const std::wstring& v){return v==L"1"||v==L"true";}
bool SaveConfig(const QvtuConfig& c){std::wofstream f(ConfigPath(),std::ios::trunc);if(!f)return false;auto enc=Protect(c.password);if(enc.empty())return false;f<<L"username="<<c.username<<L"\npassword="<<enc<<L"\nautostart="<<(c.autostart?1:0)<<L"\nsilentLogin="<<(c.silentLogin?1:0)<<L"\nshowSuccessPopup="<<(c.showSuccessPopup?1:0)<<L"\nshowFailurePopup="<<(c.showFailurePopup?1:0)<<L"\nconnectCampusWifi="<<(c.connectCampusWifi?1:0)<<L"\nenableWifiRadio="<<(c.enableWifiRadio?1:0)<<L"\nfixedWifi="<<(c.fixedWifi?1:0)<<L"\nfixedSsid="<<c.fixedSsid<<L"\nstartupWaitSeconds="<<c.startupWaitSeconds<<L"\nretryWaitSeconds="<<c.retryWaitSeconds<<L"\n";return true;}
bool LoadConfig(QvtuConfig& c){std::wifstream f(ConfigPath());if(!f)return false;std::wstring line;while(std::getline(f,line)){auto p=line.find(L'=');if(p==std::wstring::npos)continue;auto k=line.substr(0,p),v=line.substr(p+1);if(k==L"username")c.username=v;else if(k==L"password")c.password=Unprotect(v);else if(k==L"autostart")c.autostart=Flag(v);else if(k==L"silentLogin")c.silentLogin=Flag(v);else if(k==L"showSuccessPopup")c.showSuccessPopup=Flag(v);else if(k==L"showFailurePopup")c.showFailurePopup=Flag(v);else if(k==L"connectCampusWifi")c.connectCampusWifi=Flag(v);else if(k==L"enableWifiRadio")c.enableWifiRadio=Flag(v);else if(k==L"fixedWifi")c.fixedWifi=Flag(v);else if(k==L"fixedSsid")c.fixedSsid=v;else if(k==L"startupWaitSeconds")c.startupWaitSeconds=std::clamp(_wtoi(v.c_str()),0,300);else if(k==L"retryWaitSeconds")c.retryWaitSeconds=std::clamp(_wtoi(v.c_str()),1,300);}return !c.username.empty()&&!c.password.empty();}
static std::wstring InstalledLoginPath(){wchar_t p[MAX_PATH]{};GetModuleFileNameW(nullptr,p,MAX_PATH);std::wstring s=p;auto slash=s.find_last_of(L"\\/");return slash==std::wstring::npos?L"QVTLogin.exe":s.substr(0,slash)+L"\\QVTLogin.exe";}
static std::wstring StartupLoginPath(){PWSTR p=nullptr;SHGetKnownFolderPath(FOLDERID_Startup,0,nullptr,&p);std::wstring path=p?std::wstring(p):L".";if(p)CoTaskMemFree(p);return path+L"\\QVTLogin.exe";}
std::wstring InstalledLoginExePath(){return InstalledLoginPath();}
std::wstring LoginExePath(){return StartupLoginPath();}
static std::wstring StartupShortcutPath(){PWSTR p=nullptr;SHGetKnownFolderPath(FOLDERID_Startup,0,nullptr,&p);std::wstring path=p?std::wstring(p):L".";if(p)CoTaskMemFree(p);return path+L"\\QVTU校园网登录.lnk";}
bool SetAutostart(bool enabled,const std::wstring&){HKEY k{};if(RegOpenKeyExW(HKEY_CURRENT_USER,L"Software\\Microsoft\\Windows\\CurrentVersion\\Run",0,KEY_SET_VALUE,&k)==ERROR_SUCCESS){RegDeleteValueW(k,L"QVTU校园网登录");RegCloseKey(k);}DeleteFileW(StartupShortcutPath().c_str());auto target=StartupLoginPath();if(!enabled){DeleteFileW(target.c_str());return true;}std::wstring source=InstalledLoginPath();if(_wcsicmp(source.c_str(),target.c_str())!=0&&!CopyFileW(source.c_str(),target.c_str(),FALSE))return false;return GetFileAttributesW(target.c_str())!=INVALID_FILE_ATTRIBUTES;}
