# Bundled third-party files

Stored here so sound recognition and the wordmark work without runtime downloads.

| File | What | Source | License |
|---|---|---|---|
| `mediapipe/audio_bundle.mjs`, `mediapipe/wasm/*` | MediaPipe Tasks Audio 0.10.21, which runs the sound model in the browser | npm `@mediapipe/tasks-audio@0.10.21` (https://github.com/google-ai-edge/mediapipe) | Apache 2.0 |
| `models/yamnet.tflite` | YAMNet, Google's 521-class sound classifier (float32) | https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/1/yamnet.tflite (https://github.com/tensorflow/models/tree/master/research/audioset/yamnet) | Apache 2.0 |
| `fonts/ultra-latin.woff2` | Ultra by Astigmatic (Latin subset), the SYNESTHESIA wordmark | Google Fonts, https://fonts.google.com/specimen/Ultra | Apache 2.0 |

The model and runtime version match the Synesthesia Android app (github.com/RishabhK12/synesthesia,
`audio_classification` branch), whose listener `listen.js` ports. `yamnet.tflite` has the same SHA-256 as
that app's `models.lock.json`.

SHA-256:

```
4d8b4a53282dc83ef04e3e7dbc4fbc98082e34e44ed798e16c3a0cdd4c584faf  models/yamnet.tflite
74a3d1508218ccedb8566aac99e2e204865481c0aa1a006a61278fc984b12761  mediapipe/audio_bundle.mjs
6d16e3626ec3f39b85024f8769d9e796eb2c28b085c2525337c6bd6c3add2d20  mediapipe/wasm/audio_wasm_internal.js
a57c300fa8fe6756396c1718ddbe4d134e1361e973087ce192bcdab3eea528d1  mediapipe/wasm/audio_wasm_internal.wasm
b42fb30b0304a678b25a2171d1b09f10e62926fdba3debd8c5ee02fdd1173519  mediapipe/wasm/audio_wasm_nosimd_internal.js
cdd5c603a5225d85dbb30944fa1e66c46a76790ec246682c4f3d88c571b5a3a6  mediapipe/wasm/audio_wasm_nosimd_internal.wasm
bb83a02686be778d70d1d481cb9de2b7c33dd14b74cb8d9bcee94a88188dea02  fonts/ultra-latin.woff2
```

The `nosimd` pair is only loaded by browsers without WebAssembly SIMD (for example iOS before 16.4).
