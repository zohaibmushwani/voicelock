# VoiceLock

VoiceLock is an experimental, fully on-device speaker-verification app for Android. It records guided enrollment samples, removes silence with WebRTC VAD, creates a speaker embedding with ECAPA-TDNN, and reports the cosine-similarity confidence for a later verification attempt.

The app uses Jetpack Compose and Navigation 3 with serializable sealed destinations. Enrollment, verification, permissions, retry paths, confidence results, and a compact in-app status log are represented directly in the UI so the flow remains understandable without Android Studio logcat.

## Voice pipeline

1. Capture mono 16 kHz PCM audio.
2. Detect and trim speech with the `gkonovalov/android-vad` WebRTC library; reject missing or very short speech.
3. Compute WeSpeaker-compatible 80-bin filter-bank features through the native C++/JNI extractor (16-bit amplitude scale, Hamming window, no inference dither, and per-utterance CMN).
4. Produce a normalized 192-dimensional ECAPA-TDNN speaker embedding with ONNX Runtime.
5. Average validated enrollment embeddings and encrypt the template at rest.
6. Compare a verification embedding with the template using cosine similarity.

Audio and biometric templates remain on the device. Voice similarity is probabilistic and this project should not be treated as the only protection for high-value or safety-critical access.

The displayed percentage is a cosine-similarity score, not a calibrated probability. The current acceptance threshold is `0.70`; initial Pixel 7 testing after reference-compatible preprocessing measured genuine attempts at `0.744–0.843` (including an unseen sentence) and impostor attempts at `0.391–0.450`. A larger representative evaluation is required before production use.

## Build

Requirements:

- Android Studio with JDK 11 or newer
- Android SDK 37
- CMake 3.22.1 and an Android NDK supporting `arm64-v8a` or `armeabi-v7a`

Model files are intentionally not committed. Prepare them first with the Python tooling below; it writes the required files to `app/src/main/assets/models/`. Then build with:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## ADB diagnostics

VoiceLock writes privacy-safe pipeline events under one logcat tag. Raw PCM, embeddings, and stored templates are never logged.

```powershell
adb logcat -v time VoiceLock:I *:S
```

This shows microphone setup, captured duration, VAD decisions, FBank dimensions, model loading and inference timing, enrollment progress, and the final verification score and threshold. The most recent user-friendly events are also displayed in the status panel at the bottom of every app screen.

## Model preparation tools

The reproducible download, inspection, and mobile quantization scripts live separately from the Android app under `tools/model_prep/`:

```powershell
python -m pip install -r tools/model_prep/requirements.txt
python tools/model_prep/download_models.py
python tools/model_prep/inspect_onnx.py
python tools/model_prep/optimize_mobile.py
```

`download_models.py` validates the known SHA-256 hash. If Hugging Face requires authentication, provide `HF_TOKEN` only as a process environment variable; never add it to this repository. The optimizer produces `voxceleb_ECAPA512_LM.int8.onnx` and validates its output against the FP32 model. VAD uses the model-free WebRTC module from `gkonovalov/android-vad`; no VAD weight download is required. All generated files under `app/src/main/assets/models/` are ignored by Git.

## Model attribution

- `voxceleb_ECAPA512_LM.onnx` and its derived INT8 copy come from [Wespeaker/wespeaker-ecapa-tdnn512-LM](https://huggingface.co/Wespeaker/wespeaker-ecapa-tdnn512-LM), revision `a2f3dcb1c8702caccc7a55ceb57f5e8d1842112b`, licensed CC BY 4.0.
- Voice activity detection uses the MIT-licensed [gkonovalov/android-vad](https://github.com/gkonovalov/android-vad) WebRTC module, version `2.0.10`.

Review the upstream model cards and licenses before redistribution or production use.
