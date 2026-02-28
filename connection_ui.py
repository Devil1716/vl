import sys
import threading
from PyQt6.QtWidgets import (QDialog, QVBoxLayout, QHBoxLayout, QLabel, 
                             QLineEdit, QPushButton, QMessageBox, QApplication, QProgressBar)
from PyQt6.QtCore import Qt, pyqtSignal, QObject
from PyQt6.QtGui import QFont, QColor

from adb_manager import AdbManager

class ConnectionWorker(QObject):
    finished = pyqtSignal(bool, str)
    progress = pyqtSignal(int, str)

    def __init__(self, ip_port):
        super().__init__()
        self.ip_port = ip_port
        self.adb = AdbManager()

    def run(self):
        try:
            self.progress.emit(10, "Connecting to Device...")
            success, msg = self.adb.connect(self.ip_port)
            if not success:
                self.finished.emit(False, msg)
                return

            self.progress.emit(40, "Pushing OEM Server...")
            success, msg = self.adb.push_server()
            if not success:
                self.finished.emit(False, msg)
                return

            self.progress.emit(70, "Starting Hidden Daemon...")
            success, msg = self.adb.start_server_daemon()
            if not success:
                self.finished.emit(False, msg)
                return

            self.progress.emit(90, "Forwarding Video Port...")
            success, msg = self.adb.forward_port()
            if not success:
                self.finished.emit(False, msg)
                return

            self.progress.emit(100, "Ready!")
            self.finished.emit(True, "Success")
        except Exception as e:
            self.finished.emit(False, str(e))

class ConnectionUI(QDialog):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("Projector Setup")
        self.setFixedSize(350, 200)
        self.setStyleSheet("""
            QDialog {
                background-color: #1e1e1e;
                color: white;
            }
            QLabel {
                color: #e0e0e0;
                font-size: 14px;
            }
            QLineEdit {
                background-color: #2d2d2d;
                color: white;
                border: 1px solid #4a4a4a;
                border-radius: 5px;
                padding: 5px;
                font-size: 14px;
            }
            QPushButton {
                background-color: #0078d4;
                color: white;
                border: none;
                border-radius: 5px;
                padding: 8px;
                font-size: 14px;
                font-weight: bold;
            }
            QPushButton:hover {
                background-color: #1084df;
            }
            QPushButton:disabled {
                background-color: #555555;
                color: #888888;
            }
            QProgressBar {
                border: 1px solid #4a4a4a;
                border-radius: 5px;
                text-align: center;
                color: white;
            }
            QProgressBar::chunk {
                background-color: #0078d4;
                border-radius: 4px;
            }
        """)

        layout = QVBoxLayout()
        layout.setContentsMargins(20, 20, 20, 20)
        layout.setSpacing(15)

        title = QLabel("Wireless Device Connection")
        title.setFont(QFont("Arial", 16, QFont.Weight.Bold))
        title.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(title)

        desc = QLabel("Enter IP:Port from phone's Wireless Debugging")
        desc.setAlignment(Qt.AlignmentFlag.AlignCenter)
        desc.setStyleSheet("color: #aaaaaa; font-size: 12px;")
        layout.addWidget(desc)

        input_layout = QHBoxLayout()
        self.ip_input = QLineEdit()
        self.ip_input.setPlaceholderText("e.g. 192.168.1.100:5555")
        input_layout.addWidget(self.ip_input)
        layout.addLayout(input_layout)

        self.progress_bar = QProgressBar()
        self.progress_bar.setValue(0)
        self.progress_bar.setTextVisible(False)
        self.progress_bar.setFixedHeight(10)
        self.progress_bar.hide()
        layout.addWidget(self.progress_bar)

        self.status_label = QLabel("")
        self.status_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.status_label.setStyleSheet("color: #aaaaaa; font-size: 12px;")
        layout.addWidget(self.status_label)

        self.connect_btn = QPushButton("Connect")
        self.connect_btn.clicked.connect(self.start_connection)
        layout.addWidget(self.connect_btn)

        self.setLayout(layout)
        
        self.success = False

    def start_connection(self):
        ip_port = self.ip_input.text().strip()
        if not ip_port:
            QMessageBox.warning(self, "Error", "Please enter an IP and Port.")
            return

        self.connect_btn.setEnabled(False)
        self.ip_input.setEnabled(False)
        self.progress_bar.show()
        self.progress_bar.setValue(0)
        self.status_label.setText("Starting...")

        self.worker = ConnectionWorker(ip_port)
        self.worker_thread = threading.Thread(target=self.worker.run, daemon=True)
        
        self.worker.progress.connect(self.update_progress)
        self.worker.finished.connect(self.connection_done)
        
        self.worker_thread.start()

    def update_progress(self, val, text):
        self.progress_bar.setValue(val)
        self.status_label.setText(text)

    def connection_done(self, success, msg):
        self.connect_btn.setEnabled(True)
        self.ip_input.setEnabled(True)
        
        if success:
            self.success = True
            self.accept() # Close dialog with accepted state
        else:
            self.status_label.setText("Connection Failed")
            self.progress_bar.hide()
            QMessageBox.critical(self, "Connection Error", msg)
