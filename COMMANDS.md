# Re:T-UI Launcher Development & Deployment Guide

This document summarizes the essential commands for building, installing, and managing the Re:T-UI Linux CLI Launcher fork.

## 🛠 Building the APK

To perform a clean build of the F-Droid version (includes SMS permissions):

```bash
# Ensure gradlew is executable
chmod +x gradlew

# Build the F-Droid Debug APK
./gradlew assembleFdroidDebug
```

**Output Path:** `app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk`

---

## 📱 Emulator Deployment (ADB)

To install the APK on your Pixel 9 Pro emulator (or any physical device connected via USB):

```bash
# 1. Start the emulator (if not already running)
emulator -avd Pixel_9_Pro -gpu host -accel on &

# 2. Wait for the device and install (overwriting existing)
adb wait-for-device
adb install -r app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk
```

---

## 🐧 Linux Tooling

Re:T-UI no longer downloads BusyBox. Use Android's built-in shell for small explicit commands and Termux for maintained Linux packages, scripts, and modules.

```bash
# Lightweight local command
shell pwd

# Full Linux workflow through Termux
termux -setup
termux -run /data/data/com.termux/files/home/retui/script.sh
module -add server termux:/data/data/com.termux/files/home/retui/server-health.sh
```

### Re:T-UI FM handoff

The Launcher owns parsing for the public `files` command and sends structured requests to Re:T-UI FM:

```bash
files
files -search note txt
files -open Download
files -open "Download/My Folder"
files note txt
```

The final form is the temporary legacy alias for `files -search note txt`. Relative open paths resolve against the Launcher current directory. File mutations such as ZIP, move, copy, trash, and share remain owned by FM's long-press selection UI.

---

## 🔍 Useful ADB Debugging Commands

```bash
# View real-time logs (filtered for Re:T-UI)
adb logcat | grep ohi.andre

# Uninstall the launcher via ADB
adb uninstall ohi.andre.consolelauncher

# Push a file to the launcher's internal storage
adb push local_file.txt /data/user/0/ohi.andre.consolelauncher/files/
```

---

## 🛡 Security Note
Re:T-UI no longer downloads third-party runtime binaries for shell support. Linux package ownership belongs in Termux, where users can update and audit their own tools.
