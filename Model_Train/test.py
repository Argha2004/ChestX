import torch
import torch.nn as nn
from torchvision import models, transforms
from PIL import Image

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



##For ResNet34 Model
# model = models.resnet34(weights=None)

# model.fc = nn.Linear(
#     model.fc.in_features,
#     14
# )


##For DenseNet121 Model
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
# LOAD IMAGE
# ==================================

image = Image.open(
    IMAGE_PATH
).convert("RGB")

image = transform(image)

image = image.unsqueeze(0)

image = image.to(DEVICE)

# ==================================
# INFERENCE
# ==================================

with torch.no_grad():

    outputs = model(image)

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
# DISEASE DETECTION
# ==================================

print("\n" + "=" * 60)
print("DETECTED DISEASES (>30%)")
print("=" * 60)

detected = False

for disease, prob in results:

    if prob >= 0.30:

        detected = True

        print(
            f"{disease:<20}"
            f"{prob * 100:.2f}%"
        )

if not detected:

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