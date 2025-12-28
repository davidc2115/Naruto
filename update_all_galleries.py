#!/usr/bin/env python3
"""Met à jour Characters.kt pour utiliser TOUTES les images locales"""
from pathlib import Path
import re

CHARACTERS = [
    'naruto', 'sasuke', 'sakura', 'kakashi', 'itachi', 'hinata',
    'leonardo', 'brad', 'margot', 'scarlett', 'emma', 'rock', 'zendaya'
]

characters_kt = Path('app/src/main/java/com/narutoai/chat/data/Characters.kt')
content = characters_kt.read_text()

for char in CHARACTERS:
    print(f"Traitement: {char}...")
    
    gallery_lines = []
    for i in range(1, 11):
        gallery_lines.append(f'            "drawable://{char}gallery{i}.jpg"')
    
    new_gallery_content = ',\n'.join(gallery_lines)
    pattern = r'gallery = listOf\(\s*\n(.*?)\n\s*\),'
    new_replacement = f'gallery = listOf(\n{new_gallery_content}\n        ),'
    content, count = re.subn(pattern, new_replacement, content, count=1, flags=re.DOTALL)
    
    print(f"  {'✅' if count > 0 else '⚠️'} {char} {'mis à jour' if count > 0 else 'non trouvé'}")

characters_kt.write_text(content)
print(f"\n✅ Characters.kt mis à jour!")
