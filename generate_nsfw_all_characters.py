#!/usr/bin/env python3
"""
Génération COMPLÈTE de galeries NSFW pour TOUS les personnages
195 images totales (13 personnages × 15 images)

MÉTHODE:
- Utilise Pollination AI (car Freebox SD pas encore installée)
- Descriptions explicites ADULTES (18+)
- 3 niveaux: Sensuel (1-5), Sexy (6-10), Explicit (11-15)
"""

import requests
import time
import os
import urllib.parse

OUTPUT_DIR = "/workspace/app/src/main/res/drawable-nodpi"
POLLINATION_API = "https://image.pollinations.ai/prompt"

# Tous les personnages avec descriptions ADULTES (18+)
CHARACTERS = {
    "naruto": {
        "name": "Naruto Uzumaki", 
        "age": "18 years old adult",
        "desc": "young adult man, 18+ years old, spiky blonde hair, bright blue eyes, tanned skin, 3 whisker marks on each cheek, athletic muscular body, ninja outfit"
    },
    "sasuke": {
        "name": "Sasuke Uchiha",
        "age": "18 years old adult", 
        "desc": "young adult man, 18+ years old, black spiky hair, dark eyes, pale skin, lean muscular body, ninja attire"
    },
    "sakura": {
        "name": "Sakura Haruno",
        "age": "18 years old adult",
        "desc": "young adult woman, 18+ years old, pink hair, green eyes, athletic feminine body, ninja outfit"
    },
    "kakashi": {
        "name": "Kakashi Hatake",
        "age": "26 years old adult",
        "desc": "mature adult man, 26 years old, silver hair, one visible eye, lean muscular body, mask covering face"
    },
    "hinata": {
        "name": "Hinata Hyuga",
        "age": "18 years old adult",
        "desc": "young adult woman, 18+ years old, long dark blue hair, lavender eyes, pale skin, soft feminine curves"
    },
    "itachi": {
        "name": "Itachi Uchiha",
        "age": "21 years old adult",
        "desc": "young adult man, 21 years old, long black hair, dark eyes, pale skin, lean body, serious expression"
    },
    "brad": {
        "name": "Brad Pitt",
        "age": "60 years old adult",
        "desc": "mature adult man, 60 years old, blonde hair, blue eyes, chiseled features, muscular body, Hollywood actor"
    },
    "leonardo": {
        "name": "Leonardo DiCaprio",
        "age": "49 years old adult",
        "desc": "mature adult man, 49 years old, blonde-brown hair, blue eyes, handsome face, athletic body"
    },
    "rock": {
        "name": "Dwayne The Rock Johnson",
        "age": "51 years old adult",
        "desc": "mature adult man, 51 years old, bald head, extremely muscular massive body, tattooed, wrestler physique"
    },
    "scarlett": {
        "name": "Scarlett Johansson",
        "age": "39 years old adult",
        "desc": "mature adult woman, 39 years old, blonde hair, green eyes, beautiful face, curvy feminine body"
    },
    "margot": {
        "name": "Margot Robbie",
        "age": "34 years old adult",
        "desc": "adult woman, 34 years old, blonde hair, blue eyes, stunning beautiful face, athletic curvy body"
    },
    "emma": {
        "name": "Emma Watson",
        "age": "34 years old adult",
        "desc": "adult woman, 34 years old, brown hair, brown eyes, elegant face, slender feminine body"
    },
    "zendaya": {
        "name": "Zendaya",
        "age": "28 years old adult",
        "desc": "adult woman, 28 years old, curly brown hair, brown eyes, beautiful mixed-race face, tall slender body"
    }
}

def generate_pollination_image(prompt, width=768, height=768, retries=3):
    """Génère une image via Pollination AI avec retry"""
    
    encoded_prompt = urllib.parse.quote(prompt)
    url = f"{POLLINATION_API}/{encoded_prompt}?width={width}&height={height}&model=flux&nologo=true&enhance=true&seed={int(time.time())}"
    
    for attempt in range(retries):
        try:
            response = requests.get(url, timeout=120)
            
            if response.status_code == 200:
                return response.content
            elif response.status_code == 429:
                wait_time = 30 * (attempt + 1)
                print(f"      ⏳ Rate limit, attente {wait_time}s...")
                time.sleep(wait_time)
            else:
                print(f"      ⚠️ HTTP {response.status_code}")
                time.sleep(10)
                
        except Exception as e:
            print(f"      ❌ Erreur: {e}")
            if attempt < retries - 1:
                time.sleep(10)
    
    return None

def generate_prompts(char_key, char_data):
    """Génère 15 prompts NSFW progressifs pour un personnage ADULTE"""
    
    name = char_data["name"]
    age = char_data["age"]
    desc = char_data["desc"]
    
    prompts = []
    
    # Niveau 1-5: SENSUEL (nu artistique) - ADULTE
    base = f"{name} ({desc}), {age}, adult character"
    prompts.extend([
        f"artistic nude portrait, {base}, topless bare breasts, sensual pose, soft lighting, bedroom setting, photorealistic, 8K, adult content 18+",
        f"professional photo, {base}, naked upper body, nipples visible, sitting elegantly, intimate atmosphere, cinematic quality, adult 18+",
        f"sensual photograph, {base}, fully nude, breasts exposed, laying relaxed pose, soft shadows, detailed skin, adult photography 18+",
        f"artistic nudity, {base}, complete nakedness, frontal view, standing elegant, natural light, ultra realistic, adult content 18+",
        f"intimate portrait, {base}, nude body visible, breasts shown, relaxed bedroom scene, professional quality, adult 18+"
    ])
    
    # Niveau 6-10: SEXY (érotique) - ADULTE
    prompts.extend([
        f"erotic photo, {base}, completely naked, breasts and genitals visible, provocative pose, intimate setting, 8K detailed, adult 18+",
        f"explicit nude, {base}, topless and bottomless, pussy exposed, kneeling pose, sensual lighting, photorealistic anatomy, adult 18+",
        f"sexy photograph, {base}, full frontal nudity, vagina and breasts prominently visible, intimate bedroom, hyper realistic, adult 18+",
        f"provocative shot, {base}, naked body, nipples and pussy clearly visible, legs spread pose, cinematic quality, adult content 18+",
        f"erotic portrait, {base}, complete nudity, breasts and genitals fully exposed, seductive expression, professional adult photography 18+"
    ])
    
    # Niveau 11-15: EXPLICIT (scènes intimes) - ADULTE
    prompts.extend([
        f"explicit adult photo, {base}, engaged in intimate activity, sensual scene, bedroom setting, realistic depiction, adult content 18+",
        f"intimate moment, {base}, adult sexual scene, passionate embrace, explicit activity, photorealistic quality, adult 18+",
        f"NSFW adult content, {base}, explicit intimate scene, sexual activity, detailed realistic rendering, professional adult photography 18+",
        f"adult explicit photo, {base}, intimate sexual moment, passionate scene, bedroom atmosphere, ultra realistic, adult 18+",
        f"hardcore adult content, {base}, explicit sexual activity, intimate passionate scene, photorealistic details, professional adult content 18+"
    ])
    
    return prompts

def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    print("="*80)
    print("🔞 GÉNÉRATION GALERIES NSFW - TOUS PERSONNAGES (ADULTES 18+)")
    print("="*80)
    print(f"📊 Total: {len(CHARACTERS)} personnages × 15 images = {len(CHARACTERS) * 15} images")
    print(f"🌐 Source: Pollination AI (gratuit)")
    print(f"⏱️  Temps estimé: ~{len(CHARACTERS) * 15 * 10 / 60:.0f} min (avec delays anti-rate-limit)")
    print(f"📁 Output: {OUTPUT_DIR}")
    print("⚠️  CONTENU ADULTE 18+ UNIQUEMENT")
    print("="*80)
    
    total = len(CHARACTERS) * 15
    current = 0
    success = 0
    skipped = 0
    failed = 0
    
    start_time = time.time()
    
    for char_key, char_data in CHARACTERS.items():
        print(f"\n{'='*80}")
        print(f"👤 {char_data['name']} ({char_key}) - {char_data['age']}")
        print(f"{'='*80}")
        
        prompts = generate_prompts(char_key, char_data)
        
        for i, prompt in enumerate(prompts, 1):
            current += 1
            filename = f"{char_key}nsfw{i}.jpg"
            filepath = os.path.join(OUTPUT_DIR, filename)
            
            # Skip si existe
            if os.path.exists(filepath):
                file_size = os.path.getsize(filepath) // 1024
                print(f"  [{current}/{total}] {filename} - ✓ Existe ({file_size}KB)")
                skipped += 1
                continue
            
            print(f"  [{current}/{total}] {filename}")
            print(f"    🎨 Génération (Pollination AI)...")
            print(f"    📝 {prompt[:60]}...")
            
            # Générer
            image_data = generate_pollination_image(prompt)
            
            if image_data:
                try:
                    with open(filepath, 'wb') as f:
                        f.write(image_data)
                    file_size = os.path.getsize(filepath) // 1024
                    print(f"    ✅ Success ({file_size}KB)")
                    success += 1
                except Exception as e:
                    print(f"    ❌ Erreur sauvegarde: {e}")
                    failed += 1
            else:
                print(f"    ❌ Échec génération")
                failed += 1
            
            # Progress
            if current < total:
                elapsed = time.time() - start_time
                eta = (elapsed / current) * (total - current)
                print(f"    📊 {success} success, {skipped} skipped, {failed} failed")
                print(f"    ⏱️  Elapsed: {elapsed/60:.1f}min, ETA: {eta/60:.1f}min")
                
                # Delay anti-rate-limit (10s entre chaque)
                delay = 10
                if failed > 0 and current % 5 == 0:
                    delay = 20  # Plus long si échecs
                
                print(f"    💤 Attente {delay}s...")
                time.sleep(delay)
    
    # Résumé
    elapsed = time.time() - start_time
    print(f"\n{'='*80}")
    print(f"✨ GÉNÉRATION TERMINÉE!")
    print(f"{'='*80}")
    print(f"✅ Success: {success}/{total}")
    print(f"⊘ Skipped: {skipped}/{total}")
    print(f"❌ Failed: {failed}/{total}")
    print(f"⏱️  Total: {elapsed/60:.1f} minutes")
    print(f"📁 Output: {OUTPUT_DIR}")
    
    # Vérification finale
    existing = len([f for f in os.listdir(OUTPUT_DIR) if 'nsfw' in f and f.endswith('.jpg')])
    print(f"\n📊 Images NSFW totales: {existing}/{total}")
    
    if existing >= total * 0.9:
        print("🎉 COMPLET! Toutes les galeries NSFW sont prêtes!")
    elif existing >= total * 0.5:
        print("⚠️ PARTIEL: Plus de 50% générées, relancer pour compléter")
    else:
        print("❌ INCOMPLET: Moins de 50% générées, problème réseau possible")

if __name__ == "__main__":
    main()
