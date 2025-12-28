#!/usr/bin/env python3
"""
Injecte TOUTES les galleries dans Characters.kt en UNE SEULE passe
"""
import json
import re

# Charger les URLs
with open('gallery_urls.json', 'r') as f:
    galleries = json.load(f)

# Charger Characters.kt
with open('app/src/main/java/com/narutoai/chat/data/Characters.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Mapping ID Characters.kt -> ID JSON
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

# Pour chaque personnage SAUF naruto (déjà fait)
for kt_id, json_id in list(char_map.items())[1:]:
    print(f"Injecting gallery for {kt_id}...")
    
    urls = galleries[json_id]
    
    # Créer le code Kotlin
    gallery_lines = ['        gallery = listOf(']
    for i, url in enumerate(urls):
        comma = ',' if i < len(urls) - 1 else ''
        gallery_lines.append(f'            "{url}"{comma}')
    gallery_lines.append('        ),')
    gallery_code = '\n'.join(gallery_lines)
    
    # Pattern: cherche greetingMessage = "...", puis ajoute gallery AVANT systemPromptSFW
    pattern = rf'(val {kt_id} = Character\([\s\S]*?greetingMessage = """[^"]*?""",)\s*(systemPromptSFW = """)'
    
    def replacer(match):
        before_greeting = match.group(1)
        after = match.group(2)
        return f"{before_greeting}\n        \n{gallery_code}\n        \n        {after}"
    
    content = re.sub(pattern, replacer, content, count=1)

# Sauvegarder
with open('app/src/main/java/com/narutoai/chat/data/Characters.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("\n✅ TOUTES les galleries injectées!")
