#!/usr/bin/env python3
"""
Génération OPTIMISÉE de 143 images avec Pollinations AI
- Téléchargement série (pas parallèle) pour éviter 429/502
- Retry automatique sur échec
- Optimisation images (compression)
- Progression détaillée
"""
import json
import time
import urllib.request
import urllib.parse
from pathlib import Path
from PIL import Image
import io

# Charger descriptions
with open('character_descriptions.json', 'r', encoding='utf-8') as f:
    descriptions = json.load(f)

output_dir = Path('app/src/main/res/drawable-nodpi')
output_dir.mkdir(parents=True, exist_ok=True)

BASE_URL = "https://image.pollinations.ai/prompt"

def optimize_image(image_data, max_size_kb=150):
    """Optimise une image (compression JPEG)"""
    try:
        img = Image.open(io.BytesIO(image_data))
        
        # Convertir en RGB si nécessaire
        if img.mode in ('RGBA', 'LA', 'P'):
            background = Image.new('RGB', img.size, (255, 255, 255))
            if img.mode == 'P':
                img = img.convert('RGBA')
            background.paste(img, mask=img.split()[-1] if img.mode in ('RGBA', 'LA') else None)
            img = background
        
        # Compression progressive
        output = io.BytesIO()
        quality = 85
        
        while quality > 20:
            output.seek(0)
            output.truncate()
            img.save(output, format='JPEG', quality=quality, optimize=True)
            size_kb = len(output.getvalue()) / 1024
            
            if size_kb <= max_size_kb or quality <= 25:
                break
            quality -= 5
        
        return output.getvalue()
    except Exception as e:
        print(f"    ⚠️  Optim failed: {e}, using original")
        return image_data

def download_image(url, output_path, max_retries=3):
    """Télécharge une image avec retry"""
    for attempt in range(max_retries):
        try:
            # Attendre AVANT la requête
            if attempt > 0:
                wait = 15 * attempt
                print(f"    🔄 Retry {attempt + 1}/{max_retries} dans {wait}s...", end=" ", flush=True)
                time.sleep(wait)
            
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            
            with urllib.request.urlopen(req, timeout=60) as response:
                if response.status == 200:
                    image_data = response.read()
                    
                    # Optimiser
                    optimized = optimize_image(image_data)
                    
                    # Sauvegarder
                    with open(output_path, 'wb') as f:
                        f.write(optimized)
                    
                    size_kb = len(optimized) / 1024
                    return True, size_kb
                else:
                    print(f"❌ HTTP {response.status}")
                    
        except urllib.error.HTTPError as e:
            if e.code == 429:
                print(f"❌ 429 Rate Limit")
            elif e.code == 502:
                print(f"❌ 502 Bad Gateway")
            else:
                print(f"❌ HTTP {e.code}")
        except Exception as e:
            print(f"❌ {str(e)[:40]}")
    
    return False, 0

def create_thumbnail_prompt(char_name, desc):
    """Prompt pour vignette (portrait centré)"""
    # Extraire les détails clés
    prompt = f"professional headshot portrait, {desc}, centered face, studio lighting, photorealistic, 8k, highly detailed"
    return urllib.parse.quote(prompt)

def create_gallery_prompt(char_name, desc, idx):
    """Prompt pour galerie (variations)"""
    variations = [
        "close-up portrait, looking at camera",
        "side profile, dramatic lighting",
        "three-quarter view, confident expression",
        "smiling warmly, natural light",
        "serious expression, professional pose",
        "casual pose, outdoor setting",
        "dynamic pose, action shot",
        "contemplative expression, soft lighting",
        "candid moment, natural pose",
        "artistic portrait, creative angle"
    ]
    
    var = variations[idx % len(variations)]
    prompt = f"{var}, {desc}, photorealistic, high quality, detailed, 8k resolution"
    return urllib.parse.quote(prompt)

# GÉNÉRATION
print("\n" + "="*80)
print("🎨 GÉNÉRATION DE 143 IMAGES HYPER-RÉALISTES")
print("="*80)

stats = {"thumbnails": 0, "gallery": 0, "failed": 0}

# 1. VIGNETTES (13)
print("\n📸 ÉTAPE 1/2: VIGNETTES (13 images)")
print("-" * 80)

for idx, (char_name, desc) in enumerate(descriptions.items(), 1):
    print(f"\n[{idx}/13] {char_name.upper():<12s} → {char_name}.png")
    
    prompt = create_thumbnail_prompt(char_name, desc)
    url = f"{BASE_URL}/{prompt}?width=512&height=768&model=flux&enhance=true&nologo=true&seed={int(time.time() * 1000)}"
    output = output_dir / f"{char_name}.png"
    
    success, size = download_image(url, output, max_retries=3)
    
    if success:
        print(f"  ✅ Sauvegardé ({size:.0f} KB)")
        stats["thumbnails"] += 1
    else:
        print(f"  ❌ ÉCHEC après 3 tentatives")
        stats["failed"] += 1
    
    # Délai entre images
    time.sleep(12)

# 2. GALERIES (130)
print("\n\n🖼️  ÉTAPE 2/2: GALERIES (130 images, 10 par personnage)")
print("-" * 80)

for char_idx, (char_name, desc) in enumerate(descriptions.items(), 1):
    print(f"\n{'='*80}")
    print(f"Personnage {char_idx}/13: {char_name.upper()} - 10 images")
    print("=" * 80)
    
    for img_idx in range(1, 11):
        print(f"  [{img_idx}/10] {char_name}_gallery_{img_idx}.jpg ... ", end="", flush=True)
        
        prompt = create_gallery_prompt(char_name, desc, img_idx)
        url = f"{BASE_URL}/{prompt}?width=768&height=1024&model=flux&enhance=true&nologo=true&seed={int(time.time() * 1000) + img_idx}"
        output = output_dir / f"{char_name}_gallery_{img_idx}.jpg"
        
        success, size = download_image(url, output, max_retries=3)
        
        if success:
            print(f"✅ ({size:.0f} KB)")
            stats["gallery"] += 1
        else:
            print(f"❌ ÉCHEC")
            stats["failed"] += 1
        
        # Délai entre images
        time.sleep(15)
    
    print(f"\n  ✓ {char_name}: {stats['gallery'] % 10 if stats['gallery'] % 10 else 10}/10 terminé")

# RÉSUMÉ
print("\n" + "="*80)
print("✅ GÉNÉRATION TERMINÉE")
print("="*80)
print(f"📸 Vignettes:   {stats['thumbnails']}/13")
print(f"🖼️  Galeries:    {stats['gallery']}/130")
print(f"✅ Succès:      {stats['thumbnails'] + stats['gallery']}/143")
print(f"❌ Échecs:      {stats['failed']}")
print(f"📊 Taux succès: {((stats['thumbnails'] + stats['gallery']) / 143 * 100):.1f}%")
print("="*80)

# Vérifier fichiers
files = list(output_dir.glob('*.png')) + list(output_dir.glob('*.jpg'))
total_size_mb = sum(f.stat().st_size for f in files) / (1024 * 1024)
print(f"\n📦 Taille totale: {total_size_mb:.2f} MB ({len(files)} fichiers)")
