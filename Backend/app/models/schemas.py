from pydantic import BaseModel, Field
from typing import List, Optional
from datetime import datetime

class DiseasePrediction(BaseModel):
    disease: str
    confidence: float = Field(..., ge=0.0, le=100.0)

class AnalysisResponse(BaseModel):
    id: str
    timestamp: str
    top_prediction: str
    confidence: float
    predictions: List[DiseasePrediction]
    heatmap_data: Optional[str] = None
    image_url: Optional[str] = None

class HistoryItem(BaseModel):
    id: str
    timestamp: str
    prediction: str
    confidence: float
    thumbnail_url: Optional[str] = None

class HistoryResponse(BaseModel):
    total: int
    items: List[HistoryItem]

class ScanFrequency(BaseModel):
    date: str
    count: int

class StatisticsResponse(BaseModel):
    total_scans: int
    average_confidence: float
    most_common_disease: str
    scan_frequency: List[ScanFrequency]
