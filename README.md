# Synesthesia
HackGT 13 Project

## Offline sound and name alerts (build 3.0)

Open **Listen · sounds and names** from the main screen. This foreground-only
screen runs YAMNet and sherpa-onnx on the phone. It displays horns, sirens, alarms,
doorbells and knocking, plus names/nicknames you enter. Speech is a small status
indicator. No server, API key or runtime model download is needed. Live audio
stays in memory and is discarded when listening stops.

Enter a name and up to two nicknames or English pronunciation spellings, then
use **Test my name · 30 seconds** with a helper. Recognition varies by name,
accent, distance and noise. The test does not train a new model. **Start listening**
enables the selected categories; **Stop listening**, leaving the screen, or locking
the phone stops capture. Edit names and category settings while stopped. Vibration
can be changed while listening. Large cards show overlapping events and a recent
history remains until the next session. Names persist only in app preferences.

The separate experimental compass can reuse saved calibration and the same stereo
capture. Its bearing describes the dominant sound, not a particular event card.
Classification works without compass calibration or a reliable direction.

### Build the offline app

The build now uses the included Gradle 8.13 wrapper and Android Gradle Plugin
8.12.0. Install JDK 17 or newer, Android SDK platform 35, NDK 27.1.12297006 and
CMake 3.22.1. The first build needs internet to retrieve pinned libraries,
tokenizer source and models; subsequent builds reuse the cache. `models.lock.json`
pins model/runtime source URLs and SHA-256 hashes. The APK bundles the models
and native libraries for **ARM64 phones**, including the S23 Ultra.

```powershell
.\build.ps1 -RunTests -SideBySide
adb install -r .\build\SoundDirectionTest.apk
```

The standard package is built by omitting `-SideBySide`. On Linux/macOS, set
`ANDROID_HOME` and run `bash build.sh --tests --side-by-side`. The original signing
key is reused when `debug.keystore` exists; a clean clone uses Android's default
debug signing key. Keep the original key to update an existing install without
losing its saved calibration and diagnostics.

Source layout: `listen/` contains capture, resampling, independent bounded workers,
model adapters and event filtering; `ListenActivity` provides the screen.
SentencePiece's native bridge is under `native/`. Model audio and event times use
the monotonic elapsed-realtime clock; raw scores and result delay are shown only
when **Show detector diagnostics** is enabled. Audio queues reset after overflow
instead of replaying stale backlog. Each detector reports its own readiness or
failure.

Thresholds are provisional, and real-world accuracy targets have not yet been
established. See [validation and field-test procedure](docs/listen-validation.md)
for automated checks, listening trials and acceptance targets. Background
listening, Quest event labels and per-source sound localization are deferred.

The build steps below describe earlier diagnostic builds. For the current app,
use the Gradle requirements and commands above; existing diagnostic workflows
and data formats still apply.

## Experimental 360° sound compass

The **Sound Direction Test → 360° sound compass** screen uses the S23 Ultra's
CAMCORDER stereo input. It measures the arrival-time difference between two
recorded channels and learns eight phone-relative direction templates from your
claps. When one sound dominates, a blue arc shows the best direction and an
orange arc shows another plausible direction. After the phone turns while the
sound stays fixed, the app may show only one arc if the measurements separate
the candidates. The numbers are **approximate bearings**, not measured angles
or distances. One stereo delay cannot reliably distinguish front from behind
at a single pose, and processed audio or reflections may cause errors. It does
not yet separate simultaneous sound sources or identify sound types.

### Calibrate on the S23 Ultra

1. Open **360° sound compass** and allow microphone access. Disconnect Bluetooth
   and any external microphone. Keep the case and grip you intend to use. Hold
   the screen toward your eyes in landscape, with the rear camera pointing
   forward into the room. Note which side has the USB port. Keep the microphone
   holes clear. Tap **Start microphone**.
2. Use a reasonably quiet room and a helper. Keep the **phone fixed** at one
   position and heading during all eight calibration steps. The helper stands
   about 1–2 m away at phone height and changes position around the phone.
   **Front** is beyond the rear camera; **Behind** is on your side of the screen.
3. Select **Front**, tap **Capture 4 claps**, then make four sharp claps with a
   clear quiet gap between them, about one second apart. Wait for **4/4** and
   the saved message. Repeat in order: **Front-right, Right, Behind-right,
   Behind, Behind-left, Left, Front-left**. The app selects the next position
   automatically. Each step times out after 20 seconds; tap Capture again if
   fewer than four claps were accepted. Keep people, music and the phone speaker
   quiet while capturing. Do not rotate the phone between positions.
4. After the eighth step, the screen should say **Calibration complete**. If it
   says it cannot separate left and right, check that no mic is blocked and
   repeat in a quieter room. A calibration is specific to this phone, input
   mode, grip, case, and orientation. Use **Clear calibration** if any of those
   change. **Clear calibration / new session** keeps earlier recordings on the
   phone in their old session and starts a clean folder. The eight-step
   calibration must be finished before leaving the app; a completed calibration
   is saved for later launches. To recalibrate a saved setup, start a new session.

### Test whether front/back can be resolved

1. Start the compass microphone in a quiet room, then put a steady broad-band
   sound source, such as another phone playing soft noise or continuous speech,
   at a known position about 1–2 m away. Do not use this phone's own speaker.
   Pick its **starting** position in the test selector. Put the
   room, sound, distance, case, grip, and USB-port side in the notes box.
2. Tap **Record 15-second sound test**. Keep the source in place and rotate your
   body and phone together slowly by roughly 40–60° during the recording.
   Avoid walking or covering the microphones. Look for a blue and orange arc
   becoming one blue arc. An ambiguous or empty display is a valid result.
3. Repeat with the source starting **Front-right**, **Behind-right**, **Front**,
   and **Behind**, saving one trial for each. If possible, repeat those in a
   different room. Recalibrate first if the grip or case changes.
4. Tap **Export compass ZIP**, save it to Downloads, and provide that ZIP for
   analysis. It contains the calibration WAV recordings, accepted-clap CSV,
   eight-direction summary, the turn-test WAV/CSV/JSON files, device and mic
   metadata, and your notes. The WAVs contain audible surroundings. If USB
   debugging is still connected, you can instead say the trials are done and
   the assistant can retrieve the files with `collect-diagnostics.ps1 -Compass`.

These recordings let us measure angular error, ambiguous cases, and whether
turning truly resolves front/back on this hardware. No map of sound position in
metres is possible from these phone microphones alone. A 360° bearing can be
shown only when data supports it; otherwise the two arcs indicate uncertainty.

### Speech follow-up (build 2.3)

The September 26 eight-direction session had median delays of +1.2, +17.8,
+21.3, +14.2, -0.2, -16.0, -25.9, and -19.8 samples, clockwise from Front.
The pattern supports useful left/right information. Front and Behind still
overlap. Two accepted events disagreed strongly with their other three events;
the median limited their effect. That export contained calibration recordings
only, so it cannot establish speech detection performance or bearing accuracy.

Build 2.3 lowers the live activity threshold from 9 to 4 dB above the estimated
background and the minimum delay peak from 0.20 to 0.12. A weak observation
requires a second window at least 140 ms later before displaying a direction.
These are provisional sensitivity changes; clap calibration retains its
stricter transient threshold. The screen now distinguishes sound activity from
a usable direction and explains when the phone is tilted too far. Saved sound
tests include the noise estimate, activity gate, orientation availability, and
delay evidence even for windows rejected by the live gate.

To test speech, keep the existing calibration if the setup is unchanged. Start
the microphone with two seconds of quiet, then select the speaker's starting
position and tap **Record 15-second sound test**. Have a helper about 1 m away
speak at normal volume throughout: hold the phone still for the first five
seconds, then slowly turn 40–60°. Repeat with the speaker initially Right,
Front, and Behind. Also save one 15-second quiet test and put "quiet control"
in its notes. Export the ZIP; it should contain `turn-*.wav`, `turn-*.csv`, and
`turn-*.json`, alongside the calibration files. Ordinary listening without
pressing Record does not save the audio.

### Build and install the test app

On Windows, open PowerShell in this project folder. Install Java, Android SDK
platform 34, build-tools 35.0.0, and Android platform-tools if absent. With
the S23 Ultra connected by USB, USB debugging enabled, and the computer trusted
on the phone, run:

```powershell
.\build.ps1 -RunTests -SideBySide
adb devices
adb install -r .\build\SoundDirectionTest.apk
```

Launch **Sound Direction Test** from the phone's app list, then tap **360° sound
compass**. `-SideBySide` keeps an older differently signed Sound Direction app
installed. Running the build command again after code changes and then
`adb install -r` updates the test app while keeping its calibration and files.
Uninstalling the test app deletes its private recordings and calibration, so
export the ZIP first. To copy every compass session over USB instead of using
the phone's Export button, stop recording and run:

```powershell
.\collect-diagnostics.ps1 -Compass
```

## Microphone diagnostic: start here

Open **Sound Direction Test → Microphone test (start here)**. The separate test build
can coexist with an older Sound Direction app signed with a different key.
The diagnostic screen runs in landscape and needs microphone permission only while
it is open. It records for 10 seconds after a silent 3-second countdown. Leaving
the app stops the recording and marks the clip as interrupted. Calibration from
the original direction screen is not used.

**Initial connected S23 Ultra check (SM-S918U1, Android 16):** UNPROCESSED reported
unsupported and produced bit-identical channels. CAMCORDER produced different
channels and listed two active microphones with PROCESSED mappings; its routed
device metadata was unavailable. These were ambient setup recordings, not a
direction-accuracy test. Start this phone's next session with CAMCORDER and the
five labeled positions below. The other presets remain available for comparison.

**First labeled landscape trial (September 26, 2026):** The user recorded five
complete 10-second CAMCORDER clips with the case on, USB port on the right, and a
sound roughly 1 m away. Four clear left claps had GCC delays around -26 samples;
five front claps were around +1; five right claps were around +23; three behind
claps ranged from about -4 to +3. An independent waveform correlation confirmed
the left/right signs, but found right-clap peaks around +14 to +17 samples. This
supports a broad left/right indicator in this setup. It does not establish an
absolute bearing or front/back direction: channels are marked PROCESSED, the two
delay methods disagree in magnitude, and the near-center front/behind signatures
overlap. The test data is in ignored `diagnostic-data/` and remains private to the
workspace. The experimental compass above needs its own eight-direction calibration
and turn trials before its bearings can be judged for accessibility use.

### First recording session (about five minutes)

1. Use a quiet room. Disconnect Bluetooth headsets and external microphones.
   Hold the phone in landscape with the display toward your eyes. Keep the same
   grip throughout; leave the microphone openings clear.
2. Tap **New session**. In setup notes, record the room, approximate source distance,
   case on/off, grip/mount, and whether the USB port is on your left or right.
3. Start with **UNPROCESSED**. Android may report that unprocessed input is unsupported;
   still capture this preset for comparison, without assuming it provides raw mics.
4. Record **Quiet** for 10 seconds. Then record **Left**, **Front**, **Right**, and
   **Behind** separately. A helper should make 4–5 claps/knocks, spaced about two
   seconds apart, from a fixed position roughly 1–2 m from the phone at its height.
   Wait for the visible RECORDING message. Avoid moving the phone between positions.
   Use an external sound source, not the phone's own speaker.
5. The next position is selected automatically after a successful clip. After Behind,
   the app advances to the next source. Repeat the five positions for **CAMCORDER**,
   **MIC**, and **VOICE_RECOGNITION**. This produces 20 clips. If a source fails,
   its error report is saved; select the next source manually.
6. **Front** is beyond the rear camera, away from the holder. **Behind** is on the
   screen/holder side. Left and right are the holder's left and right. Keep distances
   similar; do not put a sound right against a microphone.
7. Tap **Export session ZIP** and save to Downloads, then provide the ZIP for
   analysis. The ZIP includes audible recordings; choose test surroundings accordingly.
   With USB debugging connected, `collect-diagnostics.ps1` can retrieve all sessions
   directly without a manual export. Tell the assistant when the recordings are done.

For a shorter first pass, record Quiet, Left, Front, Right, Behind for UNPROCESSED
and CAMCORDER (10 clips). We can decide whether the other presets need testing
after inspecting those results. No need to interpret the changing delay readout.

### What gets saved

Files remain in app-private `files/diagnostics/session-*` directories until exported.
New session starts a new folder and keeps earlier sessions. Returning to the test
screen resumes the latest folder. Export includes the current session only; USB
collection retrieves every folder. Uninstalling the test app removes its recordings.

- **WAV:** 48 kHz, two channels, PCM16, preserved in recorder channel order. The app
  does not normalize, denoise, subtract calibration, or filter the saved audio.
  Android/vendor processing can still be present.
- **JSON:** actual sound position and notes; model and firmware; microphone inventory;
  active microphone/channel mappings and positions when supplied; input route;
  reported recording effects; format; completion/error state; audio timestamps;
  whole-clip signal checks. Microphone metadata may be missing or incomplete.
- **CSV:** sample frame index, channel levels, zero-lag correlation, identical sample
  fraction, clipping, GCC-PHAT lag/peak, and rotation-vector quaternion/timestamp.
  Channel 0/1 are not assumed to mean physical left/right. Positive lag means
  channel 1 arrived first. Levels are dBFS, not calibrated sound-pressure levels.

Snapshots are checked roughly once a second, so short route changes may be missed.
The CSV delivery timestamp is not exact capture time; use the JSON AudioTimestamp
frame/time pairs for alignment to Android boottime. Quaternion order is w,x,y,z in
Android's device coordinate system; display rotation is saved separately. NaN is
unavailable data. The exploratory delay search is +/-42 samples, not a measured
microphone-spacing constraint. GCC peak height is not a probability.

Differing channels or a DIRECT mapping are evidence to investigate, not a guarantee
of usable localization. Scaled copies of a mono signal, silence, and clipping are
flagged. Front/back ambiguity can remain even with useful stereo. The existing
left/middle/right classifier and calibration have not been changed.

### What analysis should establish

- Which source exposes distinct, synchronized signals that retain spatial cues?
- Does moving a sound left to right consistently reverse the measured delay?
- Are delay magnitudes physically plausible and repeatable over several claps?
- Do route/effect mappings change during recordings? Is the client silenced?
- Which cues confuse front with behind? Which clips are quiet, clipped or interrupted?

After this baseline, repeat the strongest source in another room and with speech
and other sound types. Test overlapping sources only after single-source behavior
is understood. The phone-only setup cannot be assigned an accuracy until those
measurements are collected.

## Build on Windows

Requires Java, Android SDK platform 34, build-tools 35.0.0, and platform-tools.
The script defaults to `%LOCALAPPDATA%\Android\Sdk`; override with `-Sdk` if needed.

```powershell
.\build.ps1 -RunTests -SideBySide
adb install -r .\build\SoundDirectionTest.apk
```

Without `-SideBySide`, the output is `build/BehindAlert.apk` under the original
package name. Updating an existing install requires its original signing key.
The first build creates a local debug key (excluded from Git). These are development
builds with debugging enabled so the USB collector can read their private test files.

```powershell
.\collect-diagnostics.ps1
```

The collector writes under `diagnostic-data/` (excluded from Git), using a binary
stream so Windows shell text encoding cannot corrupt recordings. Avoid recording
while collecting. No test audio is uploaded by the app or collector.
