#!/usr/bin/env python3
"""
Générer 195 images NSFW pour les 13 personnages:
- 5 images sensuelles (tenue légère, pose suggestive)
- 5 images sexy (lingerie, poses provocantes)
- 5 images NSFW (nue, intimité, érotique)
"""
import json, time, urllib.request, urllib.parse, io
from pathlib import Path
from PIL import Image

output_dir = Path('app/src/main/res/drawable-nodpi')
BASE_URL = "https://image.pollinations.ai/prompt"

# Descriptions NSFW par personnage
CHARACTERS_NSFW = {
    'naruto': [
        # Sensuelles (tenue légère, suggestive)
        "Naruto Uzumaki, 18-22 year old anime male ninja shirtless showing athletic muscular chest, orange shorts low on hips, spiky blonde hair wet, blue eyes intense, confident seductive smile, six whisker marks, anime art style, sensual pose",
        "Naruto Uzumaki topless muscular body, wearing only orange pants unbuttoned, wet spiky blonde hair, ocean blue eyes bedroom gaze, whisker marks on cheeks, leaning against wall seductively, anime style",
        "Naruto shirtless after training, toned abs visible, orange towel around neck, blonde hair messy, blue eyes half-closed seductive, whisker marks, sweaty skin glistening, anime sensual",
        "Naruto in bed shirtless, orange sheets, blonde hair disheveled, blue eyes looking at camera, whisker marks, athletic body visible, morning light, anime suggestive",
        "Naruto removing orange jacket revealing bare chest, blonde hair spiky, blue eyes intense, whisker marks, muscular torso, anime sensual tease",
        
        # Sexy (lingerie, provocant)
        "Naruto in tight orange boxer briefs only, muscular body on display, blonde hair wet, blue eyes seductive, whisker marks, lying on bed provocatively, anime sexy",
        "Naruto wearing minimal orange underwear, athletic muscular body exposed, blonde spiky hair, blue eyes bedroom stare, six whisker marks, kneeling pose provocative, anime erotic art",
        "Naruto in shower water running down naked chest and abs, orange towel barely covering, blonde hair wet, blue eyes intense, whisker marks visible, steam, anime sexy scene",
        "Naruto lying naked on bed covered only by orange sheet strategically placed, blonde hair messy, blue eyes half-lidded seductive, whisker marks, toned body visible, anime provocative",
        "Naruto standing naked back view showing muscular back and glutes, blonde hair, looking over shoulder with blue eyes and whisker marks, anime sexy rear",
        
        # NSFW (nu, érotique, intime)
        "Naruto completely nude full frontal, athletic muscular body, blonde spiky hair, ocean blue eyes sultry gaze, six whisker marks on cheeks, erect posture confident, anime nsfw art explicit",
        "Naruto naked lying on bed legs spread, muscular body on display, blonde hair messy, blue eyes lustful, whisker marks, genitals visible, anime explicit nsfw",
        "Naruto nude touching himself, blonde hair spiky, blue eyes closed in pleasure, whisker marks, muscular chest and abs, anime explicit masturbation nsfw",
        "Naruto having sex doggy style position, blonde hair messy, blue eyes intense, whisker marks visible, muscular back, anime explicit intercourse nsfw",
        "Naruto nude climax face, blonde hair disheveled, blue eyes rolling back, whisker marks, mouth open moaning, cum visible, anime explicit orgasm nsfw"
    ],
    
    'sasuke': [
        # Sensuelles
        "Sasuke Uchiha 18-22 shirtless pale skin, dark blue pants low, jet black hair messy, black eyes intense, cold seductive expression, lean muscular chest, anime sensual",
        "Sasuke topless showing toned abs, white pants unbuttoned, black spiky hair, sharingan eyes red, aristocratic face stoic sexy, anime suggestive",
        "Sasuke removing shirt revealing pale muscular torso, black hair swept back, black eyes piercing, lean athletic body, anime sensual tease",
        "Sasuke in bed shirtless, white sheets, black hair disheveled, sharingan active red eyes, pale skin glowing, anime bedroom scene",
        "Sasuke after training shirtless sweaty, black hair wet, pale skin glistening, black eyes half-closed, lean muscles, anime sensual",
        
        # Sexy
        "Sasuke in tight black boxer briefs, pale lean muscular body, black hair messy, sharingan red eyes seductive, lying provocatively, anime sexy",
        "Sasuke wearing minimal underwear, pale athletic body exposed, black spiky hair, red sharingan eyes bedroom stare, kneeling provocative, anime erotic",
        "Sasuke in shower water on pale naked chest, black hair wet, sharingan glowing red, lean body visible through steam, anime sexy scene",
        "Sasuke naked on bed white sheet covering genitals, black hair messy, red sharingan eyes sultry, pale toned body, anime provocative",
        "Sasuke standing nude rear view pale muscular back and ass, black hair, sharingan over shoulder, anime sexy back",
        
        # NSFW
        "Sasuke completely nude frontal, pale lean muscular body, black spiky hair, sharingan red eyes lustful, erect confident, anime nsfw explicit",
        "Sasuke naked on bed legs open, pale body on display, black hair messy, red eyes intense, genitals visible, anime explicit nsfw",
        "Sasuke nude masturbating, black hair disheveled, sharingan activated eyes closed, pale chest heaving, anime explicit nsfw",
        "Sasuke having sex missionary position, black hair messy, red sharingan eyes dominating, pale muscular body thrusting, anime explicit intercourse",
        "Sasuke nude climax face, black hair wild, sharingan fading, pale skin flushed, mouth open moaning, cum dripping, anime explicit orgasm nsfw"
    ],
    
    # ... (je vais continuer avec les autres personnages de la même manière)
}

# Pour éviter un script trop long, je vais générer dynamiquement les prompts basiques
def generate_nsfw_prompts(name, age, hair, eyes, style="anime"):
    base_desc = f"{name}, {age} year old"
    if style == "anime":
        base_desc += " anime character"
    else:
        base_desc += " person"
    
    prompts = []
    
    # 5 Sensuelles
    prompts.extend([
        f"{base_desc} shirtless showing chest, {hair}, {eyes}, sensual pose, {style} art",
        f"{base_desc} topless wet, {hair}, {eyes}, seductive smile, {style} sensual",
        f"{base_desc} removing shirt, {hair}, {eyes}, toned body, {style} suggestive",
        f"{base_desc} in bed shirtless, {hair}, {eyes}, morning light, {style} bedroom",
        f"{base_desc} after shower towel, {hair}, {eyes}, water drops, {style} sensual"
    ])
    
    # 5 Sexy
    prompts.extend([
        f"{base_desc} in underwear only, {hair}, {eyes}, lying provocatively, {style} sexy",
        f"{base_desc} minimal clothing, {hair}, {eyes}, kneeling pose, {style} erotic",
        f"{base_desc} shower scene naked, {hair}, {eyes}, steam and water, {style} sexy",
        f"{base_desc} on bed naked covered partially, {hair}, {eyes}, seductive, {style} provocative",
        f"{base_desc} nude rear view, {hair}, {eyes} looking back, {style} sexy back"
    ])
    
    # 5 NSFW
    prompts.extend([
        f"{base_desc} completely nude frontal, {hair}, {eyes}, full body, {style} nsfw explicit",
        f"{base_desc} naked lying legs spread, {hair}, {eyes}, genitals visible, {style} explicit",
        f"{base_desc} nude masturbating, {hair}, {eyes}, pleasure face, {style} explicit nsfw",
        f"{base_desc} sex scene intercourse, {hair}, {eyes}, thrusting, {style} explicit porn",
        f"{base_desc} orgasm face nude, {hair}, {eyes}, climax expression, cum, {style} explicit nsfw"
    ])
    
    return prompts

# Définir les 13 personnages avec leurs caractéristiques
CHARACTERS_INFO = {
    'naruto': ('Naruto Uzumaki', '18-22', 'spiky blonde hair', 'blue eyes', 'anime'),
    'sasuke': ('Sasuke Uchiha', '18-22', 'black spiky hair', 'black eyes', 'anime'),
    'sakura': ('Sakura Haruno', '18-22', 'long pink hair', 'green eyes', 'anime'),
    'kakashi': ('Kakashi Hatake', '28-30', 'silver spiky hair', 'gray eye', 'anime'),
    'itachi': ('Itachi Uchiha', '21-23', 'long black hair', 'black eyes', 'anime'),
    'hinata': ('Hinata Hyuga', '18-22', 'long indigo hair', 'lavender eyes', 'anime'),
    'leonardo': ('Leonardo DiCaprio', '49', 'blonde hair with gray', 'blue eyes', 'photorealistic'),
    'brad': ('Brad Pitt', '60', 'blonde-gray hair', 'blue eyes', 'photorealistic'),
    'margot': ('Margot Robbie', '34', 'platinum blonde hair', 'blue eyes', 'photorealistic'),
    'scarlett': ('Scarlett Johansson', '39', 'blonde hair', 'green eyes', 'photorealistic'),
    'emma': ('Emma Watson', '34', 'brown hair', 'hazel eyes', 'photorealistic'),
    'rock': ('Dwayne Johnson', '51', 'bald head', 'brown eyes', 'photorealistic'),
    'zendaya': ('Zendaya', '28', 'dark brown hair', 'hazel eyes', 'photorealistic'),
}

def optimize_image(data, max_kb=150):
    try:
        img = Image.open(io.BytesIO(data))
        if img.mode in ('RGBA', 'LA', 'P'):
            bg = Image.new('RGB', img.size, (255, 255, 255))
            bg.paste(img, mask=img.split()[-1] if img.mode in ('RGBA', 'LA') else None)
            img = bg
        out = io.BytesIO()
        quality = 85
        while quality > 30:
            out.seek(0); out.truncate()
            img.save(out, format='JPEG', quality=quality, optimize=True)
            if len(out.getvalue()) / 1024 <= max_kb or quality <= 35: break
            quality -= 5
        return out.getvalue()
    except: return data

def download_image(url, path, retries=3):
    for attempt in range(retries):
        try:
            if attempt > 0:
                time.sleep(20 * attempt)
                print(f" R{attempt+1}", end="", flush=True)
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(req, timeout=120) as r:
                if r.status == 200:
                    path.write_bytes(optimize_image(r.read()))
                    return True, path.stat().st_size / 1024
        except Exception as e:
            if attempt == retries - 1:
                print(f" E:{str(e)[:20]}", end="")
    return False, 0

print("\n" + "="*80)
print("🔞 GÉNÉRATION: 195 IMAGES NSFW (13 personnages x 15 images)")
print("⚠️  CONTENU ADULTE - Sensuel, Sexy, NSFW")
print("="*80)

total_ok = 0
total_fail = 0

for char_key, (name, age, hair, eyes, style) in CHARACTERS_INFO.items():
    print(f"\n{'='*80}")
    print(f"📛 {name.upper()} - {age} ans ({style.upper()})")
    print(f"{'='*80}")
    
    prompts = generate_nsfw_prompts(name, age, hair, eyes, style)
    model = "flux" if style == "anime" else "flux-realism"
    
    categories = ["SENSUEL"] * 5 + ["SEXY"] * 5 + ["NSFW"] * 5
    
    for i, (prompt, category) in enumerate(zip(prompts, categories), 1):
        file = output_dir / f"{char_key}nsfw{i}.jpg"
        
        if file.exists():
            print(f"[{i:2d}/15] {category:8s} ⏭️  Skip", end="  ")
            continue
        
        print(f"[{i:2d}/15] {category:8s}", end=" ", flush=True)
        
        prompt_encoded = urllib.parse.quote(f"{prompt}, masterpiece, high quality, detailed")
        url = f"{BASE_URL}/{prompt_encoded}?width=768&height=1024&model={model}&nologo=true&seed={int(time.time()*1000)+i}"
        
        ok, sz = download_image(url, file)
        if ok:
            print(f" ✅{sz:.0f}KB", end="  ")
            total_ok += 1
        else:
            print(f" ❌", end="  ")
            total_fail += 1
        
        if i % 3 == 0:
            print()  # Nouvelle ligne tous les 3
        
        time.sleep(20)  # Délai entre requêtes

print("\n" + "="*80)
print(f"✅ Succès: {total_ok}/195  ❌ Échecs: {total_fail}")
files = list(output_dir.glob('*nsfw*.jpg'))
print(f"📦 Total NSFW: {len(files)} fichiers ({sum(f.stat().st_size for f in files)/1024/1024:.1f} MB)")
print("="*80)
