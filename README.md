<div align="center">

# ParkSense

### Real-time no-parking alerts, crowd-verified by photo evidence and AI.

[![React](https://img.shields.io/badge/Frontend-React%20%2B%20Vite-61DAFB?style=flat-square&logo=react&logoColor=white)](#tech-stack)
[![Spring Boot](https://img.shields.io/badge/Backend-Spring%20Boot-6DB33F?style=flat-square&logo=springboot&logoColor=white)](#tech-stack)
[![Neon](https://img.shields.io/badge/Database-Neon%20PostgreSQL-00E599?style=flat-square&logo=postgresql&logoColor=white)](#tech-stack)
[![Vercel](https://img.shields.io/badge/Hosted%20on-Vercel-000000?style=flat-square&logo=vercel&logoColor=white)](#deployment)
[![Render](https://img.shields.io/badge/Hosted%20on-Render-46E3B7?style=flat-square&logo=render&logoColor=white)](#deployment)

</div>

---

## Live Demo

- **App:** [parksense-sigma.vercel.app](https://parksense-sigma.vercel.app/)
- **Backend API:** [parksense-vvag.onrender.com](https://parksense-vvag.onrender.com)

> The backend runs on Render's free tier, so the first request after a period of inactivity may take a few seconds while the service spins back up.

## Overview

ParkSense answers one question in real time: **is it safe to park here?**

As a user moves, the app continuously checks their live location against a database of no-parking zones and warns them before they park illegally. If no zone data exists nearby, users can report one by photographing the sign — an AI service verifies that a genuine no-parking sign is present, and once enough independent reporters confirm the same spot, the zone is auto-approved and added to the live map for everyone.

## Features

**Live GPS Monitoring**
Continuously watches the user's position and checks it against known zones, distinguishing between AI-verified zones and reports still pending review.

**Find Parking Nearby**
One-tap search for legal parking near the user's current location, with distance and directions to each spot.

**Photo-Verified Zone Reporting**
Users report a no-parking zone by uploading a photo of the sign. The ML service checks for a genuine sign before the report is accepted, and duplicate reports from the same reporter at the same location aren't double-counted.

**Admin Review Panel**
A key-gated dashboard for approving or rejecting zones that haven't yet met the auto-approval threshold, showing the photo, reporter count, and confidence for each.

## Architecture

```
React (Vercel)
      │
      ▼
Spring Boot API (Render)  ───────────►  Neon PostgreSQL
      │
      ├──►  ML Verification Service — YOLOv8 (Render)
      │
      └──►  Geoapify Places API  (nearby parking data)
```

## Tech Stack

| Layer                    | Technology                              |
|----------------------------|--------------------------------------------|
| Frontend                  | React (Vite)                            |
| Frontend Hosting          | Vercel                                  |
| Backend                   | Spring Boot (Java), HikariCP            |
| Backend Hosting           | Render (Docker)                         |
| Database                  | Neon — serverless PostgreSQL            |
| ML Verification Service   | Python, YOLOv8 (Ultralytics) sign detection |
| ML Service Hosting        | Render (Docker)                         |
| Nearby Parking Data       | Geoapify Places API                     |

## Getting Started

### Prerequisites
- Node.js 18+
- Java 17+ and Maven
- Python 3.11+
- A Neon PostgreSQL database
- A free Geoapify API key

### Clone the repository
```bash
git clone https://github.com/Jamalpur-Akash/Parksense.git
cd parksense
```

### Backend
```bash
cd backend
# set your Neon connection string, admin key, ML service URL,
# and Geoapify key in application-local.properties
.\mvnw clean spring-boot:run
```

### Frontend
```bash
cd frontend
echo "VITE_API_URL=http://localhost:8080" > .env.local
npm install
npm run dev
```

### ML Service
```bash
cd ml-service
.\venv\Scripts\Activate.ps1      # or source venv/bin/activate on macOS/Linux
uvicorn main:app --reload --port 8000
```

## Environment Variables

**Backend**

| Variable          | Description                                         |
|--------------------|------------------------------------------------------|
| `ML_SERVICE_URL`   | URL of the deployed ML sign-verification service     |
| `GEOAPIFY_API_KEY` | API key for the Geoapify Places API                  |
| `ADMIN_KEY`        | Shared secret required to approve or reject zones    |
| `Datasource URL/credentials` | Neon PostgreSQL connection details           |

**Frontend**

| Variable        | Description                          |
|------------------|----------------------------------------|
| `VITE_API_URL`   | Base URL of the deployed backend API   |

## API Reference

| Method | Endpoint                     | Description                                              |
|--------|-------------------------------|------------------------------------------------------------|
| POST   | `/check-location`            | Checks whether a coordinate falls inside a known zone      |
| GET    | `/parking/nearby`             | Returns nearby legal parking spots                         |
| POST   | `/zones/report-with-photo`    | Submits a new zone report with a photo for AI verification |
| GET    | `/zones/pending`               | Lists zones awaiting admin review                           |
| POST   | `/zones/{id}/approve`         | Approves a pending zone                                    |
| POST   | `/zones/{id}/reject`          | Rejects a pending zone                                     |

## Deployment

The frontend is deployed on Vercel, the backend and ML service on Render (via Docker), and the database on Neon — three independent services communicating entirely over HTTPS, with no local infrastructure required.

## Project Structure

```
parksense/
├── backend/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/parksense/backend/
│   │       │   ├── config/
│   │       │   │   └── WebConfig.java
│   │       │   ├── controller/
│   │       │   │   ├── LocationController.java
│   │       │   │   ├── ParkingController.java
│   │       │   │   └── ZoneController.java
│   │       │   ├── model/
│   │       │   │   └── Zone.java
│   │       │   ├── repository/
│   │       │   │   ├── NearbyZoneMatch.java
│   │       │   │   ├── PendingZone.java
│   │       │   │   ├── ZoneRepository.java
│   │       │   │   └── ZoneSummary.java
│   │       │   └── BackendApplication.java
│   │       └── resources/
│   │           ├── application.properties
│   │           └── application-local.properties
│   ├── docs/
│   ├── Dockerfile
│   └── pom.xml
│
├── frontend/
│   ├── src/
│   │   ├── assets/
│   │   ├── App.jsx
│   │   ├── App.css
│   │   ├── main.jsx
│   │   └── index.css
│   ├── index.html
│   ├── vite.config.js
│   └── package.json
│
├── ml-service/
│   ├── main.py
│   ├── requirements.txt
│   ├── yolov8n.pt
│   └── Dockerfile
│
└── README.md
```

## About

ParkSense was built to explore how real-time geofencing, crowdsourced reporting, and computer vision can work together to solve a small but genuinely annoying everyday problem.