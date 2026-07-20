# Morvo Backend API v0.6.0
# Loot-Link gateway integration + 24h key system + premium tier
# Multi-device key validation + IP binding + rate limiting

from fastapi import FastAPI, HTTPException, Depends, Request, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import RedirectResponse, JSONResponse
from pydantic import BaseModel
from datetime import datetime, timedelta
from collections import defaultdict
import hashlib, hmac, time, secrets, json, sqlite3, os

# ─── Config ─────────────────────────────────────────────
LOOTLINK_WEBHOOK_SECRET = os.getenv("LOOTLINK_WEBHOOK_SECRET", "morvo-wh-secret-change-me")
LOOTLINK_SITE_ID = os.getenv("LOOTLINK_SITE_ID", "morvo")
LOOTLINK_GATEWAY_URL = "https://loot-link.com/s/{site_id}"
MORVO_DOMAIN = os.getenv("MORVO_DOMAIN", "https://mocasus.github.io/morvo")
RATE_LIMIT_WINDOW = 60  # seconds
RATE_LIMIT_MAX = 30     # max requests per window per IP
KEY_EXPIRY_HOURS = 24
PREMIUM_PRICE = 5  # USD/month

app = FastAPI(title="Morvo API", version="0.6.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

DB_PATH = os.getenv("MORVO_DB", "/root/morvo/server/morvo.db")

# ─── Models ──────────────────────────────────────────────
class KeyValidateRequest(BaseModel):
    key: str
    device_id: str
    hwid: str
    client_ip: str = ""

class KeyClaimRequest(BaseModel):
    task_id: str
    user_agent: str = ""
    ip_address: str = ""

class GatewayCallbackPayload(BaseModel):
    event: str          # "task_completed", "reward_claimed"
    task_id: str
    user_id: str
    site_id: str = ""
    reward: str = ""    # key type: "key_free", "key_premium"
    metadata: dict = {}

class KeyGenerateRequest(BaseModel):
    tier: str = "free"  # free, premium, dev
    days: int = 1
    max_devices: int = 1

class KeyBulkRequest(BaseModel):
    count: int = 10
    tier: str = "free"
    days: int = 1

class AdminStatsResponse(BaseModel):
    active_keys: int
    free_keys: int
    premium_keys: int
    total_validations_24h: int
    unique_devices_24h: int
    gateway_completions_24h: int

# ─── Rate Limiter ────────────────────────────────────────
rate_store: dict[str, list[float]] = defaultdict(list)

def rate_limit(ip: str) -> bool:
    """Return True if rate limit NOT exceeded."""
    now = time.time()
    window_start = now - RATE_LIMIT_WINDOW
    # Clean old entries
    rate_store[ip] = [t for t in rate_store[ip] if t > window_start]
    if len(rate_store[ip]) >= RATE_LIMIT_MAX:
        return False
    rate_store[ip].append(now)
    return True

# ─── Database ────────────────────────────────────────────
def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    return conn

def init_db():
    conn = get_db()
    conn.executescript("""
        CREATE TABLE IF NOT EXISTS keys (
            key_hash TEXT PRIMARY KEY,
            key_prefix TEXT NOT NULL DEFAULT 'free',
            tier TEXT NOT NULL DEFAULT 'free',
            expires_at INTEGER NOT NULL,
            max_devices INTEGER DEFAULT 1,
            is_active INTEGER DEFAULT 1,
            created_at INTEGER,
            created_via TEXT DEFAULT 'manual'
        );

        CREATE TABLE IF NOT EXISTS device_bindings (
            key_hash TEXT NOT NULL,
            device_fp TEXT NOT NULL,
            ip_address TEXT DEFAULT '',
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
            ip_address TEXT DEFAULT '',
            action TEXT,
            timestamp INTEGER
        );

        CREATE TABLE IF NOT EXISTS gateway_tasks (
            task_id TEXT PRIMARY KEY,
            key_hash TEXT,
            status TEXT DEFAULT 'pending',
            created_at INTEGER,
            completed_at INTEGER
        );

        CREATE TABLE IF NOT EXISTS rate_limit_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ip_address TEXT,
            endpoint TEXT,
            timestamp INTEGER
        );

        CREATE INDEX IF NOT EXISTS idx_usage_key ON usage_log(key_hash);
        CREATE INDEX IF NOT EXISTS idx_usage_time ON usage_log(timestamp);
        CREATE INDEX IF NOT EXISTS idx_bindings_key ON device_bindings(key_hash);
        CREATE INDEX IF NOT EXISTS idx_keys_tier ON keys(tier);
    """)
    conn.commit()
    conn.close()

init_db()

# ─── Key Generation Helpers ──────────────────────────────
def generate_key(prefix: str = "free") -> str:
    """Generate a Morvo key with tier prefix."""
    hex_part = secrets.token_hex(16)
    return f"morvo-{prefix}-{hex_part}"

def hash_key(raw_key: str) -> str:
    return hashlib.sha256(raw_key.encode()).hexdigest()

def fingerprint_device(device_id: str, hwid: str) -> str:
    return hashlib.sha256(f"{device_id}:{hwid}".encode()).hexdigest()

def fingerprint_ip(ip: str) -> str:
    return hashlib.sha256(ip.encode()).hexdigest() if ip else "unknown"

def store_key(conn, raw_key: str, tier: str, days: int, max_devices: int, via: str = "manual"):
    key_hash = hash_key(raw_key)
    expires = int(time.time()) + (days * 86400)
    now = int(time.time())
    prefix = tier
    conn.execute(
        "INSERT OR REPLACE INTO keys (key_hash, key_prefix, tier, expires_at, max_devices, is_active, created_at, created_via) VALUES (?, ?, ?, ?, ?, 1, ?, ?)",
        (key_hash, prefix, tier, expires, max_devices, now, via)
    )
    conn.commit()
    return {"key": raw_key, "key_hash": key_hash, "tier": tier, "expires_in_days": days}

# ─── Health ──────────────────────────────────────────────
@app.get("/api/v1/health")
async def health():
    conn = get_db()
    c = conn.cursor()
    c.execute("SELECT COUNT(*) FROM keys WHERE is_active = 1")
    active = c.fetchone()[0]
    c.execute("SELECT COUNT(*) FROM scripts")
    scripts = c.fetchone()[0]
    conn.close()
    return {"status": "ok", "version": "0.6.0", "active_keys": active, "scripts": scripts, "timestamp": int(time.time())}

# ═══════════════════════════════════════════════════════════
#  KEY VALIDATION
# ═══════════════════════════════════════════════════════════

@app.post("/api/v1/validate")
async def validate_key(req: KeyValidateRequest, request: Request):
    client_ip = req.client_ip or request.client.host

    # Rate limit
    if not rate_limit(client_ip):
        return {"valid": False, "tier": "none", "expires_at": 0, "message": "Rate limited — try again in 1 minute"}

    conn = get_db()
    c = conn.cursor()

    key_hash = hash_key(req.key)
    device_fp = fingerprint_device(req.device_id, req.hwid)
    now = int(time.time())

    # Check key
    c.execute(
        "SELECT tier, expires_at, max_devices, is_active FROM keys WHERE key_hash = ?",
        (key_hash,)
    )
    row = c.fetchone()

    if not row:
        conn.close()
        return {"valid": False, "tier": "none", "expires_at": 0, "message": "Invalid key — copy the full key including prefix"}

    tier, expires_at, max_devices, is_active = row

    if not is_active:
        conn.close()
        return {"valid": False, "tier": "none", "expires_at": expires_at, "message": "Key revoked"}

    # Premium keys don't expire
    if tier == "premium":
        expires_at = now + (365 * 86400)  # show 1 year for display
        tier_val = "premium"
    elif now > expires_at:
        conn.close()
        return {"valid": False, "tier": tier, "expires_at": expires_at, "message": "Key expired — generate a new key"}

    # Check device limit
    c.execute("SELECT COUNT(*) FROM device_bindings WHERE key_hash = ?", (key_hash,))
    device_count = c.fetchone()[0]

    c.execute("SELECT 1 FROM device_bindings WHERE key_hash = ? AND device_fp = ?", (key_hash, device_fp))
    is_bound = c.fetchone()

    if not is_bound and device_count >= max_devices:
        conn.close()
        return {"valid": False, "tier": tier, "expires_at": expires_at, "message": f"Device limit reached ({max_devices}) — upgrade to Premium for 3 devices"}

    # Bind/update device
    c.execute(
        "INSERT OR REPLACE INTO device_bindings (key_hash, device_fp, ip_address, last_seen) VALUES (?, ?, ?, ?)",
        (key_hash, device_fp, client_ip, now)
    )

    # Log
    c.execute(
        "INSERT INTO usage_log (key_hash, device_fp, ip_address, action, timestamp) VALUES (?, ?, ?, 'validate', ?)",
        (key_hash, device_fp, client_ip, now)
    )

    conn.commit()
    conn.close()

    return {"valid": True, "tier": tier, "expires_at": expires_at, "max_devices": max_devices, "message": "OK"}

# ═══════════════════════════════════════════════════════════
#  LOOT-LINK GATEWAY INTEGRATION
# ═══════════════════════════════════════════════════════════

@app.get("/api/v1/gateway/redirect")
async def gateway_redirect(tier: str = "free"):
    """Redirect user to Loot-Link gateway. App should open this URL."""
    gateway_url = LOOTLINK_GATEWAY_URL.replace("{site_id}", LOOTLINK_SITE_ID)
    redirect_url = f"{gateway_url}?tier={tier}&callback={MORVO_DOMAIN}/key.html"
    return RedirectResponse(url=redirect_url)

@app.post("/api/v1/gateway/callback")
async def gateway_webhook(payload: GatewayCallbackPayload, request: Request):
    """Loot-Link webhook: receives task completion notification and generates key."""
    # Verify webhook signature (if Loot-Link provides HMAC)
    # sig = request.headers.get("X-LootLink-Signature", "")
    # expected = hmac.new(LOOTLINK_WEBHOOK_SECRET.encode(), request.body(), hashlib.sha256).hexdigest()
    # if not hmac.compare_digest(sig, expected):
    #     raise HTTPException(status_code=403, detail="Invalid webhook signature")

    conn = get_db()
    c = conn.cursor()

    task_id = payload.task_id
    user_id = payload.user_id
    event = payload.event

    # Map reward to tier
    tier_map = {"key_free": "free", "key_premium": "premium", "key_dev": "dev"}
    tier = tier_map.get(payload.reward, "free")
    days = 30 if tier == "premium" else 1
    max_dev = 3 if tier == "premium" else 1

    # Check for duplicate task
    c.execute("SELECT key_hash FROM gateway_tasks WHERE task_id = ?", (task_id,))
    existing = c.fetchone()
    if existing:
        conn.close()
        return {"status": "duplicate", "task_id": task_id, "message": "This task was already processed"}

    # Generate key
    raw_key = generate_key(tier)
    key_hash = hash_key(raw_key)
    now = int(time.time())

    # Store key
    c.execute(
        "INSERT INTO keys (key_hash, key_prefix, tier, expires_at, max_devices, is_active, created_at, created_via) VALUES (?, ?, ?, ?, ?, 1, ?, 'gateway')",
        (key_hash, tier, tier, now + (days * 86400), max_dev, now)
    )

    # Log gateway task
    c.execute(
        "INSERT INTO gateway_tasks (task_id, key_hash, status, created_at, completed_at) VALUES (?, ?, 'completed', ?, ?)",
        (task_id, key_hash, now, now)
    )

    # Log
    c.execute(
        "INSERT INTO usage_log (key_hash, device_fp, ip_address, action, timestamp) VALUES (?, ?, ?, 'gateway_generate', ?)",
        (key_hash, user_id, payload.metadata.get("ip", ""), now)
    )

    conn.commit()
    conn.close()

    return {
        "status": "success",
        "task_id": task_id,
        "key": raw_key,
        "tier": tier,
        "expires_in_hours": days * 24,
        "message": f"Your {'premium' if tier == 'premium' else 'free'} key has been generated! Copy it and paste in Morvo."
    }

@app.get("/api/v1/gateway/status/{task_id}")
async def gateway_task_status(task_id: str):
    """Check if a gateway task has been processed."""
    conn = get_db()
    c = conn.cursor()
    c.execute("SELECT status, key_hash FROM gateway_tasks WHERE task_id = ?", (task_id,))
    row = c.fetchone()
    conn.close()

    if not row:
        return {"status": "not_found", "task_id": task_id, "message": "Task not found"}
    
    return {"status": row["status"], "task_id": task_id, "key_available": row["status"] == "completed"}

# ═══════════════════════════════════════════════════════════
#  KEY MANAGEMENT
# ═══════════════════════════════════════════════════════════

@app.get("/api/v1/key/status")
async def key_status(key: str):
    """Check key status without validating device."""
    key_hash = hash_key(key)
    conn = get_db()
    c = conn.cursor()
    c.execute("SELECT tier, expires_at, max_devices, is_active, created_at FROM keys WHERE key_hash = ?", (key_hash,))
    row = c.fetchone()

    if not row:
        conn.close()
        return {"status": "invalid", "message": "Key not found"}

    # Check device count
    c.execute("SELECT COUNT(*) FROM device_bindings WHERE key_hash = ?", (key_hash,))
    device_count = c.fetchone()[0]
    conn.close()

    now = int(time.time())
    return {
        "status": "active" if (row["is_active"] and row["tier"] == "premium" or now <= row["expires_at"]) else "expired",
        "tier": row["tier"],
        "expires_at": row["expires_at"],
        "expires_in_minutes": max(0, (row["expires_at"] - now) // 60),
        "max_devices": row["max_devices"],
        "devices_used": device_count,
        "created_at": row["created_at"]
    }

# ═══════════════════════════════════════════════════════════
#  SCRIPTS API
# ═══════════════════════════════════════════════════════════

@app.get("/api/v1/scripts")
async def get_scripts(game_id: str = "", tier: str = "free"):
    conn = get_db()
    c = conn.cursor()

    tier_order = {"free": 0, "premium": 1, "dev": 2}
    min_tier_val = tier_order.get(tier, 0)

    if game_id:
        c.execute(
            "SELECT name, game_id, url, min_tier, downloads FROM scripts WHERE game_id = ? ORDER BY downloads DESC",
            (game_id,)
        )
    else:
        c.execute(
            "SELECT name, game_id, url, min_tier, downloads FROM scripts ORDER BY downloads DESC"
        )

    scripts = [
        {
            "name": r["name"], "game_id": r["game_id"], "url": r["url"],
            "min_tier": r["min_tier"], "downloads": r["downloads"],
            "unlocked": tier_order.get(r["min_tier"], 0) <= min_tier_val
        }
        for r in c.fetchall()
    ]
    conn.close()
    return {"scripts": scripts, "count": len(scripts)}

# ═══════════════════════════════════════════════════════════
#  ADMIN ENDPOINTS
# ═══════════════════════════════════════════════════════════

@app.post("/api/v1/admin/keys")
async def admin_generate_key(req: KeyGenerateRequest):
    """Generate a single key (manual/admin)."""
    raw_key = generate_key(req.tier)
    conn = get_db()
    result = store_key(conn, raw_key, req.tier, req.days, req.max_devices, "manual_admin")
    conn.close()
    return result

@app.post("/api/v1/admin/keys/bulk")
async def admin_bulk_keys(req: KeyBulkRequest):
    """Bulk generate keys."""
    conn = get_db()
    keys = []
    for _ in range(req.count):
        raw_key = generate_key(req.tier)
        result = store_key(conn, raw_key, req.tier, req.days, 1, "bulk_admin")
        keys.append(raw_key)
    conn.close()
    return {"count": req.count, "keys": keys, "tier": req.tier}

@app.post("/api/v1/admin/keys/revoke")
async def admin_revoke_key(key: str):
    """Revoke a key."""
    key_hash = hash_key(key)
    conn = get_db()
    c = conn.cursor()
    c.execute("UPDATE keys SET is_active = 0 WHERE key_hash = ?", (key_hash,))
    if c.rowcount == 0:
        conn.close()
        raise HTTPException(status_code=404, detail="Key not found")
    conn.commit()
    conn.close()
    return {"status": "revoked", "key_hash": key_hash[:16] + "..."}

@app.get("/api/v1/admin/keys")
async def admin_list_keys(tier: str = "", limit: int = 50, offset: int = 0):
    """List active keys."""
    conn = get_db()
    c = conn.cursor()
    if tier:
        c.execute(
            "SELECT key_hash, key_prefix, tier, expires_at, max_devices, is_active, created_at FROM keys WHERE tier = ? AND is_active = 1 ORDER BY created_at DESC LIMIT ? OFFSET ?",
            (tier, limit, offset)
        )
    else:
        c.execute(
            "SELECT key_hash, key_prefix, tier, expires_at, max_devices, is_active, created_at FROM keys WHERE is_active = 1 ORDER BY created_at DESC LIMIT ? OFFSET ?",
            (limit, offset)
        )
    keys = [
        {"hash": r["key_hash"][:16]+"...", "tier": r["tier"], "expires_at": r["expires_at"],
         "devices": r["max_devices"], "created_at": r["created_at"]}
        for r in c.fetchall()
    ]
    conn.close()
    return {"keys": keys, "count": len(keys)}

@app.get("/api/v1/admin/stats")
async def admin_stats():
    """Usage statistics."""
    conn = get_db()
    c = conn.cursor()

    c.execute("SELECT COUNT(*) FROM keys WHERE is_active = 1 AND tier = 'free'")
    free = c.fetchone()[0]
    c.execute("SELECT COUNT(*) FROM keys WHERE is_active = 1 AND tier = 'premium'")
    premium = c.fetchone()[0]

    now = int(time.time())
    day_ago = now - 86400

    c.execute("SELECT COUNT(*) FROM usage_log WHERE timestamp > ?", (day_ago,))
    validations = c.fetchone()[0]

    c.execute("SELECT COUNT(DISTINCT device_fp) FROM usage_log WHERE timestamp > ? AND action = 'validate'", (day_ago,))
    devices = c.fetchone()[0]

    c.execute("SELECT COUNT(*) FROM gateway_tasks WHERE completed_at > ?", (day_ago,))
    gateway = c.fetchone()[0]

    conn.close()
    return {
        "active_keys": free + premium,
        "free_keys": free,
        "premium_keys": premium,
        "total_validations_24h": validations,
        "unique_devices_24h": devices,
        "gateway_completions_24h": gateway
    }

# ═══════════════════════════════════════════════════════════
#  PREMIUM MANAGEMENT (Stripe)
# ═══════════════════════════════════════════════════════════

STRIPE_SECRET_KEY = os.getenv("STRIPE_SECRET_KEY", "")
STRIPE_WEBHOOK_SECRET = os.getenv("STRIPE_WEBHOOK_SECRET", "")
STRIPE_PRICE_ID = os.getenv("STRIPE_PRICE_ID", "price_morvo_premium_monthly")
STRIPE_ENABLED = bool(STRIPE_SECRET_KEY)

import stripe as stripe_lib
if STRIPE_ENABLED:
    stripe_lib.api_key = STRIPE_SECRET_KEY

class StripeCheckoutRequest(BaseModel):
    email: str = ""
    success_url: str = ""
    cancel_url: str = ""

class StripeWebhookPayload(BaseModel):
    type: str
    data: dict = {}

@app.post("/api/v1/premium/checkout")
async def premium_checkout(req: StripeCheckoutRequest):
    """Create Stripe checkout session for Premium subscription."""
    if not STRIPE_ENABLED:
        raise HTTPException(status_code=503, detail="Payment gateway not configured")

    try:
        session = stripe_lib.checkout.Session.create(
            payment_method_types=["card"],
            mode="subscription",
            line_items=[{"price": STRIPE_PRICE_ID, "quantity": 1}],
            customer_email=req.email or None,
            success_url=req.success_url or f"{MORVO_DOMAIN}/premium.html?session_id={{CHECKOUT_SESSION_ID}}",
            cancel_url=req.cancel_url or f"{MORVO_DOMAIN}/premium.html",
            metadata={"source": "morvo_premium"}
        )
        return {"checkout_url": session.url, "session_id": session.id}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Stripe error: {str(e)}")

@app.post("/api/v1/premium/webhook")
async def premium_webhook(request: Request):
    """Stripe webhook — generates premium key on successful payment."""
    if not STRIPE_ENABLED:
        raise HTTPException(status_code=503, detail="Not configured")

    payload = await request.body()
    sig_header = request.headers.get("stripe-signature", "")

    try:
        event = stripe_lib.Webhook.construct_event(payload, sig_header, STRIPE_WEBHOOK_SECRET)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid webhook signature")

    # Handle checkout.session.completed
    if event["type"] == "checkout.session.completed":
        session = event["data"]["object"]
        customer_email = session.get("customer_email", session.get("customer_details", {}).get("email", ""))
        payment_ref = session.get("id", "")

        raw_key = generate_key("prem")
        conn = get_db()
        result = store_key(conn, raw_key, "premium", 30, 3, f"stripe_{payment_ref}")
        conn.close()

        return {
            "status": "success",
            "premium_key": raw_key,
            "email": customer_email,
            "message": "Premium key generated! Key sent to your email."
        }

    return {"status": "ignored", "event": event["type"]}

@app.post("/api/v1/premium/subscribe")
async def premium_subscribe_manual(payment_ref: str = "", email: str = ""):
    """
    Manual premium key creation (admin/fallback).
    For production: use /api/v1/premium/checkout + webhook instead.
    """
    raw_key = generate_key("prem")
    conn = get_db()
    result = store_key(conn, raw_key, "premium", 30, 3, f"manual_{payment_ref}")
    conn.close()
    return {
        "premium_key": raw_key,
        "tier": "premium",
        "duration_days": 30,
        "max_devices": 3,
        "message": "Premium key created. Enter this once in Morvo — it never expires while subscription is active."
    }

# ═══════════════════════════════════════════════════════════

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
