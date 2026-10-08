package com.univ.lyricsbridge;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.univ.lyricsbridge.data.AppSettings;
import com.univ.lyricsbridge.service.CarReceiverService;
import com.univ.lyricsbridge.service.PhoneSenderService;
import com.univ.lyricsbridge.ui.PhoneModeActivity;
import com.univ.lyricsbridge.ui.CarModeActivity;

public final class MainActivity extends Activity {
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String role = AppSettings.getRole(this);
        if ("phone".equals(role) || "car".equals(role)) {
            openConfiguration(role);
            finish();
            return;
        }
        showRoleSelection();
    }

    private void showRoleSelection() {
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(32), dp(24), dp(24));
        content.setBackgroundColor(Color.rgb(18, 24, 34));
        setContentView(content);

        TextView title = text("歌词桥", 28, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        content.addView(title, matchWrap());
        addText("请选择此设备的用途", 18, Color.LTGRAY, dp(24));
        addButton("手机发送端", view -> saveAndOpenConfiguration("phone"));
        addButton("车机显示端", view -> saveAndOpenConfiguration("car"));
        addText("同一 APK 可安装在播放音乐的手机和车机上。", 14,
                Color.rgb(180, 190, 205), dp(18));
    }

    private void saveAndOpenConfiguration(String role) {
        AppSettings.setRole(this, role);
        openConfiguration(role);
        finish();
    }

    private void openConfiguration(String role) {
        if ("phone".equals(role)) {
            stopService(new Intent(this, CarReceiverService.class));
        } else if ("car".equals(role)) {
            stopService(new Intent(this, PhoneSenderService.class));
        }
        Class<?> target = "phone".equals(role) ? PhoneModeActivity.class : CarModeActivity.class;
        startActivity(new Intent(this, target));
    }

    private void addText(String value, int sizeSp, int color, int topMargin) {
        TextView view = text(value, sizeSp, color);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = topMargin;
        content.addView(view, params);
    }

    private void addButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(16);
        content.addView(button, params);
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
