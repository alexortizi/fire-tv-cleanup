# Fire TV Stick 4K 2nd gen (AFTKM) — notes

Validated on Fire OS 8.1.x / Android 11 (mt8696 / karat).

## Home button flash

Fire OS ignores the default-home setting. Home always opens Amazon's launcher first;
`HomeRedirectService` (accessibility) then opens this launcher over it. Expect a short flash
of Amazon Home — that is normal, not a bug. Without root you cannot remove that flash.

The redirect persists across reboots while the accessibility setting stays enabled. If Home
stops redirecting after an update, re-run `launcher/build-and-install.sh` (or grant
`WRITE_SECURE_SETTINGS` and re-enable the service).

## What to keep

- **Alexa / Fire TV launcher** — required for Settings and system UI; do not disable.
- **Appstore (`com.amazon.venezia`)** — needed to install/update apps.
- **Chromecast built-in** — leave alone if you cast.

## Optional extras we used

- Custom dock icons: run `tools/make_dock_icons.py` before building (YouTube / Appstore / etc.).
- Bing picture-of-the-day wallpaper (needs network on first launch; cached ~20h).
- OTA: `com.amazon.device.software.ota` / forced OTA updaters can be suspended or
  `disable-user`'d; without root Amazon can still recover updates later.
- Root on AFTKM needs a USB connection (GhostLock / amonet style); network ADB alone is not enough.

## Silk

`com.amazon.cloud9` often cannot be uninstalled; `pm disable-user` works. Hide it from the dock
via `hidden_apps` in `config.xml`.
