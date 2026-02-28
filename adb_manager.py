import os
import sys
import subprocess
import time

class AdbManager:
    def __init__(self):
        # Determine the base path so it works both in dev and when compiled by PyInstaller
        if getattr(sys, 'frozen', False):
            base_path = sys._MEIPASS
        else:
            base_path = os.path.dirname(os.path.abspath(__file__))
            
        self.adb_path = os.path.join(base_path, "adb_bin", "adb.exe")
        self.server_jar_path = os.path.join(base_path, "server", "server.jar")
        
        self.daemon_process = None

    def _run_cmd(self, args, timeout=10):
        cmd = [self.adb_path] + args
        try:
            # Hide console window on Windows
            creationflags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
            result = subprocess.run(
                cmd, capture_output=True, text=True, timeout=timeout, creationflags=creationflags
            )
            return True, result.stdout + result.stderr
        except subprocess.TimeoutExpired:
            return False, "Command timed out"
        except Exception as e:
            return False, str(e)

    def connect(self, ip_port):
        success, out = self._run_cmd(["connect", ip_port])
        if "connected to " in out.lower() or "already connected" in out.lower():
            return True, "Connected successfully"
        return False, f"Failed to connect: {out}"

    def push_server(self):
        success, out = self._run_cmd(["push", self.server_jar_path, "/data/local/tmp/"])
        if success and "error" not in out.lower() and "fail" not in out.lower():
            return True, "Server pushed successfully"
        return False, f"Failed to push server: {out}"

    def start_server_daemon(self):
        # We start this async and don't wait for it to finish because it's a daemon
        cmd = [self.adb_path, "shell", "CLASSPATH=/data/local/tmp/server.jar", "app_process", "/", "com.projector.Server"]
        try:
            creationflags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
            self.daemon_process = subprocess.Popen(
                cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, creationflags=creationflags
            )
            time.sleep(1) # Give it a second to bind the socket
            return True, "Daemon started"
        except Exception as e:
            return False, str(e)

    def forward_port(self, port=8080):
        success, out = self._run_cmd(["forward", f"tcp:{port}", f"tcp:{port}"])
        if success:
            return True, f"Port {port} forwarded"
        return False, f"Failed to forward port: {out}"
        
    def cleanup(self):
        if self.daemon_process:
            self.daemon_process.terminate()
        # Optionally disconnect
        # self._run_cmd(["disconnect"])
