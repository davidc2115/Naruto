#!/usr/bin/env python3
"""
Télécharge les 130 images EN PARALLÈLE (plus rapide) et optimisées
"""
import json
import os
import time
import urllib.request
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor, as_completed
from PIL import Image
import io

# Charger les URLs
with open('gallery_urls.json', 'r') as f:
    galleries = json.load(f)

# Créer dossier
gallery_dir = Path('app/src/main/res/drawable-nodpi')
gallery_dir.mkdir(parents=True, exist_ok=True)

def download_and_optimize(char_id, idx, url):
    """Télécharge et optimise une image"""
    filename = f"{char_id}_gallery_{idx}.jpg"
    filepath = gallery_dir / filename
    
    # Skip si existe
    if filepath.exists() and filepath.stat().st_size > 10000:  # >10KB
        return (char_id, idx, True, "exists")
    
    try:
        # Télécharger
        response = urllib.request.urlopen(url, timeout=30)
        img_data = response.read()
        
        # Optimiser avec PIL (réduire taille)
        img = Image.open(io.BytesIO(img_data))
        
        # Redimensionner si trop grand (max 800x1200)
        if img.width > 800 or img.height > 1200:
            img.thumbnail((800, 1200), Image.Resampling.LANCZOS)
        
        # Convertir en RGB si nécessaire
        if img.mode != 'RGB':
            img = img.convert('RGB')
        
        # Sauvegarder en JPEG optimisé (qualité 85)
        img.save(filepath, 'JPEG', quality=85, optimize=True)
        
        return (char_id, idx, True, f"{filepath.stat().st_size // 1024}KB")
    except Exception as e:
        return (char_id, idx, False, str(e)[:50])

print("🖼️  Téléchargement et optimisation des 130 images...")
print("⚡ En parallèle pour accélérer...\n")

# Préparer la liste de tâches
tasks = []
for char_id, urls in galleries.items():
    for idx, url in enumerate(urls, 1):
        tasks.append((char_id, idx, url))

# Télécharger en parallèle (5 threads pour ne pas surcharger)
success = 0
failed = 0

with ThreadPoolExecutor(max_workers=5) as executor:
    futures = {executor.submit(download_and_optimize, *task): task for task in tasks}
    
    for future in as_completed(futures):
        char_id, idx, ok, msg = future.result()
        
        if ok:
            success += 1
            print(f"✓ {char_id}_{idx} ({msg})")
        else:
            failed += 1
            print(f"✗ {char_id}_{idx}: {msg}")
        
        # Petit délai pour éviter 429
        time.sleep(0.2)

print(f"\n✅ {success}/130 images téléchargées")
if failed > 0:
    print(f"❌ {failed} échecs")

# Calculer taille totale
total_size = sum(f.stat().st_size for f in gallery_dir.glob("*_gallery_*.jpg"))
print(f"📦 Taille totale: {total_size / (1024*1024):.1f} MB")
