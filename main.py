import sys
import threading
import time
from PyQt6.QtWidgets import QApplication, QLabel, QMainWindow, QVBoxLayout, QWidget, QInputDialog, QMessageBox
from PyQt6.QtGui import QImage, QPixmap
from PyQt6.QtCore import Qt, pyqtSignal, QObject

from socket_client import SocketStreamer
from decoder import H264Decoder
from input_mapper import InputMapper

class VideoStreamThread(QObject):
    frame_ready = pyqtSignal(object)

    def __init__(self, streamer, decoder):
        super().__init__()
        self.streamer = streamer
        self.decoder = decoder
        self.running = True

    def run(self):
        while self.running:
            chunk = self.streamer.get_data()
            if chunk:
                self.decoder.decode_chunk(chunk)
            
            frame = self.decoder.get_frame()
            if frame is not None:
                self.frame_ready.emit(frame)
            else:
                time.sleep(0.005)

    def stop(self):
        self.running = False

class ProjectorWindow(QMainWindow):
    def __init__(self, phone_ip):
        super().__init__()
        self.setWindowTitle(f"Wireless Projector ({phone_ip})")
        self.resize(360, 640)

        self.video_label = QLabel(self)
        self.video_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.video_label.setStyleSheet("background-color: black;")
        self.video_label.setMouseTracking(True)
        
        layout = QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.video_label)
        
        container = QWidget()
        container.setLayout(layout)
        self.setCentralWidget(container)

        self.phone_width = 720
        self.phone_height = 1280

        self.streamer = SocketStreamer(ip=phone_ip)
        self.decoder = H264Decoder()
        self.input_mapper = InputMapper(ip=phone_ip, phone_width=self.phone_width, phone_height=self.phone_height)

        self.streamer.start()
        self.decoder.start()

        self.is_pressing = False
        self.start_pos = None

        self.video_thread_worker = VideoStreamThread(self.streamer, self.decoder)
        self.video_thread_worker.frame_ready.connect(self.update_image)
        self.worker_thread = threading.Thread(target=self.video_thread_worker.run, daemon=True)
        self.worker_thread.start()

    def update_image(self, bgr_img):
        height, width, channel = bgr_img.shape
        bytes_per_line = 3 * width
        
        rgb_img = bgr_img[:, :, ::-1].copy()
        
        q_img = QImage(rgb_img.data, width, height, bytes_per_line, QImage.Format.Format_RGB888)
        pixmap = QPixmap.fromImage(q_img)
        
        scaled_pixmap = pixmap.scaled(self.video_label.size(), Qt.AspectRatioMode.KeepAspectRatio)
        self.video_label.setPixmap(scaled_pixmap)

    def mousePressEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton:
            self.is_pressing = True
            pos = event.position()
            self.start_pos = (pos.x(), pos.y())

    def mouseReleaseEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton and self.is_pressing:
            self.is_pressing = False
            pos = event.position()
            end_pos = (pos.x(), pos.y())
            
            w = self.video_label.width()
            h = self.video_label.height()

            dx = abs(end_pos[0] - self.start_pos[0])
            dy = abs(end_pos[1] - self.start_pos[1])

            if dx < 10 and dy < 10:
                self.input_mapper.send_tap(end_pos[0], end_pos[1], w, h)
            else:
                self.input_mapper.send_swipe(self.start_pos[0], self.start_pos[1], end_pos[0], end_pos[1], w, h)

    def closeEvent(self, event):
        self.video_thread_worker.stop()
        self.streamer.stop()
        self.decoder.stop()
        self.input_mapper.stop()
        event.accept()

if __name__ == '__main__':
    app = QApplication(sys.argv)
    
    ip_address, ok = QInputDialog.getText(None, "Connect to Android App", "Enter Phone IP Address\n(Ensure both devices are on the same Wi-Fi):")
    if ok and ip_address:
        window = ProjectorWindow(ip_address)
        window.show()
        sys.exit(app.exec())
    else:
        sys.exit(0)
