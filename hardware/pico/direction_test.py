"""Experimental three-microphone direction test for Raspberry Pi Pico.

Run from Thonny using MicroPython. Nothing is sent to the phone or network.
This estimates the side with the strongest sound increase, not arrival-time
direction. Set ANGLES to match the actual microphone positions on your rig.
"""

from machine import ADC, Pin
import json
import math
import time


# Degrees clockwise from the front of the device. Change these if your
# microphones are mounted in different positions.
NAMES = ("GP26 MAX4466", "GP27 MAX9814", "GP28 MAX4466")
PINS = (26, 27, 28)
ANGLES = (0, 120, 240)  # front, right, left

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


def direction_from_scores(scores):
    total = sum(scores)
    if total <= 0:
        return None, 0.0

    right = 0.0
    front = 0.0
    for i in range(3):
        radians = ANGLES[i] * math.pi / 180.0
        right += scores[i] * math.sin(radians)
        front += scores[i] * math.cos(radians)

    strength = math.sqrt(right * right + front * front) / total
    if strength < MIN_VECTOR_STRENGTH:
        return None, strength
    angle = math.atan2(right, front) * 180.0 / math.pi
    return (angle + 360.0) % 360.0, strength


def describe_event(peak_scores, peak_rms, peak_clip):
    angle, strength = direction_from_scores(peak_scores)
    strongest = 0
    for i in range(1, 3):
        if peak_scores[i] > peak_scores[strongest]:
            strongest = i
    if max(peak_clip) >= 5.0:
        print("CLIPPED: lower gain or move the sound farther away.")
    if angle is None:
        label = "uncertain direction"
    else:
        label = "roughly %.0f degrees toward %s" % (angle, NAMES[strongest])
    print(
        "EVENT: %s | separation %.2f | peak RMS %d/%d/%d | clip %d/%d/%d%%"
        % (
            label,
            strength,
            peak_rms[0], peak_rms[1], peak_rms[2],
            peak_clip[0], peak_clip[1], peak_clip[2],
        )
    )


def main():
    adcs = tuple(ADC(Pin(pin)) for pin in PINS)
    quiet_high, trigger = calibrate(adcs)
    if RUN_LEVEL_CALIBRATION:
        scales = calibrate_levels(adcs, quiet_high, trigger)
    else:
        scales = load_level_scales()
    print("Ready. Make a short sound at one position, then pause.")
    event_active = False
    peak_scores = [0.0, 0.0, 0.0]
    peak_rms = [0.0, 0.0, 0.0]
    peak_clip = [0.0, 0.0, 0.0]
    last_loud_ms = time.ticks_ms()
    last_report_ms = last_loud_ms

    try:
        while True:
            rms, clip = sample_frame(adcs)
            now = time.ticks_ms()
            loud = any(rms[i] >= trigger[i] for i in range(3))

            if loud:
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
                describe_event(peak_scores, peak_rms, peak_clip)
                event_active = False
                peak_scores = [0.0, 0.0, 0.0]
                peak_rms = [0.0, 0.0, 0.0]
                peak_clip = [0.0, 0.0, 0.0]

            if time.ticks_diff(now, last_report_ms) >= REPORT_EVERY_MS:
                print(
                    "RMS %d/%d/%d (triggers %d/%d/%d)"
                    % (rms[0], rms[1], rms[2], trigger[0], trigger[1], trigger[2])
                )
                last_report_ms = now
    except KeyboardInterrupt:
        print("Direction test stopped.")


if __name__ == "__main__":
    main()
