# The prompt

Paste this into a coding assistant with shell access on a computer that is on the same Wi-Fi as
the TV. Works on **Android TV**, **Google TV** and **Fire TV** (Xiaomi, TCL, Hisense, Sony,
OnePlus, Realme, Nvidia Shield, Fire TV Stick, etc.). Not on Samsung Tizen or LG webOS.

If you cloned this repo, say so: reuse `tools/` and `launcher/` instead of rewriting them. Edit
`launcher/app/src/main/res/values/config.xml`, generate brand dock icons with
`tools/make_dock_icons.py`, and install with `launcher/build-and-install.sh`.

Before you start, on the TV enable developer / ADB debugging (wording varies by brand: often
Settings → Device Preferences / Device & Software → About → press OK on Build seven times →
Developer options → USB / ADB / Network debugging). Keep the TV on.

---

I want to debloat my TV and install the launcher from this repo (Material dock + Bing daily
wallpaper). My TV model is: <MODEL NAME HERE>. Work on the real device, step by step, and verify
with screenshots pulled from the TV. Ask me before anything that depends on my preferences.

## Connect

- Find the TV on my network (hostname, ARP, or ask me for the IP) and connect over ADB on port 5555.
- If the stock `adb` binary fails with "No route to host" on macOS even though the port is open,
  that is macOS Local Network privacy blocking adb. Don't debug it: use `tools/tv.py` (Python
  `adb-shell` venv) instead. For larger APKs use `tools/b64push.py` and verify md5.
- Tell me when the "Allow USB debugging?" prompt will appear so I can accept it.
- Probe the device (`getprop`, manufacturer, Android / Fire OS version) and branch behaviour on that.

## Back up first

- Save package lists, disabled packages, `getprop` and settings dumps to a local `backup/` folder
  (never commit device dumps). Write a restore path that re-enables everything you disable.

## Debloat

- List installed packages and group them: manufacturer telemetry/ads, content upsell rows,
  background junk, preloaded apps, streaming apps.
- Prefer `pm disable-user --user 0` (or suspend where disable is refused). Never uninstall system
  packages.
- Before touching streaming apps, voice assistant, Chromecast / casting, Appstore, or OTA / update
  services, ask me with a checklist and only disable what I reject.
- Run `pm` one command per shell call; chained `pm` may stop after the first.
- Reuse or extend an `examples/<model>/debloat.txt` when one matches; otherwise create one.

## Launcher (use this repo)

- Build and install `launcher/` (`com.gathin.tvhome`). Do **not** invent a new launcher or install a
  paid third-party one.
- Edit `config.xml`: starting `dock_apps`, `hidden_apps`, `live_tv_package`, `columns`.
- Run `tools/make_dock_icons.py` so brand tiles exist locally before building (they are not in git).
- Install with `TV_HOST=<ip> launcher/build-and-install.sh` when possible.

### Platform rules (critical)

Detect manufacturer / OS, then:

**Android TV / Google TV (non-Amazon)**  
- After install, set this app as default home (`cmd package set-home-activity`) and only then
  disable the stock launcher (often `com.google.android.tvlauncher` or the OEM equivalent). Ask me
  if unsure which package is stock home.
- Do **not** enable Fire TV Home redirect unless the device is Amazon.

**Fire TV / Fire OS (Amazon)**  
- **Never** disable `com.amazon.tv.launcher` — it hosts Settings and system UI. Disabling it bricks
  Settings access.
- Fire OS ignores default-home; use the launcher's `HomeRedirectService` (accessibility) as
  `build-and-install.sh` already does. Expect a short flash of Amazon Home before ours — that is
  normal.
- Hide Amazon junk LAUNCHER stubs via `hidden_apps` / on-device Hide (Photos, News, Kids, Amazon
  Home tile, etc.). Keep Appstore / Alexa / casting if I asked to keep them.

### Product behaviour to preserve

- Bing picture-of-the-day background (cached; OK if offline).
- Dock + grid: pin/unpin, reorder, hide/show on the TV (Menu or hold OK; gear → Show Hidden Apps).
- Inputs / Wi-Fi / Settings top bar; greeting + clock + date.
- D-pad: Up/Down one row; Up from dock to top bar; remember last focus.
- Keep it light (~1 GB RAM devices): no heavy blur stacks, no video wallpapers.

## Verify

- After each change, inject remote keys over ADB, screenshot, and fix what you see.
- When done: restore instructions, how to rebuild/install, how to change dock/grid/hides, and note
  the Fire vs Google home difference if relevant.

---

Expect a few checklist questions (apps to keep, dock apps/order, voice assistant, casting, OTA).
Everything should stay reversible.
