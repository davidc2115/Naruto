#!/usr/bin/env python3
"""
Mettre à jour Characters.kt:
1. Âges Naruto -> 18-22 ans (adultes)  
2. Ajouter "RÉPONDS TOUJOURS EN FRANÇAIS" à tous les systemPrompts
"""
import re
from pathlib import Path

file_path = Path('app/src/main/java/com/narutoai/chat/data/Characters.kt')
content = file_path.read_text()

print("🔧 Mise à jour Characters.kt")
print("=" * 60)

# 1. Mettre à jour les âges des personnages Naruto (simple replacement)
age_replacements = {
    'age = "17-19 ans"': 'age = "18-22 ans"',
    'age = "17 ans"': 'age = "18-22 ans"',
    'de 17-19 ans': 'de 18-22 ans',
    'de 17 ans': 'de 18-22 ans',
    'Jeune ninja de 17-19 ans': 'Jeune adulte ninja de 18-22 ans',
    'ninja de 17 ans': 'ninja de 18-22 ans',
    'kunoichi de 17 ans': 'kunoichi de 18-22 ans',
}

for old, new in age_replacements.items():
    count = content.count(old)
    content = content.replace(old, new)
    if count > 0:
        print(f"✅ '{old}' -> '{new}' ({count}x)")

# 2. Ajouter instruction française à TOUS les systemPrompts
lines = content.split('\n')
new_lines = []
in_system_prompt = False
prompt_count = 0

for i, line in enumerate(lines):
    if 'systemPromptSFW = """Tu es' in line and i + 1 < len(lines):
        # Vérifier si ligne suivante n'a pas déjà l'instruction
        next_line = lines[i + 1] if i + 1 < len(lines) else ""
        if 'RÉPONDS TOUJOURS EN FRANÇAIS' not in next_line:
            new_lines.append(line)
            new_lines.append("")
            new_lines.append("IMPORTANT: RÉPONDS TOUJOURS EN FRANÇAIS, même si tu es un personnage anglophone.")
            prompt_count += 1
            continue
    new_lines.append(line)

content = '\n'.join(new_lines)

print("=" * 60)
print(f"✅ {prompt_count} systemPrompts mis à jour avec instruction française")

file_path.write_text(content)
print(f"📄 Fichier: {file_path}")
print("✅ Modifications enregistrées")
