# ChestX Model Training

This directory contains all scripts, notebooks, and utilities required to train, evaluate, and test deep learning models for multi-label chest X-ray disease classification.

The training pipeline is designed around DenseNet121 and can be extended to evaluate multiple architectures on large-scale chest X-ray datasets.

---

# Folder Structure

```text
Model Train/
│
├── Check_Cuda.py              # Check Your System Have Cuda or Not
├── chestx.ipynb               # For Train Your Model On Kaggle
├── download_dataset.py        # Download NIH Dataset
├── Readme.md 
├── requirements.txt
├── dataset.py                 # dataset is for only train_script_1
├── train_script_1.py          # This is the first configuration of training Script
├── train_script_2.py          # This is the second configuration of training script
├── ex_train_script.py         # This is the Experiment Configuration of training script
├── test.py                    # Test your best model
├── evaluate_models.py         # Evaluate Your All Model's Accuracy
└── export_onnx.py             # Export Your Pytorch Model into ONNX Config.
``` 

---

# Files Description

## Check_Cuda.py

Checks GPU availability and CUDA configuration.

Run:

```bash
python Check_Cuda.py
```

Example Output:

```text
CUDA Available: True
GPU Name: Tesla T4
CUDA Version: 12.x
```

---

## download_dataset.py

Downloads and prepares the chest X-ray dataset.

Supported datasets:

* NIH ChestX-ray14
* CheXpert
* MIMIC-CXR (if configured)

Run:

```bash
python download_dataset.py
```

After downloading, datasets should be organized inside:

```text
dataset/
├── images/
├── train.csv
├── val.csv
└── test.csv
```

---

## dataset.py

Handles:

* Dataset loading
* Label parsing
* Image preprocessing
* Data augmentation
* Train/Validation/Test split
* PyTorch DataLoader creation

---

## train.py

Main training script.

Features:

* DenseNet121 training
* Transfer Learning
* Multi-label classification
* Weighted Binary Cross Entropy Loss
* Validation AUC calculation
* Best model checkpoint saving

Run:

```bash
python train.py
```

Training outputs:

```text
checkpoints/
├── best_model.pth
├── last_model.pth
└── training_logs.csv
```

Example Output:

```text
Epoch 1: AUC = 0.74
Epoch 2: AUC = 0.78
Epoch 3: AUC = 0.80
```

The model with the highest validation AUC is automatically saved.

---

## evaluate_models.py

Evaluates multiple architectures and compares their performance.

Example Models:

* DenseNet121
* ResNet50
* EfficientNet-B0

Metrics:

* ROC-AUC
* Precision
* Recall
* F1 Score
* Validation Loss

Run:

```bash
python evaluate_models.py
```

Example Output:

```text
DenseNet121     AUC: 0.81
ResNet50        AUC: 0.78
EfficientNetB0  AUC: 0.79
```

Use this script to identify the best-performing architecture before deployment.

---

## test.py

Performs inference using a trained model.

Run:

```bash
python test.py --image path/to/image.png
```

Example Output:

```text
Cardiomegaly: 82%
Effusion: 55%
Fibrosis: 42%
```

The script returns disease probabilities for all supported classes.

---

## chestx.ipynb

Kaggle notebook version of the training pipeline.

Recommended for:

* Free GPU access
* Rapid experimentation
* Dataset exploration
* Training without local hardware

---

# Supported Diseases

The model supports multi-label prediction of:

* Atelectasis
* Cardiomegaly
* Consolidation
* Edema
* Effusion
* Emphysema
* Fibrosis
* Hernia
* Infiltration
* Mass
* Nodule
* Pleural Thickening
* Pneumonia
* Pneumothorax

---

# Installation

Install dependencies:

```bash
pip install -r requirements.txt
```

---

# Local Training Workflow

## Step 1: Check GPU

```bash
python Check_Cuda.py
```

---

## Step 2: Download Dataset

```bash
python download_dataset.py
```

---

## Step 3: Train Model

```bash
python train.py
```

---

## Step 4: Evaluate Models

```bash
python evaluate_models.py
```

---

## Step 5: Test Model

```bash
python test.py
```

---

# Training on Kaggle

The repository includes a Kaggle notebook:

```text
chestx.ipynb
```

for cloud-based training.

---

## Step 1: Create a Kaggle Notebook

Navigate to:

https://www.kaggle.com

Create:

```text
New Notebook
```

Enable:

```text
Accelerator → GPU
```

Recommended:

```text
Tesla T4
```

---

## Step 2: Upload Notebook

Upload:

```text
chestx.ipynb
```

or copy the notebook code into Kaggle.

---

## Step 3: Add Dataset

Click:

```text
Add Input
```

Search for:

```text
NIH Chest X-ray Dataset
```

or your custom uploaded dataset.

Attach the dataset to the notebook.

---

## Step 4: Update Dataset Paths

Update dataset locations inside the notebook.

Example:

```python
DATASET_PATH = "/kaggle/input/chest-xray-dataset"
```

---

## Step 5: Install Dependencies

If required:

```python
!pip install -r requirements.txt
```

---

## Step 6: Start Training

Run all notebook cells.

Training will begin automatically.

Example:

```text
Epoch 1/20
Epoch 2/20
Epoch 3/20
...
```

---

## Step 7: Save Best Model

The notebook saves:

```text
best_model.pth
```

which can later be downloaded and deployed to the backend server.

---

# Hardware Recommendations

## Minimum

* 8 GB RAM
* NVIDIA GPU with 4 GB VRAM

## Recommended

* 16 GB RAM
* NVIDIA RTX 3060 or higher

## Kaggle

* Tesla T4 GPU
* 16 GB GPU Memory
* Free cloud training

---

# Evaluation Metrics

The project primarily evaluates models using:

* ROC Curve
* AUC Score
* Precision
* Recall
* F1 Score

AUC is used as the primary model selection metric.

---

# Author

- **Arghadeep Pakhira**
- **Dr. Bidyut Saha**

## Areas of Interest

* Artificial Intelligence
* Deep Learning
* Medical Image Analysis
* Computer Vision
* TinyML
* IoT Systems
* Android Development

## Project

**ChestX: AI-Powered Chest X-Ray Disease Detection System**

An end-to-end medical AI project consisting of:

* Deep Learning Model Training Pipeline
* FastAPI Backend Inference Server
* Android Mobile Application
* Explainable AI using Grad-CAM
* Multi-label Chest Disease Classification

## Connect

GitHub: https://github.com/YOUR_USERNAME

If you find this project useful, please consider giving the repository a ⭐.

---

# Research Note

This training pipeline is intended for educational and research purposes. Results should not be used for clinical diagnosis without proper validation and regulatory approval.
