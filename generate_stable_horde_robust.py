#!/usr/bin/env python3
"""
Stable Horde avec validation et retry robustes
"""

import os
import time
import requests
from PIL import Image
from io import BytesIO

BASE_URL = "https://stablehorde.net/api/v2"
API_KEY = "0000000000"
OUTPUT_DIR = "character_images_nsfw_final"
MAX_RETRIES = 3
CHECK_INTERVAL = 10
MAX_WAIT = 600  # 10 minutes

CHARACTERS = {
    "naruto": "Naruto Uzumaki, young adult male 18+ years old, spiky blonde hair, blue eyes, whisker marks, athletic muscular nude body, explicit adult NSFW content, naked, photorealistic, masterpiece",
    "sakura": "Sakura Haruno, young adult woman 18+ years old, pink hair, green eyes, nude feminine body, medium breasts, explicit adult NSFW content, naked, photorealistic, masterpiece",
    "hinata": "Hinata Hyuga, young adult woman 18+ years old, long dark indigo hair, pale eyes, nude hourglass figure, large breasts, explicit adult NSFW content, naked, photorealistic, masterpiece",
    "ino": "Ino Yamanaka, young adult woman 18+ years old, platinum blonde ponytail, nude slender body, explicit adult NSFW content, naked, photorealistic, masterpiece",
    "temari": "Temari, young adult woman 18+ years old, blonde four ponytails, nude tall athletic body, explicit adult NSFW content, naked, photorealistic, masterpiece",
    "tsunade": "Tsunade, mature woman 30+ years old, blonde ponytails, nude voluptuous large breasts, explicit adult NSFW content, naked, photorealistic, masterpiece",
    "tenten": "Tenten, young adult woman 18+ years old, brown hair buns, nude athletic body, explicit adult NSFW content, naked, photorealistic, masterpiece",
    "konan": "Konan, young adult woman 18+ years old, short blue-grey hair, nude slender body, explicit adult NSFW content, naked, photorealistic, masterpiece",
    "kurenai": "Kurenai, mature woman 30+ years old, long black curly hair, red eyes, nude curvaceous body, explicit adult NSFW content, naked, photorealistic, masterpiece",
    "anko": "Anko, young adult woman 18+ years old, short purple hair, nude curvy body, explicit adult NSFW content, naked, photorealistic, masterpiece",
    "kushina": "Kushina, young adult woman 18+ years old, long vibrant red hair, nude hourglass body, explicit adult NSFW content, naked, photorealistic, masterpiece",
    "mikoto": "Mikoto, mature woman 30+ years old, long black hair, nude graceful body, explicit adult NSFW content, naked, photorealistic, masterpiece",
    "sasuke": "Sasuke Uchiha, young adult male 18+ years old, black hair, dark eyes, nude slender athletic body, explicit adult NSFW content, naked, photorealistic, masterpiece"
}

def validate_image(data):
    """Valide que c'est une vraie image PNG/JPEG"""
    try:
        img = Image.open(BytesIO(data))
        if img.format in ['PNG', 'JPEG']:
            return True, f"{img.format} {img.size[0]}x{img.size[1]}"
        return False, f"Format invalide: {img.format}"
    except Exception as e:
        # Vérifier si c'est du HTML (erreur)
        if data[:15].lower().find(b'<!doctype') >= 0 or data[:15].lower().find(b'<html') >= 0:
            return False, "HTML error page"
        return False, f"Corrupted: {str(e)[:50]}"

def submit_generation(prompt):
    """Soumet une génération"""
    payload = {
        "prompt": prompt,
        "params": {
            "width": 512,
            "height": 768,
            "steps": 20,
            "cfg_scale": 7.0,
            "sampler_name": "k_euler_a",
            "n": 1
        },
        "nsfw": True,
        "trusted_workers": True,
        "slow_workers": True,
        "censor_nsfw": False,
        "models": ["stable_diffusion"]
    }
    
    try:
        r = requests.post(f"{BASE_URL}/generate/async", json=payload, headers={"apikey": API_KEY}, timeout=30)
        r.raise_for_status()
        return r.json()["id"]
    except Exception as e:
        print(f"    ❌ Submit error: {e}")
        return None

def wait_and_download(request_id):
    """Attend et télécharge l'image"""
    start = time.time()
    
    while (time.time() - start) < MAX_WAIT:
        try:
            # Check status
            r = requests.get(f"{BASE_URL}/generate/check/{request_id}", headers={"apikey": API_KEY}, timeout=10)
            r.raise_for_status()
            status = r.json()
            
            if status.get("done"):
                # Get image
                r2 = requests.get(f"{BASE_URL}/generate/status/{request_id}", headers={"apikey": API_KEY}, timeout=10)
                r2.raise_for_status()
                data = r2.json()
                
                if data.get("generations") and len(data["generations"]) > 0:
                    img_url = data["generations"][0].get("img")
                    if img_url:
                        # Download
                        r3 = requests.get(img_url, timeout=60)
                        r3.raise_for_status()
                        
                        # Validate
                        valid, info = validate_image(r3.content)
                        if valid:
                            return r3.content, info
                        else:
                            print(f"    ⚠️ Invalid image: {info}")
                            return None, None
                
                return None, None
            
            if status.get("faulted"):
                print(f"    ❌ Generation faulted")
                return None, None
            
            queue_pos = status.get("queue_position", "?")
            wait_time = status.get("wait_time", "?")
            elapsed = int(time.time() - start)
            print(f"    ⏳ {elapsed}s | Queue: {queue_pos} | ETA: {wait_time}s")
            
            time.sleep(CHECK_INTERVAL)
            
        except Exception as e:
            print(f"    ⚠️ Check error: {e}")
            time.sleep(CHECK_INTERVAL)
    
    print(f"    ❌ Timeout after {MAX_WAIT}s")
    return None, None

def generate_image_with_retry(char_id, prompt, img_num):
    """Génère avec retry"""
    for attempt in range(1, MAX_RETRIES + 1):
        print(f"  📸 Image {img_num} (attempt {attempt}/{MAX_RETRIES})")
        
        request_id = submit_generation(prompt)
        if not request_id:
            print(f"    ⚠️ Submit failed, retry in 5s...")
            time.sleep(5)
            continue
        
        print(f"    ✅ ID: {request_id}")
        
        img_data, info = wait_and_download(request_id)
        if img_data:
            print(f"    ✅ Valid image: {info}")
            return img_data
        
        print(f"    ⚠️ Failed, retry in 10s...")
        time.sleep(10)
    
    print(f"    ❌ All retries failed")
    return None

def main():
    print("╔═══════════════════════════════════════════════════════════╗")
    print("║  🎨 Stable Horde Robust NSFW Generator                  ║")
    print("╚═══════════════════════════════════════════════════════════╝")
    print(f"\n📊 {len(CHARACTERS)} characters × 3 images = {len(CHARACTERS) * 3} total")
    print(f"📁 Output: {OUTPUT_DIR}/")
    print(f"🔄 Max retries per image: {MAX_RETRIES}")
    print(f"⏱️ Max wait per image: {MAX_WAIT//60} min\n")
    
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    total_success = 0
    total_attempts = 0
    start_global = time.time()
    
    for char_id, prompt in CHARACTERS.items():
        print(f"\n{'='*70}")
        print(f"👤 {char_id.upper()}")
        print(f"{'='*70}")
        print(f"📝 {prompt[:80]}...")
        
        char_success = 0
        
        for i in range(1, 4):
            total_attempts += 1
            
            img_data = generate_image_with_retry(char_id, prompt, i)
            
            if img_data:
                filepath = os.path.join(OUTPUT_DIR, f"{char_id}nsfw{i}.png")
                with open(filepath, 'wb') as f:
                    f.write(img_data)
                
                size_kb = len(img_data) // 1024
                print(f"  💾 {filepath} ({size_kb}KB)")
                
                total_success += 1
                char_success += 1
            
            # Pause entre images
            if i < 3:
                print(f"  ⏳ Pause 3s...")
                time.sleep(3)
        
        print(f"\n✅ {char_id}: {char_success}/3 images")
        
        # Pause entre personnages
        if char_id != list(CHARACTERS.keys())[-1]:
            print(f"\n⏳ Pause 10s avant personnage suivant...")
            time.sleep(10)
    
    elapsed = time.time() - start_global
    
    print(f"\n\n{'='*70}")
    print(f"🎉 GÉNÉRATION TERMINÉE")
    print(f"{'='*70}")
    print(f"✅ Succès: {total_success}/{len(CHARACTERS) * 3}")
    print(f"📊 Taux: {total_success/total_attempts*100:.1f}%")
    print(f"⏱️ Temps: {elapsed/60:.1f} minutes")
    print(f"📁 {os.path.abspath(OUTPUT_DIR)}")

if __name__ == "__main__":
    main()
