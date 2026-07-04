# ================================================================
#  Export.py     //PyTorch .pth  →  ONNX  →  INT8 Quantized ONNX
#
#  Run this on Kaggle AFTER training is complete.
#
#  Steps this script does:
#    1. Loads your best_model.pth checkpoint
#    2. Exports to model.onnx  (~85 MB, float32)
#    3. Quantizes to model_int8.onnx  (~22 MB, INT8)
#    4. Verifies the output matches the original model
#
#  Install first:
#    !pip install onnx onnxruntime -q
# ================================================================

import torch
import torch.nn as nn
import numpy as np
from torchvision import models

# ── onnx tools ───────────────────────────────────────────────
import onnx
import onnxruntime , onnxscript
from onnxruntime.quantization import (
    quantize_dynamic,
    QuantType,
)

# ── config ───────────────────────────────────────────────────
CHECKPOINT_PATH = ""                    # Model Checkpoint Path
ONNX_PATH       = ".../model.onnx"      # ONNX model Save Path
ONNX_INT8_PATH  = ".../model_int8.onnx" # INT8 Quantized ONNX model Save Path
IMG_SIZE        = 384
NUM_CLASSES     = 14

DISEASE_LABELS = [
    "Atelectasis", "Consolidation", "Infiltration",
    "Pneumothorax", "Edema", "Emphysema", "Fibrosis",
    "Effusion", "Pneumonia", "Pleural_Thickening",
    "Cardiomegaly", "Nodule", "Mass", "Hernia",
]


# ================================================================
#  STEP 1 — Rebuild model and load checkpoint
# ================================================================
def load_model(checkpoint_path: str, device: torch.device) -> nn.Module:
    print("\n[Step 1] Loading checkpoint …")

    model = models.efficientnet_v2_s(weights=None)
    in_features = model.classifier[1].in_features
    model.classifier = nn.Sequential(
        nn.Dropout(p=0.3, inplace=False),   # inplace=False required for ONNX export
        nn.Linear(in_features, NUM_CLASSES),
    )

    ckpt = torch.load(checkpoint_path, map_location=device, weights_only=False)
    model.load_state_dict(ckpt["model_state_dict"])
    model.eval()
    model = model.to(device)

    print(f"  ✔ Loaded checkpoint from epoch {ckpt.get('epoch','?')} "
          f"| AUC = {ckpt.get('auc', 0):.4f}")
    return model


# ================================================================
#  STEP 2 — Export to ONNX
# ================================================================
def export_to_onnx(model: nn.Module, onnx_path: str, img_size: int, device: torch.device):
    print(f"\n[Step 2] Exporting to ONNX → {onnx_path} …")

    # dummy input — batch=1, RGB, img_size × img_size
    dummy = torch.randn(1, 3, img_size, img_size, device=device)

    torch.onnx.export(
        model,
        dummy,
        onnx_path,
        export_params=True,
        opset_version    = 18,           # latest stable opset
        input_names      = ["image"],
        output_names     = ["logits"],
        dynamic_axes     = {
            "image":  {0: "batch_size"},  # batch dimension is dynamic
            "logits": {0: "batch_size"},
        },
        do_constant_folding = True,
        dynamo = False,
        verbose          = False,
    )

    # verify the ONNX file is valid
    onnx_model = onnx.load(onnx_path)
    onnx.checker.check_model(onnx_model)
    print(f"  ✔ ONNX model valid")

    # check file size
    import os
    size_mb = os.path.getsize(onnx_path) / 1e6
    print(f"  ✔ File size: {size_mb:.1f} MB")


# ================================================================
#  STEP 3 — INT8 Dynamic Quantization
# ================================================================
def quantize_model(onnx_path: str, int8_path: str):
    print(f"\n[Step 3] Quantizing → {int8_path} …")

    quantize_dynamic(
        model_input    = onnx_path,
        model_output   = int8_path,
        weight_type    = QuantType.QInt8,
    )

    import os
    size_mb = os.path.getsize(int8_path) / 1e6
    print(f"  ✔ Quantized model size: {size_mb:.1f} MB")


# ================================================================
#  STEP 4 — Verify both models give the same predictions
# ================================================================
def verify_models(
    model:      nn.Module,
    onnx_path:  str,
    int8_path:  str,
    img_size:   int,
    device:     torch.device,
):
    print("\n[Step 4] Verifying outputs match …")

    # random test image
    dummy_np = np.random.randn(1, 3, img_size, img_size).astype(np.float32)
    dummy_t  = torch.from_numpy(dummy_np).to(device)

    # PyTorch output
    with torch.no_grad():
        pt_logits = model(dummy_t).cpu().numpy()
        pt_probs  = 1 / (1 + np.exp(-pt_logits))   # sigmoid

    # ONNX float32 output
    sess_f32 = onnxruntime.InferenceSession(onnx_path)
    f32_logits = sess_f32.run(
        None, {"image": dummy_np}
    )[0]
    f32_probs = 1 / (1 + np.exp(-f32_logits))

    # ONNX INT8 output
    sess_int8 = onnxruntime.InferenceSession(int8_path)
    int8_logits = sess_int8.run(
        None, {"image": dummy_np}
    )[0]
    int8_probs = 1 / (1 + np.exp(-int8_logits))

    max_diff_f32  = np.abs(pt_probs - f32_probs).max()
    max_diff_int8 = np.abs(pt_probs - int8_probs).max()

    print(f"  Max prob diff  PyTorch vs ONNX f32 : {max_diff_f32:.6f}  "
          f"{'✔ OK' if max_diff_f32 < 0.001 else '⚠ CHECK'}")
    print(f"  Max prob diff  PyTorch vs ONNX int8 : {max_diff_int8:.6f}  "
          f"{'✔ OK' if max_diff_int8 < 0.01 else '⚠ CHECK'}")

    # print sample disease probabilities
    print("\n  Sample probabilities (first test image):")
    for i, disease in enumerate(DISEASE_LABELS):
        print(
            f"    {disease:<22}  "
            f"PT={pt_probs[0,i]:.4f}  "
            f"f32={f32_probs[0,i]:.4f}  "
            f"int8={int8_probs[0,i]:.4f}"
        )


# ================================================================
#  STEP 5 — Print Android integration instructions
# ================================================================
def print_android_instructions(int8_path: str):
    print("\n" + "="*60)
    print("  ANDROID INTEGRATION INSTRUCTIONS")
    print("="*60)
    print(f"""
  1. Download this file from Kaggle:
       {int8_path}

  2. In Android Studio, copy it to:
       app/src/main/assets/model_int8.onnx

  3. Add to app/build.gradle (Module):
       implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.17.0'

  4. The model expects:
       • Input  name : "image"
       • Input  shape: [1, 3, {IMG_SIZE}, {IMG_SIZE}]  float32
       • Output name : "logits"
       • Output shape: [1, 14]  float32  (raw logits, apply sigmoid)

  5. Preprocessing in Kotlin (same as Python val_transform):
       • Resize to {IMG_SIZE}×{IMG_SIZE}
       • Normalize: mean=[0.485, 0.456, 0.406]
                    std =[0.229, 0.224, 0.225]
       • Layout: NCHW float32 array

  6. Disease output order (index 0–13):
    """)
    for i, d in enumerate(DISEASE_LABELS):
        print(f"       [{i:2d}] {d}")
    print()


# ================================================================
#  MAIN
# ================================================================
def main():
    print("="*60)
    print("  PyTorch → ONNX → INT8  Export Pipeline")
    print("="*60)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Device: {device}")

    # Step 1
    model = load_model(CHECKPOINT_PATH, device)

    # Step 2
    export_to_onnx(model, ONNX_PATH, IMG_SIZE, device)

    # Step 3
    quantize_model(ONNX_PATH, ONNX_INT8_PATH)

    # Step 4
    verify_models(model, ONNX_PATH, ONNX_INT8_PATH, IMG_SIZE, device)

    # Step 5
    print_android_instructions(ONNX_INT8_PATH)

    print("\n[Done] Export complete!")
    print(f"  Float32 ONNX : {ONNX_PATH}")
    print(f"  INT8 ONNX    : {ONNX_INT8_PATH}  ← use this in Android")


if __name__ == "__main__":
    main()