#include "config.h"
#include "wifi.h"
#include <windows.h>
#include <commctrl.h>
#include <string>
#include <vector>

#define IDC_USER 101
#define IDC_PASS 102
#define IDC_AUTO 103
#define IDC_SAVE 104
#define IDC_TEST 105
#define IDC_STATUS 106
#define IDC_SHOWPASS 107
#define IDC_SILENT 108
#define IDC_POPSUCCESS 109
#define IDC_POPFAIL 110
#define IDC_WIFI 111
#define IDC_RADIO 112
#define IDC_FIXED 113
#define IDC_SSID 114
#define IDC_REFRESHWIFI 115

namespace {
HWND gUser{}, gPass{}, gAuto{}, gStatus{}, gShowPass{};
HWND gSilent{}, gPopSuccess{}, gPopFail{}, gWifi{}, gRadio{}, gFixed{}, gSsid{};
HFONT gFont{}, gSmall{}, gTitle{}; int gDpi = 96; std::wstring gSavedSsid;

int Scale(int value) { return MulDiv(value, gDpi, 96); }
std::wstring ReadText(HWND h) {
    int length = GetWindowTextLengthW(h);
    std::wstring value(static_cast<size_t>(length) + 1, L'\0');
    GetWindowTextW(h, value.data(), length + 1);
    value.resize(length);
    return value;
}
void Put(HWND h, const wchar_t* value) { SetWindowTextW(h, value); }
HWND Label(HWND parent, const wchar_t* text, int id = 0) {
    HWND h = CreateWindowW(L"STATIC", text, WS_CHILD | WS_VISIBLE,
        0, 0, 10, 10, parent, reinterpret_cast<HMENU>(static_cast<INT_PTR>(id)), nullptr, nullptr);
    SendMessageW(h, WM_SETFONT, reinterpret_cast<WPARAM>(gFont), TRUE);
    return h;
}
HWND Check(HWND parent, const wchar_t* text, int id) {
    HWND h = CreateWindowW(L"BUTTON", text, WS_CHILD | WS_VISIBLE | BS_AUTOCHECKBOX,
        0, 0, 10, 10, parent, reinterpret_cast<HMENU>(static_cast<INT_PTR>(id)), nullptr, nullptr);
    SendMessageW(h, WM_SETFONT, reinterpret_cast<WPARAM>(gFont), TRUE);
    return h;
}
void Move(HWND h, int x, int y, int w, int height) { MoveWindow(h, Scale(x), Scale(y), Scale(w), Scale(height), TRUE); }
void UpdateStatus(const std::wstring& text) { Put(gStatus, text.c_str()); Log(text); }
void RefreshWifiList() {
    const std::wstring wanted = gSavedSsid;
    SendMessageW(gSsid, CB_RESETCONTENT, 0, 0);
    for (const auto& item : ListQvtuWifi(true)) {
        SendMessageW(gSsid, CB_ADDSTRING, 0, reinterpret_cast<LPARAM>(item.ssid.c_str()));
    }
    int selected = static_cast<int>(SendMessageW(gSsid, CB_FINDSTRINGEXACT, static_cast<WPARAM>(-1), reinterpret_cast<LPARAM>(wanted.c_str())));
    if (selected >= 0) SendMessageW(gSsid, CB_SETCURSEL, selected, 0);
    else if (SendMessageW(gSsid, CB_GETCOUNT, 0, 0) > 0) SendMessageW(gSsid, CB_SETCURSEL, 0, 0);
}
void EnableOptions() {
    bool silent = SendMessageW(gSilent, BM_GETCHECK, 0, 0) == BST_CHECKED;
    EnableWindow(gPopSuccess, silent);
    EnableWindow(gPopFail, silent);
    bool wifi = SendMessageW(gWifi, BM_GETCHECK, 0, 0) == BST_CHECKED;
    EnableWindow(gRadio, TRUE);
    EnableWindow(gFixed, wifi);
    EnableWindow(gSsid, wifi && SendMessageW(gFixed, BM_GETCHECK, 0, 0) == BST_CHECKED);
}
void Layout(HWND hwnd) {
    RECT rect{};
    GetClientRect(hwnd, &rect);
    const int width = rect.right > Scale(520) ? rect.right : Scale(520);
    const int left = 28;
    const int field = 470;
    Move(GetDlgItem(hwnd, 201), left, 22, field, 34);
    Move(GetDlgItem(hwnd, 202), left, 58, field, 22);
    Move(GetDlgItem(hwnd, 203), left, 98, 120, 22);
    Move(gUser, left, 122, field, 28);
    Move(GetDlgItem(hwnd, 204), left, 160, 120, 22);
    Move(gPass, left, 184, field - 90, 28);
    Move(gShowPass, left + field - 82, 184, 82, 28);
    Move(GetDlgItem(hwnd, 205), left, 226, field, 22);
    Move(gSilent, left, 252, field, 24);
    Move(gPopSuccess, left + 24, 280, field, 24);
    Move(gPopFail, left + 24, 308, field, 24);
    Move(gRadio, left, 348, field, 24);
    Move(gWifi, left, 376, field, 24);
    Move(gFixed, left + 24, 404, 150, 26);
    Move(gSsid, left + 180, 402, field - 180, 28);
    Move(GetDlgItem(hwnd, IDC_REFRESHWIFI), left + 180, 434, field - 180, 28);
    Move(gAuto, left, 470, field, 24);
    Move(GetDlgItem(hwnd, IDC_SAVE), left + field - 230, 510, 110, 34);
    Move(GetDlgItem(hwnd, IDC_TEST), left + field - 110, 510, 110, 34);
    Move(gStatus, left, 552, field, 24);
    (void)width;
}
LRESULT CALLBACK WindowProc(HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam) {
    switch (message) {
    case WM_CREATE: {
        gDpi = GetDpiForWindow(hwnd);
        gFont = CreateFontW(-Scale(14), 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, DEFAULT_PITCH, L"Segoe UI");
        gSmall = CreateFontW(-Scale(12), 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, DEFAULT_PITCH, L"Segoe UI");
        gTitle = CreateFontW(-Scale(23), 0, 0, 0, FW_SEMIBOLD, FALSE, FALSE, FALSE, DEFAULT_CHARSET, 0, 0, CLEARTYPE_QUALITY, DEFAULT_PITCH, L"Segoe UI");
        HWND title = Label(hwnd, L"QVTU 校园网设置", 201); SendMessageW(title, WM_SETFONT, reinterpret_cast<WPARAM>(gTitle), TRUE);
        HWND subtitle = Label(hwnd, L"设置登录行为、校园网 Wi-Fi 和开机启动", 202); SendMessageW(subtitle, WM_SETFONT, reinterpret_cast<WPARAM>(gSmall), TRUE);
        Label(hwnd, L"学工号", 203); gUser = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"", WS_CHILD | WS_VISIBLE | ES_AUTOHSCROLL, 0, 0, 10, 10, hwnd, reinterpret_cast<HMENU>(IDC_USER), nullptr, nullptr);
        Label(hwnd, L"校园网密码", 204); gPass = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"", WS_CHILD | WS_VISIBLE | ES_PASSWORD | ES_AUTOHSCROLL, 0, 0, 10, 10, hwnd, reinterpret_cast<HMENU>(IDC_PASS), nullptr, nullptr);
        gShowPass = CreateWindowW(L"BUTTON", L"显示", WS_CHILD | WS_VISIBLE, 0, 0, 10, 10, hwnd, reinterpret_cast<HMENU>(IDC_SHOWPASS), nullptr, nullptr);
        Label(hwnd, L"登录行为", 205); gSilent = Check(hwnd, L"静默登录（隐藏 WebView2 页面）", IDC_SILENT); gPopSuccess = Check(hwnd, L"成功时显示通知", IDC_POPSUCCESS); gPopFail = Check(hwnd, L"失败时显示说明", IDC_POPFAIL);
        gWifi = Check(hwnd, L"自动连接 QVTU_ 开头的 Wi-Fi", IDC_WIFI); gRadio = Check(hwnd, L"尝试开启 Wi-Fi 无线电", IDC_RADIO); gFixed = Check(hwnd, L"固定网络", IDC_FIXED);
        gSsid = CreateWindowW(WC_COMBOBOXW, L"", WS_CHILD | WS_VISIBLE | CBS_DROPDOWNLIST | WS_VSCROLL, 0, 0, 10, 10, hwnd, reinterpret_cast<HMENU>(IDC_SSID), nullptr, nullptr);
        HWND wifiRefresh = CreateWindowW(L"BUTTON", L"刷新已保存网络", WS_CHILD | WS_VISIBLE, 0, 0, 10, 10, hwnd, reinterpret_cast<HMENU>(IDC_REFRESHWIFI), nullptr, nullptr);
        gAuto = Check(hwnd, L"登录 Windows 后自动运行", IDC_AUTO);
        HWND save = CreateWindowW(L"BUTTON", L"保存设置", WS_CHILD | WS_VISIBLE | BS_DEFPUSHBUTTON, 0, 0, 10, 10, hwnd, reinterpret_cast<HMENU>(IDC_SAVE), nullptr, nullptr);
        HWND test = CreateWindowW(L"BUTTON", L"测试登录", WS_CHILD | WS_VISIBLE, 0, 0, 10, 10, hwnd, reinterpret_cast<HMENU>(IDC_TEST), nullptr, nullptr);
        gStatus = Label(hwnd, L"Wi-Fi 连接只使用 Windows 已保存的 QVTU_ 网络。", IDC_STATUS); SendMessageW(gStatus, WM_SETFONT, reinterpret_cast<WPARAM>(gSmall), TRUE);
        for (HWND control : {gUser, gPass, gShowPass, gSilent, gPopSuccess, gPopFail, gWifi, gRadio, gFixed, gSsid, gAuto, wifiRefresh, save, test}) SendMessageW(control, WM_SETFONT, reinterpret_cast<WPARAM>(gFont), TRUE);
        QvtuConfig c; if (LoadConfig(c)) { gSavedSsid = c.fixedSsid; Put(gUser, c.username.c_str()); Put(gPass, c.password.c_str()); SendMessageW(gSilent, BM_SETCHECK, c.silentLogin, 0); SendMessageW(gPopSuccess, BM_SETCHECK, c.showSuccessPopup, 0); SendMessageW(gPopFail, BM_SETCHECK, c.showFailurePopup, 0); SendMessageW(gWifi, BM_SETCHECK, c.connectCampusWifi, 0); SendMessageW(gRadio, BM_SETCHECK, c.enableWifiRadio, 0); SendMessageW(gFixed, BM_SETCHECK, c.fixedWifi, 0); SendMessageW(gAuto, BM_SETCHECK, c.autostart, 0); }
        RefreshWifiList(); Layout(hwnd); EnableOptions(); return 0;
    }
    case WM_SIZE: Layout(hwnd); return 0;
    case WM_COMMAND:
        if (LOWORD(wParam) == IDC_SILENT || LOWORD(wParam) == IDC_WIFI || LOWORD(wParam) == IDC_FIXED) { EnableOptions(); return 0; }
        if (LOWORD(wParam) == IDC_SHOWPASS) { static bool visible = false; visible = !visible; SendMessageW(gPass, EM_SETPASSWORDCHAR, visible ? 0 : L'*', 0); Put(gShowPass, visible ? L"隐藏" : L"显示"); InvalidateRect(gPass, nullptr, TRUE); return 0; }
        if (LOWORD(wParam) == IDC_SSID && HIWORD(wParam) == CBN_DROPDOWN) { RefreshWifiList(); return 0; }
        if (LOWORD(wParam) == IDC_REFRESHWIFI) { RefreshWifiList(); UpdateStatus(L"已刷新 Windows 保存的 QVTU_ 网络"); return 0; }
        if (LOWORD(wParam) == IDC_SAVE || LOWORD(wParam) == IDC_TEST) {
            QvtuConfig c; c.username = ReadText(gUser); c.password = ReadText(gPass); c.autostart = SendMessageW(gAuto, BM_GETCHECK, 0, 0) == BST_CHECKED; c.silentLogin = SendMessageW(gSilent, BM_GETCHECK, 0, 0) == BST_CHECKED; c.showSuccessPopup = SendMessageW(gPopSuccess, BM_GETCHECK, 0, 0) == BST_CHECKED; c.showFailurePopup = SendMessageW(gPopFail, BM_GETCHECK, 0, 0) == BST_CHECKED; c.connectCampusWifi = SendMessageW(gWifi, BM_GETCHECK, 0, 0) == BST_CHECKED; c.enableWifiRadio = SendMessageW(gRadio, BM_GETCHECK, 0, 0) == BST_CHECKED; c.fixedWifi = SendMessageW(gFixed, BM_GETCHECK, 0, 0) == BST_CHECKED; int selected = static_cast<int>(SendMessageW(gSsid, CB_GETCURSEL, 0, 0)); if (selected >= 0) { wchar_t value[256]{}; SendMessageW(gSsid, CB_GETLBTEXT, selected, reinterpret_cast<LPARAM>(value)); c.fixedSsid = value; } gSavedSsid = c.fixedSsid;
            if (c.username.empty() || c.password.empty()) { MessageBoxW(hwnd, L"请填写学工号和密码。", L"信息不完整", MB_ICONWARNING); return 0; }
            if (!SaveConfig(c) || !SetAutostart(c.autostart, LoginExePath())) { MessageBoxW(hwnd, L"保存失败，请确认 QVTLogin.exe 与 QVTLogin_Config.exe 位于同一安装目录。", L"保存失败", MB_ICONERROR); return 0; }
            if (LOWORD(wParam) == IDC_SAVE) { UpdateStatus(L"设置已保存"); return 0; }
            STARTUPINFOW si{sizeof(si)}; PROCESS_INFORMATION pi{}; std::wstring command = L"\"" + LoginExePath() + L"\""; if (CreateProcessW(nullptr, command.data(), nullptr, nullptr, FALSE, 0, nullptr, nullptr, &si, &pi)) { CloseHandle(pi.hThread); CloseHandle(pi.hProcess); UpdateStatus(L"登录程序已启动"); } else UpdateStatus(L"启动登录程序失败"); return 0;
        }
        break;
    case WM_DESTROY: DeleteObject(gFont); DeleteObject(gSmall); DeleteObject(gTitle); PostQuitMessage(0); return 0;
    }
    return DefWindowProcW(hwnd, message, wParam, lParam);
}
}
int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, PWSTR, int) {
    SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2); INITCOMMONCONTROLSEX controls{sizeof(controls), ICC_STANDARD_CLASSES}; InitCommonControlsEx(&controls); WNDCLASSW wc{}; wc.hInstance = instance; wc.lpfnWndProc = WindowProc; wc.lpszClassName = L"QvtuSettingsSimple"; wc.hCursor = LoadCursorW(nullptr, IDC_ARROW); wc.hbrBackground = reinterpret_cast<HBRUSH>(COLOR_WINDOW + 1); RegisterClassW(&wc); HWND hwnd = CreateWindowW(wc.lpszClassName, L"QVTU 校园网设置", WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX, CW_USEDEFAULT, CW_USEDEFAULT, 570, 650, nullptr, nullptr, instance, nullptr); ShowWindow(hwnd, SW_SHOW); MSG message{}; while (GetMessageW(&message, nullptr, 0, 0) > 0) { TranslateMessage(&message); DispatchMessageW(&message); } return static_cast<int>(message.wParam);
}
