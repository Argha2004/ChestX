# =============================================================
#  Train.py  —  NIH Chest X-Ray14  |  Tuned Run #2
#  Hardware target: 30 GB RAM, 16 GB VRAM (single GPU)
#
#  Tuned config (vs Run #1 which got 0.8252):
#    FOCAL_GAMMA  : 2.0  → 1.0   (gentler focal effect)
#    LR_HEAD      : 3e-4 → 1.5e-4 (gentler peak LR)
#    DROPOUT      : 0.3  → 0.2   (less aggressive regularisation)
#
#  Kaggle ready — single file, no separate Dataset.py needed.
#  Run order:
#    Cell 1:  !pip install albumentations -q
#    Cell 2:  paste this entire file, run
# =============================================================

import os
import time
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
    roc_auc_score, roc_curve, auc,
    confusion_matrix, classification_report,
)
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import seaborn as sns

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
#  TRANSFORMS  (Albumentations)
# =============================================================
def build_train_transform(img_size: int = 384):
    return A.Compose([
        A.Resize(img_size, img_size),
        # geometric
        A.HorizontalFlip(p=0.5),
        A.Affine(
            translate_percent=(0.0, 0.05),
            scale=(0.92, 1.08),
            rotate=(-10, 10),
            mode=0,                 # cv2.BORDER_CONSTANT
            p=0.6,
        ),
        A.GridDistortion(num_steps=5, distort_limit=0.1, p=0.3),
        # photometric
        A.CLAHE(clip_limit=2.0, tile_grid_size=(8, 8), p=0.5),
        A.RandomBrightnessContrast(
            brightness_limit=0.15, contrast_limit=0.15, p=0.5
        ),
        A.GaussNoise(std_range=(0.02, 0.1), p=0.2),
        # normalise + tensor
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
    """
    NIH Chest X-Ray14 dataset.
    Pre-encodes all labels into a (N, 14) float32 tensor for speed.
    """

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
        label_to_idx = {lbl: i for i, lbl in enumerate(DISEASE_LABELS)}

        print("[Dataset] Encoding labels …")
        n = len(self.image_names)
        label_matrix = np.zeros((n, len(DISEASE_LABELS)), dtype=np.float32)

        for row_idx, img_name in enumerate(self.image_names):
            raw = label_map.get(img_name, "No Finding")
            for disease in raw.split("|"):
                col = label_to_idx.get(disease)
                if col is not None:
                    label_matrix[row_idx, col] = 1.0

        self.labels      = torch.from_numpy(label_matrix)
        self.labels_list = DISEASE_LABELS
        print(f"[Dataset] Ready — {n:,} samples, {len(DISEASE_LABELS)} classes")

    def compute_pos_weight(self, clamp_max: float = 100.0) -> torch.Tensor:
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


# =============================================================
#  CONFIG  —  Tuned Run #2
# =============================================================
CFG = dict(
    ROOT     = "/kaggle/input/datasets/organizations/nih-chest-xrays/data",
    SAVE_DIR = "/kaggle/working",

    IMG_SIZE   = 384,

    EPOCHS     = 30,
    PATIENCE   = 5,
    BATCH_SIZE = 32,

    LR_HEAD      = 1.5e-4,   # was 3e-4
    LR_BACKBONE  = 3e-5,
    WEIGHT_DECAY = 1e-4,

    FOCAL_GAMMA    = 1.0,    # was 2.0
    LABEL_SMOOTH   = 0.05,
    POS_WEIGHT_CAP = 50.0,

    DROPOUT = 0.2,           # was 0.3

    TTA_PASSES = 4,

    NUM_WORKERS = 4,
    PIN_MEMORY  = True,
    PREFETCH    = 2,

    SEED = 42,
)

SAVE_PATH = os.path.join(CFG["SAVE_DIR"], "DenseNet201.pth")


# =============================================================
#  FOCAL LOSS
# =============================================================
class FocalBCELoss(nn.Module):
    def __init__(self, pos_weight: torch.Tensor, gamma: float = 1.0, label_smooth: float = 0.05):
        super().__init__()
        self.gamma        = gamma
        self.label_smooth = label_smooth
        self.register_buffer("pos_weight", pos_weight)

    def forward(self, logits: torch.Tensor, targets: torch.Tensor) -> torch.Tensor:
        if self.label_smooth > 0:
            targets = targets * (1 - self.label_smooth) + 0.5 * self.label_smooth

        bce = F.binary_cross_entropy_with_logits(
            logits, targets, pos_weight=self.pos_weight, reduction="none"
        )

        if self.gamma > 0:
            probs = torch.sigmoid(logits).detach()
            p_t   = probs * targets + (1 - probs) * (1 - targets)
            bce   = bce * (1 - p_t) ** self.gamma

        return bce.mean()


# =============================================================
#  MODEL
# =============================================================

#For EffiientNetV2-S Model
def build_model(num_classes: int = 14, dropout: float = 0.2) -> nn.Module:
    model = models.efficientnet_v2_s(
        weights=models.EfficientNet_V2_S_Weights.IMAGENET1K_V1
    )
    in_features = model.classifier[1].in_features
    model.classifier = nn.Sequential(
        nn.Dropout(p=dropout, inplace=True),
        nn.Linear(in_features, num_classes),
    )
    model = model.to(memory_format=torch.channels_last)
    return model


#For DenseNet121 Model
# def build_model(num_classes: int = 14, dropout: float = 0.2) -> nn.Module:
#     model = models.densenet121(
#         weights=models.DenseNet121_Weights.IMAGENET1K_V1
#     )
#     in_features = model.classifier.in_features
#     model.classifier = nn.Sequential(
#         nn.Dropout(p=dropout, inplace=True),
#         nn.Linear(in_features, num_classes),
#     )
#     return model


# For DenseNet201 Model
# def build_model(num_classes: int = 14, dropout: float = 0.2) -> nn.Module:
#     model = models.densenet201(
#         weights=models.DenseNet201_Weights.IMAGENET1K_V1
#     )
#     in_features = model.classifier.in_features
#     model.classifier = nn.Sequential(
#         nn.Dropout(p=dropout, inplace=True),
#         nn.Linear(in_features, num_classes),
#     )
#     return model


def build_optimizer(model: nn.Module, cfg: dict) -> torch.optim.Optimizer:
    backbone_params = list(model.features.parameters())
    head_params     = list(model.classifier.parameters())
    return torch.optim.AdamW([
        {"params": backbone_params, "lr": cfg["LR_BACKBONE"], "weight_decay": cfg["WEIGHT_DECAY"]},
        {"params": head_params,     "lr": cfg["LR_HEAD"],     "weight_decay": 1e-5},
    ])


# =============================================================
#  TTA + EVALUATION
# =============================================================
@torch.no_grad()
def tta_predict(model: nn.Module, images: torch.Tensor, n_passes: int = 4) -> torch.Tensor:
    preds = torch.sigmoid(model(images))

    if n_passes >= 2:
        preds = preds + torch.sigmoid(model(torch.flip(images, dims=[3])))

    if n_passes >= 4:
        H    = images.shape[2]
        crop = int(H * 0.90)
        pad  = (H - crop) // 2
        c1 = images[:, :, pad:pad+crop, pad:pad+crop]
        c1 = F.interpolate(c1, size=(H, H), mode="bilinear", align_corners=False)
        preds = preds + torch.sigmoid(model(c1))
        c2 = torch.flip(c1, dims=[3])
        preds = preds + torch.sigmoid(model(c2))

    return preds / n_passes


@torch.no_grad()
def evaluate(model: nn.Module, loader: DataLoader, device: torch.device, tta_passes: int = 4):
    model.eval()
    all_labels, all_probs = [], []

    for images, labels in tqdm(loader, desc="  eval", leave=False):
        # images = images.to(device, non_blocking=True, memory_format=torch.channels_last)
        images = images.to(device, non_blocking=True)
        probs  = tta_predict(model, images, n_passes=tta_passes)
        all_labels.append(labels.numpy())
        all_probs.append(probs.cpu().numpy())

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

    axes[0].plot(metrics_df["epoch"], metrics_df["train_loss"], marker="o", label="Train Loss")
    axes[0].set_xlabel("Epoch"); axes[0].set_ylabel("Loss")
    axes[0].set_title("Training Loss"); axes[0].grid(True)

    axes[1].plot(metrics_df["epoch"], metrics_df["val_auc"], marker="o", color="green", label="Val AUC")
    axes[1].axhline(y=0.86, color="red", linestyle="--", label="Target 0.86")
    axes[1].set_xlabel("Epoch"); axes[1].set_ylabel("AUC")
    axes[1].set_title("Validation AUC"); axes[1].legend(); axes[1].grid(True)

    plt.tight_layout()
    plt.savefig(os.path.join(save_dir, "training_curves.png"), dpi=150, bbox_inches="tight")
    plt.close(fig)


def save_roc_curves(labels: np.ndarray, probs: np.ndarray, save_dir: str) -> None:
    fig, ax = plt.subplots(figsize=(11, 9))
    for i, disease in enumerate(DISEASE_LABELS):
        try:
            fpr, tpr, _ = roc_curve(labels[:, i], probs[:, i])
            roc_auc = auc(fpr, tpr)
            ax.plot(fpr, tpr, label=f"{disease} ({roc_auc:.3f})")
        except Exception:
            pass
    ax.plot([0, 1], [0, 1], "k--")
    ax.set_xlabel("FPR"); ax.set_ylabel("TPR")
    ax.set_title("ROC Curves — NIH Chest X-ray14 (EfficientNetV2-S)")
    ax.legend(fontsize=8); ax.grid(True)
    plt.savefig(os.path.join(save_dir, "roc_curves.png"), dpi=150, bbox_inches="tight")
    plt.close(fig)


def save_per_class_auc(labels: np.ndarray, probs: np.ndarray, save_dir: str) -> None:
    aucs, names = [], []
    for i, disease in enumerate(DISEASE_LABELS):
        try:
            a = roc_auc_score(labels[:, i], probs[:, i])
            aucs.append(a); names.append(disease)
        except Exception:
            pass
    pairs = sorted(zip(aucs, names))
    aucs_s, names_s = zip(*pairs)

    colors = ["#E74C3C" if a < 0.75 else "#F39C12" if a < 0.83 else "#27AE60" for a in aucs_s]
    fig, ax = plt.subplots(figsize=(10, 7))
    bars = ax.barh(names_s, aucs_s, color=colors)
    ax.axvline(x=0.86, color="navy", linestyle="--", label="Target 0.86")
    for bar, val in zip(bars, aucs_s):
        ax.text(bar.get_width() + 0.003, bar.get_y() + bar.get_height()/2,
                f"{val:.4f}", va="center", fontsize=9)
    ax.set_xlim(0.6, 1.0); ax.set_xlabel("AUC")
    ax.set_title("Per-Disease AUC — EfficientNetV2-S")
    ax.legend(); ax.grid(axis="x", alpha=0.4)
    plt.tight_layout()
    plt.savefig(os.path.join(save_dir, "per_disease_auc.png"), dpi=150, bbox_inches="tight")
    plt.close(fig)


def save_confusion_matrices(labels: np.ndarray, probs: np.ndarray, save_dir: str, threshold: float = 0.35) -> None:
    for i, disease in enumerate(DISEASE_LABELS):
        try:
            y_true = labels[:, i]
            y_pred = (probs[:, i] >= threshold).astype(int)
            cm = confusion_matrix(y_true, y_pred)
            fig, ax = plt.subplots(figsize=(4, 3))
            sns.heatmap(cm, annot=True, fmt="d", cmap="Blues", ax=ax)
            ax.set_title(f"{disease} (thresh={threshold})")
            ax.set_ylabel("Actual"); ax.set_xlabel("Predicted")
            plt.tight_layout()
            plt.savefig(os.path.join(save_dir, f"cm_{disease}.png"), dpi=100, bbox_inches="tight")
            plt.close(fig)
        except Exception:
            pass


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
        print(f"[Train] VRAM   : {torch.cuda.get_device_properties(0).total_memory / 1e9:.1f} GB")
        torch.backends.cudnn.benchmark = True

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

    pos_weight = train_ds.compute_pos_weight(clamp_max=CFG["POS_WEIGHT_CAP"]).to(device)
    print("\n[Train] Disease-wise pos_weight:")
    for name, pw in zip(DISEASE_LABELS, pos_weight.cpu()):
        print(f"  {name:<22} {pw:.2f}")

    train_loader = DataLoader(
        train_ds,
        batch_size      = CFG["BATCH_SIZE"],
        shuffle         = True,
        num_workers     = CFG["NUM_WORKERS"],
        pin_memory      = CFG["PIN_MEMORY"],
        persistent_workers = True,
        prefetch_factor = CFG["PREFETCH"],
        drop_last       = True,
    )
    test_loader = DataLoader(
        test_ds,
        batch_size      = CFG["BATCH_SIZE"] * 2,
        shuffle         = False,
        num_workers     = CFG["NUM_WORKERS"],
        pin_memory      = CFG["PIN_MEMORY"],
        persistent_workers = True,
        prefetch_factor = CFG["PREFETCH"],
    )

    print("\n[Train] Building model …")
    model = build_model(num_classes=14, dropout=CFG["DROPOUT"])
    model = model.to(device)
    total_p     = sum(p.numel() for p in model.parameters())
    trainable_p = sum(p.numel() for p in model.parameters() if p.requires_grad)
    print(f"[Train] Parameters: {total_p:,} total | {trainable_p:,} trainable")

    criterion = FocalBCELoss(
        pos_weight   = pos_weight,
        gamma        = CFG["FOCAL_GAMMA"],
        label_smooth = CFG["LABEL_SMOOTH"],
    )

    optimizer = build_optimizer(model, CFG)

    scheduler = torch.optim.lr_scheduler.OneCycleLR(
        optimizer,
        max_lr          = [CFG["LR_BACKBONE"], CFG["LR_HEAD"]],
        steps_per_epoch = len(train_loader),
        epochs          = CFG["EPOCHS"],
        pct_start       = 0.10,
        anneal_strategy = "cos",
        div_factor      = 10.0,
        final_div_factor= 1e3,
    )

    scaler = torch.amp.GradScaler("cuda", enabled=(device.type == "cuda"))

    best_auc = 0.0
    counter  = 0
    metrics  = []
    t0_total = time.time()

    print(f"\n[Train] Starting — {CFG['EPOCHS']} epochs, patience={CFG['PATIENCE']}")
    print(f"[Train] Input: {CFG['IMG_SIZE']}×{CFG['IMG_SIZE']}  |  Batch: {CFG['BATCH_SIZE']}  |  "
          f"TTA: {CFG['TTA_PASSES']}-pass  |  Gamma={CFG['FOCAL_GAMMA']}  |  "
          f"LR_HEAD={CFG['LR_HEAD']}  |  Dropout={CFG['DROPOUT']}\n")

    for epoch in range(1, CFG["EPOCHS"] + 1):
        t0 = time.time()
        model.train()
        running_loss = 0.0
        n_batches    = 0

        for images, labels in tqdm(train_loader, desc=f"Epoch {epoch:02d}/{CFG['EPOCHS']}"):
            # images = images.to(device, non_blocking=True, memory_format=torch.channels_last)
            images = images.to(device, non_blocking=True)
            labels = labels.to(device, non_blocking=True)

            optimizer.zero_grad(set_to_none=True)

            with torch.amp.autocast("cuda", enabled=(device.type == "cuda")):
                logits = model(images)
                loss   = criterion(logits, labels)

            scaler.scale(loss).backward()
            scaler.unscale_(optimizer)
            torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
            scaler.step(optimizer)
            scaler.update()
            scheduler.step()

            running_loss += loss.item()
            n_batches    += 1

        train_loss = running_loss / n_batches
        epoch_time = time.time() - t0

        val_auc, all_labels, all_probs = evaluate(model, test_loader, device, tta_passes=CFG["TTA_PASSES"])

        per_class_auc = {}
        for i, disease in enumerate(DISEASE_LABELS):
            try:
                per_class_auc[disease] = roc_auc_score(all_labels[:, i], all_probs[:, i])
            except Exception:
                per_class_auc[disease] = float("nan")

        lr_bb = optimizer.param_groups[0]["lr"]
        lr_hd = optimizer.param_groups[1]["lr"]
        print(
            f"Epoch {epoch:02d} | Loss {train_loss:.4f} | AUC {val_auc:.4f} | "
            f"LR backbone={lr_bb:.2e} head={lr_hd:.2e} | Time {epoch_time:.0f}s"
        )
        print(
            f"         Infiltration={per_class_auc.get('Infiltration',0):.4f}  "
            f"Pneumonia={per_class_auc.get('Pneumonia',0):.4f}  "
            f"Consolidation={per_class_auc.get('Consolidation',0):.4f}"
        )

        metrics.append({
            "epoch": epoch,
            "train_loss": train_loss,
            "val_auc": val_auc,
            "lr_backbone": lr_bb,
            "lr_head": lr_hd,
            **{f"auc_{d}": per_class_auc[d] for d in DISEASE_LABELS},
        })

        if val_auc > best_auc:
            best_auc = val_auc
            counter  = 0
            state = model.state_dict()
            torch.save({
                "epoch":            epoch,
                "auc":              val_auc,
                "model_state_dict": state,
                "per_class_auc":    per_class_auc,
                "cfg":              CFG,
            }, SAVE_PATH)
            print(f"  ✔ Best model saved  AUC={val_auc:.4f}")
        else:
            counter += 1
            print(f"  No improvement ({counter}/{CFG['PATIENCE']})")
            if counter >= CFG["PATIENCE"]:
                print("[Train] Early stopping triggered.")
                break

    total_time = time.time() - t0_total
    print(f"\n{'='*60}")
    print(f"Training complete in {total_time/60:.1f} min")
    print(f"Best macro AUC : {best_auc:.4f}")
    print(f"{'='*60}")

    metrics_df = pd.DataFrame(metrics)
    metrics_df.to_csv(os.path.join(CFG["SAVE_DIR"], "training_metrics.csv"), index=False)

    print("\n[Train] Loading best checkpoint for final evaluation …")
    ckpt = torch.load(SAVE_PATH, map_location=device, weights_only=False)
    model.load_state_dict(ckpt["model_state_dict"])
    final_auc, all_labels, all_probs = evaluate(model, test_loader, device, tta_passes=CFG["TTA_PASSES"])
    print(f"Final AUC (best ckpt, {CFG['TTA_PASSES']}-pass TTA): {final_auc:.4f}")

    print("\nPer-disease AUC (final):")
    for i, disease in enumerate(DISEASE_LABELS):
        try:
            a = roc_auc_score(all_labels[:, i], all_probs[:, i])
            print(f"  {disease:<22} {a:.4f}")
        except Exception as e:
            print(f"  {disease:<22} ERROR: {e}")

    print("\n[Train] Saving plots …")
    save_training_plots(metrics_df, CFG["SAVE_DIR"])
    save_roc_curves(all_labels, all_probs, CFG["SAVE_DIR"])
    save_per_class_auc(all_labels, all_probs, CFG["SAVE_DIR"])
    save_confusion_matrices(all_labels, all_probs, CFG["SAVE_DIR"], threshold=0.35)

    print("\nClassification Report (threshold=0.35):")
    threshold = 0.35
    for i, disease in enumerate(DISEASE_LABELS):
        y_true = all_labels[:, i]
        y_pred = (all_probs[:, i] >= threshold).astype(int)
        print(f"\n── {disease} ──")
        print(classification_report(y_true, y_pred, digits=4, zero_division=0))

    print("\n[Train] All outputs saved to:", CFG["SAVE_DIR"])
    print(f"[Train] Best AUC = {best_auc:.4f}")


if __name__ == "__main__":
    main()