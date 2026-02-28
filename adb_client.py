import subprocess
import threading
import queue

class AdbStreamer:
    def __init__(self, serial=None):
        self.serial = serial
        self.process = None
        self.running = False
        self.q = queue.Queue(maxsize=100)

    def start(self):
        cmd = ["adb"]
        if self.serial:
            cmd.extend(["-s", self.serial])
        
        # Stream raw H.264 from the device. Limits are imposed by screenrecord capabilities.
        cmd.extend(["shell", "screenrecord", "--output-format=h264", "--bit-rate", "2000000", "--size", "720x1280", "-"])
        
        self.process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        self.running = True
        
        threading.Thread(target=self._read_stream, daemon=True).start()

    def _read_stream(self):
        chunk_size = 4096
        while self.running and self.process.poll() is None:
            try:
                data = self.process.stdout.read(chunk_size)
                if not data:
                    break
                if not self.q.full():
                    self.q.put(data)
            except Exception as e:
                print(f"Streamer read error: {e}")
                break
                
    def get_data(self):
        try:
            return self.q.get_nowait()
        except queue.Empty:
            return None

    def stop(self):
        self.running = False
        if self.process:
            self.process.terminate()
