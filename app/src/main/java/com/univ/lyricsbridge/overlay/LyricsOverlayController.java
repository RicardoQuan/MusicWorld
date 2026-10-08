package com.univ.lyricsbridge.overlay;

import android.content.Context;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.text.SpannableString;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.univ.lyricsbridge.data.AppSettings;
import com.univ.lyricsbridge.data.CarStateStore;
import com.univ.lyricsbridge.lyric.LyricWindow;
import com.univ.lyricsbridge.model.LyricLine;
import com.univ.lyricsbridge.model.LyricWord;
import com.univ.lyricsbridge.model.PlaybackSnapshot;
import com.univ.lyricsbridge.model.TrackInfo;
import com.univ.lyricsbridge.sync.PlaybackSyncEngine;
import java.util.List;

/** A touch-through, fully transparent three-line lyrics overlay. */
public final class LyricsOverlayController {
    private final Context context;
    private final WindowManager windowManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideRequested;
    private final Runnable refreshTicker = new Runnable() {
        @Override
        public void run() {
            if (!attached) return;
            render();
            mainHandler.postDelayed(this, 100);
        }
    };

    private WindowManager.LayoutParams params;
    private LinearLayout root;
    private LinearLayout lyricsContainer;
    private TextView previousLyricView;
    private TextView currentLyricView;
    private TextView nextLyricView;
    private TextView metadataView;
    private CarStateStore.State state = CarStateStore.current();
    private int textSizeSp;
    private float opacity;
    private int sungColor;
    private int unsungColor;
    private int currentLineColor;
    private int touchSlop;
    private boolean attached;
    private boolean positionInitialized;
    private boolean dragArmed;
    private boolean dragging;
    private boolean movedBeforeLongPress;
    private float downRawX;
    private float downRawY;
    private int downWindowX;
    private int downWindowY;
    private int lastLineIndex = Integer.MIN_VALUE;
    private String lastTrackKey = "";
    private final int[] screenLocation = new int[2];
    private final Runnable armDrag = new Runnable() {
        @Override
        public void run() {
            dragArmed = true;
        }
    };

    public LyricsOverlayController(Context context) {
        this(context, null);
    }

    public LyricsOverlayController(Context context, Runnable hideRequested) {
        this.context = context.getApplicationContext();
        this.hideRequested = hideRequested;
        windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
        textSizeSp = AppSettings.getOverlayTextSize(context);
        opacity = AppSettings.getOverlayTextOpacity(context);
        sungColor = AppSettings.getOverlaySungColor(context);
        unsungColor = AppSettings.getOverlayUnsungColor(context);
        currentLineColor = AppSettings.getOverlayCurrentLineColor(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        buildView();
    }

    public boolean show() {
        if (attached) return true;
        if (windowManager == null || !android.provider.Settings.canDrawOverlays(context)) return false;
        try {
            windowManager.addView(root, params);
            attached = true;
            positionInitialized = false;
            root.post(this::positionOverlayInitially);
            refreshAppearance();
            render();
            mainHandler.removeCallbacks(refreshTicker);
            mainHandler.post(refreshTicker);
            return true;
        } catch (WindowManager.BadTokenException | SecurityException exception) {
            attached = false;
            return false;
        }
    }

    public void hide() {
        mainHandler.removeCallbacks(refreshTicker);
        if (!attached) return;
        try {
            windowManager.removeView(root);
        } catch (IllegalArgumentException ignored) {
            // The system may already have removed the overlay window.
        }
        attached = false;
    }

    public void dispose() {
        hide();
    }

    public void update(CarStateStore.State newState) {
        state = newState == null ? CarStateStore.current() : newState;
        if (attached) render();
    }

    public void refreshAppearance() {
        textSizeSp = AppSettings.getOverlayTextSize(context);
        opacity = AppSettings.getOverlayTextOpacity(context);
        sungColor = AppSettings.getOverlaySungColor(context);
        unsungColor = AppSettings.getOverlayUnsungColor(context);
        currentLineColor = AppSettings.getOverlayCurrentLineColor(context);
        if (root == null) return;
        currentLyricView.setTextSize(textSizeSp);
        previousLyricView.setTextSize(Math.max(16, textSizeSp - 8));
        nextLyricView.setTextSize(Math.max(16, textSizeSp - 8));
        applyTextOpacity();
    }

    public void resetPosition() {
        AppSettings.clearOverlayPosition(context);
        positionInitialized = false;
        positionOverlayInitially();
    }

    public boolean isVisible() {
        return attached;
    }

    private void buildView() {
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setClipToPadding(false);
        root.setClipChildren(false);
        root.setOnTouchListener(this::handleOverlayTouch);
        root.addOnLayoutChangeListener((view, left, top, right, bottom,
                                        oldLeft, oldTop, oldRight, oldBottom) -> {
            alignMetadataToLeftEdge();
            positionOverlayInitially();
        });

        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int maxTextWidth = Math.max(dp(200), screenWidth - dp(24));
        metadataView = text("", 14, Color.WHITE);
        metadataView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        metadataView.setSingleLine(true);
        metadataView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        metadataView.setMaxWidth(maxTextWidth);
        metadataView.setOnTouchListener(this::handleOverlayTouch);
        metadataView.addOnLayoutChangeListener((view, left, top, right, bottom,
                                                oldLeft, oldTop, oldRight, oldBottom) -> alignMetadataToLeftEdge());
        root.addView(metadataView, wrapWrap());

        lyricsContainer = new LinearLayout(context);
        lyricsContainer.setOrientation(LinearLayout.VERTICAL);
        lyricsContainer.setGravity(Gravity.CENTER);
        lyricsContainer.setClipToPadding(false);
        lyricsContainer.setClipChildren(false);

        previousLyricView = text("", Math.max(16, textSizeSp - 8), Color.GRAY);
        currentLyricView = text("", textSizeSp, Color.YELLOW);
        nextLyricView = text("", Math.max(16, textSizeSp - 8), Color.WHITE);
        previousLyricView.setAlpha(1f);
        currentLyricView.setAlpha(1f);
        nextLyricView.setAlpha(1f);
        previousLyricView.setMaxLines(1);
        currentLyricView.setMaxLines(2);
        nextLyricView.setMaxLines(1);
        previousLyricView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        currentLyricView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        nextLyricView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        previousLyricView.setMaxWidth(maxTextWidth);
        currentLyricView.setMaxWidth(maxTextWidth);
        nextLyricView.setMaxWidth(maxTextWidth);
        View.OnTouchListener dragTouchListener = this::handleOverlayTouch;
        previousLyricView.setOnTouchListener(dragTouchListener);
        currentLyricView.setOnTouchListener(dragTouchListener);
        nextLyricView.setOnTouchListener(dragTouchListener);
        lyricsContainer.addView(previousLyricView, wrapWrap());
        lyricsContainer.addView(currentLyricView, wrapWrap());
        lyricsContainer.addView(nextLyricView, wrapWrap());
        root.addView(lyricsContainer, wrapWrap());

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;
        applyTextOpacity();
    }

    private void positionOverlayInitially() {
        if (!attached || positionInitialized || root.getWidth() <= 0 || root.getHeight() <= 0) return;
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int x;
        int y;
        if (AppSettings.hasOverlayPosition(context)) {
            x = AppSettings.getOverlayX(context);
            y = AppSettings.getOverlayY(context);
        } else {
            OverlayDragPolicy.Position centered = OverlayDragPolicy.resetToCenter(
                    screenWidth, screenHeight, root.getWidth(), root.getHeight());
            x = centered.x;
            y = centered.y;
        }
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = OverlayDragPolicy.clampX(x, screenWidth, root.getWidth());
        params.y = OverlayDragPolicy.clampY(y, screenHeight, root.getHeight());
        positionInitialized = true;
        windowManager.updateViewLayout(root, params);
    }

    private boolean handleOverlayTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mainHandler.removeCallbacks(armDrag);
                dragArmed = false;
                dragging = false;
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                movedBeforeLongPress = false;
                root.getLocationOnScreen(screenLocation);
                downWindowX = screenLocation[0];
                downWindowY = screenLocation[1];
                mainHandler.postDelayed(armDrag, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_MOVE:
                float distanceX = event.getRawX() - downRawX;
                float distanceY = event.getRawY() - downRawY;
                boolean pastTouchSlop = Math.hypot(distanceX, distanceY) > touchSlop;
                if (!dragArmed && pastTouchSlop) {
                    movedBeforeLongPress = true;
                    mainHandler.removeCallbacks(armDrag);
                }
                if (dragArmed && pastTouchSlop && attached) {
                    dragging = true;
                    int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
                    int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
                    params.gravity = Gravity.TOP | Gravity.LEFT;
                    params.x = OverlayDragPolicy.clampX(
                            Math.round(downWindowX + event.getRawX() - downRawX), screenWidth, root.getWidth());
                    params.y = OverlayDragPolicy.clampY(
                            Math.round(downWindowY + event.getRawY() - downRawY), screenHeight, root.getHeight());
                    windowManager.updateViewLayout(root, params);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mainHandler.removeCallbacks(armDrag);
                if (dragging) AppSettings.setOverlayPosition(context, params.x, params.y);
                else if (event.getActionMasked() == MotionEvent.ACTION_UP
                        && !movedBeforeLongPress) showActionsDialog();
                dragArmed = false;
                dragging = false;
                return true;
            default:
                return true;
        }
    }

    private void render() {
        TrackInfo track = state.getTrack();
        String trackKey = track == null ? "" : track.getTitle() + "\u0000" + track.getArtist();
        String metadata = track == null ? "" : TrackMetadataFormatter.format(track.getTitle(), track.getArtist());
        if (!metadata.contentEquals(metadataView.getText())) {
            metadataView.setText(metadata);
            metadataView.setVisibility(metadata.isEmpty() ? View.GONE : View.VISIBLE);
            alignMetadataToLeftEdge();
        }
        if (!trackKey.equals(lastTrackKey)) {
            lastTrackKey = trackKey;
            lastLineIndex = Integer.MIN_VALUE;
        }
        List<LyricLine> lines = state.getLines();
        if (lines.isEmpty()) {
            if (previousLyricView.getText().length() > 0 || currentLyricView.getText().length() > 0
                    || nextLyricView.getText().length() > 0) setLines("", "", "");
            lastLineIndex = Integer.MIN_VALUE;
            return;
        }
        PlaybackSnapshot playback = state.getPlayback();
        long positionMs = PlaybackSyncEngine.positionAt(playback, SystemClock.elapsedRealtime());
        int index = PlaybackSyncEngine.lineIndexAt(lines, positionMs);
        LyricWindow window = LyricWindow.at(lines, index);
        boolean lineChanged = index != lastLineIndex;
        boolean textChanged = !window.getPrevious().contentEquals(previousLyricView.getText())
                || !window.getCurrent().contentEquals(currentLyricView.getText())
                || !window.getNext().contentEquals(nextLyricView.getText());
        if (lineChanged || textChanged) {
            setLines(window.getPrevious(), window.getCurrent(), window.getNext());
        }
        setCurrentLyricProgress(lines.get(Math.max(0, Math.min(index, lines.size() - 1))), positionMs);
        if (lineChanged && lastLineIndex != Integer.MIN_VALUE) animateAdvance();
        lastLineIndex = index;
    }

    private void setLines(String previous, String current, String next) {
        previousLyricView.setText(previous == null ? "" : previous);
        currentLyricView.setText(current == null ? "" : current);
        nextLyricView.setText(next == null ? "" : next);
        applyTextOpacity();
    }

    private void animateAdvance() {
        lyricsContainer.animate().cancel();
        lyricsContainer.setAlpha(0.15f);
        lyricsContainer.setTranslationY(dp(8));
        lyricsContainer.animate().alpha(1f).translationY(0)
                .setDuration(AppSettings.getOverlayScrollDuration(context))
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void setCurrentLyricProgress(LyricLine line, long positionMs) {
        if (line == null || line.getWords().isEmpty()) return;
        String text = line.getText();
        SpannableString styled = new SpannableString(text);
        int searchFrom = 0;
        for (LyricWord word : line.getWords()) {
            String segment = word.getText();
            if (segment.isEmpty()) continue;
            int segmentStart = text.indexOf(segment, searchFrom);
            if (segmentStart < 0) segmentStart = searchFrom;
            int segmentEnd = Math.min(text.length(), segmentStart + segment.length());
            int charCount = text.codePointCount(segmentStart, segmentEnd);
            if (charCount <= 0) continue;
            int offset = segmentStart;
            for (int charIndex = 0; charIndex < charCount && offset < segmentEnd; charIndex++) {
                int next = offset + Character.charCount(text.codePointAt(offset));
                long charStart = word.getStartMs() + word.getDurationMs() * charIndex / charCount;
                long charEnd = word.getStartMs() + word.getDurationMs() * (charIndex + 1) / charCount;
                float progress = charEnd <= charStart ? (positionMs >= charEnd ? 1f : 0f)
                        : (positionMs - charStart) / (float) (charEnd - charStart);
                String glyph = text.substring(offset, next);
                styled.setSpan(new CharacterProgressSpan(glyph, withOpacity(currentLineColor),
                                withOpacity(unsungColor), progress),
                        offset, next, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
                offset = next;
            }
            searchFrom = segmentEnd;
        }
        currentLyricView.setText(styled);
    }

    private void showActionsDialog() {
        if (hideRequested == null || !android.provider.Settings.canDrawOverlays(context)) return;
        AlertDialog dialog = new AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("歌词悬浮窗")
                .setMessage("长按歌词可拖动位置")
                .setPositiveButton("隐藏歌词", (d, which) -> hideRequested.run())
                .setNegativeButton("取消", null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.rgb(27, 37, 49)));
        }
        dialog.setOnShowListener(ignored -> {
            android.widget.TextView title = dialog.findViewById(android.R.id.title);
            android.widget.TextView message = dialog.findViewById(android.R.id.message);
            if (title != null) title.setTextColor(Color.WHITE);
            if (message != null) message.setTextColor(Color.rgb(220, 228, 235));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.rgb(242, 205, 105));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.rgb(170, 190, 205));
        });
        dialog.show();
    }

    private void applyTextOpacity() {
        previousLyricView.setTextColor(withOpacity(sungColor));
        currentLyricView.setTextColor(withOpacity(currentLineColor));
        nextLyricView.setTextColor(withOpacity(unsungColor));
        metadataView.setTextColor(withOpacity(Color.rgb(205, 214, 222)));
        int shadowColor = Color.argb(Math.round(opacity * 96), 0, 0, 0);
        previousLyricView.setShadowLayer(dp(2), 0, dp(1), shadowColor);
        currentLyricView.setShadowLayer(dp(2), 0, dp(1), shadowColor);
        nextLyricView.setShadowLayer(dp(2), 0, dp(1), shadowColor);
        metadataView.setShadowLayer(dp(2), 0, dp(1), shadowColor);
    }

    private void alignMetadataToLeftEdge() {
        if (root == null || metadataView == null || metadataView.getVisibility() != View.VISIBLE) return;
        metadataView.setTranslationX(-metadataView.getLeft());
    }

    private int withOpacity(int color) {
        return Color.argb(Math.round(opacity * 255), Color.red(color), Color.green(color), Color.blue(color));
    }

    private TextView text(String value, int sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setShadowLayer(dp(2), 0, dp(1), Color.BLACK);
        return view;
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
