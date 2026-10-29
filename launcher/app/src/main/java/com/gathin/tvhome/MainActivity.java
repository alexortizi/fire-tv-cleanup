package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Material-inspired Fire TV home: tonal surface, app-tinted blob, pinned dock, spring glass highlight.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private GlassView glass;
    private View ambient;
    private View colorBlob;
    private TextView heroName;
    private TextView focusTitle;
    private View dockDot;
    // Dock magnification: focused tile and neighbours grow from their bottom edge and lift.
    private static final float DOCK_MAGNIFY = 1.18f;
    private static final float DOCK_NEIGHBOUR = 1.06f;
    private static final float DOCK_LIFT_DP = 8f;
    private static final float DOCK_NEIGHBOUR_LIFT_DP = 3f;
    private GradientDrawable ambientGlow;
    private GradientDrawable blobDrawable;
    private int ambientColor;
    private boolean heroShown;
    private TextView greeting;
    private ImageView bgPhoto;
    private TextView btnWifi;
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    private boolean glassShown;
    private int lastFocused = 0;
    private float density;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final int COLOR_WARN = 0xFFFFB340;   // connected, no internet
    private static final int COLOR_ERROR = 0xFFFF5A52;  // no network
    private static final int[] WIFI_ICONS = {R.drawable.ic_wifi_1, R.drawable.ic_wifi_2, R.drawable.ic_wifi};
    // Refresh the Wi-Fi pill as soon as the connection changes, not on the next minute tick.
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(@NonNull Network n) { handler.post(MainActivity.this::updateWifiLabel); }
        @Override public void onLost(@NonNull Network n) { handler.post(MainActivity.this::updateWifiLabel); }
        @Override public void onCapabilitiesChanged(@NonNull Network n, @NonNull NetworkCapabilities c) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
        @Override public void onLinkPropertiesChanged(@NonNull Network n, @NonNull LinkProperties p) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateWifiLabel();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        COLUMNS = Math.max(1, getResources().getInteger(R.integer.columns));
        liveTvPkg = getString(R.string.live_tv_package).trim();

        root = findViewById(R.id.root);
        glass = findViewById(R.id.glass);
        ambient = findViewById(R.id.ambient);
        colorBlob = findViewById(R.id.colorBlob);
        heroName = findViewById(R.id.heroName);
        focusTitle = findViewById(R.id.focusTitle);
        // App name sits at ~1/3 screen height (left), between clock and dock.
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            if (h <= 0 || focusTitle == null) return;
            float y = h / 3f - focusTitle.getHeight() / 2f;
            if (Math.abs(focusTitle.getTranslationY() - y) > 1f) focusTitle.setTranslationY(y);
        });
        dockDot = findViewById(R.id.dockDot);
        ambientGlow = new GradientDrawable();
        ambientGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambientGlow.setGradientCenter(0.28f, 0.55f);
        ambient.setBackground(ambientGlow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                ambientGlow.setGradientRadius((r - l) * 0.72f));
        blobDrawable = new GradientDrawable();
        blobDrawable.setShape(GradientDrawable.OVAL);
        colorBlob.setBackground(blobDrawable);
        setAmbient(0xFF6750A4);
        colorBlob.setAlpha(0.28f);
        greeting = findViewById(R.id.greeting);
        bgPhoto = findViewById(R.id.bgPhoto);
        btnWifi = findViewById(R.id.btnWifi);
        loadBingBackground();

        grid = findViewById(R.id.grid);
        // One span unit per cell of any dock size 1..COLUMNS: the dock row splits evenly, grid rows are COLUMNS wide.
        final int spans = lcmUpTo(COLUMNS);
        GridLayoutManager glm = new GridLayoutManager(this, spans);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.isDocked(position) ? spans / adapter.dockCount() : spans / COLUMNS;
            }
        });
        grid.setLayoutManager(glm);
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);

        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));
        adapter = new AppAdapter(this, this, COLUMNS);
        grid.addItemDecoration(new DockDecoration(adapter, COLUMNS, density));
        grid.setAdapter(adapter);

        // Push the dock to the bottom; the rest of the apps sit below until Down is pressed.
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            int dockRow = (int) ((148 + 8 + 16) * density);   // full-bleed tiles + decoration
            int want = Math.max(0, h - dockRow + (int) (8 * density));
            if (grid.getPaddingTop() != want) {
                grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), grid.getPaddingBottom());
            }
        });

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!glassShown) return;
                glassTargetY -= dy;
                if (springY.isRunning()) springY.animateToFinalPosition(glassTargetY);
                else glass.setTranslationY(glassTargetY);
            }
        });

        // Liquid feel: soft springs on the highlight's position.
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_Y);
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            s.setSpring(new SpringForce().setStiffness(260f).setDampingRatio(0.58f));
        }

        View btnInputs = findViewById(R.id.btnInputs);
        View btnSettings = findViewById(R.id.btnSettings);
        View btnWallpaper = findViewById(R.id.btnWallpaper);
        // HDMI/AV Inputs — real TVs only. Fire Stick reports a fake ARC "passthrough"; hide on Amazon.
        if (!isAmazonDevice() && hasPassthroughInputs()) {
            btnInputs.setOnClickListener(v -> showInputs());
        } else {
            btnInputs.setVisibility(View.GONE);
        }
        btnWifi.setOnClickListener(v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        btnWallpaper.setOnClickListener(v -> refreshBingWallpaper(btnWallpaper));
        // Gear = launcher menu (hidden apps / home switch / system Settings). Long-press = Amazon Settings.
        btnSettings.setOnClickListener(v -> showHomeScreenMenu(v));
        btnSettings.setOnLongClickListener(v -> {
            open(new Intent(Settings.ACTION_SETTINGS));
            return true;
        });

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        View leftmost = btnInputs.getVisibility() == View.VISIBLE ? btnInputs : btnWallpaper;
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_MENU && v.getId() == R.id.btnSettings) {
                showHomeScreenMenu(v);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v.getId() == leftmost.getId()) return true;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnSettings;
        };
        btnInputs.setOnKeyListener(pillKeys);
        btnWallpaper.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);

        // Hide glass / hero when focus leaves the tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) {
                hideGlass();
                hideHero();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        glassShown = false;
        glass.setAlpha(0f);
        heroShown = false;
        heroName.setAlpha(0f);
        focusTitle.setAlpha(0f);
        dockDot.setAlpha(0f);
        ambient.setAlpha(0.2f);
        adapter.reload();
        grid.scheduleLayoutAnimation();
        updateGreeting();
        updateWifiLabel();
        loadBingBackground();
        handler.postDelayed(minuteTick, MINUTE_MS);
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.registerDefaultNetworkCallback(networkCallback);

        // Return to the tile we left from, or the first one.
        grid.post(() -> {
            View focused = getCurrentFocus();
            if (focused != null && grid.findContainingViewHolder(focused) != null) return;
            focusTile(lastFocused);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(minuteTick);
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.unregisterNetworkCallback(networkCallback);
    }

    @Override
    public void onBackPressed() {
        // Home screen: Back goes nowhere.
    }

    // ---- glass highlight -------------------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        lastFocused = position;
        if (adapter.isDocked(position)) {
            onDockFocused(position, card);
            return;
        }
        magnifyDock(-1);
        // Tile rect in root coordinates, using layout positions (ignores the scale animation).
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = 6f * density;        // matches GlassView's glow inset
        float w = card.getWidth() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float h = card.getHeight() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f;
        int color = adapter.ambientColorAt(position);
        glass.setTint(color);
        setAmbient(color);
        ambient.animate().alpha(0.28f).setDuration(280).start();
        showFocusTitle(adapter.labelAt(position));

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
            glass.setRadius(Math.min(card.getWidth(), card.getHeight()) * AppAdapter.CORNER_RATIO * AppAdapter.FOCUS_SCALE + pad);
        }
        glassTargetX = x;
        glassTargetY = y;
        if (!glassShown) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            glass.animate().alpha(1f).setDuration(180).start();
            glassShown = true;
        } else {
            springX.animateToFinalPosition(x);
            springY.animateToFinalPosition(y);
        }
        View item = (View) card.getParent();
        heroName.animate().alpha(0f).setDuration(120).start();
        dockDot.animate().alpha(0f).setDuration(120).start();
        heroShown = false;
        magnifyDock(-1);
        // Scroll just enough so the whole glass (not only the tile) is visible.
        Rect want = new Rect(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
        grid.requestChildRectangleOnScreen(item, want, false);
    }

    // ---- top shelf -------------------------------------------------------------------------------

    private void onDockFocused(int position, View card) {
        hideGlass();
        View item = (View) card.getParent();
        int decoratedTop = item.getTop() - (int) (6 * density);
        int dy = decoratedTop - grid.getPaddingTop();
        if (Math.abs(dy) > 1) grid.smoothScrollBy(0, dy);
        magnifyDock(position);
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        r.offset(0, -dy);
        showHero(position, r);
    }

    /** Scale dock icons by distance from the focused one; focused < 0 resets them all. */
    private void magnifyDock(int focused) {
        RecyclerView.LayoutManager lm = grid.getLayoutManager();
        if (lm == null) return;
        for (int i = 0; i < adapter.dockCount(); i++) {
            View item = lm.findViewByPosition(i);
            View card = item != null ? item.findViewById(R.id.card) : null;
            if (card == null) continue;
            int d = focused < 0 ? Integer.MAX_VALUE : Math.abs(i - focused);
            float s = d == 0 ? DOCK_MAGNIFY : d == 1 ? DOCK_NEIGHBOUR : 1f;
            float lift = d == 0 ? DOCK_LIFT_DP : d == 1 ? DOCK_NEIGHBOUR_LIFT_DP : 0f;
            card.setPivotX(card.getWidth() / 2f);
            card.setPivotY(card.getHeight());
            card.animate().scaleX(s).scaleY(s).translationY(-lift * density)
                    .setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
            }
    }

    // Capsule above the tile, large title, and a Material blob tinted to the app.
    private void showHero(int position, Rect card) {
        String label = adapter.labelAt(position);
        dockDot.setX(card.exactCenterX() - dockDot.getWidth() / 2f);
        dockDot.setY(card.bottom + 6 * density);
        dockDot.animate().alpha(1f).setDuration(160).start();
        showFocusTitle(label);
        int color = adapter.ambientColorAt(position);
        glass.setTint(color);
        if (!heroShown) {
            setAmbient(color);
            ambient.animate().alpha(0.35f).setDuration(420).start();
            heroShown = true;
        } else if (color != ambientColor) {
            ambient.animate().alpha(0.15f).setDuration(160).withEndAction(() -> {
                setAmbient(color);
                ambient.animate().alpha(0.35f).setDuration(360).start();
            }).start();
        }
    }

    private void showFocusTitle(String label) {
        if (focusTitle == null) return;
        focusTitle.setText(label);
        focusTitle.animate().alpha(1f).setDuration(220).start();
    }

    private void setAmbient(int color) {
        ambientColor = color;
        int c = color & 0x00FFFFFF;
        ambientGlow.setColors(new int[]{0x66000000 | c, 0x33000000 | c, 0x00000000});
        if (blobDrawable != null) {
            int mid = 0xFF000000 | c;
            float[] hsv = new float[3];
            Color.colorToHSV(mid, hsv);
            hsv[1] = Math.min(1f, hsv[1] * 0.85f);
            hsv[2] = Math.max(0.45f, Math.min(0.85f, hsv[2]));
            int bright = Color.HSVToColor(hsv);
            hsv[2] *= 0.55f;
            int deep = Color.HSVToColor(hsv);
            blobDrawable.setColors(new int[]{bright, deep});
            blobDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            blobDrawable.setGradientCenter(0.45f, 0.4f);
            blobDrawable.setGradientRadius(colorBlob.getWidth() > 0
                    ? colorBlob.getWidth() * 0.55f
                    : 400f * density);
        }
    }

    private void hideHero() {
        if (!heroShown && (focusTitle == null || focusTitle.getAlpha() < 0.05f)) return;
        heroShown = false;
        dockDot.animate().alpha(0f).setDuration(160).start();
        if (focusTitle != null) focusTitle.animate().alpha(0f).setDuration(200).start();
        ambient.animate().alpha(0.08f).setDuration(280).start();
        magnifyDock(-1);
    }

    private void loadBingBackground() {
        if (bgPhoto == null) return;
        BingWallpaper.load(this, file -> {
            try {
                bgPhoto.setImageURI(Uri.fromFile(file));
                bgPhoto.animate().alpha(1f).setDuration(500).start();
            } catch (Exception ignored) {
            }
        });
    }

    /** Top-bar picture button: force-fetch the next Bing day (idx 0..7) and apply it. */
    private void refreshBingWallpaper(View button) {
        if (button.getTag() == Boolean.TRUE) return;
        button.setTag(Boolean.TRUE);
        button.setAlpha(0.45f);
        BingWallpaper.refresh(this, (file, ok) -> {
            button.setTag(null);
            button.setAlpha(1f);
            if (ok && bgPhoto != null) {
                try {
                    bgPhoto.setImageURI(null);
                    bgPhoto.setImageURI(Uri.fromFile(file));
                    bgPhoto.setAlpha(0f);
                    bgPhoto.animate().alpha(1f).setDuration(400).start();
                } catch (Exception ignored) {
                }
                android.widget.Toast.makeText(this, R.string.wallpaper_updated,
                        android.widget.Toast.LENGTH_SHORT).show();
            } else {
                android.widget.Toast.makeText(this, R.string.wallpaper_update_failed,
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        glass.animate().alpha(0f).setDuration(160).start();
    }

    // ---- navigation ------------------------------------------------------------------------------

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        int count = adapter.getItemCount();
        int dock = adapter.dockCount();
        int target;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < dock) return false;             // dock row: let focus move to the top bar
            int col = (position - dock) % COLUMNS;
            if (position - dock < COLUMNS) {
                if (dock == 0) return false;
                target = Math.min(col, dock - 1);          // first grid row: up to the dock
            } else {
                target = position - COLUMNS;
            }
        } else {
            if (position < dock) {
                if (count == dock) return true;            // nothing below the dock
                target = Math.min(dock + position, count - 1);
            } else {
                int lastRowStart = dock + ((count - 1 - dock) / COLUMNS) * COLUMNS;
                if (position >= lastRowStart) return true; // already on the last row: stay put
                target = Math.min(position + COLUMNS, count - 1);
            }
        }
        focusTile(target);
        return true;
    }

    private static int lcmUpTo(int n) {
        int l = 1;
        for (int i = 2; i <= n; i++) {
            int a = l, b = i;
            while (b != 0) { int t = a % b; a = b; b = t; }
            l = l / a * i;
        }
        return l;
    }

    // ---- dock editing ----------------------------------------------------------------------------

    @Override
    public void onTileMenu(int position, View card) {
        String pkg = adapter.pkgAt(position);
        boolean docked = adapter.isDocked(position);
        int dock = adapter.dockCount();
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        int width = (int) (220 * density);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        if (docked) {
            addInputRow(rows, popup, R.drawable.ic_unpin, getString(R.string.unpin_from_dock), -1, () -> {
                adapter.unpin(pkg);
                focusAfterLayout(pkg);
            });
            if (position > 0) addInputRow(rows, popup, R.drawable.ic_left, getString(R.string.move_left), -1, () -> {
                adapter.move(pkg, -1);
                focusAfterLayout(pkg);
            });
            if (position < dock - 1) addInputRow(rows, popup, R.drawable.ic_right, getString(R.string.move_right), -1, () -> {
                adapter.move(pkg, 1);
                focusAfterLayout(pkg);
            });
        } else {
            addInputRow(rows, popup, R.drawable.ic_pin, getString(R.string.pin_to_dock), -1, () -> {
                if (adapter.pin(pkg)) focusAfterLayout(pkg);
                else Toast.makeText(this, R.string.dock_full, Toast.LENGTH_SHORT).show();
            });
            int gi = adapter.gridIndex(pkg);
            int gsize = adapter.gridSize();
            int cols = adapter.columns();
            if (gi > 0) addInputRow(rows, popup, R.drawable.ic_left, getString(R.string.move_left), -1, () -> {
                adapter.moveGrid(pkg, -1);
                focusAfterLayout(pkg);
            });
            if (gi >= 0 && gi < gsize - 1) addInputRow(rows, popup, R.drawable.ic_right, getString(R.string.move_right), -1, () -> {
                adapter.moveGrid(pkg, 1);
                focusAfterLayout(pkg);
            });
            if (gi >= cols) addInputRow(rows, popup, R.drawable.ic_up, getString(R.string.move_up), -1, () -> {
                adapter.moveGrid(pkg, -cols);
                focusAfterLayout(pkg);
            });
            if (gi >= 0 && gi + cols < gsize) addInputRow(rows, popup, R.drawable.ic_down, getString(R.string.move_down), -1, () -> {
                adapter.moveGrid(pkg, cols);
                focusAfterLayout(pkg);
            });
        }
        addInputRow(rows, popup, R.drawable.ic_hide, getString(R.string.hide_app), -1, () -> {
            adapter.hideApp(pkg);
            focusTile(Math.min(position, Math.max(0, adapter.getItemCount() - 1)));
        });
        addInputRow(rows, popup, R.drawable.ic_info, getString(R.string.app_info), -1, () ->
                open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))));

        // Float the menu just above the tile, centred on it; the name tooltip steps aside while it's open.
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        float top = docked ? r.bottom - r.height() * DOCK_MAGNIFY - DOCK_LIFT_DP * density : r.top;
        int x = (int) (r.exactCenterX() - width / 2f);
        int y = (int) (top - 12 * density - panel.getMeasuredHeight());
        heroName.animate().alpha(0f).setDuration(120).start();
        popup.setOnDismissListener(() -> {
            if (adapter.isDocked(lastFocused) && heroShown) heroName.animate().alpha(1f).setDuration(160).start();
        });
        panel.setAlpha(0f);
        panel.setTranslationY(8 * density);
        popup.showAtLocation(root, Gravity.NO_GRAVITY, x, y);
        panel.animate().alpha(1f).translationY(0f).setDuration(200)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    /**
     * Gear menu: hidden apps first, then home-switch escape hatch, then Amazon system Settings.
     * (Short-press used to open Settings and looked duplicated next to the dock Settings tile.)
     */
    private void showHomeScreenMenu(View anchor) {
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        int width = (int) (300 * density);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        addInputRow(rows, popup, R.drawable.ic_hide, getString(R.string.show_hidden_apps), -1, () ->
                showHiddenAppsMenu(anchor));
        boolean on = HomeRedirectService.isRedirecting(this);
        addInputRow(rows, popup, R.drawable.ic_unpin, getString(on ? R.string.use_amazon_home : R.string.use_this_home), -1, () -> {
            if (!HomeRedirectService.setRedirecting(this, !on)) {
                Toast.makeText(this, R.string.redirect_needs_permission, Toast.LENGTH_LONG).show();
            } else if (on) {
                Toast.makeText(this, R.string.amazon_home_hint, Toast.LENGTH_LONG).show();
                // Pause takes effect; leave them on Amazon Home if they navigate there next.
            } else {
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
            }
        });
        addInputRow(rows, popup, R.drawable.ic_settings, getString(R.string.open_system_settings), -1, () ->
                open(new Intent(Settings.ACTION_SETTINGS)));
        popup.showAsDropDown(anchor, anchor.getWidth() - width, (int) (10 * density), Gravity.START);
        rows.getChildAt(0).requestFocus();
    }

    /** Menu → Settings button → Show Hidden Apps: unhide user-hidden packages. */
    private void showHiddenAppsMenu(View anchor) {
        List<String> hiddenPkgs = adapter.userHiddenPackages();
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        int width = (int) (300 * density);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        if (hiddenPkgs.isEmpty()) {
            addInputRow(rows, popup, R.drawable.ic_info, getString(R.string.no_hidden_apps), -1, () -> {});
        } else {
            for (String pkg : hiddenPkgs) {
                String label = adapter.labelForPackage(pkg);
                addInputRow(rows, popup, R.drawable.ic_pin, label + " — " + getString(R.string.unhide_app), -1, () -> {
                    adapter.unhideApp(pkg);
                    focusAfterLayout(pkg);
                });
            }
        }
        popup.showAsDropDown(anchor, anchor.getWidth() - width, (int) (10 * density), Gravity.START);
        if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus();
    }

    /** After the dock changes, focus the app's new tile once the grid has re-laid out. */
    private void focusAfterLayout(String pkg) {
        hideHero();
        ((GridLayoutManager) grid.getLayoutManager()).getSpanSizeLookup().invalidateSpanIndexCache();
        grid.invalidateItemDecorations();
        grid.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                grid.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                int pos = adapter.positionOf(pkg);
                RecyclerView.ViewHolder vh = pos >= 0 ? grid.findViewHolderForAdapterPosition(pos) : null;
                if (vh == null) {
                    focusTile(Math.max(pos, 0));
                    return;
                }
                View c = vh.itemView.findViewById(R.id.card);
                c.requestFocus();
                onTileFocused(pos, c);
            }
        });
    }

    private void focusTile(int position) {
        if (adapter.getItemCount() == 0) return;
        final int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            vh.itemView.findViewById(R.id.card).requestFocus();
            return;
        }
        // Not laid out yet: scroll there, then focus once the scroll settles.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
                rv.removeOnScrollListener(this);
                RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pos);
                View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
                if (target != null) target.requestFocus();
            }
        });
        grid.smoothScrollToPosition(pos);
    }

    // ---- top bar ---------------------------------------------------------------------------------

    // Morning 5-12, afternoon 12-17, evening 17-22, night 22-5.
    private static final int[] GREETINGS = {
            R.string.good_morning, R.string.good_afternoon, R.string.good_evening, R.string.good_night};

    private static int dayPart() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 5) return 3;
        if (hour < 12) return 0;
        if (hour < 17) return 1;
        if (hour < 22) return 2;
        return 3;
    }

    private void updateGreeting() {
        // Fixed house greeting (config); optional tvhome.userName only if greetings are generic.
        String text = getString(GREETINGS[dayPart()]);
        String name = getString(R.string.user_name).trim();
        if (!name.isEmpty() && !text.toLowerCase().contains(name.toLowerCase())) {
            greeting.setText(text + ", " + name);
        } else {
            greeting.setText(text);
        }
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    // Signal bars only (no network name, so screenshots stay private); text only when something is wrong.
    private void updateWifiLabel() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network net = cm != null ? cm.getActiveNetwork() : null;
        NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
        if (caps == null) {
            setWifiPill(R.drawable.ic_wifi_off, getString(R.string.offline), COLOR_ERROR);
            return;
        }
        boolean online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            setWifiPill(R.drawable.ic_ethernet, online ? getString(R.string.ethernet) : getString(R.string.no_internet),
                    online ? 0 : COLOR_WARN);
            return;
        }
        WifiManager wm = getApplicationContext().getSystemService(WifiManager.class);
        WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
        int bars = info != null ? WifiManager.calculateSignalLevel(info.getRssi(), WIFI_ICONS.length) : 0;
        if (!online) {
            setWifiPill(WIFI_ICONS[bars], getString(R.string.no_internet), COLOR_WARN);
            return;
        }
        setWifiPill(WIFI_ICONS[bars], getString(R.string.wifi), 0);
    }

    private void setWifiPill(int icon, String label, int alertColor) {
        btnWifi.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        btnWifi.setContentDescription(label);
        // Healthy: a round icon button like Inputs and Settings. Alert: a pill with the status text.
        boolean iconOnly = alertColor == 0;
        ViewGroup.LayoutParams lp = btnWifi.getLayoutParams();
        lp.width = iconOnly ? (int) (42 * density) : ViewGroup.LayoutParams.WRAP_CONTENT;
        btnWifi.setLayoutParams(lp);
        btnWifi.setPaddingRelative((int) ((iconOnly ? 10 : 16) * density), 0, (int) ((iconOnly ? 10 : 20) * density), 0);
        btnWifi.setCompoundDrawablePadding(iconOnly ? 0 : (int) (9 * density));
        if (iconOnly) {
            btnWifi.setText("");
            return;
        }
        SpannableString text = new SpannableString("\u25CF  " + label);
        text.setSpan(new ForegroundColorSpan(alertColor), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        btnWifi.setText(text);
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show();
        }
    }

    private static boolean isAmazonDevice() {
        return "Amazon".equalsIgnoreCase(android.os.Build.MANUFACTURER);
    }

    /** True when the device exposes HDMI/AV passthrough (real TVs). */
    private boolean hasPassthroughInputs() {
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim == null) return false;
        for (TvInputInfo info : tim.getTvInputList()) {
            if (info.isPassthroughInput() && info.getType() == TvInputInfo.TYPE_HDMI) return true;
        }
        return false;
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private void showInputs() {
        View anchor = findViewById(R.id.btnInputs);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        // Compact: as wide as the Inputs pill (a touch wider so "HDMI 1" fits), hanging right below it.
        int width = Math.max(anchor.getWidth(), (int) (150 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase().contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                                TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel")));
            }
        }
        Intent liveTv = liveTvPkg.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTv));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Drop in: fade and unfold from the top edge.
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), Gravity.START);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }
}
