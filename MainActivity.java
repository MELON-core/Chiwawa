package com.chiwawa.autorp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private EditText urlInput;
    private static final String DEFAULT_URL = "http://64.235.41.53:5010";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 101);
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 101);
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#090d16"));
        layout.setPadding(60, 60, 60, 60);

        TextView title = new TextView(this);
        title.setText("⚡ CHIWAWA AUTORP OVERLAY");
        title.setTextColor(Color.parseColor("#00f5d4"));
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        TextView sub = new TextView(this);
        sub.setText("\nTombol Ngambang Pengganti MacroDroid untuk GTA SA-MP.\n");
        sub.setTextColor(Color.parseColor("#9ca3af"));
        sub.setGravity(Gravity.CENTER);
        layout.addView(sub);

        urlInput = new EditText(this);
        urlInput.setText(getSharedPreferences("cfg", MODE_PRIVATE).getString("url", DEFAULT_URL));
        urlInput.setTextColor(Color.WHITE);
        urlInput.setBackgroundColor(Color.parseColor("#111827"));
        urlInput.setPadding(30, 30, 30, 30);
        layout.addView(urlInput);

        Button startBtn = new Button(this);
        startBtn.setText("🚀 MUNCULKAN TOMBOL NGAMBANG");
        startBtn.setBackgroundColor(Color.parseColor("#10b981"));
        startBtn.setTextColor(Color.parseColor("#041f18"));
        startBtn.setOnClickListener(v -> startFloatingBubble());
        layout.addView(startBtn);

        Button stopBtn = new Button(this);
        stopBtn.setText("❌ MATIKAN TOMBOL NGAMBANG");
        stopBtn.setBackgroundColor(Color.parseColor("#ef4444"));
        stopBtn.setTextColor(Color.WHITE);
        stopBtn.setOnClickListener(v -> {
            stopService(new Intent(this, FloatingOverlayService.class));
            Toast.makeText(this, "Tombol ngambang dimatikan", Toast.LENGTH_SHORT).show();
        });
        layout.addView(stopBtn);

        setContentView(layout);
    }

    private void startFloatingBubble() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Aktifkan izin 'Tampilkan di atas aplikasi lain' dulu!", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 202);
            return;
        }

        String targetUrl = urlInput.getText().toString().trim();
        getSharedPreferences("cfg", MODE_PRIVATE).edit().putString("url", targetUrl).apply();

        Intent svc = new Intent(this, FloatingOverlayService.class);
        svc.putExtra("url", targetUrl);
        startService(svc);

        Toast.makeText(this, "⚡ Tombol Ngambang Aktif! Silakan buka GTA SA-MP", Toast.LENGTH_LONG).show();
        moveTaskToBack(true);
    }
}