#!/usr/bin/env python3
"""
Script pour générer les galeries NSFW pour tous les personnages Naruto AI Chat
Utilise Pollination AI pour générer 6 images NSFW par personnage
"""

import os
import time
import re
import requests
from urllib.parse import quote

# Configuration
POLLINATION_BASE_URL = "https://image.pollinations.ai/prompt"
OUTPUT_DIR = "character_images"
IMAGES_PER_CHARACTER = 3  # Réduit à 3 pour génération rapide
DELAY_BETWEEN_REQUESTS = 2  # secondes

# Définition des personnages avec leurs descriptions physiques
CHARACTERS = {
    "naruto": {
        "name": "Naruto Uzumaki",
        "gender": "male",
        "physical": "Young ninja man, 18-22 years old, spiky blonde hair, piercing ocean blue eyes, three whisker marks on each cheek (fox demon heritage), athletic muscular but lean physique, confident posture despite cheerful character, Konoha headband on forehead, orange and black jacket, bright contagious smile, training scars on hands"
    },
    "sasuke": {
        "name": "Sasuke Uchiha", 
        "gender": "male",
        "physical": "Young ninja man, 18-22 years old, jet black hair with bangs framing face, dark onyx eyes (Sharingan when activated), pale skin, slender athletic build, handsome angular features, cold piercing gaze, Uchiha clan symbol on back, dark blue/purple outfit, brooding mysterious aura"
    },
    "sakura": {
        "name": "Sakura Haruno",
        "gender": "female", 
        "physical": "Young kunoichi woman, 18-22 years old, bright pink hair (shoulder-length to long), vibrant green eyes, fair porcelain skin, athletic feminine figure with curves, delicate beautiful features, Konoha headband worn as hairband, red dress with white circular symbol, confident determined expression, medical ninja training scars"
    },
    "hinata": {
        "name": "Hinata Hyuga",
        "gender": "female",
        "physical": "Young kunoichi woman, 18-22 years old, long dark indigo/purple hair, pale lavender/white eyes (Byakugan), very fair porcelain skin, soft gentle face, curvaceous hourglass figure with large breasts, shy demeanor, elegant posture, purple and cream outfit with Hyuga clan symbol, gentle caring expression"
    },
    "ino": {
        "name": "Ino Yamanaka",
        "gender": "female",
        "physical": "Young kunoichi woman, 18-22 years old, long platinum blonde hair in ponytail, bright blue-green eyes, fair skin, slender athletic figure with feminine curves, confident beautiful face, purple outfit with bandages, flirty confident expression, flower shop girl aesthetic"
    },
    "temari": {
        "name": "Temari",
        "gender": "female",
        "physical": "Young kunoichi woman, 19-23 years old, blonde hair in four high ponytails, teal/green eyes, fair skin with slight tan, tall athletic figure, sharp intelligent features, confident smirk, purple battle outfit, giant fan weapon, Suna headband, assertive dominant aura"
    },
    "tsunade": {
        "name": "Tsunade Senju",
        "gender": "female",
        "physical": "Mature woman, appears 30s (actually 50s), long blonde hair in two ponytails, light brown eyes, fair smooth skin (chakra preservation), voluptuous hourglass figure with very large breasts, beautiful mature features, green haori jacket with kanji 賭, red diamond mark on forehead (Strength of a Hundred Seal), commanding presence"
    },
    "tenten": {
        "name": "Tenten",
        "gender": "female",
        "physical": "Young kunoichi woman, 18-22 years old, dark brown hair in two buns, brown eyes, fair skin, athletic toned figure, cute determined face, Chinese-inspired outfit with pink sleeves, weapons mistress aesthetic, energetic focused expression"
    },
    "konan": {
        "name": "Konan",
        "gender": "female",
        "physical": "Young woman, 23-27 years old, short blue-grey hair with paper flower ornament, amber eyes, pale fair skin, slender athletic figure with curves, beautiful serene features, Akatsuki cloak with red clouds, paper manipulation aesthetic, calm mysterious expression, lip piercing"
    },
    "kurenai": {
        "name": "Kurenai Yuhi",
        "gender": "female",
        "physical": "Mature woman, late 20s-early 30s, long black curly hair, crimson red eyes, fair skin, curvaceous athletic figure, beautiful elegant features, red and white outfit with bandages, genjutsu specialist aesthetic, confident sensual expression"
    },
    "anko": {
        "name": "Anko Mitarashi",
        "gender": "female",
        "physical": "Young woman, mid-late 20s, short purple hair, light brown eyes, fair skin with light tan, curvy athletic figure, wild playful features, revealing mesh outfit under tan trench coat, snake aesthetic (Orochimaru student), seductive mischievous grin"
    },
    "kushina": {
        "name": "Kushina Uzumaki",
        "gender": "female",
        "physical": "Young woman, 20s-30s, long vibrant red hair, violet eyes, fair skin, athletic hourglass figure with curves, beautiful strong features, green dress, Uzumaki clan spiral symbol, fiery passionate expression, motherly warmth mixed with warrior spirit"
    },
    "mikoto": {
        "name": "Mikoto Uchiha",
        "gender": "female",
        "physical": "Mature woman, 30s, long straight black hair, dark onyx eyes (Sharingan capable), pale skin, graceful elegant figure, beautiful gentle features, traditional Japanese outfit, Uchiha clan aesthetic, warm motherly smile, refined dignified presence"
    }
}

def create_nsfw_prompt(character_name, physical_description, variation_index):
    """Crée un prompt NSFW varié pour un personnage"""
    
    base_nsfw_keywords = "explicit NSFW 18+ adult content, nudity, sensual, erotic, intimate"
    
    # Variations de poses/scènes pour diversité
    variations = [
        "seductive pose, bedroom eyes, intimate setting, soft lighting",
        "sensual naked body, artistic nude, elegant pose, studio lighting",
        "provocative position, revealing lingerie, boudoir photography",
        "erotic art, passionate expression, intimate moment, dramatic lighting",
        "adult content, sensual scene, bedroom setting, romantic atmosphere",
        "explicit nude art, seductive expression, artistic photography, professional quality"
    ]
    
    prompt = f"{character_name}, {physical_description}, {base_nsfw_keywords}, {variations[variation_index]}, highly detailed, photorealistic, 8k quality, professional photography, masterpiece"
    
    return prompt

def generate_image_url(prompt, width=512, height=768, seed=None):
    """Génère l'URL Pollination AI pour un prompt"""
    if seed is None:
        seed = int(time.time() * 1000)
    
    encoded_prompt = quote(prompt)
    url = f"{POLLINATION_BASE_URL}/{encoded_prompt}?width={width}&height={height}&seed={seed}&nologo=true&enhance=true"
    return url

def download_image(url, output_path):
    """Télécharge une image depuis une URL"""
    try:
        print(f"  📥 Téléchargement: {output_path}")
        response = requests.get(url, timeout=60)
        response.raise_for_status()
        
        with open(output_path, 'wb') as f:
            f.write(response.content)
        
        file_size = len(response.content) / 1024  # KB
        print(f"  ✅ Sauvegardé: {file_size:.1f} KB")
        return True
    except Exception as e:
        print(f"  ❌ Erreur: {e}")
        return False

def generate_nsfw_gallery_for_character(char_id, char_data):
    """Génère une galerie NSFW complète pour un personnage"""
    print(f"\n{'='*60}")
    print(f"🎨 Génération galerie NSFW pour: {char_data['name']}")
    print(f"{'='*60}")
    
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    success_count = 0
    
    for i in range(1, IMAGES_PER_CHARACTER + 1):
        print(f"\n📸 Image {i}/{IMAGES_PER_CHARACTER}")
        
        # Créer le prompt
        prompt = create_nsfw_prompt(
            char_data['name'],
            char_data['physical'],
            i - 1
        )
        
        print(f"  📝 Prompt: {prompt[:100]}...")
        
        # Générer l'URL
        image_url = generate_image_url(prompt)
        
        # Nom du fichier
        filename = f"{char_id}nsfw{i}.png"
        output_path = os.path.join(OUTPUT_DIR, filename)
        
        # Télécharger
        if download_image(image_url, output_path):
            success_count += 1
        
        # Délai pour éviter rate limit
        if i < IMAGES_PER_CHARACTER:
            print(f"  ⏳ Attente de {DELAY_BETWEEN_REQUESTS}s...")
            time.sleep(DELAY_BETWEEN_REQUESTS)
    
    print(f"\n✅ Terminé pour {char_data['name']}: {success_count}/{IMAGES_PER_CHARACTER} images générées")
    return success_count

def main():
    """Fonction principale"""
    print("╔════════════════════════════════════════════════════════════╗")
    print("║  🎨 Générateur de Galeries NSFW - Naruto AI Chat         ║")
    print("╚════════════════════════════════════════════════════════════╝")
    print(f"\n📊 Configuration:")
    print(f"  - Personnages: {len(CHARACTERS)}")
    print(f"  - Images par personnage: {IMAGES_PER_CHARACTER}")
    print(f"  - Total à générer: {len(CHARACTERS) * IMAGES_PER_CHARACTER} images")
    print(f"  - Dossier de sortie: {OUTPUT_DIR}")
    print(f"  - Délai entre requêtes: {DELAY_BETWEEN_REQUESTS}s")
    
    print("\n🚀 Démarrage de la génération...")
    
    total_success = 0
    start_time = time.time()
    
    for char_id, char_data in CHARACTERS.items():
        success = generate_nsfw_gallery_for_character(char_id, char_data)
        total_success += success
        
        # Pause plus longue entre personnages
        print(f"\n⏳ Pause de 5 secondes avant le prochain personnage...")
        time.sleep(5)
    
    elapsed_time = time.time() - start_time
    
    print("\n" + "="*60)
    print("🎉 GÉNÉRATION TERMINÉE !")
    print("="*60)
    print(f"✅ Images générées: {total_success}/{len(CHARACTERS) * IMAGES_PER_CHARACTER}")
    print(f"⏱️  Temps total: {elapsed_time/60:.1f} minutes")
    print(f"📁 Images sauvegardées dans: {os.path.abspath(OUTPUT_DIR)}")
    print("\n💡 Prochaine étape: Copier les images dans app/src/main/res/drawable/")

if __name__ == "__main__":
    main()
