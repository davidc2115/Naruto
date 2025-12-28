#!/usr/bin/env python3
"""
Injecte TOUTES les galleries dans Characters.kt - VERSION 3 (gère guillemets échappés)
"""
import json
import re

# Charger les URLs
with open('gallery_urls.json', 'r') as f:
    galleries = json.load(f)

# Charger Characters.kt
with open('app/src/main/java/com/narutoai/chat/data/Characters.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Mapping ID
char_map = {
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

# Pour chaque personnage
for kt_id, json_id in char_map.items():
    print(f"Injecting gallery for {kt_id}...")
    
    urls = galleries[json_id]
    
    # Créer le code Kotlin
    gallery_lines = ['\n        gallery = listOf(']
    for i, url in enumerate(urls):
        comma = ',' if i < len(urls) - 1 else ''
        gallery_lines.append(f'            "{url}"{comma}')
    gallery_lines.append('        ),\n        ')
    gallery_code = '\n'.join(gallery_lines)
    
    # Pattern amélioré : accepte guillemets échappés dans greetingMessage
    # [^"]* devient (?:[^"\\]|\\.)*  pour accepter \"
    pattern = rf'(greetingMessage = "(?:[^"\\]|\\.)*",)\s*(systemPromptSFW = """)'
    
    def replacer(match):
        before = match.group(1)
        after = match.group(2)
        return f"{before}{gallery_code}{after}"
    
    # Chercher le nombre d'occurrences
    matches = list(re.finditer(pattern, content, re.DOTALL))
    if len(matches) == 0:
        print(f"  ⚠️  Pattern not found for {kt_id}")
        continue
    
    content = re.sub(pattern, replacer, content, count=1, flags=re.DOTALL)
    print(f"  ✅ Injected!")

# Sauvegarder
with open('app/src/main/java/com/narutoai/chat/data/Characters.kt', 'w', encoding='utf-8') as f:
    f.write(content)

# Vérifier
count = len(re.findall(r'gallery = listOf\(', content))
print(f"\n✅ Total galleries dans Characters.kt: {count}/13")
