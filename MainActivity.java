package com.chiwawa.autorp;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String DEFAULT_SERVER = "http://64.235.41.53:5010";
    private static final int REQ_OVERLAY = 202;
    private static final int REQ_MEDIA_PROJECTION = 303;

    private SharedPreferences prefs;
    private EditText apiKeyInput;
    private EditText manualSitInput;
    private Spinner styleSpinner;
    private Spinner cropSpinner;
    private Spinner copyModeSpinner;
    private Spinner sizeSpinner;
    private TextView statusBadge;
    private TextView lastMeBox;
    private TextView lastDoBox;
    private Button verifyBtn;
    private Button testManualBtn;

    private final String[] styleLabels = {
        "🎭 Warga Normal (Natural IC)",
        "🔧 Mekanik / Medis (Teknis Rapi)",
        "🔫 Street / Gangster (Tegas & Keras)",
        "👮 Polisi / Formal (Baku SOP)"
    };
    private final String[] styleValues = {"WARGA", "MEDIS_MEKA", "STREET", "FORMAL"};

    private final String[] cropLabels = {
        "🎯 Fokus Area Chatlog SA-MP (Cepat & Akurat)",
        "🖥️ Scan Full Layar Utuh"
    };
    private final String[] cropValues = {"CHATLOG_AREA", "FULL_SCREEN"};

    private final String[] copyLabels = {
        "⚡ Auto-Copy /me + Muncul Tombol Mini [/do]",
        "📋 Auto-Copy Gabungan (/me & /do sekaligus)",
        "🔍 Auto-Copy /do Saja"
    };
    private final String[] copyValues = {"ME_FIRST", "BOTH", "DO_ONLY"};

    private final String[] sizeLabels = {
        "🔘 Ukuran Tombol Ngambang: Sedang (52dp)",
        "🔹 Ukuran Tombol Ngambang: Kecil (44dp)",
        "🔵 Ukuran Tombol Ngambang: Besar (62dp)"
    };
    private final int[] sizeValues = {52, 44, 62};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("chiwawa_autorp_prefs", MODE_PRIVATE);

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
        root.setPadding(36, 42, 36, 60);
        scroll.addView(root);

        // 1. HEADER BRAND
        LinearLayout header = createCard();
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView brand = new TextView(this);
        brand.setText("⚡ CHIWAWA AUTORP");
        brand.setTextColor(Color.parseColor("#00f5d4"));
        brand.setTextSize(17);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(brand, bp);

        statusBadge = new TextView(this);
        statusBadge.setText("🔒 Belum Terhubung");
        statusBadge.setTextColor(Color.parseColor("#00f5d4"));
        statusBadge.setTextSize(11);
        statusBadge.setPadding(22, 10, 22, 10);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(Color.parseColor("#162238"));
        badgeBg.setCornerRadius(40f);
        statusBadge.setBackground(badgeBg);
        header.addView(statusBadge);
        root.addView(header);

        addSpacer(root, 24);

        // 2. CARD PENGATURAN UTAMA DI DALAM APK
        LinearLayout settingsCard = createCard();

        addSectionTitle(settingsCard, "🔑 1. LISENSI & API KEY AUTORP");
        apiKeyInput = createInput("Masukkan API Key dari Discord (atau CHIWAWA-OWNER)...");
        apiKeyInput.setText(prefs.getString("api_key", ""));
        settingsCard.addView(apiKeyInput);

        addSpacer(settingsCard, 16);
        verifyBtn = createButton("✔️ SIMPAN & VERIFIKASI PENGATURAN", "#1e293b", "#00f5d4");
        verifyBtn.setOnClickListener(v -> saveAndVerifySettings(false));
        settingsCard.addView(verifyBtn);

        addSpacer(settingsCard, 28);
        addSectionTitle(settingsCard, "🎭 2. GAYA BAHASA ROLEPLAY AI");
        styleSpinner = createSpinner(styleLabels);
        styleSpinner.setSelection(prefs.getInt("style_idx", 0));
        settingsCard.addView(styleSpinner);

        addSpacer(settingsCard, 24);
        addSectionTitle(settingsCard, "📐 3. MODE PEMINDAIAN SCREENSHOT (OCR)");
        cropSpinner = createSpinner(cropLabels);
        cropSpinner.setSelection(prefs.getInt("crop_idx", 0));
        settingsCard.addView(cropSpinner);

        addSpacer(settingsCard, 24);
        addSectionTitle(settingsCard, "📋 4. MODE AUTO-COPY CLIPBOARD SAAT TAP TOMBOL SS");
        copyModeSpinner = createSpinner(copyLabels);
        copyModeSpinner.setSelection(prefs.getInt("copy_idx", 0));
        settingsCard.addView(copyModeSpinner);

        addSpacer(settingsCard, 24);
        addSectionTitle(settingsCard, "🔘 5. UKURAN TOMBOL NGAMBANG");
        sizeSpinner = createSpinner(sizeLabels);
        sizeSpinner.setSelection(prefs.getInt("size_idx", 0));
        settingsCard.addView(sizeSpinner);

        root.addView(settingsCard);
        addSpacer(root, 28);

        // 3. CARD KONTROL TOMBOL NGAMBANG (1-TAP LIVE SCREENSHOT)
        LinearLayout floatCard = createCard();
        addSectionTitle(floatCard, "📸 KONTROL TOMBOL SS NGAMBANG (IN-GAME)");

        TextView floatInfo = new TextView(this);
        floatInfo.setText("Saat tombol ngambang (📸) diaktifkan, cukup TAP 1X pada tombol bulat di atas layar GTA SA-MP untuk langsung memotret layar dan otomatis menyalin balasan /me ke Clipboard tanpa membuka panel!");
        floatInfo.setTextColor(Color.parseColor("#9ca3af"));
        floatInfo.setTextSize(12);
        floatCard.addView(floatInfo);

        addSpacer(floatCard, 20);
        Button startFloatBtn = createButton("🚀 AKTIFKAN TOMBOL SS NGAMBANG", "#10b981", "#041f18");
        startFloatBtn.setOnClickListener(v -> {
            savePrefsLocally();
            requestAndStartFloatingCapture();
        });
        floatCard.addView(startFloatBtn);

        addSpacer(floatCard, 16);
        Button stopFloatBtn = createButton("❌ MATIKAN TOMBOL NGAMBANG", "#ef4444", "#ffffff");
        stopFloatBtn.setOnClickListener(v -> {
            stopService(new Intent(this, FloatingOverlayService.class));
            Toast.makeText(this, "Tombol ngambang dimatikan.", Toast.LENGTH_SHORT).show();
        });
        floatCard.addView(stopFloatBtn);

        root.addView(floatCard);
        addSpacer(root, 28);

        // 4. CARD RIWAYAT & TES MANUAL DI DALAM APK
        LinearLayout testCard = createCard();
        addSectionTitle(testCard, "⚡ RIWAYAT HASIL SCAN TERAKHIR & TES MANUAL");

        lastMeBox = createResultBox(
            prefs.getString("last_me", "/me (Belum ada riwayat scan, tap untuk copy)"),
            "#34d399"
        );
        lastMeBox.setOnClickListener(v -> copyToClipboard(lastMeBox.getText().toString(), "✅ /me berhasil disalin!"));
        testCard.addView(lastMeBox);

        addSpacer(testCard, 12);
        lastDoBox = createResultBox(
            prefs.getString("last_do", "/do (Belum ada riwayat scan, tap untuk copy)"),
            "#38bdf8"
        );
        lastDoBox.setOnClickListener(v -> copyToClipboard(lastDoBox.getText().toString(), "✅ /do berhasil disalin!"));
        testCard.addView(lastDoBox);

        addSpacer(testCard, 20);
        manualSitInput = createInput("Atau ketik situasi manual di sini untuk tes...");
        testCard.addView(manualSitInput);

        addSpacer(testCard, 14);
        testManualBtn = createButton("⚡ GENERATE RP MANUAL & COPY /ME", "#00f5d4", "#041f18");
        testManualBtn.setOnClickListener(v -> generateManualRpInApp());
        testCard.addView(testManualBtn);

        root.addView(testCard);

        setContentView(scroll);

        if (!prefs.getString("api_key", "").isEmpty()) {
            saveAndVerifySettings(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs != null && lastMeBox != null && lastDoBox != null) {
            lastMeBox.setText(prefs.getString("last_me", "/me (Tap tombol 📸 saat di game untuk scan otomatis)"));
            lastDoBox.setText(prefs.getString("last_do", "/do (Tap kotak ini untuk menyalin /do terakhir)"));
        }
    }

    private void savePrefsLocally() {
        int sIdx = styleSpinner.getSelectedItemPosition();
        int cIdx = cropSpinner.getSelectedItemPosition();
        int mIdx = copyModeSpinner.getSelectedItemPosition();
        int zIdx = sizeSpinner.getSelectedItemPosition();

        prefs.edit()
            .putString("server_url", DEFAULT_SERVER)
            .putString("api_key", apiKeyInput.getText().toString().trim())
            .putInt("style_idx", sIdx)
            .putString("rp_style", styleValues[sIdx])
            .putInt("crop_idx", cIdx)
            .putString("crop_mode", cropValues[cIdx])
            .putInt("copy_idx", mIdx)
            .putString("copy_mode", copyValues[mIdx])
            .putInt("size_idx", zIdx)
            .putInt("bubble_dp", sizeValues[zIdx])
            .apply();
    }

    private void saveAndVerifySettings(boolean silent) {
        savePrefsLocally();
        final String key = prefs.getString("api_key", "");
        if (key.isEmpty()) {
            if (!silent) Toast.makeText(this, "⚠️ Masukkan API Key terlebih dahulu!", Toast.LENGTH_SHORT).show();
            return;
        }

        verifyBtn.setText("⏳ Memverifikasi Key...");
        verifyBtn.setEnabled(false);

        new Thread(() -> {
            try {
                URL u = new URL(DEFAULT_SERVER + "/api/verify");
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("key", key);
                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject res = new JSONObject(sb.toString());
                boolean valid = res.optBoolean("valid", false);
                String owner = res.optString("ownerName", "VIP Member");
                String reason = res.optString("reason", "Key tidak valid");

                new Handler(Looper.getMainLooper()).post(() -> {
                    verifyBtn.setText("✔️ SIMPAN & VERIFIKASI PENGATURAN");
                    verifyBtn.setEnabled(true);
                    if (valid) {
                        statusBadge.setText("🟢 " + owner);
                        if (!silent) Toast.makeText(this, "✅ Pengaturan Disimpan & Key Aktif!", Toast.LENGTH_SHORT).show();
                    } else {
                        statusBadge.setText("❌ Key Invalid");
                        Toast.makeText(this, "❌ " + reason, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    verifyBtn.setText("✔️ SIMPAN & VERIFIKASI PENGATURAN");
                    verifyBtn.setEnabled(true);
                    if (!silent) Toast.makeText(this, "⚠️ Pengaturan disimpan lokal (Server sedang sibuk)", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void generateManualRpInApp() {
        savePrefsLocally();
        final String key = prefs.getString("api_key", "");
        final String sit = manualSitInput.getText().toString().trim();
        final String style = prefs.getString("rp_style", "WARGA");

        if (key.isEmpty()) {
            Toast.makeText(this, "⚠️ Isi API Key di atas dulu!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (sit.isEmpty()) {
            Toast.makeText(this, "⚠️ Ketik situasi RP terlebih dahulu!", Toast.LENGTH_SHORT).show();
            return;
        }

        testManualBtn.setText("⏳ Meracik Kalimat RP...");
        testManualBtn.setEnabled(false);

        new Thread(() -> {
            try {
                URL u = new URL(DEFAULT_SERVER + "/api/generate-rp");
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("key", key);
                body.put("situation", sit);
                body.put("style", style);
                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject res = new JSONObject(sb.toString());
                if (res.optBoolean("status", false)) {
                    final String me = res.optString("me", "");
                    final String doTxt = res.optString("do", "");
                    prefs.edit().putString("last_me", me).putString("last_do", doTxt).apply();

                    new Handler(Looper.getMainLooper()).post(() -> {
                        testManualBtn.setText("⚡ GENERATE RP MANUAL & COPY /ME");
                        testManualBtn.setEnabled(true);
                        lastMeBox.setText(me);
                        lastDoBox.setText(doTxt);
                        copyToClipboard(me, "✅ /me Otomatis Disalin!");
                    });
                } else {
                    final String err = res.optString("error", "Gagal memproses");
                    new Handler(Looper.getMainLooper()).post(() -> {
                        testManualBtn.setText("⚡ GENERATE RP MANUAL & COPY /ME");
                        testManualBtn.setEnabled(true);
                        Toast.makeText(this, "❌ " + err, Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    testManualBtn.setText("⚡ GENERATE RP MANUAL & COPY /ME");
                    testManualBtn.setEnabled(true);
                    Toast.makeText(this, "⚠️ Gagal menghubungi server.", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void requestAndStartFloatingCapture() {
        String key = prefs.getString("api_key", "");
        if (key.isEmpty()) {
            Toast.makeText(this, "⚠️ Masukkan API Key kamu terlebih dahulu di kolom #1!", Toast.LENGTH_LONG).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izinkan 'Tampilkan di atas aplikasi lain' terlebih dahulu!", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_OVERLAY);
            return;
        }

        // Minta izin tangkap layar 1x (MediaProjection) agar tombol bulat bisa jepret layar langsung!
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mpm != null) {
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_MEDIA_PROJECTION);
        } else {
            launchFloatingService(0, null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                requestAndStartFloatingCapture();
            }
        } else if (requestCode == REQ_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                launchFloatingService(resultCode, data);
            } else {
                // Tetap jalankan mode fallback (baca screenshot galeri) jika user menolak popup rekam layar
                launchFloatingService(0, null);
            }
        }
    }

    private void launchFloatingService(int resultCode, Intent resultData) {
        Intent svc = new Intent(this, FloatingOverlayService.class);
        svc.putExtra("resultCode", resultCode);
        if (resultData != null) {
            svc.putExtra("resultData", resultData);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }

        Toast.makeText(this, "📸 Tombol SS Ngambang Aktif! Tap 1x saat di SA-MP untuk langsung Scan & Copy /me!", Toast.LENGTH_LONG).show();
        moveTaskToBack(true);
    }

    private void copyToClipboard(String text, String toastMsg) {
        ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cb != null) {
            cb.setPrimaryClip(ClipData.newPlainText("AutoRP", text));
            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show();
        }
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

    private void addSectionTitle(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#9ca3af"));
        tv.setTextSize(11.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, 0, 0, 14);
        parent.addView(tv);
    }

    private EditText createInput(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(Color.parseColor("#6b7280"));
        et.setTextColor(Color.WHITE);
        et.setTextSize(13.5f);
        et.setPadding(28, 24, 28, 24);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0b1120"));
        bg.setCornerRadius(22f);
        bg.setStroke(2, Color.parseColor("#374151"));
        et.setBackground(bg);
        return et;
    }

    private Spinner createSpinner(String[] items) {
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(Color.WHITE);
                    ((TextView) v).setTextSize(13f);
                }
                return v;
            }
        };
        sp.setAdapter(adapter);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0b1120"));
        bg.setCornerRadius(22f);
        bg.setStroke(2, Color.parseColor("#374151"));
        sp.setBackground(bg);
        sp.setPadding(16, 12, 16, 12);
        return sp;
    }

    private Button createButton(String label, String bgColorHex, String textColorHex) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextColor(Color.parseColor(textColorHex));
        btn.setTextSize(13f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setPadding(24, 26, 24, 26);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(bgColorHex));
        bg.setCornerRadius(24f);
        btn.setBackground(bg);
        return btn;
    }

    private TextView createResultBox(String initialText, String colorHex) {
        TextView tv = new TextView(this);
        tv.setText(initialText);
        tv.setTextColor(Color.parseColor(colorHex));
        tv.setTextSize(12.5f);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setPadding(26, 24, 26, 24);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#060911"));
        bg.setCornerRadius(20f);
        bg.setStroke(2, Color.parseColor("#1e293b"));
        tv.setBackground(bg);
        return tv;
    }

    private void addSpacer(LinearLayout parent, int heightPx) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx));
    }
}