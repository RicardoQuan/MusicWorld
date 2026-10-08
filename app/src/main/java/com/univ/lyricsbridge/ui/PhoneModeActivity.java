package com.univ.lyricsbridge.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.univ.lyricsbridge.lyric.LocalLyricStore;
import com.univ.lyricsbridge.lyric.LrcParser;
import com.univ.lyricsbridge.lyric.NetEaseApiClient;
import com.univ.lyricsbridge.lyric.NetEaseSessionStore;
import com.univ.lyricsbridge.MainActivity;
import com.univ.lyricsbridge.data.AppSettings;
import com.univ.lyricsbridge.media.LyricsNotificationListenerService;
import com.univ.lyricsbridge.media.MediaSessionMonitor;
import com.univ.lyricsbridge.media.MediaStateStore;
import com.univ.lyricsbridge.model.PlaybackSnapshot;
import com.univ.lyricsbridge.model.TrackInfo;
import com.univ.lyricsbridge.service.PhoneSenderService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PhoneModeActivity extends Activity {
    private static final int BG = Color.rgb(15, 25, 35);
    private static final int CARD = Color.rgb(27, 43, 56);
    private static final int WHITE = Color.rgb(244, 248, 250);
    private static final int MUTED = Color.rgb(169, 186, 198);
    private static final int GOLD = Color.rgb(242, 205, 105);
    private static final int TEAL = Color.rgb(54, 148, 135);
    private static final int SECONDARY = Color.rgb(51, 68, 79);
    private static final int REQUEST_IMPORT_LRC = 401;
    private static final int MAX_IMPORT_BYTES = 256 * 1024;
    private static final String NETEASE_WEB_LOGIN_URL = "https://music.163.com/login?from=web";
    private static final String NETEASE_WEB_ORIGIN = "https://music.163.com/";
    private TextView permissionStatus;
    private TextView playbackStatus;
    private TextView serviceStatus;
    private TextView loginStatus;
    private TextView dialogLoginStatus;
    private Button loginEntryButton;
    private Button logoutButton;
    private AlertDialog loginDialog;
    private EditText accountInput;
    private EditText passwordInput;
    private EditText verificationCodeInput;
    private Button loginButton;
    private Button smsCodeButton;
    private Button loginModeButton;
    private LinearLayout qrLoginPanel;
    private LinearLayout passwordLoginPanel;
    private ImageView qrCodeImage;
    private TextView qrLoginStatus;
    private Button qrRefreshButton;
    private Button qrManualLoginButton;
    private Button webLoginButton;
    private Button webLoginBackButton;
    private Button webLoginReloadButton;
    private LinearLayout webLoginPanel;
    private WebView netEaseLoginWebView;
    private TextView webLoginStatus;
    private Runnable webLoginCookiePoll;
    private int webLoginGeneration;
    private boolean webLoginSaving;
    private String qrLoginKey = "";
    private int qrLoginGeneration;
    private boolean qrPollRequestRunning;
    private Runnable scheduledQrPoll;
    private boolean smsLoginMode;
    private long smsCodeResendAtMs;
    private NetEaseSessionStore sessionStore;
    private TrackInfo trackForLrcImport;
    private final NetEaseApiClient netEaseApi = new NetEaseApiClient();
    private final ExecutorService loginWorker = Executors.newSingleThreadExecutor();
    private Runnable pendingPermissionAction;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable smsCodeCountdown = new Runnable() {
        @Override
        public void run() {
            if (smsCodeButton == null) return;
            long remainingMs = smsCodeResendAtMs - SystemClock.elapsedRealtime();
            if (remainingMs <= 0) {
                smsCodeButton.setText("发送验证码");
                smsCodeButton.setEnabled(true);
                return;
            }
            smsCodeButton.setText("重新发送验证码（" + ((remainingMs + 999) / 1000) + "秒）");
            smsCodeButton.setEnabled(false);
            statusHandler.postDelayed(this, 1000);
        }
    };
    private final Runnable statusTicker = new Runnable() {
        @Override
        public void run() {
            updatePermissionStatus();
            renderMediaState(MediaStateStore.current());
            refreshServiceStatus();
            statusHandler.postDelayed(this, 1000);
        }
    };
    private final MediaStateStore.Listener mediaListener = this::renderMediaState;
    private boolean senderAutoStartPending;
    private String senderAutoStartHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sessionStore = new NetEaseSessionStore(this);
        getWindow().setStatusBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(dp(24), dp(28), dp(24), dp(24));
        layout.setBackgroundColor(BG);

        TextView title = text("手机发送端", 26, WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        layout.addView(title, matchWrap());

        LinearLayout loginCard = card();
        addCardTitle(loginCard, "网易云账号");
        TextView loginHint = text("登录账号后优先获取网易云歌词，也可为当前歌曲导入本地 LRC。", 13, MUTED);
        add(loginCard, loginHint, dp(6));
        loginStatus = text("正在读取网易云登录状态…", 14, GOLD);
        add(loginCard, loginStatus, dp(8));
        loginEntryButton = actionButton("登录或管理网易云账号", TEAL,
                view -> showNetEaseLoginDialog());
        add(loginCard, loginEntryButton, dp(6));
        logoutButton = actionButton("退出网易云账号", SECONDARY, view -> {
            sessionStore.clear();
            renderNetEaseLoginStatus("已退出网易云账号");
            refreshCurrentTrackLyrics();
        });
        add(loginCard, logoutButton, dp(4));
        add(layout, loginCard, dp(16));

        LinearLayout mediaCard = card();
        addCardTitle(mediaCard, "媒体与歌词");
        permissionStatus = text("正在检查媒体读取授权…", 14, MUTED);
        add(mediaCard, permissionStatus, dp(7));
        Button permissionButton = actionButton("打开通知使用权设置", SECONDARY, view ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        add(mediaCard, permissionButton, dp(6));
        playbackStatus = text("网易云歌曲状态尚未读取", 14, WHITE);
        playbackStatus.setMaxLines(5);
        add(mediaCard, playbackStatus, dp(10));

        Button importLrcButton = actionButton("导入当前歌曲的本地 LRC", TEAL,
                view -> importCurrentTrackLrc());
        add(mediaCard, importLrcButton, dp(6));
        add(layout, mediaCard, dp(12));

        LinearLayout bluetoothCard = card();
        addCardTitle(bluetoothCard, "蓝牙发送");
        serviceStatus = text("发送服务尚未启动", 14, GOLD);
        add(bluetoothCard, serviceStatus, dp(7));

        Button startButton = actionButton("开始蓝牙发送", TEAL, view -> startSending());
        add(bluetoothCard, startButton, dp(8));

        Button stopButton = actionButton("停止发送", SECONDARY, view -> {
            stopService(new Intent(this, PhoneSenderService.class));
            refreshServiceStatus();
        });
        add(bluetoothCard, stopButton, dp(5));
        add(layout, bluetoothCard, dp(12));

        Button backButton = actionButton("返回角色选择", SECONDARY, view -> returnToRoleSelection());
        add(layout, backButton, dp(12));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(layout);
        setContentView(scroll);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        GradientDrawable background = new GradientDrawable();
        background.setColor(CARD);
        background.setCornerRadius(dp(14));
        card.setBackground(background);
        return card;
    }

    private void addCardTitle(LinearLayout card, String label) {
        TextView heading = text(label, 16, WHITE);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        add(card, heading, 0);
    }

    private Button actionButton(String label, int color, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(WHITE);
        button.setTextSize(14);
        button.setBackgroundTintList(ColorStateList.valueOf(color));
        button.setOnClickListener(listener);
        return button;
    }

    private void returnToRoleSelection() {
        AppSettings.setRole(this, "");
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (netEaseLoginWebView != null) netEaseLoginWebView.onResume();
        updatePermissionStatus();
        refreshServiceStatus();
        maybeAutoStartSender();
        renderNetEaseLoginStatus(null);
        MediaStateStore.addListener(mediaListener);
        statusHandler.removeCallbacks(statusTicker);
        statusHandler.post(statusTicker);
    }

    @Override
    protected void onPause() {
        if (netEaseLoginWebView != null) netEaseLoginWebView.onPause();
        MediaStateStore.removeListener(mediaListener);
        statusHandler.removeCallbacks(statusTicker);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        statusHandler.removeCallbacks(smsCodeCountdown);
        invalidateQrLogin();
        stopWebLoginCookiePoll();
        destroyNetEaseLoginWebView();
        loginWorker.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_LRC) return;
        TrackInfo targetTrack = trackForLrcImport;
        trackForLrcImport = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (targetTrack == null) {
            showToast("没有正在播放的歌曲，请先播放歌曲后再导入。");
            return;
        }
        try {
            String lrc = readLrc(data.getData());
            if (LrcParser.parse(lrc).isEmpty()) {
                showToast("文件中没有可识别的时间戳歌词，请选择有效的 .lrc 文件。");
                return;
            }
            new LocalLyricStore(this).save(targetTrack, lrc);
            showToast("已绑定本地 LRC：" + targetTrack.getTitle());
            refreshCurrentTrackLyrics();
        } catch (IllegalArgumentException exception) {
            showToast(exception.getMessage());
        } catch (IOException exception) {
            showToast("无法读取歌词文件：" + safeMessage(exception));
        }
    }

    private void importCurrentTrackLrc() {
        MediaStateStore.State state = MediaStateStore.current();
        TrackInfo track = state == null || !state.hasSession() ? null : state.getTrack();
        if (track == null || track.getTitle().isEmpty() || track.getArtist().isEmpty()) {
            showToast("请先播放网易云歌曲并等待歌曲名、歌手显示出来。");
            return;
        }
        trackForLrcImport = track;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"text/plain", "application/octet-stream", "application/x-subrip"});
        intent.putExtra(Intent.EXTRA_TITLE, "选择当前歌曲的 LRC 歌词");
        try {
            startActivityForResult(intent, REQUEST_IMPORT_LRC);
        } catch (ActivityNotFoundException exception) {
            trackForLrcImport = null;
            showToast("系统没有可用的文件选择器。");
        }
    }

    private String readLrc(Uri uri) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("文件无法打开");
            byte[] buffer = new byte[8192];
            int count;
            int total = 0;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_IMPORT_BYTES) throw new IOException("文件超过 256 KB");
                output.write(buffer, 0, count);
            }
        }
        return decodeLrc(output.toByteArray());
    }

    private static String decodeLrc(byte[] bytes) throws IOException {
        int offset = 0;
        Charset charset = StandardCharsets.UTF_8;
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xef
                && (bytes[1] & 0xff) == 0xbb && (bytes[2] & 0xff) == 0xbf) {
            offset = 3;
        } else if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xfe) {
            charset = StandardCharsets.UTF_16LE;
            offset = 2;
        } else if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xfe
                && (bytes[1] & 0xff) == 0xff) {
            charset = StandardCharsets.UTF_16BE;
            offset = 2;
        }
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
        } catch (CharacterCodingException exception) {
            try {
                return Charset.forName("GB18030").newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException invalidText) {
                throw new IOException("不支持该歌词文件编码", invalidText);
            }
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? "文件格式不支持" : message;
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showNetEaseLoginDialog() {
        smsLoginMode = false;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(8), dp(4), dp(8), dp(4));
        content.setBackgroundColor(Color.rgb(30, 42, 56));

        qrLoginPanel = new LinearLayout(this);
        qrLoginPanel.setOrientation(LinearLayout.VERTICAL);
        qrLoginPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        qrLoginPanel.setPadding(dp(10), dp(8), dp(10), dp(8));
        TextView qrHint = text("使用另一台已登录网易云音乐的设备扫码，并在官方 App 确认", 14,
                Color.rgb(215, 225, 233));
        qrHint.setGravity(Gravity.CENTER);
        add(qrLoginPanel, qrHint, 0);
        qrCodeImage = new ImageView(this);
        qrCodeImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qrCodeImage.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams qrImageParams = new LinearLayout.LayoutParams(dp(232), dp(232));
        qrImageParams.gravity = Gravity.CENTER_HORIZONTAL;
        qrImageParams.topMargin = dp(12);
        qrLoginPanel.addView(qrCodeImage, qrImageParams);
        qrLoginStatus = text("正在生成二维码…", 14, Color.rgb(255, 218, 130));
        qrLoginStatus.setGravity(Gravity.CENTER);
        qrLoginStatus.setPadding(0, dp(10), 0, dp(6));
        add(qrLoginPanel, qrLoginStatus, dp(4));
        qrRefreshButton = new Button(this);
        qrRefreshButton.setText("刷新二维码");
        qrRefreshButton.setAllCaps(false);
        qrRefreshButton.setOnClickListener(view -> startQrLogin());
        add(qrLoginPanel, qrRefreshButton, dp(4));
        qrManualLoginButton = new Button(this);
        qrManualLoginButton.setText("密码 / 短信登录");
        qrManualLoginButton.setAllCaps(false);
        qrManualLoginButton.setOnClickListener(view -> {
            invalidateQrLogin();
            qrLoginPanel.setVisibility(View.GONE);
            passwordLoginPanel.setVisibility(View.VISIBLE);
        });
        add(qrLoginPanel, qrManualLoginButton, dp(4));
        webLoginButton = new Button(this);
        webLoginButton.setText("网页登录（当前设备）");
        webLoginButton.setAllCaps(false);
        webLoginButton.setOnClickListener(view -> openNetEaseWebLogin());
        add(qrLoginPanel, webLoginButton, dp(4));
        add(content, qrLoginPanel, 0);

        webLoginPanel = new LinearLayout(this);
        webLoginPanel.setOrientation(LinearLayout.VERTICAL);
        webLoginPanel.setPadding(dp(10), dp(8), dp(10), dp(8));
        webLoginPanel.setBackgroundColor(Color.rgb(30, 42, 56));
        webLoginPanel.setVisibility(View.GONE);
        TextView webHint = text("在本应用内登录 music.163.com。登录会话将加密保存在本机，用于网易云歌词查询。", 13,
                Color.rgb(215, 225, 233));
        add(webLoginPanel, webHint, 0);
        webLoginStatus = text("网页登录完成后会自动保存会话。", 14, Color.rgb(255, 218, 130));
        add(webLoginPanel, webLoginStatus, dp(4));
        webLoginBackButton = new Button(this);
        webLoginBackButton.setText("返回二维码登录");
        webLoginBackButton.setAllCaps(false);
        webLoginBackButton.setOnClickListener(view -> {
            stopWebLoginCookiePoll();
            webLoginGeneration++;
            webLoginPanel.setVisibility(View.GONE);
            qrLoginPanel.setVisibility(View.VISIBLE);
            startQrLogin();
        });
        add(webLoginPanel, webLoginBackButton, dp(4));
        webLoginReloadButton = new Button(this);
        webLoginReloadButton.setText("刷新网页登录页");
        webLoginReloadButton.setAllCaps(false);
        webLoginReloadButton.setOnClickListener(view -> {
            if (netEaseLoginWebView != null) {
                webLoginGeneration++;
                webLoginSaving = false;
                setWebLoginStatus("正在刷新网易云登录页…", false);
                netEaseLoginWebView.loadUrl(NETEASE_WEB_LOGIN_URL);
                scheduleWebLoginCookiePoll(webLoginGeneration);
            }
        });
        add(webLoginPanel, webLoginReloadButton, dp(2));
        add(content, webLoginPanel, 0);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(8));
        form.setBackgroundColor(Color.rgb(30, 42, 56));
        passwordLoginPanel = form;
        passwordLoginPanel.setVisibility(View.GONE);

        Button qrBackButton = new Button(this);
        qrBackButton.setText("返回二维码登录");
        qrBackButton.setAllCaps(false);
        qrBackButton.setOnClickListener(view -> {
            passwordLoginPanel.setVisibility(View.GONE);
            qrLoginPanel.setVisibility(View.VISIBLE);
            startQrLogin();
        });
        add(form, qrBackButton, 0);

        TextView hint = text("网易云账号仅用于优先查询歌词。登录请求直接发送到网易云，未匹配时会尝试 LRCLIB。", 13,
                Color.rgb(215, 225, 233));
        add(form, hint, 0);
        accountInput = credentialInput("手机号或邮箱", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        add(form, accountInput, dp(10));
        passwordInput = credentialInput("网易云密码", InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setSaveEnabled(false);
        passwordInput.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        add(form, passwordInput, dp(8));
        verificationCodeInput = credentialInput("短信验证码", InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        verificationCodeInput.setVisibility(View.GONE);
        add(form, verificationCodeInput, dp(8));

        smsCodeButton = new Button(this);
        smsCodeButton.setText("发送短信验证码");
        smsCodeButton.setAllCaps(false);
        smsCodeButton.setOnClickListener(view -> requestSmsCode());
        smsCodeButton.setVisibility(View.GONE);
        add(form, smsCodeButton, dp(4));
        loginButton = new Button(this);
        loginButton.setText("密码登录网易云");
        loginButton.setAllCaps(false);
        loginButton.setOnClickListener(view -> loginNetEase());
        add(form, loginButton, dp(8));
        loginModeButton = new Button(this);
        loginModeButton.setText("改用短信验证码登录");
        loginModeButton.setAllCaps(false);
        loginModeButton.setOnClickListener(view -> setSmsLoginMode(!smsLoginMode));
        add(form, loginModeButton, dp(4));
        dialogLoginStatus = text("", 14, Color.rgb(255, 218, 130));
        dialogLoginStatus.setPadding(0, dp(8), 0, dp(4));
        add(form, dialogLoginStatus, dp(4));

        add(content, passwordLoginPanel, 0);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);

        loginDialog = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("网易云账号登录")
                .setView(scroll)
                .setNegativeButton("关闭", null)
                .create();
        loginDialog.setOnDismissListener(ignored -> {
            invalidateQrLogin();
            webLoginGeneration++;
            stopWebLoginCookiePoll();
            destroyNetEaseLoginWebView();
            if (qrCodeImage != null) qrCodeImage.setImageDrawable(null);
        });
        loginDialog.setOnShowListener(ignored -> {
            android.view.Window window = loginDialog.getWindow();
            if (window != null) {
                GradientDrawable background = new GradientDrawable();
                background.setColor(Color.rgb(30, 42, 56));
                background.setCornerRadius(dp(18));
                window.setBackgroundDrawable(background);
            }
            TextView title = loginDialog.findViewById(android.R.id.title);
            if (title != null) title.setTextColor(Color.WHITE);
            loginDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(Color.rgb(242, 205, 105));
        });
        loginDialog.show();
        renderNetEaseLoginStatus(null);
        startQrLogin();
    }

    private void openNetEaseWebLogin() {
        invalidateQrLogin();
        stopWebLoginCookiePoll();
        webLoginGeneration++;
        webLoginSaving = false;
        qrLoginPanel.setVisibility(View.GONE);
        passwordLoginPanel.setVisibility(View.GONE);
        webLoginPanel.setVisibility(View.VISIBLE);
        setWebLoginStatus("正在打开网易云网页登录页…", false);
        ensureNetEaseLoginWebView();
        netEaseLoginWebView.loadUrl(NETEASE_WEB_LOGIN_URL);
        scheduleWebLoginCookiePoll(webLoginGeneration);
    }

    private void ensureNetEaseLoginWebView() {
        if (netEaseLoginWebView != null) return;
        WebView webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        webView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, false);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return blockNonNetEaseUrl(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url == null ? "" : url);
                return blockNonNetEaseUrl(uri);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (webLoginPanel != null && webLoginPanel.getVisibility() == View.VISIBLE) {
                    setWebLoginStatus("网页已打开；完成登录后会自动保存会话。", false);
                    scheduleWebLoginCookiePoll(webLoginGeneration);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    setWebLoginStatus("网页登录页加载失败，请检查网络后刷新。", true);
                }
            }
        });
        int height = Math.min(dp(430), Math.max(dp(180), getResources().getDisplayMetrics().heightPixels - dp(220)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, height);
        params.topMargin = dp(4);
        webLoginPanel.addView(webView, params);
        netEaseLoginWebView = webView;
    }

    private boolean isAllowedNetEaseWebUrl(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        return host != null && ("163.com".equalsIgnoreCase(host)
                || host.toLowerCase(java.util.Locale.ROOT).endsWith(".163.com"));
    }

    private boolean blockNonNetEaseUrl(Uri uri) {
        if (isAllowedNetEaseWebUrl(uri)) return false;
        setWebLoginStatus("为保护登录会话，登录页只允许在网易云 HTTPS 域名内跳转。", true);
        return true;
    }

    private void scheduleWebLoginCookiePoll(int generation) {
        if (!isWebLoginActive(generation) || webLoginSaving) return;
        stopWebLoginCookiePoll();
        webLoginCookiePoll = () -> checkWebLoginCookie(generation);
        statusHandler.postDelayed(webLoginCookiePoll, 1000);
    }

    private void checkWebLoginCookie(int generation) {
        webLoginCookiePoll = null;
        if (!isWebLoginActive(generation) || webLoginSaving) return;
        String cookie = CookieManager.getInstance().getCookie(NETEASE_WEB_ORIGIN);
        if (hasUsableNetEaseSession(cookie)) {
            webLoginSaving = true;
            setWebLoginStatus("已检测到网易云会话，正在加密保存…", false);
            loginWorker.execute(() -> {
                String message;
                boolean saved = false;
                try {
                    sessionStore.saveCookie(cookie);
                    message = "已保存网易云网页登录会话；正在刷新当前歌曲歌词。";
                    saved = true;
                } catch (RuntimeException exception) {
                    message = "检测到网页登录，但无法安全保存会话；请稍后重试。";
                }
                final String finalMessage = message;
                final boolean finalSaved = saved;
                runOnUiThread(() -> {
                    webLoginSaving = false;
                    stopWebLoginCookiePoll();
                    renderNetEaseLoginStatus(finalMessage);
                    if (finalSaved) {
                        if (loginDialog != null && loginDialog.isShowing()) loginDialog.dismiss();
                        refreshCurrentTrackLyrics();
                    } else {
                        setWebLoginStatus(finalMessage, true);
                    }
                });
            });
            return;
        }
        scheduleWebLoginCookiePoll(generation);
    }

    private boolean hasUsableNetEaseSession(String cookie) {
        if (cookie == null || cookie.isEmpty()) return false;
        for (String pair : cookie.split(";")) {
            String cleanPair = pair.trim();
            int equals = cleanPair.indexOf('=');
            if (equals <= 0 || !"MUSIC_U".equals(cleanPair.substring(0, equals).trim())) continue;
            String value = cleanPair.substring(equals + 1).trim();
            return !value.isEmpty() && !"undefined".equalsIgnoreCase(value) && !"null".equalsIgnoreCase(value);
        }
        return false;
    }

    private boolean isWebLoginActive(int generation) {
        return generation == webLoginGeneration && loginDialog != null && loginDialog.isShowing()
                && webLoginPanel != null && webLoginPanel.getVisibility() == View.VISIBLE;
    }

    private void stopWebLoginCookiePoll() {
        if (webLoginCookiePoll != null) {
            statusHandler.removeCallbacks(webLoginCookiePoll);
            webLoginCookiePoll = null;
        }
    }

    private void setWebLoginStatus(String message, boolean error) {
        if (webLoginStatus == null) return;
        webLoginStatus.setText(message);
        webLoginStatus.setTextColor(error ? Color.rgb(255, 145, 125) : Color.rgb(255, 218, 130));
    }

    private void destroyNetEaseLoginWebView() {
        if (netEaseLoginWebView == null) return;
        netEaseLoginWebView.stopLoading();
        if (webLoginPanel != null) webLoginPanel.removeView(netEaseLoginWebView);
        netEaseLoginWebView.destroy();
        netEaseLoginWebView = null;
    }

    private void startQrLogin() {
        if (loginDialog == null || !loginDialog.isShowing() || qrLoginPanel == null
                || qrLoginPanel.getVisibility() != View.VISIBLE) return;
        invalidateQrLogin();
        final int generation = qrLoginGeneration;
        if (qrCodeImage != null) qrCodeImage.setImageDrawable(null);
        if (qrRefreshButton != null) qrRefreshButton.setEnabled(false);
        setQrLoginStatus("正在生成二维码…");
        qrPollRequestRunning = true;
        loginWorker.execute(() -> {
            String key = "";
            Bitmap bitmap = null;
            String error = "";
            try {
                key = netEaseApi.createQrLoginKey();
                bitmap = buildQrBitmap(com.univ.lyricsbridge.lyric.NetEaseQrLoginUrl.build(key), dp(232));
            } catch (Exception exception) {
                error = exception.getMessage() == null ? "二维码生成失败，请检查网络后刷新。"
                        : exception.getMessage();
            }
            final String finalKey = key;
            final Bitmap finalBitmap = bitmap;
            final String finalError = error;
            runOnUiThread(() -> {
                if (!isQrDialogActive(generation)) return;
                qrPollRequestRunning = false;
                qrRefreshButton.setEnabled(true);
                if (!finalError.isEmpty()) {
                    boolean challenge = finalError.contains("官方网易云音乐 App")
                            || finalError.contains("安全验证") || finalError.contains("8821");
                    setQrLoginStatus(NetEaseQrStatusText.describe(challenge
                            ? NetEaseApiClient.QrLoginStatus.CHALLENGE
                            : NetEaseApiClient.QrLoginStatus.ERROR, finalError));
                    return;
                }
                qrLoginKey = finalKey;
                qrCodeImage.setImageBitmap(finalBitmap);
                setQrLoginStatus(NetEaseQrStatusText.describe(
                        NetEaseApiClient.QrLoginStatus.WAITING_SCAN, ""));
                scheduleQrPoll(generation);
            });
        });
    }

    private void scheduleQrPoll(int generation) {
        if (!isQrDialogActive(generation) || qrLoginKey.isEmpty()) return;
        if (scheduledQrPoll != null) statusHandler.removeCallbacks(scheduledQrPoll);
        scheduledQrPoll = () -> pollQrLogin(generation);
        statusHandler.postDelayed(scheduledQrPoll, 3000);
    }

    private void pollQrLogin(int generation) {
        scheduledQrPoll = null;
        if (!isQrDialogActive(generation) || qrLoginKey.isEmpty() || qrPollRequestRunning) return;
        qrPollRequestRunning = true;
        String key = qrLoginKey;
        loginWorker.execute(() -> {
            NetEaseApiClient.QrLoginResult result = null;
            String error = "";
            try {
                result = netEaseApi.checkQrLogin(key);
            } catch (Exception exception) {
                error = exception.getMessage() == null
                        ? "二维码查询失败，请检查网络或刷新二维码。" : exception.getMessage();
            }
            final NetEaseApiClient.QrLoginResult finalResult = result;
            final String finalError = error;
            runOnUiThread(() -> {
                if (!isQrDialogActive(generation)) return;
                qrPollRequestRunning = false;
                if (finalResult == null) {
                    boolean challenge = finalError.contains("官方网易云音乐 App")
                            || finalError.contains("安全验证") || finalError.contains("8821");
                    setQrLoginStatus(NetEaseQrStatusText.describe(challenge
                            ? NetEaseApiClient.QrLoginStatus.CHALLENGE
                            : NetEaseApiClient.QrLoginStatus.ERROR, finalError));
                    qrRefreshButton.setEnabled(true);
                    return;
                }
                setQrLoginStatus(NetEaseQrStatusText.describe(
                        finalResult.getStatus(), finalResult.getMessage()));
                switch (finalResult.getStatus()) {
                    case WAITING_SCAN:
                    case WAITING_CONFIRMATION:
                        scheduleQrPoll(generation);
                        break;
                    case AUTHORIZED:
                        try {
                            sessionStore.saveCookie(finalResult.getCookie());
                        } catch (RuntimeException exception) {
                            setQrLoginStatus("登录成功，但无法安全保存会话；请检查设备存储后重试。");
                            qrRefreshButton.setEnabled(true);
                            return;
                        }
                        renderNetEaseLoginStatus("网易云账号登录成功；歌词查询优先使用网易云音乐");
                        if (loginDialog != null) loginDialog.dismiss();
                        refreshCurrentTrackLyrics();
                        break;
                    case EXPIRED:
                    case CHALLENGE:
                    case ERROR:
                    default:
                        qrRefreshButton.setEnabled(true);
                        break;
                }
            });
        });
    }

    private void invalidateQrLogin() {
        qrLoginGeneration++;
        if (scheduledQrPoll != null) {
            statusHandler.removeCallbacks(scheduledQrPoll);
            scheduledQrPoll = null;
        }
        qrLoginKey = "";
        qrPollRequestRunning = false;
    }

    private boolean isQrDialogActive(int generation) {
        return generation == qrLoginGeneration && loginDialog != null && loginDialog.isShowing();
    }

    private void setQrLoginStatus(String message) {
        if (qrLoginStatus == null) return;
        qrLoginStatus.setText(message);
        boolean error = message != null && (message.contains("失败") || message.contains("验证")
                || message.contains("8821") || message.contains("无法"));
        qrLoginStatus.setTextColor(error ? Color.rgb(255, 145, 125)
                : Color.rgb(255, 218, 130));
    }

    private Bitmap buildQrBitmap(String contents, int sizePx) throws WriterException {
        BitMatrix matrix = new QRCodeWriter().encode(contents, BarcodeFormat.QR_CODE, sizePx, sizePx);
        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        int dark = Color.rgb(18, 24, 34);
        int white = Color.WHITE;
        for (int y = 0; y < sizePx; y++) {
            for (int x = 0; x < sizePx; x++) bitmap.setPixel(x, y, matrix.get(x, y) ? dark : white);
        }
        return bitmap;
    }

    private EditText credentialInput(String hint, int inputType) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setInputType(inputType);
        input.setTextColor(Color.rgb(26, 36, 47));
        input.setHintTextColor(Color.rgb(96, 108, 120));
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable field = new GradientDrawable();
        field.setColor(Color.rgb(246, 249, 251));
        field.setCornerRadius(dp(8));
        input.setBackground(field);
        return input;
    }

    private void loginNetEase() {
        if (smsLoginMode) {
            loginWithSmsCode();
            return;
        }
        String account = accountInput.getText().toString().trim();
        if (account.isEmpty() || passwordInput.length() == 0) {
            setLoginMessage("请输入手机号或邮箱及密码");
            return;
        }
        char[] password = new char[passwordInput.length()];
        passwordInput.getText().getChars(0, passwordInput.length(), password, 0);
        passwordInput.getText().clear();
        loginButton.setEnabled(false);
        setLoginMessage("正在通过 HTTPS 登录网易云…");
        loginWorker.execute(() -> {
            String message;
            boolean loginSucceeded = false;
            try {
                NetEaseApiClient.LoginResult result = netEaseApi.login(account, password);
                if (result.isSuccess()) {
                    sessionStore.saveCookie(result.getCookie());
                    message = "网易云账号已登录；优先查询网易云，未匹配时使用 LRCLIB 备用来源";
                    loginSucceeded = true;
                } else {
                    message = result.getMessage();
                }
            } catch (Exception exception) {
                Arrays.fill(password, '\0');
                message = "登录失败，请检查网络或稍后重试";
            }
            final String finalMessage = message;
            final boolean finalLoginSucceeded = loginSucceeded;
            runOnUiThread(() -> {
                loginButton.setEnabled(true);
                renderNetEaseLoginStatus(finalMessage);
                if (finalLoginSucceeded) {
                    if (loginDialog != null) loginDialog.dismiss();
                    refreshCurrentTrackLyrics();
                }
            });
        });
    }

    private void setSmsLoginMode(boolean enabled) {
        smsLoginMode = enabled;
        passwordInput.setVisibility(enabled ? View.GONE : View.VISIBLE);
        verificationCodeInput.setVisibility(enabled ? View.VISIBLE : View.GONE);
        smsCodeButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
        accountInput.setHint(enabled ? "网易云手机号（中国大陆）" : "手机号或邮箱");
        accountInput.setInputType(enabled ? InputType.TYPE_CLASS_PHONE
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        loginButton.setText(enabled ? "验证码登录网易云" : "密码登录网易云");
        loginModeButton.setText(enabled ? "改用密码登录" : "改用短信验证码登录");
        if (!enabled) statusHandler.removeCallbacks(smsCodeCountdown);
        else if (smsCodeResendAtMs > SystemClock.elapsedRealtime()) {
            statusHandler.removeCallbacks(smsCodeCountdown);
            statusHandler.post(smsCodeCountdown);
        }
    }

    private void requestSmsCode() {
        String phone = accountInput.getText().toString().trim();
        if (phone.isEmpty()) {
            setLoginMessage("请先输入网易云绑定的中国大陆手机号");
            return;
        }
        if (smsCodeResendAtMs > SystemClock.elapsedRealtime()) {
            statusHandler.removeCallbacks(smsCodeCountdown);
            statusHandler.post(smsCodeCountdown);
            return;
        }
        smsCodeButton.setEnabled(false);
        loginButton.setEnabled(false);
        setLoginMessage("正在向网易云请求短信验证码…");
        loginWorker.execute(() -> {
            NetEaseApiClient.ApiResult result;
            try {
                result = netEaseApi.sendSmsCode(phone);
            } catch (Exception exception) {
                result = null;
            }
            NetEaseApiClient.ApiResult finalResult = result;
            runOnUiThread(() -> {
                loginButton.setEnabled(true);
                String message = finalResult == null ? "验证码请求失败，请检查网络后稍后重试。"
                        : finalResult.getMessage();
                if (finalResult != null && finalResult.isSuccess()) {
                    renderNetEaseLoginStatus(message);
                    smsCodeResendAtMs = SystemClock.elapsedRealtime() + 60_000L;
                    statusHandler.removeCallbacks(smsCodeCountdown);
                    statusHandler.post(smsCodeCountdown);
                } else {
                    smsCodeButton.setEnabled(true);
                    renderNetEaseLoginStatus(message);
                }
            });
        });
    }

    private void loginWithSmsCode() {
        String phone = accountInput.getText().toString().trim();
        String code = verificationCodeInput.getText().toString().trim();
        if (phone.isEmpty() || code.isEmpty()) {
            setLoginMessage("请输入网易云手机号和短信验证码");
            return;
        }
        loginButton.setEnabled(false);
        smsCodeButton.setEnabled(false);
        setLoginMessage("正在使用短信验证码登录网易云…");
        loginWorker.execute(() -> {
            String message;
            boolean loginSucceeded = false;
            try {
                NetEaseApiClient.LoginResult result = netEaseApi.loginWithSmsCode(phone, code);
                if (result.isSuccess()) {
                    sessionStore.saveCookie(result.getCookie());
                    message = "网易云账号已登录；优先查询网易云，未匹配时使用 LRCLIB 备用来源";
                    loginSucceeded = true;
                } else {
                    message = result.getMessage();
                }
            } catch (Exception exception) {
                message = "验证码登录失败，请确认验证码有效后重试";
            }
            String finalMessage = message;
            boolean finalLoginSucceeded = loginSucceeded;
            runOnUiThread(() -> {
                verificationCodeInput.setText("");
                loginButton.setEnabled(true);
                smsCodeButton.setEnabled(smsCodeResendAtMs <= SystemClock.elapsedRealtime());
                renderNetEaseLoginStatus(finalMessage);
                if (finalLoginSucceeded) {
                    if (loginDialog != null) loginDialog.dismiss();
                    refreshCurrentTrackLyrics();
                }
            });
        });
    }

    private void refreshCurrentTrackLyrics() {
        if (!PhoneSenderService.RUNNING) return;
        Intent intent = new Intent(this, PhoneSenderService.class)
                .setAction(PhoneSenderService.ACTION_REFRESH_CURRENT_TRACK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
        else startService(intent);
    }

    private void renderNetEaseLoginStatus(String transientMessage) {
        if (loginStatus == null || sessionStore == null) return;
        boolean signedIn = sessionStore.isLoggedIn();
        String message = transientMessage;
        if (message == null || message.startsWith("正在读取")) {
            message = signedIn
                    ? "网易云账号已连接；优先网易云，LRCLIB 备用"
                    : "网易云账号未登录；仍可尝试 LRCLIB 备用来源";
        } else if ("已退出网易云账号".equals(message)) {
            message = "已退出网易云账号；仍会尝试 LRCLIB 备用来源";
        }
        setLoginMessage(message);
        if (loginEntryButton != null) {
            loginEntryButton.setText(signedIn ? "管理网易云账号" : "登录网易云账号");
        }
        if (logoutButton != null) logoutButton.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        boolean isError = message.contains("失败") || message.contains("未通过")
                || message.contains("安全验证") || message.contains("行为验证")
                || message.contains("风控") || message.contains("检查网络")
                || message.contains("无法安全保存");
        loginStatus.setTextColor(signedIn && !isError
                ? Color.rgb(128, 220, 160) : Color.rgb(255, 210, 120));
        if (dialogLoginStatus != null) {
            dialogLoginStatus.setTextColor(isError ? Color.rgb(255, 145, 125)
                    : (signedIn ? Color.rgb(128, 220, 160) : Color.rgb(255, 218, 130)));
        }
    }

    private void setLoginMessage(String message) {
        if (loginStatus != null) loginStatus.setText(message);
        if (dialogLoginStatus != null) dialogLoginStatus.setText(message);
    }

    private void updatePermissionStatus() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        boolean granted = manager != null && manager.isNotificationListenerAccessGranted(
                new ComponentName(this, LyricsNotificationListenerService.class));
        boolean connected = LyricsNotificationListenerService.isListenerConnected();
        if (!granted) {
            permissionStatus.setText("媒体读取授权：未开启。请在系统设置中允许“歌词桥”读取通知。");
            permissionStatus.setTextColor(Color.rgb(255, 210, 120));
        } else if (!connected) {
            permissionStatus.setText("媒体读取授权：已开启；监听服务尚未连接。请重新开关通知使用权后返回本页。");
            permissionStatus.setTextColor(Color.rgb(255, 210, 120));
        } else {
            permissionStatus.setText("媒体读取授权：已开启，监听服务已连接。请在网易云实际播放歌曲。");
            permissionStatus.setTextColor(Color.rgb(128, 220, 160));
        }
    }

    private void startSending() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        boolean notificationAccess = manager != null && manager.isNotificationListenerAccessGranted(
                new ComponentName(this, LyricsNotificationListenerService.class));
        if (!notificationAccess) {
            permissionStatus.setText("请先开启通知使用权，再启动蓝牙发送。");
            senderAutoStartHint = "请先开启通知使用权；授权后返回本页会自动开始蓝牙发送。";
            refreshServiceStatus();
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            return;
        }
        withRuntimePermissions(this::launchSenderService);
    }

    private void maybeAutoStartSender() {
        if (PhoneSenderService.RUNNING || senderAutoStartPending) return;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        boolean notificationAccess = manager != null && manager.isNotificationListenerAccessGranted(
                new ComponentName(this, LyricsNotificationListenerService.class));
        if (!notificationAccess) {
            senderAutoStartHint = "请先开启通知使用权；授权后返回本页会自动开始蓝牙发送。";
            refreshServiceStatus();
            return;
        }
        senderAutoStartPending = true;
        senderAutoStartHint = "正在准备蓝牙发送…";
        refreshServiceStatus();
        withRuntimePermissions(this::launchSenderService);
    }

    private void launchSenderService() {
        senderAutoStartPending = false;
        senderAutoStartHint = null;
        Intent intent = new Intent(this, PhoneSenderService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
        else startService(intent);
        refreshServiceStatus();
    }

    private void withRuntimePermissions(Runnable action) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            pendingPermissionAction = () -> withRuntimePermissions(action);
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 301);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingPermissionAction = () -> withRuntimePermissions(action);
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 302);
            return;
        }
        action.run();
    }

    private void refreshServiceStatus() {
        if (serviceStatus == null) return;
        boolean running = PhoneSenderService.RUNNING;
        String detail = running || senderAutoStartHint == null
                ? PhoneSenderService.STATUS : senderAutoStartHint;
        serviceStatus.setText(running ? "发送服务运行中：" + detail : detail);
        serviceStatus.setTextColor(running ? Color.rgb(128, 220, 160) : Color.rgb(255, 210, 120));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != 301 && requestCode != 302) return;
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        Runnable pending = pendingPermissionAction;
        pendingPermissionAction = null;
        if (granted && pending != null) pending.run();
        else if (!granted) {
            senderAutoStartPending = false;
            senderAutoStartHint = "蓝牙或通知权限未授予，暂时无法自动启动发送。";
            refreshServiceStatus();
        }
    }

    private void renderMediaState(MediaStateStore.State state) {
        if (playbackStatus == null) return;
        if (state == null || !state.hasSession()) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            boolean permissionGranted = manager != null && manager.isNotificationListenerAccessGranted(
                    new ComponentName(this, LyricsNotificationListenerService.class));
            String diagnostic = MediaSessionMonitor.getDiagnosticStatus();
            String nextStep = !permissionGranted
                    ? "请先开启通知使用权。"
                    : (!LyricsNotificationListenerService.isListenerConnected()
                    ? "监听服务未连接，请重新开关通知使用权。"
                    : (diagnostic.contains("未匹配网易云包名")
                    ? "系统看到了其他媒体会话，请确认正在使用网易云官方 App。"
                    : (diagnostic.contains("拒绝") || diagnostic.contains("未允许")
                    ? "系统拒绝了媒体会话访问，请重新开启通知使用权。"
                    : "请在网易云实际播放一首歌；仅打开网易云不会创建播放会话。")));
            playbackStatus.setText("未检测到网易云播放会话。\n"
                    + diagnostic + "\n" + nextStep);
            return;
        }
        TrackInfo track = state.getTrack();
        PlaybackSnapshot playback = state.getPlayback();
        String title = track == null || track.getTitle().isEmpty() ? "未知歌曲" : track.getTitle();
        String artist = track == null || track.getArtist().isEmpty() ? "未知歌手" : track.getArtist();
        String stateText = playback != null && playback.isPlaying() ? "播放中" : "已暂停";
        playbackStatus.setText(title + "\n" + artist + "\n" + stateText);
    }

    private void add(LinearLayout layout, View view, int topMargin) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = topMargin;
        layout.addView(view, params);
    }

    private TextView text(String value, int sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
