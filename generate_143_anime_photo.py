#!/usr/bin/env python3
"""
GÉNÉRATION: 143 Images ULTRA-RESSEMBLANTES
- Style ANIME pour Naruto (6 personnages)
- Style PHOTO-RÉALISTE pour Célébrités (7 personnages)
"""
import json, time, urllib.request, urllib.parse, io
from pathlib import Path
from PIL import Image

output_dir = Path('app/src/main/res/drawable-nodpi')
output_dir.mkdir(parents=True, exist_ok=True)

BASE_URL = "https://image.pollinations.ai/prompt"

# Personnages Naruto (ANIME)
NARUTO_ANIME = {
    'naruto': 'Naruto Uzumaki, 17 year old anime male ninja, spiky bright golden blonde hair pointing upwards, vivid ocean blue eyes, 6 thick black whisker marks on cheeks (3 per cheek), big confident smile showing teeth, orange jacket with black borders, blue ninja sandals, metal headband with Konoha leaf symbol, athletic build, anime art style, manga illustration, vibrant colors, cel shading, dynamic pose, determined expression, shounen protagonist energy',
    
    'sasuke': 'Sasuke Uchiha, 17 year old anime male ninja, jet black spiky hair swept backwards, pale porcelain skin, intense black eyes (red Sharingan optional), aristocratic sharp facial features, cold serious expression, dark blue high-collar shirt, white pants, Konoha metal headband, lean athletic build, anime art style, manga illustration, cool brooding pose, arms crossed, dark aura',
    
    'sakura': 'Sakura Haruno, 17 year old anime female kunoichi, long bright pink hair in high ponytail with red ribbon, vibrant emerald green eyes, fair porcelain skin with rosy cheeks, confident warm smile, red qipao dress or red sleeveless combat top, black fingerless gloves, Konoha metal headband, athletic feminine build, anime art style, manga illustration, determined powerful pose, fist raised',
    
    'kakashi': 'Kakashi Hatake, 26 year old anime male ninja, spiky silver-gray hair defying gravity pointing left, black mask covering lower face from nose down, one visible dark gray eye with lazy expression, left eye covered by tilted Konoha metal headband, green jonin vest over blue long-sleeve shirt, relaxed cool pose, anime art style, manga illustration, mysterious aura, eye smile',
    
    'itachi': 'Itachi Uchiha, 21 year old anime male ninja, long jet black hair in low ponytail with long bangs framing face, very pale porcelain skin, melancholic black eyes (red Sharingan optional), dark circles under eyes, two nasolabial lines from nose to chin, slender graceful build, long black Akatsuki coat with red clouds, high red collar, Konoha headband with horizontal scratch, calm sorrowful expression, anime art style, manga illustration, tragic beauty',
    
    'hinata': 'Hinata Hyuga, 17 year old anime female kunoichi, long silky indigo-black hair falling to waist with short straight bangs framing face, pale lavender-white Byakugan eyes (no visible pupil), fair porcelain skin that blushes easily on cheeks, shy sweet expression looking down, fingers pressed together nervously, lavender-light purple hooded jacket with white fur collar, dark blue pants, Konoha metal headband, delicate feminine athletic build, anime art style, manga illustration, gentle pure aura, soft smile'
}

# Célébrités (PHOTO-RÉALISTE)
CELEBRITIES_PHOTO = {
    'leonardo': 'Leonardo DiCaprio, 49 year old male Hollywood actor, golden blonde hair styled back with volume, medium length with some gray at temples, piercing ice blue eyes, square mature face with strong prominent jaw, short trimmed blonde-gray beard (3-5 days stubble) well-groomed, character lines on forehead and around eyes, clear lightly tanned skin, confident charismatic smile, well-tailored black or navy suit, white shirt unbuttoned at collar, photorealistic professional photography, Hollywood star aura, 8k ultra detailed, studio lighting',
    
    'brad': 'Brad Pitt, 60 year old male Hollywood actor, light golden blonde hair short and tousled with visible gray streaks, textured modern short haircut, bright sky blue eyes, chiseled square jaw with deep prominent dimples, short trimmed blonde-gray beard (5-7 days stubble), character lines and crow\'s feet, clear lightly tanned mature skin, iconic smile showing dimples, casual cool style wearing white/gray t-shirt and jeans, photorealistic professional photography, natural confident pose, friendly approachable aura, 8k ultra detailed',
    
    'margot': 'Margot Robbie, 34 year old female Hollywood actress, platinum blonde hair long and silky smooth with natural waves falling to shoulders, intense ice blue glacier eyes, perfect symmetrical oval face with high sculpted cheekbones, luminous flawless porcelain skin with peachy undertone, full pink lips with radiant bright smile, elegant glamorous style wearing evening gown (red/gold/black) or chic casual white blouse, sophisticated natural makeup, photorealistic professional photography, confident elegant pose, Hollywood star glamour aura, 8k ultra detailed, studio lighting',
    
    'scarlett': 'Scarlett Johansson, 39 year old female Hollywood actress, ash blonde-honey hair wavy shoulder-length with natural texture, magnetic green-hazel almond-shaped eyes very expressive, harmonious oval face with high cheekbones, clear golden flawless skin with beauty mark mole under right eye, iconic full voluptuous lips, sultry confident expression, elegant sensual style wearing black fitted dress with V-neckline, smoky eyes makeup, photorealistic professional photography, confident seductive pose, timeless mature beauty aura, 8k ultra detailed, studio lighting',
    
    'emma': 'Emma Watson, 34 year old female British actress, dark brown hair in elegant short pixie cut or shoulder-length with side-swept bangs, luminous hazel-golden brown eyes large and expressive, delicate heart-shaped face with fine jaw and pointed chin, immaculate clear porcelain skin with subtle natural freckles, graceful warm smile, chic intellectual style wearing white/beige blouse and tailored pants, natural minimal makeup, photorealistic professional photography, elegant composed pose, classic British beauty with grace and intelligence aura, 8k ultra detailed, soft lighting',
    
    'rock': 'Dwayne The Rock Johnson, 51 year old male actor and former wrestler, completely bald shaved head smooth and shiny, intense dark brown piercing eyes, massive square jaw very wide, golden bronze skin (Polynesian-African American heritage) glowing, elaborate detailed Polynesian tribal tattoos on right shoulder and chest (complex black ink), thick expressive eyebrows with iconic raised eyebrow, charismatic warm smile showing teeth, HUGE massive bodybuilder physique, enormous bulging muscles, visible veins, particularly developed shoulders and pectorals, wearing tight black under armour showcasing physique, photorealistic professional photography, powerful presence, 8k ultra detailed, dramatic lighting',
    
    'zendaya': 'Zendaya, 28 year old female actress and model, long dark brown hair with natural texture (curly or straight), magnetic hazel-golden brown feline almond-shaped eyes, elongated oval face with very high sculpted cheekbones, luminous golden caramel flawless skin (African-American heritage), full lips with confident smile, defined arched eyebrows, tall slender model figure with very long legs, graceful confident posture, modern androgynous beauty, fashion-forward avant-garde style wearing high-fashion couture, sophisticated makeup, photorealistic professional photography, model pose elegant and confident, fashion icon aura, 8k ultra detailed, editorial lighting'
}

def optimize_image(data, max_kb=120):
    try:
        img = Image.open(io.BytesIO(data))
        if img.mode in ('RGBA', 'LA', 'P'):
            bg = Image.new('RGB', img.size, (255, 255, 255))
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
print("🎨 GÉNÉRATION: 143 IMAGES ANIME + PHOTO HYPER-RESSEMBLANTES")
print("="*80)

# VIGNETTES (13)
print("\n📸 VIGNETTES (13)")
ALL_CHARS = {**NARUTO_ANIME, **CELEBRITIES_PHOTO}
for idx, (key, desc) in enumerate(ALL_CHARS.items(), 1):
    file = output_dir / f"{key}.jpg"
    if file.exists():
        print(f"[{idx:2d}/13] {key:12s} ⏭️ Existe")
        stats["skip"] += 1
        continue
    print(f"[{idx:2d}/13] {key:12s} ", end="", flush=True)
    is_anime = key in NARUTO_ANIME
    model = "flux" if is_anime else "flux-realism"
    prompt = urllib.parse.quote(f"professional centered portrait, {desc}, masterpiece")
    url = f"{BASE_URL}/{prompt}?width=768&height=1024&model={model}&enhance=true&nologo=true&seed={int(time.time()*1000)+idx}"
    ok, sz = download_image(url, file)
    print(f"✅ {sz:.0f}KB {'(ANIME)' if is_anime else '(PHOTO)'}" if ok else "❌ FAIL")
    stats["ok" if ok else "fail"] += 1
    time.sleep(15)

# GALERIES (130)
print(f"\n🖼️  GALERIES (130)")
variations_anime = ["close-up face", "side profile", "three-quarter view", "action pose fighting", "serious expression", "smiling happy", "dynamic movement", "portrait centered", "full body stance", "intense battle ready"]
variations_photo = ["close-up portrait", "side profile", "three-quarter view", "smiling warmly", "serious expression", "casual relaxed", "elegant pose", "confident stance", "natural candid", "full body professional"]

for cidx, (key, desc) in enumerate(ALL_CHARS.items(), 1):
    is_anime = key in NARUTO_ANIME
    variations = variations_anime if is_anime else variations_photo
    model = "flux" if is_anime else "flux-realism"
    style_tag = "ANIME" if is_anime else "PHOTO"
    
    print(f"\n[{cidx:2d}/13] {key.upper()} (10 images {style_tag})")
    for i in range(1, 11):
        file = output_dir / f"{key}gallery{i}.jpg"
        if file.exists():
            print(f"  [{i:2d}/10] ⏭️", end=" ")
            stats["skip"] += 1
            continue
        print(f"  [{i:2d}/10]", end=" ", flush=True)
        var = variations[(i-1) % len(variations)]
        prompt = urllib.parse.quote(f"{var}, {desc}, masterpiece")
        url = f"{BASE_URL}/{prompt}?width=768&height=1024&model={model}&enhance=true&nologo=true&seed={int(time.time()*1000)+cidx*100+i}"
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
