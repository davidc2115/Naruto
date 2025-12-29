#!/usr/bin/env python3
"""
Générateur d'images NSFW via Stable Horde
- Gratuit, illimité, pas de clé API
- Support NSFW complet
- 13 personnages × 10 images = 130 images
"""

import requests
import time
import os
import base64
import json

API_BASE = "https://stablehorde.net/api/v2"
API_KEY = "0000000000"  # Clé anonyme gratuite
OUTPUT_DIR = "/workspace/app/src/main/res/drawable"
POLL_INTERVAL = 5  # Vérifier toutes les 5 secondes
MAX_WAIT = 180  # 3 minutes max par image

CHARACTERS = {
    "naruto": "athletic 20yo male, spiky blonde hair, blue eyes, whisker marks, tanned skin, muscular",
    "sasuke": "athletic 20yo male, black spiky hair, dark eyes, pale skin, lean muscular",
    "sakura": "athletic 20yo female, pink hair, green eyes, fair skin, toned figure",
    "hinata": "elegant 20yo female, long indigo hair, lavender eyes, porcelain skin, graceful curves",
    "kakashi": "athletic 27yo male, silver hair, one visible eye, mask, lean muscular",
    "itachi": "elegant 21yo male, long black hair, dark eyes, pale skin, lean build",
    "bradpitt": "handsome 60yo male Brad Pitt, blonde hair, blue eyes, chiseled jawline",
    "leonardo": "handsome 49yo male Leonardo DiCaprio, light brown hair, blue eyes",
    "therock": "muscular 51yo male Dwayne Johnson, bald, brown eyes, massive build, tattoos",
    "scarlett": "beautiful 39yo actress Scarlett Johansson, blonde hair, green eyes, curvy",
    "margot": "beautiful 34yo actress Margot Robbie, blonde hair, blue eyes, athletic curves",
    "emma": "elegant 34yo actress Emma Watson, brown hair, brown eyes, slender figure",
    "zendaya": "stunning 28yo actress Zendaya, long curly hair, brown eyes, tall slender"
}

NSFW_PROMPTS = [
    "suggestive pose, flirty expression, elegant revealing outfit",
    "seductive bedroom eyes, intimate lingerie, sensual lighting",
    "artistic nude, tasteful, soft lighting, professional",
    "explicit nude, full body, detailed anatomy",
    "erotic pose, passionate expression, intimate",
    "adult content, explicit details, high quality",
    "nsfw explicit, adult scene, photorealistic",
    "hardcore nsfw, very explicit, ultra detailed",
    "extreme nsfw, maximum detail, hyper realistic",
    "ultimate nsfw, complete anatomy, masterpiece"
]

def submit_generation(char_desc, nsfw_prompt):
    """Soumet une requête de génération"""
    prompt = f"{char_desc}, {nsfw_prompt}, photorealistic, 8k uhd, professional, masterpiece, adult 18+"
    
    payload = {
        "prompt": prompt,
        "params": {
            "width": 512,
            "height": 768,
            "steps": 20,
            "cfg_scale": 7.0,
            "sampler_name": "k_euler",
            "n": 1
        },
        "nsfw": True,
        "trusted_workers": False,
        "slow_workers": True,
        "censor_nsfw": False,
        "models": ["stable_diffusion"],
        "r2": True  # Retourner base64
    }
    
    response = requests.post(
        f"{API_BASE}/generate/async",
        headers={"Content-Type": "application/json", "apikey": API_KEY},
        json=payload,
        timeout=30
    )
    
    if response.status_code != 202:
        raise Exception(f"Submit failed: {response.status_code}")
    
    return response.json()["id"]

def wait_for_generation(request_id):
    """Attend que la génération soit terminée"""
    start_time = time.time()
    
    while time.time() - start_time < MAX_WAIT:
        response = requests.get(
            f"{API_BASE}/generate/check/{request_id}",
            headers={"apikey": API_KEY},
            timeout=30
        )
        
        if response.status_code != 200:
            raise Exception(f"Check failed: {response.status_code}")
        
        data = response.json()
        
        if data.get("faulted"):
            raise Exception("Generation faulted")
        
        if data.get("done"):
            return get_generated_image(request_id)
        
        queue_pos = data.get("queue_position", 0)
        wait_time = data.get("wait_time", 0)
        print(f"    ⏳ Queue: {queue_pos}, Attente: {wait_time}s")
        
        time.sleep(POLL_INTERVAL)
    
    raise Exception("Timeout")

def get_generated_image(request_id):
    """Récupère l'image générée"""
    response = requests.get(
        f"{API_BASE}/generate/status/{request_id}",
        headers={"apikey": API_KEY},
        timeout=30
    )
    
    if response.status_code != 200:
        raise Exception(f"Status failed: {response.status_code}")
    
    data = response.json()
    generations = data.get("generations", [])
    
    if not generations:
        raise Exception("No image generated")
    
    # Récupérer image (base64 ou URL)
    gen = generations[0]
    if "img" in gen:
        return base64.b64decode(gen["img"])
    elif "url" in gen:
        img_response = requests.get(gen["url"], timeout=60)
        return img_response.content
    
    raise Exception("Unknown response format")

def main():
    print("="*70)
    print("🎨 GÉNÉRATION GALERIES NSFW via STABLE HORDE")
    print("="*70)
    print(f"🌐 API: Stable Horde (gratuit, illimité)")
    print(f"📁 Destination: {OUTPUT_DIR}")
    print(f"📸 Total: 13 personnages × 10 images = 130 images")
    print(f"⏱️  Temps estimé: ~40-60 minutes")
    print("="*70)
    
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    total_success = 0
    total_fail = 0
    
    for char_name, char_desc in CHARACTERS.items():
        print(f"\n{'='*70}")
        print(f"🎨 PERSONNAGE: {char_name.upper()}")
        print(f"{'='*70}")
        
        for i, nsfw_prompt in enumerate(NSFW_PROMPTS, 1):
            filename = f"{char_name}nsfw{i}.jpg"
            filepath = os.path.join(OUTPUT_DIR, filename)
            
            print(f"  [{i}/10] {filename}")
            
            # Retry
            for attempt in range(3):
                try:
                    request_id = submit_generation(char_desc, nsfw_prompt)
                    print(f"    📡 Request ID: {request_id}")
                    
                    image_data = wait_for_generation(request_id)
                    
                    with open(filepath, 'wb') as f:
                        f.write(image_data)
                    
                    print(f"    ✅ SUCCESS ({len(image_data)//1024}KB)")
                    total_success += 1
                    break
                    
                except Exception as e:
                    print(f"    ❌ Erreur (tentative {attempt+1}/3): {e}")
                    if attempt < 2:
                        time.sleep(10)
                    else:
                        total_fail += 1
            
            # Délai entre images
            time.sleep(3)
    
    print(f"\n{'='*70}")
    print(f"✅ TERMINÉ!")
    print(f"{'='*70}")
    print(f"✅ Succès: {total_success}/130")
    print(f"❌ Échecs: {total_fail}/130")
    print(f"📁 Images dans: {OUTPUT_DIR}")
    print(f"{'='*70}")

if __name__ == "__main__":
    main()
