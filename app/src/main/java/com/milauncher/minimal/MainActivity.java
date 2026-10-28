package com.milauncher.minimal;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(
            5894 | 1024 | 512 | 4096
        );

        web = new WebView(this);
        web.setBackgroundColor(0xFF090B0F);
        setContentView(web);

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);

        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new AndroidBridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
    }

    private class AndroidBridge {
        @JavascriptInterface
        public String getApps() {
            try {
                PackageManager pm = getPackageManager();
                List<ResolveInfo> found = new ArrayList<>();
                Set<String> seen = new HashSet<>();

                Intent tv = new Intent(Intent.ACTION_MAIN);
                tv.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);

                Intent normal = new Intent(Intent.ACTION_MAIN);
                normal.addCategory(Intent.CATEGORY_LAUNCHER);

                for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
                    if (seen.add(r.activityInfo.packageName)) found.add(r);
                }
                for (ResolveInfo r : pm.queryIntentActivities(normal, 0)) {
                    if (seen.add(r.activityInfo.packageName)) found.add(r);
                }

                final PackageManager fpm = pm;
                Collections.sort(found, new Comparator<ResolveInfo>() {
                    @Override
                    public int compare(ResolveInfo a, ResolveInfo b) {
                        return a.loadLabel(fpm).toString()
                            .compareToIgnoreCase(b.loadLabel(fpm).toString());
                    }
                });

                JSONArray array = new JSONArray();
                for (ResolveInfo r : found) {
                    String pkg = r.activityInfo.packageName;
                    if (pkg.equals(getPackageName())) continue;

                    JSONObject app = new JSONObject();
                    app.put("label", r.loadLabel(pm).toString());
                    app.put("pkg", pkg);
                    array.put(app);
                }
                return array.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public void launch(String pkg) {
            try {
                PackageManager pm = getPackageManager();
                Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (intent == null) {
                    intent = pm.getLaunchIntentForPackage(pkg);
                }
                if (intent != null) startActivity(intent);
            } catch (Exception ignored) {
            }
        }

        @JavascriptInterface
        public void openSettings() {
            try {
                startActivity(new Intent(
                    android.provider.Settings.ACTION_SETTINGS
                ));
            } catch (Exception ignored) {
                try {
                    startActivity(new Intent(
                        android.provider.Settings.ACTION_HOME_SETTINGS
                    ));
                } catch (Exception ignoredAgain) {
                }
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (web != null) {
                web.evaluateJavascript(
                    "window.onBack && window.onBack()", null
                );
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (web != null) {
            web.evaluateJavascript(
                "window.onHome && window.onHome()", null
            );
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.postDelayed(() -> web.evaluateJavascript(
                "window.refreshApps && window.refreshApps()", null
            ), 400);
    }
    }

    @Override
    public void onBackPressed() {
        if (web != null) {
            web.evaluateJavascript(
                "window.onBack && window.onBack()", null
            );
        }
    }
}
