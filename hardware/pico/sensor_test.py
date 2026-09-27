"""Run in Thonny with the MicroPython (Raspberry Pi Pico) interpreter.

One sensor: VCC -> 3V3 OUT (36), GND -> AGND (33), AO -> GP26 (31).
DO stays disconnected. This is a signal-response check, not a timed
audio recording or a sound-direction measurement.
"""

from machine import ADC, Pin
from array import array
import math
import time

mic = ADC(Pin(26))
samples = array("H", [0] * 2048)
volts_per_count = 3.3 / 65535

print("Sound test running. Press Stop or Ctrl+C to finish.")
print("Try 10 seconds quiet, speech at 30 cm, then speech at 1 metre.")

try:
    while True:
        for i in range(len(samples)):
            samples[i] = mic.read_u16()

        mean = sum(samples) / len(samples)
        variance = sum((x - mean) ** 2 for x in samples) / len(samples)
        rms_mv = math.sqrt(variance) * volts_per_count * 1000
        span_mv = (max(samples) - min(samples)) * volts_per_count * 1000
        near_rail = sum(x < 650 or x > 64885 for x in samples)
        rail_percent = 100 * near_rail / len(samples)

        print(
            "Average: %.2f V | Sound: %.1f mV | "
            "Peak-to-peak: %.1f mV | Near limits: %.1f%%"
            % (mean * volts_per_count, rms_mv, span_mv, rail_percent)
        )
        time.sleep_ms(100)
except KeyboardInterrupt:
    print("Sensor test stopped.")
