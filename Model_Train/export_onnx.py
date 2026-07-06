#========================================================
# convert.py — Convert a ChestX .pth checkpoint into a True-CAM-compatible float32 ONNX.
#
# Writes into the output folder:
#   model_float32.onnx        logits [1,14] + feature maps [1,1280,12,12]
#   classifier_weights.bin    [14,1280] float32 (needed for on-device True CAM)
#
#========================================================

import os
import sys

import torch
import torch.nn as nn
import torchvision.models as tvm

NUM_CLASSES = 14
IMG_SIZE = 384
DROPOUT = 0.2
OPSET = 13


class WithFeatures(nn.Module):
    """forward() returns (logits, last-conv feature maps)."""
    def __init__(self, base):
        super().__init__()
        self.base = base

    def forward(self, x):
        feats = self.base.features(x)               # [1, 1280, 12, 12]
        pooled = torch.flatten(self.base.avgpool(feats), 1)
        logits = self.base.classifier(pooled)        # [1, 14]
        return logits, feats


def load_state_dict(pth_path):
    ckpt = torch.load(pth_path, map_location="cpu", weights_only=False)
    sd = ckpt.get("model_state_dict", ckpt.get("state_dict", ckpt)) if isinstance(ckpt, dict) else ckpt
    return {k.replace("module.", ""): v for k, v in sd.items()}


def main():
    # ── 1) get the input .pth path ──
    pth_path = sys.argv[1] if len(sys.argv) > 1 else input("Path to your .pth file: ").strip('"')
    if not os.path.isfile(pth_path):
        sys.exit(f"❌ File not found: {pth_path}")

    # ── 2) get the output folder ──
    out_dir = sys.argv[2] if len(sys.argv) > 2 else input("Output folder (Enter = current folder): ").strip('"')
    out_dir = out_dir or "."
    os.makedirs(out_dir, exist_ok=True)
    onnx_path = os.path.join(out_dir, "model_float32.onnx")
    weights_path = os.path.join(out_dir, "classifier_weights.bin")

    # ── 3) build model + load weights (strict=True catches any architecture mismatch) ──
    model = tvm.efficientnet_v2_s(weights=None, num_classes=NUM_CLASSES, dropout=DROPOUT)
    sd = load_state_dict(pth_path)
    model.load_state_dict(sd, strict=True)
    model.eval().float()
    print("✅ Weights loaded successfully.")

    # ── 4) sanity check (catches saturated/garbage logits early) ──
    with torch.no_grad():
        probs = torch.sigmoid(model(torch.randn(1, 3, IMG_SIZE, IMG_SIZE)))[0]
        print("Sample outputs:", [round(p, 3) for p in probs[:5].tolist()])
        if probs.min().item() > 0.9:
            print("⚠️  All values are near 1.0 — something may be wrong.")

    # ── 5) export the ONNX (logits + feature maps) ──
    export_model = WithFeatures(model).eval().float()
    dummy = torch.randn(1, 3, IMG_SIZE, IMG_SIZE)
    torch.onnx.export(
        export_model, dummy, onnx_path,
        input_names=["image"], output_names=["logits", "features"],
        opset_version=OPSET, do_constant_folding=True,
    )
    print(f"✅ Saved {onnx_path}")

    # ── 6) dump the classifier weights for True CAM ──
    W = sd["classifier.1.weight"].detach().float().cpu().numpy()   # [14, 1280]
    W.astype("<f4").tofile(weights_path)
    print(f"✅ Saved {weights_path}  shape={W.shape}")

    print(f"\nDone! Files are in: {os.path.abspath(out_dir)}")
    print("Copy both files into your app's assets folder.")


if __name__ == "__main__":
    main()