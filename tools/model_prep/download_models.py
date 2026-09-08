"""Download immutable, verified VoiceLock ONNX assets without persisting credentials."""

from __future__ import annotations

import hashlib
import os
import shutil
from pathlib import Path

from huggingface_hub import hf_hub_download

ROOT = Path(__file__).resolve().parents[2]
DESTINATION = ROOT / "app" / "src" / "main" / "assets" / "models"
MODELS = (
    ("Wespeaker/wespeaker-ecapa-tdnn512-LM", "a2f3dcb1c8702caccc7a55ceb57f5e8d1842112b", "voxceleb_ECAPA512_LM.onnx", "voxceleb_ECAPA512_LM.onnx", "d71b85d9b48058ef68004f04f1b78acebefb9dfcf542e19b976a12a5ad1f10b0"),
    ("Wespeaker/wespeaker-ecapa-tdnn512-LM", "a2f3dcb1c8702caccc7a55ceb57f5e8d1842112b", "config.yaml", "ecapa_config.yaml", ""),
    ("bitsydarel/silero-vad-onnx", "main", "silero_vad_v6.2.1.onnx", "silero_vad_v6.2.1.onnx", "1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3"),
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> None:
    DESTINATION.mkdir(parents=True, exist_ok=True)
    token = os.environ.get("HF_TOKEN")
    for repo, revision, source_name, destination_name, expected_hash in MODELS:
        downloaded = Path(hf_hub_download(repo_id=repo, revision=revision, filename=source_name, token=token))
        if expected_hash and sha256(downloaded) != expected_hash:
            raise RuntimeError(f"SHA-256 verification failed for {source_name}")
        shutil.copyfile(downloaded, DESTINATION / destination_name)
        print(f"verified: {destination_name}")


if __name__ == "__main__":
    main()
