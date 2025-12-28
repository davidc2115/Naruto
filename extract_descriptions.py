#!/usr/bin/env python3
import re
import json

with open('app/src/main/java/com/narutoai/chat/data/Characters.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Noms CORRECTS des variables dans Characters.kt
characters = {
    'naruto': 'naruto',
    'sasuke': 'sasuke', 
    'sakura': 'sakura',
    'kakashi': 'kakashi',
    'itachi': 'itachi',
    'hinata': 'hinata',
    'leonardo': 'leoDiCaprio',
    'brad': 'bradPitt',
    'margot': 'margot',
    'scarlett': 'scarlett',
    'emma': 'emma',
    'rock': 'theRock',
    'zendaya': 'zendaya'
}

descriptions = {}

for display_name, var_name in characters.items():
    pattern = rf'val {var_name} = Character\(.*?physicalDescription = """(.*?)"""'
    match = re.search(pattern, content, re.DOTALL)
    
    if match:
        desc = match.group(1).strip()
        descriptions[display_name] = desc
        print(f"✓ {display_name:12s} ({var_name:12s}): {desc[:60]}...")
    else:
        print(f"✗ {display_name:12s} ({var_name:12s}): NOT FOUND")

with open('character_descriptions.json', 'w', encoding='utf-8') as f:
    json.dump(descriptions, f, indent=2, ensure_ascii=False)

print(f"\n✅ {len(descriptions)}/13 descriptions extraites → character_descriptions.json")
