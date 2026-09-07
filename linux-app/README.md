# SO Analyzer - Linux Desktop & Android Project

This package contains both the **Linux Native Desktop Application** and instructions for developing and running **SO Analyzer** on Linux.

---

## 1. Running the Linux Desktop App (Quick Start)

### Option A: Run Directly (No Install Needed)
```bash
cd linux-app
chmod +x run.sh so_analyzer_gui.py
./run.sh
```

### Option B: Install into Your Linux Applications Menu
To make SO Analyzer appear in your system Application Menu (GNOME, KDE, XFCE):
```bash
cd linux-app
chmod +x install.sh
./install.sh
```
After running this, search for **SO Analyzer** in your system launcher or run `so-analyzer` from your terminal.

---

## 2. Dependencies
- **Python 3.6+** (Pre-installed on almost all Linux distributions)
- **Tkinter**:
  - Ubuntu / Debian / Linux Mint: `sudo apt install python3-tk`
  - Fedora / RHEL: `sudo dnf install python3-tkinter`
  - Arch Linux: `sudo pacman -S tk`

---

## 3. Editing and Developing on Linux

### Editing the Linux Desktop App
The Linux app is contained in `linux-app/so_analyzer_gui.py`.
You can open and edit it with any code editor:
- VS Code: `code linux-app/so_analyzer_gui.py`
- PyCharm, Sublime Text, or Nano/Vim: `nano linux-app/so_analyzer_gui.py`

### Editing the Android App on Linux
The root directory contains the full **Android Kotlin Jetpack Compose** project:
1. Open **Android Studio** on Linux.
2. Select **Open Project** and choose this project folder.
3. Android Studio will automatically sync Gradle and let you edit Kotlin Compose files in `app/src/main/java/com/example/`.
4. To build the APK from your Linux terminal:
   ```bash
   ./gradlew assembleDebug
   ```
   The built APK will be located at:
   `app/build/outputs/apk/debug/app-debug.apk`

---

## 4. Running the Android APK Natively on Linux
You can also run the Android `.apk` directly inside Linux at native speed:
- **Waydroid** (Recommended for Ubuntu/Fedora/Arch with Wayland):
  ```bash
  waydroid app install app/build/outputs/apk/debug/app-debug.apk
  ```
- **Android Studio Virtual Device (AVD)**:
  Run the emulator directly inside Android Studio on Linux.
