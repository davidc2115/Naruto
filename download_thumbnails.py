#!/usr/bin/env python3
"""
Télécharge UNE image par personnage pour les vignettes ressemblantes
"""
import json
import os
import time
import urllib.request
from pathlib import Path

# Charger les URLs
with open('gallery_urls.json', 'r') as f:
    galleries = json.load(f)

# Créer dossier pour les vignettes
thumb_dir = Path('app/src/main/res/drawable-nodpi')
thumb_dir.mkdir(parents=True, exist_ok=True)

print("🖼️  Téléchargement de 13 vignettes (une par personnage)...")

char_names = {
    'naruto': 'naruto',
    'sasuke': 'sasuke', 
    'sakura': 'sakura',
    'kakashi': 'kakashi',
    'hinata': 'hinata',
    'itachi': 'itachi',
    'bradPitt': 'brad',
    'leoDiCaprio': 'leo',
    'theRock': 'rock',
    'scarlett': 'scarlett',
    'margot': 'margot',
    'emma': 'emma',
    'zendaya': 'zendaya'
}

total = 0
for char_id, short_name in char_names.items():
    filename = f"{short_name}.png"
    filepath = thumb_dir / filename
    
    # Skip si déjà existe
    if filepath.exists():
        print(f"✓ {short_name} (déjà présent)")
        total += 1
        continue
    
    # Prendre la première URL
    url = galleries[char_id][0]
    
    print(f"📥 {short_name}...", end='', flush=True)
    
    # Télécharger avec retry
    for attempt in range(3):
        try:
            if total > 0:
                time.sleep(3)  # 3s entre chaque pour éviter 429
            
            urllib.request.urlretrieve(url, filepath)
            print(" ✅")
            total += 1
            break
        except Exception as e:
            if attempt == 2:
                print(f" ❌ Erreur: {e}")
            else:
                print(f" ⏳ Retry {attempt+1}...", end='', flush=True)
                time.sleep(5)

print(f"\n✅ {total}/13 vignettes téléchargées!")
print("📝 Maintenant il faut mettre à jour Characters.kt pour utiliser ces images")
