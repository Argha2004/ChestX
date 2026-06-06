from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from typing import List, Dict
import uvicorn
from datetime import datetime, timedelta
import json
from pathlib import Path

from app.models.prediction import PredictionModel
from app.utils.image_processor import ImageProcessor
from app.models.schemas import (
    AnalysisResponse,
    StatisticsResponse,
    HistoryItem,
    HistoryResponse
)

app = FastAPI(
    title="Chest X-Ray Disease Classification API",
    description="AI-powered chest X-ray analysis for disease detection",
    version="1.0.0"
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize model and processor
prediction_model = PredictionModel()
image_processor = ImageProcessor()

# In-memory storage (use database in production)
scan_history: List[Dict] = []

@app.get("/")
async def root():
    return {
        "message": "Chest X-Ray Disease Classification API",
        "status": "running",
        "version": "1.0.0"
    }

@app.get("/api/health")
async def health_check():
    return {
        "status": "healthy",
        "model_loaded": prediction_model.is_loaded(),
        "timestamp": datetime.now().isoformat()
    }

@app.post("/api/analyze", response_model=AnalysisResponse)
async def analyze_xray(file: UploadFile = File(...)):
    """
    Analyze a chest X-ray image and return disease predictions
    """
    try:
        # Validate file type
        if not file.content_type.startswith("image/"):
            raise HTTPException(status_code=400, detail="File must be an image")
        
        # Read and process image
        image_data = await file.read()
        processed_image = image_processor.process_image(image_data)
        
        # Get predictions
        predictions = prediction_model.predict(processed_image)
        
        # Generate heatmap
        heatmap_data = prediction_model.generate_heatmap(processed_image)
        
        # Get top prediction
        top_prediction = max(predictions, key=lambda x: x["confidence"])
        
        # Create response
        analysis_result = {
            "id": f"scan_{len(scan_history) + 1}_{int(datetime.now().timestamp())}",
            "timestamp": datetime.now().isoformat(),
            "top_prediction": top_prediction["disease"],
            "confidence": top_prediction["confidence"],
            "predictions": predictions,
            "heatmap_data": heatmap_data,
            "image_url": f"/api/images/{len(scan_history)}"
        }
        
        # Store in history
        scan_history.append(analysis_result)
        
        return analysis_result
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Analysis failed: {str(e)}")

@app.get("/api/history", response_model=HistoryResponse)
async def get_history(limit: int = 50, offset: int = 0):
    """
    Get scan history with pagination
    """
    total = len(scan_history)
    items = scan_history[offset:offset + limit]
    
    # Convert to history items
    history_items = [
        {
            "id": item["id"],
            "timestamp": item["timestamp"],
            "prediction": item["top_prediction"],
            "confidence": item["confidence"],
            "thumbnail_url": item.get("image_url", "")
        }
        for item in reversed(items)
    ]
    
    return {
        "total": total,
        "items": history_items
    }

@app.get("/api/statistics", response_model=StatisticsResponse)
async def get_statistics():
    """
    Get dashboard statistics
    """
    if not scan_history:
        return {
            "total_scans": 0,
            "average_confidence": 0.0,
            "most_common_disease": "N/A",
            "scan_frequency": []
        }
    
    # Calculate statistics
    total_scans = len(scan_history)
    
    # Average confidence
    avg_confidence = sum(item["confidence"] for item in scan_history) / total_scans
    
    # Most common disease
    disease_counts = {}
    for item in scan_history:
        disease = item["top_prediction"]
        disease_counts[disease] = disease_counts.get(disease, 0) + 1
    
    most_common = max(disease_counts.items(), key=lambda x: x[1])[0] if disease_counts else "N/A"
    
    # Scan frequency (last 30 days)
    now = datetime.now()
    thirty_days_ago = now - timedelta(days=30)
    
    frequency_data = {}
    for item in scan_history:
        scan_date = datetime.fromisoformat(item["timestamp"])
        if scan_date >= thirty_days_ago:
            date_key = scan_date.strftime("%Y-%m-%d")
            frequency_data[date_key] = frequency_data.get(date_key, 0) + 1
    
    # Generate frequency array (last 30 days)
    scan_frequency = []
    for i in range(30):
        date = (now - timedelta(days=29-i)).strftime("%Y-%m-%d")
        scan_frequency.append({
            "date": date,
            "count": frequency_data.get(date, 0)
        })
    
    return {
        "total_scans": total_scans,
        "average_confidence": round(avg_confidence, 1),
        "most_common_disease": most_common,
        "scan_frequency": scan_frequency
    }

@app.get("/api/scan/{scan_id}")
async def get_scan_detail(scan_id: str):
    """
    Get detailed information about a specific scan
    """
    for item in scan_history:
        if item["id"] == scan_id:
            return item
    
    raise HTTPException(status_code=404, detail="Scan not found")

@app.delete("/api/history")
async def clear_history():
    """
    Clear all scan history
    """
    scan_history.clear()
    return {"message": "History cleared successfully"}

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
