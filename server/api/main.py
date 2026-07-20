# Morvo Backend API
# Multi-device key validation + script distribution + usage tracking

from fastapi import FastAPI, HTTPException, Depends
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import hashlib
import time
import sqlite3
import os
from datetime import datetime

app = FastAPI(title="Morvo API", version="0.2.0")

app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

DB_PATH = os.getenv("MORVO_DB", "morvo.db")

# ======== Models ========
class KeyValidateRequest(BaseModel):
    key: str
    device_id: str
    hwid: str

class KeyValidateResponse(BaseModel):
    valid: bool
    tier: str  # free, premium, dev
    expires_at: int
    message: str

class ScriptInfo(BaseModel):
    name: str
    game_id: str
    url: str
    min_tier: str
    downloads: int

# ======== Database ========
def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    conn = get_db()
    conn.executescript("""
        CREATE TABLE IF NOT EXISTS keys (
            key_hash TEXT PRIMARY KEY,
            tier TEXT NOT NULL DEFAULT 'free',
            expires_at INTEGER NOT NULL,
            max_devices INTEGER DEFAULT 1,
            is_active INTEGER DEFAULT 1,
            created_at INTEGER
        );
        CREATE TABLE IF NOT EXISTS device_bindings (
            key_hash TEXT,
            device_fp TEXT,
            last_seen INTEGER,
            PRIMARY KEY (key_hash, device_fp)
        );
        CREATE TABLE IF NOT EXISTS scripts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            game_id TEXT NOT NULL,
            name TEXT NOT NULL,
            url TEXT NOT NULL,
            min_tier TEXT DEFAULT 'free',
            downloads INTEGER DEFAULT 0,
            updated_at INTEGER
        );
        CREATE TABLE IF NOT EXISTS usage_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            key_hash TEXT,
            device_fp TEXT,
            action TEXT,
            timestamp INTEGER
        );
    """)
    conn.commit()
    conn.close()

init_db()

# ======== Key Validation ========
@app.post("/api/v1/validate", response_model=KeyValidateResponse)
async def validate_key(req: KeyValidateRequest):
    conn = get_db()
    cursor = conn.cursor()
    
    # Hash inputs
    key_hash = hashlib.sha256(req.key.encode()).hexdigest()
    device_fp = hashlib.sha256(f"{req.device_id}:{req.hwid}".encode()).hexdigest()
    now = int(time.time())
    
    # Check key
    cursor.execute(
        "SELECT tier, expires_at, max_devices FROM keys WHERE key_hash = ? AND is_active = 1",
        (key_hash,)
    )
    row = cursor.fetchone()
    
    if not row:
        conn.close()
        return KeyValidateResponse(
            valid=False, tier="none", expires_at=0,
            message="Invalid key"
        )
    
    tier, expires_at, max_devices = row
    
    if now > expires_at:
        conn.close()
        return KeyValidateResponse(
            valid=False, tier="none", expires_at=expires_at,
            message="Key expired"
        )
    
    # Check device limit
    cursor.execute(
        "SELECT COUNT(*) FROM device_bindings WHERE key_hash = ?",
        (key_hash,)
    )
    device_count = cursor.fetchone()[0]
    
    cursor.execute(
        "SELECT 1 FROM device_bindings WHERE key_hash = ? AND device_fp = ?",
        (key_hash, device_fp)
    )
    is_bound = cursor.fetchone()
    
    if not is_bound and device_count >= max_devices:
        conn.close()
        return KeyValidateResponse(
            valid=False, tier=tier, expires_at=expires_at,
            message=f"Device limit reached ({max_devices})"
        )
    
    # Bind device
    cursor.execute(
        "INSERT OR REPLACE INTO device_bindings (key_hash, device_fp, last_seen) VALUES (?, ?, ?)",
        (key_hash, device_fp, now)
    )
    
    # Log
    cursor.execute(
        "INSERT INTO usage_log (key_hash, device_fp, action, timestamp) VALUES (?, ?, 'validate', ?)",
        (key_hash, device_fp, now)
    )
    
    conn.commit()
    conn.close()
    
    return KeyValidateResponse(
        valid=True, tier=tier, expires_at=expires_at,
        message="OK"
    )

# ======== Script Distribution ========
@app.get("/api/v1/scripts")
async def get_scripts(game_id: str = "", tier: str = "free"):
    conn = get_db()
    cursor = conn.cursor()
    
    if game_id:
        cursor.execute(
            "SELECT name, game_id, url, min_tier, downloads FROM scripts WHERE game_id = ? AND min_tier <= ? ORDER BY downloads DESC",
            (game_id, tier)
        )
    else:
        cursor.execute(
            "SELECT name, game_id, url, min_tier, downloads FROM scripts WHERE min_tier <= ? ORDER BY downloads DESC",
            (tier,)
        )
    
    scripts = [
        {"name": row[0], "game_id": row[1], "url": row[2], "min_tier": row[3], "downloads": row[4]}
        for row in cursor.fetchall()
    ]
    
    conn.close()
    return {"scripts": scripts, "count": len(scripts)}

@app.get("/api/v1/scripts/{game_id}")
async def get_game_scripts(game_id: str, tier: str = "free"):
    return await get_scripts(game_id, tier)

# ======== Health ========
@app.get("/api/v1/health")
async def health():
    conn = get_db()
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM keys WHERE is_active = 1")
    active_keys = cursor.fetchone()[0]
    cursor.execute("SELECT COUNT(*) FROM scripts")
    script_count = cursor.fetchone()[0]
    conn.close()
    
    return {
        "status": "ok",
        "version": "0.2.0",
        "active_keys": active_keys,
        "scripts": script_count,
        "timestamp": int(time.time())
    }

# ======== Admin: Add key ========
@app.post("/api/v1/admin/keys")
async def admin_add_key(tier: str = "free", days: int = 30, max_devices: int = 1):
    import secrets, hashlib
    
    raw_key = f"morvo-{secrets.token_hex(12)}"
    key_hash = hashlib.sha256(raw_key.encode()).hexdigest()
    expires = int(time.time()) + (days * 86400)
    now = int(time.time())
    
    conn = get_db()
    conn.execute(
        "INSERT INTO keys (key_hash, tier, expires_at, max_devices, is_active, created_at) VALUES (?, ?, ?, ?, 1, ?)",
        (key_hash, tier, expires, max_devices, now)
    )
    conn.commit()
    conn.close()
    
    return {"key": raw_key, "tier": tier, "expires_in_days": days}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)