# QVTU Campus Login — Android 轻守护

泉州职业技术大学校园网 Android 低占用试用版，原生 Java + 系统 WebView，无 AndroidX / Compose / 第三方运行时依赖。最低 Android 8.0（API 26），当前预发布版本 **1.3.1（versionCode 5）**。使用 R8 与资源收缩的正式 release 签名，仍在夜间验证中。

## 分支与下载

- [`master`](https://github.com/chaoyimgl-rgb/QVTU-Campus-Login/tree/master)：Windows x64 C++ / WebView2 项目及其说明；Windows 最新正式发布仍为 v1.0.3。
- [`android`](https://github.com/chaoyimgl-rgb/QVTU-Campus-Login/tree/android)：独立 [`android-lite`](android-lite) 项目；保留的 Windows 源码、构建脚本和安装器不参与安卓构建。
- [Android v1.3.1 预发布与 SHA-256](https://github.com/chaoyimgl-rgb/QVTU-Campus-Login/releases/tag/android-v1.3.1)
- [下载正式签名 APK](https://github.com/chaoyimgl-rgb/QVTU-Campus-Login/releases/download/android-v1.3.1/QVTU-Campus-Login-Android-v1.3.1.apk)

**升级警告：历史 android-v1.2.0 附件为 debug 签名，与本次正式 release 签名不同，无法原地覆盖。** 旧 debug 用户需自行停止旧守护、备份所需日志，确认卸载会丢失设置后卸载重装，并重新输入凭据；不迁移或导出密码，不自动卸载。已安装同正式签名 1.3 / 1.3.1 的用户可覆盖升级。自行构建签名不同也不能保证覆盖。

## 功能与修复

手动连接校园 Wi-Fi → 保存学校 SSO 账号、密码 → 允许通知 → 开始守护。默认 120 秒，可选 60 / 300 秒。应用不自动连接 Wi-Fi、不管理热点。

- 所选 Wi-Fi 专用 HTTPS 探测结合校园门户核实，不再凭单一公网 204 跳过认证检查；未知 HTML/JS 页面由按需 WebView 核实。
- 临时网络/门户失败保留普通守护，按 60/120/240/480/900 秒退避；明确密码错误/用户不存在停止。修复退避启动时缺少后续检查调度。
- 私有滚动观测日志最多 3 × 256 KiB，仅事件枚举与数字，不含凭据；通过 SAF 手动导出。显示后台限制/电池优化状态，提供用户主动豁免入口。
- 当前轻量门户核实未知时，在线轮次也短暂启动 WebView，约一秒后释放；无常驻渲染、无常驻锁，不代表已验证低耗电。

## 验证与边界

141 项纯 Java 测试通过（策略 53、观测 45、假网络/时间决策 43）；`assembleDebug / assembleRelease / lintDebug / lintRelease` 成功，两种 Lint 各 0 errors / 7 warnings。正式 APK **33,396 字节**；版本 1.3.1 / code 5，v2 签名验证通过，`debuggable=false`，DEX 不含 release 调试认证入口。

用户现有真机在线两轮定时检查已完成门户核实、204 复核、释放资源和再次调度，无新增 crash；没有提交密码，**未验证真实掉线后端到端恢复、夜间保活、长期 Doze、重启恢复或功耗**。Doze/厂商限制可能延迟检查，豁免也不保证准时或半夜不断线。R8/资源收缩不等于 Google 后台优化。

HTTP 网关有链路安全风险；Keystore 只保护本地密码存储。完整认证流程、安全边界、构建命令、日志格式和版本变更见 [Android 说明](android-lite/README.md)。

## 许可

项目沿用 [MIT License](LICENSE)。Gradle Wrapper 为 Apache-2.0，保留原脚本许可头与 JAR 内许可，完整文本见 [Gradle Wrapper LICENSE](android-lite/gradle/wrapper/LICENSE)。
