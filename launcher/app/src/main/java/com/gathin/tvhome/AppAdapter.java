package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * App tiles. The first {@link #dockCount()} positions are the pinned dock, in the order the user set; the rest
 * is alphabetical. The pinned list is saved on the device and starts from res/values/config.xml (dock_apps).
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    interface TileListener {
        void onTileFocused(int position, View card);

        /** Called for D-pad Up/Down on a tile. Return true when handled. */
        boolean onTileVerticalKey(int position, int keyCode);

        /** Hold OK or press Menu on a tile: pin, unpin, reorder, app info. */
        void onTileMenu(int position, View card);
    }

    // Optional square icons, drawn locally by tools/make_dock_icons.py and kept out of git (they are trademarked
    // logos). Looked up by name so the app builds without them; missing ones fall back to the app's own icon.
    private static final Map<String, String> DOCK_ICONS = new HashMap<>();
    static {
        DOCK_ICONS.put("com.amazon.firetv.youtube", "dock_youtube");
        DOCK_ICONS.put("com.netflix.ninja", "dock_netflix");
        DOCK_ICONS.put("com.amazon.firebat", "dock_prime");
        DOCK_ICONS.put("com.apple.airplay.amazon.airplay", "dock_airplay");
        DOCK_ICONS.put("com.android.mgandroid", "dock_flujo");
        DOCK_ICONS.put("com.amazon.venezia", "dock_appstore");
        DOCK_ICONS.put("com.goodtoolapps.zeus", "dock_zeus");
        DOCK_ICONS.put("com.amazon.tv.settings.v2", "dock_settings");
    }

    /** Bump when config.xml dock_apps changes so device prefs pick up the new order. */
    private static final int DOCK_REV = 3;
    private static final String PREF_DOCK_REV = "dock_rev";

    // Fire TV Settings has no LEANBACK/LAUNCHER activity; the stock home opens it via ACTION_SETTINGS.
    private static final String SETTINGS_PKG = "com.amazon.tv.settings.v2";

    static final class Entry {
        final String label;
        final ComponentName component;
        final String pkg;
        /** Square dock art, or null: then the app's own icon sits on a graphite tile. */
        final Drawable square;
        final Drawable icon;
        /** When set, launch this intent instead of the component (Fire TV Settings). */
        final Intent launchIntent;
        int ambient;

        Entry(String label, ComponentName component, Drawable square, Drawable icon) {
            this(label, component, square, icon, null);
        }

        Entry(String label, ComponentName component, Drawable square, Drawable icon, Intent launchIntent) {
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.square = square;
            this.icon = icon;
            this.launchIntent = launchIntent;
        }
    }

    static final float FOCUS_SCALE = 1.10f;
    /** ~20 dp corners on an 88 dp Material media tile. */
    static final float CORNER_RATIO = 0.23f;

    private final Context ctx;
    private final TileListener listener;
    private static final String PREFS = "tvhome";
    private static final String PREF_DOCK = "dock";
    private final int maxDock;
    private int dockCount;
    /** Pinned packages in dock order, including any that are not installed right now. */
    private final List<String> pinned = new ArrayList<>();
    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private final Set<String> hidden;
    private final List<Entry> items = new ArrayList<>();

    AppAdapter(Context ctx, TileListener listener, int maxDock) {
        this.ctx = ctx;
        this.listener = listener;
        this.maxDock = maxDock;
        android.content.SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int rev = prefs.getInt(PREF_DOCK_REV, 0);
        String saved = prefs.getString(PREF_DOCK, null);
        if (rev < DOCK_REV || saved == null) {
            pinned.addAll(Arrays.asList(ctx.getResources().getStringArray(R.array.dock_apps)));
            prefs.edit()
                    .putString(PREF_DOCK, String.join(",", pinned))
                    .putInt(PREF_DOCK_REV, DOCK_REV)
                    .apply();
        } else {
            pinned.addAll(saved.isEmpty() ? Collections.emptyList() : Arrays.asList(saved.split(",")));
        }
        this.hidden = new HashSet<>(Arrays.asList(ctx.getResources().getStringArray(R.array.hidden_apps)));
    }

    void reload() {
        PackageManager pm = ctx.getPackageManager();
        // Fire TV Appstore/Silk only advertise LAUNCHER; leanback apps advertise LEANBACK_LAUNCHER.
        List<ResolveInfo> found = new ArrayList<>();
        found.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        found.addAll(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        List<Entry> fresh = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg)) continue;
            // One tile per app: prefer the package's primary leanback entry when it exists.
            Intent main = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (main == null) main = pm.getLaunchIntentForPackage(pkg);
            ComponentName mainComponent = main != null ? main.getComponent() : null;
            if (mainComponent != null && !mainComponent.getClassName().equals(ri.activityInfo.name)) continue;
            if (!seen.add(pkg)) continue;
            Drawable icon = ri.loadIcon(pm);
            String label = String.valueOf(ri.loadLabel(pm));
            String squareName = DOCK_ICONS.get(pkg);
            int square = squareName == null ? 0
                    : ctx.getResources().getIdentifier(squareName, "drawable", ctx.getPackageName());
            fresh.add(new Entry(label, new ComponentName(pkg, ri.activityInfo.name),
                    square != 0 ? ctx.getDrawable(square) : null, icon));
        }
        if (pinned.contains(SETTINGS_PKG) && seen.add(SETTINGS_PKG) && !hidden.contains(SETTINGS_PKG)) {
            fresh.add(settingsEntry(pm));
        }
        Collections.sort(fresh, (a, b) -> {
            int fa = pinned.indexOf(a.pkg), fb = pinned.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return 1;
                if (fb < 0) return -1;
                return Integer.compare(fa, fb);
            }
            return a.label.compareToIgnoreCase(b.label);
        });
        items.clear();
        items.addAll(fresh);
        int docked = 0;
        while (docked < items.size() && pinned.contains(items.get(docked).pkg)) docked++;
        dockCount = Math.min(docked, maxDock);
        notifyDataSetChanged();
    }

    private Entry settingsEntry(PackageManager pm) {
        Drawable icon = ctx.getDrawable(R.drawable.ic_settings);
        String label = ctx.getString(R.string.settings);
        try {
            ApplicationInfo ai = pm.getApplicationInfo(SETTINGS_PKG, 0);
            icon = pm.getApplicationIcon(ai);
            CharSequence name = pm.getApplicationLabel(ai);
            if (name != null && name.length() > 0) label = String.valueOf(name);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        int squareId = ctx.getResources().getIdentifier("dock_settings", "drawable", ctx.getPackageName());
        Drawable square = squareId != 0 ? ctx.getDrawable(squareId) : null;
        Intent open = new Intent(Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return new Entry(label, new ComponentName(SETTINGS_PKG, SETTINGS_PKG + ".DockShortcut"),
                square, icon, open);
    }

    // ---- dock editing ----------------------------------------------------------------------------

    int dockCount() {
        return dockCount;
    }

    boolean isDocked(int position) {
        return position >= 0 && position < dockCount;
    }

    String pkgAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).pkg : "";
    }

    int positionOf(String pkg) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).pkg.equals(pkg)) return i;
        return -1;
    }

    /** Adds the app to the end of the dock. False when the dock is full. */
    boolean pin(String pkg) {
        if (pinned.contains(pkg)) return true;
        if (dockCount >= maxDock) return false;
        pinned.removeIf(p -> positionOf(p) < 0);   // forget uninstalled pins so they don't hold slots
        pinned.add(pkg);
        saveAndReload();
        return true;
    }

    void unpin(String pkg) {
        pinned.remove(pkg);
        saveAndReload();
    }

    /** Swaps the app with its dock neighbour on the left (-1) or right (+1). */
    void move(String pkg, int delta) {
        int from = pinned.indexOf(pkg), to = from + delta;
        if (from < 0 || to < 0 || to >= pinned.size()) return;
        Collections.swap(pinned, from, to);
        saveAndReload();
    }

    private void saveAndReload() {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(PREF_DOCK, String.join(",", pinned)).apply();
        reload();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new Holder(v, listener, this);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        h.name.setText(e.label);
        boolean docked = isDocked(position);
        h.name.setVisibility(docked ? View.INVISIBLE : View.VISIBLE);   // the dock uses the tooltip instead
        h.name.setAlpha(0f);
        h.card.setScaleX(1f);
        h.card.setScaleY(1f);
        h.card.setTranslationY(0f);
        h.shadow.setVisibility(View.GONE);
        h.label.setVisibility(View.GONE);
        // Custom dock art is already full-bleed; system icons get a heavy crop past their padding.
        Drawable art = e.square != null ? e.square : e.icon;
        if (e.square != null) {
            h.card.setBackground(null);
        } else {
            h.card.setBackground(ctx.getDrawable(R.drawable.dock_tile_bg));
        }
        h.banner.setImageDrawable(art);
        h.banner.setScaleType(ImageView.ScaleType.FIT_XY);
        float zoom = e.square != null ? 1f : 2.1f;
        h.banner.setScaleX(zoom);
        h.banner.setScaleY(zoom);
        h.banner.setPadding(0, 0, 0, 0);
        h.banner.setVisibility(View.VISIBLE);
        h.icon.setVisibility(View.GONE);
        h.card.setOnClickListener(v -> launch(e));
        h.card.setOnLongClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onTileMenu(pos, v);
            return true;
        });
    }

    private void launch(Entry e) {
        try {
            if (e.launchIntent != null) {
                ctx.startActivity(e.launchIntent);
                return;
            }
            Intent i = new Intent(Intent.ACTION_MAIN)
                    .setComponent(e.component)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            i.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            try {
                ctx.startActivity(i);
            } catch (Exception leanbackMiss) {
                i.removeCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
                i.addCategory(Intent.CATEGORY_LAUNCHER);
                ctx.startActivity(i);
            }
        } catch (Exception ex) {
            Toast.makeText(ctx, "Can't open " + e.label, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).label : "";
    }

    /** A vivid colour sampled from the app's banner (or icon), cached per entry. */
    int ambientColorAt(int position) {
        if (position < 0 || position >= items.size()) return 0xFF444444;
        Entry e = items.get(position);
        if (e.ambient == 0) e.ambient = vividColor(e.square != null ? e.square : e.icon);
        return e.ambient;
    }

    private static int vividColor(Drawable live) {
        if (live == null) return 0xFF444444;
        // Draw a private copy: changing the bounds of the drawable an ImageView shows would blank the tile.
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 12;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, n, n);
        d.draw(c);
        d.setBounds(saved);
        float[] hsv = new float[3];
        int best = 0xFF444444;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bmp.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                Color.colorToHSV(p, hsv);
                float score = hsv[1] * Math.min(1f, hsv[2] * 1.4f);   // saturated and not too dark
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        bmp.recycle();
        if (bestScore < 0.25f) {   // nearly monochrome banner (e.g. white): use its average, lifted
            int avg = Color.rgb((int) (r / (n * n)), (int) (g / (n * n)), (int) (b / (n * n)));
            Color.colorToHSV(avg, hsv);
            hsv[2] = Math.max(hsv[2], 0.55f);
            return Color.HSVToColor(hsv);
        }
        Color.colorToHSV(best, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = Math.max(0.5f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final View shadow;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;

        Holder(View v, TileListener listener, AppAdapter owner) {
            super(v);
            card = v.findViewById(R.id.card);
            shadow = v.findViewById(R.id.shadow);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            name = v.findViewById(R.id.name);

            final float density = v.getResources().getDisplayMetrics().density;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    // Squircle-ish corners: 22.5% of the short side, matching the dock icon artwork.
                    float radius = Math.min(view.getWidth(), view.getHeight()) * CORNER_RATIO;
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            card.setClipToOutline(true);

            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                int pos = getBindingAdapterPosition();
                if (listener == null || pos == RecyclerView.NO_POSITION) return false;
                if (keyCode == KeyEvent.KEYCODE_MENU) {
                    listener.onTileMenu(pos, view);
                    return true;
                }
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                return listener.onTileVerticalKey(pos, keyCode);
            });

            card.setOnFocusChangeListener((view, focused) -> {
                // Dock icons are magnified as a group by MainActivity; grid tiles scale themselves.
                boolean inDock = owner.isDocked(getBindingAdapterPosition());
                float s = focused ? FOCUS_SCALE : 1.0f;
                if (!inDock) {
                    view.animate().scaleX(s).scaleY(s)
                            .setDuration(focused ? 260 : 180)
                            .setInterpolator(new OvershootInterpolator(focused ? 1.6f : 0f))
                            .start();
                }
                name.animate().alpha(focused ? 1f : 0f)
                        .translationY(focused ? density * 4f : -density * 6f)
                        .setDuration(220).start();
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}
