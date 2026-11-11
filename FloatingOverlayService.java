package com.chiwawa.autorp;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;

public class FloatingOverlayService extends Service {
    private static final String CHANNEL_ID = "chiwawa_v4_channel";
    private static final String DEFAULT_SERVER = "http://64.235.41.53:5010";

    private WindowManager wm;
    private LinearLayout rootFloat;
    private TextView bubbleBtn;
    private TextView statusPill;
    private WindowManager.LayoutParams params;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenW;
    private int screenH;
    private int screenDensity;

    private String activeApiKey = "DEFAULT_MELA";
    private String activeServerUrl = DEFAULT_SERVER;
    private boolean isBusy = false;
    private Handler mainHandler;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        screenDensity = dm.densityDpi;
        initFloatingViews();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences p1 = getSharedPreferences("chiwawa_autorp_prefs", MODE_PRIVATE);
        SharedPreferences p2 = getSharedPreferences("chiwawa_v3_prefs", MODE_PRIVATE);

        String k = intent != null ? intent.getStringExtra("api_key") : null;
        if (k == null || k.trim().isEmpty()) k = p1.getString("api_key", "");
        if (k == null || k.trim().isEmpty()) k = p2.getString("api_key", "");
        activeApiKey = (k == null || k.trim().isEmpty()) ? "DEFAULT_MELA" : k.trim().toUpperCase();

        int resultCode = intent != null ? intent.getIntExtra("resultCode", 0) : 0;
        Intent resultData = intent != null ? intent.getParcelableExtra("resultData") : null;
        boolean hasProj = (resultCode == Activity.RESULT_OK && resultData != null);

        startForegroundNotif(hasProj);
        if (hasProj) {
            initMediaProjection(resultCode, resultData);
        }
        return START_STICKY;
    }

    private void startForegroundNotif(boolean hasProj) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "CHIWAWA AutoRP", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);

            Notification notif = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("⚡ CHIWAWA AutoRP Aktif (" + activeApiKey + ")")
                    .setContentText("Tap 1x 📸 = Scan SS | Tahan 1 detik = Reset /me")
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasProj) {
                startForeground(1004, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(1004, notif);
            }
        }
    }

    private void initMediaProjection(int code, Intent data) {
        try {
            releaseProjection();
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (mpm == null) return;
            mediaProjection = mpm.getMediaProjection(code, data);
            if (mediaProjection == null) return;

            mediaProjection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() { releaseProjection(); }
            }, mainHandler);

            DisplayMetrics dm = getResources().getDisplayMetrics();
            screenW = dm.widthPixels;
            screenH = dm.heightPixels;
            screenDensity = dm.densityDpi;

            imageReader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2);
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ChiwawaV4Cap", screenW, screenH, screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, mainHandler
            );
        } catch (Exception ignored) {}
    }

    private void initFloatingViews() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        rootFloat = new LinearLayout(this);
        rootFloat.setOrientation(LinearLayout.HORIZONTAL);
        rootFloat.setGravity(Gravity.CENTER_VERTICAL);

        bubbleBtn = new TextView(this);
        bubbleBtn.setText("📸");
        bubbleBtn.setTextSize(20);
        bubbleBtn.setGravity(Gravity.CENTER);
        styleBubble("#00f5d4", "#ffffff");

        int px = (int) (52 * getResources().getDisplayMetrics().density);
        rootFloat.addView(bubbleBtn, new LinearLayout.LayoutParams(px, px));

        statusPill = new TextView(this);
        statusPill.setText("📸 SS OK!");
        statusPill.setTextColor(Color.WHITE);
        statusPill.setTextSize(11.5f);
        statusPill.setTypeface(Typeface.DEFAULT_BOLD);
        statusPill.setPadding(24, 14, 24, 14);
        stylePill("#0f172a", "#00f5d4");
        statusPill.setVisibility(View.GONE);

        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        pillLp.setMargins(12, 0, 0, 0);
        rootFloat.addView(statusPill, pillLp);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 24;
        params.y = 240;

        bubbleBtn.setOnTouchListener(new View.OnTouchListener() {
            private int initX, initY;
            private float touchX, touchY;
            private long downTs;
            private boolean dragged;
            private boolean longPressTriggered;
            private final Runnable longPressTask = () -> {
                if (!dragged && !isBusy) {
                    longPressTriggered = true;
                    triggerQuickInGameReset();
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downTs = System.currentTimeMillis();
                        dragged = false;
                        longPressTriggered = false;
                        initX = params.x;
                        initY = params.y;
                        touchX = ev.getRawX();
                        touchY = ev.getRawY();
                        mainHandler.postDelayed(longPressTask, 850);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (ev.getRawX() - touchX);
                        int dy = (int) (ev.getRawY() - touchY);
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            dragged = true;
                            mainHandler.removeCallbacks(longPressTask);
                        }
                        params.x = initX + dx;
                        params.y = initY + dy;
                        wm.updateViewLayout(rootFloat, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        mainHandler.removeCallbacks(longPressTask);
                        if (!dragged && !longPressTriggered && (System.currentTimeMillis() - downTs) < 450) {
                            captureAndSendToVisionAi();
                        }
                        return true;
                }
                return false;
            }
        });

        wm.addView(rootFloat, params);
    }

    private void triggerQuickInGameReset() {
        vibrateShort();
        SharedPreferences p = getSharedPreferences("chiwawa_autorp_prefs", MODE_PRIVATE);
        String k = p.getString("api_key", activeApiKey);
        final String key = (k == null || k.trim().isEmpty()) ? "DEFAULT_MELA" : k.trim().toUpperCase();
        showFloatingPill("🔄 Mereset giliran ke /me...", "#0f172a", "#38bdf8", 0);
        new Thread(() -> {
            try {
                URL u = new URL(activeServerUrl + "/reset?key=" + Uri.encode(key));
                u.openStream().close();
                showFloatingPill("✅ Giliran Di-Reset ke /me!", "#064e3b", "#10b981", 2800);
            } catch (Exception e) {
                showFloatingPill("⚠️ Gagal reset sinyal", "#7f1d1d", "#ef4444", 2500);
            }
        }).start();
    }

    private void styleBubble(String bgHex, String borderHex) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(Color.parseColor(bgHex));
        d.setStroke(4, Color.parseColor(borderHex));
        bubbleBtn.setBackground(d);
    }

    private void stylePill(String bgHex, String borderHex) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(bgHex));
        d.setCornerRadius(36f);
        d.setStroke(3, Color.parseColor(borderHex));
        statusPill.setBackground(d);
    }

    private void showFloatingPill(String text, String bgHex, String borderHex, int hideAfterMs) {
        mainHandler.post(() -> {
            statusPill.setText(text);
            stylePill(bgHex, borderHex);
            statusPill.setVisibility(View.VISIBLE);
            if (hideAfterMs > 0) {
                mainHandler.postDelayed(() -> {
                    if (!isBusy && statusPill != null) statusPill.setVisibility(View.GONE);
                }, hideAfterMs);
            }
        });
    }

    private boolean isBitmapValidAndNotBlack(Bitmap bm) {
        if (bm == null || bm.getWidth() < 50 || bm.getHeight() < 50) return false;
        int w = bm.getWidth();
        int h = bm.getHeight();
        int nonBlack = 0;
        int stepX = Math.max(1, w / 12);
        int stepY = Math.max(1, h / 12);
        for (int x = stepX; x < w; x += stepX) {
            for (int y = stepY; y < h; y += stepY) {
                int p = bm.getPixel(x, y);
                if (Color.red(p) > 12 || Color.green(p) > 12 || Color.blue(p) > 12) nonBlack++;
            }
        }
        return nonBlack >= 8;
    }

    private void captureAndSendToVisionAi() {
        if (isBusy) return;
        isBusy = true;
        vibrateShort();

        SharedPreferences p = getSharedPreferences("chiwawa_autorp_prefs", MODE_PRIVATE);
        String k = p.getString("api_key", activeApiKey);
        final String key = (k == null || k.trim().isEmpty()) ? "DEFAULT_MELA" : k.trim().toUpperCase();

        rootFloat.setAlpha(0f);

        mainHandler.postDelayed(() -> {
            Bitmap liveBm = grabProjectionBitmap();
            rootFloat.setAlpha(1f);

            new Thread(() -> {
                try {
                    String srcLabel = "Live Screen";
                    Bitmap finalBm = liveBm;

                    if (!isBitmapValidAndNotBlack(finalBm)) {
                        finalBm = grabLatestGalleryScreenshot();
                        srcLabel = "Galeri SS";
                    }

                    if (!isBitmapValidAndNotBlack(finalBm)) {
                        saveDebugStatus(false, "⚠️ Izin rekam layar belum aktif. Buka APK & klik Simpan & Munculkan!", "");
                        mainHandler.post(() -> {
                            isBusy = false;
                            bubbleBtn.setText("⚠️");
                            styleBubble("#ef4444", "#ffffff");
                            showFloatingPill("⚠️ Klik Simpan & Munculkan di APK (Atau ambil 1x SS HP)", "#7f1d1d", "#ef4444", 4500);
                        });
                        return;
                    }

                    int maxW = 1152;
                    if (finalBm.getWidth() > maxW) {
                        int newH = Math.round((float) finalBm.getHeight() * maxW / finalBm.getWidth());
                        finalBm = Bitmap.createScaledBitmap(finalBm, maxW, newH, true);
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    finalBm.compress(Bitmap.CompressFormat.JPEG, 78, baos);
                    byte[] jpegBytes = baos.toByteArray();
                    int sizeKb = Math.max(1, jpegBytes.length / 1024);

                    File localDebug = new File(getFilesDir(), "last_capture_debug.jpg");
                    FileOutputStream fos = new FileOutputStream(localDebug);
                    fos.write(jpegBytes);
                    fos.close();

                    mainHandler.post(() -> {
                        bubbleBtn.setText("⏳");
                        styleBubble("#f59e0b", "#ffffff");
                        showFloatingPill("📸 SS OK (" + sizeKb + "KB) ➔ ⏳ AI...", "#0f172a", "#38bdf8", 0);
                    });

                    URL u = new URL(activeServerUrl + "/rp?key=" + Uri.encode(key));
                    HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "image/jpeg");
                    conn.setConnectTimeout(22000);
                    conn.setReadTimeout(22000);
                    conn.setDoOutput(true);

                    OutputStream os = conn.getOutputStream();
                    os.write(jpegBytes);
                    os.close();

                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    final String rpOutput = sb.toString().trim();
                    saveDebugStatus(
                            true,
                            "✅ SS BERHASIL (" + sizeKb + " KB • " + finalBm.getWidth() + "x" + finalBm.getHeight() + " • " + srcLabel + ")",
                            rpOutput
                    );

                    mainHandler.post(() -> {
                        isBusy = false;
                        ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (cb != null) {
                            cb.setPrimaryClip(ClipData.newPlainText("AutoRP", rpOutput));
                        }
                        vibrateShort();
                        bubbleBtn.setText("✅");
                        styleBubble("#10b981", "#ffffff");

                        String shortPrev = rpOutput.length() > 36 ? rpOutput.substring(0, 36) + "..." : rpOutput;
                        showFloatingPill("✅ [" + sizeKb + "KB] " + shortPrev, "#064e3b", "#10b981", 4500);

                        mainHandler.postDelayed(() -> {
                            bubbleBtn.setText("📸");
                            styleBubble("#00f5d4", "#ffffff");
                        }, 2000);
                    });
                } catch (Exception e) {
                    saveDebugStatus(false, "⚠️ Sinyal sibuk saat mengirim SS ke server.", "");
                    mainHandler.post(() -> {
                        isBusy = false;
                        bubbleBtn.setText("📸");
                        styleBubble("#ef4444", "#ffffff");
                        showFloatingPill("⚠️ Server Sibuk, Tap 📸 Sekali Lagi", "#7f1d1d", "#ef4444", 3500);
                    });
                }
            }).start();
        }, 110);
    }

    private void saveDebugStatus(boolean ok, String statusText, String rpOut) {
        String[] prefNames = new String[]{"chiwawa_autorp_prefs", "chiwawa_v3_prefs"};
        for (String pName : prefNames) {
            SharedPreferences.Editor ed = getSharedPreferences(pName, MODE_PRIVATE).edit();
            ed.putBoolean("last_ss_ok", ok);
            ed.putString("last_ss_status", statusText);
            if (rpOut != null && !rpOut.isEmpty()) ed.putString("last_rp_output", rpOut);
            ed.apply();
        }
    }

    private Bitmap grabProjectionBitmap() {
        if (imageReader == null) return null;
        Image img = null;
        try {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            if (dm.widthPixels != screenW || dm.heightPixels != screenH) {
                screenW = dm.widthPixels;
                screenH = dm.heightPixels;
                if (virtualDisplay != null) {
                    imageReader.close();
                    imageReader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2);
                    virtualDisplay.resize(screenW, screenH, dm.densityDpi);
                    virtualDisplay.setSurface(imageReader.getSurface());
                    Thread.sleep(90);
                }
            }
            img = imageReader.acquireLatestImage();
            if (img == null) {
                Thread.sleep(70);
                img = imageReader.acquireLatestImage();
            }
            if (img == null) return null;

            int w = img.getWidth();
            int h = img.getHeight();
            Image.Plane[] planes = img.getPlanes();
            ByteBuffer buf = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * w;

            Bitmap bm = Bitmap.createBitmap(w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888);
            bm.copyPixelsFromBuffer(buf);
            return Bitmap.createBitmap(bm, 0, 0, w, h);
        } catch (Exception e) {
            return null;
        } finally {
            if (img != null) {
                try { img.close(); } catch (Exception ignored) {}
            }
        }
    }

    private Bitmap grabLatestGalleryScreenshot() {
        Cursor c = null;
        try {
            String[] proj = new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED};
            c = getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    proj, null, null,
                    MediaStore.Images.Media.DATE_ADDED + " DESC"
            );
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                InputStream is = getContentResolver().openInputStream(uri);
                if (is != null) {
                    Bitmap bm = BitmapFactory.decodeStream(is);
                    is.close();
                    return bm;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    private void vibrateShort() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(40);
        } catch (Exception ignored) {}
    }

    private void releaseProjection() {
        if (virtualDisplay != null) { try { virtualDisplay.release(); } catch (Exception ignored) {} virtualDisplay = null; }
        if (imageReader != null) { try { imageReader.close(); } catch (Exception ignored) {} imageReader = null; }
        if (mediaProjection != null) { try { mediaProjection.stop(); } catch (Exception ignored) {} mediaProjection = null; }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseProjection();
        if (rootFloat != null && wm != null) {
            try { wm.removeView(rootFloat); } catch (Exception ignored) {}
        }
    }
}