---
title: parksense-ml-service
emoji: 🅿️
sdk: docker
app_port: 7860
---

# ParkSense — AI-Assisted No-Parking Alert System

Detects when a car is stationary in a no-parking zone and 
triggers a dashboard warning, combining GPS geofencing with 
ML-based sign detection.

## Structure
- `backend/` — Spring Boot API (geofence logic, alert engine)
- `ml-service/` — Python YOLO model for sign detection
- `frontend/` — React dashboard simulator
- `docs/` — architecture notes, reports

## Status
🚧 In development — Day 1 of build
