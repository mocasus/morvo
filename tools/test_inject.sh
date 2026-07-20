#!/usr/bin/env bash
# tools/test_inject.sh — ADB-based automated injection testing
# Usage: ./test_inject.sh [device_serial]

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[+]${NC} $1"; }
warn() { echo -e "${YELLOW}[*]${NC} $1"; }
err()  { echo -e "${RED}[!]${NC} $1"; }

# ============================================================
# Setup
# ============================================================
DEVICE=${1:-""}
ADB="adb"
[ -n "$DEVICE" ] && ADB="adb -s $DEVICE"

log "Morvo — Automated Injection Test"

# Check ADB
if ! command -v adb &>/dev/null; then
    err "ADB not found. Install Android SDK Platform Tools."
    exit 1
fi

# Check device
DEVICES=$($ADB devices | grep -v "List" | grep "device$" | wc -l)
if [ "$DEVICES" -eq 0 ]; then
    err "No device connected. Connect Android device with USB debugging."
    exit 1
fi
log "Device connected: $DEVICES device(s)"

# Check root
ROOT=$($ADB shell "su -c 'id'" 2>/dev/null | grep "uid=0")
if [ -z "$ROOT" ]; then
    err "Root required. Use rooted device or emulator (LDPlayer recommended)."
    exit 1
fi
log "Root access: OK"

# ============================================================
# Build native library
# ============================================================
log "Building libmorvo.so..."

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
NATIVE_DIR="$PROJECT_DIR/native"

# Find NDK
NDK=""
for path in ~/Android/Sdk/ndk/* /usr/local/lib/android/sdk/ndk/* $ANDROID_NDK_HOME; do
    [ -d "$path" ] && NDK="$path" && break
done

if [ -z "$NDK" ]; then
    warn "NDK not found. Building with system clang (may fail)..."
    CLANG="aarch64-linux-gnu-gcc"
else
    CLANG="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android33-clang"
    [ ! -f "$CLANG" ] && CLANG="$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin/aarch64-linux-android33-clang"
fi

if ! command -v "$CLANG" &>/dev/null && [ ! -f "$CLANG" ]; then
    err "Cannot find ARM64 cross-compiler. Install NDK or aarch64-linux-gnu-gcc."
    exit 1
fi

# Compile
BUILD_DIR="$PROJECT_DIR/build"
mkdir -p "$BUILD_DIR"

SOURCES=(
    native/src/main.c
    native/src/injector/ptrace_attach.c
    native/src/injector/mem_scan.c
    native/src/injector/proc_maps.c
    native/src/luau/luastate.c
    native/src/luau/execute.c
    native/src/luau/identity.c
    native/src/hook/arm64_hook.c
    native/src/bypass/hyperion.c
    native/src/bypass/integrity.c
)

log "Compiling..."
$CLANG -shared -fPIC \
    -o "$BUILD_DIR/libmorvo.so" \
    $(printf "$PROJECT_DIR/%s " "${SOURCES[@]}") \
    -I "$PROJECT_DIR/native/include" \
    -llog -ldl \
    -static-libstdc++ \
    -Wall -O2 2>&1 | tail -5

if [ ! -f "$BUILD_DIR/libmorvo.so" ]; then
    err "Build failed."
    exit 1
fi

SIZE=$(stat -f%z "$BUILD_DIR/libmorvo.so" 2>/dev/null || stat -c%s "$BUILD_DIR/libmorvo.so")
log "Built: libmorvo.so ($((SIZE/1024))KB)"

# ============================================================
# Push to device
# ============================================================
log "Pushing to device..."
$ADB push "$BUILD_DIR/libmorvo.so" /data/local/tmp/libmorvo.so
$ADB shell "su -c 'chmod 755 /data/local/tmp/libmorvo.so'"
log "Pushed: /data/local/tmp/libmorvo.so"

# ============================================================
# Test 1: Find Roblox process
# ============================================================
echo ""
log "=== Test 1: Find Roblox Process ==="

ROBLOX_PID=$($ADB shell "su -c 'pidof com.roblox.client'" 2>/dev/null | tr -d '\r')

if [ -z "$ROBLOX_PID" ]; then
    warn "Roblox not running. Launching..."
    $ADB shell "am start -n com.roblox.client/.ActivityProtocolLaunch" 2>/dev/null
    sleep 5
    ROBLOX_PID=$($ADB shell "su -c 'pidof com.roblox.client'" 2>/dev/null | tr -d '\r')
fi

if [ -n "$ROBLOX_PID" ]; then
    log "Roblox PID: $ROBLOX_PID"
else
    err "Roblox not found. Install Roblox from Play Store."
    exit 1
fi

# ============================================================
# Test 2: Read memory maps
# ============================================================
echo ""
log "=== Test 2: Memory Maps ==="

$ADB shell "su -c 'cat /proc/$ROBLOX_PID/maps'" > "$BUILD_DIR/roblox_maps.txt"

# Find libroblox.so
LIBROBLOX=$(grep "libroblox.so" "$BUILD_DIR/roblox_maps.txt" | head -1)
if [ -n "$LIBROBLOX" ]; then
    BASE=$(echo "$LIBROBLOX" | awk '{print $1}' | cut -d'-' -f1)
    log "libroblox.so base: 0x$BASE"
else
    warn "libroblox.so not found in maps — may be split/bundled"
    # Try to find RobloxPlayerBeta
    LIBROBLOX=$(grep -iE "roblox|lua" "$BUILD_DIR/roblox_maps.txt" | head -1)
    if [ -n "$LIBROBLOX" ]; then
        log "Found related library: $(echo $LIBROBLOX | awk '{print $NF}')"
    fi
fi

# ============================================================
# Test 3: Check TracerPid (Hyperion detect)
# ============================================================
echo ""
log "=== Test 3: TracerPid Check ==="

TRACER=$($ADB shell "su -c 'cat /proc/$ROBLOX_PID/status | grep TracerPid'" 2>/dev/null | awk '{print $2}')
if [ "$TRACER" = "0" ]; then
    log "TracerPid: 0 (no tracer detected)"
else
    warn "TracerPid: $TRACER (may indicate Hyperion monitoring)"
fi

# ============================================================
# Test 4: Check loaded modules
# ============================================================
echo ""
log "=== Test 4: Loaded Modules ==="

$ADB shell "su -c 'cat /proc/$ROBLOX_PID/maps'" | awk '{print $NF}' | \
    grep "\.so$" | sort -u | while read lib; do
    echo "  $lib"
done

# Check for Hyperion
if $ADB shell "su -c 'cat /proc/$ROBLOX_PID/maps'" | grep -qi hyperion; then
    warn "Hyperion detected in memory!"
    $ADB shell "su -c 'cat /proc/$ROBLOX_PID/maps'" | grep -i hyperion
else
    log "No Hyperion strings in memory maps"
fi

# Check for tamper protection
if $ADB shell "su -c 'cat /proc/$ROBLOX_PID/maps'" | grep -qi tamper; then
    warn "Tamper protection detected!"
    $ADB shell "su -c 'cat /proc/$ROBLOX_PID/maps'" | grep -i tamper
else
    log "No tamper protection in memory maps"
fi

# ============================================================
# Test 5: LD_PRELOAD injection
# ============================================================
echo ""
log "=== Test 5: LD_PRELOAD Injection ==="

$ADB shell "su -c 'kill $ROBLOX_PID'" 2>/dev/null
sleep 2

log "Launching Roblox with LD_PRELOAD..."
$ADB shell "su -c 'LD_PRELOAD=/data/local/tmp/libmorvo.so am start -n com.roblox.client/.ActivityProtocolLaunch'" 2>/dev/null

sleep 3
NEW_PID=$($ADB shell "su -c 'pidof com.roblox.client'" 2>/dev/null | tr -d '\r')

if [ -n "$NEW_PID" ]; then
    # Check if our library is loaded
    OUR_LIB=$($ADB shell "su -c 'cat /proc/$NEW_PID/maps'" | grep "libmorvo" | head -1)
    if [ -n "$OUR_LIB" ]; then
        log "libmorvo.so LOADED in Roblox process!"
        log "Address: $OUR_LIB"
    else
        warn "libmorvo.so not found in maps — check LD_PRELOAD compatibility"
    fi
    
    # Monitor logs
    log "Checking Morvo logs..."
    $ADB logcat -d -s Morvo:* | tail -20
else
    err "Roblox failed to launch with LD_PRELOAD"
fi

# ============================================================
# Summary
# ============================================================
echo ""
echo "========================================"
log "Tests Complete"
echo "========================================"

# Save results
cat > "$BUILD_DIR/test_results.json" << EOF
{
    "timestamp": "$(date -Iseconds)",
    "device": "$($ADB shell getprop ro.product.model | tr -d '\r')",
    "android": "$($ADB shell getprop ro.build.version.release | tr -d '\r')",
    "roblox_pid": "$ROBLOX_PID",
    "tracer_pid": "$TRACER",
    "libroblox_base": "0x${BASE:-unknown}",
    "libmorvo_loaded": $([ -n "$OUR_LIB" ] && echo "true" || echo "false"),
    "hyperion_detected": $($ADB shell "su -c 'cat /proc/${NEW_PID:-0}/maps'" 2>/dev/null | grep -qi hyperion && echo "true" || echo "false"),
    "tamper_detected": $($ADB shell "su -c 'cat /proc/${NEW_PID:-0}/maps'" 2>/dev/null | grep -qi tamper && echo "true" || echo "false")
}
EOF

log "Results saved: $BUILD_DIR/test_results.json"
log "Maps saved: $BUILD_DIR/roblox_maps.txt"