# MECOOL LEAP-S1 (Skyworth / Android TV 14)

Tested with the Material dock launcher in this fork (`com.gathin.tvhome`) plus Bing
daily wallpaper. Stock HOME is `com.google.android.tvlauncher`.

```bash
TV_HOST=<ip> tools/disable.sh examples/mecool-leap-s1/debloat.txt
TV_HOST=<ip> launcher/build-and-install.sh
```

Undo: `TV_HOST=<ip> tools/restore.sh examples/mecool-leap-s1/debloat.txt com.google.android.tvlauncher`

Keep Assistant, Chromecast and OTA unless you choose otherwise. The top-bar picture
button cycles Bing photos (recent days) on demand.
