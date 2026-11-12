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
    private static final String CHANNEL_ID = "chiwawa_v7_sana";
    private static final String DEFAULT_SERVER = "http://64.235.41.53:5010";

    private WindowManager wm;
    private LinearLayout rootFloat;
    private TextView bubbleBtn;
    private TextView statusPill;
    private WindowManager.LayoutParams params;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Bitmap lastValidFrameCache;
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
        updateRealScreenMetrics();
        initFloatingViews();
    }

    private void updateRealScreenMetrics() {
        DisplayMetrics dm = new DisplayMetrics();
        if (wm != null && wm.getDefaultDisplay() != null) {
            wm.getDefaultDisplay().getRealMetrics(dm);
        } else {
            dm = getResources().getDisplayMetrics();
        }
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;
        screenDensity = dm.densityDpi;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences p1 = getSharedPreferences("chiwawa_autorp_prefs", MODE_PRIVATE);
        String k = intent != null ? intent.getStringExtra("api_key") : null;
        if (k == null || k.trim().isEmpty()) k = p1.getString("api_key", "DEFAULT_MELA");
        activeApiKey = (k == null || k.trim().isEmpty()) ? "DEFAULT_MELA" : k.trim().toUpperCase();

        int resultCode = intent != null ? intent.getIntExtra("resultCode", 0) : 0;
        Intent resultData = intent != null ? intent.getParcelableExtra("resultData") : null;
        boolean hasProj = (resultCode == Activity.RESULT_OK && resultData != null);

        startForegroundNotif(hasProj);
        if (hasProj) initMediaProjection(resultCode, resultData);
        return START_STICKY;
    }

    private void startForegroundNotif(boolean hasProj) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "CHIWAWA Perapi SANA", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
            Notification notif = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("⚡ CHIWAWA Vision V7 Aktif")
                    .setContentText("Tap 1x 📸 untuk baca & rapikan iklan SANA otomatis")
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .build();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasProj) {
                startForeground(1007, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(1007, notif);
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
            updateRealScreenMetrics();
            imageReader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2);
            virtualDisplay = mediaProjection.createVirtualDisplay("ChiwawaV7Sana", screenW, screenH, screenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, mainHandler);
        } catch (Exception ignored) {}
    }

    private void initFloatingViews() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
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
        statusPill.setText("⚡ Siap");
        statusPill.setTextColor(Color.WHITE);
        statusPill.setTextSize(11.5f);
        statusPill.setTypeface(Typeface.DEFAULT_BOLD);
        statusPill.setPadding(24, 14, 24, 14);
        stylePill("#0f172a", "#00f5d4");
        statusPill.setVisibility(View.GONE);

        LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pillLp.setMargins(12, 0, 0, 0);
        rootFloat.addView(statusPill, pillLp);

        params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 24;
        params.y = 240;

        bubbleBtn.setOnTouchListener(new View.OnTouchListener() {
            private int initX, initY;
            private float touchX, touchY;
            private long downTs;
            private boolean dragged;

            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downTs = System.currentTimeMillis();
                        dragged = false;
                        initX = params.x;
                        initY = params.y;
                        touchX = ev.getRawX();
                        touchY = ev.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (ev.getRawX() - touchX);
                        int dy = (int) (ev.getRawY() - touchY);
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) dragged = true;
                        params.x = initX + dx;
                        params.y = initY + dy;
                        wm.updateViewLayout(rootFloat, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragged && (System.currentTimeMillis() - downTs) < 500) {
                            captureAndFormatSanaAd();
                        }
                        return true;
                }
                return false;
            }
        });
        wm.addView(rootFloat, params);
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

    private boolean isBitmapValid(Bitmap bm) {
        if (bm == null || bm.getWidth() < 50 || bm.getHeight() < 50) return false;
        int w = bm.getWidth(), h = bm.getHeight(), nonBlack = 0;
        int stepX = Math.max(1, w / 14), stepY = Math.max(1, h / 14);
        for (int x = stepX; x < w; x += stepX) {
            for (int y = stepY; y < h; y += stepY) {
                int p = bm.getPixel(x, y);
                if (Color.red(p) > 12 || Color.green(p) > 12 || Color.blue(p) > 12) nonBlack++;
            }
        }
        return nonBlack >= 10;
    }

    private void captureAndFormatSanaAd() {
        if (isBusy) return;
        isBusy = true;
        vibrateShort();

        SharedPreferences p = getSharedPreferences("chiwawa_autorp_prefs", MODE_PRIVATE);
        final String key = p.getString("api_key", activeApiKey);
        rootFloat.setAlpha(0f);

        mainHandler.postDelayed(() -> {
            new Thread(() -> {
                try {
                    Bitmap liveBm = grabProjectionBitmapWithLandscapeFix();
                    mainHandler.post(() -> rootFloat.setAlpha(1f));

                    Bitmap finalBm = liveBm;
                    if (!isBitmapValid(finalBm) && isBitmapValid(lastValidFrameCache)) {
                        finalBm = lastValidFrameCache;
                    }
                    if (!isBitmapValid(finalBm)) {
                        finalBm = grabLatestGalleryScreenshot();
                    }
                    if (!isBitmapValid(finalBm)) {
                        mainHandler.post(() -> {
                            isBusy = false;
                            bubbleBtn.setText("⚠️");
                            styleBubble("#ef4444", "#ffffff");
                            showFloatingPill("⚠️ Izin layar belum aktif / Ambil 1x SS", "#7f1d1d", "#ef4444", 4000);
                        });
                        return;
                    }

                    lastValidFrameCache = finalBm;

                    int maxDim = 1280;
                    if (finalBm.getWidth() > maxDim || finalBm.getHeight() > maxDim) {
                        float ratio = Math.min((float) maxDim / finalBm.getWidth(), (float) maxDim / finalBm.getHeight());
                        int newW = Math.round(finalBm.getWidth() * ratio);
                        int newH = Math.round(finalBm.getHeight() * ratio);
                        finalBm = Bitmap.createScaledBitmap(finalBm, newW, newH, true);
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    finalBm.compress(Bitmap.CompressFormat.JPEG, 86, baos);
                    byte[] jpegBytes = baos.toByteArray();
                    int sizeKb = Math.max(1, jpegBytes.length / 1024);

                    File localDebug = new File(getFilesDir(), "last_capture_debug.jpg");
                    FileOutputStream fos = new FileOutputStream(localDebug);
                    fos.write(jpegBytes);
                    fos.close();

                    mainHandler.post(() -> {
                        bubbleBtn.setText("⏳");
                        styleBubble("#f59e0b", "#ffffff");
                        showFloatingPill("⚡ Membaca & Merapikan Iklan...", "#0f172a", "#38bdf8", 0);
                    });

                    URL u = new URL(activeServerUrl + "/rp?key=" + Uri.encode(key));
                    HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "image/jpeg");
                    conn.setConnectTimeout(18000);
                    conn.setReadTimeout(18000);
                    conn.setDoOutput(true);

                    OutputStream os = conn.getOutputStream();
                    os.write(jpegBytes);
                    os.close();

                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    final String adOutput = sb.toString().trim();
                    p.edit().putBoolean("last_ss_ok", true).putString("last_ss_status", "✅ Iklan Dirapikan (" + sizeKb + " KB • " + finalBm.getWidth() + "x" + finalBm.getHeight() + ")").putString("last_rp_output", adOutput).apply();

                    mainHandler.post(() -> {
                        isBusy = false;
                        ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (cb != null) cb.setPrimaryClip(ClipData.newPlainText("SANA_AD", adOutput));
                        vibrateShort();
                        bubbleBtn.setText("✅");
                        styleBubble("#10b981", "#ffffff");
                        String shortPrev = adOutput.length() > 40 ? adOutput.substring(0, 40) + "..." : adOutput;
                        showFloatingPill("✅ Disalin: " + shortPrev, "#064e3b", "#10b981", 4500);
                        mainHandler.postDelayed(() -> {
                            bubbleBtn.setText("📸");
                            styleBubble("#00f5d4", "#ffffff");
                        }, 1600);
                    });
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        rootFloat.setAlpha(1f);
                        isBusy = false;
                        bubbleBtn.setText("📸");
                        styleBubble("#ef4444", "#ffffff");
                        showFloatingPill("⚠️ Coba tap 📸 sekali lagi", "#7f1d1d", "#ef4444", 3000);
                    });
                }
            }).start();
        }, 90);
    }

    private Bitmap grabProjectionBitmapWithLandscapeFix() {
        if (imageReader == null || virtualDisplay == null) return null;
        try {
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            if (dm.widthPixels != screenW || dm.heightPixels != screenH) {
                screenW = dm.widthPixels;
                screenH = dm.heightPixels;
                screenDensity = dm.densityDpi;
                mainHandler.post(() -> {
                    try {
                        if (imageReader != null) imageReader.close();
                        imageReader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2);
                        virtualDisplay.resize(screenW, screenH, screenDensity);
                        virtualDisplay.setSurface(imageReader.getSurface());
                    } catch (Exception ignored) {}
                });
                Thread.sleep(220);
            }

            for (int i = 0; i < 4; i++) {
                Image img = imageReader.acquireLatestImage();
                if (img != null) {
                    try {
                        int w = img.getWidth(), h = img.getHeight();
                        Image.Plane[] planes = img.getPlanes();
                        ByteBuffer buf = planes[0].getBuffer();
                        int pixelStride = planes[0].getPixelStride(), rowStride = planes[0].getRowStride();
                        int rowPadding = rowStride - pixelStride * w;
                        Bitmap bm = Bitmap.createBitmap(w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888);
                        bm.copyPixelsFromBuffer(buf);
                        Bitmap cropped = Bitmap.createBitmap(bm, 0, 0, w, h);
                        if (isBitmapValid(cropped)) return cropped;
                    } finally {
                        try { img.close(); } catch (Exception ignored) {}
                    }
                }
                Thread.sleep(65);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Bitmap grabLatestGalleryScreenshot() {
        Cursor c = null;
        try {
            String[] proj = new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED};
            c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC");
            if (c != null && c.moveToFirst()) {
                long id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                InputStream is = getContentResolver().openInputStream(uri);
                if (is != null) { Bitmap bm = BitmapFactory.decodeStream(is); is.close(); return bm; }
            }
        } catch (Exception ignored) {}
        finally { if (c != null) c.close(); }
        return null;
    }

    private void vibrateShort() {
        try { Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); if (v != null) v.vibrate(40); } catch (Exception ignored) {}
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
        if (rootFloat != null && wm != null) { try { wm.removeView(rootFloat); } catch (Exception ignored) {} }
    }
}