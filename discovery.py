import socket
import threading

class DeviceDiscovery:
    """Listens for UDP beacons from Projector Android apps on the local network."""

    def __init__(self, port=9999):
        self.port = port
        self.running = False
        self.devices = {}  # ip -> device_name
        self.on_device_found = None  # callback
        self._lock = threading.Lock()

    def start(self):
        self.running = True
        self.thread = threading.Thread(target=self._listen, daemon=True)
        self.thread.start()

    def _listen(self):
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(("", self.port))
        sock.settimeout(1.0)

        while self.running:
            try:
                data, addr = sock.recvfrom(1024)
                message = data.decode("utf-8")
                
                if message.startswith("PROJECTOR_BEACON|"):
                    parts = message.split("|")
                    if len(parts) >= 3:
                        device_name = parts[1]
                        device_ip = parts[2]
                        
                        with self._lock:
                            is_new = device_ip not in self.devices
                            self.devices[device_ip] = device_name
                        
                        if is_new and self.on_device_found:
                            self.on_device_found(device_name, device_ip)
            except socket.timeout:
                continue
            except Exception as e:
                if self.running:
                    print(f"Discovery error: {e}")

        sock.close()

    def get_devices(self):
        with self._lock:
            return dict(self.devices)

    def stop(self):
        self.running = False
