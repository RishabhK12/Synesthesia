# Pico sound sensor setup

## What runs where

- Thonny runs on Windows. The portable copy in `.tools/thonny` includes its own Python.
- MicroPython runs on the Pico and provides access to its analog inputs.
- `sensor_test.py` runs on the Pico, with output displayed in Thonny's Shell.
- The test uses only built-in MicroPython modules. No pip packages, Arduino IDE,
  ESP32 setup, or phone software are needed for this stage.

## Experimental three-microphone direction test

Run `hardware/pico/direction_test.py` in Thonny. `Open Thonny for Pico.cmd`
opens this file. It uses GP26 for MAX4466, GP27 for MAX9814, and GP28 for
MAX4466. Power all three from 3V3(OUT) and connect their grounds to AGND;
keep the three OUT wires separate. The code sends nothing to a phone or network.

Mount the microphones apart on a rigid frame. At the top of the script, set
`ANGLES` to their real positions, measured clockwise from the front. The
default `(0, 120, 240)` means GP26 is front, GP27 is right, GP28 is left.
If they are close together on a breadboard, sound level alone will give little
direction information.

1. Start with 5 seconds of normal quiet. The script measures each mic's quiet
   RMS level and sets a separate event trigger. Keep hands away from the wires.
2. Make a short clap or speak for a second near one side, then pause. Repeat
   from the other two sides at the same distance. A report appears after sound
   falls below the trigger for about 180 ms.
3. Read the `RMS ... (triggers ...)` line every second. If quiet readings exceed
   a trigger, restart in a quieter setup, check wiring, or reduce MAX4466 gain.
   If speech never crosses a trigger, use a louder test sound or cautiously
   reduce `TRIGGER_MULTIPLIER` from 1.8. Lower values also admit more false
   events. The MAX4466 trim pots adjust gain; turn them gently.
4. If an event says `CLIPPED`, reduce gain or move the sound farther away.
   `uncertain direction` means the channels did not favor one area enough.

### Optional loudness calibration

The five-second quiet calibration runs on every start. To correct for different
mic sensitivity, set `RUN_LEVEL_CALIBRATION = True` near the top of
`direction_test.py` and run it once. After the quiet step, the script gives you
four seconds to position each mic, then measures that mic for 3.5 seconds.
Use a steady sound from a computer speaker or other fixed source. Keep its
volume and distance the same for each mic; rotate the whole rig so the named
mic faces the same speaker position each time. Do not use claps for this step.
The script rejects a test that is too quiet or clips, and saves a correction
in `mic_level_cal.json` on the Pico only after all three pass. Set the switch
back to `False` afterward; later runs load the saved correction. Repeat this
calibration if you adjust gain, replace a mic, or move the microphones.

This equalizes the three microphones for *one test sound*. The MAX9814's
automatic gain may change its response for other sounds, so calibration cannot
make this a precise direction sensor. If the quiet RMS fluctuates as much as
speech, fix that first; loudness calibration cannot separate those signals.

The angle is a *rough sound-level direction*, not a measured arrival angle.
The Pico reads its ADC channels in quick succession rather than simultaneously.
The MAX9814 changes gain automatically, so it can distort sound-level
comparisons with the two MAX4466 modules. Reflections from walls and a sound
source far from a small microphone array can also make the result ambiguous.
Treat these outputs as a prototype test, not reliable navigation information.
`all_sensors_test.py` remains available for raw per-mic diagnostics.

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
