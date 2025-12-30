#!/usr/bin/env python3
"""
Test Stable Horde avec 3 personnages seulement
"""

import os
import time
import requests
from PIL import Image
from io import BytesIO

BASE_URL = "https://stablehorde.net/api/v2"
API_KEY = "0000000000"
OUTPUT_DIR = "character_images_test"
MAX_RETRIES = 2
CHECK_INTERVAL = 10
MAX_WAIT = 600

# TEST avec 3 personnages seulement
CHARACTERS = {
    "naruto": "Naruto Uzumaki, young adult male 18+ years, spiky blonde hair, blue eyes, whisker marks, athletic muscular nude body, explicit adult NSFW, naked, photorealistic",
    "sakura": "Sakura Haruno, young adult woman 18+ years, pink hair, green eyes, nude feminine body, medium breasts, explicit adult NSFW, naked, photorealistic",
    "hinata": "Hinata Hyuga, young adult woman 18+ years, long dark indigo hair, pale eyes, nude hourglass figure, large breasts, explicit adult NSFW, naked, photorealistic"
}

def validate_image(data):
    try:
        img = Image.open(BytesIO(data))
        if img.format in ['PNG', 'JPEG', 'WEBP']:
            return True, f"{img.format} {img.size[0]}x{img.size[1]}"
        return False, f"Invalid format: {img.format}"
    except:
        if b'<!doctype' in data[:50].lower() or b'<html' in data[:50].lower():
            return False, "HTML error"
        return False, "Corrupted file"

def generate(prompt):
    try:
        print(f"    📤 Submitting...")
        r = requests.post(
            f"{BASE_URL}/generate/async",
            json={
                "prompt": prompt,
                "params": {"width": 512, "height": 768, "steps": 20, "cfg_scale": 7, "sampler_name": "k_euler", "n": 1},
                "nsfw": True,
                "trusted_workers": True,
                "censor_nsfw": False,
                "models": ["stable_diffusion"]
            },
            headers={"apikey": API_KEY},
            timeout=30
        )
        
        if r.status_code != 200:
            print(f"    ❌ Submit failed: HTTP {r.status_code}")
            return None
        
        req_id = r.json()["id"]
        print(f"    ✅ ID: {req_id}")
        
        # Wait
        start = time.time()
        while (time.time() - start) < MAX_WAIT:
            r2 = requests.get(f"{BASE_URL}/generate/check/{req_id}", headers={"apikey": API_KEY}, timeout=10)
            status = r2.json()
            
            if status.get("done"):
                print(f"    ✅ Done in {int(time.time()-start)}s")
                
                # Get result
                r3 = requests.get(f"{BASE_URL}/generate/status/{req_id}", headers={"apikey": API_KEY}, timeout=10)
                data = r3.json()
                
                if data.get("generations") and data["generations"]:
                    img_url = data["generations"][0].get("img")
                    if img_url:
                        r4 = requests.get(img_url, timeout=60)
                        if r4.status_code == 200:
                            valid, info = validate_image(r4.content)
                            if valid:
                                print(f"    ✅ Valid: {info}")
                                return r4.content
                            else:
                                print(f"    ❌ Invalid: {info}")
                return None
            
            if status.get("faulted"):
                print(f"    ❌ Faulted")
                return None
            
            q = status.get("queue_position", "?")
            w = status.get("wait_time", "?")
            print(f"    ⏳ {int(time.time()-start)}s | Queue: {q} | Wait: {w}s")
            time.sleep(CHECK_INTERVAL)
        
        print(f"    ❌ Timeout")
        return None
        
    except Exception as e:
        print(f"    ❌ Error: {e}")
        return None

def main():
    print("╔═════════════════════════════════════════════════╗")
    print("║  🧪 Test Stable Horde (3 characters)          ║")
    print("╚═════════════════════════════════════════════════╝\n")
    
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    total = 0
    
    for char_id, prompt in CHARACTERS.items():
        print(f"\n{'='*60}")
        print(f"👤 {char_id.upper()}")
        print(f"{'='*60}")
        
        for i in range(1, 4):
            print(f"\n  📸 Image {i}/3")
            
            for attempt in range(1, MAX_RETRIES + 1):
                if attempt > 1:
                    print(f"    🔄 Retry {attempt}/{MAX_RETRIES}")
                
                img_data = generate(prompt)
                
                if img_data:
                    filepath = os.path.join(OUTPUT_DIR, f"{char_id}nsfw{i}.png")
                    with open(filepath, 'wb') as f:
                        f.write(img_data)
                    print(f"    💾 {filepath} ({len(img_data)//1024}KB)")
                    total += 1
                    break
                
                if attempt < MAX_RETRIES:
                    print(f"    ⏳ Wait 10s before retry...")
                    time.sleep(10)
            
            time.sleep(2)
        
        print(f"\n⏳ Pause 10s...")
        time.sleep(10)
    
    print(f"\n\n🎉 Test terminé: {total}/9 images")
    print(f"📁 {os.path.abspath(OUTPUT_DIR)}")

if __name__ == "__main__":
    main()
