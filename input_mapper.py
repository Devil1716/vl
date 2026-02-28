import socket
import threading
import json
import time

class InputMapper:
    def __init__(self, ip, phone_width=720, phone_height=1280):
        self.ip = ip
        self.port = 8081
        self.phone_width = phone_width
        self.phone_height = phone_height
        
        self.event_queue = []
        self.lock = threading.Lock()
        self.running = True
        self.sock = None
        
        self.executor = threading.Thread(target=self._worker, daemon=True)
        self.executor.start()
        
    def _worker(self):
        connected = False
        while self.running and not connected:
            try:
                self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                self.sock.connect((self.ip, self.port))
                connected = True
                print("Input socket connected!")
            except Exception as e:
                time.sleep(1)

        while self.running:
            cmd_json = None
            with self.lock:
                if self.event_queue:
                    cmd_json = self.event_queue.pop(0)
            
            if cmd_json:
                try:
                    self.sock.sendall((cmd_json + "\n").encode('utf-8'))
                except Exception as e:
                    print(f"Failed to send input: {e}")
            else:
                time.sleep(0.01)
                
    def send_tap(self, ui_x, ui_y, ui_width, ui_height):
        px = (ui_x / ui_width) * self.phone_width
        py = (ui_y / ui_height) * self.phone_height
        
        cmd = json.dumps({"type": "tap", "x": px, "y": py})
        
        with self.lock:
            self.event_queue.append(cmd)

    def send_swipe(self, start_x, start_y, end_x, end_y, ui_width, ui_height, duration_ms=300):
        px1 = (start_x / ui_width) * self.phone_width
        py1 = (start_y / ui_height) * self.phone_height
        px2 = (end_x / ui_width) * self.phone_width
        py2 = (end_y / ui_height) * self.phone_height
        
        cmd = json.dumps({
            "type": "swipe", 
            "x1": px1, "y1": py1, 
            "x2": px2, "y2": py2, 
            "duration": duration_ms
        })

        with self.lock:
            self.event_queue.append(cmd)

    def stop(self):
        self.running = False
        if self.sock:
            try:
                self.sock.close()
            except:
                pass
