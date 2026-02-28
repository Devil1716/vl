# Projector — Seamless Phone Mirroring

Mirror and control your Android phone from your Windows laptop, just like Xiaomi PC Connect.

**No ADB. No terminals. No IP addresses.** Just install both apps and they find each other automatically.

## How It Works

```
Android Phone (Wi-Fi)  ←→  Windows Laptop (Wi-Fi)
     📱 Projector App         💻 ADB Projector.exe
```

Both apps are on the same Wi-Fi network. The phone broadcasts "I'm here!" every 2 seconds. The PC listens, discovers it, and you click Connect. Done.

## Setup (One-Time)

### Phone
1. Build the Android app from the `projector-android/` source using Android Studio, OR sideload the APK.
2. Open **Projector** on your phone.
3. Tap **"Enable Accessibility"** and turn on the Projector service (this enables touch control from PC).
4. Tap **"Start Sharing"** and grant Screen Recording permission.

### PC
1. Download this repository as a ZIP and extract it.
2. Open `dist\ADB Projector\` and run **`ADB Projector.exe`**.
3. Your phone will appear automatically in the device list.
4. Click your phone → Click **Connect**.
5. Your phone screen appears as a sleek floating window! 🎉

## Controls
- **Click** on the floating phone to tap
- **Click & drag** to swipe
- **Drag the black border** to move the window
- **Press ESC** to close

## Project Structure
```
projector/              ← Windows client (Python + PyQt6)
├── main.py             ← Entry point
├── discovery.py        ← UDP auto-discovery listener
├── connection_ui.py    ← Device picker UI
├── socket_client.py    ← Wi-Fi TCP video + input
├── decoder.py          ← H.264 video decoder
├── input_mapper.py     ← Touch coordinate mapper
└── dist/               ← Pre-built Windows .exe

projector-android/      ← Android companion app (Kotlin)
└── app/src/main/java/com/projector/companion/
    ├── MainActivity.kt
    ├── DiscoveryService.kt
    ├── ScreenCaptureService.kt
    └── InputAccessibilityService.kt
```
