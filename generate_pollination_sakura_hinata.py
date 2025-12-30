#!/usr/bin/env python3
"""
Génération NSFW Sakura + Hinata via Pollination AI
3 images par personnage = 6 images total
"""

import os
import time
import requests
from PIL import Image
from io import BytesIO

API_URL = "https://image.pollinations.ai/prompt"
OUTPUT_DIR = "character_images_nsfw"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Sakura + Hinata (3 images chacune)
CHARACTERS = {
    # Sakura Haruno
    "sakura_1": {
        "prompt": "Sakura Haruno from Naruto anime, young adult woman 18+, pink hair, green eyes, petite athletic body, anime style, high quality, detailed, NSFW, nude, explicit",
        "character": "Sakura"
    },
    "sakura_2": {
        "prompt": "Sakura Haruno, young adult woman 18+, short pink hair, green eyes, fit body, anime art style, beautiful face, NSFW, nude, explicit content",
        "character": "Sakura"
    },
    "sakura_3": {
        "prompt": "Sakura Haruno anime character, adult woman 18+, pink hair green eyes, athletic physique, detailed anime illustration, NSFW, nude, explicit",
        "character": "Sakura"
    },
    
    # Hinata Hyuga
    "hinata_1": {
        "prompt": "Hinata Hyuga from Naruto anime, young adult woman 18+, long dark blue hair, pale lavender eyes, curvy body, anime style, high quality, detailed, NSFW, nude, explicit",
        "character": "Hinata"
    },
    "hinata_2": {
        "prompt": "Hinata Hyuga, adult woman 18+, long dark hair, pale eyes, voluptuous body, large breasts, anime art style, beautiful, NSFW, nude, explicit content",
        "character": "Hinata"
    },
    "hinata_3": {
        "prompt": "Hinata Hyuga anime character, adult woman 18+, dark hair pale eyes, curvy physique, shy expression, detailed anime illustration, NSFW, nude, explicit",
        "character": "Hinata"
    }
}

def validate_image(content):
    """Valide qu'on a bien une image PNG/JPG"""
    try:
        # Check si c'est pas du HTML
        if content[:100].decode('utf-8', errors='ignore').lower().startswith('<!doctype') or \
           b'<html' in content[:100].lower():
            return False
        
        # Check format image
        img = Image.open(BytesIO(content))
        if img.format not in ['PNG', 'JPEG', 'JPG']:
            return False
        
        # Check taille minimale (>10KB)
        if len(content) < 10000:
            return False
        
        # Check dimensions (>200px)
        if img.size[0] < 200 or img.size[1] < 200:
            return False
        
        return True
    except Exception as e:
        return False

def generate_one(img_id, data):
    """Génère 1 image via Pollination AI"""
    prompt = data["prompt"]
    character = data["character"]
    
    print(f"\n[{img_id}] {character} - Start...")
    print(f"Prompt: {prompt[:80]}...")
    
    max_retries = 3
    
    for attempt in range(max_retries):
        try:
            # Paramètres optimisés pour NSFW
            params = {
                "prompt": prompt,
                "width": 512,
                "height": 768,
                "seed": int(time.time() * 1000 + hash(img_id) + attempt) % 2147483647,
                "nologo": "true",
                "enhance": "true"
            }
            
            print(f"[{img_id}] Attempt {attempt + 1}/{max_retries}...", flush=True)
            
            # Request avec timeout long
            r = requests.get(API_URL, params=params, timeout=(30, 120))
            
            if r.status_code != 200:
                print(f"[{img_id}] ❌ HTTP {r.status_code}")
                time.sleep(10)
                continue
            
            # Valider image
            if not validate_image(r.content):
                print(f"[{img_id}] ⚠️  Invalid image, retry...")
                time.sleep(10)
                continue
            
            # Sauvegarder
            filepath = os.path.join(OUTPUT_DIR, f"{img_id}.png")
            with open(filepath, 'wb') as f:
                f.write(r.content)
            
            size_kb = len(r.content) // 1024
            print(f"[{img_id}] ✅ Success! ({size_kb}KB)")
            print(f"📁 {os.path.abspath(filepath)}")
            
            return True
            
        except requests.Timeout:
            print(f"[{img_id}] ⏱️  Timeout, retry...")
            time.sleep(15)
        except Exception as e:
            print(f"[{img_id}] ❌ Error: {e}")
            time.sleep(10)
    
    print(f"[{img_id}] ❌ Failed after {max_retries} attempts")
    return False

def main():
    print("╔═════════════════════════════════════════════╗")
    print("║  🌸 Génération Sakura + Hinata (6 images)  ║")
    print("║       via Pollination AI                    ║")
    print("╚═════════════════════════════════════════════╝\n")
    print(f"Images: {len(CHARACTERS)}")
    print(f"API: Pollination AI")
    print(f"Output: {os.path.abspath(OUTPUT_DIR)}\n")
    
    start_global = time.time()
    results = {}
    
    for i, (img_id, data) in enumerate(CHARACTERS.items(), 1):
        print(f"\n{'='*60}")
        print(f"Progress: {i}/{len(CHARACTERS)}")
        print(f"{'='*60}")
        
        success = generate_one(img_id, data)
        results[img_id] = "✅" if success else "❌"
        
        # Pause entre images (rate limiting)
        if i < len(CHARACTERS):
            print(f"\n💤 Waiting 15s before next image...")
            time.sleep(15)
    
    elapsed = time.time() - start_global
    success_count = sum(1 for v in results.values() if v == "✅")
    
    print(f"\n{'='*60}")
    print(f"📊 FINAL RESULTS")
    print(f"{'='*60}\n")
    
    print("🌸 Sakura:")
    for img_id in ["sakura_1", "sakura_2", "sakura_3"]:
        print(f"  {results.get(img_id, '❓')} {img_id}")
    
    print("\n💜 Hinata:")
    for img_id in ["hinata_1", "hinata_2", "hinata_3"]:
        print(f"  {results.get(img_id, '❓')} {img_id}")
    
    print(f"\n✅ Success: {success_count}/{len(CHARACTERS)}")
    print(f"⏱️  Total time: {elapsed/60:.1f} min ({elapsed/success_count:.1f}s per image)")
    print(f"📁 Output: {os.path.abspath(OUTPUT_DIR)}")
    print()

if __name__ == "__main__":
    main()
