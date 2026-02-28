import sys
import threading
from PyQt6.QtWidgets import (QDialog, QVBoxLayout, QHBoxLayout, QLabel,
                             QPushButton, QListWidget, QListWidgetItem, QApplication)
from PyQt6.QtCore import Qt, pyqtSignal, QTimer
from PyQt6.QtGui import QFont

from discovery import DeviceDiscovery

class ConnectionUI(QDialog):
    device_found_signal = pyqtSignal(str, str)

    def __init__(self):
        super().__init__()
        self.setWindowTitle("Projector — Discover Devices")
        self.setFixedSize(400, 350)
        self.setStyleSheet("""
            QDialog { background-color: #1e1e1e; color: white; }
            QLabel { color: #e0e0e0; }
            QListWidget {
                background-color: #2d2d2d; color: white;
                border: 1px solid #4a4a4a; border-radius: 8px;
                padding: 5px; font-size: 14px;
            }
            QListWidget::item { padding: 10px; border-radius: 5px; }
            QListWidget::item:selected { background-color: #0078d4; }
            QPushButton {
                background-color: #0078d4; color: white;
                border: none; border-radius: 5px;
                padding: 10px; font-size: 14px; font-weight: bold;
            }
            QPushButton:hover { background-color: #1084df; }
            QPushButton:disabled { background-color: #555555; color: #888888; }
        """)

        layout = QVBoxLayout()
        layout.setContentsMargins(20, 20, 20, 20)
        layout.setSpacing(12)

        title = QLabel("Searching for Devices...")
        title.setFont(QFont("Arial", 16, QFont.Weight.Bold))
        title.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(title)
        self.title_label = title

        desc = QLabel("Make sure the Projector app is running on your phone")
        desc.setAlignment(Qt.AlignmentFlag.AlignCenter)
        desc.setStyleSheet("color: #aaaaaa; font-size: 12px;")
        layout.addWidget(desc)

        self.device_list = QListWidget()
        layout.addWidget(self.device_list)

        self.connect_btn = QPushButton("Connect")
        self.connect_btn.setEnabled(False)
        self.connect_btn.clicked.connect(self.on_connect)
        layout.addWidget(self.connect_btn)

        self.setLayout(layout)

        self.selected_ip = None
        self.discovered = {}

        self.device_list.itemClicked.connect(self.on_item_selected)

        # Connect signal
        self.device_found_signal.connect(self._add_device_to_list)

        # Start discovery
        self.discovery = DeviceDiscovery()
        self.discovery.on_device_found = self._on_device_found
        self.discovery.start()

    def _on_device_found(self, name, ip):
        # Called from background thread, emit signal to update UI on main thread
        self.device_found_signal.emit(name, ip)

    def _add_device_to_list(self, name, ip):
        if ip not in self.discovered:
            self.discovered[ip] = name
            item = QListWidgetItem(f"📱  {name}   ({ip})")
            item.setData(Qt.ItemDataRole.UserRole, ip)
            self.device_list.addItem(item)
            self.title_label.setText(f"Found {len(self.discovered)} Device(s)")

    def on_item_selected(self, item):
        self.selected_ip = item.data(Qt.ItemDataRole.UserRole)
        self.connect_btn.setEnabled(True)

    def on_connect(self):
        if self.selected_ip:
            self.discovery.stop()
            self.accept()

    def get_selected_ip(self):
        return self.selected_ip

    def closeEvent(self, event):
        self.discovery.stop()
        event.accept()
