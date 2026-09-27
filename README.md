# Synesthesia

An experimental sound display for a Samsung S23 Ultra. The Pico estimates a rough bearing from three analog microphone modules and an optional rear LM393 threshold sensor. The ESP32-WROOM-32 relays direction events over Bluetooth Low Energy (BLE). The [phone page](web/index.html) uses the phone microphone to classify sound with a bundled YAMNet model, then shows a visual cue. It can also watch for a saved name using Chrome's **on-device** speech recognition when that feature is available.

The page has no WebSocket client, simulator, or application server. It is a static site; an HTTPS host such as GitHub Pages is needed to open it in Chrome on the phone and use Web Bluetooth and the microphone. Audio never goes to the Pico, ESP32, or a project server. Name recognition is disabled if Chrome cannot process speech locally.

## Hardware

- [Pico wiring, calibration, and tests](hardware/pico/SETUP.md)
- [Pico to ESP32 wiring and BLE test](hardware/esp32/SETUP.md)

Save `hardware/pico/direction_test.py` and `hardware/pico/main.py` on the Pico. Save `hardware/esp32/ble_bridge.py` and `hardware/esp32/main.py` on the ESP32. Each board is powered from its own USB port. Connect Pico GP0 to ESP32 GPIO16 and connect their grounds. Do not connect their power pins together. Follow the linked guides to verify each stage before using the phone page.

## Phone setup

1. Publish `web/` as a **static HTTPS site**. After this branch is merged into the repository's default branch, enable **Settings → Pages → Build and deployment → GitHub Actions**, then run the **Publish phone page** workflow from the Actions tab. GitHub requires a manually run workflow to exist on the default branch. The page will be at your repository's Pages URL. Hosting files is the only web service involved; there is no live data server.
2. On the S23 Ultra, open that URL in **Chrome**. Enter your name and optionally two nicknames or pronunciation spellings, then tap **Save name**. These values stay in that browser's local storage.
3. Power the Pico and ESP32. Allow the Pico five quiet seconds to measure its background. Tap **Connect**, choose **SynDir**, and grant Bluetooth access. Chrome requires this tap for each connection; reloads may disconnect it.
4. Tap **Start** to classify sound using the phone microphone. Name alerts start at the same time if local recognition and its English language pack are available. Chrome may offer to download that pack. The **Name alerts** row reports whether it is listening or unavailable. If it is unavailable, other sound classification still works. Chrome must remain open and active for continuous cues.
5. Test with a helper: say the saved name, clap once directly ahead of the rig, and then from each side, leaving a quiet gap between tests. Compare the **Latest cue** with the known direction. In **Sensor setup and diagnostics**, set the rig-front angle offset if the rig points away from the back camera. Test multiple rooms and distances; reflected sound and the MAX9814's automatic gain can shift the result.

The two detection paths are separate: YAMNet classifies sound types; Chrome's local speech recognizer listens for the saved name. The older `audio_classification` Android branch uses a sherpa-onnx keyword model instead. Browser name recognition is an experimental substitute and depends on Chrome's support on the specific phone. A direction is attached only if a single plausible BLE event and a single phone recognition occur close together. With simultaneous sounds or uncertain sensor readings, the cue shows an unknown direction. The rear LM393 supplies a possible-behind hint, not a precise bearing. **Do not rely on this prototype for safety-critical alerts or navigation.**

## Development checks

Run `node --test web/web.test.mjs` to check packet decoding, direction matching, and name matching. To inspect the page on a computer, serve `web/` over `http://localhost` using any simple static file server; this is only for local development. A plain `file://` URL does not provide the secure browser features used by the phone page. No build step is required for the static site.

The previous Android-only experiment remains in Git history and in the `audio_classification` branch. `web/vendor/NOTICES.md` lists bundled model and runtime licenses.
