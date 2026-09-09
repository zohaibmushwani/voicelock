# Manual diarization fixtures

Run `python download_librispeech_dummy.py` to download a few small FLAC fixtures into `audio/`.
They come from `sanchit-gandhi/librispeech_asr_dummy`, a truncated LibriSpeech dataset under
CC BY 4.0. The fixtures are intentionally not placed in Android assets or version control.

Play clips from two distinct `speaker_*.flac` files in alternating turns from a second phone or
computer near the test phone, then use **Speaker Diarization** → **Record and cluster speakers**.
The screen should show separate temporary `Speaker 1`, `Speaker 2`, etc. spans. These labels are
cluster IDs for the one recording, not verified enrolled identities.
