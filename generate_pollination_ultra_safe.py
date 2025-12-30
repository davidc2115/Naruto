#!/usr/bin/env python3
"""
Pollination AI avec rate limiting agressif et validation
"""

import os
import time
import requests
from PIL import Image
from io import BytesIO

BASE_URL = "https://image.pollinations.ai/prompt"
OUTPUT_DIR = "character_images_pollination"
DELAY_BETWEEN_IMAGES = 10  # 10s entre chaque image
DELAY_BETWEEN_CHARS = 30  # 30s entre chaque personnage
MAX_RETRIES = 3
RETRY_DELAY = 30  # 30s avant retry

CHARACTERS = {
    "naruto": "Naruto Uzumaki, young adult male 18+, spiky blonde hair, blue eyes, whisker marks, athletic muscular nude body, explicit adult NSFW",
    "sakura": "Sakura Haruno, young adult woman 18+, pink hair, green eyes, nude feminine body, medium breasts, explicit adult NSFW",
    "hinata": "Hinata Hyuga, young adult woman 18+, long dark indigo hair, pale eyes, nude hourglass figure, large breasts, explicit adult NSFW",
    "ino": "Ino Yamanaka, young adult woman 18+, platinum blonde ponytail, nude slender body, explicit adult NSFW",
    "temari": "Temari, young adult woman 18+, blonde four ponytails, nude tall athletic body, explicit adult NSFW",
    "tsunade": "Tsunade, mature woman 30+, blonde ponytails, nude voluptuous large breasts, explicit adult NSFW",
    "tenten": "Tenten, young adult woman 18+, brown hair buns, nude athletic body, explicit adult NSFW",
    "konan": "Konan, young adult woman 18+, short blue-grey hair, nude slender body, explicit adult NSFW",
    "kurenai": "Kurenai, mature woman 30+, long black curly hair, red eyes, nude curvaceous body, explicit adult NSFW",
    "anko": "Anko, young adult woman 18+, short purple hair, nude curvy body, explicit adult NSFW",
    "kushina": "Kushina, young adult woman 18+, long vibrant red hair, nude hourglass body, explicit adult NSFW",
    "mikoto": "Mikoto, mature woman 30+, long black hair, nude graceful body, explicit adult NSFW",
    "sasuke": "Sasuke Uchiha, young adult male 18+, black hair, dark eyes, nude slender athletic body, explicit adult NSFW"
}

def validate_image(data):
    """Validation stricte"""
    # Vérifier HTML error
    if b'<!DOCTYPE' in data[:100] or b'<html' in data[:100] or b'<HTML' in data[:100]:
        return False, "HTML error page"
    
    if b'Error' in data[:200] or b'error' in data[:200]:
        return False, "Error message"
    
    # Vérifier que c'est une vraie image
    try:
        img = Image.open(BytesIO(data))
        
        # Vérifier format
        if img.format not in ['PNG', 'JPEG', 'WEBP']:
            return False, f"Invalid format: {img.format}"
        
        # Vérifier taille minimale
        if img.size[0] < 400 or img.size[1] < 400:
            return False, f"Too small: {img.size}"
        
        # Vérifier taille fichier minimale (anti-placeholder)
        if len(data) < 50000:  # 50KB min
            return False, f"File too small: {len(data)//1024}KB"
        
        return True, f"{img.format} {img.size[0]}x{img.size[1]} {len(data)//1024}KB"
        
    except Exception as e:
        return False, f"Corrupted: {str(e)[:50]}"

def generate_image(prompt, seed):
    """Génère une image via Pollination AI"""
    # URL avec paramètres
    url = f"{BASE_URL}/{requests.utils.quote(prompt)}"
    params = {
        "width": 512,
        "height": 768,
        "seed": seed,
        "model": "flux",
        "nologo": "true",
        "enhance": "false"
    }
    
    try:
        print(f"    📤 Requesting image...")
        response = requests.get(url, params=params, timeout=120)
        
        print(f"    📊 HTTP {response.status_code}, {len(response.content)//1024}KB")
        
        if response.status_code == 429:
            return None, "Rate limit (429)"
        
        if response.status_code == 500:
            return None, "Server error (500)"
        
        if response.status_code == 502:
            return None, "Bad gateway (502)"
        
        if response.status_code != 200:
            return None, f"HTTP {response.status_code}"
        
        # Valider
        valid, info = validate_image(response.content)
        if valid:
            return response.content, info
        else:
            return None, info
            
    except requests.exceptions.Timeout:
        return None, "Timeout"
    except Exception as e:
        return None, f"Error: {str(e)[:50]}"

def generate_with_retry(char_id, prompt, img_num):
    """Génère avec retry"""
    seed = int(time.time() * 1000) % 2147483647
    
    for attempt in range(1, MAX_RETRIES + 1):
        print(f"  📸 Image {img_num}/3 (attempt {attempt}/{MAX_RETRIES})")
        
        img_data, info = generate_image(prompt, seed + attempt)
        
        if img_data:
            print(f"    ✅ Valid: {info}")
            return img_data
        else:
            print(f"    ❌ Failed: {info}")
            
            if attempt < MAX_RETRIES:
                print(f"    ⏳ Wait {RETRY_DELAY}s before retry...")
                time.sleep(RETRY_DELAY)
            else:
                print(f"    ❌ All retries exhausted")
    
    return None

def main():
    print("╔════════════════════════════════════════════════════════╗")
    print("║  🎨 Pollination AI Ultra Safe NSFW Generator         ║")
    print("╚════════════════════════════════════════════════════════╝\n")
    print(f"📊 {len(CHARACTERS)} chars × 3 = {len(CHARACTERS) * 3} images")
    print(f"⏱️ Delays: {DELAY_BETWEEN_IMAGES}s/image, {DELAY_BETWEEN_CHARS}s/char")
    print(f"🔄 Retries: {MAX_RETRIES} with {RETRY_DELAY}s delay")
    print(f"📁 Output: {OUTPUT_DIR}/\n")
    
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    total_success = 0
    total_attempts = 0
    start_time = time.time()
    
    for i, (char_id, prompt) in enumerate(CHARACTERS.items(), 1):
        print(f"\n{'='*70}")
        print(f"👤 {char_id.upper()} ({i}/{len(CHARACTERS)})")
        print(f"{'='*70}")
        print(f"📝 {prompt[:80]}...")
        
        char_success = 0
        
        for img_num in range(1, 4):
            total_attempts += 1
            
            img_data = generate_with_retry(char_id, prompt, img_num)
            
            if img_data:
                filepath = os.path.join(OUTPUT_DIR, f"{char_id}nsfw{img_num}.png")
                with open(filepath, 'wb') as f:
                    f.write(img_data)
                print(f"  💾 {filepath}")
                total_success += 1
                char_success += 1
            
            # Délai entre images
            if img_num < 3:
                print(f"  ⏳ Wait {DELAY_BETWEEN_IMAGES}s...")
                time.sleep(DELAY_BETWEEN_IMAGES)
        
        print(f"\n✅ {char_id}: {char_success}/3 images")
        
        # Délai entre personnages
        if i < len(CHARACTERS):
            print(f"\n⏳ Wait {DELAY_BETWEEN_CHARS}s before next character...")
            time.sleep(DELAY_BETWEEN_CHARS)
    
    elapsed = time.time() - start_time
    
    print(f"\n\n{'='*70}")
    print(f"🎉 GÉNÉRATION TERMINÉE")
    print(f"{'='*70}")
    print(f"✅ Succès: {total_success}/{len(CHARACTERS) * 3}")
    print(f"📊 Taux: {total_success/total_attempts*100:.1f}%")
    print(f"⏱️ Temps: {elapsed/60:.1f} minutes ({elapsed/3600:.1f}h)")
    print(f"📁 {os.path.abspath(OUTPUT_DIR)}")

if __name__ == "__main__":
    main()
