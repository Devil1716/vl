@echo off
echo ==========================================================
echo Starting Hot Reload for Projector Android
echo ==========================================================
echo.
echo This script uses Gradle continuous build.
echo Every time you save a Kotlin or XML file, the app will
echo automatically build, install, and launch on your device.
echo Keep this window open while you develop.
echo.
echo Press Ctrl+C to stop.
echo ==========================================================

call gradlew.bat :app:installAndRun -t
