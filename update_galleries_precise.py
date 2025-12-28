#!/usr/bin/env python3
"""Met à jour Characters.kt en identifiant PRÉCISÉMENT chaque personnage"""
from pathlib import Path
import re

# Correspondance NOM VARIABLE → nom fichier
CHARACTER_MAP = {
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

characters_kt = Path('app/src/main/java/com/narutoai/chat/data/Characters.kt')
content = characters_kt.read_text()

for var_name, file_prefix in CHARACTER_MAP.items():
    print(f"Traitement: {var_name} -> {file_prefix}...")
    
    # Trouver le bloc du personnage (val {var_name} = Character)
    pattern_char = rf'(val {var_name} = Character\([^)]*?\n.*?)(gallery = listOf\(\s*\n)(.*?)(\n\s*\),)'
    
    # Créer la nouvelle liste de galerie
    gallery_lines = []
    for i in range(1, 11):
        gallery_lines.append(f'            "drawable://{file_prefix}gallery{i}.jpg"')
    new_gallery_content = ',\n'.join(gallery_lines)
    
    def replacer(match):
        return match.group(1) + match.group(2) + new_gallery_content + match.group(4)
    
    new_content = re.sub(pattern_char, replacer, content, count=1, flags=re.DOTALL)
    
    if new_content != content:
        print(f"  ✅ {var_name} mis à jour")
        content = new_content
    else:
        print(f"  ⚠️ {var_name} non trouvé")

characters_kt.write_text(content)
print(f"\n✅ Characters.kt mis à jour correctement!")
