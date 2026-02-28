# Running the High-Performance System Server

The `server.jar` you built using `build.bat` is not a standard Android application. It is a raw Java payload designed to run with system-level privileges. 

To achieve the near-zero latency video and instant touch injection of Xiaomi PC connect, it must be launched via the Android `app_process` command.

## Instructions

1. **Connect your phone** via USB (ensure USB Debugging is enabled).
2. **Push the server** to a temporary directory on the phone:
   ```bash
   adb push server\server.jar /data/local/tmp/
   ```
3. **Execute the server** as the shell user:
   ```bash
   adb shell CLASSPATH=/data/local/tmp/server.jar app_process / com.projector.Server
   ```

*(Note: The server will now start running on the phone and listening on port 8080. Leave this terminal window open.)*

4. **Forward the TCP ports** so your PC can access the server locally:
   ```bash
   adb forward tcp:8080 tcp:8080
   ```
5. **Run the Windows Client** (`python main.py` or the compiled `.exe`). It will connect instantly to `localhost:8080` and you will have OEM level control of your device!
