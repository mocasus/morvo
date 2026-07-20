# Morvo Backend API
# Multi-device key validation + script distribution

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import hashlib
import time

app = FastAPI(title="Morvo API", version="0.1.0")

@app.get("/health")
async def health():
    return {"status": "ok", "version": "0.1.0"}

@app.post("/api/v1/validate")
async def validate_key(key: str, device_id: str, hwid: str):
    return {"valid": True, "tier": "dev"}
