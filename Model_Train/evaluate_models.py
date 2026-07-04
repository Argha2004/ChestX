# =============================================================
#  Evaluate.py  —  NIH Chest X-Ray14  |  Accuracy report
#  Matches "Tuned Run #2" (Config 2): 384px, 4-pass TTA.
#
#  What it reports:
#    1. Image-level EXACT-MATCH accuracy  (all 14 findings correct)
#         -> "X-rays detected fully correct" vs "wrong on >=1 finding"
#    2. Per-label (Hamming) accuracy       (correct decisions / N*14)
#    3. Per-class accuracy + TP/FP/FN/TN + precision/recall/F1 + AUC
#    4. Macro AUC / F1 / precision / recall
#
#  Run order on Kaggle:
#    Cell 1:  !pip install albumentations -q
#    Cell 2:  paste this file, set EVAL_CFG["CKPT_PATH"], run
#
#  IMPORTANT (honesty for the paper):
#    - AUC is the primary metric; accuracy is imbalance-sensitive.
#    - If USE_PER_CLASS_THRESHOLDS=True, thresholds are tuned on the
#      TEST set here for convenience; for a paper, tune them on a
#      validation split instead and state that clearly.
# =============================================================

import os
import numpy as np
import pandas as pd
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import Dataset, DataLoader
from torchvision import models
from PIL import Image
import glob
from tqdm import tqdm
from sklearn.metrics import (
    roc_auc_score, precision_score, recall_score, f1_score,
)

import albumentations as A
from albumentations.pytorch import ToTensorV2


# =============================================================
#  CONSTANTS
# =============================================================
DISEASE_LABELS = [
    "Atelectasis", "Consolidation", "Infiltration",
    "Pneumothorax", "Edema", "Emphysema", "Fibrosis",
    "Effusion", "Pneumonia", "Pleural_Thickening",
    "Cardiomegaly", "Nodule", "Mass", "Hernia"
]
IMAGENET_MEAN = (0.485, 0.456, 0.406)
IMAGENET_STD  = (0.229, 0.224, 0.225)


# =============================================================
#  CONFIG
# =============================================================
EVAL_CFG = dict(
    ROOT      = "",                  # Root directory of the dataset

    CKPT_PATH = "",                  # Path to the model checkpoint to evaluate
    SAVE_DIR  = "",                  # Directory to save evaluation CSVs

    MODEL     = "efficientnet_v2_s",   # "densenet201" | "densenet121" | "efficientnet_v2_s"
    IMG_SIZE  = 384,
    BATCH_SIZE = 64,
    TTA_PASSES = 4,              # set 1 for a fast (no-TTA) pass
    NUM_WORKERS = 4,

    THRESHOLD = 0.35,                 # global threshold (used if not per-class)
    USE_PER_CLASS_THRESHOLDS = False, # True -> pick each class's best-F1 threshold
    DROPOUT = 0.2,
    SEED = 42,
)


# =============================================================
#  TRANSFORM (val) + DATASET  (same as training)
# =============================================================
def build_val_transform(img_size: int = 384):
    return A.Compose([
        A.Resize(img_size, img_size),
        A.Normalize(mean=IMAGENET_MEAN, std=IMAGENET_STD),
        ToTensorV2(),
    ])


class ChestXrayDataset(Dataset):
    def __init__(self, csv_file, root_dir, file_list, transform=None, img_size=384):
        self.root_dir  = root_dir
        self.img_size  = img_size
        self.transform = transform

        print("[Data] Loading CSV …")
        df = pd.read_csv(csv_file)
        print("[Data] Loading file list …")
        with open(file_list) as f:
            self.image_names = [l.strip() for l in f if l.strip()]

        print("[Data] Indexing image files …")
        image_paths = glob.glob(os.path.join(root_dir, "**", "*.png"), recursive=True)
        self.image_dict = {os.path.basename(p): p for p in image_paths}
        print(f"[Data] Found {len(self.image_dict):,} PNG files")

        label_map    = dict(zip(df["Image Index"], df["Finding Labels"]))
        label_to_idx = {lbl: i for i, lbl in enumerate(DISEASE_LABELS)}

        n = len(self.image_names)
        label_matrix = np.zeros((n, len(DISEASE_LABELS)), dtype=np.float32)
        for row_idx, img_name in enumerate(self.image_names):
            raw = label_map.get(img_name, "No Finding")
            for disease in raw.split("|"):
                col = label_to_idx.get(disease)
                if col is not None:
                    label_matrix[row_idx, col] = 1.0
        self.labels = torch.from_numpy(label_matrix)
        print(f"[Data] Ready — {n:,} samples")

    def __len__(self):
        return len(self.image_names)

    def __getitem__(self, idx):
        img_name = self.image_names[idx]
        img_path = self.image_dict.get(img_name)
        if img_path is None:
            img_np = np.zeros((self.img_size, self.img_size, 3), dtype=np.uint8)
        else:
            try:
                with Image.open(img_path) as pil_img:
                    img_np = np.array(pil_img.convert("RGB"), dtype=np.uint8)
            except Exception:
                img_np = np.zeros((self.img_size, self.img_size, 3), dtype=np.uint8)
        img_tensor = self.transform(image=img_np)["image"]
        return img_tensor, self.labels[idx]


# =============================================================
#  MODEL  (build the same architecture, then load weights)
# =============================================================
def build_model(name: str, num_classes: int = 14, dropout: float = 0.2) -> nn.Module:
    if name == "densenet201":
        m = models.densenet201(weights=None)
        in_f = m.classifier.in_features
        m.classifier = nn.Sequential(nn.Dropout(p=dropout, inplace=True),
                                     nn.Linear(in_f, num_classes))
    elif name == "densenet121":
        m = models.densenet121(weights=None)
        in_f = m.classifier.in_features
        m.classifier = nn.Sequential(nn.Dropout(p=dropout, inplace=True),
                                     nn.Linear(in_f, num_classes))
    elif name == "efficientnet_v2_s":
        m = models.efficientnet_v2_s(weights=None)
        in_f = m.classifier[1].in_features
        m.classifier = nn.Sequential(nn.Dropout(p=dropout, inplace=True),
                                     nn.Linear(in_f, num_classes))
    else:
        raise ValueError(f"Unknown MODEL: {name}")
    return m


# =============================================================
#  TTA + INFERENCE
# =============================================================
@torch.no_grad()
def tta_predict(model, images, n_passes=4):
    preds = torch.sigmoid(model(images))
    if n_passes >= 2:
        preds = preds + torch.sigmoid(model(torch.flip(images, dims=[3])))
    if n_passes >= 4:
        H = images.shape[2]
        crop = int(H * 0.90)
        pad  = (H - crop) // 2
        c1 = images[:, :, pad:pad+crop, pad:pad+crop]
        c1 = F.interpolate(c1, size=(H, H), mode="bilinear", align_corners=False)
        preds = preds + torch.sigmoid(model(c1))
        preds = preds + torch.sigmoid(model(torch.flip(c1, dims=[3])))
    return preds / n_passes


@torch.no_grad()
def run_inference(model, loader, device, tta_passes=4):
    model.eval()
    all_labels, all_probs = [], []
    for images, labels in tqdm(loader, desc="Inference"):
        images = images.to(device, non_blocking=True)
        with torch.amp.autocast("cuda", enabled=(device.type == "cuda")):
            probs = tta_predict(model, images, n_passes=tta_passes)
        all_labels.append(labels.numpy())
        all_probs.append(probs.float().cpu().numpy())
    return np.vstack(all_labels), np.vstack(all_probs)


# =============================================================
#  THRESHOLDS + METRICS
# =============================================================
def per_class_best_thresholds(labels, probs, grid=None):
    if grid is None:
        grid = np.linspace(0.05, 0.95, 19)
    thr = np.full(labels.shape[1], 0.35, dtype=np.float32)
    for i in range(labels.shape[1]):
        best_f1, best_t = -1.0, 0.35
        for t in grid:
            yp = (probs[:, i] >= t).astype(int)
            f = f1_score(labels[:, i], yp, zero_division=0)
            if f > best_f1:
                best_f1, best_t = f, t
        thr[i] = best_t
    return thr


def compute_report(labels, probs, thresholds):
    """thresholds: array (14,). Returns dict of everything."""
    N, C = labels.shape
    preds = (probs >= thresholds[None, :]).astype(int)
    labs  = labels.astype(int)

    # ---- image-level exact match ----
    exact_correct = int((preds == labs).all(axis=1).sum())
    exact_wrong   = N - exact_correct

    # ---- per-label (Hamming) ----
    total_decisions = N * C
    correct_decisions = int((preds == labs).sum())

    # ---- per class ----
    rows = []
    for i, d in enumerate(DISEASE_LABELS):
        yt, yp = labs[:, i], preds[:, i]
        tp = int(((yp == 1) & (yt == 1)).sum())
        fp = int(((yp == 1) & (yt == 0)).sum())
        fn = int(((yp == 0) & (yt == 1)).sum())
        tn = int(((yp == 0) & (yt == 0)).sum())
        acc = (tp + tn) / max(N, 1)
        prec = precision_score(yt, yp, zero_division=0)
        rec  = recall_score(yt, yp, zero_division=0)
        f1   = f1_score(yt, yp, zero_division=0)
        try:
            auc_i = roc_auc_score(yt, probs[:, i]) if len(np.unique(yt)) > 1 else float("nan")
        except Exception:
            auc_i = float("nan")
        rows.append(dict(disease=d, thr=float(thresholds[i]), n_pos=int(yt.sum()),
                         TP=tp, FP=fp, FN=fn, TN=tn, accuracy=acc,
                         precision=prec, recall=rec, f1=f1, auc=auc_i))

    macro_auc = np.nanmean([r["auc"] for r in rows])
    macro_f1  = np.mean([r["f1"] for r in rows])
    macro_p   = np.mean([r["precision"] for r in rows])
    macro_r   = np.mean([r["recall"] for r in rows])

    return dict(
        N=N, C=C,
        exact_correct=exact_correct, exact_wrong=exact_wrong,
        exact_acc=exact_correct / max(N, 1),
        correct_decisions=correct_decisions, total_decisions=total_decisions,
        hamming_acc=correct_decisions / max(total_decisions, 1),
        rows=rows,
        macro_auc=macro_auc, macro_f1=macro_f1,
        macro_precision=macro_p, macro_recall=macro_r,
    )


def print_report(rep, threshold_desc):
    print("\n" + "=" * 68)
    print("  NIH ChestX-ray14  —  EVALUATION REPORT")
    print("=" * 68)
    print(f"  Test images        : {rep['N']:,}")
    print(f"  Findings per image : {rep['C']}")
    print(f"  Threshold          : {threshold_desc}")

    print("\n" + "-" * 68)
    print("  1) IMAGE-LEVEL EXACT-MATCH ACCURACY (all 14 findings correct)")
    print("-" * 68)
    print(f"  Fully correct X-rays : {rep['exact_correct']:,} / {rep['N']:,} "
          f"({100*rep['exact_acc']:.2f}%)")
    print(f"  Wrong on >=1 finding : {rep['exact_wrong']:,} / {rep['N']:,} "
          f"({100*(1-rep['exact_acc']):.2f}%)")
    print("  (Strictest metric — hard to score high on 14 simultaneous labels.)")

    print("\n" + "-" * 68)
    print("  2) PER-LABEL (HAMMING) ACCURACY (every disease decision)")
    print("-" * 68)
    print(f"  Correct decisions    : {rep['correct_decisions']:,} / "
          f"{rep['total_decisions']:,} ({100*rep['hamming_acc']:.2f}%)")
    print("  (High partly because most labels are negative — imbalance inflates it.)")

    print("\n" + "-" * 68)
    print("  3) PER-CLASS BREAKDOWN")
    print("-" * 68)
    hdr = (f"  {'Disease':<20}{'thr':>5}{'AUC':>8}{'Acc':>8}"
           f"{'Prec':>8}{'Rec':>8}{'F1':>8}{'TP':>7}{'FP':>7}{'FN':>7}")
    print(hdr)
    print("  " + "-" * (len(hdr) - 2))
    for r in rep["rows"]:
        print(f"  {r['disease']:<20}{r['thr']:>5.2f}{r['auc']:>8.4f}"
              f"{r['accuracy']:>8.4f}{r['precision']:>8.4f}{r['recall']:>8.4f}"
              f"{r['f1']:>8.4f}{r['TP']:>7}{r['FP']:>7}{r['FN']:>7}")

    print("\n" + "-" * 68)
    print("  4) MACRO AVERAGES")
    print("-" * 68)
    print(f"  Macro AUC       : {rep['macro_auc']:.4f}   <- primary metric")
    print(f"  Macro F1        : {rep['macro_f1']:.4f}")
    print(f"  Macro Precision : {rep['macro_precision']:.4f}")
    print(f"  Macro Recall    : {rep['macro_recall']:.4f}")
    print("=" * 68 + "\n")


# =============================================================
#  MAIN
# =============================================================
def main():
    torch.manual_seed(EVAL_CFG["SEED"]); np.random.seed(EVAL_CFG["SEED"])
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"[Eval] Device: {device}")
    if device.type == "cuda":
        print(f"[Eval] GPU   : {torch.cuda.get_device_name(0)}")
        torch.backends.cudnn.benchmark = True

    ROOT = EVAL_CFG["ROOT"]
    val_tf = build_val_transform(EVAL_CFG["IMG_SIZE"])

    test_ds = ChestXrayDataset(
        csv_file  = f"{ROOT}/Data_Entry_2017.csv",
        root_dir  = ROOT,
        file_list = f"{ROOT}/test_list.txt",
        transform = val_tf,
        img_size  = EVAL_CFG["IMG_SIZE"],
    )
    test_loader = DataLoader(
        test_ds, batch_size=EVAL_CFG["BATCH_SIZE"], shuffle=False,
        num_workers=EVAL_CFG["NUM_WORKERS"], pin_memory=True,
    )

    print(f"\n[Eval] Building {EVAL_CFG['MODEL']} and loading checkpoint …")
    model = build_model(EVAL_CFG["MODEL"], 14, EVAL_CFG["DROPOUT"]).to(device)
    ckpt = torch.load(EVAL_CFG["CKPT_PATH"], map_location=device, weights_only=False)
    state = ckpt["model_state_dict"] if "model_state_dict" in ckpt else ckpt
    missing, unexpected = model.load_state_dict(state, strict=False)
    if missing:    print(f"[Eval] Missing keys   : {missing}")
    if unexpected: print(f"[Eval] Unexpected keys: {unexpected}")
    if "auc" in ckpt:   print(f"[Eval] Checkpoint stored AUC   : {ckpt['auc']:.4f}")
    if "epoch" in ckpt: print(f"[Eval] Checkpoint stored epoch : {ckpt['epoch']}")

    print(f"\n[Eval] Running inference ({EVAL_CFG['TTA_PASSES']}-pass TTA) …")
    labels, probs = run_inference(model, test_loader, device, EVAL_CFG["TTA_PASSES"])

    if EVAL_CFG["USE_PER_CLASS_THRESHOLDS"]:
        thresholds = per_class_best_thresholds(labels, probs)
        thr_desc = "per-class best-F1 (tuned on test — see header caveat)"
    else:
        thresholds = np.full(14, EVAL_CFG["THRESHOLD"], dtype=np.float32)
        thr_desc = f"global {EVAL_CFG['THRESHOLD']}"

    rep = compute_report(labels, probs, thresholds)
    print_report(rep, thr_desc)

    # ---- save CSV summary ----
    os.makedirs(EVAL_CFG["SAVE_DIR"], exist_ok=True)
    df = pd.DataFrame(rep["rows"])
    summary_path = os.path.join(EVAL_CFG["SAVE_DIR"], "evaluation_per_class_efficientnet.csv")
    df.to_csv(summary_path, index=False)

    overall = pd.DataFrame([{
        "test_images": rep["N"],
        "exact_match_correct": rep["exact_correct"],
        "exact_match_wrong": rep["exact_wrong"],
        "exact_match_accuracy": rep["exact_acc"],
        "hamming_accuracy": rep["hamming_acc"],
        "macro_auc": rep["macro_auc"],
        "macro_f1": rep["macro_f1"],
        "macro_precision": rep["macro_precision"],
        "macro_recall": rep["macro_recall"],
    }])
    overall_path = os.path.join(EVAL_CFG["SAVE_DIR"], "evaluation_overall_efficientnet.csv")
    overall.to_csv(overall_path, index=False)
    print(f"[Eval] Saved: {summary_path}")
    print(f"[Eval] Saved: {overall_path}")


if __name__ == "__main__":
    main()3