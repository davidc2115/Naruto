#!/usr/bin/env python3
"""
Générateur NSFW optimisé pour intégration APK
- Noms de fichiers Android-compatible (lowercase, underscores)
- 13 personnages × 10 images = 130 images (au lieu de 195)
- Sauvegarde directe dans drawable/
"""

import requests
import time
import os
from urllib.parse import quote

# Configuration
POLLINATION_BASE_URL = "https://image.pollinations.ai/prompt"
OUTPUT_DIR = "/workspace/app/src/main/res/drawable"
DELAY = 10  # 10 secondes entre images

# 13 personnages de l'app
CHARACTERS = {
    "naruto": {
        "desc": "athletic 20yo male, spiky blonde hair, blue eyes, whisker marks, tanned skin, muscular, confident smile",
        "negative": "low quality, blurry, deformed, ugly, child, minor, young"
    },
    "sasuke": {
        "desc": "athletic 20yo male, black spiky hair, dark eyes, pale skin, lean muscular, serious, sharp features",
        "negative": "low quality, blurry, deformed, ugly, child, minor, young"
    },
    "sakura": {
        "desc": "athletic 20yo female, pink hair, green eyes, fair skin, toned figure, delicate features, determined",
        "negative": "low quality, blurry, deformed, ugly, child, minor, young"
    },
    "hinata": {
        "desc": "elegant 20yo female, long indigo hair, lavender eyes, porcelain skin, graceful curves, shy gentle",
        "negative": "low quality, blurry, deformed, ugly, child, minor, young"
    },
    "kakashi": {
        "desc": "athletic 27yo male, silver hair, one visible eye, mask covering face, lean muscular, mysterious",
        "negative": "low quality, blurry, deformed, ugly, child, minor"
    },
    "itachi": {
        "desc": "elegant 21yo male, long black hair, dark eyes, pale skin, lean build, calm melancholic",
        "negative": "low quality, blurry, deformed, ugly, child, minor, young"
    },
    "bradpitt": {
        "desc": "handsome 60yo male actor, blonde hair, blue eyes, chiseled jawline, fit muscular, charismatic smile",
        "negative": "low quality, blurry, deformed, ugly, anime"
    },
    "leonardo": {
        "desc": "handsome 49yo male actor, light brown hair, blue eyes, mature features, fit build, intense gaze",
        "negative": "low quality, blurry, deformed, ugly, anime"
    },
    "therock": {
        "desc": "muscular 51yo male, bald head, brown eyes, tanned skin, massive build, charismatic smile, tattoos",
        "negative": "low quality, blurry, deformed, ugly, anime, small"
    },
    "scarlett": {
        "desc": "beautiful 39yo female actress, blonde hair, green eyes, fair skin, curvy figure, sensual confident",
        "negative": "low quality, blurry, deformed, ugly, anime, child"
    },
    "margot": {
        "desc": "beautiful 34yo female actress, blonde hair, blue eyes, fair skin, athletic curves, radiant smile",
        "negative": "low quality, blurry, deformed, ugly, anime, child"
    },
    "emma": {
        "desc": "elegant 34yo female actress, brown hair, brown eyes, fair skin, slender figure, intelligent refined",
        "negative": "low quality, blurry, deformed, ugly, anime, child"
    },
    "zendaya": {
        "desc": "stunning 28yo female, long curly hair, brown eyes, glowing skin, tall slender curves, confident modern",
        "negative": "low quality, blurry, deformed, ugly, anime, child"
    }
}

# 10 niveaux NSFW progressifs
NSFW_LEVELS = [
    "suggestive pose, flirty expression, stylish outfit",
    "seductive pose, bedroom eyes, revealing elegant dress",
    "intimate setting, sensual expression, lingerie visible",
    "artistic nude, tasteful composition, soft lighting",
    "explicit nude, full body, detailed anatomy, professional photography",
    "erotic pose, passionate expression, intimate moment",
    "adult content, explicit details, high quality",
    "nsfw explicit, adult scene, photorealistic",
    "hardcore nsfw, very explicit, ultra detailed",
    "extreme nsfw, maximum detail, hyper realistic"
]

def generate_image(char_name, char_data, level_idx, img_num):
    """Génère une image NSFW"""
    
    # Construire le prompt
    prompt = f"{char_data['desc']}, {NSFW_LEVELS[level_idx]}, photorealistic, 8k uhd, professional, masterpiece, adult 18+"
    
    # URL Pollination
    encoded_prompt = quote(prompt)
    url = f"{POLLINATION_BASE_URL}/{encoded_prompt}?width=512&height=768&nologo=true&enhance=true&seed={int(time.time())}"
    
    # Nom de fichier Android-compatible
    filename = f"{char_name}nsfw{img_num}.jpg"
    filepath = os.path.join(OUTPUT_DIR, filename)
    
    print(f"⏳ Génération {char_name} #{img_num} (niveau {level_idx+1}/10)...")
    
    # Télécharger avec retry
    for attempt in range(5):
        try:
            response = requests.get(url, timeout=60)
            if response.status_code == 200 and len(response.content) > 10000:
                with open(filepath, 'wb') as f:
                    f.write(response.content)
                print(f"✅ {filename} ({len(response.content)//1024}KB)")
                return True
            elif response.status_code == 429:
                print(f"⚠️  Rate limit, attente {20*(attempt+1)}s...")
                time.sleep(20 * (attempt + 1))
            else:
                print(f"❌ HTTP {response.status_code}, retry {attempt+1}/5...")
                time.sleep(10 * (attempt + 1))
        except Exception as e:
            print(f"❌ Erreur: {e}, retry {attempt+1}/5...")
            time.sleep(10 * (attempt + 1))
    
    print(f"❌ Échec après 5 tentatives: {filename}")
    return False

def main():
    print("="*60)
    print("🔞 GÉNÉRATION GALERIES NSFW POUR APK")
    print("="*60)
    print(f"📁 Destination: {OUTPUT_DIR}")
    print(f"📸 Total: 13 personnages × 10 images = 130 images")
    print(f"⏱️  Temps estimé: ~25 minutes")
    print("="*60)
    
    # Créer le dossier
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    total_success = 0
    total_fail = 0
    
    # Pour chaque personnage
    for char_name, char_data in CHARACTERS.items():
        print(f"\n{'='*60}")
        print(f"🎨 PERSONNAGE: {char_name.upper()}")
        print(f"{'='*60}")
        
        # Générer 10 images (une par niveau NSFW)
        for i in range(10):
            success = generate_image(char_name, char_data, i, i+1)
            if success:
                total_success += 1
            else:
                total_fail += 1
            
            # Délai anti-rate-limit
            time.sleep(DELAY)
    
    print(f"\n{'='*60}")
    print(f"✅ TERMINÉ!")
    print(f"{'='*60}")
    print(f"✅ Succès: {total_success}/130")
    print(f"❌ Échecs: {total_fail}/130")
    print(f"📁 Images dans: {OUTPUT_DIR}")
    print(f"{'='*60}")

if __name__ == "__main__":
    main()
