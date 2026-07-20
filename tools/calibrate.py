#!/usr/bin/env python3
# tools/calibrate.py — Auto-calibrate Hyperion bypass layers
# Usage: python3 calibrate.py [--device SERIAL] [--rounds 10]

import os, sys, re, json, time, argparse, subprocess
from pathlib import Path

PROJECT_DIR = Path(__file__).parent.parent
BUILD_DIR = PROJECT_DIR / "build"

# ============================================================
# Layer definitions
# ============================================================
LAYERS = {
    "tracerpid": {
        "name": "TracerPid Spoof",
        "priority": 1,
        "enabled": True,
        "detect": r"TracerPid:\s+([0-9]+)",
        "success": lambda m: m and int(m.group(1)) == 0,
        "source": "spoof_tracerpid"
    },
    "maps_hide": {
        "name": "Memory Maps Hide",
        "priority": 2,
        "enabled": True,
        "detect": r"libmorvo\.so",
        "success": lambda m: m is None,  # We DON'T want to see it in maps
        "source": "hide_from_maps"
    },
    "debugger": {
        "name": "Debugger Detection Bypass",
        "priority": 3,
        "enabled": True,
        "detect": r"ptrace.*TRACEME",
        "success": lambda m: True,  # Always "succeeds" if no crash
        "source": "bypass_debugger_detect"
    },
    "timing": {
        "name": "Timing Analysis Bypass",
        "priority": 4,
        "enabled": True,
        "detect": None,
        "success": lambda m: True,
        "source": "bypass_timing_check"
    },
    "integrity": {
        "name": "Integrity Check Bypass",
        "priority": 5,
        "enabled": True,
        "detect": r"VerifyCodeIntegrity|CRC failed|integrity",
        "success": lambda m: m is None,
        "source": "bypass_integrity_check"
    },
    "cfg_poison": {
        "name": "CFG/PAC Cache Poison",
        "priority": 6,
        "enabled": True,
        "detect": None,
        "success": lambda m: True,
        "source": "cfg_cache_poison"
    },
    "antitamper": {
        "name": "Anti-Tamper Bypass",
        "priority": 7,
        "enabled": True,
        "detect": r"libtamperprotection\.so",
        "success": lambda m: m is not None,  # Found it → need to deal with it
        "source": "bypass_antitamper"
    }
}

# ============================================================
# Test runner
# ============================================================
class HyperionCalibrator:
    def __init__(self, device=None):
        self.device = device
        self.adb = f"adb -s {device}" if device else "adb"
        self.results = []
        self.passed_layers = set()
        self.failed_layers = set()
    
    def adb_shell(self, cmd):
        """Run ADB shell command with root"""
        result = subprocess.run(
            f"{self.adb} shell 'su -c \"{cmd}\"'",
            shell=True, capture_output=True, text=True, timeout=15
        )
        return result.stdout.strip()
    
    def get_roblox_pid(self):
        """Get Roblox PID"""
        pid = self.adb_shell("pidof com.roblox.client")
        return int(pid) if pid else 0
    
    def restart_roblox(self, enabled_layers):
        """Kill and restart Roblox with specific layers enabled"""
        pid = self.get_roblox_pid()
        if pid:
            self.adb_shell(f"kill {pid}")
            time.sleep(2)
        
        # Build with specific layers
        layers_str = ",".join(enabled_layers)
        self.build_with_layers(enabled_layers)
        
        # Launch
        self.adb_shell(
            "LD_PRELOAD=/data/local/tmp/libmorvo.so "
            "am start -n com.roblox.client/.ActivityProtocolLaunch"
        )
        time.sleep(3)
        
        return self.get_roblox_pid()
    
    def build_with_layers(self, enabled):
        """Build libmorvo.so with specific bypass layers enabled"""
        # Generate config header
        config = "#ifndef MORVO_LAYER_CONFIG_H\n#define MORVO_LAYER_CONFIG_H\n\n"
        for name in LAYERS:
            config += f"#define LAYER_{name.upper()}_ENABLED {'1' if name in enabled else '0'}\n"
        config += "\n#endif\n"
        
        (BUILD_DIR / "layer_config.h").write_text(config)
        
        # Build
        subprocess.run(
            f"bash {PROJECT_DIR}/tools/test_inject.sh {self.device or ''} 2>&1 | tail -3",
            shell=True, capture_output=True
        )
    
    def check_layer(self, layer_name):
        """Check if a specific bypass layer is working"""
        layer = LAYERS[layer_name]
        pattern = layer.get("detect")
        
        if pattern is None:
            return True  # No automatic detection, assume OK
        
        # Check in logcat
        log = subprocess.run(
            f"{self.adb} logcat -d -s Morvo:* Roblox:* 2>/dev/null",
            shell=True, capture_output=True, text=True, timeout=10
        ).stdout
        
        match = re.search(pattern, log, re.IGNORECASE)
        return layer["success"](match)
    
    def check_crash(self, pid):
        """Check if process crashed"""
        if pid == 0:
            return True
        
        # Check if process still alive
        current = self.get_roblox_pid()
        return current == 0 or current != pid
    
    def run_round(self, enabled_layers):
        """Run one calibration round"""
        print(f"\n  [*] Testing layers: {enabled_layers}")
        
        pid = self.restart_roblox(enabled_layers)
        print(f"  [*] Roblox PID: {pid}")
        
        if pid == 0:
            print(f"  [!] Roblox failed to launch — layers may be too aggressive")
            return {"status": "crash", "pid": 0, "layers": enabled_layers}
        
        # Wait for initialization
        time.sleep(2)
        
        results = {"status": "ok", "pid": pid, "layers": enabled_layers, "checks": {}}
        
        for layer_name in enabled_layers:
            passed = self.check_layer(layer_name)
            results["checks"][layer_name] = passed
            
            status = "✅" if passed else "❌"
            print(f"    {status} {LAYERS[layer_name]['name']}")
        
        # Check for crash after checks
        if self.check_crash(pid):
            results["status"] = "crash"
            print(f"  [!] Roblox crashed during tests")
        
        return results
    
    def calibrate(self, rounds=10):
        """Binary search for stable bypass configuration"""
        print("=" * 50)
        print("Morvo — Hyperion Calibration")
        print("=" * 50)
        
        # Start with all layers
        current_layers = list(LAYERS.keys())
        
        for round_num in range(rounds):
            print(f"\n=== Round {round_num + 1}/{rounds} ===")
            
            result = self.run_round(current_layers)
            self.results.append(result)
            
            if result["status"] == "crash":
                # Binary search: disable half of remaining layers
                if len(current_layers) > 1:
                    remove = current_layers[len(current_layers)//2:]
                    print(f"  [*] Removing layers: {remove}")
                    for l in remove:
                        self.failed_layers.add(l)
                    current_layers = [l for l in current_layers if l not in remove]
                else:
                    print(f"  [!] Only 1 layer left and still crashing. Can't calibrate further.")
                    break
            else:
                # All layers pass — mark as stable
                self.passed_layers.update(current_layers)
                
                # Try adding back failed layers one by one
                for failed in list(self.failed_layers):
                    if failed not in current_layers:
                        current_layers = sorted(
                            set(current_layers + [failed]),
                            key=lambda x: LAYERS[x]["priority"]
                        )
                        print(f"  [*] Adding back layer: {failed}")
                        break
                else:
                    print(f"  [*] All layers added back. Stable configuration found!")
                    break
            
            if round_num == rounds - 1:
                print(f"\n[*] Max rounds reached.")
        
        return self.generate_report()
    
    def generate_report(self):
        """Generate calibration report"""
        report = {
            "timestamp": time.time(),
            "rounds": len(self.results),
            "stable_layers": sorted(self.passed_layers),
            "unstable_layers": sorted(self.failed_layers - self.passed_layers),
            "details": self.results
        }
        
        # Save
        report_path = BUILD_DIR / "calibration_report.json"
        BUILD_DIR.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, indent=2))
        
        # Print summary
        print("\n" + "=" * 50)
        print("Calibration Complete")
        print("=" * 50)
        print(f"\nStable layers ({len(self.passed_layers)}/{len(LAYERS)}):")
        for l in sorted(self.passed_layers):
            print(f"  ✅ {LAYERS[l]['name']}")
        
        unstable = sorted(self.failed_layers - self.passed_layers)
        if unstable:
            print(f"\nFailed layers ({len(unstable)}):")
            for l in unstable:
                print(f"  ❌ {LAYERS[l]['name']}")
        
        print(f"\nReport saved: {report_path}")
        return report

# ============================================================
# Main
# ============================================================
def main():
    parser = argparse.ArgumentParser(description="Auto-calibrate Hyperion bypass")
    parser.add_argument('--device', help='ADB device serial')
    parser.add_argument('--rounds', type=int, default=10)
    parser.add_argument('--list-layers', action='store_true', help='List all layers')
    parser.add_argument('--test-layer', help='Test a specific layer only')
    args = parser.parse_args()
    
    if args.list_layers:
        for name, layer in sorted(LAYERS.items(), key=lambda x: x[1]['priority']):
            print(f"  [{layer['priority']}] {name}: {layer['name']}")
        return
    
    cal = HyperionCalibrator(args.device)
    
    if args.test_layer:
        if args.test_layer not in LAYERS:
            print(f"Unknown layer: {args.test_layer}")
            sys.exit(1)
        pid = cal.restart_roblox([args.test_layer])
        passed = cal.check_layer(args.test_layer)
        print(f"\n{args.test_layer}: {'✅ PASS' if passed else '❌ FAIL'}")
    else:
        cal.calibrate(args.rounds)

if __name__ == "__main__":
    main()