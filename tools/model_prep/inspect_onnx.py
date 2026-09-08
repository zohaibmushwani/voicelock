"""Print ONNX input and output metadata for Android binding review."""

from pathlib import Path

import onnx

MODELS = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets" / "models"


def main() -> None:
    for path in sorted(MODELS.glob("*.onnx")):
        if path.stat().st_size == 0:
            raise RuntimeError(f"Incomplete download: {path.name}")
        graph = onnx.load(path, load_external_data=False).graph
        print(f"\n{path.name}")
        for label, values in (("inputs", graph.input), ("outputs", graph.output)):
            print(f"  {label}:")
            for value in values:
                shape = [dim.dim_value or dim.dim_param or "?" for dim in value.type.tensor_type.shape.dim]
                print(f"    {value.name}: {shape}")


if __name__ == "__main__":
    main()
