from machine import ADC, Pin
import time
import math

mics = {
    "MAX4466 on GP26": ADC(Pin(26)),
    "MAX9814 on GP27": ADC(Pin(27)),
    "MAX4466 on GP28": ADC(Pin(28)),
}

while True:
    for name, mic in mics.items():
        samples = [mic.read_u16() for _ in range(500)]
        average = sum(samples) / len(samples)
        rms = math.sqrt(
            sum((value - average) ** 2 for value in samples) / len(samples)
        )
        peak_to_peak = max(samples) - min(samples)

        print(
            "{}: average={:.0f}, RMS={:.0f}, peak-to-peak={}".format(
                name, average, rms, peak_to_peak
            )
        )

    print("---")
    time.sleep(0.5)