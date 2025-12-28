#!/usr/bin/env python3
"""
Générateur de galeries NSFW hyper-réalistes pour Naruto AI Chat
Utilise l'API Pollinations.ai avec des prompts détaillés basés sur les descriptions physiques
13 personnages × 15 images = 195 images NSFW totales

ATTENTION: Ce script génère du contenu NSFW (18+)
"""

import requests
import time
import json
from urllib.parse import quote

# Configuration
POLLINATION_BASE_URL = "https://image.pollinations.ai/prompt"
OUTPUT_DIR = "/workspace/character_images"
DELAY_BETWEEN_IMAGES = 12  # 12 secondes entre chaque image

# Personnages avec descriptions physiques hyper-détaillées
CHARACTERS = {
    "Naruto": {
        "description": "athletic 20-year-old male with spiky blonde hair, ocean blue eyes, whisker marks on cheeks, tanned skin, lean muscular build, confident smile, ninja headband",
        "gender": "male",
        "style": "photorealistic anime character"
    },
    "Sasuke": {
        "description": "athletic 20-year-old male with jet black spiky hair, dark intense eyes, pale skin, lean muscular build, serious expression, sharp jawline",
        "gender": "male",
        "style": "photorealistic anime character"
    },
    "Sakura": {
        "description": "athletic 20-year-old female with pink shoulder-length hair, bright green eyes, fair porcelain skin, toned feminine figure, delicate features, determined expression",
        "gender": "female",
        "style": "photorealistic anime character"
    },
    "Hinata": {
        "description": "elegant 20-year-old female with long indigo-blue hair, lavender pearl eyes, porcelain skin, graceful feminine curves, shy gentle expression, refined features",
        "gender": "female",
        "style": "photorealistic anime character"
    },
    "Ino": {
        "description": "attractive 20-year-old female with long platinum blonde hair in ponytail, light blue eyes, fair skin, slender curvy figure, confident smile, elegant posture",
        "gender": "female",
        "style": "photorealistic anime character"
    },
    "Tsunade": {
        "description": "voluptuous mature woman 55 years (looks 30), long blonde hair in twin tails, honey-brown eyes, fair skin, legendary hourglass figure, commanding presence, beauty mark under lip",
        "gender": "female",
        "style": "photorealistic anime character"
    },
    "Kushina": {
        "description": "beautiful mature woman 30s with long red hair, violet eyes, fair skin, curvy feminine figure, warm motherly smile, elegant features",
        "gender": "female",
        "style": "photorealistic anime character"
    },
    "Temari": {
        "description": "athletic 21-year-old female with blonde hair in four ponytails, teal eyes, tanned skin, strong toned figure, confident fierce expression",
        "gender": "female",
        "style": "photorealistic anime character"
    },
    "TenTen": {
        "description": "athletic 20-year-old female with brown hair in twin buns, dark eyes, fair skin, fit toned figure, determined warrior expression",
        "gender": "female",
        "style": "photorealistic anime character"
    },
    "Konan": {
        "description": "elegant 30-year-old female with blue hair in paper flower, amber eyes, pale skin, slender graceful figure, serene mysterious expression, piercing on lip",
        "gender": "female",
        "style": "photorealistic anime character"
    },
    "Mei": {
        "description": "beautiful mature woman 35 with long auburn-red hair, green eyes, fair skin, voluptuous hourglass figure, seductive smile, elegant features",
        "gender": "female",
        "style": "photorealistic anime character"
    },
    "Anko": {
        "description": "wild 30-year-old female with short spiky purple hair, dark eyes, fair skin, curvy athletic figure, mischievous playful expression",
        "gender": "female",
        "style": "photorealistic anime character"
    },
    "Kaguya": {
        "description": "ethereal goddess-like woman with long white hair, pale lavender eyes with Byakugan, porcelain white skin, perfect hourglass figure, otherworldly beauty, regal expression",
        "gender": "female",
        "style": "photorealistic anime goddess"
    }
}

# Variations NSFW progressives (15 niveaux)
NSFW_PROMPTS = [
    # Niveau 1-3: Suggestif
    "seductive pose, bedroom eyes, intimate lighting, sultry expression, adult 18+",
    "alluring pose, sensual gaze, romantic atmosphere, teasing expression, adult 18+",
    "provocative pose, flirtatious smile, dim lighting, suggestive clothing, adult 18+",
    
    # Niveau 4-6: Léger
    "sensual pose, revealing outfit, soft lighting, confident expression, adult 18+ content",
    "intimate pose, elegant lingerie, romantic setting, seductive look, adult 18+ content",
    "alluring pose, partially undressed, atmospheric lighting, sultry expression, adult 18+ content",
    
    # Niveau 7-9: Modéré
    "erotic pose, minimal clothing, intimate setting, passionate expression, explicit adult 18+ content",
    "sensual nude pose, artistic lighting, bedroom scene, seductive gaze, explicit adult 18+ content",
    "intimate nude pose, romantic atmosphere, sensual expression, explicit adult 18+ content",
    
    # Niveau 10-12: Explicite
    "explicit erotic pose, full nudity, intimate bedroom, passionate expression, explicit adult 18+ nsfw content",
    "erotic full nude pose, sensual lighting, intimate setting, seductive gaze, explicit adult 18+ nsfw content",
    "explicit nude pose, provocative angle, bedroom scene, lustful expression, explicit adult 18+ nsfw content",
    
    # Niveau 13-15: Très explicite
    "explicit erotic scene, full nudity, intimate act suggestion, passionate expression, explicit adult 18+ nsfw content, photorealistic",
    "highly explicit erotic pose, full nude body, intimate bedroom, intense expression, explicit adult 18+ nsfw content, hyperrealistic",
    "extreme explicit erotic scene, full nudity, provocative intimate pose, passionate gaze, explicit adult 18+ nsfw content, ultra realistic"
]

def generate_nsfw_prompt(character_name, character_desc, nsfw_variation):
    """Génère un prompt NSFW hyper-détaillé"""
    return f"""NSFW explicit adult content 18+, {character_desc}, {nsfw_variation}, 
photorealistic, hyperdetailed, professional photography, 8k uhd, sharp focus, 
cinematic lighting, anatomically correct, perfect anatomy, beautiful detailed face, 
detailed eyes, detailed skin texture, masterpiece quality, adult content, 
mature content 18+, realistic proportions, high resolution"""

def download_image(url, filepath, max_retries=3):
    """Télécharge une image avec retry"""
    for attempt in range(max_retries):
        try:
            print(f"  Tentative {attempt + 1}/{max_retries}...")
            response = requests.get(url, timeout=120)
            
            if response.status_code == 200:
                # Vérifier que c'est bien une image
                content_type = response.headers.get('content-type', '')
                if 'image' in content_type:
                    with open(filepath, 'wb') as f:
                        f.write(response.content)
                    print(f"  ✓ OK ({len(response.content) // 1024}KB)")
                    return True
                else:
                    print(f"  ⚠️ Pas une image: {content_type}")
            elif response.status_code in [500, 502, 503]:
                print(f"  ⚠️ Erreur serveur {response.status_code}, retry dans 20s...")
                time.sleep(20)
            else:
                print(f"  ❌ HTTP {response.status_code}")
                
        except Exception as e:
            print(f"  ❌ Erreur: {e}")
            if attempt < max_retries - 1:
                time.sleep(10)
    
    return False

def generate_character_nsfw_gallery(character_name, character_info):
    """Génère 15 images NSFW pour un personnage"""
    print(f"\n{'='*60}")
    print(f"📸 GÉNÉRATION GALERIE NSFW: {character_name}")
    print(f"{'='*60}")
    
    success_count = 0
    
    for i, nsfw_variation in enumerate(NSFW_PROMPTS, 1):
        print(f"\n[{character_name}] Image {i}/15 - Niveau {(i-1)//3 + 1}/5")
        
        # Générer prompt
        prompt = generate_nsfw_prompt(character_name, character_info["description"], nsfw_variation)
        encoded_prompt = quote(prompt)
        
        # URL Pollinations
        image_url = f"{POLLINATION_BASE_URL}/{encoded_prompt}"
        image_url += f"?width=768&height=1024&model=flux&nologo=true&enhance=true&seed={int(time.time())}"
        
        # Nom fichier
        filename = f"{OUTPUT_DIR}/{character_name.lower()}_nsfw_{i:02d}.jpg"
        
        print(f"  Prompt: {nsfw_variation[:60]}...")
        print(f"  URL: {image_url[:80]}...")
        
        # Télécharger
        if download_image(image_url, filename):
            success_count += 1
        
        # Pause anti-rate-limit
        if i < len(NSFW_PROMPTS):
            print(f"  ⏳ Pause {DELAY_BETWEEN_IMAGES}s...")
            time.sleep(DELAY_BETWEEN_IMAGES)
    
    print(f"\n✅ {character_name}: {success_count}/15 images générées")
    return success_count

def main():
    print("="*60)
    print("🔞 GÉNÉRATEUR GALERIES NSFW - NARUTO AI CHAT")
    print("="*60)
    print(f"\n📦 {len(CHARACTERS)} personnages × 15 images = {len(CHARACTERS) * 15} images NSFW")
    print(f"⏱️  Temps estimé: {len(CHARACTERS) * 15 * DELAY_BETWEEN_IMAGES / 60:.0f} minutes")
    print(f"📁 Dossier: {OUTPUT_DIR}")
    print("\n⚠️  ATTENTION: Contenu NSFW explicite 18+")
    print("\nDébut dans 5 secondes...")
    time.sleep(5)
    
    total_success = 0
    total_images = 0
    
    start_time = time.time()
    
    for char_name, char_info in CHARACTERS.items():
        success = generate_character_nsfw_gallery(char_name, char_info)
        total_success += success
        total_images += 15
        
        print(f"\n⏸️  Pause 30s avant personnage suivant...")
        time.sleep(30)
    
    elapsed = time.time() - start_time
    
    print("\n" + "="*60)
    print("📊 RÉSUMÉ FINAL")
    print("="*60)
    print(f"✅ Images générées: {total_success}/{total_images}")
    print(f"⏱️  Temps total: {elapsed/60:.1f} minutes")
    print(f"📁 Dossier: {OUTPUT_DIR}")
    print("\n🎉 Génération terminée !")

if __name__ == "__main__":
    main()
