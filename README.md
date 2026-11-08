# BYD Voice Commands

English voice control for BYD cars with the DiLink Android head unit. Press the steering‑wheel
mic button, say what you want — "make it warmer", "open all the windows", "turn on the driver
seat heating" — and the car does it. Speech is recognised **on the head unit, in English**.

<p align="center">
  <img src="docs/screenshots/home.png" width="720" alt="Home screen">
</p>

## What you need — and what you don't

- ✅ A BYD car with the **DiLink** head unit, with Developer options / ADB you can switch on.
- ❌ **No Di+. No root. No other app.** The app sets up its own steering‑wheel button and
  background start‑up — nothing else to install.
- 🎙️ **The mic is off until you ask.** It's push‑to‑talk on the wheel button, so it is *not*
  listening all the time. (Optional "Hi BYD" hands‑free if you ever want it.)
- 📶 Runs offline. Only a few commands (windows, doors, mirrors…) need the car to be online.

## Install

Pick one. Both just put the APK — `app-car-release.apk` — onto the head unit.

### A · Manually, on the head unit

1. Put `app-car-release.apk` on a USB stick and plug it into the car (or download it on the unit).
2. Open the head unit's **file manager**, tap the APK.
3. If asked, allow **install from unknown sources**, then tap **Install**.

> _Screenshot: file manager → tap APK → Install_

### B · Over ADB, from a computer

1. On the head unit: **Settings → Developer options → enable USB/ADB debugging** (wireless).
2. On your computer (with the APK in the current folder):

   ```bash
   adb connect <head-unit-ip>:5555     # e.g. adb connect 172.20.10.2:5555
   adb install -r app-car-release.apk
   ```

> _Screenshot: terminal showing `Success`_

## First run — one‑time setup

1. Open **BYD Voice Commands**. Allow **Microphone** and **Notifications** when prompted.
2. In **Controls**, tap **Set up advanced control**. On the car screen, tick **Always allow** and
   **OK** on the *"Allow USB debugging?"* dialog.
   - That's it. The app grants itself the **wheel button** and **background auto‑start** by talking
     to the car's own ADB — **the job Di+ used to do, done by the app itself.**
3. Press the steering‑wheel mic button and speak.

> _Screenshot: Controls → "Set up advanced control" → the Allow USB debugging dialog_

<p align="center">
  <img src="docs/screenshots/command-done.png" width="520" alt="A command running">
</p>

## Using it

- **Press the wheel mic button** → "Say a command…" → speak in English.
- Or tap a big button on the **Quick grid**.
- The app **starts with the car and stays in the background**; the mic only opens when you press
  the button. Nothing runs or listens until then.

## What it can do

120+ commands across climate, air flow, seats, steering & mirrors, doors & trunk, windows, sun
shade & roof, lights, cameras, driving modes and screen — plus "open ‹any installed app›". Say it
your way; it matches on meaning, not fixed phrases.

## Build it yourself (optional)

JDK 17 + Android SDK (platform 34).

```bash
./gradlew :app:assembleCarRelease
# -> app/build/outputs/apk/car/release/app-car-release.apk  (~140 MB, speech model bundled)
```

---

<sub>Under the hood it hands each command to the car's built‑in assistant (which holds the vehicle
permissions) and silences its reply — so there's nothing else to install or sign. Speech:
[Moonshine](https://github.com/moonshine-ai/moonshine) (MIT). BYD logo © BYD Co. Ltd. Independent
project, not affiliated with BYD — it drives real vehicle systems, so use it responsibly and never
in a way that distracts you from driving.</sub>
