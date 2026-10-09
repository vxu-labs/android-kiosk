package il.co.kiosk;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.content.res.ColorStateList;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.SafeBrowsingResponse;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayInputStream;

public final class MainActivity extends Activity {
    private int INK, TEAL, MUTED, BG, SURFACE, INPUT, BORDER, DANGER;
    private boolean dark;
    private static final long ADMIN_TIMEOUT = 180000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Config config;
    private String displayedLanguage;
    private KioskPolicy policy;
    private FrameLayout root;
    private TextView adminHandle;
    private WebView web;
    private LinearLayout errorPanel;
    private AlertDialog dialog;
    private boolean admin, paused, redrawOnResume, attemptingLock;
    private long adminUntil;
    private String lockError = "";
    private final Runnable adminExpiry = () -> { if (admin) closeAdmin(); };
    private final Runnable webRetry = () -> { if (web != null && !paused && !admin) web.loadUrl(config.text("url", "")); };

    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Language.wrap(base));
    }

    @Override public void onCreate(Bundle state) {
        config = new Config(this);
        dark = !"light".equals(config.text("appearance", "dark"));
        setTheme(dark ? R.style.AppTheme : R.style.AppThemeLight);
        INK = Color.parseColor(dark ? "#F5F5F7" : "#1D1D1F");
        TEAL = Color.parseColor(dark ? "#0A84FF" : "#0071E3");
        MUTED = Color.parseColor(dark ? "#A1A1AA" : "#63636B");
        BG = Color.parseColor(dark ? "#09090B" : "#F5F5F7");
        SURFACE = Color.parseColor(dark ? "#1C1C1E" : "#FFFFFF");
        INPUT = Color.parseColor(dark ? "#2C2C2E" : "#F2F2F7");
        BORDER = Color.parseColor(dark ? "#636366" : "#A1A1AA");
        DANGER = Color.parseColor(dark ? "#FF6961" : "#B42318");
        super.onCreate(state);
        displayedLanguage = Language.code(this);
        policy = new KioskPolicy(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SECURE);
        immersive();
        if (!config.hasPin()) showSetup(); else showKiosk();
    }
    @Override protected void onResume() {
        super.onResume(); paused = false;
        if (redrawOnResume) { redrawOnResume = false; if (config.hasPin()) showKiosk(); else showSetup(); }
        if (web != null) web.onResume();
        enforceLock(); immersive();
    }
    @Override protected void onPause() {
        super.onPause(); paused = true;
        admin = false; adminUntil = 0;
        handler.removeCallbacks(adminExpiry); handler.removeCallbacks(webRetry);
        if (dialog != null) { dialog.dismiss(); dialog = null; }
        if (web != null) web.onPause();
        redrawOnResume = true;
    }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); destroyWeb(); super.onDestroy(); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); /* No intent can grant an admin session. */ }
    @Override public void onWindowFocusChanged(boolean focus) { super.onWindowFocusChanged(focus); if (focus) immersive(); }
    @Override public void onUserInteraction() {
        super.onUserInteraction();
        if (admin && SystemClock.elapsedRealtime() < adminUntil) extendAdmin();
    }
    @Override public void onBackPressed() {
        if (admin) closeAdmin();
        else if (web != null && web.canGoBack()) web.goBack();
    }
    @Override public void onActionModeStarted(ActionMode mode) { super.onActionModeStarted(mode); mode.finish(); }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    private void enforceLock() {
        if (!config.enabled() || !config.hasPin() || !policy.isOwner() || attemptingLock) return;
        attemptingLock = true;
        try { policy.lock(this); lockError = ""; }
        catch (RuntimeException e) { lockError = getString(R.string.lock_failure) + e.getMessage(); toast(lockError); }
        finally { attemptingLock = false; }
    }
    private boolean authorized() {
        if (!admin || SystemClock.elapsedRealtime() >= adminUntil) { closeAdmin(); return false; }
        return true;
    }
    private void extendAdmin() {
        adminUntil = SystemClock.elapsedRealtime() + ADMIN_TIMEOUT;
        handler.removeCallbacks(adminExpiry); handler.postDelayed(adminExpiry, ADMIN_TIMEOUT);
    }
    private void closeAdmin() {
        admin = false; adminUntil = 0; handler.removeCallbacks(adminExpiry);
        if (dialog != null) { dialog.dismiss(); dialog = null; }
        if (!displayedLanguage.equals(Language.code(this)) || dark != !"light".equals(config.text("appearance", "dark"))) { recreate(); return; }
        showKiosk(); enforceLock();
    }
    private void destroyWeb() {
        handler.removeCallbacks(webRetry);
        if (web != null) { web.stopLoading(); web.setWebChromeClient(null); web.setWebViewClient(new WebViewClient());
            if (web.getParent() instanceof ViewGroup) ((ViewGroup)web.getParent()).removeView(web);
            web.destroy(); web = null;
        }
    }
    private void freshRoot() {
        View focus = getCurrentFocus();
        if (focus != null) getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(focus.getWindowToken(), 0);
        destroyWeb(); errorPanel = null;
        root = new FrameLayout(this); root.setBackgroundColor(BG); root.setLayoutDirection(getResources().getConfiguration().getLayoutDirection());
        setContentView(root);
    }
    private LinearLayout form(String eyebrow, String title, String subtitle) {
        freshRoot();
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        FrameLayout wrap = new FrameLayout(this);
        LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL); column.setPadding(dp(30), dp(38), dp(30), dp(40));
        FrameLayout.LayoutParams size = new FrameLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels, dp(740)), -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        wrap.addView(column, size); scroll.addView(wrap); root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        TextView overline = label(eyebrow, 12, MUTED); overline.setLetterSpacing(0.12f); column.addView(overline); column.addView(label(title, 34, INK));
        TextView description = label(subtitle, 16, MUTED); description.setPadding(0, dp(8), 0, dp(22)); column.addView(description);
        return column;
    }
    private void showSetup() {
        LinearLayout column = form(getString(R.string.setup_eyebrow), getString(R.string.setup_title), getString(R.string.setup_intro));
        column = section(column, getString(R.string.secure_setup));
        languageChoices(column, true);
        EditText pin = field(column, getString(R.string.new_pin), "", true);
        EditText again = field(column, getString(R.string.confirm_pin), "", true);
        TextView error = label(getString(R.string.remember_pin), 14, MUTED); column.addView(error);
        column.addView(button(getString(R.string.save_pin), () -> {
            String value = pin.getText().toString();
            if (!PinCrypto.valid(value)) { error.setText(getString(R.string.pin_digits)); return; }
            if (!value.equals(again.getText().toString())) { error.setText(getString(R.string.pin_mismatch)); return; }
            try { config.savePin(value); admin = true; extendAdmin(); showSettings(); }
            catch (RuntimeException e) { error.setText(getString(R.string.pin_save_error)); }
        }));
    }
    private void showKiosk() {
        if (!config.hasPin()) { showSetup(); return; }
        freshRoot();
        if ("web".equals(config.text("mode", "home")) && UrlPolicy.valid(config.text("url", ""))) showWebsite(); else showHome();
        addAdminHandle();
        // Lock diagnostics belong in PIN-protected administration, never over public content.
    }
    private void showHome() {
        LinearLayout center = new LinearLayout(this); center.setOrientation(LinearLayout.VERTICAL); center.setGravity(Gravity.CENTER); center.setPadding(dp(40), dp(64), dp(40), dp(64));
        root.addView(center, new FrameLayout.LayoutParams(-1, -1));
        TextView badge = label(getString(R.string.welcome), 16, TEAL); badge.setGravity(Gravity.CENTER); center.addView(badge);
        TextView title = label(config.text("title", getString(R.string.default_title)), 42, INK); title.setGravity(Gravity.CENTER); title.setPadding(0, dp(16), 0, dp(16)); center.addView(title);
        TextView message = label(config.text("message", getString(R.string.default_message)), 22, MUTED); message.setGravity(Gravity.CENTER); center.addView(message);
        if (config.flag("clock", true)) {
            TextClock clock = new TextClock(this); clock.setFormat24Hour("HH:mm"); clock.setFormat12Hour("HH:mm"); clock.setTextSize(72); clock.setTextColor(INK); clock.setGravity(Gravity.CENTER); clock.setPadding(0, dp(44), 0, 0); center.addView(clock);
            TextClock date = new TextClock(this); date.setFormat24Hour("EEEE, d MMMM yyyy"); date.setFormat12Hour("EEEE, d MMMM yyyy"); date.setTextSize(17); date.setTextColor(MUTED); date.setGravity(Gravity.CENTER); center.addView(date);
        }
    }
    private void addAdminHandle() {
        TextView handle = label("⋮", 30, INK); adminHandle = handle; handle.setGravity(Gravity.CENTER); handle.setContentDescription(getString(R.string.admin_handle)); handle.setBackground(ripple(SURFACE, 26)); handle.setElevation(dp(4));
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.TOP | Gravity.RIGHT); position.setMargins(dp(16), dp(16), dp(16), 0); root.addView(handle, position);
        handle.setFocusable(true); handle.setOnClickListener(v -> showQuickMenu());
        handle.setOnLongClickListener(v -> { showQuickMenu(); return true; });
    }
    private void showQuickMenu() {
        if (paused || admin || (dialog != null && dialog.isShowing())) return;
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(24), dp(8), dp(24), dp(24));
        box.addView(button(getString(R.string.refresh_home), () -> {
            dialog.dismiss(); dialog = null;
            // Rebuild from saved configuration: reset website navigation and load its home URL.
            showKiosk(); enforceLock();
        }));
        box.addView(secondaryButton(getString(R.string.kiosk_settings), () -> {
            dialog.dismiss(); dialog = null;
            requestPin(() -> { admin = true; extendAdmin(); showSettings(); });
        }));
        dialog = new AlertDialog.Builder(this).setTitle(getString(R.string.quick_actions)).setView(box).create();
        presentDialog();
    }
    private void requestPin(Runnable success) {
        if (paused || (dialog != null && dialog.isShowing())) return;
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(24), dp(8), dp(24), dp(8)); box.setLayoutDirection(getResources().getConfiguration().getLayoutDirection());
        EditText pin = field(box, getString(R.string.admin_pin), "", true);
        TextView error = label(getString(R.string.pin_intro), 14, MUTED); box.addView(error);
        dialog = new AlertDialog.Builder(this).setTitle(getString(R.string.admin_login)).setView(box).setNegativeButton(getString(R.string.cancel), null).setPositiveButton(getString(R.string.sign_in), null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            long wait = config.waitMillis();
            if (wait > 0) { error.setText(getString(R.string.attempts_wait) + ((wait + 999) / 1000) + getString(R.string.seconds)); return; }
            if (config.authenticate(pin.getText().toString())) { dialog.dismiss(); dialog = null; success.run(); }
            else { pin.setText(""); long remaining = config.waitMillis(); error.setText(remaining > 0 ? getString(R.string.access_delayed) + ((remaining + 999) / 1000) + getString(R.string.seconds) : getString(R.string.wrong_pin)); }
        }));
        presentDialog();
    }
    private void showSettings() {
        if (!authorized()) return;
        LinearLayout column = form(getString(R.string.settings_eyebrow), getString(R.string.settings_title), getString(R.string.settings_intro));
        String status = policy.isOwner() ? (policy.isLocked() ? getString(R.string.status_locked) : getString(R.string.status_owner_ready)) : getString(R.string.status_no_owner);
        TextView statusView = label(status, 15, policy.isLocked() ? TEAL : MUTED); column.addView(statusView);
        if (!policy.isLocked()) column.addView(label(getString(R.string.status_explanation), 14, MUTED));
        if (!lockError.isEmpty()) column.addView(label(lockError, 14, DANGER));
        LinearLayout preferences = section(column, getString(R.string.preferences));
        RadioGroup languages = languageChoices(preferences, false);
        RadioGroup appearances = appearanceChoices(preferences);
        LinearLayout content = section(column, getString(R.string.mode_question));
        RadioGroup modes = new RadioGroup(this); modes.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton home = new RadioButton(this); home.setId(View.generateViewId()); home.setText(getString(R.string.mode_home));
        RadioButton website = new RadioButton(this); website.setId(View.generateViewId()); website.setText(getString(R.string.mode_web));
        modes.addView(home); modes.addView(website); modes.check("web".equals(config.text("mode", "home")) ? website.getId() : home.getId()); styleChoices(modes); content.addView(modes);
        EditText url = field(content, getString(R.string.website_url), config.text("url", ""), false); url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); url.setTextDirection(View.TEXT_DIRECTION_LTR);
        CheckBox sameHost = check(content, getString(R.string.same_host), config.flag("same_host", true));
        content.addView(label(getString(R.string.web_limits), 13, MUTED));
        EditText title = field(content, getString(R.string.home_title), config.text("title", getString(R.string.default_title)), false);
        EditText message = field(content, getString(R.string.home_message), config.text("message", getString(R.string.default_message)), false);
        CheckBox clock = check(content, getString(R.string.show_clock), config.flag("clock", true));
        TextView error = label("", 14, DANGER); column.addView(error);
        Runnable save = () -> {
            if (!authorized()) return;
            String entered = url.getText().toString().trim();
            boolean useWeb = modes.getCheckedRadioButtonId() == website.getId();
            if (useWeb && !UrlPolicy.valid(entered)) { error.setText(getString(R.string.invalid_url)); return; }
            boolean saved = config.prefs.edit().putString("mode", useWeb ? "web" : "home").putString("url", entered)
                .putString("language", (String) languages.findViewById(languages.getCheckedRadioButtonId()).getTag())
                .putString("appearance", (String) appearances.findViewById(appearances.getCheckedRadioButtonId()).getTag())
                .putString("title", title.getText().toString().trim()).putString("message", message.getText().toString().trim())
                .putBoolean("same_host", sameHost.isChecked()).putBoolean("clock", clock.isChecked()).commit();
            if (!saved) { error.setText(getString(R.string.save_failed)); return; }
            if (!policy.isOwner()) { closeAdmin(); return; }
            dialog = new AlertDialog.Builder(this).setTitle(getString(R.string.enable_title))
                .setMessage(getString(R.string.enable_message))
                .setNegativeButton(getString(R.string.cancel), null).setPositiveButton(getString(R.string.enable), (d,w) -> {
                    if (!authorized()) return;
                    if (!config.prefs.edit().putBoolean("enabled", true).commit()) { toast(getString(R.string.cannot_save)); return; }
                    enforceLock(); closeAdmin();
                }).create(); presentDialog();
        };
        column.addView(button(policy.isOwner() ? getString(R.string.save_enable) : getString(R.string.save_preview), save));
        column.addView(secondaryButton(getString(R.string.return_kiosk), this::closeAdmin));
        LinearLayout maintenance = section(column, getString(R.string.admin_only));
        maintenance.addView(secondaryButton(getString(R.string.change_pin), () -> { if (authorized()) changePin(); }));
        maintenance.addView(secondaryButton(getString(R.string.setup_help), () -> {
            if (!authorized()) return;
            dialog = new AlertDialog.Builder(this).setTitle(getString(R.string.full_lock_title))
                .setMessage(getString(R.string.full_lock_help))
                .setPositiveButton(getString(R.string.understood), null).create(); presentDialog();
        }));
        maintenance.addView(secondaryButton(getString(R.string.android_settings), () -> {
            if (!authorized()) return;
            requestPin(() -> openAndroid(false));
        }));
        maintenance.addView(secondaryButton(getString(R.string.exit_android), () -> {
            if (!authorized()) return;
            requestPin(() -> openAndroid(true));
        }));
        maintenance.addView(secondaryButton(getString(R.string.clear_site), () -> {
            if (!authorized()) return;
            dialog = new AlertDialog.Builder(this).setTitle(getString(R.string.clear_site_title)).setMessage(getString(R.string.clear_site_message))
                .setNegativeButton(getString(R.string.cancel), null).setPositiveButton(getString(R.string.clear), (d,w) -> {
                    if (!authorized()) return;
                    CookieManager.getInstance().removeAllCookies(null); CookieManager.getInstance().flush();
                    android.webkit.WebStorage.getInstance().deleteAllData();
                    WebView cleaner = new WebView(this); cleaner.clearCache(true); cleaner.clearHistory(); cleaner.destroy(); toast(getString(R.string.site_cleared));
                }).create(); presentDialog();
        }));
        if (policy.isOwner()) maintenance.addView(secondaryButton(getString(R.string.remove_management), () -> {
            if (!authorized()) return;
            requestPin(() -> {
                admin = true; extendAdmin();
                dialog = new AlertDialog.Builder(this).setTitle(getString(R.string.remove_title))
                    .setMessage(getString(R.string.remove_message))
                    .setNegativeButton(getString(R.string.cancel), null).setPositiveButton(getString(R.string.remove), (d,w) -> {
                        if (!authorized()) return;
                        try {
                            policy.release(this, true); policy.dpm.clearDeviceOwnerApp(getPackageName());
                            config.prefs.edit().putBoolean("enabled", false).commit(); closeAdmin(); toast(getString(R.string.management_removed));
                        } catch (RuntimeException e) { toast(getString(R.string.remove_failed) + e.getMessage()); enforceLock(); }
                    }).create(); presentDialog();
            });
        }));
        column.addView(label(getString(R.string.about), 13, MUTED));
    }
    private void openAndroid(boolean home) {
        try {
            policy.release(this, home);
            Intent intent = home ? new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME) : new Intent(Settings.ACTION_SETTINGS);
            admin = false; adminUntil = 0;
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException e) { toast(getString(R.string.android_open_failed) + e.getMessage()); closeAdmin(); }
    }
    private void changePin() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(24), dp(8), dp(24), dp(8)); box.setLayoutDirection(getResources().getConfiguration().getLayoutDirection());
        EditText current = field(box, getString(R.string.current_pin), "", true), next = field(box, getString(R.string.new_pin_hint), "", true), confirm = field(box, getString(R.string.new_pin_confirm), "", true);
        TextView error = label("", 14, DANGER); box.addView(error);
        dialog = new AlertDialog.Builder(this).setTitle(getString(R.string.change_pin)).setView(box).setNegativeButton(getString(R.string.cancel), null).setPositiveButton(getString(R.string.save), null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!authorized()) return;
            if (!PinCrypto.valid(next.getText().toString()) || !next.getText().toString().equals(confirm.getText().toString())) { error.setText(getString(R.string.new_pin_invalid)); return; }
            if (!config.authenticate(current.getText().toString())) { error.setText(getString(R.string.current_pin_invalid)); return; }
            try { config.savePin(next.getText().toString()); dialog.dismiss(); dialog = null; toast(getString(R.string.pin_updated)); }
            catch (RuntimeException e) { error.setText(getString(R.string.pin_update_failed)); }
        })); presentDialog();
    }
    @SuppressLint("SetJavaScriptEnabled") private void showWebsite() {
        web = new WebView(this); web.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(false); s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false); s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); s.setSafeBrowsingEnabled(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false); s.setSupportMultipleWindows(true);
        s.setMediaPlaybackRequiresUserGesture(true); s.setGeolocationEnabled(false);
        s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false); s.setLoadWithOverviewMode(true); s.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        WebView.setWebContentsDebuggingEnabled(false);
        web.setOnLongClickListener(v -> true); web.setLongClickable(false);
        web.setDownloadListener((url, agent, disposition, mime, length) -> toast(getString(R.string.downloads_blocked)));
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        root.addView(progress, new FrameLayout.LayoutParams(-1, dp(4), Gravity.TOP));
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) { progress.setProgress(value); progress.setVisibility(value == 100 ? View.GONE : View.VISIBLE); }
            @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) { callback.invoke(origin, false, false); }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<android.net.Uri[]> callback, FileChooserParams params) { callback.onReceiveValue(null); toast(getString(R.string.files_blocked)); return true; }
            @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean gesture, android.os.Message message) { toast(getString(R.string.popups_blocked)); return false; }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String target = request.getUrl().toString();
                boolean allow = request.isForMainFrame() ? UrlPolicy.allowed(config.text("url", ""), target, config.flag("same_host", true)) : UrlPolicy.valid(target);
                if (!allow && request.isForMainFrame()) toast(getString(R.string.link_blocked));
                return !allow;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                if (request.isForMainFrame() && !UrlPolicy.allowed(config.text("url", ""), request.getUrl().toString(), config.flag("same_host", true)))
                    return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", java.util.Collections.emptyMap(), new ByteArrayInputStream(getString(R.string.link_blocked).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                if (!"https".equalsIgnoreCase(scheme)) return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
                return null;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) { handler.removeCallbacks(webRetry); if (errorPanel != null) errorPanel.setVisibility(View.GONE); }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) { if (request.isForMainFrame()) showWebError(getString(R.string.network_error), true); }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) { if (request.isForMainFrame()) showWebError(getString(R.string.http_error) + response.getStatusCode() + ".", false); }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler ssl, SslError error) { ssl.cancel(); showWebError(getString(R.string.ssl_error), false); }
            @Override public void onSafeBrowsingHit(WebView view, WebResourceRequest request, int threatType, SafeBrowsingResponse response) { response.backToSafety(true); showWebError(getString(R.string.unsafe_site), false); }
            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) { handler.post(() -> { if (!isFinishing() && !paused) showKiosk(); }); return true; }
        });
        web.loadUrl(config.text("url", ""));
    }
    private void showWebError(String message, boolean retry) {
        if (web == null) return;
        if (errorPanel != null) root.removeView(errorPanel);
        errorPanel = new LinearLayout(this); errorPanel.setOrientation(LinearLayout.VERTICAL); errorPanel.setGravity(Gravity.CENTER); errorPanel.setPadding(dp(32), dp(70), dp(32), dp(32)); errorPanel.setBackgroundColor(BG);
        TextView title = label(getString(R.string.site_unavailable), 30, INK); title.setGravity(Gravity.CENTER); errorPanel.addView(title);
        TextView info = label(message + (retry ? getString(R.string.retry_delay) : ""), 17, MUTED); info.setGravity(Gravity.CENTER); errorPanel.addView(info);
        errorPanel.addView(button(getString(R.string.retry), () -> { if (web != null) web.loadUrl(config.text("url", "")); }));
        // Keep the PIN entry handle above every error state.
        root.addView(errorPanel, Math.max(0, root.getChildCount() - 1), new FrameLayout.LayoutParams(-1, -1));
        if (adminHandle != null) adminHandle.bringToFront();
        handler.removeCallbacks(webRetry); if (retry) handler.postDelayed(webRetry, 15000);
    }
    private TextView label(String text, float size, int color) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); view.setPadding(0, dp(6), 0, dp(6));
        view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG); if (size >= 22) view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); return view;
    }
    private LinearLayout section(LinearLayout parent, String title) {
        TextView heading = label(title, 18, INK); heading.setPadding(dp(4), dp(24), dp(4), dp(12)); parent.addView(heading);
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(22), dp(18), dp(22), dp(22)); card.setBackground(shape(SURFACE, 24));
        parent.addView(card, new LinearLayout.LayoutParams(-1, -2)); return card;
    }
    private void styleChoices(RadioGroup group) {
        group.setPadding(0, dp(4), 0, dp(8));
        // Segments wrap their text on narrow screens and remain comfortable touch targets.
        for (int i = 0; i < group.getChildCount(); i++) {
            RadioButton option = (RadioButton)group.getChildAt(i);
            option.setButtonDrawable((android.graphics.drawable.Drawable)null); option.setGravity(Gravity.CENTER); option.setTextSize(15); option.setMinHeight(dp(52)); option.setPadding(dp(8), dp(12), dp(8), dp(12));
            option.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_checked},{}}, new int[]{Color.WHITE,INK}));
            StateListDrawable background = new StateListDrawable(); background.addState(new int[]{android.R.attr.state_checked}, shape(Color.rgb(0, 113, 227), 12)); background.addState(new int[]{}, shape(INPUT, 12)); option.setBackground(background);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMarginEnd(dp(4)); p.setMarginStart(dp(4)); option.setLayoutParams(p);
        }
    }
    private RadioGroup appearanceChoices(LinearLayout parent) {
        parent.addView(label(getString(R.string.appearance), 18, INK));
        RadioGroup group = new RadioGroup(this); group.setOrientation(LinearLayout.HORIZONTAL);
        for (String value : new String[]{"dark", "light"}) {
            RadioButton option = new RadioButton(this); option.setId(View.generateViewId()); option.setTag(value);
            option.setText(getString("dark".equals(value) ? R.string.theme_dark : R.string.theme_light)); group.addView(option);
            if (value.equals(config.text("appearance", "dark"))) group.check(option.getId());
        }
        styleChoices(group); parent.addView(group); parent.addView(label(getString(R.string.appearance_hint), 13, MUTED)); return group;
    }
    private void presentDialog() {
        dialog.setOnDismissListener(d -> immersive()); dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            dialog.getWindow().setBackgroundDrawable(shape(SURFACE, 28));
            dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(460)), -2);
            dialog.getWindow().getDecorView().setLayoutDirection(getResources().getConfiguration().getLayoutDirection());
            dialog.getWindow().getDecorView().setSystemUiVisibility(getWindow().getDecorView().getSystemUiVisibility());
        }
        for (int id : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE}) {
            Button action = dialog.getButton(id); if (action != null) { action.setAllCaps(false); action.setTextColor(TEAL); action.setMinHeight(dp(48)); }
        }
    }
    private RadioGroup languageChoices(LinearLayout parent, boolean initialSetup) {
        parent.addView(label("Language / שפה", 18, INK));
        RadioGroup group = new RadioGroup(this); group.setOrientation(LinearLayout.HORIZONTAL);
        for (String code : new String[]{"he", "en"}) {
            RadioButton option = new RadioButton(this); option.setId(View.generateViewId());
            option.setTag(code); option.setText("he".equals(code) ? "עברית" : "English");
            option.setTextColor(INK); group.addView(option);
            if (code.equals(Language.code(this))) group.check(option.getId());
        }
        styleChoices(group); parent.addView(group);
        if (initialSetup) group.setOnCheckedChangeListener((g, id) -> {
            if (config.hasPin()) return;
            String code = (String)g.findViewById(id).getTag();
            if (config.prefs.edit().putString("language", code).commit()) recreate();
            else toast(getString(R.string.save_failed));
        });
        else parent.addView(label(getString(R.string.language_hint), 13, MUTED));
        return group;
    }
    private EditText field(LinearLayout parent, String caption, String value, boolean pin) {
        TextView captionView = label(caption, 14, MUTED); parent.addView(captionView);
        EditText field = new EditText(this); field.setId(View.generateViewId()); captionView.setLabelFor(field.getId()); field.setSingleLine(true); field.setTextSize(18); field.setTextColor(INK); field.setHintTextColor(MUTED); field.setPadding(dp(16), dp(16), dp(16), dp(16)); field.setMinHeight(dp(58));
        GradientDrawable normal = shape(INPUT, 14); normal.setStroke(dp(1), BORDER);
        GradientDrawable focused = shape(INPUT, 14); focused.setStroke(dp(2), TEAL);
        StateListDrawable states = new StateListDrawable(); states.addState(new int[]{android.R.attr.state_focused}, focused); states.addState(new int[]{}, normal); field.setBackground(states);
        field.setInputType(pin ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT);
        field.setHint(pin ? getString(R.string.pin_placeholder) : caption);
        if (pin) { field.setTextDirection(View.TEXT_DIRECTION_LTR); field.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); field.setLetterSpacing(0.12f); }
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(pin ? 12 : 4096)}); field.setText(value);
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS); field.setSaveEnabled(false);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(10); parent.addView(field, p); return field;
    }
    private CheckBox check(LinearLayout parent, String text, boolean checked) { CheckBox c = new CheckBox(this); c.setText(text); c.setTextColor(INK); c.setTextSize(15); c.setChecked(checked); parent.addView(c); return c; }
    private Button button(String text, Runnable action) {
        Button b = new Button(this); b.setText(text); b.setTextSize(16); b.setAllCaps(false); b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); b.setTextColor(Color.WHITE); b.setBackground(ripple(Color.rgb(0, 113, 227), 14)); b.setStateListAnimator(null); b.setPadding(dp(16), dp(14), dp(16), dp(14)); b.setMinHeight(dp(54));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(12); p.bottomMargin = dp(4); b.setLayoutParams(p); b.setOnClickListener(v -> action.run()); return b;
    }
    private Button secondaryButton(String text, Runnable action) { Button b = button(text, action); b.setTextColor(INK); b.setBackground(ripple(INPUT, 14)); return b; }
    private RippleDrawable ripple(int color, int radius) { return new RippleDrawable(ColorStateList.valueOf(dark ? 0x33FFFFFF : 0x22000000), shape(color, radius), shape(Color.WHITE, radius)); }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
