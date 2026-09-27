@echo off
setlocal
set "THONNY_USER_DIR=%~dp0.tools\thonny-pico-settings"
if not exist "%~dp0.tools\thonny\thonny.exe" (
  echo Thonny is missing from the project tools folder.
  pause
  exit /b 1
)
if exist "%~dp0hardware\pico\direction_test.py" (
  start "" "%~dp0.tools\thonny\thonny.exe" "%~dp0hardware\pico\direction_test.py"
) else (
  start "" "%~dp0.tools\thonny\thonny.exe"
)
