#!/usr/bin/env python3
import json, time, urllib.request, urllib.parse, io
from pathlib import Path
from PIL import Image

output_dir = Path('app/src/main/res/drawable-nodpi')
output_dir.mkdir(parents=True, exist_ok=True)

BASE_URL = "https://image.pollinations.ai/prompt"

CHARACTERS = {
    'naruto': 'Naruto Uzumaki, 17 year old male ninja, spiky bright golden blonde hair, vivid ocean blue eyes, 6 whisker marks on cheeks, athletic build, orange and black ninja jacket, Konoha headband, big smile',
    'sasuke': 'Sasuke Uchiha, 17 year old male ninja, jet black spiky hair, pale skin, intense black eyes, aristocratic features, dark blue shirt, serious expression',
    'sakura': 'Sakura Haruno, 17 year old female ninja, long bright pink hair in ponytail, emerald green eyes, red qipao dress, Konoha headband, warm smile',
    'kakashi': 'Kakashi Hatake, 26 year old male ninja, silver gray spiky hair, one visible black eye, black face mask, green jonin vest, relaxed posture',
    'itachi': 'Itachi Uchiha, 21 year old male ninja, long black hair in ponytail, pale skin, dark circles, black Akatsuki coat with red clouds, melancholic expression',
    'hinata': 'Hinata Hyuga, 17 year old female ninja, long dark blue hair, pale lavender Byakugan eyes, lavender jacket, shy smile',
    'leonardo': 'Leonardo DiCaprio, 49 year old actor, blonde hair, blue eyes, beard, handsome mature features, suit',
    'brad': 'Brad Pitt, 60 year old actor, blonde hair with gray, blue eyes, beard, dimples, charming smile',
    'margot': 'Margot Robbie, 34 year old actress, platinum blonde hair, blue eyes, beautiful face, radiant smile',
    'scarlett': 'Scarlett Johansson, 39 year old actress, blonde hair, green eyes, full lips, elegant beauty',
    'emma': 'Emma Watson, 34 year old actress, brown short hair, hazel eyes, delicate features, graceful smile',
    'rock': 'Dwayne The Rock Johnson, 51 year old, bald head, intense eyes, massive muscles, Polynesian tattoos, charismatic smile',
    'zendaya': 'Zendaya, 28 year old actress, long brown hair, hazel eyes, high cheekbones, elegant model beauty'
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
            out.seek(0)
            out.truncate()
            img.save(out, format='JPEG', quality=quality, optimize=True)
            if len(out.getvalue()) / 1024 <= max_kb or quality <= 35:
                break
            quality -= 5
        
        return out.getvalue()
    except:
        return data

def download(url, path, retries=5):
    for i in range(retries):
        try:
            if i > 0:
                time.sleep(min(20 * i, 60))
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, timeout=120) as r:
                if r.status == 200:
                    data = optimize_image(r.read())
                    path.write_bytes(data)
                    return True, len(data) / 1024
        except:
            pass
    return False, 0

stats = {"ok": 0, "fail": 0, "skip": 0}

print("\n" + "="*80)
print("🎨 GÉNÉRATION 143 IMAGES HYPER-RÉALISTES")
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
    prompt = urllib.parse.quote(f"professional portrait, {desc}, photorealistic, 8k, masterpiece")
    url = f"{BASE_URL}/{prompt}?width=768&height=1024&model=flux-realism&enhance=true&nologo=true&seed={int(time.time()*1000)+idx}"
    
    ok, size = download(url, file)
    if ok:
        print(f"✅ {size:.0f}KB")
        stats["ok"] += 1
    else:
        print("❌")
        stats["fail"] += 1
    time.sleep(15)

# GALERIES (130)
print("\n🖼️ GALERIES (130)")
variations = ["front view", "side profile", "3/4 view", "smiling", "serious", "casual", "action", "artistic", "candid", "full body"]

for cidx, (key, desc) in enumerate(CHARACTERS.items(), 1):
    print(f"\n[{cidx}/13] {key.upper()}")
    for i in range(1, 11):
        file = output_dir / f"{key}gallery{i}.jpg"
        if file.exists():
            print(f"  [{i:2d}/10] ⏭️", end=" ")
            stats["skip"] += 1
            continue
        
        var = variations[(i-1) % len(variations)]
        prompt = urllib.parse.quote(f"{var}, {desc}, photorealistic, 8k")
        url = f"{BASE_URL}/{prompt}?width=768&height=1024&model=flux-realism&enhance=true&nologo=true&seed={int(time.time()*1000)+cidx*100+i}"
        
        ok, size = download(url, file)
        print(f"  [{i:2d}/10] {'✅' if ok else '❌'} {size:.0f if ok else 0}KB", end=" ")
        if ok: stats["ok"] += 1
        else: stats["fail"] += 1
        time.sleep(18)
    print()

print("\n" + "="*80)
print(f"✅ Succès: {stats['ok']}/143 | ❌ Échecs: {stats['fail']} | ⏭️ Sautés: {stats['skip']}")
files = list(output_dir.glob('*.jpg'))
print(f"📦 Total: {len(files)} fichiers ({sum(f.stat().st_size for f in files)/1024/1024:.1f} MB)")
