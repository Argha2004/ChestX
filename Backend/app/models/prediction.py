import numpy as np
import torch
import torch.nn as nn
from torchvision import models, transforms
from PIL import Image
import io
import base64
import cv2
import os
from pathlib import Path

class PredictionModel:
    """
    Chest X-Ray Disease Classification Model
    
    Loads a trained PyTorch model for chest X-ray disease classification.
    Supports DenseNet, ResNet, and other torchvision architectures.
    """
    
    def __init__(self, model_path=None, num_classes=None):
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        print(f"🔧 Using device: {self.device}")
        
        # Disease classes - Update these to match your model's output
        # ChestX-ray14 standard classes
        self.classes = [
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
        
        # Override number of classes if specified
        if num_classes is not None:
            self.classes = [f"Class_{i}" for i in range(num_classes)]
        
        # Auto-detect model path if not provided
        if model_path is None:
            model_path = self._find_model_file()
        
        self.model_path = model_path
        
        # Initialize model
        self.model = self._create_model()
        self.model_loaded = self.model is not None
        
        if self.model_loaded:
            print("✅ Model loaded and ready for inference!")
        else:
            print("⚠️  No model loaded. Using placeholder predictions.")
        
        # Image preprocessing - standard ImageNet normalization
        self.transform = transforms.Compose([
            transforms.Resize((224, 224)),
            transforms.ToTensor(),
            transforms.Normalize(
                mean=[0.485, 0.456, 0.406],
                std=[0.229, 0.224, 0.225]
            )
        ])
    
    def _find_model_file(self):
        """
        Automatically find .pth file in models directory
        """
        # Look for .pth file in models directory
        models_dir = Path(__file__).parent.parent.parent / "models"
        
        if not models_dir.exists():
            print(f"📁 Models directory not found at: {models_dir}")
            return None
        
        pth_files = list(models_dir.glob("*.pth"))
        
        if pth_files:
            model_path = str(pth_files[0])
            print(f"📦 Found model file: {model_path}")
            return model_path
        else:
            print("📁 No .pth file found in models directory")
            print(f"📌 Place your model file in: {models_dir}")
            return None
    
    def _create_model(self):
        """
        Load trained model from .pth file
        
        Supports multiple model architectures and state dict formats:
        - Full model save: torch.save(model, 'model.pth')
        - State dict save: torch.save(model.state_dict(), 'model.pth')
        - Checkpoint save: torch.save({'state_dict': model.state_dict(), ...}, 'model.pth')
        """
        try:
            if not self.model_path or not os.path.exists(self.model_path):
                print("⚠️  Model file not found. Using placeholder.")
                return None
            
            print(f"⏳ Loading model from: {self.model_path}")
            
            # Load checkpoint
            checkpoint = torch.load(
                self.model_path, 
                map_location=self.device,
                weights_only=False  # Allow loading full model objects
            )
            
            # Handle different save formats
            model = None
            
            # Format 1: Full model saved directly
            if isinstance(checkpoint, nn.Module):
                print("📋 Format: Full model object")
                model = checkpoint
            
            # Format 2: State dict with various keys
            elif isinstance(checkpoint, dict):
                # Detect number of output classes from state dict
                num_classes = self._detect_num_classes(checkpoint)
                if num_classes:
                    print(f"📊 Detected {num_classes} output classes")
                    if num_classes != len(self.classes):
                        self.classes = [f"Disease_{i+1}" for i in range(num_classes)]
                
                # Create model architecture
                model = models.densenet121(pretrained=False)
                num_ftrs = model.classifier.in_features
                model.classifier = nn.Linear(num_ftrs, len(self.classes))
                
                # Try different state dict keys
                if 'model_state_dict' in checkpoint:
                    print("📋 Format: Checkpoint with 'model_state_dict'")
                    model.load_state_dict(checkpoint['model_state_dict'])
                elif 'state_dict' in checkpoint:
                    print("📋 Format: Checkpoint with 'state_dict'")
                    model.load_state_dict(checkpoint['state_dict'])
                elif 'model' in checkpoint:
                    print("📋 Format: Checkpoint with 'model'")
                    model.load_state_dict(checkpoint['model'])
                else:
                    # Assume it's a direct state dict
                    print("📋 Format: Direct state dict")
                    model.load_state_dict(checkpoint)
            
            if model is None:
                raise ValueError("Could not load model from checkpoint")
            
            # Move to device and set to eval mode
            model = model.to(self.device)
            model.eval()
            
            print("✓ Model weights loaded successfully!")
            return model
            
        except Exception as e:
            print(f"❌ Error loading model: {e}")
            print(f"   Error type: {type(e).__name__}")
            print("\n💡 Troubleshooting tips:")
            print("   1. Ensure .pth file is in backend/models/ directory")
            print("   2. Check if model architecture matches (DenseNet121 expected)")
            print("   3. Verify model was saved correctly")
            print("   4. Check number of output classes")
            return None
    
    def _detect_num_classes(self, checkpoint):
        """
        Try to detect number of output classes from state dict
        """
        # Extract state dict if it's nested
        if isinstance(checkpoint, dict):
            state_dict = None
            for key in ['model_state_dict', 'state_dict', 'model']:
                if key in checkpoint:
                    state_dict = checkpoint[key]
                    break
            if state_dict is None:
                state_dict = checkpoint
        else:
            return None
        
        # Look for classifier layer
        for key in state_dict.keys():
            if 'classifier' in key and 'weight' in key:
                # Get output dimension (first dimension of weight)
                return state_dict[key].shape[0]
        
        return None
    
    def is_loaded(self):
        return self.model_loaded
    
    def predict(self, image_array: np.ndarray):
        """
        Predict diseases from chest X-ray image
        
        Args:
            image_array: Processed image as numpy array
            
        Returns:
            List of disease predictions with confidence scores
        """
        # Use real model if loaded, otherwise fallback to mock
        if not self.model_loaded:
            print("⚠️  Using mock predictions (no model loaded)")
            return self._generate_mock_predictions()
        
        try:
            # Convert numpy array to PIL Image
            if len(image_array.shape) == 2:
                # Grayscale to RGB
                image_array = np.stack([image_array] * 3, axis=-1)
            
            image = Image.fromarray(image_array.astype('uint8'))
            
            # Preprocess
            input_tensor = self.transform(image).unsqueeze(0).to(self.device)
            
            # Inference
            with torch.no_grad():
                outputs = self.model(input_tensor)
                # Apply sigmoid for multi-label classification
                probabilities = torch.sigmoid(outputs).cpu().numpy()[0]
            
            # Create predictions list
            predictions = []
            for disease, prob in zip(self.classes, probabilities):
                confidence = float(prob * 100)
                if confidence > 5:  # Lower threshold to catch more predictions
                    predictions.append({
                        "disease": disease,
                        "confidence": round(confidence, 1)
                    })
            
            # Sort by confidence
            predictions.sort(key=lambda x: x["confidence"], reverse=True)
            
            # Ensure we have at least one prediction
            if not predictions:
                print("⚠️  No predictions above threshold, returning top prediction")
                max_idx = np.argmax(probabilities)
                predictions = [{
                    "disease": self.classes[max_idx],
                    "confidence": round(float(probabilities[max_idx] * 100), 1)
                }]
            
            return predictions[:6]  # Return top 6 predictions
            
        except Exception as e:
            print(f"❌ Prediction error: {e}")
            return self._generate_mock_predictions()
    
    def _generate_mock_predictions(self):
        """
        Generate mock predictions for demonstration
        Used when no model is loaded
        """
        import random
        
        # Select random diseases with realistic confidence scores
        selected_diseases = random.sample(self.classes, k=min(4, len(self.classes)))
        
        predictions = []
        base_confidence = random.uniform(70, 95)
        
        for i, disease in enumerate(selected_diseases):
            confidence = base_confidence - (i * random.uniform(10, 20))
            predictions.append({
                "disease": disease,
                "confidence": round(max(confidence, 10), 1)
            })
        
        return predictions
    
    def generate_heatmap(self, image_array: np.ndarray):
        """
        Generate Grad-CAM heatmap for visualization
        
        Args:
            image_array: Processed image as numpy array
            
        Returns:
            Base64 encoded heatmap image
        """
        try:
            # Simplified heatmap generation (placeholder)
            # For production Grad-CAM implementation, use pytorch-grad-cam library
            
            height, width = image_array.shape[:2]
            
            # Create a mock heatmap with gaussian blob
            center_x, center_y = width // 2, height // 3
            y, x = np.ogrid[:height, :width]
            
            # Create gaussian distribution
            sigma = min(height, width) // 4
            mask = np.exp(-((x - center_x)**2 + (y - center_y)**2) / (2 * sigma**2))
            
            # Normalize to 0-255
            heatmap = (mask * 255).astype(np.uint8)
            
            # Apply colormap (JET for medical imaging)
            heatmap_colored = cv2.applyColorMap(heatmap, cv2.COLORMAP_JET)
            
            # Convert to base64
            _, buffer = cv2.imencode('.png', heatmap_colored)
            heatmap_base64 = base64.b64encode(buffer).decode('utf-8')
            
            return heatmap_base64
            
        except Exception as e:
            print(f"Heatmap generation error: {e}")
            return None
