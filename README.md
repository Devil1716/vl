# High-Performance Android Projector

A zero-latency, OEM-level Android screen mirroring and remote control application for Windows.

Unlike standard screen mirroring apps that rely on slow casting protocols or accessibility services, this tool utilizes **Deep System Optimization**. By pushing a raw Java Daemon (`.jar`) directly into the Android `app_process`, it achieves:
* **Zero-Latency Video**: Hooks directly into hidden `android.view.SurfaceControl` buffers.
* **Instant Touch Injection**: Injects mouse drag and click events natively into the `InputManager` (~2ms response time).
* **Fully Wireless, Frameless UI**: Your phone is mirrored as a transparent, sleek floating window on your PC.

## How to Install and Use

We've automated the entire connection process so you never have to open a terminal. The ADB binaries are bundled directly into the application.

### 1. Requirements
* A PC running Windows.
* An Android device with [Wireless Debugging Enabled](https://developer.android.com/studio/debug/dev-options) on the same Wi-Fi network as the PC.

### 2. Download the App
1. Go to the [Releases](https://github.com/Devil1716/vl/releases) page or click the **Code > Download ZIP** button above.
2. Extract the folder to your desktop.

### 3. Connect Wireless
1. Open your Android phone's **Settings > Developer Options > Wireless Debugging**.
2. Note the IP Address and Port (e.g., `192.168.1.100:5555`).
3. On your PC, navigate into the extracted `dist\ADB Projector\` folder and double click **`ADB Projector.exe`**.
4. Type your Phone's IP and Port into the connection wizard and click **Connect**.

The application will handle everything in the background—pushing the server to your phone, starting the system daemon, and hooking the ports. When it reaches 100%, the sleek floating phone will appear on your desktop!

*(Press `ESC` to close the mirroring window)*
