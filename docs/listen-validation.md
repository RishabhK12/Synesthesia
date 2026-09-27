# Listen validation

## Automated checks

Build and host tests:

```powershell
.\build.ps1 -RunTests -SideBySide
```

Device model tests (connected ARM64 Android phone):

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:GRADLE_USER_HOME = "$PWD\.gradle-user"
.\gradlew.bat connectedSideBySideDebugAndroidTest
```

These tests run the actual packaged tokenizer, keyword model, and YAMNet. They
check a known keyword in upstream speech, reset/restart behavior, speech
classification, and silence. They do not establish environmental sound accuracy
or recognition accuracy for user names. The host tests cover filtering,
anti-aliasing, block continuity, category controls, simultaneous detections,
cooldowns, queue overflow and the existing direction/recording regression suite.

## Human listening evaluation

Use the S23 Ultra's built-in microphones, with Bluetooth disconnected. Keep the
case, grip and distances consistent and record them in the test log. Use an
external speaker for reproducible playback; include real sounds where practical.

1. Collect at least 20 held-out examples for each of Horn, Siren, Alarm, Doorbell,
   and Knock. Include brief and repeated examples, quiet rooms and background
   conversation, and multiple distances. Use separate recordings for threshold
   tuning and final evaluation.
2. Test at least 10 names with multiple speakers. Include accents, nicknames,
   unfamiliar names, similar-sounding words, names alone and names in sentences.
   Test name-plus-horn overlap without expecting either source's direction.
3. Listen to at least one hour of ordinary conversation, music and unrelated
   sounds with none of the selected target names. Count false alerts by category.
4. Run the app for 30 minutes. Check microphone status, detector failures/audio
   gaps, battery drain, temperature, memory growth and alert delay. Pause and
   restart afterward. Confirm the screen stays responsive.
5. Repeat a short test in airplane mode. Turn airplane mode back off afterward.
6. Deny microphone permission, turn off microphone privacy access, switch apps,
   lock the screen and return. Confirm there is no stale alert or recording after
   Stop or leaving the screen. Repeatedly Start/Stop and rotate the phone.
7. Check large system text, readable simultaneous cards, name alert persistence,
   category controls and vibration with deaf or hard-of-hearing participants.
8. Confirm previous compass calibration loads, microphone diagnostics still
   record correctly, and exported ZIPs contain the expected WAV/CSV/JSON files.

Suggested log columns: trial ID, sound/name, speaker, environment, distance,
ground-truth onset/end, displayed event, alert time, missed event, false event,
model/build version, notes. Do not store surrounding audio unless participants
have agreed and explicit diagnostic recording is enabled.

Targets, not measured claims: at least 90% detection in quiet controlled trials,
at most two false alerts per hour, environmental alerts within two seconds of
onset, and name alerts within one second after the name finishes. Report recall
and false-alert rate per category rather than one aggregate accuracy number.

## Tuning

Environmental thresholds live in EventFilter.rule; category labels are mapped
in EventFilter.category. Start with per-category threshold changes on the tuning
set, then reevaluate on held-out examples. A strong window can trigger alone;
weaker windows need adjacent evidence. Events within 1.5 seconds are merged.
Names use a 2.5-second per-name cooldown. Do not describe model scores as accuracy.

The current thresholds are provisional. If threshold changes cannot meet the
targets, evaluate a small classifier trained on YAMNet embeddings with recordings
from this microphone path. Name pronunciation failures need separate keyword
evaluation; environmental fine-tuning does not improve spoken name recognition.
