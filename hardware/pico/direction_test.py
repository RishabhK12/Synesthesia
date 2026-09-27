"""Experimental direction test for three analog mics and one rear LM393.

Run from Thonny or install as main.py on Pico. Sound events are printed as
SND1-prefixed JSON lines over the Pico's USB serial connection. No network
connection is used. Set ANGLES to match the actual microphone positions.
"""

from machine import ADC, Pin, UART
import json
import math
import time


# Degrees clockwise from the front of the device. Change these if your
# microphones are mounted in different positions.
NAMES = ("GP26 MAX4466", "GP27 MAX9814", "GP28 MAX4466")
PINS = (26, 27, 28)
ANGLES = (0, 120, 240)  # front, right, left
REAR_PIN = 22  # LM393 DO; Pico physical pin 29 (AO is not connected)
REAR_ANGLE = 180
# Most KY-038-style boards assert DO high; change to 0 if your test shows
# that DO goes low when the rear sensor detects a sound.
REAR_ACTIVE_LEVEL = 1
REAR_JOIN_MS = 250  # associate a rear pulse with a nearby analog event
REAR_DEBOUNCE_MS = 80
REAR_VOTE_FRACTION = 0.35  # deliberately weak: DO supplies no loudness

SAMPLES_PER_FRAME = 128  # each mic is sampled once per pass
QUIET_CALIBRATION_MS = 5000
EVENT_END_MS = 180
REPORT_EVERY_MS = 1000
TRIGGER_MULTIPLIER = 1.8
MIN_RISE_COUNTS = 800  # minimum RMS rise above the quiet median
MIN_VECTOR_STRENGTH = 0.35
CLIP_LIMIT = 650  # within about 1% of either ADC rail

# Set to True for one run to measure each microphone against the same steady
# test sound. Set it back to False afterward; the correction is saved on Pico.
RUN_LEVEL_CALIBRATION = False
CALIBRATION_FILE = "mic_level_cal.json"
LEVEL_TEST_MS = 3500
LEVEL_PREP_SECONDS = 4
UART_BAUD = 115200
UART_TX_PIN = 0  # Pico physical pin 1 -> ESP32 UART RX
UART_RX_PIN = 1  # Pico physical pin 2; reserved for optional future commands


def sample_frame(adcs):
    """Interleave the ADC channels and return RMS and clipping percentage."""
    sums = [0, 0, 0]
    sums_sq = [0, 0, 0]
    clipped = [0, 0, 0]

    for _ in range(SAMPLES_PER_FRAME):
        for i in range(3):
            value = adcs[i].read_u16()
            sums[i] += value
            sums_sq[i] += value * value
            if value < CLIP_LIMIT or value > 65535 - CLIP_LIMIT:
                clipped[i] += 1

    rms = [0.0, 0.0, 0.0]
    clip_percent = [0.0, 0.0, 0.0]
    for i in range(3):
        mean = sums[i] / SAMPLES_PER_FRAME
        variance = sums_sq[i] / SAMPLES_PER_FRAME - mean * mean
        rms[i] = math.sqrt(max(0.0, variance))
        clip_percent[i] = 100.0 * clipped[i] / SAMPLES_PER_FRAME
    return rms, clip_percent


def percentile(sorted_values, fraction):
    return sorted_values[int((len(sorted_values) - 1) * fraction)]


def calibrate(adcs):
    print("Keep the room quiet for 5 seconds; do not touch the board...")
    history = ([], [], [])
    started = time.ticks_ms()
    while time.ticks_diff(time.ticks_ms(), started) < QUIET_CALIBRATION_MS:
        rms, _ = sample_frame(adcs)
        for i in range(3):
            history[i].append(rms[i])

    median = [0.0, 0.0, 0.0]
    quiet_high = [0.0, 0.0, 0.0]
    trigger = [0.0, 0.0, 0.0]
    for i in range(3):
        history[i].sort()
        median[i] = percentile(history[i], 0.50)
        quiet_high[i] = percentile(history[i], 0.90)
        trigger[i] = max(
            quiet_high[i] * TRIGGER_MULTIPLIER,
            median[i] + MIN_RISE_COUNTS,
        )
        print(
            "%s: quiet median %.0f, quiet high %.0f, event trigger %.0f"
            % (NAMES[i], median[i], quiet_high[i], trigger[i])
        )
        if quiet_high[i] > median[i] * 1.6 and quiet_high[i] - median[i] > 500:
            print("  Quiet signal varies a lot; check wiring and gain.")
    return quiet_high, trigger


def level_score(rms, quiet_high, trigger):
    gap = max(1.0, trigger - quiet_high)
    return max(0.0, (rms - quiet_high) / gap)


def load_level_scales():
    try:
        with open(CALIBRATION_FILE) as file:
            saved = json.loads(file.read())
        scales = saved["scales"]
        if saved["pins"] != list(PINS) or len(scales) != 3:
            raise ValueError("mic layout changed")
        if any(not 0.5 <= value <= 2.0 for value in scales):
            raise ValueError("invalid scale")
        print("Loaded level correction: %.2f/%.2f/%.2f" % tuple(scales))
        return scales
    except (OSError, KeyError, TypeError, ValueError):
        print("No saved level correction; using equal weighting.")
        return [1.0, 1.0, 1.0]


def calibrate_levels(adcs, quiet_high, trigger):
    print("Level calibration: use one steady sound at a fixed distance.")
    print("Point each named mic at the same speaker position in turn.")
    responses = []
    for i in range(3):
        print("Prepare %s: %d seconds" % (NAMES[i], LEVEL_PREP_SECONDS))
        time.sleep(LEVEL_PREP_SECONDS)
        print("Measuring %s; keep sound and distance steady..." % NAMES[i])
        values = []
        worst_clip = 0.0
        started = time.ticks_ms()
        while time.ticks_diff(time.ticks_ms(), started) < LEVEL_TEST_MS:
            rms, clip = sample_frame(adcs)
            values.append(level_score(rms[i], quiet_high[i], trigger[i]))
            worst_clip = max(worst_clip, clip[i])
        values.sort()
        response = percentile(values, 0.50)
        print("  Response %.2f, worst clipping %.1f%%" % (response, worst_clip))
        if response < 1.0 or worst_clip >= 5.0:
            print("Calibration failed: sound too quiet or clipped. Previous correction kept.")
            return load_level_scales()
        responses.append(response)

    ordered = sorted(responses)
    reference = ordered[1]
    scales = [min(2.0, max(0.5, reference / value)) for value in responses]
    try:
        with open(CALIBRATION_FILE, "w") as file:
            file.write(json.dumps({"pins": list(PINS), "scales": scales}))
        print("Saved level correction: %.2f/%.2f/%.2f" % tuple(scales))
    except OSError:
        print("Could not save correction; it applies only until this run stops.")
    if max(responses) / min(responses) > 4.0:
        print("Large mic mismatch: correction was limited; check gain and wiring.")
    return scales


def direction_from_scores(scores, rear_triggered=False):
    rear_score = max(scores) * REAR_VOTE_FRACTION if rear_triggered else 0.0
    total = sum(scores) + rear_score
    if total <= 0:
        return None, 0.0

    right = 0.0
    front = 0.0
    for i in range(3):
        radians = ANGLES[i] * math.pi / 180.0
        right += scores[i] * math.sin(radians)
        front += scores[i] * math.cos(radians)
    if rear_score:
        radians = REAR_ANGLE * math.pi / 180.0
        right += rear_score * math.sin(radians)
        front += rear_score * math.cos(radians)

    strength = math.sqrt(right * right + front * front) / total
    if strength < MIN_VECTOR_STRENGTH:
        return None, strength
    angle = math.atan2(right, front) * 180.0 / math.pi
    return (angle + 360.0) % 360.0, strength


def send_event(event, uart=None):
    # The phone/web reader ignores all other printed diagnostic lines.
    line = "SND1 " + json.dumps(event)
    print(line)
    if uart is not None:
        uart.write(line + "\n")


def describe_rear_only_event(uart=None):
    print("EVENT: possible sound behind (rear LM393 only; direction uncertain)")
    send_event({
        "v": 1,
        "type": "sound_direction",
        "angle_deg": None,
        "separation": 0.0,
        "status": "rear_possible",
        "clipped": False,
        "rear_triggered": True,
        "peak_rms": [0, 0, 0],
        "t_ms": time.ticks_ms(),
    }, uart)


def describe_event(peak_scores, peak_rms, peak_clip, uart=None,
                   rear_triggered=False):
    angle, strength = direction_from_scores(peak_scores, rear_triggered)
    clipped = max(peak_clip) >= 5.0
    if clipped:
        print("CLIPPED: lower gain or move the sound farther away.")
    if clipped:
        status = "clipped"
        angle = None
    elif angle is None:
        status = "ambiguous"
    else:
        status = "ok"
    if angle is None:
        label = "uncertain direction"
    else:
        label = "roughly %.0f degrees" % angle
    print(
        "EVENT: %s | separation %.2f | peak RMS %d/%d/%d | clip %d/%d/%d%% | rear %s"
        % (
            label,
            strength,
            peak_rms[0], peak_rms[1], peak_rms[2],
            peak_clip[0], peak_clip[1], peak_clip[2],
            "yes" if rear_triggered else "no",
        )
    )
    # One complete SND1 line is one event; angle is clockwise from rig front.
    send_event({
        "v": 1,
        "type": "sound_direction",
        "angle_deg": None if angle is None else round(angle, 1),
        "separation": round(strength, 3),
        "status": status,
        "clipped": clipped,
        "rear_triggered": rear_triggered,
        "peak_rms": [int(value) for value in peak_rms],
        "t_ms": time.ticks_ms(),
    }, uart)


class RearTrigger:
    """Latch short LM393 digital pulses while the ADC loop is sampling."""

    def __init__(self):
        idle_pull = Pin.PULL_DOWN if REAR_ACTIVE_LEVEL else Pin.PULL_UP
        self.pin = Pin(REAR_PIN, Pin.IN, idle_pull)
        self.count = 0
        edge = Pin.IRQ_RISING if REAR_ACTIVE_LEVEL else Pin.IRQ_FALLING
        self.pin.irq(trigger=edge, handler=self._on_edge)

    def _on_edge(self, pin):
        # Keep the interrupt handler tiny; process its count in the main loop.
        self.count += 1


def main():
    adcs = tuple(ADC(Pin(pin)) for pin in PINS)
    uart = UART(0, baudrate=UART_BAUD, tx=Pin(UART_TX_PIN), rx=Pin(UART_RX_PIN))
    quiet_high, trigger = calibrate(adcs)
    if RUN_LEVEL_CALIBRATION:
        scales = calibrate_levels(adcs, quiet_high, trigger)
    else:
        scales = load_level_scales()
    rear = RearTrigger()
    print("Rear LM393 DO on GP%d: idle level %d, active level %d" %
          (REAR_PIN, rear.pin.value(), REAR_ACTIVE_LEVEL))
    if rear.pin.value() == REAR_ACTIVE_LEVEL:
        print("Rear DO is active in quiet; adjust its trim pot or check polarity.")
    print("Ready. Make a short sound at one position, then pause.")
    event_active = False
    peak_scores = [0.0, 0.0, 0.0]
    peak_rms = [0.0, 0.0, 0.0]
    peak_clip = [0.0, 0.0, 0.0]
    last_loud_ms = time.ticks_ms()
    last_report_ms = last_loud_ms
    rear_seen_count = rear.count
    rear_accepted = 0
    last_rear_ms = None
    rear_pending_ms = None
    event_rear = False

    try:
        while True:
            rms, clip = sample_frame(adcs)
            now = time.ticks_ms()
            loud = any(rms[i] >= trigger[i] for i in range(3))

            if rear.count != rear_seen_count:
                rear_seen_count = rear.count
                if last_rear_ms is None or time.ticks_diff(now, last_rear_ms) >= REAR_DEBOUNCE_MS:
                    last_rear_ms = now
                    rear_accepted += 1
                    if event_active:
                        event_rear = True
                    else:
                        rear_pending_ms = now

            if (rear_pending_ms is not None and not event_active and
                    time.ticks_diff(now, rear_pending_ms) >= REAR_JOIN_MS):
                describe_rear_only_event(uart)
                rear_pending_ms = None

            if loud:
                if not event_active:
                    event_rear = rear_pending_ms is not None
                    rear_pending_ms = None
                event_active = True
                last_loud_ms = now
                for i in range(3):
                    # Divide by each mic's own quiet-to-trigger gap so that
                    # a naturally louder microphone does not always win.
                    score = level_score(rms[i], quiet_high[i], trigger[i]) * scales[i]
                    peak_scores[i] = max(peak_scores[i], score)
                    peak_rms[i] = max(peak_rms[i], rms[i])
                    peak_clip[i] = max(peak_clip[i], clip[i])

            if event_active and time.ticks_diff(now, last_loud_ms) >= EVENT_END_MS:
                describe_event(peak_scores, peak_rms, peak_clip, uart,
                               rear_triggered=event_rear)
                event_active = False
                event_rear = False
                peak_scores = [0.0, 0.0, 0.0]
                peak_rms = [0.0, 0.0, 0.0]
                peak_clip = [0.0, 0.0, 0.0]

            if time.ticks_diff(now, last_report_ms) >= REPORT_EVERY_MS:
                print(
                    "RMS %d/%d/%d (triggers %d/%d/%d) | rear DO %d, pulses %d"
                    % (rms[0], rms[1], rms[2], trigger[0], trigger[1], trigger[2],
                       rear.pin.value(), rear_accepted)
                )
                last_report_ms = now
    except KeyboardInterrupt:
        print("Direction test stopped.")


if __name__ == "__main__":
    main()
