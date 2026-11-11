package com.chiwawa.autorp;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String DEFAULT_SERVER = "http://64.235.41.53:5010";
    private static final int REQ_OVERLAY = 202;
    private static final int REQ_PROJECTION = 303;

    private SharedPreferences prefs;
    private EditText keyInput;
    private EditText customGayaInput;
    private TextView statusBadge;
    private TextView ssProofBanner;
    private ImageView ssPreviewImg;
    private TextView lastRpBox;
    private Button saveGayaBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("chiwawa_v3_prefs", MODE_PRIVATE);

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 101);
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 101);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#090d16"));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(34, 40, 34, 50);
        scroll.addView(root);

        // HEADER SIMPLE
        LinearLayout header = createCard();
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView brand = new TextView(this);
        brand.setText("⚡ CHIWAWA AUTORP");
        brand.setTextColor(Color.parseColor("#00f5d4"));
        brand.setTextSize(17);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        statusBadge = new TextView(this);
        statusBadge.setText("🟢 Siap Pakai");
        statusBadge.setTextColor(Color.parseColor("#00f5d4"));
        statusBadge.setTextSize(11.5f);
        statusBadge.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(statusBadge);
        root.addView(header);

        addSpacer(root, 22);

        // CARD 1: HANYA API KEY & CUSTOM GAYA (TANPA DROPDOWN RIBET)
        LinearLayout cfgCard = createCard();
        addLabel(cfgCard, "🔑 KODE API KEY VIP");
        keyInput = createInput("Contoh: DEFAULT_MELA", false);
        keyInput.setText(prefs.getString("api_key", "DEFAULT_MELA"));
        cfgCard.addView(keyInput);

        addSpacer(cfgCard, 22);
        addLabel(cfgCard, "✨ CUSTOM GAYA BAHASA SENDIRI");
        customGayaInput = createInput("Contoh: Kamu adalah seorang supir taxi yang ramah...", true);
        customGayaInput.setText(prefs.getString("custom_gaya", "Kamu adalah seorang supir taxi yang ramah"));
        cfgCard.addView(customGayaInput);

        addSpacer(cfgCard, 18);
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        saveGayaBtn = createButton("💾 SIMPAN CUSTOM GAYA", "#00f5d4", "#041f18");
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f);
        lp1.setMargins(0, 0, 12, 0);
        saveGayaBtn.setOnClickListener(v -> syncCustomGayaToServer(false));
        btnRow.addView(saveGayaBtn, lp1);

        Button resetMeBtn = createButton("🔄 RESET /ME", "#1e293b", "#e2e8f0");
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f);
        resetMeBtn.setOnClickListener(v -> resetTurnToMe());
        btnRow.addView(resetMeBtn, lp2);

        cfgCard.addView(btnRow);
        root.addView(cfgCard);

        addSpacer(root, 22);

        // CARD 2: TOMBOL NGAMBANG IN-GAME
        LinearLayout floatCard = createCard();
        addLabel(floatCard, "📸 TOMBOL SS NGAMBANG (1-KLIK DI SA-MP)");

        Button startBtn = createButton("🚀 MUNCULKAN TOMBOL SS NGAMBANG", "#10b981", "#041f18");
        startBtn.setOnClickListener(v -> {
            syncCustomGayaToServer(true);
            startFloatingCaptureService();
        });
        floatCard.addView(startBtn);

        addSpacer(floatCard, 14);
        Button stopBtn = createButton("❌ MATIKAN TOMBOL NGAMBANG", "#ef4444", "#ffffff");
        stopBtn.setOnClickListener(v -> {
            stopService(new Intent(this, FloatingOverlayService.class));
            Toast.makeText(this, "Tombol ngambang dimatikan.", Toast.LENGTH_SHORT).show();
        });
        floatCard.addView(stopBtn);
        root.addView(floatCard);

        addSpacer(root, 22);

        // CARD 3: DEBUGGER BUKTI SCREENSHOT
        LinearLayout debugCard = createCard();
        addLabel(debugCard, "🛠️ VISUAL DEBUGGER (CEK BUKTI SS)");

        ssProofBanner = new TextView(this);
        ssProofBanner.setText("⏳ Belum ada SS baru. Tap tombol 📸 saat di game!");
        ssProofBanner.setTextColor(Color.parseColor("#38bdf8"));
        ssProofBanner.setTextSize(12f);
        ssProofBanner.setTypeface(Typeface.DEFAULT_BOLD);
        ssProofBanner.setPadding(24, 20, 24, 20);
        GradientDrawable bannerBg = new GradientDrawable();
        bannerBg.setColor(Color.parseColor("#0b1120"));
        bannerBg.setCornerRadius(20f);
        bannerBg.setStroke(2, Color.parseColor("#1e293b"));
        ssProofBanner.setBackground(bannerBg);
        debugCard.addView(ssProofBanner);

        addSpacer(debugCard, 14);
        ssPreviewImg = new ImageView(this);
        ssPreviewImg.setBackgroundColor(Color.BLACK);
        ssPreviewImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 380);
        debugCard.addView(ssPreviewImg, imgLp);

        addSpacer(debugCard, 14);
        lastRpBox = new TextView(this);
        lastRpBox.setText("/me (Hasil RP terakhir akan tampil di sini)");
        lastRpBox.setTextColor(Color.parseColor("#fde047"));
        lastRpBox.setTextSize(12.5f);
        lastRpBox.setTypeface(Typeface.MONOSPACE);
        lastRpBox.setPadding(24, 22, 24, 22);
        GradientDrawable rpBg = new GradientDrawable();
        rpBg.setColor(Color.parseColor("#060911"));
        rpBg.setCornerRadius(20f);
        rpBg.setStroke(2, Color.parseColor("#1e293b"));
        lastRpBox.setBackground(rpBg);
        lastRpBox.setOnClickListener(v -> {
            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("AutoRP", lastRpBox.getText().toString()));
            Toast.makeText(this, "✅ Kalimat RP disalin!", Toast.LENGTH_SHORT).show();
        });
        debugCard.addView(lastRpBox);

        root.addView(debugCard);
        setContentView(scroll);

        loadServerProfile();
        refreshLocalDebugPreview();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLocalDebugPreview();
        loadServerProfile();
    }

    private void refreshLocalDebugPreview() {
        String statusNote = prefs.getString("last_ss_status", "⏳ Belum ada SS baru.");
        boolean ssOk = prefs.getBoolean("last_ss_ok", false);
        String lastRp = prefs.getString("last_rp_output", "/me (Belum ada riwayat RP)");

        ssProofBanner.setText(statusNote);
        ssProofBanner.setTextColor(Color.parseColor(ssOk ? "#34d399" : "#f87171"));
        lastRpBox.setText(lastRp);

        File localDebugFile = new File(getFilesDir(), "last_capture_debug.jpg");
        if (localDebugFile.exists()) {
            Bitmap bm = BitmapFactory.decodeFile(localDebugFile.getAbsolutePath());
            if (bm != null) {
                ssPreviewImg.setImageBitmap(bm);
            }
        }
    }

    private void loadServerProfile() {
        final String key = keyInput.getText().toString().trim().toUpperCase();
        if (key.isEmpty()) return;

        new Thread(() -> {
            try {
                URL u = new URL(DEFAULT_SERVER + "/api/apk-profile?key=" + Uri.encode(key));
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(8000);
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject res = new JSONObject(sb.toString());
                if (res.optBoolean("valid", false)) {
                    final String cName = res.optString("charName", "VIP");
                    final String nextCmd = res.optString("nextCmd", "/me");
                    final String serverRule = res.optString("customStyleRule", "");
                    new Handler(Looper.getMainLooper()).post(() -> {
                        statusBadge.setText("🟢 " + cName + " • " + nextCmd);
                        if (!serverRule.isEmpty()) {
                            customGayaInput.setText(serverRule);
                            prefs.edit().putString("custom_gaya", serverRule).apply();
                        }
                    });
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void syncCustomGayaToServer(boolean silent) {
        final String key = keyInput.getText().toString().trim().toUpperCase();
        final String rule = customGayaInput.getText().toString().trim();

        prefs.edit()
            .putString("server_url", DEFAULT_SERVER)
            .putString("api_key", key)
            .putString("custom_gaya", rule)
            .apply();

        if (!silent) {
            saveGayaBtn.setText("⏳ MENYIMPAN...");
            saveGayaBtn.setEnabled(false);
        }

        new Thread(() -> {
            try {
                URL u = new URL(DEFAULT_SERVER + "/api/apk-save-style");
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setConnectTimeout(10000);
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("key", key);
                body.put("customRule", rule);
                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject res = new JSONObject(sb.toString());
                final boolean ok = res.optBoolean("ok", false);
                final String charName = res.optString("charName", "VIP");

                new Handler(Looper.getMainLooper()).post(() -> {
                    saveGayaBtn.setText("💾 SIMPAN CUSTOM GAYA");
                    saveGayaBtn.setEnabled(true);
                    if (ok) {
                        statusBadge.setText("🟢 " + charName + " • /me");
                        if (!silent) Toast.makeText(this, "✨ Custom Gaya berhasil disimpan & aktif!", Toast.LENGTH_SHORT).show();
                    } else if (!silent) {
                        Toast.makeText(this, "❌ Key tidak ditemukan di server!", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    saveGayaBtn.setText("💾 SIMPAN CUSTOM GAYA");
                    saveGayaBtn.setEnabled(true);
                });
            }
        }).start();
    }

    private void resetTurnToMe() {
        final String key = keyInput.getText().toString().trim().toUpperCase();
        new Thread(() -> {
            try {
                URL u = new URL(DEFAULT_SERVER + "/reset?key=" + Uri.encode(key));
                u.openStream().close();
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(this, "🔄 Giliran di-reset kembali ke /me!", Toast.LENGTH_SHORT).show();
                    loadServerProfile();
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private void startFloatingCaptureService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izinkan 'Tampilkan di atas aplikasi lain' dulu!", Toast.LENGTH_LONG).show();
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())), REQ_OVERLAY);
            return;
        }

        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mpm != null) {
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJECTION);
        } else {
            launchService(0, null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                startFloatingCaptureService();
            }
        } else if (requestCode == REQ_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                launchService(resultCode, data);
            } else {
                launchService(0, null);
            }
        }
    }

    private void launchService(int resultCode, Intent resultData) {
        Intent svc = new Intent(this, FloatingOverlayService.class);
        svc.putExtra("resultCode", resultCode);
        if (resultData != null) svc.putExtra("resultData", resultData);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
        Toast.makeText(this, "📸 Tombol Ngambang Aktif! Tap 1x saat di SA-MP untuk langsung SS & Copy!", Toast.LENGTH_LONG).show();
        moveTaskToBack(true);
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(34, 34, 34, 34);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#111827"));
        bg.setCornerRadius(32f);
        bg.setStroke(2, Color.parseColor("#1f2937"));
        card.setBackground(bg);
        return card;
    }

    private void addLabel(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#9ca3af"));
        tv.setTextSize(11.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, 0, 0, 14);
        parent.addView(tv);
    }

    private EditText createInput(String hint, boolean multiline) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(Color.parseColor("#6b7280"));
        et.setTextColor(Color.WHITE);
        et.setTextSize(13.5f);
        et.setPadding(28, 24, 28, 24);
        if (multiline) {
            et.setMinLines(3);
            et.setGravity(Gravity.TOP | Gravity.START);
        } else {
            et.setSingleLine(true);
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0b1120"));
        bg.setCornerRadius(22f);
        bg.setStroke(2, Color.parseColor("#374151"));
        et.setBackground(bg);
        return et;
    }

    private Button createButton(String text, String bgHex, String fgHex) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.parseColor(fgHex));
        b.setTextSize(13f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(24, 26, 24, 26);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(bgHex));
        bg.setCornerRadius(24f);
        b.setBackground(bg);
        return b;
    }

    private void addSpacer(LinearLayout parent, int px) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px));
    }
}
