#!/usr/bin/env python3
"""
Script de génération batch de galeries NSFW pour personnages Naruto
Utilise Pollination AI (gratuit, rapide, NSFW)
"""

import requests
import time
from pathlib import Path

# Configuration
OUTPUT_DIR = Path("/workspace/character_images")
OUTPUT_DIR.mkdir(exist_ok=True)

# Personnages et leurs descriptions pour génération
CHARACTERS = {
    "naruto": {
        "name": "Naruto Uzumaki",
        "description": "young adult male ninja, spiky blonde hair, blue eyes, tan skin, athletic muscular build, orange outfit",
        "count": 5
    },
    "ino": {
        "name": "Ino Yamanaka",
        "description": "young adult female ninja, long platinum blonde ponytail hair, light blue eyes, fair skin, slim athletic figure, purple outfit",
        "count": 5
    },
    "temari": {
        "name": "Temari",
        "description": "young adult female ninja, blonde hair in four ponytails, teal eyes, fair skin, athletic figure, black outfit",
        "count": 5
    },
    "tsunade": {
        "name": "Tsunade",
        "description": "mature adult female ninja, blonde hair in low ponytails, brown eyes, fair skin, voluptuous curvy figure, green outfit",
        "count": 5
    },
    "sasuke": {
        "name": "Sasuke Uchiha",
        "description": "young adult male ninja, black spiky hair, dark eyes, pale skin, lean muscular build, dark blue outfit",
        "count": 5
    }
}

def generate_nsfw_image(character_key: str, character_data: dict, index: int) -> str:
    """
    Génère une image NSFW pour un personnage
    """
    prompt = f"{character_data['description']}, {character_data['name']}, explicit nudity, NSFW, erotic, sensual pose, detailed, high quality, anime style"
    
    url = f"https://image.pollinations.ai/prompt/{requests.utils.quote(prompt)}"
    
    params = {
        "width": 512,
        "height": 512,
        "seed": int(time.time() * 1000) + index,  # Seed unique
        "nologo": "true",
        "enhance": "true"
    }
    
    print(f"  🎨 Génération {character_key}_{index}...")
    print(f"     Prompt: {prompt[:80]}...")
    
    try:
        response = requests.get(url, params=params, timeout=60)
        
        if response.status_code == 200:
            output_path = OUTPUT_DIR / f"{character_key}nsfw{index}.png"
            
            with open(output_path, 'wb') as f:
                f.write(response.content)
            
            print(f"  ✅ Sauvegardé: {output_path.name} ({len(response.content) // 1024}KB)")
            return str(output_path)
        else:
            print(f"  ❌ Erreur HTTP {response.status_code}")
            return None
            
    except Exception as e:
        print(f"  ❌ Exception: {e}")
        return None

def main():
    """
    Génère toutes les galeries NSFW
    """
    print("🎨 GÉNÉRATION GALERIES NSFW - BATCH")
    print("=" * 60)
    
    total_images = sum(char["count"] for char in CHARACTERS.values())
    generated = 0
    failed = 0
    
    print(f"📊 {len(CHARACTERS)} personnages × ~5 images = ~{total_images} images")
    print(f"⏱️  Estimation: ~{total_images * 8} secondes (~{total_images * 8 // 60} min)")
    print()
    
    for char_key, char_data in CHARACTERS.items():
        print(f"\n🎭 {char_data['name']}")
        print(f"   Description: {char_data['description']}")
        print(f"   Objectif: {char_data['count']} images NSFW")
        print()
        
        for i in range(1, char_data['count'] + 1):
            result = generate_nsfw_image(char_key, char_data, i)
            
            if result:
                generated += 1
            else:
                failed += 1
            
            # Pause entre images pour éviter rate limiting
            if i < char_data['count']:
                time.sleep(3)
        
        # Pause entre personnages
        if char_key != list(CHARACTERS.keys())[-1]:
            print(f"\n   ⏸️  Pause 5s avant personnage suivant...")
            time.sleep(5)
    
    print("\n" + "=" * 60)
    print(f"✅ TERMINÉ !")
    print(f"   Générées: {generated}/{total_images}")
    print(f"   Échouées: {failed}")
    print(f"   Dossier: {OUTPUT_DIR}")
    print()
    
    if generated > 0:
        print("📤 Prochaines étapes:")
        print("   1. Vérifier les images générées")
        print("   2. Upload vers Freebox (scp)")
        print("   3. Mettre à jour Characters.kt")

if __name__ == "__main__":
    main()
