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
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;

public class FloatingOverlayService extends Service {
    private static final String CHANNEL_ID = "chiwawa_overlay_channel";
    private static final String DEFAULT_SERVER = "http://64.235.41.53:5010";

    private WindowManager wm;
    private LinearLayout floatingContainer;
    private TextView mainBubble;
    private TextView doQuickBadge;
    private WindowManager.LayoutParams overlayParams;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    private boolean isScanning = false;
    private String lastDoResult = "";
    private Handler mainHandler;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;
        screenDensity = dm.densityDpi;

        buildFloatingBubbleUi();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int resultCode = intent != null ? intent.getIntExtra("resultCode", 0) : 0;
        Intent resultData = intent != null ? intent.getParcelableExtra("resultData") : null;
        boolean hasProjectionData = (resultCode == Activity.RESULT_OK && resultData != null);

        startForegroundNotification(hasProjectionData);

        if (hasProjectionData) {
            setupLiveMediaProjection(resultCode, resultData);
        }

        updateBubbleSizeFromPrefs();
        return START_STICKY;
    }

    private void startForegroundNotification(boolean withProjectionType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "CHIWAWA AutoRP Floating SS",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);

            Notification notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("⚡ CHIWAWA AutoRP Aktif")
                    .setContentText("Tap tombol ngambang 📸 untuk langsung Scan SS & Copy /me")
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && withProjectionType) {
                startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(1001, notification);
            }
        }
    }

    private void setupLiveMediaProjection(int resultCode, Intent resultData) {
        try {
            stopProjectionResources();
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (mpm == null) return;

            mediaProjection = mpm.getMediaProjection(resultCode, resultData);
            if (mediaProjection == null) return;

            mediaProjection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    stopProjectionResources();
                }
            }, mainHandler);

            DisplayMetrics dm = getResources().getDisplayMetrics();
            screenWidth = dm.widthPixels;
            screenHeight = dm.heightPixels;
            screenDensity = dm.densityDpi;

            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ChiwawaScreenCapture",
                    screenWidth,
                    screenHeight,
                    screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null,
                    mainHandler
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void buildFloatingBubbleUi() {
        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        floatingContainer = new LinearLayout(this);
        floatingContainer.setOrientation(LinearLayout.HORIZONTAL);
        floatingContainer.setGravity(Gravity.CENTER_VERTICAL);

        // 1. TOMBOL UTAMA BULAT (📸) — TAP 1X LANGSUNG SCREENSHOT & SCAN!
        mainBubble = new TextView(this);
        mainBubble.setText("📸");
        mainBubble.setTextSize(20);
        mainBubble.setGravity(Gravity.CENTER);
        setBubbleAppearance("#00f5d4", "#ffffff");

        SharedPreferences prefs = getSharedPreferences("chiwawa_autorp_prefs", MODE_PRIVATE);
        int dpSize = prefs.getInt("bubble_dp", 52);
        int pxSize = (int) (dpSize * getResources().getDisplayMetrics().density);

        LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(pxSize, pxSize);
        floatingContainer.addView(mainBubble, bubbleLp);

        // 2. BADGE KECIL [/do] DI SAMPING TOMBOL BULAT (MUNCUL SAAT HASIL RP SELESAI)
        doQuickBadge = new TextView(this);
        doQuickBadge.setText("📋 /do");
        doQuickBadge.setTextColor(Color.parseColor("#041f18"));
        doQuickBadge.setTextSize(12);
        doQuickBadge.setTypeface(Typeface.DEFAULT_BOLD);
        doQuickBadge.setPadding(24, 14, 24, 14);
        GradientDrawable doBg = new GradientDrawable();
        doBg.setColor(Color.parseColor("#38bdf8"));
        doBg.setCornerRadius(40f);
        doBg.setStroke(3, Color.WHITE);
        doQuickBadge.setBackground(doBg);
        doQuickBadge.setVisibility(View.GONE);

        LinearLayout.LayoutParams doLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        doLp.setMargins(12, 0, 0, 0);
        floatingContainer.addView(doQuickBadge, doLp);

        doQuickBadge.setOnClickListener(v -> {
            if (!lastDoResult.isEmpty()) {
                copyClipboardNative(lastDoResult);
                Toast.makeText(getApplicationContext(), "✅ Disalin: " + lastDoResult, Toast.LENGTH_SHORT).show();
                doQuickBadge.setVisibility(View.GONE);
            }
        });

        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.TOP | Gravity.START;
        overlayParams.x = 24;
        overlayParams.y = 260;

        setupTouchAndTapTrigger();
        wm.addView(floatingContainer, overlayParams);
    }

    private void updateBubbleSizeFromPrefs() {
        if (mainBubble == null) return;
        SharedPreferences prefs = getSharedPreferences("chiwawa_autorp_prefs", MODE_PRIVATE);
        int dpSize = prefs.getInt("bubble_dp", 52);
        int pxSize = (int) (dpSize * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) mainBubble.getLayoutParams();
        lp.width = pxSize;
        lp.height = pxSize;
        mainBubble.setLayoutParams(lp);
    }

    private void setBubbleAppearance(String bgHex, String strokeHex) {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.parseColor(bgHex));
        circle.setStroke(4, Color.parseColor(strokeHex));
        mainBubble.setBackground(circle);
    }

    private void setupTouchAndTapTrigger() {
        mainBubble.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long downTime;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downTime = System.currentTimeMillis();
                        moved = false;
                        initialX = overlayParams.x;
                        initialY = overlayParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            moved = true;
                        }
                        overlayParams.x = initialX + dx;
                        overlayParams.y = initialY + dy;
                        wm.updateViewLayout(floatingContainer, overlayParams);
                        return true;

                    case MotionEvent.ACTION_UP:
                        long elapsed = System.currentTimeMillis() - downTime;
                        if (!moved && elapsed < 350) {
                            // TAP 1X -> LANGSUNG JEPRET LAYAR & PROSES OCR! (TIDAK BUKA PANEL)
                            triggerDirectScreenshotAndOcr();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    // =========================================================================
    // AKSI UTAMA SAAT TOMBOL NGAMBANG DI-TAP: LANGSUNG SS -> OCR -> AUTO-COPY!
    // =========================================================================
    private void triggerDirectScreenshotAndOcr() {
        if (isScanning) {
            Toast.makeText(this, "⏳ Sedang memindai layar, tunggu sebentar...", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("chiwawa_autorp_prefs", MODE_PRIVATE);
        final String apiKey = prefs.getString("api_key", "");
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "⚠️ Buka aplikasi CHIWAWA AutoRP dulu untuk mengisi API Key!", Toast.LENGTH_LONG).show();
            return;
        }

        isScanning = true;
        vibrateShort();
        doQuickBadge.setVisibility(View.GONE);

        // Sembunyikan tombol bulat 120ms agar tidak ikut terfoto di dalam screenshot
        floatingContainer.setAlpha(0f);

        mainHandler.postDelayed(() -> {
            Bitmap capturedBitmap = captureLiveScreenFrame();

            // Kembalikan tampilan tombol bulat dengan status Loading (⏳)
            floatingContainer.setAlpha(1f);
            mainBubble.setText("⏳");
            setBubbleAppearance("#f59e0b", "#ffffff");

            new Thread(() -> {
                try {
                    Bitmap finalBitmap = capturedBitmap;
                    if (finalBitmap == null) {
                        finalBitmap = loadLatestScreenshotFromMediaStore();
                    }

                    if (finalBitmap == null) {
                        showResultOnMainThread(false, "Gagal mengambil gambar layar. Pastikan izin layar aktif atau ambil 1x SS.", "", "");
                        return;
                    }

                    String cropMode = prefs.getString("crop_mode", "CHATLOG_AREA");
                    String rpStyle = prefs.getString("rp_style", "WARGA");
                    String serverUrl = prefs.getString("server_url", DEFAULT_SERVER);

                    String base64Jpeg = encodeAndCropBitmap(finalBitmap, cropMode);

                    URL u = new URL(serverUrl + "/api/ocr-rp");
                    HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setConnectTimeout(20000);
                    conn.setReadTimeout(20000);
                    conn.setDoOutput(true);

                    JSONObject reqJson = new JSONObject();
                    reqJson.put("key", apiKey);
                    reqJson.put("imageBase64", base64Jpeg);
                    reqJson.put("style", rpStyle);

                    OutputStream os = conn.getOutputStream();
                    os.write(reqJson.toString().getBytes("UTF-8"));
                    os.close();

                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject res = new JSONObject(sb.toString());
                    if (res.optBoolean("status", false)) {
                        String me = res.optString("me", "");
                        String doTxt = res.optString("do", "");
                        showResultOnMainThread(true, "", me, doTxt);
                    } else {
                        String err = res.optString("error", "Gagal membaca chatlog");
                        showResultOnMainThread(false, err, "", "");
                    }
                } catch (Exception e) {
                    showResultOnMainThread(false, "Koneksi sibuk, coba tap 📸 sekali lagi.", "", "");
                }
            }).start();
        }, 120);
    }

    private Bitmap captureLiveScreenFrame() {
        if (imageReader == null) return null;
        Image image = null;
        try {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            if (dm.widthPixels != screenWidth || dm.heightPixels != screenHeight) {
                screenWidth = dm.widthPixels;
                screenHeight = dm.heightPixels;
                if (virtualDisplay != null && imageReader != null) {
                    imageReader.close();
                    imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
                    virtualDisplay.resize(screenWidth, screenHeight, dm.densityDpi);
                    virtualDisplay.setSurface(imageReader.getSurface());
                    Thread.sleep(90);
                }
            }

            image = imageReader.acquireLatestImage();
            if (image == null) {
                Thread.sleep(80);
                image = imageReader.acquireLatestImage();
            }
            if (image == null) return null;

            int width = image.getWidth();
            int height = image.getHeight();
            final Image.Plane[] planes = image.getPlanes();
            final ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * width;

            Bitmap bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
            );
            bitmap.copyPixelsFromBuffer(buffer);
            return Bitmap.createBitmap(bitmap, 0, 0, width, height);
        } catch (Exception e) {
            return null;
        } finally {
            if (image != null) {
                try { image.close(); } catch (Exception ignored) {}
            }
        }
    }

    // Fallback: Baca screenshot paling baru lewat ContentResolver MediaStore (Kompatibel Android 10-14)
    private Bitmap loadLatestScreenshotFromMediaStore() {
        Cursor cursor = null;
        try {
            String[] projection = new String[]{
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATE_ADDED
            };
            String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";
            cursor = getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
            );
            if (cursor != null && cursor.moveToFirst()) {
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                long id = cursor.getLong(idCol);
                Uri contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                InputStream is = getContentResolver().openInputStream(contentUri);
                if (is != null) {
                    Bitmap bm = BitmapFactory.decodeStream(is);
                    is.close();
                    if (bm != null) return bm;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }

    private String encodeAndCropBitmap(Bitmap src, String cropMode) {
        Bitmap target = src;
        int w = src.getWidth();
        int h = src.getHeight();

        // Jika layar Landscape (sedang main GTA SA-MP) & mode Fokus Chatlog aktif:
        // Potong area chatlog SA-MP (kiri atas sampai tengah) agar OCR 3x lebih cepat & bebas gangguan HUD!
        if ("CHATLOG_AREA".equals(cropMode) && w > h) {
            int startX = (int) (w * 0.14f);
            int cropW = (int) (w * 0.68f);
            int cropH = (int) (h * 0.58f);
            if (startX + cropW <= w && cropH <= h) {
                target = Bitmap.createBitmap(src, startX, 0, cropW, cropH);
            }
        }

        int maxDim = 960;
        if (target.getWidth() > maxDim) {
            int newH = Math.round((float) target.getHeight() * maxDim / target.getWidth());
            target = Bitmap.createScaledBitmap(target, maxDim, newH, true);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        target.compress(Bitmap.CompressFormat.JPEG, 76, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
    }

    private void showResultOnMainThread(boolean success, String errMsg, String meText, String doText) {
        mainHandler.post(() -> {
            isScanning = false;
            if (!success) {
                mainBubble.setText("📸");
                setBubbleAppearance("#ef4444", "#ffffff");
                Toast.makeText(getApplicationContext(), "❌ " + errMsg, Toast.LENGTH_LONG).show();
                mainHandler.postDelayed(() -> setBubbleAppearance("#00f5d4", "#ffffff"), 1800);
                return;
            }

            SharedPreferences prefs = getSharedPreferences("chiwawa_autorp_prefs", MODE_PRIVATE);
            prefs.edit().putString("last_me", meText).putString("last_do", doText).apply();
            lastDoResult = doText;

            String copyMode = prefs.getString("copy_mode", "ME_FIRST");
            if ("DO_ONLY".equals(copyMode)) {
                copyClipboardNative(doText);
                Toast.makeText(getApplicationContext(), "✅ Disalin: " + doText, Toast.LENGTH_LONG).show();
            } else if ("BOTH".equals(copyMode)) {
                copyClipboardNative(meText + "\n" + doText);
                Toast.makeText(getApplicationContext(), "✅ /me & /do Disalin!\n" + meText, Toast.LENGTH_LONG).show();
            } else {
                copyClipboardNative(meText);
                Toast.makeText(getApplicationContext(), "✅ /me Disalin: " + meText, Toast.LENGTH_LONG).show();
                // Munculkan tombol kecil [📋 /do] di sebelah bubble agar bisa di-tap setelah paste /me!
                doQuickBadge.setVisibility(View.VISIBLE);
                mainHandler.postDelayed(() -> {
                    if (doQuickBadge != null) doQuickBadge.setVisibility(View.GONE);
                }, 12000);
            }

            vibrateShort();
            mainBubble.setText("✅");
            setBubbleAppearance("#10b981", "#ffffff");

            mainHandler.postDelayed(() -> {
                mainBubble.setText("📸");
                setBubbleAppearance("#00f5d4", "#ffffff");
            }, 2000);
        });
    }

    private void copyClipboardNative(String text) {
        ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cb != null) {
            cb.setPrimaryClip(ClipData.newPlainText("AutoRP", text));
        }
    }

    private void vibrateShort() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(45);
        } catch (Exception ignored) {}
    }

    private void stopProjectionResources() {
        if (virtualDisplay != null) {
            try { virtualDisplay.release(); } catch (Exception ignored) {}
            virtualDisplay = null;
        }
        if (imageReader != null) {
            try { imageReader.close(); } catch (Exception ignored) {}
            imageReader = null;
        }
        if (mediaProjection != null) {
            try { mediaProjection.stop(); } catch (Exception ignored) {}
            mediaProjection = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopProjectionResources();
        if (floatingContainer != null && wm != null) {
            try { wm.removeView(floatingContainer); } catch (Exception ignored) {}
        }
    }
}