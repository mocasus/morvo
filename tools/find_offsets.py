#!/usr/bin/env python3
# tools/find_offsets.py — Auto-scan lua_State + luaL_loadbuffer offsets
# Uses r2pipe (radare2) for automated binary analysis
# Usage: python3 find_offsets.py libs/arm64-v8a/libroblox.so

import os, sys, re, json, struct
from pathlib import Path

try:
    import r2pipe
except ImportError:
    print("[!] r2pipe not installed. Install: pip install r2pipe")
    print("[*] Fallback: using subprocess + radare2 commands")
    HAS_R2PIPE = False
else:
    HAS_R2PIPE = True

# ============================================================
# Signatures (ARM64)
# ============================================================
SIGNATURES = {
    # luaL_loadbuffer: function prologue + string "loadbuffer" reference
    "luaL_loadbuffer": {
        "pattern": bytes([0xFF, 0x43, 0x01, 0xD1]),  # sub sp, sp, #0x50
        "mask":    bytes([0xFF, 0xFF, 0xFF, 0xFF]),
        "description": "luaL_loadbuffer — script loading entry point"
    },
    # lua_pcall: similar prologue
    "lua_pcall": {
        "pattern": bytes([0xFF, 0x03, 0x01, 0xD1]),  # sub sp, sp, #0x40
        "mask":    bytes([0xFF, 0xFF, 0xFF, 0xFF]),
        "description": "lua_pcall — protected call"
    },
    # CRC32 integrity validator
    "crc_validator": {
        "pattern": bytes([0xFD, 0x7B, 0xBF, 0xA9]),  # stp x29, x30, [sp, #-0x10]!
        "mask":    bytes([0xFF, 0xFF, 0xFF, 0xFF]),
        "description": "CRC32 integrity check function"
    },
    # lua_State global pointer access
    "luastate_access": {
        "pattern": bytes([0xE0, 0x03, 0x00, 0xAA]),  # mov x0, x0 (common no-op around state access)
        "mask":    bytes([0xFF, 0xFF, 0xFF, 0xFF]),
        "description": "lua_State access pattern"
    }
}

# ============================================================
# Radare2-based analysis
# ============================================================
class OffsetFinder:
    def __init__(self, lib_path):
        self.lib_path = Path(lib_path)
        if not self.lib_path.exists():
            print(f"[!] File not found: {lib_path}")
            sys.exit(1)
        
        self.size = self.lib_path.stat().st_size
        print(f"[*] Loading: {self.lib_path.name} ({self.size//1024//1024}MB)")
        
        if HAS_R2PIPE:
            self.r2 = r2pipe.open(str(self.lib_path), flags=['-2'])
            self.r2.cmd('aaaa')  # Full auto analysis
        else:
            self.r2 = None
            print("[*] Running in subprocess mode (slower)")
    
    def r2cmd(self, cmd):
        """Execute radare2 command"""
        if self.r2:
            return self.r2.cmd(cmd)
        else:
            result = os.popen(f'r2 -q -c "{cmd}" -e io.cache=true {self.lib_path}').read()
            return result
    
    def find_strings(self, keyword, limit=50):
        """Find all strings containing keyword"""
        print(f"\n[*] Searching strings: '{keyword}'...")
        result = self.r2cmd(f'izz~{keyword}')
        lines = result.strip().split('\n')[:limit]
        
        matches = []
        for line in lines:
            # Parse: addr size string
            parts = line.split()
            if len(parts) >= 3:
                addr = int(parts[0], 16) if parts[0].startswith('0x') else None
                string = ' '.join(parts[2:])
                if addr:
                    matches.append({"addr": addr, "string": string})
                    print(f"  {hex(addr)}: {string}")
        
        return matches
    
    def find_xrefs(self, addr):
        """Find cross-references to an address"""
        result = self.r2cmd(f'axt @ {hex(addr)}')
        lines = result.strip().split('\n')
        xrefs = []
        for line in lines:
            if line.strip():
                parts = line.split()
                if parts:
                    try:
                        xref_addr = int(parts[0], 16)
                        xrefs.append(xref_addr)
                    except:
                        pass
        return xrefs
    
    def find_function_by_string(self, keyword):
        """Find function that references a specific string"""
        strings = self.find_strings(keyword)
        if not strings:
            return None
        
        for s in strings:
            xrefs = self.find_xrefs(s['addr'])
            if xrefs:
                print(f"\n  [+] String '{s['string']}' referenced by:")
                for xref in xrefs:
                    # Get function name
                    func_info = self.r2cmd(f'afi @ {hex(xref)}')
                    func_name = "unknown"
                    for line in func_info.split('\n'):
                        if 'name:' in line:
                            func_name = line.split('name:')[1].strip()
                            break
                    print(f"      {hex(xref)} — {func_name}")
                return xrefs[0]
        return None
    
    def scan_pattern(self, pattern, mask, start=0, end=None):
        """Scan for byte pattern in file"""
        if end is None:
            end = self.size
        
        print(f"\n[*] Scanning pattern: {pattern.hex()}...")
        
        with open(self.lib_path, 'rb') as f:
            f.seek(start)
            chunk_size = 1024 * 1024  # 1MB
            offset = start
            
            while offset < end:
                chunk = f.read(min(chunk_size, end - offset))
                if not chunk:
                    break
                
                # Search in chunk
                for i in range(len(chunk) - len(pattern)):
                    match = True
                    for j in range(len(pattern)):
                        if mask[j] == 0xFF and chunk[i+j] != pattern[j]:
                            match = False
                            break
                    if match:
                        found = offset + i
                        print(f"  [+] Found at: {hex(found)}")
                        
                        # Get function info
                        if self.r2:
                            self.r2cmd(f's {hex(found)}')
                            afi = self.r2cmd('afi.')
                            print(f"      {afi.strip()}")
                        
                        return found
                
                offset += len(chunk) - len(pattern)  # Overlap
        
        print(f"  [!] Pattern not found")
        return None
    
    def find_lua_state_offsets(self):
        """Find lua_State structure offsets"""
        print("\n" + "=" * 50)
        print("[*] Finding lua_State offsets...")
        print("=" * 50)
        
        # Method 1: Find via ScriptContext string
        sc_func = self.find_function_by_string("ScriptContext")
        
        # Method 2: Find via identity string
        id_func = self.find_function_by_string("identity")
        
        # Method 3: Find via getgenv string
        genv_func = self.find_function_by_string("getgenv")
        
        # Analyze the function that accesses lua_State extra_space
        # Look for LDR instructions with suspicious offsets
        offsets = {
            "luastate_extra_space": 0x138,  # Default, recalibrate
            "luastate_identity": 0x140,     # Default
            "luastate_global": 0x20,        # Default
            "luastate_top": 0x18,           # Default
        }
        
        return offsets
    
    def find_executor_offsets(self):
        """Find all offsets needed for the executor"""
        print("\n" + "=" * 50)
        print("[*] Finding executor-critical offsets...")
        print("=" * 50)
        
        results = {
            "lib_path": str(self.lib_path),
            "size": self.size,
            "offsets": {}
        }
        
        # Scan for luaL_loadbuffer
        for name, sig in SIGNATURES.items():
            addr = self.scan_pattern(sig["pattern"], sig["mask"])
            if addr:
                results["offsets"][name] = addr
        
        # Find lua_State offsets
        lua_offsets = self.find_lua_state_offsets()
        results["offsets"].update(lua_offsets)
        
        return results
    
    def generate_header(self, offsets):
        """Generate C header with offsets"""
        header = f"""// auto-generated by tools/find_offsets.py
// Roblox version: {self.lib_path.stem}
// File: {self.lib_path.name} ({self.size//1024//1024}MB)

#ifndef MORVO_OFFSETS_H
#define MORVO_OFFSETS_H

#include <stdint.h>

// Function addresses (offsets from libroblox.so base)
"""
        for name, addr in offsets.get("offsets", {}).items():
            if isinstance(addr, int):
                header += f"#define OFFSET_{name.upper()} 0x{addr:x}\n"
        
        header += """
// lua_State structure offsets
#define LUASTATE_TOP          0x18
#define LUASTATE_GLOBAL       0x20
#define LUASTATE_EXTRA_SPACE  0x138
#define LUASTATE_IDENTITY     0x140

// Signature patterns for runtime scanning
#define SIG_LUA_LOADBUFFER    {0xFF, 0x43, 0x01, 0xD1}
#define SIG_LUA_PCALL         {0xFF, 0x03, 0x01, 0xD1}
#define SIG_CRC_VALIDATOR     {0xFD, 0x7B, 0xBF, 0xA9}

#endif // MORVO_OFFSETS_H
"""
        return header
    
    def close(self):
        if self.r2:
            self.r2.quit()

# ============================================================
# Main
# ============================================================
def main():
    if len(sys.argv) < 2:
        print("Usage: python3 find_offsets.py <path/to/libroblox.so>")
        print("       python3 find_offsets.py --scan libs/arm64-v8a/")
        sys.exit(1)
    
    if sys.argv[1] == '--scan':
        # Scan all .so files in directory
        lib_dir = Path(sys.argv[2])
        for so in lib_dir.glob("*.so"):
            finder = OffsetFinder(so)
            offsets = finder.find_executor_offsets()
            
            # Generate header
            header = finder.generate_header(offsets)
            out_path = Path(__file__).parent.parent / "native" / "include" / "offsets.h"
            out_path.write_text(header)
            print(f"\n[+] Offsets written to: {out_path}")
            
            finder.close()
    else:
        lib_path = Path(sys.argv[1])
        finder = OffsetFinder(lib_path)
        offsets = finder.find_executor_offsets()
        
        # Print results
        print("\n" + "=" * 50)
        print("[+] Found offsets:")
        print("=" * 50)
        for name, addr in offsets.get("offsets", {}).items():
            if isinstance(addr, int):
                print(f"  {name:30s} = 0x{addr:x}")
        for name, val in offsets.get("offsets", {}).items():
            if not isinstance(val, int):
                print(f"  {name:30s} = {val}")
        
        # Generate header
        header = finder.generate_header(offsets)
        out_path = Path(__file__).parent.parent / "native" / "include" / "offsets.h"
        out_path.write_text(header)
        print(f"\n[+] Offsets written to: {out_path}")
        
        finder.close()

if __name__ == "__main__":
    main()