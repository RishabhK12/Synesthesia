# Pico sound sensor setup

## What runs where

- Thonny runs on Windows. The portable copy in `.tools/thonny` includes its own Python.
- MicroPython runs on the Pico and provides access to its analog inputs.
- `sensor_test.py` runs on the Pico, with output displayed in Thonny's Shell.
- The test uses only built-in MicroPython modules. No pip packages, Arduino IDE,
  ESP32 setup, or phone software are needed for this stage.

## Hardware

Use a Pico with soldered headers (or properly soldered wires), a USB data cable,
one GY-MAX4466 microphone module, and three jumper wires. A breadboard helps.
Loose pins pushed through unsoldered holes do not make dependable connections.

Unplug USB before wiring. Follow the sensor's printed labels, not its pin order.

| GY-MAX4466 label | Pico label | Physical pin |
| --- | --- | --- |
| VCC / + | 3V3 OUT | 36 |
| GND / - | AGND | 33 |
| OUT | GP26 / ADC0 | 31 |

If testing an LM393 module instead, connect its AO pin to GP26 and leave DO
disconnected. Use 3.3 V for either module in this test. Do not power the sensor
from VBUS/5 V while connecting its output directly: Pico analog inputs must
remain between 0 and 3.3 V.

### Connect three GY-MAX4466 microphones

The Pico has one `3V3(OUT)` pin. Split it through a breadboard power rail or a
small 3-way connector. Do the same for ground. Some breadboards split their
long power rails in the middle; bridge the split if using both halves. Keep
each microphone's OUT wire separate.

| Connection | Microphone 1 | Microphone 2 | Microphone 3 |
| --- | --- | --- | --- |
| VCC | Shared 3.3 V rail from Pico pin 36 | Same rail | Same rail |
| GND | Shared ground rail from Pico AGND pin 33 | Same rail | Same rail |
| OUT | GP26 / pin 31 | GP27 / pin 32 | GP28 / pin 34 |

Unplug the Pico before changing wiring. Use `3V3(OUT)`, not `VBUS` (5 V),
`3V3_EN`, or `ADC_VREF`. The current `sensor_test.py` reads only GP26; it
checks microphone 1 and does not yet measure direction.

## Open Thonny

Double-click `Open Thonny for Pico.cmd` in the project folder. It opens the
saved test and selects the Pico interpreter and its current COM3 port.
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
   at the bottom right) if the Pico is not connected automatically.
2. Select MicroPython (RP2040), then its USB serial/COM port. COM3 was detected
   for the Pico in the current setup; the port can change after reconnecting.
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

For the GY-MAX4466, start with the gain adjustment near its middle. Turn it
gently in small steps while repeating the tests. Aim for speech readings clearly
above quiet readings without frequent Near limits readings. Stop turning the
adjustment screw when it reaches resistance.

Sound is the changing portion of the electrical signal in millivolts, not
calibrated acoustic decibels. Speech should repeatedly exceed the quiet baseline.
Average is the resting voltage; it need not be 1.65 V. A large Near limits
percentage suggests little headroom or possible clipping.

Repeat with the other modules one at a time using identical wiring and placement.
Passing this check does not establish waveform quality or localization accuracy.
The next step is a recording with controlled sampling.

## Troubleshooting

- `No module named machine`: select MicroPython (RP2040), not local Python.
  Do not try to pip-install machine.
- No board or COM port: try another data cable and USB port, then verify firmware
  and close other apps using the serial port.
- RPI-RP2 is visible but no COM port: the Pico is in firmware-loading mode.
- Flat readings: check OUT (or LM393 AO), power, ground, and soldered connections.
- On a GY-MAX4466 the adjustment screw changes microphone gain. On common LM393
  boards it mainly adjusts the digital threshold.

References:
- https://thonny.org/
- https://datasheets.raspberrypi.com/pico/Pico-2-Pinout.pdf
- https://docs.micropython.org/en/v1.26.0/rp2/quickref.html
- https://learn.adafruit.com/adafruit-microphone-amplifier-breakout/overview
- https://sensorkit.joy-it.net/en/sensors/ky-038
