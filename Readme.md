# ChestX: AI-Powered Chest X-Ray Disease Detection System

## Overview

ChestX is an end-to-end Artificial Intelligence system designed for automated chest X-ray disease detection. The project combines Deep Learning, Medical Image Analysis, Backend API Services, and a Modern Android Mobile Application to provide fast and accessible chest radiograph screening.

The system uses a DenseNet121-based deep learning model trained on large-scale chest X-ray datasets to identify multiple thoracic diseases from chest radiographs. Predictions are served through a backend API and visualized through a user-friendly Android application.

---

## Key Features

### AI Disease Detection

* Multi-label chest disease classification
* DenseNet121-based deep learning architecture
* Transfer learning approach
* Weighted Binary Cross Entropy Loss
* ROC-AUC based evaluation
* Probability-based disease prediction

### Explainable AI

* Grad-CAM heatmap generation
* Visual localization of disease-related regions
* Model interpretability support
* Clinical decision support visualization

### Android Application

* Modern Android UI using Jetpack Compose
* Upload X-ray images from gallery
* Camera image capture support
* Real-time disease prediction
* Confidence score visualization
* Prediction history
* Grad-CAM visualization
* Mobile-first design

### Backend API

* FastAPI-based inference server
* RESTful API architecture
* Image preprocessing pipeline
* Model inference endpoint
* Grad-CAM generation endpoint
* JSON response support

### Research-Oriented Development

* Medical AI workflow implementation
* Reproducible training pipeline
* Dataset preprocessing utilities
* Evaluation and benchmarking tools

---

## Repository Structure

```text
ChestX/
│
├── Frontend/
│   ├── Android Application
│   ├── Jetpack Compose UI
│   ├── API Integration
│   ├── Camera & Gallery Support
│   └── Visualization Components
│
├── Backend/
│   ├── app/
│   │     ├── models/
│   │     │    ├── prediction.py
│   │     │    └── schemas.py
│   │     ├── utils/
│   │     │    └── image_processor.py 
│   │     └── main.py     
│   ├── models/
│   │    └── Model.pth      # Your Trained Model
│   ├── start_server.bat    # Start Backend Server on Windows System
│   ├── start_server.sh     # Start Backend Server on Linux System
│   ├── requirements.txt
│   ├── Readme.md
│   └── .gitignore
│
├── Model Train/
│   ├── Check_Cuda.py             # Check If Your System Have Cuda or Not
│   ├── download_dataset.py       # Download NIH Chest-Xray Dataset
│   ├── dataset.py                # Connect Dataset to Neural Network
│   ├── train.py                  # Train Your CNN Model From Scratch
│   ├── test.py                   # Test Your Trained Model
│   ├── evaluate_models.py        # Compare Your Models to Each Other
│   ├── chestx.ipynb              # Run All Scripts in Kaggle/Google Colab
│   ├── requirements.txt
│   └── Readme.md
│        
├── License
├── .gitignore
├──
└── README.md
```

---

## Project Architecture

```text
Chest X-Ray Image
        │
        ▼
 Android Application
        │
        ▼
    Backend API
        │
        ▼
  Image Preprocessing
        │
        ▼
 DenseNet121/EfficientNet-B0/EfficientNetV2-S AI Model
        │
        ▼
 Disease Prediction
        │
        ├── Confidence Scores
        ├── Disease Labels
        └── Grad-CAM Heatmap
        │
        ▼
 Android Results Dashboard
```

---

## Supported Disease Categories

The model is designed for multi-label classification of chest X-ray diseases including:

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

## Deep Learning Model

### Model Architecture

* DenseNet121
* Transfer Learning
* Multi-label Classification
* Sigmoid Activation
* Weighted Binary Cross Entropy Loss

### Training Strategy

* Image Resizing
* Data Augmentation
* Class Imbalance Handling
* Weighted BCE Loss
* Learning Rate Scheduling
* Best Model Checkpoint Saving

### Evaluation Metrics

* ROC Curve
* Area Under Curve (AUC)
* Validation Loss
* Precision
* Recall
* F1 Score

---

## Datasets

The project is designed to support large-scale public chest X-ray datasets such as:

### NIH ChestX-ray14

* Over 100,000 chest X-ray images
* 14 disease labels
* Widely used benchmark dataset

### CheXpert

* Large-scale chest radiograph dataset
* Expert-labeled findings
* Clinical-grade annotations

### MIMIC-CXR

* Hospital-scale chest radiograph dataset
* Rich clinical metadata
* Research-oriented benchmark

---

## Android Application

### Features

* Modern Material Design UI
* Dark Theme Support
* Image Upload
* Camera Capture
* Prediction Dashboard
* Disease Confidence Scores
* Grad-CAM Visualization
* Analysis History
* Mobile Optimized Interface

### Technology Stack

* Kotlin
* Jetpack Compose
* Material 3
* Retrofit
* Coil
* CameraX
* Navigation Compose
* ViewModel Architecture

---

## Backend

### Features

* FastAPI Framework
* RESTful APIs
* Model Loading
* Batch Inference
* Grad-CAM Generation
* JSON Responses

### Technology Stack

* Python
* FastAPI
* PyTorch
* OpenCV
* NumPy
* Pillow
* Uvicorn

---

## Model Training

### Technology Stack

* Python
* PyTorch
* Torchvision
* NumPy
* Pandas
* Scikit-learn
* Matplotlib

### Training Pipeline

1. Dataset Loading
2. Data Cleaning
3. Label Processing
4. Data Augmentation
5. Model Training
6. Validation
7. AUC Evaluation
8. Checkpoint Saving

---

## Installation

### Clone Repository

```bash
git clone https://github.com/Argha2004/ChestX.git
cd ChestX
```

---

## Run Backend

```bash
cd Backend

pip install -r requirements.txt

uvicorn app:app --reload
```

---

## Run Android Application

Open the Frontend project in Android Studio and run:

```bash
Build → Run App
```

---

## Train Model

```bash
cd "Model Train"

python train.py
```

---

## Future Improvements

* TensorFlow Lite Deployment
* Offline Mobile Inference
* Multi-language Support
* PDF Report Generation
* Doctor Dashboard
* Cloud Deployment
* Patient Management System
* Real-Time Clinical Integration

---

## Research Applications

This project can be used for:

* Medical AI Research
* Computer Vision Research
* Healthcare Informatics
* Deep Learning Studies
* Academic Projects
* Final Year Projects
* Medical Imaging Applications

---

## Disclaimer

This project is intended for educational, research, and development purposes only. The predictions generated by the model should not be used as a substitute for professional medical diagnosis or clinical decision-making.

---

## Author

**Arghadeep Pakhira**

Student | Sister Nivedita University

**Dr. Bidyut Saha**

Assistant Professor | Sister Nivedita University

Developed as part of an AI-powered Medical Imaging and Chest X-Ray Disease Detection research project.

If you find this project useful, consider giving it a star.

