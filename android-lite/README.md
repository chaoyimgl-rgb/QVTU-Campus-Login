# 校园网轻守护 android-lite

独立 Android Java 原生项目，不调用或导入 Windows 版本及其凭据。原生 Activity + 系统控件 + 系统 WebView，无 AndroidX / Compose / 第三方运行时依赖。最低 Android 8.0（API 26），compileSdk / targetSdk 34。当前预发布版本 1.3.1，versionCode 5，tag 为 `android-v1.3.1`；仍在夜间验证中。历史 debug 发布 tag 为 `android-v1.2.0`。

## 下载与签名

[预发布、APK 与 SHA-256 文件](https://github.com/chaoyimgl-rgb/QVTU-Campus-Login/releases/tag/android-v1.3.1)。附件 `QVTU-Campus-Login-Android-v1.3.1.apk` 为 **正式 release 签名**，使用用户已安装正式 1.3 / 1.3.1 的同一证书；私钥不公开。历史 `android-v1.2.0` 附件为 debug 签名，与本次不同，不能原地覆盖。旧 debug 用户需自行停止守护、备份需要的日志，确认卸载会丢失设置后卸载重装并重新输入凭据；不导出或迁移密码，不自动卸载。同正式签名 1.3 / 1.3.1 可覆盖升级。自行构建若签名不同也无法保证覆盖。仅从可信来源安装，并校验下载文件。

本次附件实际 33,396 字节，SHA-256：`9cae6cc0cf560f4898b014e0c1bd6bc7e2ff2166e62b7c5d1d726478bece6471`。

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
- 公网 204 必须结合可信门户检查，不能单独证明校园认证在线。每轮追加经所选 Wi-Fi 的 GET-only 门户重定向检查（最多 5 跳，精确可信主机，无缓存、不带应用凭据、不输出 URL/正文）；只有实时 `portal-main?userOnline=true` 可直接跳过 WebView。HTML/JS 或未知结果进入无缓存 WebView 判定，不代表必定提交密码。认证复核须同时处于可信成功页面且 Wi-Fi HTTPS 返回 204，不能仅凭已点击登录提前结束。
- 监听未 VALIDATED 的 Wi-Fi；多个 Wi-Fi Network 时选择第一个可用者，失效时切换。仅认证期间短暂 bindProcessToNetwork，结束解除，仅影响本应用进程，不更改其他应用路由或热点。
- 认证期间以私有 VirtualDisplay + Presentation 附着 WebView 窗口，无悬浮窗权限；结束释放 WebView、窗口、显示、Surface、网络绑定和锁，无常驻渲染资源。
- 无常驻 wakelock；认证 partial wakelock 最多 60 秒。失败退避为 60/120/240/480/900 秒，持久化设置，停止再开始不绕过。临时网络/门户失败即使已提交也不关闭普通守护，按原低频探测与退避恢复；明确凭据错误立即停服，单次模式仍不自动重试。
- specialUse 前台服务提供停止通知；停止撤销回调、定时任务、HTTP 和显示资源，过期回调以会话代数失效。无 JS interface，无文件/content 访问，不忽略 TLS 错误，禁止混合内容、第三方 Cookie 和 Service Worker 网络加载。

## 安全与 debug 限制

Keystore 保护本地密码，不保护已解密进程内存、被攻陷设备或网络。账号和运行状态保存在应用私有 SharedPreferences；系统备份和设备迁移排除应用数据。UI 禁止截图、密码 autofill 和状态保存。网关允许明文 HTTP；IP 白名单不能防止恶意接入点或网关被攻陷，SSO 回调也存在明文链路风险。系统 WebView Cookie / DOM storage 可能保留认证会话，销毁 WebView 不等于清空全部 Cookie。

**历史 1.2 下载附件及当前 debug 构建保留 debug 功能；1.3 release 不保留这些入口。** 使用 `BuildConfig.DEBUG` 编译期条件，R8 去除 Activity action 入口、DOM 诊断和单次测试功能。 导出的 MainActivity 在 debuggable 条件下识别显式 `portal.diagnose` / `portal.testOnce` action：前者暂停自动认证、不读取或提交凭据，但会探测网络并记录脱敏门户结构；后者保留退避但可读取已保存凭据并尝试单次认证。其他本机应用或 adb 可显式启动这些入口，不应视作生产安全边界。诊断日志不输出字段值、Cookie、完整 URL query，但 host/path、页面标题和 DOM 结构仍可能包含环境信息。仓库和附件不包含用户日志、设置、设备诊断或密钥。普通服务和开机接收器不导出。不为历史附件改变签名；本地 1.3 release 使用新的独立正式签名，不能覆盖 debug 签名安装。不要为更新自动卸载或清数据；如用户决定迁移，需先停止旧守护，手动确认卸载风险并重新输入账户密码，不导出密码。

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

## 1.3 观测日志与夜间限制

系统卡片显示电池优化是否豁免、Android 后台限制 API 状态；厂商自启动策略不能完整检测。点击“申请本应用电池优化豁免”，阅读核心常驻校园认证用途及耗电说明后，用户自行在系统授权。不会自动改全局设置或 app-op。豁免、前台服务和 Wi-Fi 回调都不能保证深度 Doze 内 Handler 按 120 秒执行，更不能承诺长期不被杀。仍无常驻锁、精确 alarm、双进程或音乐保活；认证锁最多 60 秒。

“刷新最近事件”手动刷新本次进程最近 20 条，不注册日志 UI 监听。“导出观测日志”使用系统 SAF 文件保存器，不要授予存储权限。应用只写私有目录，独立有界 IO 队列、线程空闲 10 秒退出；最多三份各 256 KiB，满后轮转最旧文件。未达到上限不会因启停服务删除历史。不上传日志；用户自己选择的文件提供方可能为云盘。导出在同一 IO 队列生成不可变快照，不与写入冲突。写入错误或队列溢出显示不完整提示。

TSV v1 每条含带时区的本地时间、epoch 毫秒、elapsedRealtime 毫秒及事件；后者是从开机计时，不跨重启比较。日志不含任意文本字段，只允许事件枚举和数字；固定探测目标 host/path 写在格式头，没有 query、SSID/BSSID、设备 IP、账户、密码、Cookie、正文或令牌。普通每轮探测两条轻量 start/result 加资源释放，无高频守护忙循环；原有认证内短期页面轮询和认证后复核仍仅限最长 45 秒会话。

读夜间时间线：
- `PROBE_START` 的字段为期望 elapsed、实际相邻开始间隔、延迟、配置间隔。若配置 120000 而实际间隔 720000，表示有 12 分钟观测缺口，不能认定故障发生在首次失败那一刻。Handler 使用 uptime 延时，深睡期间不唤醒；以 elapsed 差明确标记。
- `PROBE_RESULT` 为成功(1/0)、HTTP 状态(-1=异常)、耗时、阶段(0=常规，1=认证复核)。只代表该 Wi-Fi 探测端点；失败可能是端点/上游/认证问题，不等于路由器重启。
- `OUTAGE_START` 给出最近成功 epoch 和首次失败 epoch（-1=本次服务未知）。故障起点最多只能限制在两者之间；恢复 `OUTAGE_RECOVERY` 追加首次失败至恢复的观测时长、起点不确定区间长度、失败阶段最大相邻探测间隔。认证复核也参与恢复观测。
- `WIFI_LOST` 与 `WIFI_RECOVERY` 单独记录选中网络丢失及恢复；恢复字段为丢失 epoch、观测失联时长。这是应用收到回调的时间，不是硬件重启证据。
- `MONITOR_START/STOP` 区分服务生命周期。进程被强杀可能没有 STOP；跨服务会话不拼接猜测，统计重新开始但历史文件保留。系统校时会影响 epoch 区间，耗时取单调时钟；调度 gap 也可能来自认证、退避或线程/系统延迟，不只来自 Doze。

## 历史 1.3 构建记录

开启 `debuggable false`、R8 `minifyEnabled` 和 `shrinkResources`，无 JS interface 或自定义反射接口，不添加全量保留规则。优化减少打包类及资源，不等于“Google 后台优化”。ART 安装编译也可能作用于 debug；Google Play baseline/cloud profiles 不会因侧载自动得到，不能由 APK 变小推断 RAM 或功耗明显下降。

签名从仓库外配置注入：设置 `CAMPUS_SIGNING_PROPERTIES` 为私有 properties 文件绝对路径，内容字段为 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`。也可使用环境变量 `CAMPUS_STOREFILE`、`CAMPUS_STOREPASSWORD`、`CAMPUS_KEYALIAS`、`CAMPUS_KEYPASSWORD`。没有配置则 release 为未签名产物，不回退 debug 签名。不要把密码写入命令行、tracked gradle.properties 或源码，不提交/上传 keystore。妥善加密备份仓库外 keystore 与私有配置，丢失私钥将无法以相同签名升级；证书 fingerprint 不是秘密。

```bat
set CAMPUS_SIGNING_PROPERTIES=外部私有配置的绝对路径
 gradlew.bat --no-daemon assembleDebug assembleRelease lintDebug lintRelease
 test-policy.bat
```

本次 1.3 实测 `assembleDebug` / `assembleRelease` / `lintDebug` / `lintRelease` 全部成功；两种 Lint 均 0 errors / 7 warnings（电池豁免用途、必要 JS、同步状态保存和界面国际化）。纯 Java 策略 53 项、观测日志 45 项检查通过。最终 debug 109,958 字节，优化正式 release 32,276 字节；APK 大小不代表 RAM/耗电。已用 aapt 验证 release 不含 debuggable 标志、包名不变、code 4；DEX 不含 `portal.diagnose` / `portal.testOnce` / `PortalDiag` / DOM 诊断特征，APK Signature Scheme v2 校验通过。签名证书 SHA-256：`e6e206683a328e893e5936d215f15b107bd585e0e8a4ecf8d4766899169ba65b`。

策略脚本同时运行纯 Java 观测/日志测试，不引入第三方测试库，覆盖文件上限、轮转、快照、时区、字段白名单、失败区间、调度缺口和 Wi-Fi 独立时间线。本次维护 `github-android-release/android-lite` 为唯一源码；外部旧 `android-lite` 目录未同步，避免覆盖未知本地更改。真机未连接，未安装、未触发凭据提交或改变网络，未做 overnight 验证。

## 1.3.1 变更与验证（预发布，2026-10-14）

真机原 1.3 日志连续约 120 秒执行并返回 204，不能归因为完全没有轮询；检查当时 Doze ACTIVE，应用已有电池优化豁免。代码确定存在单 204 跳过门户、已提交临时失败 fatal 关闭守护、立即认证处于退避且新服务无后续 tick 三条问题。未复现历史夜间故障，不能声称已证实校园网白名单就是当晚原因。历史 logcat 有本应用前台服务启动超时，当前服务正常，未证明与该夜间问题关联。

修复后保留默认 120 秒；未知门户结果让按需 WebView 核实，不增加常驻锁或忙循环。当前校园门户 HTTP 只读检查返回 UNKNOWN（HTML/JS 不解析正文），因此在线轮次也会短暂创建 WebView，约一秒后确认成功并释放；这是可靠性与开销的明确取舍，尚无功耗实测。只有明确密码错误/用户不存在停止，泛化“认证失败/登录失败”按有界退避恢复。打开正常界面仅恢复用户原已启用的守护，不增加 release 导出控制入口。

新增事件：`PORTAL_RESULT` 0=未知、1=明确在线、2=登录页；`MONITOR_STATE` 为 enabled、oneShot、busy、退避剩余毫秒、间隔秒；`SCHEDULED` 为延迟与目标 elapsed；`AUTH_SUBMITTED` 只表示执行过一次登录点击，不含表单值。`AUTH_START/SUCCESS` 也可能仅核实已有在线会话，不等于提交过密码。UNKNOWN 不计作实际断网；普通 `PROBE_RESULT` 的 204 也不再独自决定跳过门户。release logcat 同步仅枚举事件和数字，供非 debuggable 包排查，不开放远程凭据调试。

纯 Java 共 141 项检查（策略 53、观测 45、假网络/时间决策 43）通过；假网络覆盖 204 白名单、未知门户、可信成功复核、已提交临时失败继续调度、退避上限、凭据错误停止、单次/禁用与代数失效，不伪称真实掉线端到端验证。发布前重新运行现项目 `test-policy.bat` 及 `assembleDebug/assembleRelease/lintDebug/lintRelease --offline` 全通过，Gradle 复用已验证的最新产物；两种 Lint 各 0 errors / 7 warnings。发布附件为实际核查的正式优化 release **33,396 字节**，不是早先记录的 33,348 字节。版本 1.3.1 / code 5，证书与现有正式版本一致，v2 签名验证通过；aapt 未显示 debuggable，DEX 不含 `portal.diagnose` / `portal.testOnce` / `PortalDiag`。R8 和资源收缩均开启。

本次上传不操作手机、不读取私有设置。此前已同签名覆盖安装并由用户完成两轮在线定时检查：均完成门户核实、204 复核、资源释放并再次安排 120 秒检查，无新增 crash；两轮无 `AUTH_SUBMITTED`，只确认现有在线会话。因此不声称已验证真实掉认证后提交密码恢复，也未验证夜间断网、深度 Doze、重连或开机恢复。守护继续由用户控制。

## 权限与许可

声明 INTERNET、ACCESS_NETWORK_STATE、CHANGE_NETWORK_STATE、FOREGROUND_SERVICE、FOREGROUND_SERVICE_SPECIAL_USE、POST_NOTIFICATIONS、REQUEST_IGNORE_BATTERY_OPTIMIZATIONS（核心常驻认证，仅用户主动请求）、WAKE_LOCK、RECEIVE_BOOT_COMPLETED。无定位、存储、精确 alarm、自动 Wi-Fi 连接或热点管理权限。

项目沿用根目录 MIT LICENSE。Gradle Wrapper 为 Apache-2.0，脚本原许可头及 JAR 内 `META-INF/LICENSE` 均保留，随附完整 [wrapper/LICENSE](gradle/wrapper/LICENSE)。
