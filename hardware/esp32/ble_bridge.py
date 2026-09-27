"""ESP32-WROOM-32 MicroPython: Pico UART events to BLE phone notifications."""

import bluetooth
import json
from machine import Pin, UART
import struct
import time


UART_ID = 1
RX_PIN = 16  # connect to Pico GP0 / TX / physical pin 1
TX_PIN = 17  # optional; leave unconnected for one-way events
UART_BAUD = 115200
DEVICE_NAME = "SynDir"
MAX_LINE_BYTES = 512

SERVICE_UUID = bluetooth.UUID("0066cf31-d07d-44cf-9a90-af6fb370f5e7")
EVENT_UUID = bluetooth.UUID("e56dd748-976b-4fd1-b7cf-7e9f480f1c7a")
EVENT_CHARACTERISTIC = (EVENT_UUID, bluetooth.FLAG_READ | bluetooth.FLAG_NOTIFY)
SERVICES = ((SERVICE_UUID, (EVENT_CHARACTERISTIC,)),)

IRQ_CENTRAL_CONNECT = 1
IRQ_CENTRAL_DISCONNECT = 2
STATUS_CODES = {"ok": 0, "ambiguous": 1, "clipped": 2, "rear_possible": 3}
REAR_FLAG = 0x80


def advertising_data():
    name = DEVICE_NAME.encode()
    # Flags, complete name, complete 128-bit service UUID: 29 bytes total.
    return (
        b"\x02\x01\x06"
        + bytes((len(name) + 1, 0x09)) + name
        + b"\x11\x07" + bytes(SERVICE_UUID)
    )


def encode_event(event, sequence):
    """Six bytes: version, status plus rear bit, angle, separation, sequence."""
    if not isinstance(event, dict):
        raise ValueError("event must be an object")
    if event.get("v") != 1 or event.get("type") != "sound_direction":
        raise ValueError("unsupported event")
    status = STATUS_CODES[event["status"]]
    rear_triggered = event.get("rear_triggered", False)
    if not isinstance(rear_triggered, bool):
        raise ValueError("invalid rear flag")
    if status == 3 and not rear_triggered:
        raise ValueError("rear-only status needs rear flag")
    angle = event.get("angle_deg")
    if status == 0:
        if not isinstance(angle, (int, float)) or not 0 <= angle < 360:
            raise ValueError("invalid angle")
        angle_tenths = int(round(angle * 10)) % 3600
    elif angle is None:
        angle_tenths = 0xFFFF
    else:
        raise ValueError("uncertain event needs null angle")
    separation = event.get("separation")
    if not isinstance(separation, (int, float)) or not 0 <= separation <= 1:
        raise ValueError("invalid separation")
    return struct.pack(
        "<BBHBB", 1, status | (REAR_FLAG if rear_triggered else 0), angle_tenths,
        min(100, int(round(separation * 100))), sequence & 0xFF,
    )


def complete_lines(pending, dropping, chunk):
    """Reassemble newline-delimited UART data; discard overlong lines."""
    lines = []
    for byte in chunk:
        if byte == 10:  # newline
            if not dropping:
                lines.append(bytes(pending).strip())
            pending = bytearray()
            dropping = False
        elif not dropping:
            if len(pending) < MAX_LINE_BYTES:
                pending.append(byte)
            else:
                pending = bytearray()
                dropping = True
    return pending, dropping, lines


class BleBridge:
    def __init__(self, ble):
        self.ble = ble
        self.connections = set()
        self.ble.active(True)
        self.ble.irq(self.on_irq)
        ((self.event_handle,),) = self.ble.gatts_register_services(SERVICES)
        self.ble.gatts_write(self.event_handle, b"\x01\x01\xff\xff\x00\x00")
        self.advertise()

    def advertise(self):
        self.ble.gap_advertise(500000, adv_data=advertising_data())
        print("Advertising as", DEVICE_NAME)

    def on_irq(self, event, data):
        if event == IRQ_CENTRAL_CONNECT:
            self.connections.add(data[0])
            print("Phone connected")
        elif event == IRQ_CENTRAL_DISCONNECT:
            self.connections.discard(data[0])
            print("Phone disconnected")
            self.advertise()

    def send(self, packet):
        self.ble.gatts_write(self.event_handle, packet)
        for connection in tuple(self.connections):
            try:
                self.ble.gatts_notify(connection, self.event_handle, packet)
            except OSError:
                self.connections.discard(connection)


def main():
    uart = UART(UART_ID, baudrate=UART_BAUD, tx=Pin(TX_PIN), rx=Pin(RX_PIN))
    bridge = BleBridge(bluetooth.BLE())
    pending = bytearray()
    dropping = False
    sequence = 0
    print("UART ready at", UART_BAUD, "baud; waiting for Pico events")

    try:
        while True:
            chunk = uart.read(128)
            if not chunk:
                time.sleep_ms(10)
                continue
            pending, dropping, lines = complete_lines(pending, dropping, chunk)
            for line in lines:
                if not line.startswith(b"SND1 "):
                    continue
                try:
                    event = json.loads(line[5:].decode("utf-8"))
                    packet = encode_event(event, sequence)
                except (ValueError, KeyError, TypeError):
                    print("Ignored invalid Pico event")
                    continue
                bridge.send(packet)
                print("Forwarded", event["status"], event["angle_deg"])
                sequence = (sequence + 1) & 0xFF
    except KeyboardInterrupt:
        print("BLE bridge stopped")


if __name__ == "__main__":
    main()
