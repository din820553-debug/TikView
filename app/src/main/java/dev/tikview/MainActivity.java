package dev.tikview;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
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
        toolbar.addView(button("Главная", () -> load(HOME)), new LinearLayout.LayoutParams(0, (int) (52 * getResources().getDisplayMetrics().density), 1));
        toolbar.addView(button("Ссылка", this::linkDialog), new LinearLayout.LayoutParams(0, (int) (52 * getResources().getDisplayMetrics().density), 1));
        toolbar.addView(button("Настройки", this::settings), new LinearLayout.LayoutParams(0, (int) (52 * getResources().getDisplayMetrics().density), 1));
        content.addView(toolbar);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        content.addView(progress, new LinearLayout.LayoutParams(-1, 4));
        status = new TextView(this);
        status.setTextColor(Color.LTGRAY);
        status.setTextSize(12);
        status.setPadding(16, 8, 16, 8);
        status.setOnClickListener(v -> settings());
        content.addView(status);
        web = new WebView(this);
        content.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri uri = request.getUrl();
                if (isTikTok(uri)) return false;
                if ("https".equalsIgnoreCase(uri.getScheme())) {
                    new AlertDialog.Builder(MainActivity.this).setTitle("Внешняя ссылка")
                        .setMessage("Открыть в браузере?\n" + uri.getHost())
                        .setPositiveButton("Открыть", (d, w) -> {
                            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                            catch (android.content.ActivityNotFoundException e) { message("Браузер не установлен"); }
                        }).setNegativeButton("Отмена", null).show();
                } else {
                    message("Переход в другое приложение заблокирован. Используйте веб-версию TikTok.");
                }
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                status.setText("Загрузка… · " + connectionLabel());
            }
            @Override public void onPageFinished(WebView view, String url) {
                status.setText("TikView · " + connectionLabel());
                CookieManager.getInstance().flush();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError error) {
                if (req.isForMainFrame()) {
                    progress.setVisibility(View.INVISIBLE);
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
            status.setText("Выберите способ подключения");
            new AlertDialog.Builder(this).setTitle("TikView")
                .setMessage("Отдельный клиент веб-версии TikTok. Доступность зависит от сети и ограничений TikTok. Если видео не загружаются, используйте свой прокси или системный VPN. Встроенного VPN нет.\n\nНужен актуальный Android System WebView. Вход в аккаунт и некоторые функции веб-версии могут быть ограничены самим TikTok.")
                .setPositiveButton("Продолжить", (d, w) -> {
                    prefs.edit().putBoolean("intro", true).apply();
                    configureConnection(prefs.getString("proxy", ""));
                }).setCancelable(false).show();
        } else configureConnection(prefs.getString("proxy", ""));
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
        if (fullscreen != null) closeFullscreen();
        else if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("url", pendingUrl);
        super.onSaveInstanceState(out);
    }
    @Override protected void onPause() {
        web.onPause();
        CookieManager.getInstance().flush();
        super.onPause();
    }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onDestroy() {
        closeFullscreen();
        ((ViewGroup) web.getParent()).removeView(web);
        web.destroy();
        super.onDestroy();
    }
}
