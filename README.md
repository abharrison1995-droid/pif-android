# Infinite Fusion for Android (personal build)

A native Android build of the [mkxp-z](https://github.com/mkxp-z/mkxp-z) engine that
Pokémon Infinite Fusion runs on, set up for handhelds with built-in controls
(tested target: Mangmi Air X). Based on
[thehatkid/mkxp-z-android](https://github.com/thehatkid/mkxp-z-android).

Personal use only. The APK contains only the engine; game files are copied to the
device separately and are never committed or published here.

## What's different from the upstream port

- **No on-screen joypad.** Physical buttons, D-pad (button or hat type) and the left
  stick are mapped directly (`ControllerMapper.java`).
- **Optimised build.** Engine, Ruby and libraries are compiled at `-O2` for 64-bit ARM
  (upstream builds at `-O0` / debug).
- **Saves next to the game** in `InfiniteFusion/Saves/`, so they survive reinstalls.
- **Android compatibility script** bundled in the APK (`app/src/main/assets/pif/android.rb`),
  applied without editing any game files.

## Device setup

1. Install the APK from this repo's Releases (or via Obtainium, see below).
2. Copy the game folder (the one containing `Game.ini`, `Data/`, `Graphics/`, `Audio/`)
   to the device's internal storage as `InfiniteFusion/`, i.e.
   `/storage/emulated/0/InfiniteFusion/Game.ini`.
3. Launch **Infinite Fusion** and grant "All files access" when asked.

## Controls

| Button | Game action |
|---|---|
| A (bottom) | Confirm / interact |
| B (right) | Back / open menu |
| X (left) | Run (hold), sort, Mega |
| Y (top) | Registered item / shift |
| L1 / R1 | Page up / page down |
| Start | Pause menu |
| D-pad / left stick | Move |

To change these, put a `controller.json` in the `InfiniteFusion/` folder:

```json
{
  "swapConfirmCancel": false,
  "stickDeadzone": 0.5,
  "buttons": { "BUTTON_R2": "F", "BUTTON_SELECT": "NONE" }
}
```

Button names are Android `KEYCODE_*` names without the prefix.

## Updates via Obtainium

Add this repo in [Obtainium](https://github.com/ImranR98/Obtainium) with a GitHub
personal access token (needed because the repo is private). New tags (`v*`) build and
publish a signed APK automatically.

## Building locally

Requirements: JDK 17, Android SDK with NDK 25.2.9519653 and CMake 3.22.1, a host
Ruby 3.1.0 on `PATH`, autoconf/automake/bison.

```bash
cd app/jni
bash get_deps.sh
ANDROID_NDK_HOME=$HOME/Android/Sdk/ndk/25.2.9519653 ARCH=linux-x86_64 make all
cd ../..
./gradlew assembleRelease
```

The release keystore is read from `~/.pif-android-keys/` locally and from repo secrets on CI.
