import subprocess
import threading

class InputMapper:
    def __init__(self, serial=None, phone_width=720, phone_height=1280):
        self.serial = serial
        self.phone_width = phone_width
        self.phone_height = phone_height
        self.event_queue = []
        self.lock = threading.Lock()
        self.running = True
        
        self.executor = threading.Thread(target=self._worker, daemon=True)
        self.executor.start()
        
    def _worker(self):
        while self.running:
            cmd = None
            with self.lock:
                if self.event_queue:
                    cmd = self.event_queue.pop(0)
            if cmd:
                # Use sub-process to prevent blocking the worker
                subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            else:
                threading.Event().wait(0.01)
                
    def send_tap(self, ui_x, ui_y, ui_width, ui_height):
        px = int((ui_x / ui_width) * self.phone_width)
        py = int((ui_y / ui_height) * self.phone_height)
        
        base_cmd = ["adb"]
        if self.serial:
            base_cmd.extend(["-s", self.serial])
            
        cmd = base_cmd + ["shell", "input", "tap", str(px), str(py)]
        with self.lock:
            self.event_queue.append(cmd)

    def send_swipe(self, start_x, start_y, end_x, end_y, ui_width, ui_height, duration_ms=300):
        px1 = int((start_x / ui_width) * self.phone_width)
        py1 = int((start_y / ui_height) * self.phone_height)
        px2 = int((end_x / ui_width) * self.phone_width)
        py2 = int((end_y / ui_height) * self.phone_height)
        
        base_cmd = ["adb"]
        if self.serial:
            base_cmd.extend(["-s", self.serial])
            
        cmd = base_cmd + ["shell", "input", "swipe", str(px1), str(py1), str(px2), str(py2), str(duration_ms)]
        with self.lock:
            self.event_queue.append(cmd)

    def stop(self):
        self.running = False
