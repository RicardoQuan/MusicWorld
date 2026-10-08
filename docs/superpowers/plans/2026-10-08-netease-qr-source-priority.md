# 网易云扫码登录与歌词来源优先级实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在手机发送端加入二维码优先的网易云登录、网易云优先/LRCLIB 备用歌词查询，并准确显示当前歌词来源。

**Architecture:** 保留当前单模块 Android Java 应用。扩展 NetEase API client 完成二维码认证；用本地 ZXing 生成二维码，不把一次性登录 key 发给外部二维码服务。歌词查询按 provider 顺序独立匹配，首个可用时间戳来源胜出；在结果对象和发送端状态中保留真实来源。

**Tech Stack:** Android Java 17 / minSdk 28、Gradle、JUnit 4、NetEase WeAPI client、LRCLIB HTTPS API、ZXing core 3.5.4。

---

## 文件职责

| 路径 | 变更职责 |
|---|---|
| `app/build.gradle` | 增加 ZXing core，用于设备本地生成二维码矩阵。 |
| `app/src/main/java/com/univ/lyricsbridge/lyric/NetEaseApiClient.java` | 获取二维码 key、查询扫码状态、解析成功 Cookie 与安全挑战。 |
| `app/src/main/java/com/univ/lyricsbridge/lyric/NetEaseQrLoginUrl.java` | 生成官方网页扫码器使用的编码 URL。 |
| `app/src/main/java/com/univ/lyricsbridge/lyric/LyricSource.java` | 定义可显示的歌词来源枚举。 |
| `app/src/main/java/com/univ/lyricsbridge/lyric/LyricCandidate.java` | 在匹配候选中携带来源。 |
| `app/src/main/java/com/univ/lyricsbridge/lyric/LyricLookupResult.java` | 将候选来源传递到最终查询结果。 |
| `app/src/main/java/com/univ/lyricsbridge/lyric/FallbackLyricProvider.java` | 顺序查询 provider；只在前一来源没有可信的时间戳匹配时继续。 |
| `app/src/main/java/com/univ/lyricsbridge/lyric/LrclibApiClient.java` | 封装 LRCLIB HTTPS 查询、响应解析和超时。 |
| `app/src/main/java/com/univ/lyricsbridge/lyric/LrclibLyricProvider.java` | 使用标题/歌手/时长查询并筛选时间戳歌词。 |
| `app/src/main/java/com/univ/lyricsbridge/ui/PhoneModeActivity.java` | 默认二维码登录 UI、扫码状态、现有密码/SMS 备用模式、歌词来源展示。 |
| `app/src/main/java/com/univ/lyricsbridge/service/PhoneSenderService.java` | 注入有序 provider，并将命中来源写入发送端实时状态/通知。 |
| `app/src/test/java/com/univ/lyricsbridge/lyric/NetEaseApiClientTest.java` | 假传输测试二维码 key、状态、Cookie、失效与安全挑战。 |
| `app/src/test/java/com/univ/lyricsbridge/lyric/FallbackLyricProviderTest.java` | 验证来源顺序、无会话回退、失败回退和无效歌词回退。 |
| `app/src/test/java/com/univ/lyricsbridge/lyric/LrclibLyricProviderTest.java` | 验证请求编码、候选解析、时戳筛选和网络失败。 |
| `app/src/test/java/com/univ/lyricsbridge/lyric/LyricSourceTest.java` | 验证来源标签和未知来源不伪装成已匹配。 |
| `app/src/test/java/com/univ/lyricsbridge/lyric/NetEaseQrLoginUrlTest.java` | 验证生成的网易云扫码内容包含正确 key 和 URL 编码。 |
| `app/src/main/java/com/univ/lyricsbridge/ui/NetEaseQrStatusText.java` | 将认证状态码映射为明确的中文提示。 |
| `app/src/test/java/com/univ/lyricsbridge/ui/NetEaseQrStatusTextTest.java` | 覆盖二维码等待/确认/过期/成功/安全挑战提示。 |
| `README.md` | 更新扫码登录、备用歌词来源和两手机验证步骤。 |
| `docs/superpowers/specs/2026-10-08-netease-qr-source-priority-design.md` | 记录实施结论及平台限制。 |

## Task 1: 给歌词候选增加来源，并验证严格优先级

**Files:** 新建 `LyricSource.java`、`FallbackLyricProvider.java`、`LyricSourceTest.java`、`FallbackLyricProviderTest.java`；修改 `LyricCandidate.java`、`LyricLookupResult.java`。

- [x] **Step 1: 先写候选来源和展示标签测试。** `LyricSourceTest` 检查 `NETEASE.displayName()` 为“网易云音乐”、`LRCLIB.displayName()` 为“LRCLIB”、`UNKNOWN.displayName()` 为空；创建没有指定来源的旧构造器候选时应为 `UNKNOWN`。
- [x] **Step 2: 写优先级测试并观察失败。** 用两个内存 `LyricProvider` 记录调用顺序：NetEase 给出有效候选时只返回该候选且不调用 LRCLIB；NetEase 为空或抛 `IOException` 时尝试 LRCLIB；NetEase 只有无时间戳/低置信候选时继续尝试 LRCLIB；两源都没有有效候选时返回空列表。
- [x] **Step 3: 运行聚焦测试确认 RED。** 执行 `U:\gradlew.bat testDebugUnitTest --tests com.univ.lyricsbridge.lyric.LyricSourceTest --tests com.univ.lyricsbridge.lyric.FallbackLyricProviderTest`；预期因新增类型和来源访问器不存在而失败。
- [x] **Step 4: 实现来源模型和短路 fallback。** `LyricSource` 为 `NETEASE`、`LRCLIB`、`UNKNOWN` 提供展示名。保留 `LyricCandidate` 五参数构造器并默认 `UNKNOWN`，新增带 `LyricSource` 的构造器和 `getSource()`。`FallbackLyricProvider` 按构造时传入顺序逐个查找；某 provider 抛 `IOException` 时继续下一个；只在候选经 `LyricMatcher.bestMatch(track, candidates)` 通过且解析出时间戳行时返回该 provider 候选，否则继续。
- [x] **Step 5: 让结果继承候选来源并验证 GREEN。** 在 `LyricLookupResult.found(...)` 保存候选 `LyricSource` 并提供 `getSource()`；`NO_MATCH` 和 `ERROR` 返回 `UNKNOWN`。重跑 Task 1 聚焦命令，预期全部通过（从 `U:\` ASCII 工作目录执行）。

## Task 2: 实现 LRCLIB 时间戳歌词 provider

**Files:** 新建 `LrclibApiClient.java`、`LrclibLyricProvider.java`、`LrclibLyricProviderTest.java`。

- [x] **Step 1: 先写 HTTP client 和 provider 测试。** 用注入式传输记录 URL：第一请求 `/api/get` 包含 URL 编码的标题、歌手与时长；精确查询无命中后搜索 `/api/search`，标题/歌手搜索在前、标题单独搜索在后。JSON 中解析 `trackName`、`artistName`、`duration`、`syncedLyrics`；拒绝 `syncedLyrics` 为空或不能被 `LrcParser` 解析的结果；网络异常作为 `IOException` 返回。
- [x] **Step 2: 跑聚焦测试确认 RED。** 执行 `U:\gradlew.bat testDebugUnitTest --tests com.univ.lyricsbridge.lyric.LrclibLyricProviderTest`；预期因 client/provider 尚不存在而失败。
- [x] **Step 3: 实现注入式 LRCLIB client。** `LrclibApiClient.Transport.get(path)` 返回 HTTP 状态码和 JSON body；默认传输访问 `https://lrclib.net`、连接超时 6 秒、读取超时 8 秒，限制响应体为 2 MiB。所有参数用 UTF-8 URL 编码，非 2xx、无效 JSON 返回 `IOException`。
- [x] **Step 4: 实现候选搜索。** `LrclibLyricProvider.search(track)` 对有标题的曲目先调用 `/api/get`，再依次调用带标题和歌手、仅标题的 `/api/search`。只生成带 `LyricSource.LRCLIB` 且 `LrcParser` 有时间戳行的 `LyricCandidate`；按标题、歌手与时长排序/筛选沿用 `LyricMatcher` 现有接受阈值。
- [x] **Step 5: 重跑聚焦测试确认 GREEN。** 聚焦测试覆盖 URL 编码、404 后搜索、无歌手跳过、无时间戳和网络失败回退，全部通过。

## Task 3: 在 NetEase API client 中实现二维码登录协议

**Files:** 修改 `app/build.gradle`、`NetEaseApiClient.java`、`NetEaseApiClientTest.java`；新建 `NetEaseQrLoginUrl.java` 和 `NetEaseQrLoginUrlTest.java`。

- [x] **Step 1: 添加 fake-transport QR 测试。** 使用测试传输覆盖 key、800/801/802/803/8821。确认 803 只有非空 `MUSIC_U` Cookie 时成功；失败/挑战状态不会交付 Cookie。普通密码和短信登录测试保持通过。
- [x] **Step 2: 运行 QR 测试确认 RED。** QR 方法、类型与 URL builder 加入前，聚焦测试按预期编译失败。
- [x] **Step 3: 添加本地 QR 生成依赖和内容测试。** 加入 ZXing core 3.5.4；URL 编码覆盖固定 key、保留字符和空 key。
- [x] **Step 4: 增加 NetEase QR 传输方法。** 获取 key 和轮询状态使用加密 JSON 请求及唯一时间戳。状态 803 只有 Cookie 含非空 `MUSIC_U` 才输出会话；安全挑战只返回说明。
- [x] **Step 5: 重跑 QR client 测试确认 GREEN。** QR client、URL、密码、短信和 YRC 测试全部通过。

## Task 4: 将登录窗口改成二维码优先并带生命周期控制

**Files:** 修改 `PhoneModeActivity.java`。

- [x] **Step 1: 先写二维码状态提示测试。** `NetEaseQrStatusTextTest` 覆盖扫码等待、确认、过期、成功和安全验证；新增 helper 前测试按预期编译失败。
- [x] **Step 2: 在现有 AlertDialog 中增加默认二维码页。** 登录窗默认呈现二维码、扫码说明和刷新按钮；密码/SMS 登录保留为备用入口，二维码由 ZXing 本地生成。
- [x] **Step 3: 实现轮询与窗口关闭清理。** 打开 QR 页生成 key 并每 3 秒轮询；单线程请求，使用 generation 丢弃旧响应。关闭 Dialog 会移除回调并清 key；803 且含非空 `MUSIC_U` 时加密保存并刷新歌曲。
- [x] **Step 4: 显示挑战和网络错误。** 安全挑战停止轮询并引导官方 App 验证；普通网络异常显示错误和手动刷新。
- [x] **Step 5: 检查 Android 9 编译。** `assembleDebug` 成功，应用 `minSdk` 为 28。

## Task 5: 接入来源顺序并显示真实命中来源

**Files:** 修改 `PhoneSenderService.java`、`PhoneModeActivity.java`、`README.md`；测试 `LyricLookupEngine` 和 fallback provider。

- [x] **Step 1: 写来源传播测试并观察 RED。** 来源测试覆盖 found/no-match/error；fallback 测试验证网易云命中后不再调用 LRCLIB，即使 LRCLIB 候选分数更高也不覆盖。
- [x] **Step 2: 执行聚焦测试。** 来源和 fallback 聚焦测试通过。
- [x] **Step 3: 注入 source-priority provider。** `PhoneSenderService` 使用 NetEase → LRCLIB 顺序，禁用来源未知的旧缓存读取；未登录网易云时仍尝试 LRCLIB。
- [x] **Step 4: 显示来源并验证。** 手机状态和通知显示真实命中来源；不匹配时不显示来源。手机登录说明已更新。
- [x] **Step 5: 重跑聚焦测试确认 GREEN。** NetEase first、LRCLIB fallback、source label 测试均通过。

## Task 6: 完整验证和交付

**Files:** `README.md`、规格和进度文档、`dist/univ-lyrics-bridge-debug.apk`。

- [x] **Step 1: 更新 README。** 已说明扫码优先、另一台已登录设备扫码、密码/SMS 备用、8821 官方 App 验证、网易云优先/LRCLIB 备用，以及手机发送端来源标签。
- [x] **Step 2: 运行完整单元测试与 APK 构建。** 从 ASCII 别名工作区执行 `clean testDebugUnitTest assembleDebug`；构建成功，78 tests、0 failures、0 errors。
- [x] **Step 3: 检查 APK 并复制交付。** 已复制到 `dist/univ-lyrics-bridge-debug.apk`；`aapt` 确认 minSdk 28，`apksigner` 确认 v2 签名，源/交付 SHA-256 相同：`1680E84B6C8927404CE321D0E85F20F63EF206FF918FF6403540160C8CC22D8D`。
- [x] **Step 4: 记录硬件验证限制。** SDK 的 `adb devices -l` 无已连接设备；真实扫码登录、LRCLIB 网络和双手机蓝牙仍待用户设备验证。未触发真实登录或短信请求。

## 执行约束

- 按用户已确认的偏好在当前会话逐项实施；不创建子代理。
- 每项行为修改先写/运行失败测试，再实现最小代码并复跑。
- 本 workspace 没有 Git 仓库，因此本计划不包含 Git commit 步骤。
- 不实现网页登录 Cookie/Token 手动导入；不模仿官方客户端设备身份、不绕过 8821 或其它安全挑战。
