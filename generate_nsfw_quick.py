#!/usr/bin/env python3
"""
Génération RAPIDE - 3 images par personnage = 39 images (~8 minutes)
Pour avoir un APK rapidement avec des galeries fonctionnelles
"""

import requests
import time
import os
from urllib.parse import quote

POLLINATION_BASE_URL = "https://image.pollinations.ai/prompt"
OUTPUT_DIR = "/workspace/app/src/main/res/drawable"
DELAY = 8  # 8 secondes entre images

CHARACTERS = {
    "naruto": "athletic 20yo male, spiky blonde hair, blue eyes, whisker marks, tanned skin, muscular",
    "sasuke": "athletic 20yo male, black spiky hair, dark eyes, pale skin, lean muscular, serious",
    "sakura": "athletic 20yo female, pink hair, green eyes, fair skin, toned figure",
    "hinata": "elegant 20yo female, long indigo hair, lavender eyes, porcelain skin, graceful curves",
    "kakashi": "athletic 27yo male, silver hair, one visible eye, mask, lean muscular",
    "itachi": "elegant 21yo male, long black hair, dark eyes, pale skin, lean build",
    "bradpitt": "handsome 60yo male actor Brad Pitt, blonde hair, blue eyes, chiseled jawline",
    "leonardo": "handsome 49yo male actor Leonardo DiCaprio, light brown hair, blue eyes",
    "therock": "muscular 51yo male Dwayne Johnson The Rock, bald, brown eyes, massive build, tattoos",
    "scarlett": "beautiful 39yo actress Scarlett Johansson, blonde hair, green eyes, curvy figure",
    "margot": "beautiful 34yo actress Margot Robbie, blonde hair, blue eyes, athletic curves",
    "emma": "elegant 34yo actress Emma Watson, brown hair, brown eyes, slender figure",
    "zendaya": "stunning 28yo actress Zendaya, long curly hair, brown eyes, tall slender curves"
}

QUICK_PROMPTS = [
    "suggestive pose, flirty expression, elegant revealing outfit, photorealistic, 8k",
    "seductive bedroom eyes, intimate lingerie, sensual lighting, professional photography",
    "artistic nude, tasteful composition, soft lighting, hyper realistic, masterpiece"
]

os.makedirs(OUTPUT_DIR, exist_ok=True)

print("🚀 GÉNÉRATION RAPIDE - 3 images/personnage = 39 images")
print(f"⏱️  Temps estimé: ~8 minutes")
print("="*60)

success = 0
for char_name, char_desc in CHARACTERS.items():
    print(f"\n🎨 {char_name.upper()}")
    for i, nsfw_prompt in enumerate(QUICK_PROMPTS, 1):
        prompt = f"{char_desc}, {nsfw_prompt}, adult 18+"
        url = f"{POLLINATION_BASE_URL}/{quote(prompt)}?width=512&height=768&nologo=true&enhance=true&seed={int(time.time())}"
        filename = f"{char_name}nsfw{i}.jpg"
        filepath = os.path.join(OUTPUT_DIR, filename)
        
        for attempt in range(3):
            try:
                r = requests.get(url, timeout=60)
                if r.status_code == 200 and len(r.content) > 10000:
                    with open(filepath, 'wb') as f:
                        f.write(r.content)
                    print(f"  ✅ {filename} ({len(r.content)//1024}KB)")
                    success += 1
                    break
                else:
                    time.sleep(10 * (attempt + 1))
            except:
                time.sleep(10 * (attempt + 1))
        
        time.sleep(DELAY)

print(f"\n{'='*60}")
print(f"✅ TERMINÉ: {success}/39 images générées")
print(f"📁 Destination: {OUTPUT_DIR}")
print(f"{'='*60}")
