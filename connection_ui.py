import sys
import threading
from PyQt6.QtWidgets import (QDialog, QVBoxLayout, QHBoxLayout, QLabel,
                             QPushButton, QListWidget, QListWidgetItem, QApplication,
                             QProgressBar, QMessageBox)
from PyQt6.QtCore import Qt, pyqtSignal, QTimer
from PyQt6.QtGui import QFont

from discovery import DeviceDiscovery
from updater import AutoUpdater
from version import VERSION


class ConnectionUI(QDialog):
    device_found_signal = pyqtSignal(str, str)
    update_available_signal = pyqtSignal(str)
    update_progress_signal = pyqtSignal(int)

    def __init__(self):
        super().__init__()
        self.setWindowTitle(f"Projector v{VERSION} — Discover Devices")
        self.setFixedSize(420, 420)
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
            QProgressBar {
                border: 1px solid #4a4a4a; border-radius: 5px;
                background-color: #2d2d2d; text-align: center; color: white;
            }
            QProgressBar::chunk { background-color: #4CAF50; border-radius: 4px; }
        """)

        layout = QVBoxLayout()
        layout.setContentsMargins(20, 20, 20, 20)
        layout.setSpacing(10)

        # Version + update row
        top_row = QHBoxLayout()
        ver_label = QLabel(f"v{VERSION}")
        ver_label.setStyleSheet("color: #666; font-size: 11px;")
        top_row.addWidget(ver_label)
        top_row.addStretch()

        self.update_btn = QPushButton("Update Available!")
        self.update_btn.setStyleSheet("""
            QPushButton { background-color: #4CAF50; font-size: 12px; padding: 5px 12px; }
            QPushButton:hover { background-color: #45a049; }
        """)
        self.update_btn.setVisible(False)
        self.update_btn.clicked.connect(self.on_update_clicked)
        top_row.addWidget(self.update_btn)
        layout.addLayout(top_row)

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

        self.progress_bar = QProgressBar()
        self.progress_bar.setVisible(False)
        self.progress_bar.setMaximum(100)
        layout.addWidget(self.progress_bar)

        self.connect_btn = QPushButton("Connect")
        self.connect_btn.setEnabled(False)
        self.connect_btn.clicked.connect(self.on_connect)
        layout.addWidget(self.connect_btn)

        self.setLayout(layout)

        self.selected_ip = None
        self.discovered = {}
        self.updater = AutoUpdater()

        self.device_list.itemClicked.connect(self.on_item_selected)
        self.device_found_signal.connect(self._add_device_to_list)
        self.update_available_signal.connect(self._show_update_button)
        self.update_progress_signal.connect(self._update_progress)

        # Start device discovery
        self.discovery = DeviceDiscovery()
        self.discovery.on_device_found = self._on_device_found
        self.discovery.start()

        # Check for updates in background
        threading.Thread(target=self._check_updates, daemon=True).start()

    def _check_updates(self):
        if self.updater.check_for_update():
            self.update_available_signal.emit(self.updater.latest_version)

    def _show_update_button(self, version):
        self.update_btn.setText(f"Update to v{version}")
        self.update_btn.setVisible(True)

    def on_update_clicked(self):
        reply = QMessageBox.question(
            self, "Update Available",
            f"Version {self.updater.latest_version} is available.\n\n"
            f"The app will download the update, close, and restart automatically.\n\n"
            f"Continue?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
        )
        if reply == QMessageBox.StandardButton.Yes:
            self.update_btn.setEnabled(False)
            self.update_btn.setText("Downloading...")
            self.progress_bar.setVisible(True)
            threading.Thread(target=self._do_update, daemon=True).start()

    def _do_update(self):
        def progress(pct):
            self.update_progress_signal.emit(pct)

        success = self.updater.download_and_install(progress_callback=progress)
        if success:
            QApplication.quit()

    def _update_progress(self, pct):
        self.progress_bar.setValue(pct)

    def _on_device_found(self, name, ip):
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
