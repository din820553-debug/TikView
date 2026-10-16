package dev.tikview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import org.json.JSONTokener;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.webkit.ProxyConfig;
import androidx.webkit.ProxyController;
import androidx.webkit.WebViewFeature;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A separate web client. No modification of TikTok's APK or private API. */
public final class MainActivity extends Activity {
    private static final String HOME = "https://www.tiktok.com/";
    private WebView web;
    private LinearLayout content;
    private FrameLayout root;
    private TextView status;
    private ProgressBar progress;
    private SharedPreferences prefs;
    private View fullscreen;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private String pendingUrl = HOME;
    private boolean ready;
    private FrameLayout commentsOverlay;
    private WebView commentsWeb;
    private boolean pageFailed;
    private static final int INK = Color.rgb(16, 16, 20);

    @Override @SuppressLint("SetJavaScriptEnabled")
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        root = new FrameLayout(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(Color.rgb(16, 16, 20));
        root.addView(content);
        setContentView(root);
        // Keep controls out of system bars, including Android 15 edge-to-edge.
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER);
        toolbar.addView(nav("Подписки", () -> load("https://www.tiktok.com/following")), new LinearLayout.LayoutParams(0, dp(46), 1));
        toolbar.addView(nav("Для вас", () -> load(HOME)), new LinearLayout.LayoutParams(0, dp(46), 1));
        toolbar.addView(nav("↗ Ссылка", this::linkDialog), new LinearLayout.LayoutParams(0, dp(46), 1));
        content.addView(toolbar);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        content.addView(progress, new LinearLayout.LayoutParams(-1, dp(2)));
        status = new TextView(this);
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(12);
        status.setPadding(dp(12), dp(6), dp(12), dp(6));
        status.setVisibility(View.GONE);
        status.setOnClickListener(v -> settings());
        content.addView(status);
        web = new WebView(this);
        web.setBackgroundColor(INK);
        content.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout navigation = new LinearLayout(this);
        navigation.setPadding(0, dp(4), 0, dp(4));
        navigation.addView(nav("⌂\nГлавная", () -> load(HOME)), new LinearLayout.LayoutParams(0, dp(54), 1));
        navigation.addView(nav("⌕\nПоиск", () -> load("https://www.tiktok.com/search")), new LinearLayout.LayoutParams(0, dp(54), 1));
        navigation.addView(nav("💬\nКомментарии", this::openComments), new LinearLayout.LayoutParams(0, dp(54), 1));
        navigation.addView(nav("☰\nМеню", this::settings), new LinearLayout.LayoutParams(0, dp(54), 1));
        content.addView(navigation);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSafeBrowsingEnabled(true);
        // Use the browser layout instead of advertising an embedded-app WebView.
        s.setUserAgentString(s.getUserAgentString().replace("; wv", "").replace("Version/4.0 ", ""));
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri uri = request.getUrl();
                if ("tikview".equals(uri.getScheme()) && "comments".equals(uri.getHost())) {
                    openComments();
                    return true;
                }
                if (isTikTok(uri)) return false;
                if ("https".equalsIgnoreCase(uri.getScheme())) {
                    new AlertDialog.Builder(MainActivity.this).setTitle("Внешняя ссылка")
                        .setMessage("Открыть в браузере?\n" + uri.getHost())
                        .setPositiveButton("Открыть", (d, w) -> {
                            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                            catch (android.content.ActivityNotFoundException e) { message("Браузер не установлен"); }
                        }).setNegativeButton("Отмена", null).show();
                } else {
                    if ("intent".equalsIgnoreCase(uri.getScheme()) || "snssdk1233".equalsIgnoreCase(uri.getScheme())) {
                        new AlertDialog.Builder(MainActivity.this).setMessage("TikTok предлагает открыть приложение. Комментарии можно открыть здесь кнопкой ниже.")
                            .setPositiveButton("Комментарии", (d, w) -> openComments())
                            .setNegativeButton("Закрыть", null).show();
                    } else message("Эта ссылка не поддерживается");
                }
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                pageFailed = false;
                status.setVisibility(View.GONE);
                if (isTikTok(Uri.parse(url))) pendingUrl = url;
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (!pageFailed) status.setVisibility(View.GONE);
                if (isTikTok(Uri.parse(url))) view.evaluateJavascript(asset("feed-controls.js"), null);
                CookieManager.getInstance().flush();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError error) {
                if (req.isForMainFrame()) {
                    pageFailed = true;
                    progress.setVisibility(View.INVISIBLE);
                    status.setVisibility(View.VISIBLE);
                    status.setText("Не удалось загрузить. Откройте настройки: прокси / VPN / повторить.");
                }
            }
            // Default SSL error handling cancels requests. Never bypass certificate errors.
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value == 100 ? View.INVISIBLE : View.VISIBLE);
            }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreen != null) { callback.onCustomViewHidden(); return; }
                fullscreen = view;
                fullscreenCallback = callback;
                content.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            @Override public void onHideCustomView() { closeFullscreen(); }
        });
        if (saved != null) pendingUrl = saved.getString("url", HOME);
        acceptShare(getIntent());
        if (!prefs.getBoolean("intro", false)) {
            status.setVisibility(View.VISIBLE);
            status.setText("Выберите способ подключения");
            new AlertDialog.Builder(this).setTitle("TikView")
                .setMessage("Отдельный клиент веб-версии TikTok. Доступность зависит от сети и ограничений TikTok. Если видео не загружаются, используйте свой прокси или системный VPN. Встроенного VPN нет.\n\nНужен актуальный Android System WebView. Вход в аккаунт и некоторые функции веб-версии могут быть ограничены самим TikTok.")
                .setPositiveButton("Продолжить", (d, w) -> {
                    prefs.edit().putBoolean("intro", true).apply();
                    configureConnection(prefs.getString("proxy", ""));
                }).setCancelable(false).show();
        } else configureConnection(prefs.getString("proxy", ""));
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView nav(String text, Runnable action) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(Color.WHITE);
        v.setTextSize(12);
        v.setTypeface(null, Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        v.setBackgroundResource(android.R.drawable.list_selector_background);
        v.setOnClickListener(clicked -> action.run());
        return v;
    }
    private String asset(String name) {
        try (java.io.InputStream input = getAssets().open(name)) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) { return "null"; }
    }
    private void openComments() {
        if (!ready || commentsOverlay != null) return;
        if (fullscreen != null) closeFullscreen();
        web.evaluateJavascript(asset("current-video.js"), result -> {
            if (isFinishing() || isDestroyed() || commentsOverlay != null) return;
            try {
                Object value = new JSONTokener(result).nextValue();
                if (value instanceof String && UrlPolicy.isTikTok((String) value)) {
                    showComments((String) value);
                    return;
                }
            } catch (org.json.JSONException ignored) { }
            message("Сначала откройте ролик. Если лента не передаёт ссылку, откройте видео кнопкой «Ссылка» или через «Поделиться → TikView», затем нажмите «Комментарии».");
        });
    }
    @SuppressLint("SetJavaScriptEnabled")
    private void showComments(String url) {
        web.evaluateJavascript("document.querySelectorAll('video').forEach(v=>v.pause())", null);
        web.onPause();
        commentsOverlay = new FrameLayout(this);
        commentsOverlay.setBackgroundColor(0x99000000);
        commentsOverlay.setOnClickListener(v -> closeComments());
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackgroundColor(Color.WHITE);
        sheet.setOnClickListener(v -> { });
        FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(-1, Math.round(getResources().getDisplayMetrics().heightPixels * 0.82f), Gravity.BOTTOM);
        commentsOverlay.addView(sheet, layout);
        LinearLayout header = new LinearLayout(this);
        header.setBackgroundColor(INK);
        TextView title = nav("Комментарии", () -> { });
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        header.addView(nav("Вся страница", () -> {
            if (commentsWeb != null) {
                commentsWeb.setTag(Boolean.FALSE);
                commentsWeb.reload();
            }
        }), new LinearLayout.LayoutParams(dp(108), dp(48)));
        header.addView(nav("✕", this::closeComments), new LinearLayout.LayoutParams(dp(48), dp(48)));
        sheet.addView(header);
        TextView hint = new TextView(this);
        hint.setText("Комментарии TikTok · для ответа может потребоваться вход");
        hint.setTextColor(Color.DKGRAY);
        hint.setTextSize(12);
        hint.setPadding(dp(12), dp(8), dp(12), dp(8));
        sheet.addView(hint);
        ProgressBar loading = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        sheet.addView(loading, new LinearLayout.LayoutParams(-1, dp(2)));
        commentsWeb = new WebView(this);
        commentsWeb.setTag(Boolean.TRUE);
        WebSettings options = commentsWeb.getSettings();
        options.setJavaScriptEnabled(true);
        options.setDomStorageEnabled(true);
        options.setAllowFileAccess(false);
        options.setAllowContentAccess(false);
        options.setSafeBrowsingEnabled(true);
        options.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        options.setMediaPlaybackRequiresUserGesture(true);
        options.setBuiltInZoomControls(true);
        options.setDisplayZoomControls(false);
        String ua = WebSettings.getDefaultUserAgent(this);
        Matcher chrome = Pattern.compile("Chrome/[0-9.]+").matcher(ua);
        String browserVersion = chrome.find() ? chrome.group() : "Chrome/131.0.0.0";
        options.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " + browserVersion + " Safari/537.36");
        CookieManager.getInstance().setAcceptThirdPartyCookies(commentsWeb, false);
        commentsWeb.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                if (isTikTok(request.getUrl())) return false;
                hint.setText("Для входа используйте способ, доступный на сайте TikTok");
                return true;
            }
            @Override public void onPageFinished(WebView view, String pageUrl) {
                if (isTikTok(Uri.parse(pageUrl)) && Boolean.TRUE.equals(view.getTag()))
                    view.evaluateJavascript(asset("comments-panel.js"), null);
                CookieManager.getInstance().flush();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) hint.setText("Ошибка подключения. Закройте панель и проверьте сеть / прокси.");
            }
        });
        commentsWeb.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                loading.setProgress(value);
                loading.setVisibility(value == 100 ? View.GONE : View.VISIBLE);
            }
        });
        sheet.addView(commentsWeb, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(commentsOverlay, new FrameLayout.LayoutParams(-1, -1));
        commentsWeb.loadUrl(url);
    }
    private void closeComments() {
        if (commentsOverlay == null) return;
        if (commentsWeb != null) {
            commentsWeb.stopLoading();
            ((ViewGroup) commentsWeb.getParent()).removeView(commentsWeb);
            commentsWeb.destroy();
            commentsWeb = null;
        }
        root.removeView(commentsOverlay);
        commentsOverlay = null;
        web.onResume();
    }

    private Button button(String text, Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private static boolean isTikTok(Uri uri) { return UrlPolicy.isTikTok(uri.toString()); }
    private void load(String url) {
        if (!isTikTok(Uri.parse(url))) { message("Нужна HTTPS-ссылка на tiktok.com"); return; }
        pendingUrl = url;
        if (ready) web.loadUrl(url);
    }
    private void linkDialog() {
        EditText field = new EditText(this);
        field.setHint("https://www.tiktok.com/@user/video/…");
        field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this).setTitle("Открыть TikTok-ссылку").setView(field)
            .setPositiveButton("Открыть", (d, w) -> load(field.getText().toString().trim()))
            .setNegativeButton("Отмена", null).show();
    }
    private String connectionLabel() {
        return prefs.getString("proxy", "").isEmpty() ? "системная сеть / VPN" : "прокси";
    }
    private void settings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(32, 8, 32, 8);
        TextView help = new TextView(this);
        help.setText("Прокси направляет трафик WebView. Используйте доверенный сервер вне региона блокировки.\nФормат: http://host:port, https://host:port или socks://host:port. Без логина и пароля. Пустое поле — системная сеть / VPN.\n");
        box.addView(help);
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        field.setText(prefs.getString("proxy", ""));
        field.setHint("http://proxy.example:8080");
        box.addView(field);
        box.addView(button("Открыть ссылку на ролик", this::linkDialog));
        box.addView(button("Комментарии к ролику", this::openComments));
        box.addView(button("Повторить загрузку", () -> load(pendingUrl)));
        box.addView(button("Очистить cookies и данные сайта", () -> new AlertDialog.Builder(this)
            .setMessage("Выйти из аккаунта и удалить данные TikTok на устройстве?")
            .setPositiveButton("Очистить", (d, w) -> {
                ready = false;
                web.stopLoading();
                web.loadUrl("about:blank");
                CookieManager.getInstance().removeAllCookies(removed -> {
                    CookieManager.getInstance().flush();
                    android.webkit.WebStorage.getInstance().deleteAllData();
                    web.clearCache(true);
                    web.clearHistory();
                    ready = true;
                    load(HOME);
                });
            }).setNegativeButton("Отмена", null).show()));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Подключение")
            .setView(box).setPositiveButton("Применить", null).setNegativeButton("Закрыть", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = field.getText().toString().trim();
            if (!value.isEmpty() && !validProxy(value)) {
                field.setError("Укажите схему, хост и порт 1–65535 без пароля и пути");
                return;
            }
            dialog.dismiss();
            configureConnection(value);
        }));
        dialog.show();
    }
    private boolean validProxy(String value) { return UrlPolicy.validProxy(value); }
    private void configureConnection(String proxy) {
        ready = false;
        web.stopLoading();
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            if (!proxy.isEmpty()) {
                status.setVisibility(View.VISIBLE);
                status.setText("Прокси не применён. Обновите Android System WebView или используйте VPN.");
                message("Эта версия WebView не поддерживает прокси. Обновите WebView или очистите поле прокси и используйте VPN.");
                return;
            }
            connected("");
            return;
        }
        try {
            ProxyController controller = ProxyController.getInstance();
            if (proxy.isEmpty()) controller.clearProxyOverride(this::runOnUiThread, () -> connected(""));
            else controller.setProxyOverride(new ProxyConfig.Builder().addProxyRule(proxy).build(),
                    this::runOnUiThread, () -> connected(proxy));
        } catch (IllegalArgumentException | IllegalStateException e) {
            status.setVisibility(View.VISIBLE);
            status.setText("Прокси не применён. Проверьте адрес в настройках.");
            message("Не удалось применить прокси: " + e.getMessage());
        }
    }
    private void connected(String proxy) {
        if (isFinishing() || isDestroyed()) return;
        prefs.edit().putString("proxy", proxy).apply();
        ready = true;
        load(pendingUrl);
    }
    private void message(String text) {
        new AlertDialog.Builder(this).setMessage(text).setPositiveButton("OK", null).show();
    }
    private void acceptShare(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                Matcher m = Pattern.compile("https://[^\\s<>\"]+").matcher(text);
                while (m.find()) {
                    if (isTikTok(Uri.parse(m.group()))) { load(m.group()); return; }
                }
            }
            message("В сообщении нет HTTPS-ссылки TikTok");
        }
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        acceptShare(intent);
    }
    private void closeFullscreen() {
        if (fullscreen == null) return;
        root.removeView(fullscreen);
        fullscreen = null;
        content.setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        fullscreenCallback.onCustomViewHidden();
        fullscreenCallback = null;
    }
    @Override public void onBackPressed() {
        if (commentsOverlay != null) closeComments();
        else if (fullscreen != null) closeFullscreen();
        else if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("url", pendingUrl);
        super.onSaveInstanceState(out);
    }
    @Override protected void onPause() {
        web.onPause();
        if (commentsWeb != null) commentsWeb.onPause();
        CookieManager.getInstance().flush();
        super.onPause();
    }
    @Override protected void onResume() { super.onResume(); if (web != null && commentsOverlay == null) web.onResume(); if (commentsWeb != null) commentsWeb.onResume(); }
    @Override protected void onDestroy() {
        closeComments();
        closeFullscreen();
        ((ViewGroup) web.getParent()).removeView(web);
        web.destroy();
        super.onDestroy();
    }
}
