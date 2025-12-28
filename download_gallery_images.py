#!/usr/bin/env python3
"""
Télécharge les 130 images et les intègre dans l'APK
"""
import json
import os
import time
import urllib.request
from pathlib import Path

# Charger les URLs
with open('gallery_urls.json', 'r') as f:
    galleries = json.load(f)

# Créer dossier pour les galleries
gallery_dir = Path('app/src/main/res/drawable-nodpi/gallery')
gallery_dir.mkdir(parents=True, exist_ok=True)

print("🖼️  Téléchargement des 130 images...")
print(f"📁 Destination: {gallery_dir}")

total = 0
for char_id, urls in galleries.items():
    print(f"\n📥 {char_id}: ", end='', flush=True)
    
    for i, url in enumerate(urls, 1):
        # Nom du fichier
        filename = f"{char_id}_{i}.jpg"
        filepath = gallery_dir / filename
        
        # Skip si déjà téléchargé
        if filepath.exists():
            print('.', end='', flush=True)
            total += 1
            continue
        
        # Télécharger avec retry
        for attempt in range(3):
            try:
                # Attendre 2s entre chaque téléchargement pour éviter 429
                if total > 0:
                    time.sleep(2)
                
                urllib.request.urlretrieve(url, filepath)
                print('✓', end='', flush=True)
                total += 1
                break
            except Exception as e:
                if attempt == 2:
                    print(f'\n❌ Erreur {filename}: {e}')
                else:
                    time.sleep(5)  # Attendre plus longtemps avant retry

print(f"\n\n✅ {total}/130 images téléchargées!")

# Créer un mapping Kotlin
print("\n📝 Génération du mapping Kotlin...")

mapping_lines = ["// Mapping des images de galerie\nval galleryImages = mapOf(\n"]

for char_id, urls in galleries.items():
    mapping_lines.append(f'    "{char_id}" to listOf(\n')
    for i in range(1, len(urls) + 1):
        filename = f"{char_id}_{i}"
        comma = ',' if i < len(urls) else ''
        mapping_lines.append(f'        R.drawable.gallery_{filename}{comma}\n')
    mapping_lines.append('    ),\n')

mapping_lines.append(')\n')

with open('gallery_mapping.kt', 'w') as f:
    f.writelines(mapping_lines)

print("✅ Mapping généré: gallery_mapping.kt")
print("\n🎉 Terminé! Les images sont prêtes pour l'APK")
