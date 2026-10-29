# fire-tv-cleanup

**Your TV, minus the ads.** Debloat an **Android TV**, **Google TV** or **Fire TV** over ADB and
install a clean Material-style home with a Bing daily wallpaper. Hand
[`PROMPT.md`](PROMPT.md) to a coding assistant, answer a few checklist questions, and it works on
the real device.

Forked from [itsmeterrylin/fire-tv-cleanup](https://github.com/itsmeterrylin/fire-tv-cleanup)
(itself from [Gathin23/tv-vibehack](https://github.com/Gathin23/tv-vibehack) / the CleanYourTV
line). This fork keeps **Fire TV** support, a Material dock, Bing wallpaper (auto + manual
refresh), and per-device examples for both Fire Stick and Android TV boxes.

![Material dock on Fire TV with Bing daily wallpaper](docs/firetv-material-home.jpg)

Everything is reversible: packages are **disabled**, never uninstalled.
`tools/restore.sh` re-enables them and can restore the stock home.

## Platform rule (read this first)

`launcher/build-and-install.sh` branches on `ro.product.manufacturer`:

| Device | What install does | What you must not do |
|---|---|---|
| **Fire TV / Fire OS** (`manufacturer=Amazon`) | Install APK, enable `HomeRedirectService` + photo screensaver. **Leaves** `com.amazon.tv.launcher` alone. | Never `pm disable` Amazon’s launcher — it hosts Settings and bricks UI access. |
| **Android TV / Google TV** (everyone else) | Install APK, `cmd package set-home-activity`, then `pm disable-user` the stock HOME app (auto-detected: `tvlauncher`, `launcherx`, …). | Disable stock HOME only **after** our launcher starts. Watch for `setupwraith` stealing Home (below). |

The launcher itself also branches: Inputs pill is hidden on Amazon (fake HDMI passthrough);
Fire-only menus use `HomeRedirectService`.

## Quick start

1. Same Wi-Fi as the computer. On the TV enable developer / ADB (wording varies):
   - **Android / Google TV:** Settings → Device Preferences → About → OK on Build ×7 → Developer
     options → USB / Network debugging.
   - **Fire TV:** Settings → Device & Software → About → OK on device name ×7 → Developer Options
     → ADB debugging.
2. Paste [`PROMPT.md`](PROMPT.md) into a coding assistant (or follow the hand commands below).
3. Accept “Allow USB debugging?” on the TV when asked. Answer the checklist (apps to keep, dock,
   Assistant / Cast / OTA).

## What's in the repo

| Path | What it is |
|---|---|
| [`PROMPT.md`](PROMPT.md) | Brand-neutral prompt for an assistant with shell access. |
| [`launcher/`](launcher/) | Home screen (`com.gathin.tvhome`). Edit `config.xml`, then `build-and-install.sh`. |
| [`tools/tv.py`](tools/tv.py) | Pure-Python ADB client (no Google `adb` binary). |
| [`tools/b64push.py`](tools/b64push.py) | Large APK push as base64 + md5. |
| [`tools/disable.sh`](tools/disable.sh) / [`restore.sh`](tools/restore.sh) | Apply / undo a debloat list. |
| [`tools/make_dock_icons.py`](tools/make_dock_icons.py) | Generate brand dock tiles locally (not in git — trademarks). |
| [`examples/`](examples/) | Per-device debloat lists (Fire Stick, Hisense Fire, Xiaomi, MECOOL LEAP-S1, …). |

## The launcher

Small native Android TV home for ~1 GB RAM sticks/boxes:

- **Bing picture of the day** (HPImageArchive, cached ~20 h, offline-safe). Top-bar **picture
  button** (left of Wi-Fi) force-refreshes and cycles recent Bing days (idx 0–7).
- Material dock: full-bleed tiles, quiet focus label, Bing photo as the hero.
- Pin / unpin / reorder / hide on the TV (Menu or hold OK; gear → Show Hidden Apps).
- Top bar: Inputs (non-Amazon with real HDMI), **wallpaper refresh**, Wi-Fi, Settings gear.
- Greeting + clock + date (Open Sans). Override greetings via `good_*` in `strings.xml`, or set
  `tvhome.userName=…` in `launcher/local.properties`.
- Local-photo screensaver (`PhotoDream`); on Fire TV, install sets it as the system screensaver.

Configure starting dock / hidden apps / columns in
[`launcher/app/src/main/res/values/config.xml`](launcher/app/src/main/res/values/config.xml).
Generate brand icons before building:

```bash
tools/iconvenv/bin/python -I tools/make_dock_icons.py launcher/app/src/main/res/drawable-nodpi /path/to/font.ttf
TV_HOST=192.168.1.50 launcher/build-and-install.sh
```

Needs JDK 17+ and Android SDK (platform 34, build-tools 34). On macOS, Android Studio’s JBR works;
`build-and-install.sh` also looks for `$HOME/Library/Android/sdk`.

## Android TV / Google TV

Tested: **MECOOL LEAP-S1** (SkyworthDigital / Android 14) — see
[`examples/mecool-leap-s1/`](examples/mecool-leap-s1/).

```bash
export TV_HOST=<box ip>
tools/venv/bin/python -I tools/tv.py --probe
tools/disable.sh examples/mecool-leap-s1/debloat.txt   # soft junk first; launcher last
launcher/build-and-install.sh
```

Notes from real boxes:

- Stock HOME is often `com.google.android.tvlauncher` or
  `com.google.android.apps.tv.launcherx`. The install script detects it; override with
  `STOCK_LAUNCHER=…` or `STOCK_LAUNCHER=none`.
- **`setupwraith`:** after disabling the stock launcher, Google TV’s
  `com.google.android.tungsten.setupwraith/.RecoveryActivity` can claim HOME. If Wi-Fi was saved
  during first setup, disabling setupwraith **deletes that network** and drops ADB. Check
  `dumpsys wifi` (`networkCreator` / `cname=`). Prefer forget + rejoin Wi-Fi first so the network
  belongs to `android.uid.system`, then disable setupwraith and re-run `set-home-activity`.
- Prefer **Play Store** for apps that are there (e.g. browsers). Sideload only what Play cannot
  provide (SmartTube, Flujo, etc.). SmartTube stable APKs:
  [smarttubeapp.github.io](https://smarttubeapp.github.io/) / `yuliskov/SmartTube` releases — pick
  `armeabi-v7a` or `arm64-v8a` to match `getprop ro.product.cpu.abi`, push with `b64push.py`,
  `pm install -r`.
- Keep Assistant (`katniss`), Chromecast (`mediashell`) and OEM OTA unless the owner opts out.
- Settings may not expose a leanback tile; the top **Configuración / Settings** pill is enough —
  do not rely on `com.android.tv.settings` in `dock_apps`.

Undo:

```bash
tools/restore.sh examples/mecool-leap-s1/debloat.txt com.google.android.tvlauncher
```

## Fire TV

Fire OS is Android underneath, but it guards home. Tested:

- Hisense Fire TV Edition (Fire OS 8 / Android 11, AFTOLCBC82)
- Amazon Fire TV Stick 4K 2nd gen (AFTKM / karat, Fire OS 8 / Android 11) —
  [`examples/amazon-fire-tv-stick-4k-2ndgen-AFTKM/`](examples/amazon-fire-tv-stick-4k-2ndgen-AFTKM/)

![Amazon screensaver (top) and the launcher's screensaver (bottom)](docs/firetv-screensaver-before-after.jpg)

```bash
export TV_HOST=<stick ip>
launcher/build-and-install.sh          # enables HomeRedirect; does NOT disable Amazon Home
tools/disable.sh examples/amazon-fire-tv-stick-4k-2ndgen-AFTKM/debloat.txt   # optional
```

How it differs from Android TV:

- **Amazon’s launcher stays.** Fire OS ignores default-home; `com.amazon.tv.launcher` also hosts
  Settings. Disabling it locks you out. `HomeRedirectService` (accessibility) opens our home when
  Amazon Home appears — a short flash of Amazon Home is **expected**.
- **Settings grace:** redirect waits so Amazon Settings can open; returning to Amazon Home brings
  you back to ours.
- Stuck on Amazon Home → **Menu** or **← Home**, or open **Home** under Your Apps
  (`LEANBACK_LAUNCHER`). Gear → **Switch to Amazon Home** turns the redirect off.
- Hide Amazon junk tiles via `hidden_apps` / on-device Hide (Photos, News, Kids, Amazon Home tile).
  Keep Appstore / Alexa / casting if asked.
- ADB often turns off after reboot — re-enable ADB debugging before reconnecting.
- Some packages are protected; see the example NOTES. Silk: `disable-user` + hide is enough.
- Dock packages differ from Google TV (e.g. `com.amazon.firetv.youtube`, `com.amazon.venezia`,
  `com.amazon.tv.settings.v2` vs `com.google.android.youtube.tv` / Play Store /
  `com.android.tv.settings`).

## Talking to the TV by hand

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50
tools/venv/bin/python -I tools/tv.py --probe
tools/venv/bin/python -I tools/tv.py 'pm list packages'
printf 'cmd1\ncmd2\n' | tools/venv/bin/python -I tools/tv.py --batch
tools/venv/bin/python -I tools/b64push.py app.apk /data/local/tmp/app.apk
tools/venv/bin/python -I tools/tv.py 'pm install -r /data/local/tmp/app.apk'
tools/disable.sh examples/mecool-leap-s1/debloat.txt
tools/restore.sh examples/mecool-leap-s1/debloat.txt com.google.android.tvlauncher
```

### Gotchas

- **macOS `adb`: “No route to host”** while the port is open → Local Network privacy blocking the
  Google `adb` binary. Use `tools/tv.py` instead; don’t debug the binary.
- **Chained `pm`** (`pm disable a; pm disable b`) may stop after the first. One command per shell
  call; `--batch` does that.
- **Large pushes** hang on the Python client → `tools/b64push.py`.
- **Disable stock HOME only after** ours is installed and started.
- **Xiaomi IME:** after switching launchers, set
  `ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME` if the
  keyboard is missing.
- **Private DNS** (optional ad-block): ask first if the router already filters; Private DNS bypasses
  it. Undo with `private_dns_mode opportunistic`.

## Supported TVs

Android TV / Google TV: Xiaomi, TCL, Hisense, Sony, OnePlus, Realme, Nvidia Shield, MECOOL /
Skyworth boxes, and most others. Fire TV: see above. **Not** Samsung Tizen or LG webOS. Device
lists live under [`examples/`](examples/).

## Contributing

Add a debloat list, improve the launcher, or sharpen the prompt — [CONTRIBUTING.md](CONTRIBUTING.md).

## Licence

MIT, see [`LICENSE`](LICENSE). Outfit / Open Sans: SIL OFL (`launcher/FONT-LICENSE-OFL.txt`).
Photo credits: [`CREDITS.md`](CREDITS.md). Bing wallpapers remain © Microsoft / photographers; the
launcher only downloads the public HPImageArchive asset onto the device.
