# Auto Grayscale

A tiny Android app with one job: **keep your phone in grayscale.** If you turn
grayscale off, the app waits a number of minutes you choose and then turns it
back on automatically — a gentle nudge for reducing screen-time appeal.

## What it does

- Forces the **whole system** into grayscale using Android's built-in
  color-correction (display daltonizer) feature — not just this app, the entire
  screen.
- Watches the setting. The moment grayscale is switched off, it starts a timer;
  when the timer elapses it re-enables grayscale.
- Simple UI: an **on/off switch** and a **minute selector** (1–60 minutes) for
  the re-enable delay.
- A foreground service keeps it working in the background and after reboot.

## The one requirement: `WRITE_SECURE_SETTINGS`

Turning the whole screen grayscale means writing two secure settings
(`accessibility_display_daltonizer_enabled` and
`accessibility_display_daltonizer`). Android does **not** let a normal app grant
itself this permission, so you grant it once using any **one** of these methods.
All three do the same thing — once granted, the app needs nothing else.

### Option A — ADB (works on any phone, no root, no extra app)

1. Enable **Developer options → USB debugging** on the phone.
2. Connect it to a computer with `adb` installed and run:

   ```
   adb shell pm grant com.dolphinforward.grayscale android.permission.WRITE_SECURE_SETTINGS
   ```

### Option B — Shizuku

1. Install and start [Shizuku](https://shizuku.rikka.app/) (via wireless
   debugging or root — Shizuku's own setup).
2. Open Auto Grayscale, tap **Allow Shizuku access**, then **Grant via Shizuku**.

### Option C — Root

On a rooted device, tap **Grant via root (su)** and approve the root prompt.

The app shows which methods are available and updates its status once the
permission is held.

## Usage

1. Grant the permission (above).
2. Flip the **Auto grayscale** switch on — the screen goes grayscale.
3. Set the **re-enable delay** with the slider.
4. From now on, if grayscale gets turned off, it returns after the delay.

To stop, flip the switch off.

## Building

### In the cloud / CI

Every push builds a debug APK via GitHub Actions
(`.github/workflows/build.yml`); download it from the run's **Artifacts**
(`auto-grayscale-debug`).

### Locally

Requires JDK 17+ and the Android SDK (platform 34, build-tools 34.0.0).

```
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Project layout

| Path | Purpose |
| --- | --- |
| `MainActivity.kt` | Compose UI: switch, minute slider, permission status |
| `GrayscaleController.kt` | Reads/writes the grayscale secure settings |
| `PermissionManager.kt` | Checks/obtains `WRITE_SECURE_SETTINGS` |
| `ShizukuGranter.kt` / `RootGranter.kt` | The Shizuku and root grant paths |
| `GrayscaleService.kt` | Foreground service: watches the setting, schedules re-enable |
| `ReapplyReceiver.kt` | Alarm callback that re-enables grayscale |
| `BootReceiver.kt` | Restarts the service after reboot |

## Notes

- Min Android 8.0 (API 26); targets Android 14 (API 34).
- This app cannot grant itself elevated permissions — that is by design on
  Android. The one-time grant step above is unavoidable for any app that
  controls a system-wide display setting.
