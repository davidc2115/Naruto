#!/usr/bin/env python3
"""
Génère les galeries NSFW via Stable Horde (gratuit, illimité, NSFW OK)
"""

import os
import time
import requests
import json

# Config Stable Horde
BASE_URL = "https://stablehorde.net/api/v2"
API_KEY = "0000000000"  # Clé anonyme gratuite
OUTPUT_DIR = "character_images_nsfw"
IMAGES_PER_CHARACTER = 3
CHECK_INTERVAL = 5
MAX_WAIT = 300  # 5 minutes max par image

CHARACTERS = {
    "naruto": ("Naruto Uzumaki", "young male ninja, spiky blonde hair, blue eyes, whisker marks, athletic muscular body, explicit nude NSFW 18+"),
    "sakura": ("Sakura Haruno", "young woman, pink hair, green eyes, athletic feminine body, medium breasts, explicit nude NSFW 18+"),
    "hinata": ("Hinata Hyuga", "young woman, long dark indigo hair, pale lavender eyes, hourglass figure, large breasts, explicit nude NSFW 18+"),
    "ino": ("Ino Yamanaka", "young woman, platinum blonde ponytail, blue-green eyes, slender curves, explicit nude NSFW 18+"),
    "temari": ("Temari", "young woman, blonde four ponytails, teal eyes, tall athletic figure, explicit nude NSFW 18+"),
    "tsunade": ("Tsunade", "mature woman, blonde ponytails, voluptuous large breasts, explicit nude NSFW 18+"),
    "tenten": ("Tenten", "young woman, brown hair buns, athletic toned body, explicit nude NSFW 18+"),
    "konan": ("Konan", "young woman, short blue-grey hair, pale skin, slender curves, explicit nude NSFW 18+"),
    "kurenai": ("Kurenai", "mature woman, long black curly hair, red eyes, curvaceous figure, explicit nude NSFW 18+"),
    "anko": ("Anko", "young woman, short purple hair, tan skin, curvy figure, explicit nude NSFW 18+"),
    "kushina": ("Kushina", "young woman, long vibrant red hair, violet eyes, hourglass curves, explicit nude NSFW 18+"),
    "mikoto": ("Mikoto", "mature woman, long black hair, dark eyes, graceful figure, explicit nude NSFW 18+"),
    "sasuke": ("Sasuke Uchiha", "young male ninja, black hair, dark eyes, pale skin, slender athletic body, explicit nude NSFW 18+")
}

def submit_generation(prompt, width=512, height=768, steps=15):
    """Soumet une génération à Stable Horde"""
    payload = {
        "prompt": prompt,
        "params": {
            "width": width,
            "height": height,
            "steps": steps,
            "cfg_scale": 7.0,
            "sampler_name": "k_euler",
            "n": 1
        },
        "nsfw": True,
        "trusted_workers": False,
        "slow_workers": True,
        "censor_nsfw": False,
        "models": ["stable_diffusion"],
        "r2": False  # URL directe
    }
    
    response = requests.post(
        f"{BASE_URL}/generate/async",
        json=payload,
        headers={"apikey": API_KEY, "Content-Type": "application/json"},
        timeout=30
    )
    response.raise_for_status()
    return response.json()["id"]

def check_status(request_id):
    """Vérifie le statut"""
    response = requests.get(
        f"{BASE_URL}/generate/check/{request_id}",
        headers={"apikey": API_KEY},
        timeout=10
    )
    response.raise_for_status()
    return response.json()

def get_image(request_id):
    """Récupère l'image générée"""
    response = requests.get(
        f"{BASE_URL}/generate/status/{request_id}",
        headers={"apikey": API_KEY},
        timeout=10
    )
    response.raise_for_status()
    data = response.json()
    
    if data.get("generations") and len(data["generations"]) > 0:
        img_url = data["generations"][0].get("url")
        if img_url:
            # Télécharger l'image
            img_response = requests.get(img_url, timeout=30)
            img_response.raise_for_status()
            return img_response.content
    return None

def generate_image(char_name, description, variation):
    """Génère une image complète"""
    variations = [
        "seductive pose, bedroom scene",
        "artistic nude, elegant pose",
        "intimate setting, sensual"
    ]
    
    prompt = f"{char_name}, {description}, {variations[variation % 3]}"
    
    print(f"  📝 Prompt: {prompt[:100]}")
    
    # Soumettre
    request_id = submit_generation(prompt)
    print(f"  ✅ ID: {request_id}")
    
    # Attendre
    start = time.time()
    while (time.time() - start) < MAX_WAIT:
        status = check_status(request_id)
        
        if status.get("done"):
            print(f"  ✅ Terminé ({int(time.time() - start)}s)")
            return get_image(request_id)
        
        if status.get("faulted"):
            print(f"  ❌ Échec (faulted)")
            return None
        
        queue_pos = status.get("queue_position", 0)
        wait_time = status.get("wait_time", 0)
        print(f"  ⏳ Queue: {queue_pos}, Attente: {wait_time}s")
        
        time.sleep(CHECK_INTERVAL)
    
    print(f"  ❌ Timeout")
    return None

def main():
    print("╔════════════════════════════════════════════════════╗")
    print("║  🎨 Galeries NSFW - Stable Horde (Gratuit)      ║")
    print("╚════════════════════════════════════════════════════╝")
    print(f"\n📊 {len(CHARACTERS)} personnages × {IMAGES_PER_CHARACTER} = {len(CHARACTERS) * IMAGES_PER_CHARACTER} images")
    print(f"📁 Output: {OUTPUT_DIR}/\n")
    
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    total = 0
    
    for char_id, (name, desc) in CHARACTERS.items():
        print(f"\n{'='*70}")
        print(f"🎨 {name}")
        print(f"{'='*70}")
        
        for i in range(1, IMAGES_PER_CHARACTER + 1):
            print(f"\n📸 Image {i}/{IMAGES_PER_CHARACTER}")
            
            img_data = generate_image(name, desc, i)
            
            if img_data:
                filepath = os.path.join(OUTPUT_DIR, f"{char_id}nsfw{i}.png")
                with open(filepath, 'wb') as f:
                    f.write(img_data)
                print(f"  💾 {filepath} ({len(img_data)//1024}KB)")
                total += 1
            
            time.sleep(2)
        
        print(f"\n⏳ Pause 10s avant personnage suivant...")
        time.sleep(10)
    
    print(f"\n\n🎉 TERMINÉ: {total}/{len(CHARACTERS) * IMAGES_PER_CHARACTER} images")
    print(f"📁 {os.path.abspath(OUTPUT_DIR)}")

if __name__ == "__main__":
    main()
