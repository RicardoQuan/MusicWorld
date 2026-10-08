# 当前行颜色设置实施计划

> **For agentic workers:** Use the executing-plans workflow to implement this plan task by task. Steps use checkbox syntax for tracking.

**Goal:** 在车机显示设置中增加可保存的当前行颜色，并提供 Android 9 可安装的调试 APK。

**Architecture:** 复用现有预设色板和 `AppSettings` SharedPreferences。设置页新增独立颜色目标；悬浮层的句级当前行使用该值，YRC 逐字渲染时用该值作为已唱字色并继续使用原未唱字色。

**Tech Stack:** Java、Android SDK、Gradle Wrapper、JUnit 4。

---

### Task 1：先验证当前行默认色

**Files:**
- Modify: `app/src/test/java/com/univ/lyricsbridge/overlay/OverlayColorPaletteTest.java`

- [x] **Step 1: 写默认色回归测试**

在测试类中加入：

```java
@Test
public void currentLineDefaultIsGoldAndSelectable() {
    assertEquals(0xFFFFCD53, OverlayColorPalette.DEFAULT_CURRENT_LINE_COLOR);
    assertTrue(OverlayColorPalette.indexOfColor(
            OverlayColorPalette.DEFAULT_CURRENT_LINE_COLOR) >= 0);
}
```

- [x] **Step 2: 运行测试确认按预期失败**

运行 `testDebugUnitTest --tests com.univ.lyricsbridge.overlay.OverlayColorPaletteTest`。预期编译失败，提示 `DEFAULT_CURRENT_LINE_COLOR` 尚未定义。

### Task 2：保存颜色并加入车机设置

**Files:**
- Modify: `app/src/main/java/com/univ/lyricsbridge/data/AppSettings.java`
- Modify: `app/src/main/java/com/univ/lyricsbridge/overlay/OverlayColorPalette.java`
- Modify: `app/src/main/java/com/univ/lyricsbridge/ui/CarModeActivity.java`

- [x] **Step 1: 为 `AppSettings` 添加独立持久化值**

增加默认值、偏好键和读写方法：

```java
public static final int DEFAULT_OVERLAY_CURRENT_LINE_COLOR = 0xFFFFCD53;
private static final String OVERLAY_CURRENT_LINE_COLOR = "overlay_current_line_color";

public static int getOverlayCurrentLineColor(Context context) {
    return preferences(context).getInt(
            OVERLAY_CURRENT_LINE_COLOR, DEFAULT_OVERLAY_CURRENT_LINE_COLOR);
}

public static void setOverlayCurrentLineColor(Context context, int color) {
    preferences(context).edit().putInt(OVERLAY_CURRENT_LINE_COLOR, color).apply();
}
```

- [x] **Step 2: 让默认色进入现有预设色板**

在 `OverlayColorPalette` 中公开 `DEFAULT_CURRENT_LINE_COLOR`，值取自 `AppSettings.DEFAULT_OVERLAY_CURRENT_LINE_COLOR`；现有金色预设改为引用该常量，现有 `CURRENT_LINE_COLOR` 保留为兼容别名。

- [x] **Step 3: 为车机设置页增加独立颜色目标**

把现有颜色选择目标从布尔值扩展为私有枚举 `SUNG`、`UNSUNG`、`CURRENT_LINE`。新增 `currentLineColorButton`，标题为“当前行”；弹窗仍使用 `OverlayColorPalette.labels()` 和 `colorAt()`。选色时只写对应设置、更新对应按钮，再调用 `refreshVisibleOverlay()`。

### Task 3：把当前行颜色接入悬浮歌词

**Files:**
- Modify: `app/src/main/java/com/univ/lyricsbridge/overlay/LyricsOverlayController.java`

- [x] **Step 1: 加载并刷新当前行颜色**

增加 `currentLineColor` 字段，在构造器和 `refreshAppearance()` 中调用 `AppSettings.getOverlayCurrentLineColor(context)`。

- [x] **Step 2: 应用到句级和逐字歌词**

`applyTextOpacity()` 中当前行使用 `withOpacity(currentLineColor)`。`setCurrentLyricProgress()` 创建 `CharacterProgressSpan` 时，已唱字使用当前行颜色，未唱字继续使用 `unsungColor`。上一句仍使用 `sungColor`，下一句仍使用 `unsungColor`。

### Task 4：验证并生成测试 APK

**Files:**
- Generate: `app/build/outputs/apk/debug/app-debug.apk`
- Copy: `dist/univ-lyrics-bridge-debug.apk`

- [x] **Step 1: 运行目标单测**

运行 `testDebugUnitTest --tests com.univ.lyricsbridge.overlay.OverlayColorPaletteTest`，预期通过。

- [x] **Step 2: 运行完整单测和 Android Debug 构建**

运行 `testDebugUnitTest assembleDebug`，预期任务成功并生成 `app/build/outputs/apk/debug/app-debug.apk`。

- [x] **Step 3: 复制 APK 并核对产物**

复制到 `dist/univ-lyrics-bridge-debug.apk`，核对文件存在且大小大于 0；最终报告 APK 下载路径和构建结果。
