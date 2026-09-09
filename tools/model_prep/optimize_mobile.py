"""Create and validate an INT8-weight mobile copy of the ECAPA ONNX model."""

from __future__ import annotations

from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
from onnxruntime.quantization import QuantType, quantize_dynamic

ROOT = Path(__file__).resolve().parents[2]
MODELS = ROOT / "app" / "src" / "main" / "assets" / "models"
SOURCE = MODELS / "voxceleb_ECAPA512_LM.onnx"
OUTPUT = MODELS / "voxceleb_ECAPA512_LM.int8.onnx"


def normalized_output(path: Path, features: np.ndarray) -> np.ndarray:
    session = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
    name = session.get_inputs()[0].name
    vector = np.asarray(session.run(None, {name: features})[0], dtype=np.float32).reshape(-1)
    return vector / np.linalg.norm(vector)


def main() -> None:
    if not SOURCE.exists():
        raise RuntimeError(f"Missing source model: {SOURCE}")
    # Quantize only dense operators. Convolutions and attention pooling stay float for fidelity.
    quantize_dynamic(
        model_input=SOURCE,
        model_output=OUTPUT,
        weight_type=QuantType.QInt8,
        op_types_to_quantize=["MatMul", "Gemm"],
        extra_options={"EnableSubgraph": True},
    )
    onnx.checker.check_model(OUTPUT)
    # Regression guard: cosine agreement must be near-identical on a deterministic fbank80 input.
    features = np.linspace(-2.0, 2.0, 200 * 80, dtype=np.float32).reshape(1, 200, 80)
    agreement = float(np.dot(normalized_output(SOURCE, features), normalized_output(OUTPUT, features)))
    if agreement < 0.99:
        OUTPUT.unlink(missing_ok=True)
        raise RuntimeError(f"Quantized model failed embedding agreement: {agreement:.6f}")
    print(f"created {OUTPUT.name}; embedding cosine agreement={agreement:.6f}")


if __name__ == "__main__":
    main()
