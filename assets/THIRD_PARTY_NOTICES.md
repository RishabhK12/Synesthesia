# Bundled audio components

The app runs these components locally. The build retrieves pinned artifacts;
models.lock.json records their sources and SHA-256 checksums.

- YAMNet, Google / TensorFlow: https://github.com/tensorflow/models/tree/master/research/audioset/yamnet
  Model: https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/1/yamnet.tflite
  Apache License 2.0: https://github.com/tensorflow/models/blob/master/LICENSE
- MediaPipe Tasks Audio 0.10.21, Google: https://github.com/google-ai-edge/mediapipe
  Apache License 2.0: https://github.com/google-ai-edge/mediapipe/blob/master/LICENSE
- sherpa-onnx 1.13.8 and the English GigaSpeech keyword model:
  https://github.com/k2-fsa/sherpa-onnx
  Apache License 2.0: https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/LICENSE
  Model provenance: https://k2-fsa.github.io/sherpa/onnx/kws/pretrained_models/index.html
- SentencePiece 0.2.1, Google: https://github.com/google/sentencepiece/tree/v0.2.1
  Apache License 2.0: https://github.com/google/sentencepiece/blob/v0.2.1/LICENSE
  Uses its bundled protobuf-lite, Abseil, Darts and other upstream source components.
  The build preserves their license files in build/dependencies/sentencepiece-0.2.1.
- ONNX Runtime is included by sherpa-onnx:
  MIT License: https://github.com/microsoft/onnxruntime/blob/main/LICENSE
- Kotlin standard library 2.1.0:
  Apache License 2.0: https://github.com/JetBrains/kotlin/blob/v2.1.0/license/LICENSE.txt

The keyword-model archive's sample WAVs are packaged only in the separate test
APK. They are not included in the application or used as real-world accuracy data.
