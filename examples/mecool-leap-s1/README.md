# MECOOL LEAP-S1 (Skyworth / Android TV 14)

- Brand MECOOL, manufacturer SkyworthDigital, device `LAS`, SDK 34, often `armeabi-v7a`
- Stock HOME: `com.google.android.tvlauncher` (also watch `setupwraith` RecoveryActivity)
- Soft debloat in `debloat.txt` (ads / QA / recommendations / junk browsers). Keeps Assistant,
  Chromecast and OEM OTA. Stock launcher line is included for after our Home is installed.

```bash
export TV_HOST=<ip>
tools/venv/bin/python -I tools/tv.py --probe
tools/disable.sh examples/mecool-leap-s1/debloat.txt
TV_HOST=$TV_HOST launcher/build-and-install.sh
```

Undo:

```bash
tools/restore.sh examples/mecool-leap-s1/debloat.txt com.google.android.tvlauncher
```

If Home lands on setupwraith after disabling `tvlauncher`, forget+rejoin Wi-Fi first, then
`pm disable-user --user 0 com.google.android.tungsten.setupwraith` and
`cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity`.
