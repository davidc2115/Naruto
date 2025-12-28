#!/usr/bin/env python3
"""
Ajoute les galleryNSFW dans Characters.kt après génération des 195 images.
"""
from pathlib import Path

CHARACTERS_FILE = Path('app/src/main/java/com/narutoai/chat/data/Characters.kt')

# Les 13 personnages avec leurs clés
CHAR_KEYS = ['naruto', 'sasuke', 'sakura', 'kakashi', 'itachi', 'hinata', 
             'leonardo', 'brad', 'margot', 'scarlett', 'emma', 'rock', 'zendaya']

def generate_gallery_nsfw_list(char_key):
    """Génère la liste des 15 images NSFW pour un personnage."""
    lines = []
    lines.append("        galleryNSFW = listOf(")
    for i in range(1, 16):
        comma = "," if i < 15 else ""
        lines.append(f'            "drawable://{char_key}nsfw{i}.jpg"{comma}')
    lines.append("        ),")
    return "\n".join(lines)

# Lire le fichier
content = CHARACTERS_FILE.read_text(encoding='utf-8')

# Pour chaque personnage, ajouter galleryNSFW APRÈS gallery
for char_key in CHAR_KEYS:
    # Chercher la section gallery = listOf(...), pour ce personnage
    # On va chercher le pattern "id = "{char_key}"" puis trouver la ligne "gallery = listOf("
    # et insérer après la ligne "),\n"
    
    # Stratégie : chercher la fin de chaque bloc gallery et insérer galleryNSFW après
    # Pattern: on trouve "drawable://XXXgallery10.jpg"\n        ),
    # Et on ajoute juste après
    
    # Chercher la dernière ligne de gallery pour ce personnage
    pattern = f'            "drawable://{char_key}gallery10.jpg"\n        ),'
    
    if pattern in content:
        # Ajouter galleryNSFW juste après
        gallery_nsfw = generate_gallery_nsfw_list(char_key)
        replacement = f'{pattern}\n{gallery_nsfw}'
        content = content.replace(pattern, replacement, 1)
        print(f"✅ {char_key:10s} galleryNSFW ajoutée")
    else:
        print(f"❌ {char_key:10s} pattern gallery non trouvé")

# Écrire le fichier modifié
CHARACTERS_FILE.write_text(content, encoding='utf-8')
print(f"\n✅ Fichier mis à jour : {CHARACTERS_FILE}")
print("🔄 Vérifiez que toutes les galleryNSFW sont ajoutées")
