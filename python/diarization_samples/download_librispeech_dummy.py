"""Download a few CC-BY-4.0 LibriSpeech-derived FLAC fixtures for manual diarization tests.

The app never packages or reads these files. Play them from a second device near the phone while
using the Diarization experiment, or use them from local desktop test tooling.
"""

from __future__ import annotations

import base64
import json
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import urlopen

DATASET = "sanchit-gandhi/librispeech_asr_dummy"
SPLITS = ("validation.clean", "test.clean", "test.other", "validation.other")
OUTPUT = Path(__file__).parent / "audio"


def fetch_rows(split: str) -> list[dict]:
    query = urlencode({"dataset": DATASET, "config": "default", "split": split, "offset": 0, "length": 4})
    with urlopen(f"https://datasets-server.huggingface.co/rows?{query}", timeout=30) as response:
        return json.load(response)["rows"]


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    selected_speakers: set[int] = set()
    for split in SPLITS:
        try:
            rows = fetch_rows(split)
        except Exception as error:  # A split name can change; continue with the available fixtures.
            print(f"Skipping {split}: {error}")
            continue
        for item in rows:
            row = item["row"]
            speaker_id = int(row["speaker_id"])
            if speaker_id in selected_speakers:
                continue
            encoded = row["audio"].get("bytes")
            if not encoded:
                continue
            audio = base64.b64decode(encoded)
            destination = OUTPUT / f"speaker_{speaker_id}_{row['id']}.flac"
            destination.write_bytes(audio)
            print(f"Downloaded {destination.name} ({len(audio) // 1024} KiB) from {split}")
            selected_speakers.add(speaker_id)
            break
        if len(selected_speakers) >= 3:
            break
    if len(selected_speakers) < 2:
        raise SystemExit("Could not fetch two distinct speakers; inspect the dataset splits and retry.")


if __name__ == "__main__":
    main()
