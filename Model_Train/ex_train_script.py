# ================================================================
#  Train_T4.py  —  NIH Chest X-Ray14  |  Experiment Run
#  Kaggle T4 (16GB) edition
#
#
#  KEY CHANGES vs the H100 script (and why):
#  ─────────────────────────────────────────────────────────────
#  1. ConvNeXt-Base -> ConvNeXt-SMALL (still .fb_in22k_ft_in1k).
#     You KEEP the ImageNet-21K pretraining — the single biggest
#     quality lever — but at 50M params instead of 88M so it fits.
#  2. 448px -> 384px input. 448 at batch 64 needs ~28GB; impossible
#     on a T4. 384 keeps most of the fine-detail benefit.
#  3. batch 64 -> 16 + GRAD ACCUMULATION x2 (effective batch 32).
#  4. GRADIENT CHECKPOINTING on — trades ~20% speed for big VRAM
#     savings so the model fits with headroom.
#  5. Per-epoch validation = 1-pass, on a fixed 8k SUBSET of test.
#     Full 4-pass TTA on the WHOLE test set runs ONCE at the end.
#     This is what keeps the run inside 12 hours on a slow T4.
#  6. EPOCHS 30 -> 18. Every prior run peaked by epoch 8-10; the
#     data ceiling means more epochs just memorise noise.
#  7. num_workers 8 -> 2 (Kaggle GPU notebooks have ~4 vCPUs).
#  8. TF32 flags removed — Turing (T4) has no TF32; they're no-ops.
#
#  REALISTIC TARGET on T4: 0.83 - 0.86 macro AUC (single model).
#  Deploy the BEST EMA checkpoint.
#
#  MODEL SWITCHER: change CFG["MODEL_NAME"] if you want:
#    - "convnext_tiny.fb_in22k_ft_in1k"   (28M, fastest/safest on time)
#    - "convnext_small.fb_in22k_ft_in1k"  (50M, DEFAULT — best balance)
#    - "convnext_base.fb_in22k_ft_in1k"   (88M, ONLY on T4 x2 / longer)
#
#  Run order:
#    Cell 1:  !pip install timm albumentations -q
#    Cell 2:  paste this entire file, run
# ================================================================

import os
import time
import copy
import numpy as np
import pandas as pd
import torch
import torch.nn as nn
import torch.nn.functional as F
from torch.utils.data import Dataset, DataLoader, Subset
from PIL import Image
import glob
from tqdm import tqdm
from sklearn.metrics import (
    roc_auc_score, roc_curve, auc,
    confusion_matrix, classification_report,
    precision_score, f1_score, recall_score,
)
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import seaborn as sns

import timm
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
#  TRANSFORMS  (384px)
# =============================================================
def build_train_transform(img_size: int = 384):
    return A.Compose([
        A.Resize(img_size, img_size),
        A.HorizontalFlip(p=0.5),
        A.Affine(
            translate_percent=(0.0, 0.04),
            scale=(0.92, 1.08),
            rotate=(-8, 8),
            mode=0,
            p=0.5,
        ),
        A.GridDistortion(num_steps=4, distort_limit=0.08, p=0.25),
        A.CLAHE(clip_limit=2.5, tile_grid_size=(8, 8), p=0.5),
        A.RandomBrightnessContrast(
            brightness_limit=0.15, contrast_limit=0.15, p=0.5
        ),
        A.GaussNoise(std_range=(0.01, 0.05), p=0.15),
        A.Normalize(mean=IMAGENET_MEAN, std=IMAGENET_STD),
        ToTensorV2(),
    ])


def build_val_transform(img_size: int = 384):
    return A.Compose([
        A.Resize(img_size, img_size),
        A.Normalize(mean=IMAGENET_MEAN, std=IMAGENET_STD),
        ToTensorV2(),
    ])


# =============================================================
#  DATASET
# =============================================================
class ChestXrayDataset(Dataset):
    def __init__(
        self,
        csv_file: str,
        root_dir: str,
        file_list: str,
        transform=None,
        img_size: int = 384,
    ):
        self.root_dir  = root_dir
        self.img_size  = img_size
        self.transform = transform

        print("[Dataset] Loading CSV …")
        df = pd.read_csv(csv_file)

        print("[Dataset] Loading file list …")
        with open(file_list) as f:
            self.image_names = [l.strip() for l in f if l.strip()]

        print("[Dataset] Indexing image files …")
        image_paths = glob.glob(
            os.path.join(root_dir, "**", "*.png"), recursive=True
        )
        self.image_dict = {os.path.basename(p): p for p in image_paths}
        print(f"[Dataset] Found {len(self.image_dict):,} PNG files")

        label_map    = dict(zip(df["Image Index"], df["Finding Labels"]))
        patient_map  = dict(zip(df["Image Index"], df["Patient ID"])) \
                       if "Patient ID" in df.columns else None
        label_to_idx = {lbl: i for i, lbl in enumerate(DISEASE_LABELS)}

        print("[Dataset] Encoding labels …")
        n = len(self.image_names)
        label_matrix = np.zeros((n, len(DISEASE_LABELS)), dtype=np.float32)
        self.patient_ids = []

        for row_idx, img_name in enumerate(self.image_names):
            raw = label_map.get(img_name, "No Finding")
            for disease in raw.split("|"):
                col = label_to_idx.get(disease)
                if col is not None:
                    label_matrix[row_idx, col] = 1.0
            if patient_map is not None:
                self.patient_ids.append(
                    str(patient_map.get(img_name, img_name.split("_")[0]))
                )
            else:
                self.patient_ids.append(img_name.split("_")[0])

        self.labels      = torch.from_numpy(label_matrix)
        self.labels_list = DISEASE_LABELS
        n_patients = len(set(self.patient_ids))
        print(f"[Dataset] Ready — {n:,} samples | {n_patients:,} unique patients")

    def compute_pos_weight(self, clamp_max: float = 30.0) -> torch.Tensor:
        pos = self.labels.sum(dim=0)
        neg = len(self.labels) - pos
        pw  = neg / (pos + 1e-6)
        return pw.clamp(min=1.0, max=clamp_max)

    def __len__(self) -> int:
        return len(self.image_names)

    def __getitem__(self, idx: int):
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

        if self.transform is not None:
            img_tensor = self.transform(image=img_np)["image"]
        else:
            pil = Image.fromarray(img_np).resize(
                (self.img_size, self.img_size), Image.BILINEAR
            )
            img_tensor = torch.from_numpy(
                np.array(pil, dtype=np.float32) / 255.0
            ).permute(2, 0, 1)

        return img_tensor, self.labels[idx]


def report_patient_leakage(
    train_ds: ChestXrayDataset, test_ds: ChestXrayDataset
) -> None:
    train_pts = set(train_ds.patient_ids)
    test_pts  = set(test_ds.patient_ids)
    overlap   = train_pts & test_pts
    pct       = 100 * len(overlap) / max(len(test_pts), 1)
    print(f"\n[LeakCheck] Train patients : {len(train_pts):,}")
    print(f"[LeakCheck] Test  patients : {len(test_pts):,}")
    print(f"[LeakCheck] Overlap        : {len(overlap):,} ({pct:.1f}% of test)")
    print(f"[LeakCheck] NOTE: NIH official split has known patient overlap — "
          f"reported AUC is vs this split.")


# =============================================================
#  CONFIG  (T4-sized)
# =============================================================
CFG = dict(
    ROOT     = "/kaggle/input/datasets/organizations/nih-chest-xrays/data",
    SAVE_DIR = "/kaggle/working",

    MODEL_NAME = "convnext_small.fb_in22k_ft_in1k",  # see switcher in header

    # ── input ─────────────────────────────────────────────────
    IMG_SIZE   = 384,    # 448 OOMs on a T4; 384 keeps most detail

    # ── training ──────────────────────────────────────────────
    EPOCHS      = 18,    # prior runs peaked by ep 8-10; 18 is plenty
    PATIENCE    = 6,
    BATCH_SIZE  = 16,    # fits T4 16GB at 384px with grad checkpointing
    ACCUM_STEPS = 2,     # effective batch = 16 * 2 = 32

    # ── LR: layer-wise decay across ConvNeXt's 4 stages ───────
    LR_HEAD      = 1e-4,
    LR_BACKBONE  = 2e-5,
    LAYER_DECAY  = 0.80,
    WEIGHT_DECAY = 0.05,

    # ── Asymmetric Loss ───────────────────────────────────────
    ASL_GAMMA_POS = 0.0,
    ASL_GAMMA_NEG = 4.0,
    ASL_CLIP      = 0.05,
    LABEL_SMOOTH  = 0.07,

    DROPOUT        = 0.10,
    DROP_PATH_RATE = 0.10,   # Small uses a lower rate than Base

    # ── EMA ───────────────────────────────────────────────────
    EMA_DECAY = 0.9995,

    # ── TTA / validation strategy ─────────────────────────────
    TTA_PASSES_EVAL  = 1,     # fast per-epoch validation (no TTA)
    TTA_PASSES_FINAL = 4,     # full TTA once, on best checkpoint
    VAL_SUBSET       = 8000,  # per-epoch val subset size (None = full)

    GRAD_CHECKPOINT = True,   # big VRAM saver on T4

    # ── dataloader ────────────────────────────────────────────
    NUM_WORKERS = 2,          # Kaggle GPU notebooks ~4 vCPUs
    PIN_MEMORY  = True,
    PREFETCH    = 2,

    SEED = 42,
)

SAVE_PATH     = os.path.join(CFG["SAVE_DIR"], "convnext_best_raw.pth")
SAVE_PATH_EMA = os.path.join(CFG["SAVE_DIR"], "convnext_best_ema.pth")


# =============================================================
#  ASYMMETRIC LOSS
# =============================================================
class AsymmetricLoss(nn.Module):
    def __init__(
        self,
        gamma_pos:    float = 0.0,
        gamma_neg:    float = 4.0,
        clip:         float = 0.05,
        label_smooth: float = 0.07,
        eps:          float = 1e-8,
    ):
        super().__init__()
        self.gamma_pos    = gamma_pos
        self.gamma_neg    = gamma_neg
        self.clip         = clip
        self.label_smooth = label_smooth
        self.eps          = eps

    def forward(
        self, logits: torch.Tensor, targets: torch.Tensor
    ) -> torch.Tensor:
        if self.label_smooth > 0:
            targets = targets * (1 - self.label_smooth) + 0.5 * self.label_smooth

        probs     = torch.sigmoid(logits)
        probs_neg = (1 - probs + self.clip).clamp(max=1)

        loss_pos = targets       * torch.log(probs.clamp(min=self.eps))
        loss_neg = (1 - targets) * torch.log(probs_neg.clamp(min=self.eps))

        pt0 = probs     * targets
        pt1 = probs_neg * (1 - targets)
        pt  = pt0 + pt1
        gamma = self.gamma_pos * targets + self.gamma_neg * (1 - targets)
        w     = torch.pow(1 - pt, gamma)

        return (-(loss_pos + loss_neg) * w).mean()


# =============================================================
#  EMA
# =============================================================
class ModelEMA:
    def __init__(self, model: nn.Module, decay: float = 0.9995):
        self.ema   = copy.deepcopy(model).eval()
        self.decay = decay
        for p in self.ema.parameters():
            p.requires_grad_(False)

    @torch.no_grad()
    def update(self, model: nn.Module):
        for ema_p, p in zip(self.ema.parameters(), model.parameters()):
            ema_p.mul_(self.decay).add_(p.detach(), alpha=1 - self.decay)
        for ema_b, b in zip(self.ema.buffers(), model.buffers()):
            ema_b.copy_(b)


# =============================================================
#  MODEL — ConvNeXt (ImageNet-21K pretrained via timm)
# =============================================================
def build_model(num_classes: int = 14, cfg: dict = CFG) -> nn.Module:
    model = timm.create_model(
        cfg["MODEL_NAME"],
        pretrained     = True,
        num_classes    = num_classes,
        drop_rate      = cfg["DROPOUT"],
        drop_path_rate = cfg["DROP_PATH_RATE"],
    )
    if cfg.get("GRAD_CHECKPOINT", False):
        try:
            model.set_grad_checkpointing(enable=True)
            print("[Model] Gradient checkpointing ENABLED")
        except Exception as e:
            print(f"[Model] Could not enable grad checkpointing: {e}")
    return model


def build_optimizer(model: nn.Module, cfg: dict) -> torch.optim.Optimizer:
    no_decay_kws = ["norm", "bias"]
    stage_names  = ["stem", "stages.0", "stages.1", "stages.2", "stages.3", "head"]
    n_stages     = len(stage_names)
    param_groups = {}

    for name, param in model.named_parameters():
        if not param.requires_grad:
            continue
        stage_idx = n_stages - 1
        for i, sname in enumerate(stage_names):
            if sname in name:
                stage_idx = i
                break

        lr_scale = cfg["LAYER_DECAY"] ** (n_stages - 1 - stage_idx)
        lr       = cfg["LR_BACKBONE"] + (cfg["LR_HEAD"] - cfg["LR_BACKBONE"]) * lr_scale
        no_decay = any(k in name for k in no_decay_kws)
        key      = (round(lr, 10), no_decay)
        if key not in param_groups:
            param_groups[key] = []
        param_groups[key].append(param)

    return torch.optim.AdamW([
        {
            "params":       params,
            "lr":           lr,
            "weight_decay": 0.0 if no_decay else cfg["WEIGHT_DECAY"],
        }
        for (lr, no_decay), params in param_groups.items()
    ])


# =============================================================
#  TTA + EVALUATION
# =============================================================
@torch.no_grad()
def tta_predict(
    model: nn.Module, images: torch.Tensor, n_passes: int = 1
) -> torch.Tensor:
    preds = torch.sigmoid(model(images))
    if n_passes >= 2:
        preds = preds + torch.sigmoid(model(torch.flip(images, dims=[3])))
    if n_passes >= 4:
        H    = images.shape[2]
        crop = int(H * 0.90)
        pad  = (H - crop) // 2
        c1   = images[:, :, pad:pad+crop, pad:pad+crop]
        c1   = F.interpolate(c1, size=(H, H), mode="bilinear", align_corners=False)
        preds = preds + torch.sigmoid(model(c1))
        preds = preds + torch.sigmoid(model(torch.flip(c1, dims=[3])))
    return preds / n_passes


@torch.no_grad()
def evaluate(
    model: nn.Module,
    loader: DataLoader,
    device: torch.device,
    tta_passes: int = 1,
    desc: str = "eval",
):
    model.eval()
    all_labels, all_probs = [], []
    for images, labels in tqdm(loader, desc=f"  {desc}", leave=False):
        images = images.to(device, non_blocking=True)
        with torch.amp.autocast("cuda", enabled=(device.type == "cuda")):
            probs = tta_predict(model, images, n_passes=tta_passes)
        all_labels.append(labels.numpy())
        all_probs.append(probs.float().cpu().numpy())
    all_labels = np.vstack(all_labels)
    all_probs  = np.vstack(all_probs)
    try:
        macro_auc = roc_auc_score(all_labels, all_probs, average="macro")
    except Exception:
        macro_auc = 0.0
    return macro_auc, all_labels, all_probs


# =============================================================
#  PLOTTING
# =============================================================
def save_training_plots(metrics_df: pd.DataFrame, save_dir: str) -> None:
    fig, axes = plt.subplots(1, 2, figsize=(14, 5))
    axes[0].plot(metrics_df["epoch"], metrics_df["train_loss"],
                 marker="o", label="Train Loss")
    axes[0].set_xlabel("Epoch"); axes[0].set_ylabel("Loss")
    axes[0].set_title("Training Loss"); axes[0].grid(True)

    axes[1].plot(metrics_df["epoch"], metrics_df["val_auc"],
                 marker="o", color="steelblue", label="Raw AUC")
    axes[1].plot(metrics_df["epoch"], metrics_df["val_auc_ema"],
                 marker="s", color="purple", linewidth=2, label="EMA AUC")
    axes[1].axhline(y=0.86, color="red", linestyle="--", label="Target 0.86")
    axes[1].set_xlabel("Epoch"); axes[1].set_ylabel("Macro AUC")
    axes[1].set_title("Validation AUC — Raw vs EMA")
    axes[1].legend(); axes[1].grid(True)

    plt.tight_layout()
    plt.savefig(os.path.join(save_dir, "training_curves.png"),
                dpi=150, bbox_inches="tight")
    plt.close(fig)


def save_roc_curves(
    labels: np.ndarray, probs: np.ndarray, save_dir: str, suffix: str = ""
) -> None:
    fig, ax = plt.subplots(figsize=(11, 9))
    for i, disease in enumerate(DISEASE_LABELS):
        try:
            fpr, tpr, _ = roc_curve(labels[:, i], probs[:, i])
            roc_auc     = auc(fpr, tpr)
            ax.plot(fpr, tpr, label=f"{disease} ({roc_auc:.3f})")
        except Exception:
            pass
    ax.plot([0, 1], [0, 1], "k--")
    ax.set_xlabel("FPR"); ax.set_ylabel("TPR")
    ax.set_title(f"ROC Curves — NIH Chest X-ray14{suffix}")
    ax.legend(fontsize=8); ax.grid(True)
    plt.savefig(os.path.join(save_dir, f"roc_curves{suffix}.png"),
                dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"[Plot] roc_curves{suffix}.png saved")


def save_per_class_auc(
    labels: np.ndarray, probs: np.ndarray, save_dir: str, suffix: str = ""
) -> None:
    aucs, names = [], []
    for i, disease in enumerate(DISEASE_LABELS):
        try:
            a = roc_auc_score(labels[:, i], probs[:, i])
            aucs.append(a); names.append(disease)
        except Exception:
            pass
    pairs   = sorted(zip(aucs, names))
    aucs_s  = [p[0] for p in pairs]
    names_s = [p[1] for p in pairs]
    colors  = ["#E74C3C" if a < 0.75 else "#F39C12" if a < 0.83 else "#27AE60"
               for a in aucs_s]
    fig, ax = plt.subplots(figsize=(10, 7))
    bars = ax.barh(names_s, aucs_s, color=colors)
    ax.axvline(x=0.86, color="navy", linestyle="--", label="Target 0.86")
    for bar, val in zip(bars, aucs_s):
        ax.text(bar.get_width() + 0.003,
                bar.get_y() + bar.get_height() / 2,
                f"{val:.4f}", va="center", fontsize=9)
    ax.set_xlim(0.60, 1.0); ax.set_xlabel("AUC")
    ax.set_title(f"Per-Disease AUC{suffix}")
    ax.legend(); ax.grid(axis="x", alpha=0.4)
    plt.tight_layout()
    plt.savefig(os.path.join(save_dir, f"per_disease_auc{suffix}.png"),
                dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"[Plot] per_disease_auc{suffix}.png saved")


def save_confusion_matrices_grid(
    labels: np.ndarray, probs: np.ndarray, save_dir: str,
    threshold: float = 0.35, suffix: str = ""
) -> None:
    n_cols = 4
    n_rows = int(np.ceil(len(DISEASE_LABELS) / n_cols))
    fig, axes = plt.subplots(n_rows, n_cols,
                              figsize=(4 * n_cols, 3.6 * n_rows))
    axes = axes.flatten()
    for i, disease in enumerate(DISEASE_LABELS):
        ax = axes[i]
        try:
            y_true = labels[:, i]
            y_pred = (probs[:, i] >= threshold).astype(int)
            cm     = confusion_matrix(y_true, y_pred)
            sns.heatmap(cm, annot=True, fmt="d", cmap="Blues", ax=ax,
                        cbar=False, square=True,
                        xticklabels=["Neg", "Pos"],
                        yticklabels=["Neg", "Pos"],
                        annot_kws={"size": 11})
            d_auc = roc_auc_score(y_true, probs[:, i]) \
                    if len(np.unique(y_true)) > 1 else float("nan")
            ax.set_title(f"{disease}\nAUC={d_auc:.3f}", fontsize=11)
            ax.set_xlabel("Predicted", fontsize=9)
            ax.set_ylabel("Actual",    fontsize=9)
            ax.tick_params(labelsize=9)
        except Exception:
            ax.text(0.5, 0.5, disease, ha="center", va="center")
            ax.axis("off")
    for j in range(len(DISEASE_LABELS), len(axes)):
        axes[j].axis("off")
    fig.suptitle(
        f"Confusion Matrices — All 14 Diseases (threshold={threshold}){suffix}",
        fontsize=14, y=1.01,
    )
    plt.tight_layout()
    plt.savefig(os.path.join(save_dir, f"confusion_matrices_grid{suffix}.png"),
                dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"[Plot] confusion_matrices_grid{suffix}.png saved")


def save_precision_recall_f1_curves(
    labels: np.ndarray, probs: np.ndarray, save_dir: str
) -> None:
    thresholds  = np.linspace(0.05, 0.95, 19)
    n_cols      = 4
    n_rows      = int(np.ceil(len(DISEASE_LABELS) / n_cols))
    fig, axes   = plt.subplots(n_rows, n_cols,
                                figsize=(4.2 * n_cols, 3.4 * n_rows))
    axes        = axes.flatten()
    macro_p     = np.zeros(len(thresholds))
    macro_r     = np.zeros(len(thresholds))
    macro_f     = np.zeros(len(thresholds))

    for i, disease in enumerate(DISEASE_LABELS):
        y_true = labels[:, i]
        ps, rs, fs = [], [], []
        for t in thresholds:
            y_pred = (probs[:, i] >= t).astype(int)
            ps.append(precision_score(y_true, y_pred, zero_division=0))
            rs.append(recall_score(y_true,    y_pred, zero_division=0))
            fs.append(f1_score(y_true,        y_pred, zero_division=0))
        macro_p += np.array(ps)
        macro_r += np.array(rs)
        macro_f += np.array(fs)
        ax = axes[i]
        ax.plot(thresholds, ps, color="#2196F3", linewidth=1.6, label="Precision")
        ax.plot(thresholds, rs, color="#FF9800", linewidth=1.6, label="Recall")
        ax.plot(thresholds, fs, color="#4CAF50", linewidth=2.0, label="F1")
        best_idx = int(np.argmax(fs))
        ax.axvline(thresholds[best_idx], color="grey", linestyle=":", linewidth=1)
        ax.scatter([thresholds[best_idx]], [fs[best_idx]],
                   color="#4CAF50", zorder=5, s=25)
        ax.set_title(
            f"{disease}  (F1={fs[best_idx]:.3f} @ t={thresholds[best_idx]:.2f})",
            fontsize=10)
        ax.set_ylim(0, 1); ax.tick_params(labelsize=8); ax.grid(True, alpha=0.3)
        if i == 0:
            ax.legend(fontsize=7, loc="upper right")

    for j in range(len(DISEASE_LABELS), len(axes)):
        axes[j].axis("off")
    fig.suptitle("Precision / Recall / F1 vs Threshold — Per Disease",
                 fontsize=14, y=1.01)
    plt.tight_layout()
    plt.savefig(os.path.join(save_dir, "prf1_per_class.png"),
                dpi=150, bbox_inches="tight")
    plt.close(fig)

    n = len(DISEASE_LABELS)
    macro_p /= n; macro_r /= n; macro_f /= n
    best_idx = int(np.argmax(macro_f))
    fig2, ax2 = plt.subplots(figsize=(8, 5.5))
    ax2.plot(thresholds, macro_p, label="Macro Precision", color="#2196F3", linewidth=2)
    ax2.plot(thresholds, macro_r, label="Macro Recall",    color="#FF9800", linewidth=2)
    ax2.plot(thresholds, macro_f, label="Macro F1",        color="#4CAF50", linewidth=2.5)
    ax2.axvline(thresholds[best_idx], color="red", linestyle="--", linewidth=1.5,
                label=f"Best F1 @ t={thresholds[best_idx]:.2f}")
    ax2.set_xlabel("Threshold"); ax2.set_ylabel("Score")
    ax2.set_title(
        f"Macro Precision / Recall / F1 vs Threshold\n"
        f"Best Macro F1 = {macro_f[best_idx]:.4f} @ threshold = {thresholds[best_idx]:.2f}"
    )
    ax2.set_ylim(0, 1); ax2.legend(); ax2.grid(True, alpha=0.3)
    plt.tight_layout()
    plt.savefig(os.path.join(save_dir, "prf1_macro.png"),
                dpi=150, bbox_inches="tight")
    plt.close(fig2)
    print(f"[Plot] prf1_per_class.png + prf1_macro.png saved "
          f"(best macro F1={macro_f[best_idx]:.4f} @ t={thresholds[best_idx]:.2f})")


# =============================================================
#  MAIN
# =============================================================
def main():
    torch.manual_seed(CFG["SEED"])
    np.random.seed(CFG["SEED"])

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"[Train] Device : {device}")
    if device.type == "cuda":
        print(f"[Train] GPU    : {torch.cuda.get_device_name(0)}")
        print(f"[Train] VRAM   : "
              f"{torch.cuda.get_device_properties(0).total_memory / 1e9:.1f} GB")
        torch.backends.cudnn.benchmark = True
        # NOTE: TF32 is Ampere+; the T4 (Turing) has none, so no TF32 flags here.

    os.makedirs(CFG["SAVE_DIR"], exist_ok=True)
    ROOT = CFG["ROOT"]

    train_tf = build_train_transform(CFG["IMG_SIZE"])
    val_tf   = build_val_transform(CFG["IMG_SIZE"])

    print("\n[Train] Building datasets …")
    train_ds = ChestXrayDataset(
        csv_file  = f"{ROOT}/Data_Entry_2017.csv",
        root_dir  = ROOT,
        file_list = f"{ROOT}/train_val_list.txt",
        transform = train_tf,
        img_size  = CFG["IMG_SIZE"],
    )
    test_ds = ChestXrayDataset(
        csv_file  = f"{ROOT}/Data_Entry_2017.csv",
        root_dir  = ROOT,
        file_list = f"{ROOT}/test_list.txt",
        transform = val_tf,
        img_size  = CFG["IMG_SIZE"],
    )
    report_patient_leakage(train_ds, test_ds)

    # ── fast per-epoch validation subset (fixed across epochs) ──
    if CFG["VAL_SUBSET"] is not None and CFG["VAL_SUBSET"] < len(test_ds):
        rng        = np.random.RandomState(CFG["SEED"])
        sub_idx    = rng.choice(len(test_ds), CFG["VAL_SUBSET"], replace=False)
        val_ds_fast = Subset(test_ds, sub_idx.tolist())
        print(f"[Train] Per-epoch val subset: {len(val_ds_fast):,} "
              f"of {len(test_ds):,} test images")
    else:
        val_ds_fast = test_ds

    train_loader = DataLoader(
        train_ds,
        batch_size         = CFG["BATCH_SIZE"],
        shuffle            = True,
        num_workers        = CFG["NUM_WORKERS"],
        pin_memory         = CFG["PIN_MEMORY"],
        persistent_workers = True,
        prefetch_factor    = CFG["PREFETCH"],
        drop_last          = True,
    )
    val_loader_fast = DataLoader(
        val_ds_fast,
        batch_size         = CFG["BATCH_SIZE"] * 2,
        shuffle            = False,
        num_workers        = CFG["NUM_WORKERS"],
        pin_memory         = CFG["PIN_MEMORY"],
        persistent_workers = True,
        prefetch_factor    = CFG["PREFETCH"],
    )
    test_loader_full = DataLoader(
        test_ds,
        batch_size         = CFG["BATCH_SIZE"] * 2,
        shuffle            = False,
        num_workers        = CFG["NUM_WORKERS"],
        pin_memory         = CFG["PIN_MEMORY"],
        persistent_workers = False,
        prefetch_factor    = CFG["PREFETCH"],
    )

    print(f"\n[Train] Building {CFG['MODEL_NAME']} …")
    model = build_model(num_classes=14, cfg=CFG)
    model = model.to(device)
    total_p     = sum(p.numel() for p in model.parameters())
    trainable_p = sum(p.numel() for p in model.parameters() if p.requires_grad)
    print(f"[Train] Parameters: {total_p:,} total | {trainable_p:,} trainable")

    ema       = ModelEMA(model, decay=CFG["EMA_DECAY"])
    criterion = AsymmetricLoss(
        gamma_pos    = CFG["ASL_GAMMA_POS"],
        gamma_neg    = CFG["ASL_GAMMA_NEG"],
        clip         = CFG["ASL_CLIP"],
        label_smooth = CFG["LABEL_SMOOTH"],
    )
    optimizer = build_optimizer(model, CFG)
    print(f"[Train] Optimiser: {len(optimizer.param_groups)} param groups "
          f"(layer-wise LR decay={CFG['LAYER_DECAY']})")

    # T_0 chosen so a warm restart lands roughly mid-run
    scheduler = torch.optim.lr_scheduler.CosineAnnealingWarmRestarts(
        optimizer,
        T_0     = max(1, CFG["EPOCHS"] // 2),
        T_mult  = 1,
        eta_min = 1e-7,
    )

    scaler = torch.amp.GradScaler("cuda", enabled=(device.type == "cuda"))

    best_auc     = 0.0
    best_auc_ema = 0.0
    counter      = 0
    metrics      = []
    accum_steps  = max(1, CFG["ACCUM_STEPS"])
    t0_total     = time.time()

    print(f"\n[Train] ═══════════════════════════════════════")
    print(f"[Train] {CFG['MODEL_NAME']}")
    print(f"[Train] Input {CFG['IMG_SIZE']}×{CFG['IMG_SIZE']} | "
          f"Batch {CFG['BATCH_SIZE']}×{accum_steps}accum "
          f"(eff {CFG['BATCH_SIZE']*accum_steps})")
    print(f"[Train] Epochs {CFG['EPOCHS']} | Patience {CFG['PATIENCE']}")
    print(f"[Train] Val: {CFG['TTA_PASSES_EVAL']}-pass on subset | "
          f"Final: {CFG['TTA_PASSES_FINAL']}-pass on full test")
    print(f"[Train] ASL γ_neg={CFG['ASL_GAMMA_NEG']} | "
          f"clip={CFG['ASL_CLIP']} | smooth={CFG['LABEL_SMOOTH']}")
    print(f"[Train] EMA decay={CFG['EMA_DECAY']}")
    print(f"[Train] ═══════════════════════════════════════\n")

    for epoch in range(1, CFG["EPOCHS"] + 1):
        t0 = time.time()
        model.train()
        running_loss = 0.0
        n_batches    = 0
        optimizer.zero_grad(set_to_none=True)

        n_steps = len(train_loader)
        for step, (images, labels) in enumerate(tqdm(
            train_loader, desc=f"Epoch {epoch:02d}/{CFG['EPOCHS']}"
        )):
            images = images.to(device, non_blocking=True)
            labels = labels.to(device, non_blocking=True)

            with torch.amp.autocast("cuda", enabled=(device.type == "cuda")):
                logits = model(images)
                loss   = criterion(logits, labels) / accum_steps

            scaler.scale(loss).backward()

            is_step = ((step + 1) % accum_steps == 0) or ((step + 1) == n_steps)
            if is_step:
                scaler.unscale_(optimizer)
                torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
                scaler.step(optimizer)
                scaler.update()
                optimizer.zero_grad(set_to_none=True)
                ema.update(model)

            running_loss += loss.item() * accum_steps
            n_batches    += 1

        scheduler.step()

        train_loss = running_loss / n_batches
        epoch_time = time.time() - t0

        val_auc, _, _ = evaluate(
            model, val_loader_fast, device,
            CFG["TTA_PASSES_EVAL"], "raw")
        val_auc_ema, all_labels, all_probs_ema = evaluate(
            ema.ema, val_loader_fast, device,
            CFG["TTA_PASSES_EVAL"], "ema")

        per_class_auc = {}
        for i, d in enumerate(DISEASE_LABELS):
            try:
                per_class_auc[d] = roc_auc_score(
                    all_labels[:, i], all_probs_ema[:, i]
                )
            except Exception:
                per_class_auc[d] = float("nan")

        print(
            f"Epoch {epoch:02d} | Loss {train_loss:.5f} | "
            f"AUC(Raw)={val_auc:.4f}  AUC(EMA)={val_auc_ema:.4f} | "
            f"Time {epoch_time:.0f}s"
        )
        print(
            f"         Infiltration={per_class_auc.get('Infiltration',0):.4f}  "
            f"Pneumonia={per_class_auc.get('Pneumonia',0):.4f}  "
            f"Consolidation={per_class_auc.get('Consolidation',0):.4f}"
        )

        metrics.append({
            "epoch": epoch, "train_loss": train_loss,
            "val_auc": val_auc, "val_auc_ema": val_auc_ema,
            **{f"auc_{d}": per_class_auc[d] for d in DISEASE_LABELS},
        })

        improved = False

        if val_auc > best_auc:
            best_auc = val_auc
            torch.save({
                "epoch": epoch, "auc": val_auc,
                "model_state_dict": model.state_dict(),
                "per_class_auc": per_class_auc, "cfg": CFG,
            }, SAVE_PATH)
            improved = True
            print(f"  ✔ Best RAW  saved  AUC={val_auc:.4f}")

        if val_auc_ema > best_auc_ema:
            best_auc_ema = val_auc_ema
            torch.save({
                "epoch": epoch, "auc": val_auc_ema,
                "model_state_dict": ema.ema.state_dict(),
                "per_class_auc": per_class_auc, "cfg": CFG,
            }, SAVE_PATH_EMA)
            improved = True
            print(f"  ✔ Best EMA  saved  AUC={val_auc_ema:.4f}")

        if improved:
            counter = 0
        else:
            counter += 1
            print(f"  No improvement ({counter}/{CFG['PATIENCE']})")
            if counter >= CFG["PATIENCE"]:
                print("[Train] Early stopping.")
                break

    # ── Final summary ─────────────────────────────────────────
    total_time = time.time() - t0_total
    print(f"\n{'='*60}")
    print(f"Training complete in {total_time/60:.1f} min")
    print(f"Best RAW AUC (subset) : {best_auc:.4f}")
    print(f"Best EMA AUC (subset) : {best_auc_ema:.4f}")
    print(f"{'='*60}")

    metrics_df = pd.DataFrame(metrics)
    metrics_df.to_csv(
        os.path.join(CFG["SAVE_DIR"], "training_metrics.csv"), index=False
    )

    # ── Final evaluation: best checkpoint, FULL test set, full TTA ──
    best_path = SAVE_PATH_EMA if best_auc_ema >= best_auc else SAVE_PATH
    print(f"\n[Train] Loading {best_path} for final FULL-test evaluation …")
    ckpt = torch.load(best_path, map_location=device, weights_only=False)
    model.load_state_dict(ckpt["model_state_dict"])
    final_auc_val, all_labels, all_probs = evaluate(
        model, test_loader_full, device, CFG["TTA_PASSES_FINAL"], "final"
    )
    print(f"\nFINAL macro AUC (full test, {CFG['TTA_PASSES_FINAL']}-pass TTA): "
          f"{final_auc_val:.4f}")

    print("\nPer-disease AUC (final, full test):")
    for i, disease in enumerate(DISEASE_LABELS):
        try:
            a = roc_auc_score(all_labels[:, i], all_probs[:, i])
            print(f"  {disease:<22} {a:.4f}")
        except Exception as e:
            print(f"  {disease:<22} ERROR: {e}")

    # ── Save all plots ─────────────────────────────────────────
    print("\n[Train] Saving plots …")
    save_training_plots(metrics_df, CFG["SAVE_DIR"])
    save_roc_curves(all_labels, all_probs, CFG["SAVE_DIR"])
    save_per_class_auc(all_labels, all_probs, CFG["SAVE_DIR"])
    save_confusion_matrices_grid(
        all_labels, all_probs, CFG["SAVE_DIR"], threshold=0.35
    )
    save_precision_recall_f1_curves(all_labels, all_probs, CFG["SAVE_DIR"])

    # ── Classification report ──────────────────────────────────
    threshold = 0.35
    print(f"\nClassification Report (threshold={threshold}):")
    for i, disease in enumerate(DISEASE_LABELS):
        y_true = all_labels[:, i]
        y_pred = (all_probs[:, i] >= threshold).astype(int)
        print(f"\n── {disease} ──")
        print(classification_report(y_true, y_pred, digits=4, zero_division=0))

    print("\n[Train] All outputs saved to:", CFG["SAVE_DIR"])
    print(f"[Train] FINAL full-test AUC = {final_auc_val:.4f}")
    print(f"[Train] Deploy checkpoint:    {best_path}")


if __name__ == "__main__":
    main()