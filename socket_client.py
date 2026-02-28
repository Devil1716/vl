import socket
import threading
import queue
import time

class SocketStreamer:
    def __init__(self, ip):
        self.ip = ip
        self.port = 8080
        self.sock = None
        self.running = False
        self.q = queue.Queue(maxsize=100)
    
    def start(self):
        self.running = True
        threading.Thread(target=self._read_stream, daemon=True).start()

    def _read_stream(self):
        try:
            self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            # Wait for user to launch the Android app service
            print(f"Connecting to video stream at {self.ip}:{self.port}...")
            self.sock.connect((self.ip, self.port))
            print("Video Connected!")
            
            chunk_size = 4096
            while self.running:
                data = self.sock.recv(chunk_size)
                if not data:
                    break
                if not self.q.full():
                    self.q.put(data)
        except Exception as e:
            print(f"Streamer socket error: {e}")
        finally:
            self.stop()
                
    def get_data(self):
        try:
            return self.q.get_nowait()
        except queue.Empty:
            return None

    def stop(self):
        self.running = False
        if self.sock:
            try:
                self.sock.close()
            except:
                pass
