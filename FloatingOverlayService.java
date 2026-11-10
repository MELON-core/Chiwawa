package com.chiwawa.autorp;

import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;

public class FloatingOverlayService extends Service {
    private WindowManager wm;
    private TextView bubbleView;
    private LinearLayout panelLayout;
    private WebView webView;
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams panelParams;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String url = intent != null && intent.getStringExtra("url") != null
                ? intent.getStringExtra("url")
                : "http://64.235.41.53:5010";
        if (webView != null) {
            webView.loadUrl(url);
        }
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        // 1. BUAT TOMBOL BULAT NGAMBANG (⚡) YANG BISA DIGESER DI ATAS SA-MP
        bubbleView = new TextView(this);
        bubbleView.setText("⚡");
        bubbleView.setTextSize(22);
        bubbleView.setGravity(Gravity.CENTER);
        bubbleView.setTextColor(Color.parseColor("#041f18"));

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.parseColor("#00f5d4"));
        circle.setStroke(4, Color.WHITE);
        bubbleView.setBackground(circle);

        int sizePx = (int) (54 * getResources().getDisplayMetrics().density);
        bubbleParams = new WindowManager.LayoutParams(
                sizePx, sizePx, overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 30;
        bubbleParams.y = 280;

        // 2. BUAT PANEL JENDELA OVERLAY SAAT TOMBOL BULAT DIKLIK
        panelLayout = new LinearLayout(this);
        panelLayout.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.parseColor("#090d16"));
        panelBg.setCornerRadius(32f);
        panelBg.setStroke(3, Color.parseColor("#00f5d4"));
        panelLayout.setBackground(panelBg);
        panelLayout.setPadding(12, 12, 12, 12);

        webView = new WebView(this);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new WebAppBridge(), "AndroidBridge");
        webView.loadUrl(getSharedPreferences("cfg", MODE_PRIVATE).getString("url", "http://64.235.41.53:5010"));

        int panelW = (int) (315 * getResources().getDisplayMetrics().density);
        int panelH = (int) (430 * getResources().getDisplayMetrics().density);
        panelLayout.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        panelParams = new WindowManager.LayoutParams(
                panelW, panelH, overlayType,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        panelParams.gravity = Gravity.CENTER;

        setupBubbleDragAndClick();
        wm.addView(bubbleView, bubbleParams);
        panelLayout.setVisibility(View.GONE);
        wm.addView(panelLayout, panelParams);
    }

    private void setupBubbleDragAndClick() {
        bubbleView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long downTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downTime = System.currentTimeMillis();
                        initialX = bubbleParams.x;
                        initialY = bubbleParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        bubbleParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        bubbleParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        wm.updateViewLayout(bubbleView, bubbleParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (System.currentTimeMillis() - downTime < 220) {
                            bubbleView.setVisibility(View.GONE);
                            panelLayout.setVisibility(View.VISIBLE);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    // JEMBATAN NATIVE ANDROID UNTUK CLIPBOARD, MINIMIZE & AMBIL SCREENSHOT TERAKHIR
    public class WebAppBridge {
        @JavascriptInterface
        public void minimizeOverlay() {
            new Handler(Looper.getMainLooper()).post(() -> {
                panelLayout.setVisibility(View.GONE);
                bubbleView.setVisibility(View.VISIBLE);
            });
        }

        @JavascriptInterface
        public void copyClipboard(String text) {
            new Handler(Looper.getMainLooper()).post(() -> {
                ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText("AutoRP", text));
            });
        }

        @JavascriptInterface
        public void showToast(String msg) {
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show()
            );
        }

        @JavascriptInterface
        public String getLatestScreenshotBase64() {
            try {
                File[] dirs = new File[]{
                        new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots"),
                        new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Screenshots")
                };
                File newest = null;
                for (File dir : dirs) {
                    if (dir.exists() && dir.isDirectory()) {
                        File[] files = dir.listFiles();
                        if (files != null) {
                            for (File f : files) {
                                String n = f.getName().toLowerCase();
                                if (f.isFile() && (n.endsWith(".jpg") || n.endsWith(".png") || n.endsWith(".jpeg"))) {
                                    if (newest == null || f.lastModified() > newest.lastModified()) {
                                        newest = f;
                                    }
                                }
                            }
                        }
                    }
                }
                if (newest == null) return "";

                Bitmap bm = BitmapFactory.decodeFile(newest.getAbsolutePath());
                if (bm == null) return "";
                int maxW = 960;
                if (bm.getWidth() > maxW) {
                    int h = Math.round((float) bm.getHeight() * maxW / bm.getWidth());
                    bm = Bitmap.createScaledBitmap(bm, maxW, h, true);
                }
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bm.compress(Bitmap.CompressFormat.JPEG, 72, baos);
                return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
            } catch (Exception e) {
                return "";
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bubbleView != null) wm.removeView(bubbleView);
        if (panelLayout != null) wm.removeView(panelLayout);
    }
}