#!/usr/bin/env python3
"""
Génération de TOUTES les images (13 vignettes + 130 galeries)
avec l'API Hugging Face Inference (gratuite)
"""
import json
import time
import urllib.request
import urllib.parse
import os
from pathlib import Path

# Charger les descriptions
with open('character_descriptions.json', 'r', encoding='utf-8') as f:
    descriptions = json.load(f)

# Répertoire de sortie
output_dir = Path('app/src/main/res/drawable-nodpi')
output_dir.mkdir(parents=True, exist_ok=True)

# API Hugging Face - modèle Stable Diffusion XL (gratuit)
API_URL = "https://api-inference.huggingface.co/models/stabilityai/stable-diffusion-xl-base-1.0"

def generate_image(prompt, output_path, delay_after=5):
    """Génère une image via Hugging Face API"""
    try:
        # Préparer la requête
        headers = {
            'Content-Type': 'application/json',
        }
        
        data = json.dumps({
            "inputs": prompt,
            "parameters": {
                "negative_prompt": "blurry, low quality, distorted, deformed, ugly, bad anatomy",
                "num_inference_steps": 30,
                "guidance_scale": 7.5
            }
        }).encode('utf-8')
        
        # Envoyer requête
        req = urllib.request.Request(API_URL, data=data, headers=headers)
        
        print(f"  🔄 Génération: {output_path.name}...", end=" ", flush=True)
        
        with urllib.request.urlopen(req, timeout=120) as response:
            if response.status == 200:
                image_data = response.read()
                
                # Sauvegarder
                with open(output_path, 'wb') as f:
                    f.write(image_data)
                
                size_kb = len(image_data) / 1024
                print(f"✅ ({size_kb:.0f} KB)")
                
                # Délai anti-rate-limit
                time.sleep(delay_after)
                return True
            else:
                print(f"❌ HTTP {response.status}")
                return False
                
    except Exception as e:
        print(f"❌ {str(e)[:50]}")
        return False

def create_thumbnail_prompt(name, description):
    """Créer un prompt optimisé pour une vignette"""
    return f"professional portrait photo, {description}, centered composition, studio lighting, high quality, detailed, 8k resolution, photorealistic"

def create_gallery_prompt(name, description, variation):
    """Créer un prompt de galerie avec variation"""
    variations = [
        "close-up portrait",
        "side profile view", 
        "three-quarter view",
        "smiling expression",
        "serious expression",
        "casual pose",
        "professional pose",
        "outdoor setting",
        "indoor setting",
        "dramatic lighting"
    ]
    
    var_text = variations[variation % len(variations)]
    return f"{var_text}, {description}, high quality, detailed, 8k resolution, photorealistic"

# GÉNÉRATION DES 13 VIGNETTES
print("\n" + "="*70)
print("📸 GÉNÉRATION DES 13 VIGNETTES")
print("="*70)

thumbnail_count = 0
for char_name in descriptions.keys():
    description = descriptions[char_name]
    output_path = output_dir / f"{char_name}.png"
    
    print(f"\n[{thumbnail_count + 1}/13] {char_name.upper()}")
    
    prompt = create_thumbnail_prompt(char_name, description)
    
    if generate_image(prompt, output_path, delay_after=8):
        thumbnail_count += 1
    else:
        print(f"  ⚠️  Échec, réessai dans 10s...")
        time.sleep(10)
        if generate_image(prompt, output_path, delay_after=8):
            thumbnail_count += 1

print(f"\n✅ Vignettes: {thumbnail_count}/13 générées")

# GÉNÉRATION DES 130 IMAGES DE GALERIE (10 par personnage)
print("\n" + "="*70)
print("🖼️  GÉNÉRATION DES 130 IMAGES DE GALERIE")
print("="*70)

gallery_count = 0
total_gallery = len(descriptions) * 10

for char_idx, char_name in enumerate(descriptions.keys()):
    description = descriptions[char_name]
    
    print(f"\n[Personnage {char_idx + 1}/13] {char_name.upper()} - 10 images")
    
    for i in range(1, 11):
        output_path = output_dir / f"{char_name}_gallery_{i}.jpg"
        
        prompt = create_gallery_prompt(char_name, description, i)
        
        if generate_image(prompt, output_path, delay_after=10):
            gallery_count += 1
        else:
            print(f"  ⚠️  Échec image {i}, réessai...")
            time.sleep(15)
            if generate_image(prompt, output_path, delay_after=10):
                gallery_count += 1

print("\n" + "="*70)
print(f"✅ GÉNÉRATION TERMINÉE")
print(f"  📸 Vignettes: {thumbnail_count}/13")
print(f"  🖼️  Galeries: {gallery_count}/130")
print(f"  📦 Total: {thumbnail_count + gallery_count}/143")
print("="*70)
