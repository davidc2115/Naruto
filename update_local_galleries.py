#!/usr/bin/env python3
import re
from pathlib import Path

chars_file = Path('app/src/main/java/com/narutoai/chat/data/Characters.kt')
content = chars_file.read_text(encoding='utf-8')

# Patterns pour trouver les galeries (entre "gallery = listOf(" et ")," suivant)
def replace_gallery(content, char_name, local_images):
    # Trouver la section du personnage
    char_start = content.find(f'val {char_name} = Character(')
    if char_start == -1:
        print(f"❌ {char_name} not found")
        return content
    
    # Trouver gallery = listOf( après le char_start
    gallery_start = content.find('gallery = listOf(', char_start)
    next_char = content.find('val ', char_start + 10)  # Prochain personnage
    
    # Vérifier que gallery appartient bien à ce personnage
    if gallery_start == -1 or (next_char != -1 and gallery_start > next_char):
        print(f"❌ {char_name} gallery not found")
        return content
    
    # Trouver la fin de la liste (le ")," suivant)
    list_start = gallery_start + len('gallery = listOf(')
    depth = 1
    i = list_start
    while i < len(content) and depth > 0:
        if content[i] == '(':
            depth += 1
        elif content[i] == ')':
            depth -= 1
        i += 1
    
    if depth != 0:
        print(f"❌ {char_name} gallery end not found")
        return content
    
    list_end = i  # Position après le ')'
    
    # Construire la nouvelle galerie
    new_gallery = '        gallery = listOf(\n' + '\n'.join([f'            "drawable://{img}",' for img in local_images]) + '\n        ),'
    
    # Remplacer
    new_content = content[:gallery_start] + new_gallery + content[list_end + 1:]  # +1 pour skip la virgule
    
    print(f"✅ {char_name} gallery: {len(local_images)} images locales")
    return new_content

# Sasuke
sasuke_images = [f"sasuke_gallery_{i}.jpg" for i in range(1, 11)]
content = replace_gallery(content, 'sasuke', sasuke_images)

# Sakura
sakura_images = [f"sakura_gallery_{i}.jpg" for i in range(1, 11)]
content = replace_gallery(content, 'sakura', sakura_images)

# Sauvegarder
chars_file.write_text(content, encoding='utf-8')
print("\n✅ Characters.kt mis à jour avec 31 images locales")
