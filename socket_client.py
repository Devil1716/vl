import socket
import threading
import queue
import time
import struct

class SystemSocketAdapter:
    """Connects directly to the Android phone's IP over Wi-Fi (no ADB)."""

    def __init__(self, ip, video_port=8080, input_port=8081):
        self.ip = ip
        self.video_port = video_port
        self.input_port = input_port
        self.video_sock = None
        self.input_sock = None
        self.running = False
        self.video_q = queue.Queue(maxsize=100)

    def start(self):
        self.running = True
        threading.Thread(target=self._connect_video, daemon=True).start()
        threading.Thread(target=self._connect_input, daemon=True).start()

    def _connect_video(self):
        while self.running:
            try:
                self.video_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                print(f"Connecting video to {self.ip}:{self.video_port}...")
                self.video_sock.connect((self.ip, self.video_port))
                print("Video connected!")
                self._read_video_stream()
                break
            except Exception as e:
                print(f"Video connection failed, retrying... ({e})")
                time.sleep(1)

    def _connect_input(self):
        while self.running:
            try:
                self.input_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                print(f"Connecting input to {self.ip}:{self.input_port}...")
                self.input_sock.connect((self.ip, self.input_port))
                print("Input connected!")
                break
            except Exception as e:
                print(f"Input connection failed, retrying... ({e})")
                time.sleep(1)

    def _read_video_stream(self):
        try:
            while self.running and self.video_sock:
                # Read 4-byte length prefix
                header = self._recv_exact(self.video_sock, 4)
                if not header:
                    break
                frame_size = struct.unpack(">I", header)[0]

                # Read the frame data
                frame_data = self._recv_exact(self.video_sock, frame_size)
                if not frame_data:
                    break

                if not self.video_q.full():
                    self.video_q.put(frame_data)
        except Exception as e:
            print(f"Video stream closed: {e}")
        finally:
            self.stop()

    def _recv_exact(self, sock, n):
        data = b""
        while len(data) < n:
            chunk = sock.recv(n - len(data))
            if not chunk:
                return None
            data += chunk
        return data

    def get_data(self):
        try:
            return self.video_q.get_nowait()
        except queue.Empty:
            return None

    def send_control(self, json_payload):
        if self.input_sock and self.running:
            try:
                self.input_sock.sendall((json_payload + "\n").encode('utf-8'))
            except Exception as e:
                print(f"Failed to send input: {e}")

    def stop(self):
        self.running = False
        for s in [self.video_sock, self.input_sock]:
            if s:
                try:
                    s.close()
                except:
                    pass
