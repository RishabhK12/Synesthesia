"""Check the rear LM393's DO polarity and trim-pot threshold on Pico GP22.

Run this file from Thonny. It does not replace direction_test.py or main.py.
The counters catch short digital pulses that a slow printed level may miss.
"""

from machine import Pin
import time


rear = Pin(22, Pin.IN)
edges = [0, 0]


def on_edge(pin):
    edges[pin.value()] += 1


rear.irq(trigger=Pin.IRQ_RISING | Pin.IRQ_FALLING, handler=on_edge)
print("Rear LM393 test on GP22. Keep quiet, then clap behind the hat.")
print("Adjust the LM393 trim pot until quiet gives no edges and claps do.")

try:
    while True:
        print("DO level %d | falling edges %d | rising edges %d" %
              (rear.value(), edges[0], edges[1]))
        time.sleep_ms(500)
except KeyboardInterrupt:
    rear.irq(handler=None)
    print("Rear sensor test stopped.")
