#!/usr/bin/env python3
"""
Mettre à jour Characters.kt pour utiliser les vraies vignettes JPG
au lieu des XML vectoriels
"""
import re
from pathlib import Path

# Mapping nom variable -> nom fichier JPG
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

file_path = Path('app/src/main/java/com/narutoai/chat/data/Characters.kt')
content = file_path.read_text()

print("🔧 Mise à jour des imageResId vers les vraies vignettes JPG")
print("=" * 60)

changes = 0
for var_name, jpg_name in CHARACTER_MAP.items():
    # Chercher l'ancien imageResId (R.drawable.xxx)
    old_pattern = rf'(val {var_name} = Character\([^)]*?imageResId = )R\.drawable\.\w+'
    new_value = f'\\1R.drawable.{jpg_name}'
    
    new_content, count = re.subn(old_pattern, new_value, content, count=1)
    
    if count > 0:
        print(f"✅ {var_name:15s} -> R.drawable.{jpg_name}")
        content = new_content
        changes += count
    else:
        print(f"⚠️  {var_name:15s} - Pas de changement trouvé")

file_path.write_text(content)

print("=" * 60)
print(f"✅ {changes} imageResId mis à jour")
print(f"📄 Fichier: {file_path}")
