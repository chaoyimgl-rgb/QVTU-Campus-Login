# 校园网轻守护 android-lite

独立 Android Java 原生项目，不调用或导入 Windows 版本及其凭据。原生 Activity + 系统控件 + 系统 WebView，无 AndroidX / Compose / 第三方运行时依赖。最低 Android 8.0（API 26），compileSdk / targetSdk 34。版本 1.2，versionCode 3；发布 tag 为 `android-v1.2.0`。

## 下载与签名

[预发布、APK 与 SHA-256 文件](https://github.com/chaoyimgl-rgb/QVTU-Campus-Login/releases/tag/android-v1.2.0)。附件 `QVTU-Campus-Login-Android-v1.2.0.apk` 为 **debug 签名测试版**，不是正式 release signing。未公开 debug.keystore 或任何签名私钥；本次沿用用户已安装 APK 的签名。自行构建使用本机 debug 签名，可能无法覆盖安装附件；卸载将丢失设置。仅从可信来源安装，并校验下载文件。

## 使用

1. 手动连接校园 Wi-Fi。应用不自动连接 Wi-Fi、不读取 SSID、不申请定位、不修改热点。
2. 输入学校 SSO 账号和密码并保存。密码使用 Android Keystore AES/GCM、随机 IV 加密存储，不回填密码、不导入桌面凭据。密码留空保留原密码，更换账号必须重新输入密码；账号及运行状态属于应用私有设置，不全部加密。
3. 选择 60 / 120 / 300 秒，默认 120 秒。运行中修改设置须先停止守护。
4. 开始守护；Android 13+ 应允许通知，常驻通知提供“停止”。未启用守护时“立即认证”仅执行一次，无 Wi-Fi 等待最多 45 秒；已运行时不会绕过失败退避。
5. 按厂商策略允许后台、自启动或调整电池优化。首次安装不会默认开启；只有用户已启用守护才尝试开机恢复。停止或凭据报错清除启用标记。

## 认证与网络策略

- 固定入口 `http://192.168.120.156/`。按需创建真实系统 WebView，保留移动默认 UA，不用 HTTP 请求模拟登录。
- 网关精确 IP 允许 HTTP 80 / HTTPS 443；学校 SSO 精确域名 `sid.qvtu.edu.cn` 仅 HTTPS 默认 443。拒绝 userinfo、非默认端口、其他 scheme 和未知主机。`http://123.123.123.123/` 仅作为提交前主框架 GET 的精确根路径过渡，不能填值，也不放开它的子资源。
- 仅在精确 SSO origin、`/cas/login` 填值，要求同源、无 userinfo、同路径 POST 表单。匹配 `#nameInput`、`input[type=password]` 和去除空白后精确为“登录”或“立即登录”的 button。使用 HTMLInputElement 原型 setter、冒泡 input/change 与 JSONObject.quote，每会话至多点击一次，最长 45 秒。
- 注入同源 CSP，`form-action` 仅额外允许精确 `http://192.168.120.156/cas-sso/login` 回调；不放宽 connect-src，阻止框架、worker 和 object。页面与请求策略不是操作系统级网络防火墙。
- 成功候选为网关精确 `/portal/entry/pc/finish`、`/portal/entry/phone/finish`，或 `/portal/portal-main` 且唯一 `userOnline=true`；“试验”选项只额外接受未经真机验证的 `/portal/entry/mobile/finish`。拒绝未知 finish、尾斜线及编码替代路径。实际完成还结合所选 Wi-Fi 的 HTTPS 204 探测，不能把 SSO 地址或回调本身当成功。
- 公网探测为 `https://connectivitycheck.gstatic.com/generate_204`，要求 204、不跟随重定向，通过选中 Wi-Fi 的 Network.openConnection，不以蜂窝联网误判。探测端点受阻可能误触发认证，成功也不代表所有网站可达。系统 VALIDATED 不是唯一成功条件。
- 监听未 VALIDATED 的 Wi-Fi；多个 Wi-Fi Network 时选择第一个可用者，失效时切换。仅认证期间短暂 bindProcessToNetwork，结束解除，仅影响本应用进程，不更改其他应用路由或热点。
- 认证期间以私有 VirtualDisplay + Presentation 附着 WebView 窗口，无悬浮窗权限；结束释放 WebView、窗口、显示、Surface、网络绑定和锁，无常驻渲染资源。
- 无常驻 wakelock；认证 partial wakelock 最多 60 秒。失败退避为 60/120/240/480/900 秒，持久化设置，停止再开始不绕过。已提交会话失败后普通守护也停止，等待人工确认，不自动反复提交；明确凭据错误立即停服。
- specialUse 前台服务提供停止通知；停止撤销回调、定时任务、HTTP 和显示资源，过期回调以会话代数失效。无 JS interface，无文件/content 访问，不忽略 TLS 错误，禁止混合内容、第三方 Cookie 和 Service Worker 网络加载。

## 安全与 debug 限制

Keystore 保护本地密码，不保护已解密进程内存、被攻陷设备或网络。账号和运行状态保存在应用私有 SharedPreferences；系统备份和设备迁移排除应用数据。UI 禁止截图、密码 autofill 和状态保存。网关允许明文 HTTP；IP 白名单不能防止恶意接入点或网关被攻陷，SSO 回调也存在明文链路风险。系统 WebView Cookie / DOM storage 可能保留认证会话，销毁 WebView 不等于清空全部 Cookie。

**发布附件保留 debug 功能。** 导出的 MainActivity 在 debuggable 条件下识别显式 `portal.diagnose` / `portal.testOnce` action：前者暂停自动认证、不读取或提交凭据，但会探测网络并记录脱敏门户结构；后者保留退避但可读取已保存凭据并尝试单次认证。其他本机应用或 adb 可显式启动这些入口，不应视作生产安全边界。诊断日志不输出字段值、Cookie、完整 URL query，但 host/path、页面标题和 DOM 结构仍可能包含环境信息。仓库和附件不包含用户日志、设置、设备诊断或密钥。普通服务和开机接收器不导出。不为发布改变已有功能或签名。

## 构建与测试

Gradle Wrapper 9.6.0、Android Gradle Plugin 9.3.2、Android SDK platform 34。本次干净复制源码以 **Android Studio JBR / OpenJDK 25.0.3** 实测 `assembleDebug` / `lintDebug` 成功，未将 JDK 21 兼容性或 GitHub Actions 构建标为已验证。

设置 `JAVA_HOME` 与 `ANDROID_HOME` 指向可用 JDK 和 Android SDK。Windows 脚本也支持自动寻找 Android Studio JBR 和当前用户默认 SDK，无需提交 `local.properties`。

```bat
build.bat
rem 已缓存完整依赖时可用
build.bat --offline
test-policy.bat
```

其他平台使用同样的工具链与环境变量：

```sh
./gradlew --no-daemon assembleDebug lintDebug
mkdir -p build/policy-tests
javac -encoding UTF-8 -d build/policy-tests app/src/main/java/cn/qvtu/campuslite/PortalPolicy.java tests/PortalPolicyTest.java
java -cp build/policy-tests cn.qvtu.campuslite.PortalPolicyTest
```

首次构建可能需联网获取 Gradle / Maven 工具依赖。Java 源码目标为 17，不代表 Gradle / AGP 可以用任意 JDK 17 构建。中文路径选项为实验项，关闭内置 Kotlin 的选项已弃用；还有部分系统 UI API 弃用警告。输出 APK 为 `app/build/outputs/apk/debug/app-debug.apk`；Lint 报告在 `app/build/reports/`。构建缓存、日志和 APK 不提交。

干净构建与原 APK 逐字节一致：62,770 字节，53 项网关 / SSO / 过渡地址 / 成功策略测试全部通过；Lint 为 **0 errors / 7 warnings**。保留警告涉及固定构建版本、必要 JS、界面国际化及状态同步保存等，不承诺无风险。

## 验证范围与后台限制

用户已确认 **Redmi K20 Pro / Android 15** 使用成功。未公开设备 ID、诊断时间线或凭据。未做夜间持续保活、长期 Doze、重启恢复、热点共存或功耗实测，也未测得可量化耗电数据。“低占用”指按需 WebView、无常驻渲染和无常驻锁的实现取向，不是功耗测试结论。

普通前台服务不保证 Doze 中按时联网；厂商杀进程、强行停止及开机后台限制可能中断恢复，强停后通常需手动打开。Wi-Fi 与热点并行取决于芯片和系统，应用无法保证或开启此能力。不申请精确 alarm，不承诺半夜不断线。校园门户改版、域名/IP/DOM 变化可能需要更新。Google Play targetSdk / specialUse 审核需另按发布时规则处理；当前是试用预发布，不是已验证生产版本。

## 权限与许可

声明 INTERNET、ACCESS_NETWORK_STATE、CHANGE_NETWORK_STATE、FOREGROUND_SERVICE、FOREGROUND_SERVICE_SPECIAL_USE、POST_NOTIFICATIONS、WAKE_LOCK、RECEIVE_BOOT_COMPLETED。无定位、存储、精确 alarm、自动 Wi-Fi 连接或热点管理权限。

项目沿用根目录 MIT LICENSE。Gradle Wrapper 为 Apache-2.0，脚本原许可头及 JAR 内 `META-INF/LICENSE` 均保留，随附完整 [wrapper/LICENSE](gradle/wrapper/LICENSE)。
