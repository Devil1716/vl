import json
import os
import sys
import subprocess
import tempfile
import zipfile
import threading
import urllib.request
from version import VERSION, GITHUB_REPO


class AutoUpdater:
    """Checks GitHub Releases for new versions and handles self-update."""

    def __init__(self):
        self.latest_version = None
        self.download_url = None
        self.release_notes = ""

    def check_for_update(self):
        """Returns True if a newer version is available."""
        try:
            url = f"https://api.github.com/repos/{GITHUB_REPO}/releases/latest"
            req = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json"})
            with urllib.request.urlopen(req, timeout=5) as resp:
                data = json.loads(resp.read().decode())

            self.latest_version = data.get("tag_name", "").lstrip("v")
            self.release_notes = data.get("body", "")

            # Find the Windows ZIP asset
            for asset in data.get("assets", []):
                if asset["name"].endswith(".zip") and "Windows" in asset["name"]:
                    self.download_url = asset["browser_download_url"]
                    break

            if self.latest_version and self._is_newer(self.latest_version, VERSION):
                return True
        except Exception as e:
            print(f"Update check failed: {e}")
        return False

    def _is_newer(self, remote, local):
        """Compare version strings like '1.1.0' > '1.0.0'."""
        try:
            remote_parts = [int(x) for x in remote.split(".")]
            local_parts = [int(x) for x in local.split(".")]
            return remote_parts > local_parts
        except:
            return False

    def download_and_install(self, progress_callback=None):
        """Downloads the new ZIP and creates a batch script to replace the current app."""
        if not self.download_url:
            return False

        try:
            tmp_dir = tempfile.mkdtemp()
            zip_path = os.path.join(tmp_dir, "update.zip")

            # Download with progress
            def reporthook(block_num, block_size, total_size):
                if progress_callback and total_size > 0:
                    percent = int(block_num * block_size * 100 / total_size)
                    progress_callback(min(percent, 100))

            urllib.request.urlretrieve(self.download_url, zip_path, reporthook)

            # Get the current app directory
            if getattr(sys, 'frozen', False):
                app_dir = os.path.dirname(sys.executable)
            else:
                app_dir = os.path.dirname(os.path.abspath(__file__))

            extract_dir = os.path.join(tmp_dir, "extracted")

            # Extract the ZIP
            with zipfile.ZipFile(zip_path, 'r') as zf:
                zf.extractall(extract_dir)

            # Find the extracted app folder
            extracted_items = os.listdir(extract_dir)
            if len(extracted_items) == 1:
                source_dir = os.path.join(extract_dir, extracted_items[0])
            else:
                source_dir = extract_dir

            # Create a batch script to replace files after the app closes
            batch_path = os.path.join(tmp_dir, "update.bat")
            exe_name = os.path.basename(sys.executable) if getattr(sys, 'frozen', False) else "ADB-Projector.exe"

            with open(batch_path, "w") as f:
                f.write(f"""@echo off
echo Updating Projector...
timeout /t 2 /nobreak >nul
xcopy /s /e /y "{source_dir}\\*" "{app_dir}\\"
echo Update complete! Restarting...
start "" "{os.path.join(app_dir, exe_name)}"
rmdir /s /q "{tmp_dir}"
""")

            # Launch the batch script and exit
            subprocess.Popen(["cmd", "/c", batch_path], creationflags=subprocess.CREATE_NO_WINDOW)
            return True

        except Exception as e:
            print(f"Update failed: {e}")
            return False
