# HPLC Batch Timer — Android App Setup Guide

This guide takes the validated web prototype (`hplc-timer.html`) and turns it into a real Android app using **Capacitor**, with a **local notification** firing at the calculated batch end time.

---

## 1. Prerequisites

Install these once:

- **Node.js** v18 or newer — https://nodejs.org
- **Android Studio** (includes the Android SDK, emulator, and build tools) — https://developer.android.com/studio
  - On first launch, open **SDK Manager** and confirm at least one Android SDK Platform is installed
  - Open **Device Manager** and create a virtual device (e.g. Pixel 6, API 34) for testing

Check installs:

```bash
node -v
npm -v
```

---

## 2. Project Structure

Target layout once set up:

```
hplc-timer-app/
├── www/
│   └── index.html          # your web app (renamed from hplc-timer.html)
├── android/                 # generated native project (Android Studio opens this)
├── capacitor.config.json
├── package.json
└── node_modules/
```

---

## 3. Create the Project

```bash
mkdir hplc-timer-app
cd hplc-timer-app
npm init -y

npm install @capacitor/core @capacitor/cli @capacitor/android
npx cap init "HPLC Timer" "com.yourname.hplctimer" --web-dir www
```

Replace `com.yourname.hplctimer` with your own package ID (reverse-domain style, all lowercase, no spaces).

Create the `www/` folder and copy your prototype in:

```bash
mkdir www
cp /path/to/hplc-timer.html www/index.html
```

---

## 4. Add the Android Platform

```bash
npx cap add android
```

This generates the `android/` folder — a full Android Studio project that loads `www/index.html` in a WebView.

Every time you change files in `www/`, sync them into the native project:

```bash
npx cap sync
```

---

## 5. Add a True Alarm (Bypasses Silent / Vibrate)

**Important distinction:** a standard notification (`@capacitor/local-notifications`) plays sound on the *notification/ringtone* audio stream — which silent and vibrate modes mute by design. To get an alarm-clock-style sound that **breaks through silent/vibrate**, you need sound played explicitly on Android's **alarm audio stream** (`STREAM_ALARM`), scheduled via `AlarmManager`. This is exactly how the built-in Clock app's alarms behave, and it requires a small bridge into native Android code — there's no pure-JS way to do it.

### 5.1 Capacitor project setup

Same as before — install the base local-notifications plugin for permission handling and a fallback banner, plus prepare to add a custom native piece:

```bash
npm install @capacitor/local-notifications
npx cap sync
```

### 5.2 Add a custom native Android plugin

Capacitor lets you write small native plugins that your JS calls directly. Create one (e.g. `AlarmPlugin`) inside `android/app/src/main/java/com/yourname/hplctimer/`:

```kotlin
package com.yourname.hplctimer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "BatchAlarm")
class AlarmPlugin : Plugin() {

    @PluginMethod
    fun scheduleAlarm(call: PluginCall) {
        val triggerAtMillis = call.getLong("triggerAtMillis") ?: return call.reject("Missing triggerAtMillis")
        val context = context
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
        call.resolve()
    }
}
```

`AlarmReceiver` (a `BroadcastReceiver`) is what actually fires: it should launch a full-screen `Activity` (or at minimum play a sound) using `AudioManager.STREAM_ALARM` explicitly, e.g.:

```kotlin
val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
val ringtone = RingtoneManager.getRingtone(context, RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM))
ringtone.audioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ALARM)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()
ringtone.play()
```

Using `USAGE_ALARM` is the key line — it's what ignores the ringer/silent setting.

Register the plugin in `MainActivity.java`/`.kt` via `registerPlugin(AlarmPlugin::class.java)`.

### 5.3 Required manifest permissions

In `android/app/src/main/AndroidManifest.xml`, add:

```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

On Android 12+ (`API 31+`), exact alarms need the user to grant permission in system settings — prompt via `AlarmManager.canScheduleExactAlarms()` and deep-link to the settings screen if not granted.

### 5.4 Call it from your JS

```javascript
import { registerPlugin } from '@capacitor/core';
const BatchAlarm = registerPlugin('BatchAlarm');

async function scheduleEndAlarm(end) {
  await BatchAlarm.scheduleAlarm({ triggerAtMillis: end.getTime() });
}
```

Call `scheduleEndAlarm(end)` right after you compute `end` in your existing `calculate()` function.

> Note: Capacitor plugins use ES module `import` syntax. If you're keeping a plain `<script>` tag (no bundler), you'll need a build step — see Section 8 (Vite) below.

### 5.5 Simpler fallback

If the native plugin work feels like too much up front, you can ship v1 with standard `@capacitor/local-notifications` (respects silent mode) and upgrade to the alarm-stream approach once the core app is validated. The calculation logic and UI don't change either way — only the final "how does it ring" piece.

---

## 6. Run and Test

Open the native project in Android Studio:

```bash
npx cap open android
```

In Android Studio, hit **Run ▶** with your emulator or a USB-connected phone selected.

Test checklist:
- [ ] App loads and looks correct on a real device screen size
- [ ] Calculate a short test batch (e.g. 1 minute total) and confirm the notification fires on time
- [ ] Confirm the notification still fires when the app is backgrounded
- [ ] Confirm the notification still fires when the screen is off

---

## 7. Iterating During Development

Typical loop while developing:

1. Edit `www/index.html` (or your source files if you add a bundler)
2. Run `npx cap sync`
3. Re-run from Android Studio, or use `npx cap run android` for a faster CLI-based reload

---

## 8. Optional: Add Vite for Real JS Modules

If you want proper `import`/`export`, multiple JS files, and hot-reload during development rather than one big HTML file:

```bash
npm install -D vite
```

Create `vite.config.js`:

```javascript
export default {
  root: 'src',
  build: {
    outDir: '../www',
    emptyOutDir: true
  }
};
```

Move your HTML/JS/CSS into `src/`, run `npx vite build` to output into `www/`, then `npx cap sync` as usual. This is optional — the current single-file prototype works fine without it, but becomes harder to maintain as features grow (history persistence, presets, dark mode from your original README roadmap).

---

## 9. Build a Signed Release APK

When ready to install permanently or share outside the emulator:

1. In Android Studio: **Build → Generate Signed Bundle / APK**
2. Choose **APK**
3. Create a new **keystore** the first time — store the `.jks` file and its passwords somewhere safe. You'll need the *same* keystore for every future update to this app; losing it means you can't publish updates under the same app identity.
4. Choose **release** build variant, finish the wizard
5. Find the signed APK under `android/app/release/`
6. Transfer to your phone (USB, email, cloud link) and install directly, or upload to Google Play Console if you want it distributed via the Play Store

---

## 10. Useful Commands Reference

| Command | Purpose |
|---|---|
| `npx cap sync` | Push `www/` changes + plugin config into native project |
| `npx cap open android` | Open project in Android Studio |
| `npx cap run android` | Build + run on connected device/emulator from CLI |
| `npx cap update` | Update native platform after upgrading Capacitor packages |

---

## 11. Roadmap Alignment

Matches the phases from the original README:

- **Phase 1 (done)** — web prototype with core calculation logic
- **Phase 2** — this guide covers wrapping it into Android; batch history persistence can use `@capacitor/preferences` (simple key-value storage) instead of in-memory JS
- **Phase 3** — presets and dark mode are pure front-end additions to `www/index.html`, no native changes needed