# Pico + ESP32-WROOM-32 wireless sound direction

The Pico samples the three analog microphones and estimates a rough direction.
It sends one `SND1` JSON line per sound event over a 3.3 V UART wire. The ESP32
receives that line and sends a compact Bluetooth Low Energy (BLE) notification
to Chrome on the Samsung S23 Ultra. The phone can use its own microphone to
identify the sound; only direction events cross this BLE connection.

## Files for each board

| Board | Save these files to the board's root in Thonny |
| --- | --- |
| Raspberry Pi Pico | `hardware/pico/direction_test.py`, `hardware/pico/main.py` |
| ESP32-WROOM-32 | `hardware/esp32/ble_bridge.py`, `hardware/esp32/main.py` |

Back up an existing `main.py` on either board before replacing it. The two
`main.py` files are different. They make each board start its program when USB
power is applied. The Pico's `mic_level_cal.json` stays on the Pico. Set
`RUN_LEVEL_CALIBRATION = False` in the Pico file after any level calibration.
Save the two files to **Pico** while Thonny's Pico interpreter is selected;
switch Thonny to **MicroPython (ESP32)** and its own USB serial port before
saving the ESP32 files. An ESP32 that does not yet run MicroPython needs the
official `ESP32_GENERIC` firmware installed first; Thonny can install it for
the selected board and port.

## Wiring

Unplug both boards before connecting wires. Leave the three microphones on
Pico GP26, GP27, and GP28 as described in `../pico/SETUP.md`.

| Pico | ESP32-WROOM-32 dev board | Needed? |
| --- | --- | --- |
| GP0, physical pin 1 (UART TX) | GPIO16 / RX | Yes, direction data |
| GND, physical pin 3 | GND | Yes, common ground |
| GP1, physical pin 2 (UART RX) | GPIO17 / TX | No, reserved for future commands |

Power **each board through its own USB connector** from a battery pack with
two outputs or while testing from two computer USB ports. The UART signals
are 3.3 V. Do not connect either board's 3V3 or 5V power pin to the other's
power pin. ESP32 GPIO16/17 are the defaults in `ble_bridge.py` for the stated
ESP32-WROOM-32 module; the RX wire is the only data wire required now.

## Test one link at a time

1. On the Pico, run `direction_test.py` in Thonny. After five quiet seconds,
   make a sound near one mic and pause. Check for a complete `SND1 {...}` line.
   If no event prints, solve the mic threshold or wiring issue first.
2. On the ESP32, run `ble_bridge.py` in Thonny. Look for `Advertising as SynDir`
   and `UART ready at 115200 baud`. Keep both boards powered and make another
   sound. The ESP32 should print `Forwarded ok ...`, `ambiguous`, or `clipped`.
   If the Pico prints an event but ESP32 does not, check Pico GP0 to ESP32
   GPIO16, the GND wire, and the matching 115200 baud settings.
3. Save both sets of files onto their boards and restart them from the battery
   pack. Keep the microphones quiet for the first five seconds after power-up.
4. Host `ble_demo.html` over HTTPS. Open it in Chrome on the S23 Ultra, tap
   **Connect sensor**, select **SynDir**, and approve Bluetooth access. Make a
   sound near one mic and pause. The compass should show a direction or say
   that the direction is unclear. The browser page needs to stay open for its
   visual cues.

If the ESP32 forwards events but the page shows none, check that the page
says **Sensor connected**, the phone's Bluetooth is on, and the ESP32 prints
`Phone connected`. If the ESP32 cannot start Bluetooth, check that the
installed MicroPython build supports BLE on the ESP32-WROOM-32.

## Data your web app receives

`ble_demo.html` shows the Web Bluetooth connection and dispatches a
`pico-sound` event on the **same page** for each notification. Move its BLE
script into your web app, or adapt its `receiveNotification` function.
For example:

```js
window.addEventListener("pico-sound", ({ detail }) => {
  if (detail.status === "ok") showDirection(detail.angle_deg);
  else showUncertainDirection();
});
```

`angle_deg` is clockwise from the mic rig's front (0° front, 90° right).
`separation` is a heuristic from 0 to 1, not a probability. `status` is `ok`,
`ambiguous`, or `clipped`; `angle_deg` is `null` for the last two. Each BLE
notification is six bytes: version (1), status (0/1/2), angle in tenths of a
degree as a little-endian unsigned 16-bit number (`65535` means unknown),
separation as 0–100, and a sequence number 0–255. The browser example turns
this into an ordinary JavaScript object. BLE does not carry audio samples.

The BLE service UUID is `0066cf31-d07d-44cf-9a90-af6fb370f5e7`; its
notification characteristic UUID is `e56dd748-976b-4fd1-b7cf-7e9f480f1c7a`.
Chrome's Web Bluetooth connection needs an HTTPS page and a user tap to open
the device picker. Mount the rig with its front aligned with the phone's
displayed front. This setup has been checked in code but still needs a real
ESP32 and phone test.

References:
- https://docs.micropython.org/en/latest/library/bluetooth.html
- https://docs.micropython.org/en/latest/esp32/quickref.html
- https://micropython.org/download/ESP32_GENERIC/
- https://developer.chrome.com/docs/capabilities/bluetooth
