#!/usr/bin/env python3
import json, time, urllib.request, urllib.parse, io, sys
from pathlib import Path
from PIL import Image

output_dir = Path('app/src/main/res/drawable-nodpi')
output_dir.mkdir(parents=True, exist_ok=True)

BASE_URL = "https://image.pollinations.ai/prompt"

CHARACTERS = {
    'naruto': 'Naruto Uzumaki, 17 year old male ninja, spiky bright golden blonde hair, vivid ocean blue eyes, 6 whisker marks on cheeks, athletic build, orange ninja jacket, Konoha headband',
    'sasuke': 'Sasuke Uchiha, 17 year old male ninja, jet black spiky hair, pale skin, intense black eyes, aristocratic features, dark blue shirt, Konoha headband',
    'sakura': 'Sakura Haruno, 17 year old female kunoichi, long bright pink hair in ponytail, emerald green eyes, red qipao dress, Konoha headband',
    'kakashi': 'Kakashi Hatake, 26 year old male ninja, silver gray spiky hair, one visible eye, black mask covering face, green jonin vest',
    'itachi': 'Itachi Uchiha, 21 year old male ninja, long black hair in ponytail, pale skin, dark eyes, black Akatsuki coat with red clouds',
    'hinata': 'Hinata Hyuga, 17 year old female kunoichi, long indigo hair, pale lavender eyes, lavender jacket, shy expression',
    'leonardo': 'Leonardo DiCaprio, 49 year old male actor, blonde hair, blue eyes, trimmed beard, handsome mature features, suit',
    'brad': 'Brad Pitt, 60 year old male actor, blonde hair with gray, blue eyes, chiseled jaw, dimples, casual style',
    'margot': 'Margot Robbie, 34 year old female actress, platinum blonde hair, blue eyes, high cheekbones, glamorous',
    'scarlett': 'Scarlett Johansson, 39 year old female actress, blonde wavy hair, green eyes, full lips, elegant',
    'emma': 'Emma Watson, 34 year old female actress, brown hair short pixie cut, hazel eyes, delicate features, classic beauty',
    'rock': 'Dwayne The Rock Johnson, 51 year old male, bald head, dark eyes, massive muscular build, Polynesian tattoos',
    'zendaya': 'Zendaya, 28 year old female actress, long brown hair, hazel eyes, high cheekbones, tall model figure'
}

def optimize_image(data, max_kb=120):
    try:
        img = Image.open(io.BytesIO(data))
        if img.mode in ('RGBA', 'LA', 'P'):
            bg = Image.new('RGB', img.size, (255, 255, 255))
            if img.mode == 'P': img = img.convert('RGBA')
            bg.paste(img, mask=img.split()[-1] if img.mode in ('RGBA', 'LA') else None)
            img = bg
        if img.width > 1024 or img.height > 1536:
            img.thumbnail((1024, 1536), Image.Resampling.LANCZOS)
        out = io.BytesIO()
        quality = 85
        while quality > 30:
            out.seek(0); out.truncate()
            img.save(out, format='JPEG', quality=quality, optimize=True)
            if len(out.getvalue()) / 1024 <= max_kb or quality <= 35: break
            quality -= 5
        return out.getvalue()
    except: return data

def download_image(url, path, retries=5):
    for attempt in range(retries):
        try:
            if attempt > 0:
                time.sleep(min(20 * attempt, 60))
                print(f"    Retry {attempt+1}/{retries}...", end=" ", flush=True)
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, timeout=120) as r:
                if r.status == 200:
                    path.write_bytes(optimize_image(r.read()))
                    return True, path.stat().st_size / 1024
        except Exception as e:
            print(f"Error: {str(e)[:30]}", end=" ")
    return False, 0

stats = {"ok": 0, "fail": 0, "skip": 0}

print("\n" + "="*80)
print("🎨 GÉNÉRATION: 143 IMAGES HYPER-RÉALISTES")
print("="*80)

# VIGNETTES (13)
print("\n📸 VIGNETTES (13)")
for idx, (key, desc) in enumerate(CHARACTERS.items(), 1):
    file = output_dir / f"{key}.jpg"
    if file.exists():
        print(f"[{idx:2d}/13] {key:12s} ⏭️ Existe")
        stats["skip"] += 1
        continue
    print(f"[{idx:2d}/13] {key:12s} ", end="", flush=True)
    prompt = urllib.parse.quote(f"professional portrait, {desc}, centered, studio lighting, photorealistic, 8k, masterpiece")
    url = f"{BASE_URL}/{prompt}?width=768&height=1024&model=flux-realism&enhance=true&nologo=true&seed={int(time.time()*1000)+idx}"
    ok, sz = download_image(url, file)
    print(f"✅ {sz:.0f}KB" if ok else "❌ FAIL")
    stats["ok" if ok else "fail"] += 1
    time.sleep(15)

# GALERIES (130)
print(f"\n🖼️  GALERIES (130)")
variations = ["close-up", "profile", "3/4 view", "smiling", "serious", "casual", "action", "artistic", "candid", "full body"]

for cidx, (key, desc) in enumerate(CHARACTERS.items(), 1):
    print(f"\n[{cidx:2d}/13] {key.upper()} (10 images)")
    for i in range(1, 11):
        file = output_dir / f"{key}gallery{i}.jpg"
        if file.exists():
            print(f"  [{i:2d}/10] ⏭️", end=" ")
            stats["skip"] += 1
            continue
        print(f"  [{i:2d}/10]", end=" ", flush=True)
        var = variations[i % len(variations)]
        prompt = urllib.parse.quote(f"{var}, {desc}, photorealistic, 8k, professional photo")
        url = f"{BASE_URL}/{prompt}?width=768&height=1024&model=flux-realism&enhance=true&nologo=true&seed={int(time.time()*1000)+i}"
        ok, sz = download_image(url, file)
        print(f"✅{sz:.0f}KB" if ok else "❌", end=" ")
        stats["ok" if ok else "fail"] += 1
        time.sleep(18)
    print()

print("\n" + "="*80)
print(f"✅ Succès: {stats['ok']}/143  ❌ Échecs: {stats['fail']}  ⏭️ Skip: {stats['skip']}")
files = list(output_dir.glob('*.jpg'))
print(f"📦 Total: {len(files)} fichiers ({sum(f.stat().st_size for f in files)/1024/1024:.1f} MB)")
print("="*80)
