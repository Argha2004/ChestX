#============================
# Train.py  //Training Script
#============================




import torch
import torch.nn as nn
from torch.utils.data import DataLoader
from torchvision import models, transforms
from tqdm import tqdm
from sklearn.metrics import roc_auc_score
import numpy as np
import pandas as pd
from sklearn.metrics import confusion_matrix
from sklearn.metrics import classification_report
from sklearn.metrics import f1_score
from sklearn.metrics import precision_score
from sklearn.metrics import recall_score
import seaborn as sns
from sklearn.metrics import roc_curve, auc
import matplotlib.pyplot as plt
from dataset import ChestXrayDataset

torch.backends.cudnn.benchmark = True


def main():

    device = torch.device(
        "cuda" if torch.cuda.is_available() else "cpu"
    )

    print("Using device:", device)

    print("GPU Count:", torch.cuda.device_count())

    for i in range(torch.cuda.device_count()):
        print(torch.cuda.get_device_name(i))

# ==========================
# DATASET PATHS
# ==========================

    ROOT = "Your Root Directory"   #This is For Dataset Root

    CSV = f"{ROOT}/Data_Entry_2017.csv"

    TRAIN_LIST = f"{ROOT}/train_val_list.txt"
    TEST_LIST = f"{ROOT}/test_list.txt"

# ==========================
# TRANSFORMS
# ==========================

    train_transform = transforms.Compose([
        transforms.Resize((224,224)),
        #transforms.RandomCrop((224,224)),
        transforms.RandomHorizontalFlip(),
        transforms.RandomRotation(5),
        transforms.ColorJitter(
            brightness=0.1,
            contrast=0.1
        ),
        transforms.ToTensor(),
        transforms.Normalize(
            [0.485, 0.456, 0.406],
            [0.229, 0.224, 0.225]
        )
    ])
    
    val_transform = transforms.Compose([
        transforms.Resize((224,224)),
        transforms.ToTensor(),
        transforms.Normalize(
            [0.485, 0.456, 0.406],
            [0.229, 0.224, 0.225]
        )
    ])
# ==========================
# DATASET
# ==========================

    train_dataset = ChestXrayDataset(
        CSV,
        ROOT,
        TRAIN_LIST,
        train_transform
    )
    
    test_dataset = ChestXrayDataset(
        CSV,
        ROOT,
        TEST_LIST,
        val_transform
    )


# ==========================
# COMPUTE POSITIVE WEIGHTS
# ==========================
    
    print("Computing class weights...")
    
    all_labels = []
    
    for img_name in train_dataset.image_names:
    
        if img_name in train_dataset.encoded_labels:
    
            all_labels.append(
                train_dataset.encoded_labels[img_name].numpy()
            )

    all_labels = np.array(all_labels)
    
    positive_count = all_labels.sum(axis=0)
    
    negative_count = len(all_labels) - positive_count
    
    pos_weight = torch.tensor(
        negative_count / (positive_count + 1e-6),
        dtype=torch.float32
    )

    pos_weight = torch.clamp(
        pos_weight,
        min=1.0,
        max=100.0
    )

    pos_weight = pos_weight.to(device)
    
    print("Class Weights:")
    print(pos_weight)

    print("\nDisease-wise Class Weights\n")
    
    for disease, weight in zip(
        train_dataset.labels_list,
        pos_weight.cpu().numpy()
    ):
        print(
            f"{disease:<20} {weight:.2f}"
        )

# ==========================
# DATALOADER
# ==========================

    train_loader = DataLoader(
        train_dataset,
        batch_size=192,
        shuffle=True,
        num_workers=4,
        pin_memory=True,
        persistent_workers=True,
        prefetch_factor=2
    )

    test_loader = DataLoader(
        test_dataset,
        batch_size=192,
        shuffle=False,
        num_workers=4,
        pin_memory=True,
        persistent_workers=True,
        prefetch_factor=2
    )

# ==========================
# MODEL
# ==========================

    model = models.densenet121(
        weights=models.DenseNet121_Weights.IMAGENET1K_V1
    )
    
    model.classifier = nn.Linear(
        model.classifier.in_features,
        14
    )

    model = model.to(device)

    if torch.cuda.device_count() > 1:
        print(f"Using {torch.cuda.device_count()} GPUs")
        model = nn.DataParallel(model)

# ==========================
# LOSS
# ==========================

    criterion = nn.BCEWithLogitsLoss(
        pos_weight=pos_weight
    )

    optimizer = torch.optim.AdamW(
        model.parameters(),
        lr=3e-4,
        weight_decay=1e-4
    )

    scheduler = torch.optim.lr_scheduler.ReduceLROnPlateau(
        optimizer,
        mode="max",
        factor=0.5,
        patience=2
    )

    scaler = torch.amp.GradScaler(
        "cuda",
        enabled=torch.cuda.is_available()
    )

    best_auc = 0
    epochs = 10
    patience = 2
    counter = 0
    
    metrics = []

# ==========================
# TRAINING LOOP
# ==========================

    for epoch in range(epochs):

        print(f"\nEpoch {epoch+1}/{epochs}")

        model.train()

        running_loss = 0

        for images, labels in tqdm(train_loader):

            images = images.to(
                device,
                non_blocking=True
            )

            labels = labels.to(
                device,
                non_blocking=True
            )

            optimizer.zero_grad()

            with torch.amp.autocast(
                "cuda",
                enabled=torch.cuda.is_available()
            ):

                outputs = model(images)

                loss = criterion(
                    outputs,
                    labels
                )

            scaler.scale(loss).backward()

            scaler.step(optimizer)

            scaler.update()

            running_loss += loss.item()

        train_loss = running_loss / len(train_loader)

        print(
            f"Train Loss: {train_loss:.4f}"
        )


# ==========================
# VALIDATION
# ==========================

        model.eval()

        all_labels = []
        all_outputs = []

        with torch.no_grad():

            for images, labels in test_loader:

                images = images.to(device)

                # Original image prediction
                outputs1 = model(images)
                
                # Horizontally flipped image
                flipped_images = torch.flip(
                    images,
                    dims=[3]
                )
                
                outputs2 = model(flipped_images)
                
                # Average predictions
                probs = (
                    torch.sigmoid(outputs1) +
                    torch.sigmoid(outputs2)
                ) / 2

                all_labels.append(
                    labels.numpy()
                )

                all_outputs.append(
                    probs.cpu().numpy()
                )

        all_labels = np.vstack(all_labels)
        all_outputs = np.vstack(all_outputs)

        try:

            auc = roc_auc_score(
                all_labels,
                all_outputs,
                average="macro"
            )

            print(
                f"Validation AUC: {auc:.4f}"
            )

            metrics.append({
                "epoch": epoch + 1,
                "loss": train_loss,
                "auc": auc
            })

            scheduler.step(auc)

            if auc > best_auc:
            
                best_auc = auc
            
                counter = 0
            
                state_dict = (
                    model.module.state_dict()
                    if isinstance(model, nn.DataParallel)
                    else model.state_dict()
                )

                torch.save({
                    "epoch": epoch + 1,
                    "auc": auc,
                    "model_state_dict": state_dict
                },
                "/Model Train/best_model.pth")
            
                print(
                    f"Best model saved! AUC={auc:.4f}"
                )

            else:
            
                counter += 1
            
                print(
                    f"No improvement ({counter}/{patience})"
                )
            
                if counter >= patience:
            
                    print(
                        "Early stopping triggered!"
                    )
            
                    break
        except Exception as e:

            print(
                f"AUC Error: {e}"
            )


    pd.DataFrame(metrics).to_csv(
        "/Model_Train/training_metrics.csv",
        index=False
    )
    
    print(
        "Metrics CSV saved."
    )
    print(
        f"\nBest AUC: {best_auc:.4f}"
    )


    disease_names = [
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

    for i, disease in enumerate(disease_names):
    
        try:
    
            disease_auc = roc_auc_score(
                all_labels[:, i],
                all_outputs[:, i]
            )
    
            print(
                f"{disease}: {disease_auc:.4f}"
            )
    
        except Exception as e:
            print(f"{disease}: {e}")


    metrics_df = pd.DataFrame(metrics)

# ==========================
# LOSS CURVE
# ==========================
    
    plt.figure(figsize=(8, 5))
    
    plt.plot(
        metrics_df["epoch"],
        metrics_df["loss"],
        marker="o"
    )

    plt.xlabel("Epoch")
    plt.ylabel("Loss")
    plt.title("Training Loss Curve")
    plt.grid(True)
    
    plt.savefig(
        "/Model_Train/loss_curve.png",
        bbox_inches="tight"
    )
    
    plt.close()

# ==========================
# AUC CURVE
# ==========================
    
    plt.figure(figsize=(8, 5))
    
    plt.plot(
        metrics_df["epoch"],
        metrics_df["auc"],
        marker="o"
    )

    plt.xlabel("Epoch")
    plt.ylabel("AUC")
    plt.title("Validation AUC Curve")
    plt.grid(True)
    
    plt.savefig(
        "/Model_Train/auc_curve.png",
        bbox_inches="tight"
    )

    plt.close()
    
    print("Loss curve saved.")
    print("AUC curve saved.")


# ==========================
# ROC CURVE
# ==========================
    
    disease_names = [
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

    plt.figure(figsize=(10, 8))
    
    for i, disease in enumerate(disease_names):
    
        try:
    
            fpr, tpr, _ = roc_curve(
                all_labels[:, i],
                all_outputs[:, i]
            )
    
            roc_auc = auc(fpr, tpr)
    
            plt.plot(
                fpr,
                tpr,
                label=f"{disease} ({roc_auc:.3f})"
            )
    
        except Exception as e:
            print(f"{disease}: {e}")

    plt.plot(
        [0, 1],
        [0, 1],
        linestyle="--"
    )
    
    plt.xlabel("False Positive Rate")
    plt.ylabel("True Positive Rate")
    plt.title("ROC Curves - NIH Chest X-ray")
    plt.legend(fontsize=8)
    plt.grid(True)
    
    plt.savefig(
        "/Model_Train/roc_curves.png",
        bbox_inches="tight"
    )
    
    plt.close()
    
    print("ROC Curves saved.")



# ==========================
# CLASS DISTRIBUTION PLOT
# ==========================
    
    disease_names = train_dataset.labels_list
    
    plt.figure(figsize=(12,6))
    
    plt.bar(
        disease_names,
        positive_count
    )
    
    plt.xticks(rotation=45)
    
    plt.ylabel("Number of Positive Samples")
    
    plt.title(
        "Class Distribution - NIH Chest X-ray14"
    )

    plt.tight_layout()
    
    plt.savefig(
        "/Model_Train/class_distribution.png",
        bbox_inches="tight"
    )
    
    plt.close()
    
    print("Class Distribution Saved.")



    # ==========================
    # CONFUSION MATRICES
    # ==========================
    
    threshold = 0.3
    
    for i, disease in enumerate(disease_names):
    
        try:
    
            y_true = all_labels[:, i]
    
            y_pred = (
                all_outputs[:, i] >= threshold
            ).astype(int)
    
            cm = confusion_matrix(
                y_true,
                y_pred
            )

            plt.figure(figsize=(5,4))
    
            sns.heatmap(
                cm,
                annot=True,
                fmt="d",
                cmap="Blues"
            )
    
            plt.title(
                f"{disease} Confusion Matrix"
            )
    
            plt.ylabel("Actual")
    
            plt.xlabel("Predicted")
    
            plt.savefig(
                f"/Model_Train/{disease}_cm.png",
                bbox_inches="tight"
            )
    
            plt.close()

        except Exception as e:
    
            print(
                f"{disease}: {e}"
            )
    
    print("Confusion matrices saved.")


    print("\nClassification Reports\n")
    for i, disease in enumerate(disease_names):
    
        y_true = all_labels[:, i]
    
        y_pred = (
            all_outputs[:, i] >= threshold
        ).astype(int)
    
        print(f"\n{disease}")
    
        print(
            classification_report(
                y_true,
                y_pred,
                digits=4,
                zero_division=0
            )
        )


#====================
# F1-Score
#====================

    macro_f1 = f1_score(
        all_labels,
        (all_outputs >= threshold).astype(int),
        average="macro",
        zero_division=0
    )
    
    print(f"\nMacro F1 Score: {macro_f1:.4f}")




#====================
# Preciosion & Recall
#====================
    
    macro_precision = precision_score(
        all_labels,
        (all_outputs >= threshold).astype(int),
        average="macro",
        zero_division=0
    )
    
    macro_recall = recall_score(
        all_labels,
        (all_outputs >= threshold).astype(int),
        average="macro",
        zero_division=0
    )
    
    print(
        f"Macro Precision: {macro_precision:.4f}"
    )
    
    print(
        f"Macro Recall: {macro_recall:.4f}"
    )


if __name__ == "__main__":
    main()