import numpy as np
from PIL import Image
import io
import cv2

class ImageProcessor:
    """
    Image preprocessing utilities for chest X-ray images
    """
    
    def __init__(self, target_size=(512, 512)):
        self.target_size = target_size
    
    def process_image(self, image_data: bytes) -> np.ndarray:
        """
        Process uploaded image data
        
        Args:
            image_data: Raw image bytes
            
        Returns:
            Processed image as numpy array
        """
        try:
            # Load image from bytes
            image = Image.open(io.BytesIO(image_data))
            
            # Convert to RGB if needed
            if image.mode != 'RGB':
                if image.mode == 'RGBA':
                    # Create white background
                    background = Image.new('RGB', image.size, (255, 255, 255))
                    background.paste(image, mask=image.split()[3] if len(image.split()) == 4 else None)
                    image = background
                else:
                    image = image.convert('RGB')
            
            # Resize
            image = image.resize(self.target_size, Image.Resampling.LANCZOS)
            
            # Convert to numpy array
            image_array = np.array(image)
            
            # Apply preprocessing
            image_array = self._preprocess(image_array)
            
            return image_array
            
        except Exception as e:
            raise ValueError(f"Image processing failed: {str(e)}")
    
    def _preprocess(self, image: np.ndarray) -> np.ndarray:
        """
        Apply image preprocessing techniques
        
        Args:
            image: Image as numpy array
            
        Returns:
            Preprocessed image
        """
        # Convert to grayscale for medical image processing
        if len(image.shape) == 3:
            gray = cv2.cvtColor(image, cv2.COLOR_RGB2GRAY)
        else:
            gray = image
        
        # Apply CLAHE (Contrast Limited Adaptive Histogram Equalization)
        clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
        enhanced = clahe.apply(gray)
        
        # Convert back to RGB for model input
        enhanced_rgb = cv2.cvtColor(enhanced, cv2.COLOR_GRAY2RGB)
        
        return enhanced_rgb
    
    def create_thumbnail(self, image_data: bytes, size=(100, 100)) -> bytes:
        """
        Create thumbnail from image
        
        Args:
            image_data: Original image bytes
            size: Thumbnail size
            
        Returns:
            Thumbnail image bytes
        """
        image = Image.open(io.BytesIO(image_data))
        image.thumbnail(size, Image.Resampling.LANCZOS)
        
        buffer = io.BytesIO()
        image.save(buffer, format='JPEG', quality=85)
        return buffer.getvalue()
