# Model preparation tools

These scripts are outside `app/`: Android packages only verified ONNX assets, while model
acquisition and inspection happen on a developer machine.

```powershell
python -m pip install -r tools/model_prep/requirements.txt
$env:HF_TOKEN = "..." # only if Hugging Face asks for authentication
python tools/model_prep/download_models.py
python tools/model_prep/inspect_onnx.py
python tools/model_prep/optimize_mobile.py
```

The downloader writes the ECAPA speaker model to `app/src/main/assets/models/`, validates its published SHA-256 value,
and reads an optional `HF_TOKEN` only from the process environment. It never writes tokens to
source, project files, or logs. Both selected models are already ONNX; no conversion is needed.
`optimize_mobile.py` optionally creates a validated INT8-weight ECAPA copy for smaller mobile
deployment. Voice activity detection is supplied by the Android WebRTC VAD library and needs no
separate model download.
