# Server-Side Optimization (Java/Kotlin JAR)

While standard ADB commands (`screenrecord` and `input tap/swipe`) are functional, they introduce latency because:
1. `screenrecord` buffers data and isn't optimized for zero-latency streaming.
2. `input tap / swipe` launches a new Dalton/ART VM instance on every call, taking >100ms per injection.

To achieve near-zero latency like **scrcpy**, you must bypass standard shell utilities by deploying a small Java Server (`.jar` / `.dex`) to the Android device. 

## The Optimization Approach

### 1. Build a Server JAR
Write a lightweight Java or Kotlin application containing a `public static void main(String[] args)` method. Compile it into an Android `.dex` format using the Android build tools (e.g., `d8`).

### 2. Push and Execute via App_Process
Push the `.jar` to a temporary directory on the phone:
```bash
adb push server.jar /data/local/tmp/
```
Start the Java process directly on the Android device using `app_process` with "shell" privileges. This grants it the exact same permissions as the ADB shell:
```bash
adb shell CLASSPATH=/data/local/tmp/server.jar app_process / com.example.projector.Server
```

### 3. Screen Encoding Optimization
Inside your server's `main` method:
*   Use internal Android APIs (like `android.view.SurfaceControl` and `android.media.projection.MediaProjection`) to capture the screen directly.
*   Pipe the screen output to a `MediaCodec` instance configured with hardware acceleration (`MIMETYPE_VIDEO_AVC`, i.e., H.264) and minimal buffering/latency presets.
*   Establish a `LocalSocket` via `adb forward` on the PC:
    ```bash
    adb forward tcp:8080 localabstract:projector
    ```
    Your Java server reads raw byte output from the `MediaCodec` and flushes it seamlessly over the reverse/local socket to your Python backend.

### 4. Input Injection Optimization
Instead of launching `input tap` from bash, manage input entirely inside the Java server:
*   Listen on the same socket (or a control socket) for custom binary packets from your Python application (e.g., 2 bytes for action type, 4 bytes each for X/Y coordinates).
*   Use Android's internal `InputManager` or `IWindowManager` APIs to inject `MotionEvent` objects. 
*   **Result**: Injection drops from >100ms per tap to <2ms, allowing for high framerate dragging and touch tracking.

## Summary 
By running a continuous server JAR over a LocalSocket, your architecture shifts from repeated process execution to a single continuous bidirectional pipe—making 60fps streaming and instant input possible.
