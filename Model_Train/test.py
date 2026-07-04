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

MODEL_PATH = ""                # Your Best Model Checkpoint Path

IMAGE_PATH = ""                # Your Test Image Path

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
# MODEL
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
    model.features.denseblock4
]

# ==================================
# LOAD IMAGE
# ==================================

original_image = Image.open(
    IMAGE_PATH
).convert("RGB")

image_tensor = transform(
    original_image
)

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
# BEST PREDICTION
# ==================================

best_idx = np.argmax(probabilities)

best_disease = LABELS[best_idx]

best_prob = probabilities[best_idx]

print("\n" + "=" * 60)
print("MOST LIKELY DISEASE")
print("=" * 60)

print(
    f"{best_disease} "
    f"({best_prob * 100:.2f}%)"
)

# ==================================
# GRAD-CAM
# ==================================

rgb_img = np.array(
    original_image.resize((224, 224))
).astype(np.float32) / 255.0

cam = GradCAM(
    model=model,
    target_layers=target_layers
)

targets = [
    ClassifierOutputTarget(best_idx)
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

# ==================================
# SAVE RESULT
# ==================================

save_path = (
    f"gradcam_{best_disease}.jpg"
)

cv2.imwrite(
    save_path,
    cv2.cvtColor(
        visualization,
        cv2.COLOR_RGB2BGR
    )
)

print(
    f"\nGrad-CAM Saved: {save_path}"
)

# ==================================
# DISPLAY RESULT
# ==================================

plt.figure(figsize=(8, 8))

plt.imshow(visualization)

plt.title(
    f"{best_disease} "
    f"({best_prob * 100:.2f}%)"
)

plt.axis("off")

plt.show()

print("\nDone.")