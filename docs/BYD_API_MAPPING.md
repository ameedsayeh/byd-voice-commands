# BYD API mapping (mined from diplus 1.3.8 command table + executor)

Source: `decompiled/diplus_jadx` — `cmd/f.java` (command name -> opcode), `service/c1.java` (opcode -> facade),
`byd/c.java` + `z/*.java` (facade -> `android.hardware.bydauto.*` calls), `z/d.java` + lambdas (feature IDs).

| Feature | Call | Values |
|---|---|---|
| AC off | `AcDevice.stop(src)` | src fallback `0` |
| Auto AC | `AcDevice.setAcControlMode(0, m)` | `0` auto, `1` manual |
| Temperature | `AcDevice.setAcTemperature(0, t, 0, unit)` | unit `getTemperatureUnit()`: `0` °F (64–91), `1` °C (17–33) |
| Current set-point / outside temp | `AcDevice.getTemprature(1)` / `getTemprature(4)` | |
| Fan speed | `AcDevice.setAcWindLevel(0, n)` | `0..7` |
| Air direction | `AcDevice.setAcWindMode(0, m)` | 1 face, 2 face+feet, 3 feet, 4 feet+windshield, 5 windshield, 6 all, 7 face+windshield |
| Recirculation | `AcDevice.setAcCycleMode(0, v)` | `1` inside, `0` outside |
| Windshield fan / mirror heat | `AcDevice.setAcDefrostState(0, which, on)` | which `1` windshield, `2` mirrors |
| Cabin ventilation | `AcDevice.setAcVentilationState(0, on)` | |
| Seat heat / vent | `SettingDevice.setSeatHeatingState(seat, lvl)` / `setSeatVentilatingState` | seat `1` driver `2` passenger; lvl `1` off `2` low `3` high |
| Steering-wheel heat | `SettingDevice.setSteeringWheelHeatingState(v)` | `2` on, `1` off |
| Mirror fold on lock | `SettingDevice.setAutoExternalRearMirrorFollowUpSwitch(v)` | `1`/`0` |
| Trunk | `SettingDevice.voiceCtlBackDoor(v)` | `1` open, `3` close (`2` partial = provisional) |
| Windows | `BodyworkDevice.setAllWindowState(FL, FR, RL, RR)` | `0` keep, `1` open, `2` close, `3` stop, `4` half, `5` vent |
| Window/roof/shade % | open/close + `onWindowOpenPercentChanged(area,%)` then `3` stop | area 1–4 windows, 5 roof, 6 shade |
| Sunshade | `BodyworkDevice.setSunshadeState(percent)` | `0..100` |
| Roof | `BodyworkDevice.setMoonRoofState(code)` | window codes |
| DRL | `LightDevice.setDayTimeLightState(v)` | `1` on, `2` off |
| Ambient light | `SettingDevice.setIALBrightness(3, level, 0)` | `5` on, `0` off |
| Hazards | `LightDevice.set([Light.LIGHT_TURN_SIGNAL_LIGHT_SET], intValue)` | `1` on, `0` off (fallback id 871366669) |
| Fog lights | `LightDevice.set([LIGHT_FRONT/REAR_FOG_LIGHT_SET])` | `1` on, `2` off |
| Cabin light | generic `set(1023, SET_INSIDE_LIGHT_STATE_SET, v)` | `2` on, `1` off (fallback 1330643002) |
| HUD | generic `set(1023, SET_HUD_SWITCH_SET, v)` | `1` on, `2` off (fallback 1276174371) |
| Auto high beam | generic `set(1004, ADAS_HMA_STATE_SET, v)` | `1` on, `0` off (fallback 944767008) |
| Drive mode | `EnergyDevice.setOperationMode(m)` | ECO 1, SPORT 2, NORMAL 3, SNOW 4 |
| Regen | `SettingDevice.setEnergyFeedback(v)` | `1` standard, `2` strong |
| Steering feel | `SettingDevice.setSteerAssis(v)` | `1` comfort, `2` sport |
| Panorama view | broadcast `android.intent.action.AUTO_VIDEO_BUTTON`, extra `android.intent.extra.KEY_EVENT` | 3001 front, 3002 rear, 3003 left, 3004 right |
| Generic set | `AbsBYDAutoDevice.set(deviceType, featureId, value)` via subclass | |

Not found in diplus (implemented as runtime-probed / provisional): door lock/unlock (diplus uses a privileged
helper service), fold mirrors *now*, wiper service position, ambient colour, inside temperature, split screen,
screen brightness presets, light/dark theme (diplus uses a hidden `UiModeManager` method).
