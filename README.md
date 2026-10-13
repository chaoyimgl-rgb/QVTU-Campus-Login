# QVTU Campus Login — Android 轻守护

泉州职业技术大学校园网 Android 低占用试用版，原生 Java + 系统 WebView，无 AndroidX / Compose / 第三方运行时依赖。最低 Android 8.0（API 26），当前版本 **1.2（versionCode 3）**。

## 分支与下载

- [`master`](https://github.com/chaoyimgl-rgb/QVTU-Campus-Login/tree/master)：Windows x64 C++ / WebView2 项目及其说明。
- [`android`](https://github.com/chaoyimgl-rgb/QVTU-Campus-Login/tree/android)：新增独立 [`android-lite`](android-lite) 项目；原 Windows 源码、构建脚本和安装器保留，不参与安卓构建。
- [Android v1.2.0 预发布与 SHA-256](https://github.com/chaoyimgl-rgb/QVTU-Campus-Login/releases/tag/android-v1.2.0)
- [下载 APK](https://github.com/chaoyimgl-rgb/QVTU-Campus-Login/releases/download/android-v1.2.0/QVTU-Campus-Login-Android-v1.2.0.apk)

APK 是 **debug 签名测试版，不是正式 release signing**。本次沿用已在用户手机安装的签名，没有发布私钥。自行构建通常使用自己的 debug 签名，不能保证覆盖安装此附件；卸载会丢失应用设置。

## 使用与验证

手动连接校园 Wi-Fi → 保存学校 SSO 账号、密码 → 允许通知 → 开始守护。默认每 120 秒通过所选 Wi-Fi 探测，必要时使用系统 WebView 认证；应用不自动连接 Wi-Fi、不管理热点。

用户已确认 Redmi K20 Pro / Android 15 真机使用成功。干净源码构建 `assembleDebug` / `lintDebug` 成功，53 项策略测试通过，Lint 为 0 errors / 7 warnings。APK 62,770 字节。**未做夜间保活、长期 Doze、开机恢复或功耗实测，不承诺半夜不断线。**

HTTP 网关有链路安全风险；Keystore 只保护本地密码存储。debug APK 保留可由显式 Activity action 触发的诊断及单次认证测试入口，不能视作生产加固版本。Wi-Fi 与热点同时工作依赖手机硬件和系统。完整认证流程、安全边界、构建命令、测试及已知限制见 [Android 说明](android-lite/README.md)。

## 许可

项目沿用 [MIT License](LICENSE)。Gradle Wrapper 为 Apache-2.0，保留原脚本许可头与 JAR 内许可，完整文本见 [Gradle Wrapper LICENSE](android-lite/gradle/wrapper/LICENSE)。
