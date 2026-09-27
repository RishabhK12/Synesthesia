# Pico sound sensor setup

## What runs where

- Thonny runs on Windows. The portable copy in `.tools/thonny` includes its own Python.
- MicroPython runs on the Pico and provides access to its analog inputs.
- `sensor_test.py` runs on the Pico, with output displayed in Thonny's Shell.
- The test uses only built-in MicroPython modules. No pip packages, Arduino IDE,
  ESP32 setup, or phone software are needed for this stage.

## Hardware

Use a Pico with soldered headers (or properly soldered wires), a USB data cable,
one LM393 microphone module with AO, and three jumper wires. A breadboard helps.
Loose pins pushed through unsoldered holes do not make dependable connections.

Unplug USB before wiring. Follow the sensor's printed labels, not its pin order.

| Sensor | Pico label | Physical pin |
| --- | --- | --- |
| VCC / + | 3V3 OUT | 36 |
| GND / - | AGND | 33 |
| AO | GP26 / ADC0 | 31 |
| DO | Leave disconnected | - |

Use 3.3 V for this test. Do not power the sensor from VBUS/5 V while connecting
AO directly: Pico analog inputs must remain between 0 and 3.3 V. If the module
requires 5 V, check its output and arrange voltage scaling before connecting AO.

## Open Thonny

Double-click `Open Thonny.cmd` in the project folder. It opens the saved test.
If a first-run dialog appears, accept the default language/settings.

## Install MicroPython on the Pico if needed

1. Read the board label: Pico/Pico H, Pico W, Pico 2, or Pico 2 W.
2. Download the stable UF2 for that exact model from the official page below.
   Pico H uses Pico firmware; Pico WH uses Pico W firmware.
3. Hold BOOTSEL while connecting USB to the computer, then release it.
4. The original Pico appears as RPI-RP2; Pico 2 appears as RP2350.
5. Copy the matching UF2 onto that drive. The drive disappears as the board
   restarts. That is expected. Installing firmware replaces the running program;
   preserve any existing board code you need before doing this.
6. Subsequent normal connections do not require BOOTSEL.

Official firmware/setup: https://www.raspberrypi.com/documentation/microcontrollers/micropython.html

## Select the Pico and run

1. In Thonny, open Tools > Options > Interpreter (or the interpreter selector
   at the bottom right).
2. Select MicroPython (Raspberry Pi Pico), then its USB serial/COM port.
3. Click Stop/Restart if needed. The Shell should show a MicroPython banner
   or respond to `print("Pico connected")`.
4. Open `hardware/pico/sensor_test.py` from this computer if it is not open.
5. Press F5 or the green Run button. Keep the source file saved on this computer;
   Thonny sends it to the Pico to execute. Do not name it main.py yet.
6. Press Stop or Ctrl+C to stop the test.

## Test and interpret

Record several output lines for each condition, without touching the board:

1. Quiet for 10 seconds.
2. Normal speech from 20-30 cm.
3. Normal speech from 1 metre.
4. Several claps from 1 metre. Bursts have gaps, so a clap may be missed.

Sound is the changing portion of the electrical signal in millivolts, not
calibrated acoustic decibels. Speech should repeatedly exceed the quiet baseline.
Average is the resting voltage; it need not be 1.65 V. A large Near limits
percentage suggests little headroom or possible clipping.

Repeat with the other modules one at a time using identical wiring and placement.
Passing this check does not establish waveform quality or localization accuracy.
The next step is a recording with controlled sampling.

## Troubleshooting

- `No module named machine`: select MicroPython (Raspberry Pi Pico), not local Python.
  Do not try to pip-install machine.
- No board or COM port: try another data cable and USB port, then verify firmware
  and close other apps using the serial port.
- RPI-RP2 is visible but no COM port: the Pico is in firmware-loading mode.
- Flat readings: check AO vs DO, power, ground, and soldered connections.
- Turning the adjustment screw does little: on common LM393 boards it mainly
  adjusts the digital threshold, not microphone amplification.

References:
- https://thonny.org/
- https://datasheets.raspberrypi.com/pico/Pico-2-Pinout.pdf
- https://docs.micropython.org/en/v1.26.0/rp2/quickref.html
- https://sensorkit.joy-it.net/en/sensors/ky-038
