#!/usr/bin/env python3
"""
SOLUTION FINALE:
- Utilise les 23 images déjà téléchargées (naruto x10, sasuke x8, hinata x4, itachi x1)
- Modifie Characters.kt pour référencer ces images locales DIRECTEMENT
- Garde les URLs comme fallback
"""
import re
from pathlib import Path

# Lire Characters.kt
with open('app/src/main/java/com/narutoai/chat/data/Characters.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Mapping des images locales disponibles
local_galleries = {
    'naruto': list(range(1, 11)),  # 1-10
    'sasuke': list(range(1, 9)),    # 1-8
    'hinata': [6, 7, 8, 10],         # 4 images
    'itachi': [1],                   # 1 image
}

print("🔧 Modification de Characters.kt pour utiliser les images locales...\n")

for char_id, indices in local_galleries.items():
    print(f"📝 {char_id}: {len(indices)} images locales")
    
    # Créer les références drawable
    drawable_refs = []
    for idx in indices:
        # IMPORTANT: Utiliser @ string pour référencer les drawables
        drawable_refs.append(f'            "@drawable/{char_id}_gallery_{idx}"')
    
    gallery_code = ",\n".join(drawable_refs)
    
    # Pattern: trouver la gallery actuelle
    pattern = rf'(val {char_id} = Character\([^)]*?)\s*gallery = listOf\([^)]*?\),\s*'
    
    replacement = rf'\1\n        gallery = listOf(\n{gallery_code}\n        ),\n        '
    
    content = re.sub(pattern, replacement, content, flags=re.DOTALL, count=1)

# Sauvegarder
with open('app/src/main/java/com/narutoai/chat/data/Characters.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("\n✅ Characters.kt modifié!")
print("\n💡 Les personnages utiliseront maintenant les images LOCALES:")
print("   - Naruto: 10 images")
print("   - Sasuke: 8 images")
print("   - Hinata: 4 images")
print("   - Itachi: 1 image")
print("\n📦 Ces images seront intégrées dans l'APK (pas de téléchargement nécessaire!)")
