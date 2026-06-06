# Chest X-Ray Classification Backend

FastAPI backend for chest X-ray disease classification with AI model inference.

## Setup Instructions

### Prerequisites

- Python 3.9 or higher
- pip package manager
- Virtual environment (recommended)

### Installation

1. Create and activate virtual environment:

```bash
# Windows
python -m venv venv
venv\Scripts\activate

# Linux/Mac
python3 -m venv venv
source venv/bin/activate
```

2. Install dependencies:

```bash
pip install -r requirements.txt
```

### Running the Server

#### Development Mode

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

#### Production Mode

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers 4
```

The API will be available at: `http://localhost:8000`

API Documentation (Swagger UI): `http://localhost:8000/docs`

Alternative Documentation (ReDoc): `http://localhost:8000/redoc`

### Deploying to Vercel

This repository includes a Vercel configuration that serves the FastAPI app from `api/index.py`.

1. Push the repo to GitHub.
2. Import the project into Vercel.
3. Set the root directory to `Backend` if Vercel does not detect it automatically.
4. Deploy using the existing Python runtime configuration.

The deployed app will expose the same FastAPI routes defined in `app/main.py`.

## API Endpoints

### Health Check

```
GET /api/health
```

Returns server status and model availability.

### Analyze X-Ray

```
POST /api/analyze
Content-Type: multipart/form-data
```

Upload chest X-ray image for disease classification.

**Request:**
- `file`: Image file (JPEG, PNG)

**Response:**
```json
{
  "id": "scan_123456789",
  "timestamp": "2023-10-26T10:30:00",
  "top_prediction": "Pneumonia",
  "confidence": 95.0,
  "predictions": [
    {
      "disease": "Pneumonia",
      "confidence": 95.0
    },
    {
      "disease": "Infiltration",
      "confidence": 65.0
    }
  ],
  "heatmap_data": "base64_encoded_image",
  "image_url": "/api/images/123"
}
```

### Get History

```
GET /api/history?limit=50&offset=0
```

Retrieve scan history with pagination.

### Get Statistics

```
GET /api/statistics
```

Get dashboard statistics including total scans, average confidence, and scan frequency.

### Get Scan Detail

```
GET /api/scan/{scan_id}
```

Get detailed information about a specific scan.

## Model Configuration

### 🚀 Quick Start: Upload Your Model

**Simply place your `.pth` model file in the `backend/models/` directory!**

```bash
backend/
├── models/
│   └── your_model.pth  ← Place your file here
└── app/
```

The system will **automatically detect and load** your model when the server starts!

### Supported Model Formats

✅ **All common PyTorch save formats are supported:**

1. **State Dict** (Recommended):
   ```python
   torch.save(model.state_dict(), 'model.pth')
   ```

2. **Full Model**:
   ```python
   torch.save(model, 'model.pth')
   ```

3. **Checkpoint with Metadata**:
   ```python
   torch.save({
       'model_state_dict': model.state_dict(),
       'epoch': 100,
       'optimizer_state_dict': optimizer.state_dict()
   }, 'checkpoint.pth')
   ```

### Model Requirements

- **Architecture**: DenseNet121 by default (easily customizable)
- **Input Size**: 224×224 (auto-resized)
- **Output**: Multi-label classification with sigmoid activation
- **Classes**: Auto-detected from model (14 diseases by default)

### Verify Model Loading

Start the server and check the console output:

```bash
uvicorn app.main:app --reload

# You should see:
# 📦 Found model file: backend/models/your_model.pth
# ✓ Model weights loaded successfully!
# ✅ Model loaded and ready for inference!
```

### 📖 Detailed Guide

For complete instructions on model upload, configuration, and troubleshooting:

👉 **See [HOW_TO_UPLOAD_MODEL.md](HOW_TO_UPLOAD_MODEL.md)**

### Quick Configuration

If your model uses a **different architecture**, update `app/models/prediction.py`:

```python
# Change from DenseNet121 to your architecture
model = models.resnet50(pretrained=False)  # or your architecture
```

If you have **custom disease classes**, update the `self.classes` list in `prediction.py`

## Project Structure

```
backend/
├── app/
│   ├── main.py              # FastAPI application
│   ├── models/
│   │   ├── schemas.py       # Pydantic models
│   │   └── prediction.py    # AI model inference
│   └── utils/
│       └── image_processor.py  # Image preprocessing
├── requirements.txt
└── README.md
```

## Testing with cURL

```bash
# Health check
curl http://localhost:8000/api/health

# Analyze X-ray
curl -X POST http://localhost:8000/api/analyze \
  -F "file=@/path/to/xray.jpg"

# Get statistics
curl http://localhost:8000/api/statistics
```

## Environment Variables

Create a `.env` file for configuration:

```env
API_HOST=0.0.0.0
API_PORT=8000
MODEL_PATH=models/chest_xray_model.pth
MAX_UPLOAD_SIZE=10485760
```

## Deployment

### Docker

```dockerfile
FROM python:3.9-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

Build and run:

```bash
docker build -t chest-xray-api .
docker run -p 8000:8000 chest-xray-api
```

### Vercel Notes

- `api/index.py` is the deployment entrypoint.
- `.vercelignore` excludes the large local model file and cache artifacts from the upload.
- If the model file is not present in the deployed environment, the API falls back to mock predictions.

## Performance Optimization

- Use GPU inference for faster predictions
- Implement caching for repeated requests
- Use async processing for batch uploads
- Optimize image preprocessing pipeline

## Security Considerations

- Implement authentication/authorization
- Rate limiting for API endpoints
- Input validation and sanitization
- HTTPS in production
- Secure file upload handling

## Troubleshooting

**Issue:** Model loading fails
- Check PyTorch installation and CUDA compatibility
- Verify model file path and permissions

**Issue:** High memory usage
- Reduce batch size
- Use model quantization
- Implement lazy loading

**Issue:** Slow inference
- Enable GPU acceleration
- Optimize image preprocessing
- Use TorchScript compilation

## License

MIT License
