# QVTU Campus Login for Windows x64

这是面向泉州职业技术大学校园网环境的 Windows x64 登录工具。程序使用原生 C++、Windows WLAN API 和 Microsoft Edge WebView2 Runtime。

## 安装包

安装包包含两个程序：

- `QVTLogin_Config.exe`：安装在 `C:\Program Files\QVTU Campus Login\`，用于配置账号、登录行为、QVTU Wi-Fi 和启动项。
- `QVTLogin.exe`：安装目录保留一份，并把同一程序复制到当前用户 Startup 文件夹，供 Windows 用户登录后自动运行。

安装器 `installer\output\QVTU-Campus-Login-Setup-x64.exe` 安装到 Program Files；安装和卸载均清理 Startup 中的登录程序副本及旧 Run 注册表值。

## 系统要求

- Windows 10/11 x64
- Microsoft Edge WebView2 Runtime
- WLAN AutoConfig 服务和可用无线网卡（使用自动 Wi-Fi 连接时）
- 校园网 Wi-Fi 已经在 Windows 系统中保存过 profile

WebView2 Runtime 不随安装包携带，请从微软官方渠道安装 Evergreen Runtime。程序不保存或读取 Wi-Fi 密码，只使用 Windows 已保存的 `QVTU` 开头网络。

## 配置

运行 `QVTLogin_Config.exe`：

- 输入校园网学工号和密码；
- 选择是否静默登录；
- 选择成功/失败通知；
- 独立选择是否尝试开启 Wi-Fi 无线电；
- 选择是否在没有 Wi-Fi 连接时自动连接 QVTU 网络；
- 按信号选择或固定一个已保存的 QVTU 网络；
- 选择是否开机自动运行；启用后，Windows 用户登录时会从 Startup 启动 `QVTLogin.exe`。
- 设置启动后等待时间（保留兼容配置，当前登录流程会优先检测网关是否真正就绪）。
- 设置首次失败后的重试间隔，默认 6 秒；首次失败后只重试一次。

账号配置位于：

```text
%APPDATA%\QVTU校园网-cpp\config.dat
```

密码使用当前 Windows 用户的 DPAPI 加密。换 Windows 用户后不能直接迁移该配置，应重新配置。

日志位于：

```text
%APPDATA%\QVTU校园网-cpp\autologin.log
```

WebView2 登录临时数据位于 `%LOCALAPPDATA%\QVTU校园网-cpp\WebView2\` 的单次运行目录，程序退出后会尝试删除，不会写入安装目录。Wi-Fi 连接后程序会立即打开网关探测页，持续等待登录页或已登录门户真正出现；网关未就绪不会立即判失败。首次认证失败后默认等待 6 秒再刷新重试；这两个行为可在设置界面调整。

## 构建

需要：

- Visual Studio Desktop development with C++
- MSVC x64 工具链
- Windows SDK
- CMake 3.24+
- WebView2 Native SDK NuGet 包

先设置 WebView2 SDK 路径：

```bat
set WEBVIEW2_SDK=%USERPROFILE%\.nuget\packages\microsoft.web.webview2\1.0.2903.40\build\native
```

再运行：

```bat
build_win64.bat
```

输出：

```text
build\bin\Release\QVTLogin_Config.exe
build\bin\Release\QVTLogin.exe
```

## 安装器构建

安装器脚本位于 `installer\QVTUCampusLogin.iss`。需要安装 Inno Setup 6，并先把两个 EXE 放入：

```text
installer\staging\QVTLogin_Config.exe
installer\staging\QVTLogin.exe
```

使用 Inno Setup Compiler 打开 `.iss` 后编译即可生成 `setup.exe`。

## 兼容范围

登录流程针对当前校园网门户实现，使用网关 `http://192.168.120.156/`、当前登录页 DOM 和成功跳转页面。校园网门户改版、域名/IP 或页面字段变化后，程序可能需要更新。
