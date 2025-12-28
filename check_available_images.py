#!/usr/bin/env python3
"""
Mettre à jour Characters.kt avec les images locales
- Vignettes: drawable://nom_fichier
- Galeries: mix local + URLs Pollinations AI
"""
import re
from pathlib import Path

# Lire Characters.kt
chars_path = Path('app/src/main/java/com/narutoai/chat/data/Characters.kt')
content = chars_path.read_text(encoding='utf-8')

# Images disponibles
drawable_dir = Path('app/src/main/res/drawable-nodpi')
available_images = {f.stem: f.suffix for f in drawable_dir.glob('*') if f.suffix in ['.png', '.jpg']}

print(f"📦 {len(available_images)} images trouvées")

# Mapping noms Kotlin → noms fichiers
char_map = {
    'naruto': 'naruto',
    'sasuke': 'sasuke', 
    'sakura': 'sakura',
    'kakashi': 'kakashi',
    'itachi': 'itachi',
    'hinata': 'hinata',
    'leoDiCaprio': 'leonardo',
    'bradPitt': 'brad',
    'margot': 'margot',
    'scarlett': 'scarlett',
    'emma': 'emma',
    'theRock': 'rock',
    'zendaya': 'zendaya'
}

# Pour chaque personnage, mettre à jour gallery avec images locales
for kotlin_name, file_prefix in char_map.items():
    # Trouver les images de galerie pour ce personnage
    gallery_images = []
    for i in range(1, 11):
        img_name = f"{file_prefix}_gallery_{i}"
        if img_name in available_images:
            ext = available_images[img_name]
            # Utiliser drawable:// pour images locales
            gallery_images.append(f"drawable://{file_prefix}_gallery_{i}{ext}")
    
    if gallery_images:
        print(f"✓ {kotlin_name:15s}: {len(gallery_images)} images locales")
    else:
        print(f"- {kotlin_name:15s}: 0 images locales (garder URLs)")

print(f"\n📝 NOTE: On garde les URLs Pollinations AI pour les images manquantes")
print(f"   → Chargement dynamique via Coil AsyncImage")
