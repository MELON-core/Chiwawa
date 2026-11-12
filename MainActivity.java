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
    private EditText customRuleInput;
    private EditText manualAdInput;
    private TextView statusBadge;
    private TextView ssProofBanner;
    private ImageView ssPreviewImg;
    private TextView lastAdBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("chiwawa_autorp_prefs", MODE_PRIVATE);

        if (prefs.getString("api_key", "").trim().isEmpty()) {
            prefs.edit().putString("api_key", "DEFAULT_MELA").putString("server_url", DEFAULT_SERVER).apply();
        }

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 101);
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 101);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#060a12"));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(30, 34, 30, 42);
        scroll.addView(root);

        LinearLayout header = createCard();
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView brand = new TextView(this);
        brand.setText("⚡ CHIWAWA SANA V6");
        brand.setTextColor(Color.parseColor("#00f5d4"));
        brand.setTextSize(16f);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        statusBadge = new TextView(this);
        statusBadge.setText("🟢 NEURAL READY");
        statusBadge.setTextColor(Color.parseColor("#34d399"));
        statusBadge.setTextSize(11f);
        statusBadge.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(statusBadge);
        root.addView(header);

        addSpacer(root, 18);

        LinearLayout mainCard = createCard();
        addLabel(mainCard, "🔑 KODE PROFIL & CATATAN ATURAN SANA");
        keyInput = createInput("Kode Key (DEFAULT_MELA)", false);
        keyInput.setText(prefs.getString("api_key", "DEFAULT_MELA"));
        mainCard.addView(keyInput);

        addSpacer(mainCard, 12);
        customRuleInput = createInput("Aturan ekstra (Misal: Berdekorasi 10 tetap ditulis angkanya)...", false);
        customRuleInput.setText(prefs.getString("custom_gaya", "Berdekorasi 10 tetap ditulis angkanya"));
        mainCard.addView(customRuleInput);

        addSpacer(mainCard, 18);

        LinearLayout btnRow1 = new LinearLayout(this);
        btnRow1.setOrientation(LinearLayout.HORIZONTAL);
        Button startOverlayBtn = createButton("🚀 AKTIFKAN TOMBOL 📸", "#10b981", "#041f18");
        startOverlayBtn.setOnClickListener(v -> {
            saveAndSyncToServer();
            requestAndStartFloatingBubble();
        });
        Button stopOverlayBtn = createButton("⏹️ MATIKAN", "#1e293b", "#e2e8f0");
        stopOverlayBtn.setOnClickListener(v -> {
            stopService(new Intent(this, FloatingOverlayService.class));
            Toast.makeText(this, "⏹️ Tombol Ngambang Dimatikan", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams lpLeft = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.25f);
        lpLeft.setMargins(0, 0, 10, 0);
        LinearLayout.LayoutParams lpRight = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.75f);
        btnRow1.addView(startOverlayBtn, lpLeft);
        btnRow1.addView(stopOverlayBtn, lpRight);
        mainCard.addView(btnRow1);
        root.addView(mainCard);

        addSpacer(root, 18);

        LinearLayout manualCard = createCard();
        addLabel(manualCard, "✨ PERAPI TEKS MANUAL & HASIL AUTO-COPY");
        manualAdInput = createInput("Ketik iklan mentah jika ingin merapikan tanpa SS...", false);
        manualCard.addView(manualAdInput);

        addSpacer(manualCard, 12);
        LinearLayout btnRow2 = new LinearLayout(this);
        btnRow2.setOrientation(LinearLayout.HORIZONTAL);
        Button btnSell = createButton("🏷️ JUAL (MINAT)", "#00f5d4", "#041f18");
        btnSell.setOnClickListener(v -> formatManualText("SELL"));
        Button btnFind = createButton("🔍 CARI (ADA)", "#38bdf8", "#04131f");
        btnFind.setOnClickListener(v -> formatManualText("FIND"));
        LinearLayout.LayoutParams lpHalf1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpHalf1.setMargins(0, 0, 10, 0);
        LinearLayout.LayoutParams lpHalf2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnRow2.addView(btnSell, lpHalf1);
        btnRow2.addView(btnFind, lpHalf2);
        manualCard.addView(btnRow2);

        addSpacer(manualCard, 14);
        lastAdBox = new TextView(this);
        lastAdBox.setText("Satu Unit Kendaraan Import Sport. Ada? Hubungi Saya!");
        lastAdBox.setTextColor(Color.parseColor("#fde047"));
        lastAdBox.setTextSize(12.5f);
        lastAdBox.setTypeface(Typeface.MONOSPACE);
        lastAdBox.setPadding(24, 20, 24, 20);
        GradientDrawable adBg = new GradientDrawable();
        adBg.setColor(Color.parseColor("#050811"));
        adBg.setCornerRadius(18f);
        adBg.setStroke(2, Color.parseColor("#1e293b"));
        lastAdBox.setBackground(adBg);
        lastAdBox.setOnClickListener(v -> {
            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("SANA_AD", lastAdBox.getText().toString()));
            Toast.makeText(this, "📋 Teks Iklan Disalin ke Clipboard!", Toast.LENGTH_SHORT).show();
        });
        manualCard.addView(lastAdBox);
        root.addView(manualCard);

        addSpacer(root, 18);

        LinearLayout debugCard = createCard();
        addLabel(debugCard, "📸 BUKTI SCREENSHOT KOTAK IKLAN SANA");
        ssProofBanner = new TextView(this);
        ssProofBanner.setText("⏳ Tap 1x tombol 📸 saat kotak Ads terbuka di game");
        ssProofBanner.setTextColor(Color.parseColor("#38bdf8"));
        ssProofBanner.setTextSize(11.5f);
        ssProofBanner.setTypeface(Typeface.DEFAULT_BOLD);
        ssProofBanner.setPadding(20, 15, 20, 15);
        GradientDrawable bannerBg = new GradientDrawable();
        bannerBg.setColor(Color.parseColor("#080d18"));
        bannerBg.setCornerRadius(16f);
        bannerBg.setStroke(2, Color.parseColor("#1e293b"));
        ssProofBanner.setBackground(bannerBg);
        debugCard.addView(ssProofBanner);

        addSpacer(debugCard, 12);
        ssPreviewImg = new ImageView(this);
        ssPreviewImg.setBackgroundColor(Color.BLACK);
        ssPreviewImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        debugCard.addView(ssPreviewImg, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 310));
        root.addView(debugCard);

        setContentView(scroll);
        refreshLocalDebugPreview();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLocalDebugPreview();
    }

    private void formatManualText(final String adType) {
        final String raw = manualAdInput.getText().toString().trim();
        final String rule = customRuleInput.getText().toString().trim();
        if (raw.isEmpty()) {
            Toast.makeText(this, "Ketik teks iklan dulu!", Toast.LENGTH_SHORT).show();
            return;
        }
        lastAdBox.setText("⏳ Merapikan dengan CHIWAWA Neural V6...");
        new Thread(() -> {
            try {
                URL u = new URL(DEFAULT_SERVER + "/api/format-text");
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setConnectTimeout(12000);
                conn.setDoOutput(true);
                JSONObject body = new JSONObject();
                body.put("text", raw);
                body.put("adType", adType);
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
                final String out = res.optString("result", raw);
                new Handler(Looper.getMainLooper()).post(() -> {
                    lastAdBox.setText(out);
                    ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cb != null) cb.setPrimaryClip(ClipData.newPlainText("SANA_AD", out));
                    Toast.makeText(MainActivity.this, "✅ Dirapikan & Otomatis Disalin!", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(MainActivity.this, "⚠️ Gagal menghubungi server", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void saveAndSyncToServer() {
        final String key = keyInput.getText().toString().trim().isEmpty() ? "DEFAULT_MELA" : keyInput.getText().toString().trim().toUpperCase();
        final String rule = customRuleInput.getText().toString().trim();
        prefs.edit().putString("server_url", DEFAULT_SERVER).putString("api_key", key).putString("custom_gaya", rule).apply();
        new Thread(() -> {
            try {
                URL u = new URL(DEFAULT_SERVER + "/api/apk-save-style");
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setConnectTimeout(8000);
                conn.setDoOutput(true);
                JSONObject body = new JSONObject();
                body.put("key", key);
                body.put("customRule", rule);
                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();
                conn.getInputStream().close();
            } catch (Exception ignored) {}
        }).start();
    }

    private void refreshLocalDebugPreview() {
        String statusNote = prefs.getString("last_ss_status", "⏳ Tap 1x tombol 📸 saat kotak Ads terbuka di game");
        boolean ssOk = prefs.getBoolean("last_ss_ok", true);
        String lastAd = prefs.getString("last_rp_output", "Satu Unit Kendaraan Import Sport. Ada? Hubungi Saya!");
        ssProofBanner.setText(statusNote);
        ssProofBanner.setTextColor(Color.parseColor(ssOk ? "#34d399" : "#f87171"));
        lastAdBox.setText(lastAd);
        File localDebugFile = new File(getFilesDir(), "last_capture_debug.jpg");
        if (localDebugFile.exists()) {
            Bitmap bm = BitmapFactory.decodeFile(localDebugFile.getAbsolutePath());
            if (bm != null) ssPreviewImg.setImageBitmap(bm);
        }
    }

    private void requestAndStartFloatingBubble() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izinkan Tampilkan di atas aplikasi lain dulu!", Toast.LENGTH_LONG).show();
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), REQ_OVERLAY);
            return;
        }
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mpm != null) {
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_PROJECTION);
        } else {
            launchFloatingService(0, null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) requestAndStartFloatingBubble();
        } else if (requestCode == REQ_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) launchFloatingService(resultCode, data);
            else launchFloatingService(0, null);
        }
    }

    private void launchFloatingService(int resultCode, Intent resultData) {
        String key = keyInput.getText().toString().trim().isEmpty() ? "DEFAULT_MELA" : keyInput.getText().toString().trim().toUpperCase();
        stopService(new Intent(this, FloatingOverlayService.class));
        Intent svc = new Intent(this, FloatingOverlayService.class);
        svc.putExtra("resultCode", resultCode);
        svc.putExtra("api_key", key);
        svc.putExtra("server_url", DEFAULT_SERVER);
        if (resultData != null) svc.putExtra("resultData", resultData);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc);
        else startService(svc);
        Toast.makeText(this, "📸 Perapi SANA V6 Aktif! Tap 1x tombol 📸 saat dialog iklan terbuka!", Toast.LENGTH_LONG).show();
        moveTaskToBack(true);
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(28, 28, 28, 28);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0e1525"));
        bg.setCornerRadius(26f);
        bg.setStroke(2, Color.parseColor("#1e293b"));
        card.setBackground(bg);
        return card;
    }

    private void addLabel(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#94a3b8"));
        tv.setTextSize(11f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, 0, 0, 10);
        parent.addView(tv);
    }

    private EditText createInput(String hint, boolean multiline) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(Color.parseColor("#64748b"));
        et.setTextColor(Color.WHITE);
        et.setTextSize(13f);
        et.setPadding(22, 18, 22, 18);
        et.setSingleLine(!multiline);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#080d18"));
        bg.setCornerRadius(16f);
        bg.setStroke(2, Color.parseColor("#334155"));
        et.setBackground(bg);
        return et;
    }

    private Button createButton(String text, String bgHex, String fgHex) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.parseColor(fgHex));
        b.setTextSize(12f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(16, 22, 16, 22);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(bgHex));
        bg.setCornerRadius(18f);
        b.setBackground(bg);
        return b;
    }

    private void addSpacer(LinearLayout parent, int px) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px));
    }
}