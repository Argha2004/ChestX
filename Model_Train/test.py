import torch
import torch.nn as nn
from torchvision import models, transforms
from PIL import Image

import cv2
import numpy as np
import matplotlib.pyplot as plt

from pytorch_grad_cam import GradCAM
from pytorch_grad_cam.utils.image import show_cam_on_image
from pytorch_grad_cam.utils.model_targets import ClassifierOutputTarget

# ==================================
# PATHS
# ==================================

MODEL_PATH = r"Your Model Path Here"
IMAGE_PATH = r"Your Test Image Path Here"

# ==================================
# DEVICE
# ==================================

DEVICE = torch.device(
    "cuda" if torch.cuda.is_available() else "cpu"
)

print("Using Device:", DEVICE)

# ==================================
# LABELS
# ==================================

LABELS = [
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

# ==================================
# TRANSFORM
# ==================================

transform = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
    transforms.Normalize(
        [0.485, 0.456, 0.406],
        [0.229, 0.224, 0.225]
    )
])

# ==================================
# CREATE MODEL
# ==================================

model = models.densenet121(weights=None)

model.classifier = nn.Linear(
    model.classifier.in_features,
    14
)

# ==================================
# LOAD CHECKPOINT
# ==================================

checkpoint = torch.load(
    MODEL_PATH,
    map_location=DEVICE,
    weights_only=False
)

print("\nCheckpoint Loaded")
print("Best Epoch :", checkpoint["epoch"])
print("Best AUC   :", checkpoint["auc"])

model.load_state_dict(
    checkpoint["model_state_dict"]
)

model = model.to(DEVICE)
model.eval()

print("Model Loaded Successfully")

# ==================================
# GRAD-CAM TARGET LAYER
# ==================================

target_layers = [
    model.features[-1]
]

# ==================================
# LOAD IMAGE
# ==================================

original_image = Image.open(
    IMAGE_PATH
).convert("RGB")

image = original_image.copy()

image_tensor = transform(image)
image_tensor = image_tensor.unsqueeze(0)
image_tensor = image_tensor.to(DEVICE)

# ==================================
# INFERENCE
# ==================================

with torch.no_grad():

    outputs = model(image_tensor)

    probabilities = torch.sigmoid(
        outputs
    ).cpu().numpy()[0]

# ==================================
# SORT RESULTS
# ==================================

results = list(
    zip(LABELS, probabilities)
)

results.sort(
    key=lambda x: x[1],
    reverse=True
)

# ==================================
# TOP PREDICTION
# ==================================

best_disease, best_prob = results[0]

print("\n" + "=" * 60)
print("MOST LIKELY DISEASE")
print("=" * 60)

print(
    f"{best_disease} "
    f"({best_prob * 100:.2f}%)"
)

# ==================================
# TOP 5 DISEASES
# ==================================

print("\n" + "=" * 60)
print("TOP 5 POSSIBLE DISEASES")
print("=" * 60)

for disease, prob in results[:5]:

    print(
        f"{disease:<20}"
        f"{prob * 100:.2f}%"
    )

# ==================================
# DETECTED DISEASES
# ==================================

print("\n" + "=" * 60)
print("DETECTED DISEASES (>30%)")
print("=" * 60)

detected_indices = []

for idx, prob in enumerate(probabilities):

    if prob >= 0.30:

        detected_indices.append(idx)

        print(
            f"{LABELS[idx]:<20}"
            f"{prob * 100:.2f}%"
        )

if len(detected_indices) == 0:

    print(
        "No disease detected with high confidence."
    )

# ==================================
# ALL PROBABILITIES
# ==================================

print("\n" + "=" * 60)
print("ALL DISEASE PROBABILITIES")
print("=" * 60)

for disease, prob in results:

    print(
        f"{disease:<20}"
        f"{prob * 100:.2f}%"
    )

# ==================================
# PREPARE IMAGE FOR GRAD-CAM
# ==================================

rgb_img = np.array(
    original_image.resize((224, 224))
).astype(np.float32) / 255.0

cam = GradCAM(
    model=model,
    target_layers=target_layers
)

# ==================================
# GENERATE GRAD-CAM
# ==================================

print("\n" + "=" * 60)
print("GENERATING GRAD-CAM")
print("=" * 60)

if len(detected_indices) == 0:

    detected_indices = [
        np.argmax(probabilities)
    ]

for idx in detected_indices:

    disease = LABELS[idx]

    targets = [
        ClassifierOutputTarget(idx)
    ]

    grayscale_cam = cam(
        input_tensor=image_tensor,
        targets=targets
    )[0]

    visualization = show_cam_on_image(
        rgb_img,
        grayscale_cam,
        use_rgb=True
    )

    save_path = (
        f"gradcam_{disease}.jpg"
    )

    cv2.imwrite(
        save_path,
        cv2.cvtColor(
            visualization,
            cv2.COLOR_RGB2BGR
        )
    )

    print(
        f"Saved: {save_path}"
    )

    plt.figure(figsize=(8, 8))
    plt.imshow(visualization)
    plt.title(
        f"Grad-CAM: {disease}"
    )
    plt.axis("off")
    plt.show()

print("\nDone.")