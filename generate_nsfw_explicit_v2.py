#!/usr/bin/env python3
"""
GÉNÉRATION 195 IMAGES NSFW EXPLICITES - VERSION OPTIMISÉE
Délai 60s entre requêtes pour éviter rate limit
Prompts ULTRA EXPLICITES (nudité frontale, sexe visible)
"""
import json, time, urllib.request, urllib.parse, io
from pathlib import Path
from PIL import Image

output_dir = Path('app/src/main/res/drawable-nodpi')
BASE_URL = "https://image.pollinations.ai/prompt"

# Prompts ULTRA EXPLICITES par type
def generate_explicit_prompts(name, age, hair, eyes, body, style="anime"):
    base = f"{name}, {age} year old"
    prompts = []
    
    # 5 SENSUELLES (tenue légère, suggestive)
    prompts.extend([
        f"{base} {style}, shirtless bare chest exposed, {hair}, {eyes}, {body}, sensual seductive pose, bedroom eyes, wet skin glistening",
        f"{base} {style}, topless nude upper body, {hair}, {eyes}, {body}, hands covering breasts, teasing smile, soft lighting",
        f"{base} {style}, wearing only underwear, {hair}, {eyes}, {body}, lying on bed provocatively, legs spread slightly, sultry gaze",
        f"{base} {style}, removing shirt revealing bare torso, {hair}, {eyes}, {body}, intimate moment, bedroom setting, seductive expression",
        f"{base} {style}, after shower wet naked shoulders, towel barely covering, {hair}, {eyes}, {body}, steamy atmosphere, inviting look"
    ])
    
    # 5 SEXY (lingerie, nu partiel)
    prompts.extend([
        f"{base} {style}, wearing sexy lingerie, {hair}, {eyes}, {body}, breasts visible through sheer fabric, kneeling pose provocative, bedroom lighting",
        f"{base} {style}, topless nude showing breasts, {hair}, {eyes}, {body}, hands on hips confident, nipples visible, erotic art",
        f"{base} {style}, wearing only panties topless, {hair}, {eyes}, {body}, lying on bed legs open, bare breasts exposed, seductive gaze",
        f"{base} {style}, naked in shower water on breasts, {hair}, {eyes}, {body}, butt visible from behind, wet hair, steamy glass",
        f"{base} {style}, nude covered by sheet barely, {hair}, {eyes}, {body}, breasts peeking out, intimate bedroom scene, morning light"
    ])
    
    # 5 NSFW EXPLICIT (nudité complète, sexe visible)
    prompts.extend([
        f"{base} {style}, completely naked full frontal nudity, {hair}, {eyes}, {body}, breasts and pussy visible, legs spread wide, explicit erotic pose",
        f"{base} {style}, nude lying on bed full body naked, {hair}, {eyes}, {body}, vagina visible between legs, breasts exposed, explicit adult content",
        f"{base} {style}, naked masturbating touching pussy, {hair}, {eyes}, {body}, fingers between legs, breasts bouncing, pleasure face, explicit nsfw",
        f"{base} {style}, having sex intercourse explicit, {hair}, {eyes}, {body}, penetration visible, naked bodies intertwined, explicit porn art",
        f"{base} {style}, nude orgasm face cumming, {hair}, {eyes}, {body}, pussy dripping, breasts heaving, mouth open moaning, explicit climax nsfw"
    ])
    
    return prompts

# Définir les 13 personnages
CHARACTERS_INFO = {
    'naruto': ('Naruto Uzumaki', '18-22', 'spiky blonde hair', 'blue eyes', 'athletic muscular body', 'anime'),
    'sasuke': ('Sasuke Uchiha', '18-22', 'black spiky hair', 'black eyes', 'lean muscular body', 'anime'),
    'sakura': ('Sakura Haruno', '18-22', 'long pink hair', 'green eyes', 'athletic feminine body big breasts', 'anime'),
    'kakashi': ('Kakashi Hatake', '28-30', 'silver spiky hair', 'gray eye', 'lean muscular body', 'anime'),
    'itachi': ('Itachi Uchiha', '21-23', 'long black hair', 'black eyes', 'lean graceful body', 'anime'),
    'hinata': ('Hinata Hyuga', '18-22', 'long indigo hair', 'lavender eyes', 'curvy feminine body large breasts', 'anime'),
    'leonardo': ('Leonardo DiCaprio', '49', 'blonde hair', 'blue eyes', 'fit body', 'photorealistic'),
    'brad': ('Brad Pitt', '60', 'blonde gray hair', 'blue eyes', 'athletic body', 'photorealistic'),
    'margot': ('Margot Robbie', '34', 'platinum blonde hair', 'blue eyes', 'perfect hourglass body big breasts', 'photorealistic'),
    'scarlett': ('Scarlett Johansson', '39', 'blonde hair', 'green eyes', 'curvy body large breasts', 'photorealistic'),
    'emma': ('Emma Watson', '34', 'brown hair', 'hazel eyes', 'slim body perky breasts', 'photorealistic'),
    'rock': ('Dwayne Johnson The Rock', '51', 'bald head', 'brown eyes', 'massive muscular body', 'photorealistic'),
    'zendaya': ('Zendaya', '28', 'dark brown curly hair', 'hazel eyes', 'slim tall body', 'photorealistic'),
}

def optimize_image(data, max_kb=200):
    try:
        img = Image.open(io.BytesIO(data))
        if img.mode in ('RGBA', 'LA', 'P'):
            bg = Image.new('RGB', img.size, (255, 255, 255))
            bg.paste(img, mask=img.split()[-1] if img.mode in ('RGBA', 'LA') else None)
            img = bg
        out = io.BytesIO()
        quality = 90
        while quality > 40:
            out.seek(0); out.truncate()
            img.save(out, format='JPEG', quality=quality, optimize=True)
            if len(out.getvalue()) / 1024 <= max_kb or quality <= 45: break
            quality -= 5
        return out.getvalue()
    except: return data

def download_image(url, path, retries=5):
    for attempt in range(retries):
        try:
            if attempt > 0:
                delay = 30 * attempt  # 30s, 60s, 90s, 120s, 150s
                print(f" R{attempt+1}({delay}s)", end="", flush=True)
                time.sleep(delay)
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, timeout=180) as r:
                if r.status == 200:
                    path.write_bytes(optimize_image(r.read()))
                    return True, path.stat().st_size / 1024
        except Exception as e:
            if attempt == retries - 1:
                print(f" E:{str(e)[:30]}", end="")
    return False, 0

print("\n" + "="*80)
print("🔞 GÉNÉRATION: 195 IMAGES NSFW EXPLICITES (v2 - Délais optimisés)")
print("⚠️  CONTENU ADULTE EXTREME - Nudité frontale, sexe visible")
print("="*80)

total_ok = 0
total_fail = 0
start_time = time.time()

for char_key, (name, age, hair, eyes, body, style) in CHARACTERS_INFO.items():
    print(f"\n{'='*80}")
    print(f"📛 {name.upper()} - {age} ans ({style.upper()})")
    print(f"{'='*80}")
    
    prompts = generate_explicit_prompts(name, age, hair, eyes, body, style)
    model = "flux" if style == "anime" else "flux-realism"
    
    categories = ["SENSUEL"] * 5 + ["SEXY"] * 5 + ["NSFW-EX"] * 5
    
    for i, (prompt, category) in enumerate(zip(prompts, categories), 1):
        file = output_dir / f"{char_key}nsfw{i}.jpg"
        
        if file.exists():
            sz = file.stat().st_size / 1024
            print(f"[{i:2d}/15] {category:9s} ⏭️  Skip ({sz:.0f}KB)", end="  ")
            total_ok += 1
            if i % 2 == 0: print()
            continue
        
        print(f"[{i:2d}/15] {category:9s}", end=" ", flush=True)
        
        prompt_encoded = urllib.parse.quote(f"{prompt}, masterpiece, ultra detailed, 8k")
        url = f"{BASE_URL}/{prompt_encoded}?width=768&height=1024&model={model}&nologo=true&enhance=true&seed={int(time.time()*1000)+i}"
        
        ok, sz = download_image(url, file, retries=5)
        if ok:
            print(f" ✅{sz:.0f}KB", end="  ")
            total_ok += 1
        else:
            print(f" ❌", end="  ")
            total_fail += 1
        
        if i % 2 == 0:
            print()
        
        # DÉLAI LONG pour éviter rate limit (60s au lieu de 20s)
        if i < 15:  # Pas de délai après la dernière
            time.sleep(60)

elapsed = time.time() - start_time
print("\n" + "="*80)
print(f"✅ Succès: {total_ok}/195  ❌ Échecs: {total_fail}")
files = list(output_dir.glob('*nsfw*.jpg'))
print(f"📦 Total NSFW: {len(files)} fichiers ({sum(f.stat().st_size for f in files)/1024/1024:.1f} MB)")
print(f"⏱️  Temps: {elapsed/3600:.1f}h")
print("="*80)
