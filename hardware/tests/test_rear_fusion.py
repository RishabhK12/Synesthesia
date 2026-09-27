"""Host-side checks for the rear cue math and Pico-to-BLE event encoding."""

import contextlib
import importlib.util
import io
from pathlib import Path
import struct
import sys
import types
import unittest


HARDWARE = Path(__file__).resolve().parents[1]
machine = types.ModuleType("machine")
machine.ADC = object
machine.Pin = object
machine.UART = object
bluetooth = types.ModuleType("bluetooth")
bluetooth.UUID = lambda value: value
bluetooth.FLAG_READ = 1
bluetooth.FLAG_NOTIFY = 2
sys.modules.setdefault("machine", machine)
sys.modules.setdefault("bluetooth", bluetooth)


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


pico = load_module("direction_test", HARDWARE / "pico" / "direction_test.py")
bridge = load_module("ble_bridge", HARDWARE / "esp32" / "ble_bridge.py")


class RearFusionTests(unittest.TestCase):
    def test_rear_vote_shifts_side_sound_but_does_not_claim_rear_angle(self):
        angle_without, _ = pico.direction_from_scores([0, 1, 0])
        angle_with, strength = pico.direction_from_scores([0, 1, 0], True)
        self.assertAlmostEqual(angle_without, 120)
        self.assertGreater(angle_with, angle_without)
        self.assertLess(angle_with, 180)
        self.assertGreater(strength, pico.MIN_VECTOR_STRENGTH)

    def test_balanced_analog_scores_remain_ambiguous_with_rear_trigger(self):
        angle, strength = pico.direction_from_scores([1, 1, 1], True)
        self.assertIsNone(angle)
        self.assertLess(strength, pico.MIN_VECTOR_STRENGTH)

    def test_rear_only_event_is_uncertain_and_keeps_rear_flag_over_ble(self):
        pico.time.ticks_ms = lambda: 1234
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            pico.describe_rear_only_event()
        import json
        event = json.loads(output.getvalue().split("SND1 ", 1)[1])
        self.assertEqual(event["status"], "rear_possible")
        self.assertIsNone(event["angle_deg"])
        self.assertTrue(event["rear_triggered"])
        packet = bridge.encode_event(event, 7)
        self.assertEqual(struct.unpack("<BBHBB", packet), (1, 0x83, 0xffff, 0, 7))

    def test_fused_angle_packet_has_rear_bit(self):
        event = {
            "v": 1, "type": "sound_direction", "status": "ok",
            "angle_deg": 137.2, "separation": 0.6,
            "rear_triggered": True,
        }
        self.assertEqual(
            struct.unpack("<BBHBB", bridge.encode_event(event, 5)),
            (1, 0x80, 1372, 60, 5),
        )


if __name__ == "__main__":
    unittest.main()
