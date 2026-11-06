# BYD Voice Commands

English voice control for BYD cars with the DiLink Android head unit. Press the steering‑wheel
button, say what you want in your own words ("make it a bit warmer", "open all the windows",
"turn on the driver seat heating"), and the car does it — with the command recognised entirely
on the head unit.

<p align="center">
  <img src="docs/screenshots/home.png" width="720" alt="Home screen on a tablet-sized head unit">
</p>

## Why this exists

DiLink head units ship with a capable voice assistant — but it only understands Chinese. This app
puts an English front end on the same car functions: you speak English, it recognises the command
on‑device, and it drives the car.

## How it controls the car

This is the interesting part. On a **stock, unrooted** DiLink unit a sideloaded app **cannot** use
the vehicle API directly: the read/write permissions (`android.permission.BYDAUTO_*_SET/GET`) are
`signature`‑level, granted only to apps signed with BYD's platform key. A debug‑signed APK is
refused every call, and there is no dialog or `pm grant` that changes it.

So instead of calling the car API itself, the app **hands the command to the car's own voice
assistant** (`com.byd.autovoice`), which *is* platform‑signed and holds those permissions:

```
wheel button ─▶ mic ─▶ Moonshine Small (on-device English speech-to-text)
                         ▼
                 command matcher  (English → VehicleAction)
                         ▼
                 AssistantPhrases (VehicleAction → the assistant's Chinese phrase)
                         ▼
                 BydAssistant ──▶ com.byd.autovoice automation service ──▶ car acts
```

1. **Speech → text.** [Moonshine](https://github.com/moonshine-ai/moonshine) Small streaming
   (123M params, MIT) runs on the head unit's CPU through ONNX Runtime. Fully offline.
2. **Text → command.** A synonym lexicon + number parser + fuzzy matching turns the English into a
   structured `VehicleAction`, tolerating mishearings ("hazzard", "Fanspeed for").
3. **Command → the assistant.** `AssistantPhrases` maps the action to the exact Chinese phrase the
   assistant uses (taken from its own `local_command_code.xml`), and `BydAssistant` feeds it to the
   assistant's exported automation service (`AutomataTestService.c3`). The assistant executes it
   with its platform permissions, and the app silences its spoken Chinese reply.

**Offline vs cloud.** The assistant resolves some commands on‑device and sends the rest to BYD's
cloud:

- **Work offline, always:** AC on/off, AC auto, fan level, front defrost, cabin ventilation,
  recirculation, sunroof (open/close/half/vent), sun shade, driver/passenger seat heating &
  ventilation.
- **Need the car's BYD voice cloud (car online):** windows, trunk, doors, mirrors, lights, ambient
  colour, drive modes, cameras, head‑up display, set‑temperature‑by‑number, warmer/cooler.

For the offline commands the app also dismisses the assistant's pop‑up and silences its spoken
reply right after (they finish in ~300 ms, so this never interrupts them). Cloud commands are left
to run uninterrupted; to silence those too, turn off **语音播报 (voice broadcast)** in the car's
voice‑assistant settings.

**Direct‑API fallback.** On a rooted or platform‑signed unit, the app can also call the vehicle API
directly (`BydController`, mappings in [docs/BYD_API_MAPPING.md](docs/BYD_API_MAPPING.md)); it uses
this automatically when a command has no assistant phrase (e.g. launching an app). Toggle in
settings (`Use BYD assistant`).

## The steering‑wheel button & auto‑start (no Di+)

The native assistant **reserves** the wheel voice button. To own it, the app runs an
`AccessibilityService` with `canRequestFilterKeyEvents` and consumes the key in `onKeyEvent` — the
same mechanism Di+ uses — so the press triggers our listener and the native assistant stays quiet.
That service also **auto‑starts on every boot**, which is what makes the app run in the background
when the car turns on, with no manual launch.

Enabling an accessibility service and whitelisting background run normally need `shell`‑level
access the app can't grant itself. Rather than depend on a third‑party tool (Di+), the app does its
own **self‑setup** — tap **Set up advanced control** in the app:

```
Set up advanced control ─▶ embedded ADB client (dadb)
                            ▼  connect to the car's own adbd
                    localhost:5555   ── you approve once on the car screen
                            ▼  run as the shell user
   pm grant … WRITE_SECURE_SETTINGS
   settings put secure enabled_accessibility_services …/KeyButtonService   (wheel button + auto-start)
   settings put secure accessibility_enabled 1
   dumpsys deviceidle whitelist +com.offline.bydvoice                       (background run)
```

The RSA key is generated once and kept in the app's private storage, so the car authorises this app
a single time. This is in [setup/AdbBootstrap.kt](app/src/main/java/com/offline/bydvoice/setup/AdbBootstrap.kt).
It needs USB/ADB debugging enabled on the head unit, and the `INTERNET` permission — Android guards
every `socket()`, loopback included, behind it; the app makes no outbound network calls.

> It does **not** unlock the signature vehicle API (that still needs BYD's platform key) — car
> control keeps routing through `BydAssistant`. Self‑setup only unlocks the wheel button and
> background auto‑start.

**Di+ fallback.** If you'd rather not grant ADB access, Di+ (迪加) can bind the wheel button to a
run‑shell action instead:

```bash
am start-foreground-service -n com.offline.bydvoice/com.offline.bydvoice.service.VoiceService -a com.offline.bydvoice.action.TRIGGER_LISTEN
```

Either way the button opens the "Say a command…" overlay; speak, and it runs through the pipeline
above.

## Features

- **122 commands** across climate, air flow, seats, steering & mirrors, doors & trunk, windows,
  sun shade & roof, lights, cameras, driving modes and screen, plus "open ‹any installed app›".
- **Say it your way** — meaning‑based matching, not fixed phrases.
- **Numbers and values** — temperatures, fan levels, colours, app names.
- **On‑device English speech** (Moonshine Small) with a live transcript overlay and spoken
  English replies.
- **Push‑to‑talk** via the wheel button, the app, or the notification; optional hands‑free "Hi BYD".
- **Quick grid** of big one‑tap buttons for the most‑used commands (a tab / a Quick‑vs‑All toggle).
- **Pick your commands** (enable/disable per command or category) and **tune the listening**
  (end‑of‑speech pause, timeouts, hands‑free window, mic sensitivity).
- **~140 MB**, speech model included.

| Command done | Settings |
|---|---|
| ![Command done](docs/screenshots/command-done.png) | ![Settings](docs/screenshots/settings.png) |

## Install on the car

1. On the head unit, enable developer options + ADB. From your computer:
   ```bash
   adb connect <head-unit-ip>:5555        # wireless ADB
   adb install -r app-car-release.apk
   ```
2. Open **BYD Voice Commands** and allow Microphone, Notifications, Show over apps, Stay running.
3. **Wheel button + auto‑start:** tap **Set up advanced control** and approve the one‑time ADB
   prompt on the head unit (or use the Di+ fallback above).
4. Test with the ▶ button next to any command, then with your voice.

> This app routes through the car's assistant, so the assistant (`com.byd.autovoice`) must be
> present (it is, on stock DiLink). The wheel button/auto‑start is set up in‑app; Di+ is optional.

### If a command doesn't act

```bash
adb logcat -s VoiceService BydAssistant
```

`VoiceService` logs `via assistant: '<heard>' -> <id> phrase='<chinese>'`. If a cloud command does
nothing, check the car is online. If a phrase is wrong, it's a one‑line fix in
`app/.../byd/AssistantPhrases.kt` (the assistant's own wording is in its `local_command_code.xml`).

## Build

JDK 17 + Android SDK (platform 34).

```bash
./gradlew :voicecore:test           # command-matching tests (plain JVM)
./gradlew :app:assembleCarRelease   # app/build/outputs/apk/car/release/app-car-release.apk
./gradlew :bridge:assembleDebug     # tiny standalone "assistant gate" demo (optional)
```

R8‑shrunk, debug‑signed for sideloading. The Moonshine model is bundled in
`app/src/main/assets/moonshine/small-streaming-en/`.

## Testing without a car

The **mock** flavor stands in for the vehicle API and forwards every call to the **BYD Mock
Service** app (`recorder/`); the `car` flavor's assistant path is a no‑op off a real head unit.

```bash
./gradlew :app:assembleMockDebug :recorder:assembleDebug
adb install -r recorder/build/outputs/apk/debug/recorder-debug.apk
adb install -r app/build/outputs/apk/mock/debug/app-mock-debug.apk
R=com.offline.bydvoice.mock/com.offline.bydvoice.testhook.RunCommandReceiver
adb shell am broadcast -n $R --es text "'open all the windows'"   # text straight to the matcher
adb shell am broadcast -n $R --es id ALL                          # every command once
```

Recognition benchmark (macOS, uses `say`):

```bash
pip install moonshine-voice numpy
python tools/asr_bench.py /tmp/bench
./gradlew :voicecore:nluEval --args="score /tmp/bench/results.tsv"
```

## Project layout

| Module | Contents |
|---|---|
| `app` | The app: speech engine, overlay, Compose UI, `BydAssistant` gate + `AssistantPhrases`, `BydController` fallback. Flavors `car` / `mock`. |
| `voicecore` | Pure Kotlin: lexicon, numbers, fuzzy matching, command catalog, matcher, wake word, tests. |
| `bridge` | A minimal standalone app that demonstrates the assistant gate (`am start … BridgeActivity --es cmd "打开空调"`). |
| `bydstub` | Compile‑only stubs of `android.hardware.bydauto.*` for the direct‑API fallback. |
| `bydmockimpl` / `recorder` | The mock vehicle API and the app that shows its calls. |
| `tools` | Recognition benchmark and the emulator launcher. |

## Credits

- Speech: [Moonshine](https://github.com/moonshine-ai/moonshine) (MIT) on
  [ONNX Runtime](https://onnxruntime.ai) (MIT).
- The BYD logo is a trademark of BYD Company Ltd.; the vector is from
  [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:BYD_Auto_2022_logo.svg).

Independent project, not affiliated with or endorsed by BYD. It drives real vehicle systems — use
it at your own risk, and never in a way that distracts you from driving.
