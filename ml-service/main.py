from fastapi import FastAPI, File, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from ultralytics import YOLO
from PIL import Image
import io

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173", "http://localhost:8080"],
    allow_methods=["*"],
    allow_headers=["*"],
)

model = YOLO("yolov8n.pt")

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/detect-sign")
async def detect_sign(file: UploadFile = File(...)):
    image_bytes = await file.read()
    image = Image.open(io.BytesIO(image_bytes))

    results = model(image)

    return {
        "detections": len(results[0].boxes),
        "raw_classes": [int(c) for c in results[0].boxes.cls.tolist()] if len(results[0].boxes) > 0 else []
    }