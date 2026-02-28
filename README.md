# High-Performance Android Projector

A zero-latency, OEM-level Android screen mirroring and remote control application for Windows.

Unlike standard screen mirroring apps that rely on slow casting protocols or accessibility services, this tool utilizes **Deep System Optimization**. By pushing a raw Java Daemon (`.jar`) directly into the Android `app_process`, it achieves:
* **Zero-Latency Video**: Hooks directly into hidden `android.view.SurfaceControl` buffers.
* **Instant Touch Injection**: Injects mouse drag and click events natively into the `InputManager` (~2ms response time).

## How to Install and Use

Because this tool taps into hidden OS APIs, it cannot be run as a standard downloaded Android App. You must launch the server via ADB.

### 1. Requirements
* A PC running Windows.
* An Android device with [USB Debugging Enabled](https://developer.android.com/studio/debug/dev-options) connected via USB.
* [ADB (Android Debug Bridge)](https://developer.android.com/tools/adb) installed on your PC.

### 2. Download the App
1. Go to the [Releases](https://github.com/Devil1716/vl/releases) page or click the **Code > Download ZIP** button above.
2. Extract the folder to your desktop.

### 3. Start the Server (Do this once per reboot)
Open a terminal in the extracted folder and push the provided `.jar` server to your phone:
```bash
adb push server\server.jar /data/local/tmp/
```
Start the high-performance daemon on the phone:
```bash
adb shell CLASSPATH=/data/local/tmp/server.jar app_process / com.projector.Server
```
*(Leave this terminal window open!)*

### 4. Connect and Play
Open a second terminal window and tell ADB to forward the socket so your PC can communicate with the daemon:
```bash
adb forward tcp:8080 tcp:8080
```
Finally, navigate to `dist\ADB Projector\` and double-click **`ADB Projector.exe`**.

Your phone screen will instantly appear on your PC, and you can control it with your mouse!
