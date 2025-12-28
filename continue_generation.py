#!/usr/bin/env python3
"""
Génération CONTINUE (skip images existantes)
"""
import json, time, urllib.request, urllib.parse, io
from pathlib import Path
from PIL import Image

with open('character_descriptions.json', 'r') as f:
    descriptions = json.load(f)

output_dir = Path('app/src/main/res/drawable-nodpi')
BASE_URL = "https://image.pollinations.ai/prompt"

def optimize_image(data):
    try:
        img = Image.open(io.BytesIO(data))
        if img.mode in ('RGBA', 'LA', 'P'):
            bg = Image.new('RGB', img.size, (255, 255, 255))
            if img.mode == 'P': img = img.convert('RGBA')
            bg.paste(img, mask=img.split()[-1] if img.mode in ('RGBA', 'LA') else None)
            img = bg
        out = io.BytesIO()
        img.save(out, format='JPEG', quality=80, optimize=True)
        return out.getvalue()
    except:
        return data

def download(url, path, retries=3):
    for attempt in range(retries):
        try:
            if attempt > 0:
                time.sleep(20 * attempt)
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, timeout=90) as r:
                if r.status == 200:
                    data = optimize_image(r.read())
                    path.write_bytes(data)
                    return True, len(data) / 1024
        except Exception as e:
            print(f"    Attempt {attempt+1} failed: {str(e)[:40]}")
    return False, 0

stats = {"done": 0, "failed": 0}

# Compter existant
existing = list(output_dir.glob('*.png')) + list(output_dir.glob('*.jpg'))
print(f"📦 Images existantes: {len(existing)}/143")

# GALERIES (on skip les vignettes car 13/13 faites)
print("\n🖼️  GÉNÉRATION GALERIES (130 images)")
print("="*80)

variations = ["close-up", "profile", "3/4 view", "smiling", "serious", "casual", "professional", "outdoor", "indoor", "dramatic"]

for char_idx, (char, desc) in enumerate(descriptions.items(), 1):
    char_done = 0
    
    for i in range(1, 11):
        path = output_dir / f"{char}_gallery_{i}.jpg"
        
        if path.exists():
            char_done += 1
            continue
        
        var = variations[i % len(variations)]
        prompt = urllib.parse.quote(f"{var}, {desc}, photorealistic, 8k, detailed")
        url = f"{BASE_URL}/{prompt}?width=768&height=1024&model=flux&enhance=true&nologo=true&seed={int(time.time()*1000)+i}"
        
        print(f"[{char_idx}/13] {char:12s} {i:2d}/10 ... ", end="", flush=True)
        
        ok, size = download(url, path)
        if ok:
            print(f"✅ {size:.0f}KB")
            stats["done"] += 1
            char_done += 1
        else:
            print(f"❌ FAIL")
            stats["failed"] += 1
        
        time.sleep(18)
    
    print(f"  → {char}: {char_done}/10\n")

total = list(output_dir.glob('*.png')) + list(output_dir.glob('*.jpg'))
print(f"\n✅ TERMINÉ: {len(total)}/143 ({len(total)/143*100:.1f}%)")
print(f"   Nouveaux: {stats['done']}, Échecs: {stats['failed']}")
