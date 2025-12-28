#!/usr/bin/env python3
"""
Script de génération de VRAIES images ressemblantes pour chaque personnage
Télécharge les images réellement générées par Pollination AI
"""

import requests
import json
import time
import os
from pathlib import Path

# Créer dossier pour images
OUTPUT_DIR = "character_images"
Path(OUTPUT_DIR).mkdir(exist_ok=True)

# Prompts ULTRA-DÉTAILLÉS pour images ressemblantes
CHARACTERS_DETAILED = {
    "Naruto": {
        "full_prompt": "close-up portrait photo of a young 17 year old Japanese male ninja, spiky bright golden blonde hair sticking up in all directions like hedgehog spikes, vivid ocean blue eyes with determination, six thin whisker marks on cheeks (3 per cheek like cat whiskers), tan skin, wide confident smile showing white teeth, orange and black ninja jacket with high collar, metal headband with leaf symbol on forehead, energetic happy expression, golden hour lighting, professional photography, 8k, hyper realistic",
        "count": 1  # Une vraie image de test
    },
    
    "Sasuke": {
        "full_prompt": "close-up portrait photo of a young 17 year old Japanese male ninja, spiky jet black hair swept backwards, very pale porcelain white skin, deep black eyes with intense cold stare (can add red Sharingan eyes with tomoe pattern), aristocratic facial features, serious expression, dark blue high-collar shirt, metal headband, cold and intimidating aura, dramatic side lighting, professional photography, 8k, hyper realistic",
        "count": 1
    },
    
    "Sakura": {
        "full_prompt": "close-up portrait photo of a young 17 year old Japanese female ninja, long bright pink hair in high ponytail, bright green emerald eyes, fair skin, athletic feminine build, determined confident expression, red and black combat outfit, medical ninja gear, strong and beautiful, natural lighting, professional photography, 8k, hyper realistic",
        "count": 1
    },
    
    "Kakashi": {
        "full_prompt": "close-up portrait photo of a 26 year old Japanese male ninja, spiky silver-gray hair defying gravity, one eye visible (right eye), left eye covered by metal headband, black mask covering lower half of face from nose down, relaxed mysterious expression with eye smiling, ninja vest, holding orange book, cool and laid-back aura, soft lighting, professional photography, 8k, hyper realistic",
        "count": 1
    },
    
    "Hinata": {
        "full_prompt": "close-up portrait photo of a young 17 year old Japanese female ninja, very long silky dark blue-black hair reaching hips, pale lavender eyes (or white pearl Byakugan eyes with veins), extremely pale delicate skin, shy gentle expression with blushing cheeks, lavender and beige traditional outfit, timid and sweet demeanor, soft romantic lighting, professional photography, 8k, hyper realistic",
        "count": 1
    },
    
    "Itachi": {
        "full_prompt": "close-up portrait photo of a 21 year old Japanese male, long jet black hair in low ponytail, stress lines under eyes, very pale sickly skin, deep black eyes (or red Mangekyō Sharingan with unique pattern), melancholic sad expression, black cloak with red clouds (Akatsuki), metal headband with slash mark, tragic and sorrowful aura, moonlight dramatic lighting, professional photography, 8k, hyper realistic",
        "count": 1
    },
    
    "Brad_Pitt": {
        "full_prompt": "close-up portrait photo of Brad Pitt at age 60, golden blonde hair with gray streaks, piercing blue eyes, chiseled square jawline, sun-kissed tan skin, charming smile, wrinkles showing maturity, casual stylish clothing, Hollywood star presence, confident and warm expression, natural daylight, professional celebrity photography, 8k, hyper realistic",
        "count": 1
    },
    
    "Leonardo_DiCaprio": {
        "full_prompt": "close-up portrait photo of Leonardo DiCaprio at age 49, golden blonde hair, intense blue eyes, masculine mature features, passionate expression, elegant suit, environmental activist vibe, mature Hollywood leading man, determined gaze, studio portrait lighting, professional celebrity photography, 8k, hyper realistic",
        "count": 1
    },
    
    "Dwayne_The_Rock_Johnson": {
        "full_prompt": "close-up portrait photo of Dwayne The Rock Johnson at age 51, completely bald shaved head, brown eyes with raised eyebrow, massive muscular build, large Polynesian tribal tattoo visible on left arm and shoulder, tan Samoan skin, huge charismatic smile, athletic wear showing muscles, intimidating yet friendly presence, bright energetic lighting, professional celebrity photography, 8k, hyper realistic",
        "count": 1
    },
    
    "Scarlett_Johansson": {
        "full_prompt": "close-up portrait photo of Scarlett Johansson at age 39, platinum blonde wavy hair, green magnetic eyes, full sensual lips, fair luminous skin, athletic feminine curves, confident sensual expression, elegant black dress, Hollywood glamour, powerful and beautiful, studio glamour lighting, professional celebrity photography, 8k, hyper realistic",
        "count": 1
    },
    
    "Margot_Robbie": {
        "full_prompt": "close-up portrait photo of Margot Robbie at age 34, golden blonde wavy hair, sparkling blue eyes, radiant huge smile, sun-kissed Australian tan skin, perfect beautiful features, fun energetic vibe, stylish modern outfit, Barbie-like beauty, joyful and charismatic, bright cheerful lighting, professional celebrity photography, 8k, hyper realistic",
        "count": 1
    },
    
    "Emma_Watson": {
        "full_prompt": "close-up portrait photo of Emma Watson at age 34, short elegant brown hair styled pixie cut, expressive intelligent brown eyes, fair British skin, refined elegant features, poised graceful posture, sophisticated minimalist black outfit, feminist activist aura, Hermione grown up, gentle warm expression, natural soft lighting, professional celebrity photography, 8k, hyper realistic",
        "count": 1
    },
    
    "Zendaya": {
        "full_prompt": "close-up portrait photo of Zendaya at age 28, long curly brown hair, hazel expressive eyes, caramel glowing skin, tall elegant model build, confident modern expression, avant-garde high fashion outfit, Gen Z icon, stunning mixed-race features, fierce model gaze, high fashion lighting, professional celebrity photography, 8k, hyper realistic",
        "count": 1
    }
}


def generate_and_download_image(character_name, prompt, index=1):
    """
    Génère et télécharge une vraie image depuis Pollination AI
    """
    # URL Pollination AI
    url = f"https://image.pollinations.ai/prompt/{requests.utils.quote(prompt)}?width=768&height=1024&nologo=true&model=flux&enhance=true"
    
    print(f"  📸 Génération image {index} pour {character_name}...")
    print(f"     Prompt: {prompt[:100]}...")
    
    try:
        # Télécharger l'image
        response = requests.get(url, timeout=60)
        response.raise_for_status()
        
        # Sauvegarder l'image
        filename = f"{OUTPUT_DIR}/{character_name}_{index}.png"
        with open(filename, 'wb') as f:
            f.write(response.content)
        
        file_size = os.path.getsize(filename) / 1024  # KB
        print(f"  ✅ Image sauvegardée: {filename} ({file_size:.1f} KB)")
        
        return filename, url
        
    except Exception as e:
        print(f"  ❌ Erreur: {e}")
        return None, url


def main():
    print("""
    ╔═══════════════════════════════════════════════════════════════╗
    ║   Générateur d'Images RÉELLES Ressemblantes v2             ║
    ║   Télécharge de vraies images depuis Pollination AI         ║
    ╚═══════════════════════════════════════════════════════════════╝
    """)
    
    results = {}
    total_images = 0
    
    for char_name, char_data in CHARACTERS_DETAILED.items():
        print(f"\n🎨 {char_name}")
        print("=" * 70)
        
        images = []
        for i in range(1, char_data["count"] + 1):
            filename, url = generate_and_download_image(
                char_name,
                char_data["full_prompt"],
                i
            )
            
            if filename:
                images.append({
                    "filename": filename,
                    "url": url
                })
                total_images += 1
            
            # Délai entre requêtes
            time.sleep(2)
        
        results[char_name] = {
            "prompt": char_data["full_prompt"],
            "images": images
        }
    
    # Sauvegarder les métadonnées
    with open("generated_images_real.json", 'w', encoding='utf-8') as f:
        json.dump(results, f, indent=2, ensure_ascii=False)
    
    print("\n" + "=" * 70)
    print(f"📊 RÉSUMÉ:")
    print(f"   - Personnages: {len(results)}")
    print(f"   - Images téléchargées: {total_images}")
    print(f"   - Dossier: {OUTPUT_DIR}/")
    print(f"   - Métadonnées: generated_images_real.json")
    print("=" * 70)
    
    print("\n✅ Génération terminée!")
    print("\n💡 Prochaines étapes:")
    print("   1. Vérifie les images dans le dossier character_images/")
    print("   2. Les images ressemblent-elles aux personnages?")
    print("   3. Si oui, on peut en générer 10 par personnage")
    print("   4. Puis intégrer dans l'app Android\n")


if __name__ == "__main__":
    main()
