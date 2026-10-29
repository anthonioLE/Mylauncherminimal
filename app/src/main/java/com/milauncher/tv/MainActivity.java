package com.milauncher.tv;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private WebView web;

    // Iconos ya convertidos a PNG (se piden uno por uno, solo los que se ven)
    private final LruCache<String, byte[]> iconCache = new LruCache<String, byte[]>(96);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.setBackgroundColor(0xFF07102A);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (u != null && "icons.local".equals(u.getHost())) {
                    byte[] data = iconBytes(u.getLastPathSegment());
                    if (data != null) {
                        return new WebResourceResponse("image/png", null, new ByteArrayInputStream(data));
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });

        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (web != null) {
            web.evaluateJavascript("window.onHome && window.onHome()", null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.evaluateJavascript("window.refreshApps && window.refreshApps()", null);
        }
    }

    // Atrás cierra "Todas las apps" si está abierta; en el inicio no hace nada.
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (web != null) {
                web.evaluateJavascript("window.onBack && window.onBack()", null);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
    }

    private class Bridge {

        @JavascriptInterface
        public String getApps() {
            try {
                PackageManager pm = getPackageManager();
                List<ResolveInfo> list = new ArrayList<ResolveInfo>();
                Set<String> seen = new HashSet<String>();

                Intent tv = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
                Intent std = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER);

                for (ResolveInfo ri : pm.queryIntentActivities(tv, 0)) {
                    if (seen.add(ri.activityInfo.packageName)) list.add(ri);
                }
                for (ResolveInfo ri : pm.queryIntentActivities(std, 0)) {
                    if (seen.add(ri.activityInfo.packageName)) list.add(ri);
                }

                final PackageManager fpm = pm;
                Collections.sort(list, new Comparator<ResolveInfo>() {
                    @Override
                    public int compare(ResolveInfo a, ResolveInfo b) {
                        return a.loadLabel(fpm).toString()
                                .compareToIgnoreCase(b.loadLabel(fpm).toString());
                    }
                });

                JSONArray arr = new JSONArray();
                for (ResolveInfo ri : list) {
                    String pkg = ri.activityInfo.packageName;
                    if (pkg.equals(getPackageName())) continue;
                    JSONObject o = new JSONObject();
                    o.put("label", ri.loadLabel(pm).toString());
                    o.put("pkg", pkg);
                    int cat = -1;
                    if (Build.VERSION.SDK_INT >= 26) cat = ri.activityInfo.applicationInfo.category;
                    o.put("cat", cat);
                    arr.put(o);
                }
                return arr.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        // Accesos elegidos por el usuario para la fila principal (JSON de paquetes; "" = sin configurar)
        @JavascriptInterface
        public String getPins() {
            return getSharedPreferences("launcher", MODE_PRIVATE).getString("pins", "");
        }

        @JavascriptInterface
        public void setPins(String json) {
            SharedPreferences.Editor e = getSharedPreferences("launcher", MODE_PRIVATE).edit();
            e.putString("pins", json == null ? "" : json);
            e.apply();
        }

        @JavascriptInterface
        public void launch(String pkg) {
            try {
                PackageManager pm = getPackageManager();
                Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (i == null) i = pm.getLaunchIntentForPackage(pkg);
                if (i != null) {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                }
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void openSettings() {
            try {
                Intent i = new Intent(android.provider.Settings.ACTION_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception ignored) {
            }
        }
    }

    // Devuelve el icono de la app como PNG de 256 px, usando la versión de mayor resolución.
    private byte[] iconBytes(String pkg) {
        if (pkg == null) return null;
        byte[] cached = iconCache.get(pkg);
        if (cached != null) return cached;
        try {
            Drawable d = loadBestIcon(getPackageManager(), pkg);
            if (d == null) return null;
            int size = 256;
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            d.setBounds(0, 0, size, size);
            d.draw(c);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            bmp.recycle();
            byte[] b = out.toByteArray();
            iconCache.put(pkg, b);
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private Drawable loadBestIcon(PackageManager pm, String pkg) {
        try {
            int res = pm.getApplicationInfo(pkg, 0).icon;

            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i != null && i.getComponent() != null) {
                ActivityInfo info = pm.getActivityInfo(i.getComponent(), 0);
                if (info.icon != 0) res = info.icon;
            }

            if (res != 0) {
                Resources r = pm.getResourcesForApplication(pkg);
                Drawable d = r.getDrawableForDensity(res, DisplayMetrics.DENSITY_XXXHIGH, null);
                if (d != null) return d;
            }
        } catch (Exception ignored) {
        }
        try {
            return pm.getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }
}
