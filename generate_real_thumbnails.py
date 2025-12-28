#!/usr/bin/env python3
"""
Génère les 13 vignettes avec fal.ai (Flux Pro, gratuit et rapide)
Basé sur les descriptions physiques EXACTES de Characters.kt
"""
import requests
import json
import time
from pathlib import Path

# Prompts basés sur physicalDescription de Characters.kt
CHARACTERS = {
    'naruto': {
        'prompt': 'close-up portrait, Naruto Uzumaki, 17 year old male, spiky bright golden blonde hair sticking up like hedgehog spikes, vivid ocean blue eyes full of determination, six thin whisker marks on cheeks (3 per cheek like cat whiskers), athletic muscular but lean build, confident smile, orange and black ninja jacket with high collar, metal headband with leaf symbol on forehead, anime art style, manga style, vibrant colors, best quality, masterpiece',
        'style': 'anime'
    },
    'sasuke': {
        'prompt': 'close-up portrait, Sasuke Uchiha, 17 year old male, spiky jet black hair swept backwards, very pale porcelain skin, deep black eyes with intense cold stare, aristocratic sharp facial features, serious brooding expression, dark blue high-collar shirt, metal headband, anime art style, dramatic shading, best quality, masterpiece',
        'style': 'anime'
    },
    'sakura': {
        'prompt': 'close-up portrait, Sakura Haruno, 17 year old female, long bright pink hair in high ponytail, vibrant green emerald eyes, fair delicate skin, athletic feminine build, determined confident expression, red and black combat outfit, anime art style, manga style, vibrant colors, best quality, masterpiece',
        'style': 'anime'
    },
    'kakashi': {
        'prompt': 'close-up portrait, Kakashi Hatake, 26 year old male, spiky silver-gray hair defying gravity, left eye covered by metal headband tilted, right eye visible dark, black mask covering lower face from nose down, relaxed mysterious expression, dark blue ninja vest, anime art style, cool aesthetic, best quality, masterpiece',
        'style': 'anime'
    },
    'hinata': {
        'prompt': 'close-up portrait, Hinata Hyuga, 17 year old female, very long silky dark blue-black hair reaching hips, pale lavender-white pearl eyes, extremely pale delicate porcelain skin, shy gentle expression with blushing pink cheeks, lavender and beige traditional outfit, anime art style, soft romantic aesthetic, best quality, masterpiece',
        'style': 'anime'
    },
    'itachi': {
        'prompt': 'close-up portrait, Itachi Uchiha, 21 year old male, long jet black hair in low ponytail, stress lines under eyes, very pale sickly skin, deep black eyes with sorrowful gaze, melancholic sad expression, black cloak with red clouds, metal headband with slash mark, anime art style, dark dramatic, best quality, masterpiece',
        'style': 'anime'
    },
    'brad': {
        'prompt': 'professional headshot portrait, Brad Pitt at age 60, golden blonde hair with distinguished gray streaks, piercing bright blue eyes, chiseled square jawline, sun-kissed tan skin, charming confident smile with wrinkles showing maturity, photorealistic, 8k uhd, sharp focus, professional photography',
        'style': 'realistic'
    },
    'leo': {
        'prompt': 'professional headshot portrait, Leonardo DiCaprio at age 49, golden blonde hair styled back, intense piercing blue eyes, mature masculine handsome features, passionate determined expression, elegant dark suit, photorealistic, 8k uhd, sharp focus, professional photography',
        'style': 'realistic'
    },
    'rock': {
        'prompt': 'professional headshot portrait, Dwayne The Rock Johnson at age 51, completely bald shaved head, brown eyes with iconic raised eyebrow, massive muscular build, large Polynesian tribal tattoo visible on left arm, tan Samoan skin, huge charismatic smile, photorealistic, 8k uhd, sharp focus, professional photography',
        'style': 'realistic'
    },
    'scarlett': {
        'prompt': 'professional headshot portrait, Scarlett Johansson at age 39, platinum blonde wavy hair, magnetic green eyes, full sensual lips, fair luminous glowing skin, athletic feminine curves, confident sensual expression, elegant black dress, photorealistic, 8k uhd, sharp focus, professional photography',
        'style': 'realistic'
    },
    'margot': {
        'prompt': 'professional headshot portrait, Margot Robbie at age 34, golden blonde wavy hair, sparkling bright blue eyes, radiant huge cheerful smile, sun-kissed Australian tan skin, perfect beautiful features, fun energetic joyful vibe, photorealistic, 8k uhd, sharp focus, professional photography',
        'style': 'realistic'
    },
    'emma': {
        'prompt': 'professional headshot portrait, Emma Watson at age 34, short elegant brown pixie cut hair, expressive intelligent brown eyes, fair British skin, refined elegant sophisticated features, poised graceful posture, sophisticated minimalist black outfit, photorealistic, 8k uhd, sharp focus, professional photography',
        'style': 'realistic'
    },
    'zendaya': {
        'prompt': 'professional headshot portrait, Zendaya at age 28, long curly voluminous brown hair, hazel expressive striking eyes, caramel glowing radiant skin, tall elegant model slender build, confident modern fierce expression, high fashion outfit, photorealistic, 8k uhd, sharp focus, professional photography',
        'style': 'realistic'
    }
}

# fal.ai API (gratuit, pas de clé nécessaire pour quelques requêtes)
FAL_API_URL = "https://fal.run/fal-ai/flux/schnell"

def generate_image_fal(prompt, style='anime'):
    """Génère une image avec fal.ai Flux Schnell (rapide et gratuit)"""
    payload = {
        "prompt": prompt,
        "image_size": "portrait_4_3",
        "num_inference_steps": 4,  # Schnell = ultra rapide
        "num_images": 1,
        "enable_safety_checker": False
    }
    
    try:
        response = requests.post(FAL_API_URL, json=payload, timeout=30)
        response.raise_for_status()
        data = response.json()
        
        if 'images' in data and len(data['images']) > 0:
            return data['images'][0]['url']
        return None
    except Exception as e:
        print(f"  ❌ Erreur: {e}")
        return None

print("🎨 Génération des 13 vignettes avec fal.ai Flux Schnell...")
print("⚡ Rapide et GRATUIT!\n")

output_dir = Path('app/src/main/res/drawable-nodpi')
output_dir.mkdir(parents=True, exist_ok=True)

results = {}

for char_id, data in CHARACTERS.items():
    print(f"📥 {char_id}...", end='', flush=True)
    
    # Générer l'image
    image_url = generate_image_fal(data['prompt'], data['style'])
    
    if image_url:
        # Télécharger
        try:
            time.sleep(2)  # Éviter rate limit
            img_response = requests.get(image_url, timeout=15)
            img_response.raise_for_status()
            
            # Sauvegarder
            filename = f"{char_id}.png"
            filepath = output_dir / filename
            filepath.write_bytes(img_response.content)
            
            print(f" ✅ ({len(img_response.content) // 1024} KB)")
            results[char_id] = 'success'
        except Exception as e:
            print(f" ❌ Download failed: {e}")
            results[char_id] = 'failed'
    else:
        print(" ❌ Generation failed")
        results[char_id] = 'failed'
    
    time.sleep(3)  # Pause entre requêtes

print(f"\n✅ Génération terminée!")
print(f"Succès: {list(results.values()).count('success')}/13")
print(f"Échecs: {list(results.values()).count('failed')}/13")
