#!/usr/bin/env python3
"""
Modifie Characters.kt pour utiliser les images LOCALES disponibles
"""
import os
from pathlib import Path

# Lister les images gallery disponibles
gallery_dir = Path('app/src/main/res/drawable-nodpi')
gallery_images = {}

for img_file in sorted(gallery_dir.glob('*_gallery_*.jpg')):
    # Extraire char_id et index
    name = img_file.stem  # ex: naruto_gallery_1
    parts = name.split('_gallery_')
    if len(parts) == 2:
        char_id = parts[0]
        idx = parts[1]
        
        if char_id not in gallery_images:
            gallery_images[char_id] = []
        gallery_images[char_id].append(idx)

print("📁 Images gallery locales trouvées:")
for char_id, indices in sorted(gallery_images.items()):
    print(f"  {char_id}: {len(indices)} images")

# Générer le code Kotlin pour les galleries LOCALES
print("\n📝 Génération du code Kotlin...")

for char_id in ['naruto', 'sasuke', 'sakura', 'kakashi', 'hinata', 'itachi']:
    if char_id in gallery_images and len(gallery_images[char_id]) > 0:
        print(f"\n// Gallery locale pour {char_id}:")
        print(f"gallery = listOf(")
        for idx in sorted(gallery_images[char_id]):
            resource_name = f"{char_id}_gallery_{idx}"
            print(f'    "drawable://{resource_name}",')
        print("),")
    else:
        print(f"\n// Pas d'images locales pour {char_id}, garder les URLs")

print("\n✅ Terminé!")
print("\n💡 Solution finale:")
print("  1. Garder les URLs Pollination dans gallery (AsyncImage les charge)")
print("  2. Les images locales sont des BONUS pour ceux qui se téléchargent")
print("  3. L'app fonctionne même sans images locales (charge depuis URLs)")
