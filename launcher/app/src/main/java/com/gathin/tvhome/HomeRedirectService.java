package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;

/**
 * Makes this launcher the home screen on Fire OS, which ignores the default-home setting.
 *
 * Watches window changes: Amazon Home → open this launcher after a short delay. If Settings
 * (often {@code MainSettingsActivity} inside {@code com.amazon.tv.launcher}) appears during that
 * delay, the redirect is cancelled so Settings can open. Leaving Settings onto Amazon Home
 * schedules a fresh redirect.
 *
 * Fallback if Amazon Home sticks: focusable "← Mi Home" overlay + Menu key.
 */
public class HomeRedirectService extends AccessibilityService {

    private static final String TAG = "HomeRedirect";
    private static final String PREFS = "home_redirect";
    private static final String KEY_PAUSED = "paused";
    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final String AMAZON_HOME = "HomeActivity";
    private static final String[] SETTINGS_APPS = {
            "com.amazon.tv.settings",
            "com.amazon.tv.quicksettings",
            "com.amazon.tv.devicecontrolsettings"
    };
    private static final long BOOT_WINDOW_MS = 3 * 60_000;
    /** Wait so a Settings window can cancel the redirect (Home often flashes first). */
    private static final long REDIRECT_DELAY_MS = 320;
    private static final long STUCK_HINT_MS = 900;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable redirect = this::openLauncher;
    private final Runnable stuckHint = this::showReturnButton;
    private boolean onAmazonHome;
    private View returnButton;

    @Override
    protected void onServiceConnected() {
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS && isRedirecting(this)) openLauncher();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkgCs = event.getPackageName(), clsCs = event.getClassName();
        if (pkgCs == null || clsCs == null) return;
        String pkg = pkgCs.toString();
        String cls = clsCs.toString();

        if (isSettingsWindow(pkg, cls)) {
            // Settings won the race — do not steal focus back to our launcher.
            handler.removeCallbacks(redirect);
            handler.removeCallbacks(stuckHint);
            setOnAmazonHome(false);
            Log.i(TAG, "settings: redirect cancelled");
            return;
        }

        // Our own UI — nothing to do.
        if (getPackageName().equals(pkg)) {
            handler.removeCallbacks(redirect);
            handler.removeCallbacks(stuckHint);
            setOnAmazonHome(false);
            return;
        }

        boolean amazonHome = AMAZON_LAUNCHER.equals(pkg) && cls.contains(AMAZON_HOME);
        if (!amazonHome) {
            handler.removeCallbacks(redirect);
            handler.removeCallbacks(stuckHint);
            setOnAmazonHome(false);
            return;
        }

        setOnAmazonHome(true);
        if (isPaused(this)) {
            handler.removeCallbacks(redirect);
            handler.postDelayed(stuckHint, 120);
            return;
        }

        Log.i(TAG, "amazon home: redirect in " + REDIRECT_DELAY_MS + "ms");
        handler.removeCallbacks(redirect);
        handler.postDelayed(redirect, REDIRECT_DELAY_MS);
        handler.removeCallbacks(stuckHint);
        handler.postDelayed(stuckHint, STUCK_HINT_MS);
    }

    private void setOnAmazonHome(boolean on) {
        if (onAmazonHome == on) return;
        onAmazonHome = on;
        if (!on) {
            handler.removeCallbacks(stuckHint);
            handler.post(this::hideReturnButton);
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (!onAmazonHome) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (event.getKeyCode() != KeyEvent.KEYCODE_MENU) return false;
        Log.i(TAG, "Menu on Amazon Home: returning");
        openLauncher();
        return true;
    }

    private static boolean isSettingsWindow(String pkg, String cls) {
        for (String app : SETTINGS_APPS) {
            if (pkg.startsWith(app)) return true;
        }
        if (AMAZON_LAUNCHER.equals(pkg)) {
            String c = cls.toLowerCase();
            return c.contains("settings") || c.contains("preferences");
        }
        return false;
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(redirect);
        handler.removeCallbacks(stuckHint);
        hideReturnButton();
        super.onDestroy();
    }

    private void openLauncher() {
        handler.removeCallbacks(stuckHint);
        hideReturnButton();
        setPaused(this, false);
        Log.i(TAG, "opening launcher");
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    private void showReturnButton() {
        if (!onAmazonHome || returnButton != null) return;
        float d = getResources().getDisplayMetrics().density;
        Button btn = new Button(this);
        btn.setText(R.string.return_home_button);
        btn.setAllCaps(false);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btn.setPadding((int) (22 * d), (int) (14 * d), (int) (22 * d), (int) (14 * d));
        btn.setBackgroundColor(0xF0282828);
        btn.setOnClickListener(v -> openLauncher());
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = (int) (28 * d);
        lp.y = (int) (28 * d);
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(btn, lp);
            returnButton = btn;
            btn.post(btn::requestFocus);
        } catch (RuntimeException e) {
            Log.w(TAG, "return button: " + e.getMessage());
        }
    }

    private void hideReturnButton() {
        if (returnButton == null) return;
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(returnButton);
        } catch (RuntimeException ignored) {
        }
        returnButton = null;
    }

    static boolean isRedirecting(Context ctx) {
        return isEnabled(ctx) && !isPaused(ctx);
    }

    static boolean isPaused(Context ctx) {
        return prefs(ctx).getBoolean(KEY_PAUSED, false);
    }

    static void setPaused(Context ctx, boolean paused) {
        prefs(ctx).edit().putBoolean(KEY_PAUSED, paused).apply();
    }

    static boolean isEnabled(Context ctx) {
        String on = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return on != null && on.contains(component(ctx).flattenToString());
    }

    static boolean setRedirecting(Context ctx, boolean on) {
        if (on) {
            setPaused(ctx, false);
            return setEnabled(ctx, true);
        }
        setPaused(ctx, true);
        return isEnabled(ctx) || setEnabled(ctx, true);
    }

    static boolean setEnabled(Context ctx, boolean on) {
        try {
            Settings.Secure.putString(ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    on ? component(ctx).flattenToString() : "");
            Settings.Secure.putInt(ctx.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, on ? 1 : 0);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private static ComponentName component(Context ctx) {
        return new ComponentName(ctx, HomeRedirectService.class);
    }
}
