#!/usr/bin/env python3
# tools/dump_roblox.py — Auto-download Roblox APK + extract libroblox.so
# Usage: python3 dump_roblox.py [--version VERSION] [--arch arm64|armv7]

import os, sys, re, json, subprocess, hashlib, argparse, tempfile
from urllib.request import urlopen, Request
from pathlib import Path

ARCH = "arm64-v8a"
OUT_DIR = Path(__file__).parent.parent / "libs"
APK_MIRROR_BASE = "https://apkpure.com/roblox/com.roblox.client"

# ============================================================
# Step 1: Get latest version
# ============================================================
def get_latest_version():
    """Scrape APKPure/APKMirror for latest Roblox version"""
    # APKPure API
    url = f"https://apkpure.com/roblox/com.roblox.client/versions"
    req = Request(url, headers={"User-Agent": "Mozilla/5.0 (Linux; Android 13)"})
    try:
        html = urlopen(req, timeout=15).read().decode()
        # Extract version from HTML
        versions = re.findall(r'data-dt-version="(\d+\.\d+\.\d+)"', html)
        if versions:
            return versions[0]
    except Exception as e:
        print(f"  [!] APKPure scrape failed: {e}")
    
    # Fallback: APKMirror
    print("  [*] Trying APKMirror...")
    url = "https://www.apkmirror.com/apk/roblox-corporation/roblox/"
    try:
        html = urlopen(Request(url, headers={"User-Agent": "Mozilla/5.0"}), timeout=15).read().decode()
        versions = re.findall(r'roblox-(\d+-\d+-\d+)-android-apk-download', html)
        if versions:
            return versions[0].replace('-', '.')
    except:
        pass
    
    return None

# ============================================================
# Step 2: Download APK
# ============================================================
def download_apk(version, out_path):
    """Download APK from APKPure"""
    # APKPure direct download
    url = f"https://d.apkpure.com/b/APK/com.roblox.client?version={version}"
    
    print(f"  [*] Downloading Roblox v{version}...")
    req = Request(url, headers={
        "User-Agent": "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36",
        "Accept": "application/vnd.android.package-archive"
    })
    
    try:
        resp = urlopen(req, timeout=300)
        total = int(resp.headers.get('content-length', 0))
        downloaded = 0
        
        with open(out_path, 'wb') as f:
            while True:
                chunk = resp.read(8192)
                if not chunk: break
                f.write(chunk)
                downloaded += len(chunk)
                if total:
                    pct = (downloaded / total) * 100
                    print(f"\r    {downloaded//1024//1024}MB / {total//1024//1024}MB ({pct:.0f}%)", end='')
        print()
        return True
    except Exception as e:
        print(f"  [!] Download failed: {e}")
        return False

# ============================================================
# Step 3: Extract libroblox.so
# ============================================================
def extract_lib(apk_path, arch=ARCH):
    """Extract libroblox.so from APK"""
    import zipfile
    
    print(f"  [*] Extracting lib/{arch}/libroblox.so...")
    
    with zipfile.ZipFile(apk_path, 'r') as z:
        # Check for split APK structure
        target = f"lib/{arch}/libroblox.so"
        
        # List all .so files
        libs = [f for f in z.namelist() if f.endswith('.so')]
        print(f"  [*] Found {len(libs)} .so files in APK")
        
        if target in z.namelist():
            out = OUT_DIR / arch / "libroblox.so"
            out.parent.mkdir(parents=True, exist_ok=True)
            z.extract(target, OUT_DIR / "extracted")
            
            # Move to correct path
            extracted = OUT_DIR / "extracted" / target
            extracted.rename(out)
            
            # Cleanup extracted dir
            import shutil
            shutil.rmtree(OUT_DIR / "extracted", ignore_errors=True)
            
            size = out.stat().st_size
            print(f"  [+] Extracted: {out} ({size//1024//1024}MB)")
            return out
        else:
            # Check for split APK (config.arm64_v8a)
            for f in z.namelist():
                if 'arm64' in f.lower() and f.endswith('.apk'):
                    print(f"  [*] Found split APK: {f} — extracting to temp")
                    # Extract nested APK
                    tmp = tempfile.NamedTemporaryFile(suffix='.apk', delete=False)
                    tmp.write(z.read(f))
                    tmp.close()
                    result = extract_lib(tmp.name, arch)
                    os.unlink(tmp.name)
                    return result
            
            print(f"  [!] {target} not found in APK")
            print(f"  Available libs: {libs[:10]}")
            return None

# ============================================================
# Step 4: Verify
# ============================================================
def verify_lib(lib_path):
    """Verify the .so file is valid ARM64 ELF"""
    if not lib_path or not lib_path.exists():
        return False
    
    try:
        result = subprocess.run(
            ['file', str(lib_path)],
            capture_output=True, text=True, timeout=5
        )
        output = result.stdout
        print(f"  [*] File: {output.strip()}")
        
        if 'ELF' in output and 'ARM' in output:
            print(f"  [+] Valid ARM64 ELF binary")
            
            # Check symbols
            result = subprocess.run(
                ['nm', '-D', str(lib_path)],
                capture_output=True, text=True, timeout=30
            )
            symbols = result.stdout
            lua_count = symbols.count('lua')
            print(f"  [*] Lua symbols: {lua_count}")
            
            # Check for key functions
            for sym in ['luaL_loadbuffer', 'lua_pcall', 'lua_newstate']:
                if sym in symbols:
                    print(f"  [+] Found: {sym}")
            
            return True
    except Exception as e:
        print(f"  [!] Verification failed: {e}")
    
    return False

# ============================================================
# Main
# ============================================================
def main():
    parser = argparse.ArgumentParser(description="Auto-dump Roblox libroblox.so")
    parser.add_argument('--version', help='Specific version (e.g. 2.600.500)')
    parser.add_argument('--arch', default='arm64-v8a', help='Target architecture')
    parser.add_argument('--apk', help='Use local APK file instead of downloading')
    args = parser.parse_args()
    
    print("=" * 50)
    print("Morvo — Auto libroblox.so Dumper")
    print("=" * 50)
    
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    
    if args.apk:
        apk_path = Path(args.apk)
        if not apk_path.exists():
            print(f"[!] APK not found: {args.apk}")
            sys.exit(1)
    else:
        version = args.version or get_latest_version()
        if not version:
            print("[!] Could not determine Roblox version")
            print("[*] Try: python3 dump_roblox.py --version 2.600.500")
            sys.exit(1)
        
        print(f"[+] Latest Roblox: v{version}")
        apk_path = OUT_DIR / f"roblox-{version}.apk"
        
        if apk_path.exists():
            print(f"[*] APK already downloaded: {apk_path}")
        else:
            if not download_apk(version, apk_path):
                sys.exit(1)
    
    # Extract
    lib_path = extract_lib(apk_path, args.arch)
    
    if lib_path and verify_lib(lib_path):
        print(f"\n[+] SUCCESS: {lib_path}")
        print(f"[*] Ready for offset scanning")
        print(f"\nNext: python3 tools/find_offsets.py {lib_path}")
    else:
        print("\n[!] Failed to extract libroblox.so")
        sys.exit(1)

if __name__ == "__main__":
    main()