#!/usr/bin/env python3
"""
Injecte toutes les galleries dans Characters.kt automatiquement
"""

import re
import json

# Charger les URLs générées
with open('gallery_urls.json', 'r') as f:
    all_urls = json.load(f)

# Lire Characters.kt
with open('app/src/main/java/com/narutoai/chat/data/Characters.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Mapping des IDs de Characters.kt vers nos IDs générés
char_map = {
    'naruto': 'naruto',
    'sasuke': 'sasuke',
    'sakura': 'sakura',
    'kakashi': 'kakashi',
    'hinata': 'hinata',
    'itachi': 'itachi',
    'bradPitt': 'bradPitt',
    'leoDiCaprio': 'leoDiCaprio',
    'theRock': 'theRock',
    'scarlett': 'scarlett',
    'margot': 'margot',
    'emma': 'emma',
    'zendaya': 'zendaya'
}

# Pour chaque personnage (sauf naruto déjà fait)
for char_kt, char_json in list(char_map.items())[1:]:  # Skip naruto
    print(f"📝 Injection gallery pour {char_kt}...")
    
    urls = all_urls[char_json]
    
    # Construire la gallery Kotlin
    gallery_lines = ['        gallery = listOf(']
    for i, url in enumerate(urls):
        comma = ',' if i < len(urls) - 1 else ''
        gallery_lines.append(f'            "{url}"{comma}')
    gallery_lines.append('        ),')
    gallery_code = '\n'.join(gallery_lines)
    
    # Pattern pour trouver l'insertion (après greetingMessage mais avant systemPromptSFW)
    # On cherche: greetingMessage = "..."
    #             <-- insertion ICI
    #             systemPromptSFW = """..."""
    
    pattern = rf'(val {char_kt} = Character\([^)]*greetingMessage = """[^"]*""",\s*)\n(\s*systemPromptSFW = """)'
    
    def replacer(match):
        before = match.group(1)
        after = match.group(2)
        return f"{before}\n{gallery_code}\n        \n{after}"
    
    content = re.sub(pattern, replacer, content, count=1, flags=re.DOTALL)

# Sauvegarder
with open('app/src/main/java/com/narutoai/chat/data/Characters.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("\n✅ Toutes les galleries ont été injectées dans Characters.kt!")
