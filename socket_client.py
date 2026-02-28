import socket
import threading
import queue
import time

class SystemSocketAdapter:
    def __init__(self, port=8080):
        self.ip = "127.0.0.1"
        self.port = port
        self.sock = None
        self.running = False
        self.video_q = queue.Queue(maxsize=100)
    
    def start(self):
        self.running = True
        
        while self.running and not self.sock:
            try:
                self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                print(f"Connecting to System Server at {self.ip}:{self.port}...")
                self.sock.connect((self.ip, self.port))
                print("Connected! Receiving video and enabling control.")
            except Exception as e:
                time.sleep(1)

        threading.Thread(target=self._read_video_stream, daemon=True).start()

    def _read_video_stream(self):
        try:
            chunk_size = 8192
            while self.running and self.sock:
                data = self.sock.recv(chunk_size)
                if not data:
                    break
                if not self.video_q.full():
                    self.video_q.put(data)
        except Exception as e:
            print(f"Video stream closed: {e}")
        finally:
            self.stop()
                
    def get_data(self):
        try:
            return self.video_q.get_nowait()
        except queue.Empty:
            return None

    def send_control(self, json_payload):
        if self.sock and self.running:
            try:
                self.sock.sendall((json_payload + "\n").encode('utf-8'))
            except Exception as e:
                print(f"Failed to send input: {e}")

    def stop(self):
        self.running = False
        if self.sock:
            try:
                self.sock.close()
            except:
                pass
