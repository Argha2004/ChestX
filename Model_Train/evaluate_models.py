
# =====================================================
# evaluate_models.py
# Compare:
# 1. ResNet34 (BCE)
# 2. DenseNet121 (BCE)
# 3. DenseNet121 (Weighted BCE)
# =====================================================

import os
import torch
import torch.nn as nn
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

from tqdm import tqdm
from torchvision import models, transforms
from torch.utils.data import DataLoader

from sklearn.metrics import (
    roc_auc_score,
    roc_curve,
    auc,
    f1_score,
    precision_score,
    recall_score
)

from dataset import ChestXrayDataset

# ==========================
# EDIT PATHS
# ==========================

ROOT = r"D:\PROJECTS\LUNG-DISEASE-DITECTION\Dataset"

CSV_FILE = os.path.join(
    ROOT,
    "Data_Entry_2017.csv"
)

TEST_LIST = os.path.join(
    ROOT,
    "test_list.txt"
)

RESNET_MODEL = r"D:\PROJECTS\LUNG-DISEASE-DITECTION\Model\ResNet34\ResNet34.pth"
DENSENET_BCE_MODEL = r"D:\PROJECTS\LUNG-DISEASE-DITECTION\Model\DenseNet121\DenseNet121.pth"
DENSENET_WEIGHTED_MODEL = r"D:\PROJECTS\LUNG-DISEASE-DITECTION\Model\DenseNet121-WB\best_model.pth"

OUTPUT_DIR = r"D:\PROJECTS\LUNG-DISEASE-DITECTION\evaluation_results"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# ==========================
# LABELS
# ==========================

DISEASES = [
    "Atelectasis",
    "Consolidation",
    "Infiltration",
    "Pneumothorax",
    "Edema",
    "Emphysema",
    "Fibrosis",
    "Effusion",
    "Pneumonia",
    "Pleural_Thickening",
    "Cardiomegaly",
    "Nodule",
    "Mass",
    "Hernia"
]

THRESHOLD = 0.30

DEVICE = torch.device(
    "cuda" if torch.cuda.is_available()
    else "cpu"
)

print("Using:", DEVICE)

# ==========================
# TRANSFORM
# ==========================

transform = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
    transforms.Normalize(
        [0.485, 0.456, 0.406],
        [0.229, 0.224, 0.225]
    )
])

# ==========================
# DATASET
# ==========================

test_dataset = ChestXrayDataset(
    csv_file=CSV_FILE,
    root_dir=ROOT,
    file_list=TEST_LIST,
    transform=transform
)

test_loader = DataLoader(
    test_dataset,
    batch_size=64,
    shuffle=False,
    num_workers=0,
    pin_memory=True
)

# ==========================
# MODEL LOADERS
# ==========================

def load_resnet34(path):

    model = models.resnet34(weights=None)

    model.fc = nn.Linear(
        model.fc.in_features,
        14
    )

    ckpt = torch.load(
        path,
        map_location=DEVICE,
        weights_only=False
    )

    model.load_state_dict(
        ckpt["model_state_dict"]
    )

    model = model.to(DEVICE)
    model.eval()

    return model


def load_densenet121(path):

    model = models.densenet121(
        weights=None
    )

    model.classifier = nn.Linear(
        model.classifier.in_features,
        14
    )

    ckpt = torch.load(
        path,
        map_location=DEVICE,
        weights_only=False
    )

    model.load_state_dict(
        ckpt["model_state_dict"]
    )

    model = model.to(DEVICE)
    model.eval()

    return model

# ==========================
# INFERENCE
# ==========================

def evaluate_model(model, model_name):

    all_labels = []
    all_outputs = []

    print(f"\nEvaluating {model_name}")

    with torch.no_grad():

        for images, labels in tqdm(test_loader):

            images = images.to(DEVICE)

            outputs = model(images)

            probs = torch.sigmoid(
                outputs
            )

            all_labels.append(
                labels.numpy()
            )

            all_outputs.append(
                probs.cpu().numpy()
            )

    all_labels = np.vstack(
        all_labels
    )

    all_outputs = np.vstack(
        all_outputs
    )

    macro_auc = roc_auc_score(
        all_labels,
        all_outputs,
        average="macro"
    )

    binary_preds = (
        all_outputs >= THRESHOLD
    ).astype(int)

    macro_f1 = f1_score(
        all_labels,
        binary_preds,
        average="macro",
        zero_division=0
    )

    macro_precision = precision_score(
        all_labels,
        binary_preds,
        average="macro",
        zero_division=0
    )

    macro_recall = recall_score(
        all_labels,
        binary_preds,
        average="macro",
        zero_division=0
    )

    disease_aucs = {}

    for i, disease in enumerate(DISEASES):

        try:

            disease_auc = roc_auc_score(
                all_labels[:, i],
                all_outputs[:, i]
            )

            disease_aucs[disease] = disease_auc

        except Exception:

            disease_aucs[disease] = np.nan

    return {
        "labels": all_labels,
        "outputs": all_outputs,
        "macro_auc": macro_auc,
        "macro_f1": macro_f1,
        "macro_precision": macro_precision,
        "macro_recall": macro_recall,
        "disease_aucs": disease_aucs
    }

# ==========================
# LOAD MODELS
# ==========================

resnet_model = load_resnet34(
    RESNET_MODEL
)

dense_bce_model = load_densenet121(
    DENSENET_BCE_MODEL
)

dense_weighted_model = load_densenet121(
    DENSENET_WEIGHTED_MODEL
)

# ==========================
# RUN EVALUATION
# ==========================

resnet_results = evaluate_model(
    resnet_model,
    "ResNet34"
)

dense_bce_results = evaluate_model(
    dense_bce_model,
    "DenseNet121_BCE"
)

dense_weighted_results = evaluate_model(
    dense_weighted_model,
    "DenseNet121_Weighted_BCE"
)

# ==========================
# OVERALL COMPARISON
# ==========================

comparison_df = pd.DataFrame([
    {
        "Model": "ResNet34",
        "Macro_AUC":
        resnet_results["macro_auc"],
        "Macro_F1":
        resnet_results["macro_f1"],
        "Macro_Precision":
        resnet_results["macro_precision"],
        "Macro_Recall":
        resnet_results["macro_recall"]
    },
    {
        "Model": "DenseNet121_BCE",
        "Macro_AUC":
        dense_bce_results["macro_auc"],
        "Macro_F1":
        dense_bce_results["macro_f1"],
        "Macro_Precision":
        dense_bce_results["macro_precision"],
        "Macro_Recall":
        dense_bce_results["macro_recall"]
    },
    {
        "Model": "DenseNet121_Weighted_BCE",
        "Macro_AUC":
        dense_weighted_results["macro_auc"],
        "Macro_F1":
        dense_weighted_results["macro_f1"],
        "Macro_Precision":
        dense_weighted_results["macro_precision"],
        "Macro_Recall":
        dense_weighted_results["macro_recall"]
    }
])

comparison_df.to_csv(
    os.path.join(
        OUTPUT_DIR,
        "model_comparison.csv"
    ),
    index=False
)

print("\nMODEL COMPARISON")
print(comparison_df)

# ==========================
# DISEASE AUC TABLE
# ==========================

rows = []

for disease in DISEASES:

    rows.append([
        disease,
        resnet_results["disease_aucs"][disease],
        dense_bce_results["disease_aucs"][disease],
        dense_weighted_results["disease_aucs"][disease]
    ])

disease_auc_df = pd.DataFrame(
    rows,
    columns=[
        "Disease",
        "ResNet34",
        "DenseNet121_BCE",
        "DenseNet121_Weighted_BCE"
    ]
)

disease_auc_df.to_csv(
    os.path.join(
        OUTPUT_DIR,
        "disease_auc_comparison.csv"
    ),
    index=False
)

# ==========================
# BEST MODEL PER DISEASE
# ==========================

print("\nBEST MODEL PER DISEASE\n")

for _, row in disease_auc_df.iterrows():

    disease = row["Disease"]

    scores = {
        "ResNet34": row["ResNet34"],
        "DenseNet121_BCE": row["DenseNet121_BCE"],
        "DenseNet121_Weighted_BCE":
        row["DenseNet121_Weighted_BCE"]
    }

    winner = max(
        scores,
        key=scores.get
    )

    print(
        f"{disease:<20} "
        f"{winner} "
        f"({scores[winner]:.4f})"
    )

# ==========================
# ROC COMPARISON PLOTS
# ==========================

labels = resnet_results["labels"]

for i, disease in enumerate(DISEASES):

    plt.figure(figsize=(8, 6))

    models_data = [
        (
            "ResNet34",
            resnet_results["outputs"]
        ),
        (
            "DenseNet121_BCE",
            dense_bce_results["outputs"]
        ),
        (
            "DenseNet121_Weighted_BCE",
            dense_weighted_results["outputs"]
        )
    ]

    for name, outputs in models_data:

        try:

            fpr, tpr, _ = roc_curve(
                labels[:, i],
                outputs[:, i]
            )

            roc_auc = auc(
                fpr,
                tpr
            )

            plt.plot(
                fpr,
                tpr,
                label=f"{name} ({roc_auc:.3f})"
            )

        except Exception as e:

            print(
                disease,
                name,
                e
            )

    plt.plot(
        [0, 1],
        [0, 1],
        linestyle="--"
    )

    plt.xlabel(
        "False Positive Rate"
    )

    plt.ylabel(
        "True Positive Rate"
    )

    plt.title(
        f"{disease} ROC Comparison"
    )

    plt.legend()

    plt.grid(True)

    plt.savefig(
        os.path.join(
            OUTPUT_DIR,
            f"{disease}_ROC_Comparison.png"
        ),
        bbox_inches="tight"
    )

    plt.close()

print("\nROC plots saved.")

# ==========================
# OVERALL WINNER
# ==========================

best_idx = comparison_df[
    "Macro_AUC"
].idxmax()

winner = comparison_df.loc[
    best_idx,
    "Model"
]

winner_auc = comparison_df.loc[
    best_idx,
    "Macro_AUC"
]

print("\n====================")
print("BEST OVERALL MODEL")
print("====================")
print(
    f"{winner} : {winner_auc:.4f}"
)
