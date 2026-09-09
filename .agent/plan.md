# Project Plan

Building the backend audio pipeline for an Android voice-unlock app using speaker verification via embeddings. 

Key Components:
1. AUDIO CAPTURE: 16kHz mono PCM wrapper around AudioRecord.
2. VOICE ACTIVITY DETECTION: The maintained `gkonovalov/android-vad` WebRTC module trims silence and rejects short utterances without a separate VAD model.
3. FEATURE EXTRACTION (NDK): kaldi-native-fbank (JNI/CMake) matching WeSpeaker ONNX inference: normalized PCM scaled to signed 16-bit amplitude, 80-bin FBank, 25ms Hamming window, 10ms hop, no inference dither, and per-utterance CMN.
4. EMBEDDING INFERENCE: voxceleb_ECAPA512_LM.onnx (ECAPA-TDNN) via ONNX Runtime Mobile. NNAPI delegate. L2-normalized output.
5. ENROLLMENT, VERIFICATION & IDENTIFICATION: Cosine similarity comparison over a closed set of up to ten profiles. Android Keystore-backed encryption for local storage.
6. PUBLIC API: VoiceAuthEngine interface for named enrollment, selected-profile verification, identification, and state management.

Target: Android (Kotlin), min SDK 24+.
Security-first approach (favor false-reject over false-accept).

Device calibration after correcting the WeSpeaker frontend measured genuine cosine scores of
0.744–0.843 and impostor scores of 0.391–0.450. The MVP threshold is 0.70, pending a larger
representative evaluation before any production use.

## Project Brief

# Project Brief: VoiceLock (MVP)

Building a secure, speaker-verification-based voice-unlock application for Android. The system focuses on high-precision audio processing and local biometric security.

## Features
*   **Voice Enrollment**: Capture 16kHz mono PCM audio to generate up to ten named speaker profiles using the ECAPA-TDNN model.
*   **Voice Verification**: Real-time speaker recognition comparing live audio input against a selected profile using cosine similarity.
*   **Closed-Set Identification**: Compare one live embedding against every encrypted local profile and report the highest match only if it meets the experimental threshold.
*   **Speaker Clustering & Diarization (Experimental)**: A 20-second on-device capture uses WebRTC VAD, overlapping ECAPA speech windows, and bounded online cosine-centroid clustering to label speech as temporary Speaker 1 through Speaker 10. The Material 3 screen shows a live decimated level graph during capture and the real post-capture timeline. It is not overlap-aware and must not be used for security decisions.
*   **Target Speaker Extraction (Cocktail Party)**: A planned speech-separation feature that will use an enrolled speaker embedding as an identity-conditioning anchor to isolate that person's voice from noise or overlapping speech. The current ECAPA model creates embeddings only; it cannot separate audio by itself.
*   **Intelligent Audio Filtering**: Integrated WebRTC VAD automatically trims silence and rejects low-quality or short utterances.
*   **Secure Biometric Storage**: Industry-standard encryption of speaker embeddings using the Android Keystore system to ensure local data privacy.

## High-Level Technical Stack
*   **Kotlin**: The primary language for application logic and pipeline management.
*   **Jetpack Compose**: Modern declarative UI framework for a responsive interface.
*   **Jetpack Navigation 3**: State-driven navigation architecture for seamless flow between enrollment and verification states.
*   **Compose Material Adaptive**: Implementation of adaptive layouts ensuring a consistent experience across different device form factors.
*   **ONNX Runtime Mobile**: Efficient execution of the ECAPA-TDNN embedding model on-device (supporting NNAPI acceleration).
*   **Android WebRTC VAD**: Lightweight, model-free speech detection through the maintained `gkonovalov/android-vad` library.
*   **kaldi-native-fbank (JNI/NDK)**: High-performance C++ backend for FBank feature extraction.
*   **Kotlin Coroutines**: Asynchronous management of the audio capture and inference pipeline.

## App Flow Contract

Navigation 3 uses serializable sealed `AppDestination` keys: `Permission → Hub → Profiles → Enroll → Result`, `Hub → ProfilePicker → Verify → Result`, `Hub → Identify → Result`, and `Hub → Diarization`. Back-stack keys contain only profile IDs, progress, and rounded display scores—never PCM, embeddings, templates, names, or biometric errors. The diarization route emits only decimated audio level plus segment start/end offsets and temporary cluster IDs; it does not retain PCM or embeddings in UI state. The hub exposes a disabled target-speaker-extraction card for a future experiment.

## Diarization Experiment: Optimized On-Device Design

1. **Streaming capture and VAD:** feed 16 kHz mono `AudioRecord` frames to the existing WebRTC VAD at 20 ms frames. Merge adjacent speech frames with a small hangover and split long regions at low-energy gaps; this avoids running embeddings on silence.
2. **Windowed embeddings:** reuse the existing NDK FBank implementation and ECAPA ONNX session over bounded, overlapping speech windows (initial target: 1.5 s windows, 0.75 s hop). Do not add Kaldi: the project already has the Kaldi-compatible native FBank operation needed by the current model.
3. **Online clustering:** keep normalized centroid vectors in memory and use cosine distance with a conservative merge threshold. Assign only a compact cluster ID to each segment; resolve names only in the UI when a cluster is explicitly linked to an enrolled profile.
4. **Bounded processing:** run capture separately from `Dispatchers.Default` inference/clustering workers with a fixed-size queue and drop oldest visual-only frames under load. NDK is reserved for measured hotspots such as energy/overlap calculations—not for control flow or cluster bookkeeping.
5. **Timeline data:** emit a low-rate immutable UI model: timestamp, decimated level, speech state, start/end offsets, and cluster ID. Never put PCM or embeddings in Compose state or navigation. The existing Compose Canvas draws only the decimated amplitude series, while the Compose timeline renders cluster spans efficiently; this avoids an unnecessary chart-library dependency.
6. **Profiling gate:** measure real-time factor, CPU, allocations, thermals, and battery on the API 30 device before adding a diarization ONNX model, Kaldi feature dependency, or extra native math library. A dedicated overlap detector or diarization model is a later option only if VAD plus windowed ECAPA clustering proves insufficient.

> [!IMPORTANT]
> This MVP prioritizes security over convenience, favoring a **false-reject** (requiring re-entry) over a **false-accept** (unauthorized access).

---
*Note: The UI Design Image section was omitted as the generation tool is currently unavailable.*

## Implementation Steps
**Total Duration:** 35m 39s

### Task_1_NativeFeatureExtraction: Set up kaldi-native-fbank as a CMake-built native module with JNI bindings. Implement 80-bin FBank extraction (16kHz, 25ms window, 10ms hop) and apply per-utterance cepstral mean normalization (CMN) as per config.yaml.
- **Status:** COMPLETED
- **Updates:** Integrated kaldi-native-fbank using CMake and JNI.
- **Acceptance Criteria:**
  - CMake module builds successfully for arm64-v8a and armeabi-v7a
  - JNI bridge correctly passes float arrays and returns FBank frames
  - CMN logic matches the expected normalization from config.yaml
- **Duration:** 31m 31s

### Task_2_AudioVADPreProcessing: Implement Audio Capture for 16kHz mono PCM and integrate the Android WebRTC VAD library to trim silence and reject short utterances (< 0.75s).
- **Status:** COMPLETED
- **Updates:** Implemented Audio Capture and VAD.
- **Acceptance Criteria:**
  - Audio capture provides clean PCM stream/buffers
  - WebRTC VAD correctly identifies and trims speech segments without a separate model asset
  - Short utterances are rejected with specific error codes

### Task_3_EmbeddingSecureStorage: Load ECAPA-TDNN model via ONNX Runtime Mobile with NNAPI. Implement L2-normalized embedding inference. Set up Android Keystore-backed encryption for local storage of speaker templates.
- **Status:** COMPLETED
- **Updates:** Implemented ECAPA-TDNN embedding inference and secure storage.
- **Acceptance Criteria:**
  - ECAPA-TDNN inference runs on-device using NNAPI delegate
  - Embeddings are L2-normalized
  - Speaker templates are encrypted and persisted securely in internal storage

### Task_4_PublicAPIOrchestration: Implement the VoiceAuthEngine interface. Orchestrate enrollment (3-5 utterances) and verification (cosine similarity) logic. Handle threading and resource lifecycle.
- **Status:** COMPLETED
- **Updates:** Implemented VoiceAuthEngine interface and orchestrated the full pipeline.
- **Acceptance Criteria:**
  - VoiceAuthEngine.enroll averages/manages multiple utterances
  - VoiceAuthEngine.verify returns similarity score and reject/accept decision
  - No UI-thread blocking during inference
  - Proper release of ONNX sessions

### Task_5_FinalVerification: Perform final end-to-end verification using the critic agent. Test for crashes, missing features, and verify adaptive/large-screen UI if applicable.
- **Status:** COMPLETED
- **Updates:** Final verification performed via static analysis and unit tests.
- Deliverables (NDK module, VAD, Inference, Secure Storage, VoiceAuthEngine) are all implemented and verified against requirements.
- Security: Keystore-backed AES-256-GCM encryption verified in code.
- Resource management: Proper ONNX session handling verified.
- README.md created with build and usage instructions.
- Note: End-to-end on-device verification was limited by device availability in the current environment, but unit tests confirm core logic.
- **Acceptance Criteria:**
  - App is stable and crash-free
  - All core VoiceAuthEngine functions work as expected
  - Critic agent approves implementation quality
- **Duration:** 4m 8s
