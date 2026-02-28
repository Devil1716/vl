import sys
import threading
import time
from PyQt6.QtWidgets import QApplication, QLabel, QMainWindow, QVBoxLayout, QWidget
from PyQt6.QtGui import QImage, QPixmap, QPainter, QPainterPath, QColor
from PyQt6.QtCore import Qt, pyqtSignal, QObject, QPoint

from socket_client import SystemSocketAdapter
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
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Projector")
        self.resize(360, 640)

        # 1. Floating & Frameless Constraints
        self.setWindowFlags(Qt.WindowType.FramelessWindowHint | Qt.WindowType.WindowStaysOnTopHint)
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground)

        # Main Central Widget that acts as the completely rounded "Phone"
        self.central_widget = QWidget(self)
        self.central_widget.setObjectName("PhoneBezel")
        self.central_widget.setStyleSheet("""
            #PhoneBezel {
                background-color: black;
                border-radius: 30px;
                border: 2px solid #333333;
            }
        """)
        
        self.video_label = QLabel(self.central_widget)
        self.video_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.video_label.setStyleSheet("background-color: transparent;")
        self.video_label.setMouseTracking(True)
        
        # Add padding to simulate a phone bezel
        layout = QVBoxLayout()
        layout.setContentsMargins(10, 20, 10, 20)
        layout.addWidget(self.video_label)
        self.central_widget.setLayout(layout)
        
        self.setCentralWidget(self.central_widget)

        self.phone_width = 720
        self.phone_height = 1280

        self.streamer = SystemSocketAdapter(port=8080)
        self.decoder = H264Decoder()
        self.input_mapper = InputMapper(socket_adapter=self.streamer, phone_width=self.phone_width, phone_height=self.phone_height)

        self.streamer.start()
        self.decoder.start()

        # Input tracking
        self.is_pressing = False
        self.start_pos = None

        # Window Dragging State
        self.drag_position = None

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
        
        # Scale image to the inside of the label
        scaled_pixmap = pixmap.scaled(self.video_label.size(), Qt.AspectRatioMode.KeepAspectRatio)
        self.video_label.setPixmap(scaled_pixmap)

    # Mouse Events (Drag the window if clicking the bezel, otherwise send to Android)
    def mousePressEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton:
            # Check if click is inside the actual video area
            video_rect = self.video_label.geometry()
            if video_rect.contains(event.position().toPoint()):
                self.is_pressing = True
                
                # Calculate coordinates relative only to the video sub-label
                pos = self.video_label.mapFrom(self, event.position().toPoint())
                self.start_pos = (pos.x(), pos.y())
            else:
                # The user clicked the outer bounds (Bezel) -> Drag window
                self.drag_position = event.globalPosition().toPoint() - self.frameGeometry().topLeft()
                event.accept()

    def mouseMoveEvent(self, event):
        if event.buttons() == Qt.MouseButton.LeftButton and self.drag_position is not None:
            # Moving the entire window
            self.move(event.globalPosition().toPoint() - self.drag_position)
            event.accept()

    def mouseReleaseEvent(self, event):
        if event.button() == Qt.MouseButton.LeftButton:
            self.drag_position = None # Stop window drag
            
            if self.is_pressing:
                self.is_pressing = False
                pos = self.video_label.mapFrom(self, event.position().toPoint())
                end_pos = (pos.x(), pos.y())
                
                w = self.video_label.width()
                h = self.video_label.height()

                dx = abs(end_pos[0] - self.start_pos[0])
                dy = abs(end_pos[1] - self.start_pos[1])

                if dx < 10 and dy < 10:
                    self.input_mapper.send_tap(end_pos[0], end_pos[1], w, h)
                else:
                    self.input_mapper.send_swipe(self.start_pos[0], self.start_pos[1], end_pos[0], end_pos[1], w, h)

    def keyPressEvent(self, event):
        # Press ESC to close the frameless window
        if event.key() == Qt.Key.Key_Escape:
            self.close()

    def closeEvent(self, event):
        self.video_thread_worker.stop()
        self.streamer.stop()
        self.decoder.stop()
        event.accept()

if __name__ == '__main__':
    app = QApplication(sys.argv)
    window = ProjectorWindow()
    window.show()
    sys.exit(app.exec())
