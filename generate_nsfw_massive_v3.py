#!/usr/bin/env python3
"""
Génération ULTRA MASSIVE d'images NSFW explicites
v3.0 - 195 images (13 personnages × 15 images)
Délai: 120s entre chaque (2 minutes)
Durée estimée: ~6h30
"""

import requests
import time
import os
from urllib.parse import quote

API_URL = "https://image.pollinations.ai/prompt"
OUTPUT_DIR = "/workspace/app/src/main/res/drawable-nodpi"

# Définir caractères avec DESCRIPTIONS ULTRA DÉTAILLÉES
CHARACTERS = {
    "naruto": {
        "name": "Naruto Uzumaki",
        "description": "young muscular man, spiky blonde hair, blue eyes, tanned skin, whisker marks on cheeks, confident smile, athletic body"
    },
    "sasuke": {
        "name": "Sasuke Uchiha", 
        "description": "young handsome man, black spiky hair, dark eyes, pale skin, sharp facial features, lean muscular body, intense gaze"
    },
    "sakura": {
        "name": "Sakura Haruno",
        "description": "young beautiful woman, pink hair, green eyes, fair skin, athletic feminine body, soft facial features"
    },
    "kakashi": {
        "name": "Kakashi Hatake",
        "description": "mature handsome man, silver-grey spiky hair, one visible dark eye, fair skin, lean muscular body, mysterious look"
    },
    "hinata": {
        "name": "Hinata Hyuga",
        "description": "young beautiful woman, long dark blue hair, lavender eyes, pale skin, gentle features, soft curves, shy expression"
    },
    "itachi": {
        "name": "Itachi Uchiha",
        "description": "young handsome man, long black hair, dark eyes with red sharingan, pale skin, lean body, serious expression"
    },
    "brad": {
        "name": "Brad Pitt",
        "description": "Brad Pitt, blonde hair, blue eyes, chiseled jawline, muscular body, famous actor, Hollywood star"
    },
    "leonardo": {
        "name": "Leonardo DiCaprio",
        "description": "Leonardo DiCaprio, blonde hair, blue eyes, handsome face, athletic body, famous actor, Hollywood star"
    },
    "rock": {
        "name": "The Rock",
        "description": "Dwayne The Rock Johnson, bald head, brown eyes, Samoan heritage, extremely muscular bodybuilder physique, famous wrestler actor"
    },
    "scarlett": {
        "name": "Scarlett Johansson",
        "description": "Scarlett Johansson, blonde hair, green eyes, beautiful face, curvy body, famous actress, Hollywood star"
    },
    "margot": {
        "name": "Margot Robbie",
        "description": "Margot Robbie, blonde hair, blue eyes, stunning face, athletic body, famous actress, Hollywood star"
    },
    "emma": {
        "name": "Emma Watson",
        "description": "Emma Watson, brown hair, brown eyes, beautiful face, slender body, famous actress, Hollywood star"
    },
    "zendaya": {
        "name": "Zendaya",
        "description": "Zendaya, curly brown hair, brown eyes, beautiful mixed-race face, tall slender body, famous actress, model"
    }
}

def generate_prompts(char_key, char_data):
    """Génère 15 prompts NSFW explicites par personnage"""
    name = char_data["name"]
    desc = char_data["description"]
    
    prompts = []
    
    # Images 1-5: SENSUEL (Nu artistique)
    prompts.extend([
        f"Hyper-realistic photo of {name} ({desc}), topless, breasts visible, artistic nude pose, soft lighting, intimate bedroom, sensual expression, 8K ultra detailed",
        f"Professional photo of {name} ({desc}), naked from waist up, nipples visible, lying on silk sheets, seductive look, cinematic lighting, extremely detailed",
        f"High quality photo of {name} ({desc}), fully naked, breasts and pussy visible, sitting pose, soft shadows, intimate setting, photorealistic, 8K",
        f"Artistic nude photo of {name} ({desc}), completely naked, full frontal nudity, standing pose, natural lighting, bedroom background, ultra realistic",
        f"Sensual photo of {name} ({desc}), nude body, breasts exposed, laying down, intimate atmosphere, professional photography, hyper detailed"
    ])
    
    # Images 6-10: SEXY (Nu érotique)
    prompts.extend([
        f"Erotic photo of {name} ({desc}), completely naked, breasts and vagina visible, legs spread, intimate bedroom, seductive expression, photorealistic, 8K",
        f"Explicit nude photo of {name} ({desc}), topless and bottomless, pussy exposed, kneeling pose, sensual lighting, ultra detailed genitals, professional",
        f"High quality erotic shot of {name} ({desc}), full nudity, breasts and vagina clearly visible, provocative pose, intimate setting, 8K resolution",
        f"Sexy photo of {name} ({desc}), naked body, nipples and pussy visible, sitting with legs open, bedroom scene, cinematic quality, hyper realistic",
        f"Provocative nude of {name} ({desc}), completely naked, full frontal, breasts and genitals exposed, intimate lighting, photorealistic details, 8K"
    ])
    
    # Images 11-15: NSFW-EXPLICIT (Scènes sexuelles)
    prompts.extend([
        f"Explicit sexual photo of {name} ({desc}), penetration visible, sexual intercourse, intimate bedroom, realistic sex scene, pornographic style, 8K ultra detailed",
        f"Hardcore photo of {name} ({desc}), engaged in sex, vaginal penetration visible, explicit sexual act, intimate lighting, photorealistic, professional pornography",
        f"NSFW explicit photo of {name} ({desc}), during intercourse, penetration clearly visible, sexual activity, bedroom setting, ultra realistic details, 8K",
        f"Sexual scene photo of {name} ({desc}), having sex, genitals connected, explicit penetration, intimate moment, cinematic pornographic quality, hyper detailed",
        f"Pornographic photo of {name} ({desc}), explicit sexual intercourse, vaginal penetration visible, realistic sex scene, professional adult content, 8K resolution"
    ])
    
    return prompts

def download_image(prompt, output_path, max_retries=5):
    """Télécharge une image depuis Pollination AI avec retry"""
    
    for attempt in range(1, max_retries + 1):
        try:
            # Construire URL
            encoded_prompt = quote(prompt)
            url = f"{API_URL}/{encoded_prompt}?width=1024&height=1024&model=flux&nologo=true&enhance=true&seed={int(time.time())}"
            
            print(f"  [Attempt {attempt}/{max_retries}] Downloading...")
            
            # Télécharger avec timeout étendu
            response = requests.get(url, timeout=60)
            
            if response.status_code == 200:
                # Vérifier taille
                if len(response.content) > 10000:  # Au moins 10KB
                    with open(output_path, 'wb') as f:
                        f.write(response.content)
                    print(f"  ✓ Success: {len(response.content)} bytes")
                    return True
                else:
                    print(f"  ✗ Image too small: {len(response.content)} bytes")
            elif response.status_code == 429:
                wait_time = 60 * (2 ** (attempt - 1))  # 60s, 120s, 240s, 480s, 960s
                print(f"  ⚠ Rate limit! Waiting {wait_time}s...")
                time.sleep(wait_time)
                continue
            elif response.status_code >= 500:
                wait_time = 30 * (2 ** (attempt - 1))  # 30s, 60s, 120s, 240s, 480s
                print(f"  ⚠ Server error {response.status_code}! Waiting {wait_time}s...")
                time.sleep(wait_time)
                continue
            else:
                print(f"  ✗ HTTP {response.status_code}")
                
        except Exception as e:
            print(f"  ✗ Error: {e}")
            time.sleep(10)
    
    return False

def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    total_images = len(CHARACTERS) * 15
    current = 0
    success = 0
    skipped = 0
    failed = 0
    
    print(f"🚀 GÉNÉRATION MASSIVE: {total_images} images NSFW explicites")
    print(f"⏱️  Délai entre images: 120s (2 minutes)")
    print(f"⏱️  Durée estimée: ~{total_images * 2 / 60:.1f}h")
    print("="*80)
    
    start_time = time.time()
    
    for char_key, char_data in CHARACTERS.items():
        print(f"\n{'='*80}")
        print(f"👤 CHARACTER: {char_data['name']} ({char_key})")
        print(f"{'='*80}")
        
        prompts = generate_prompts(char_key, char_data)
        
        for i, prompt in enumerate(prompts, 1):
            current += 1
            output_file = f"{char_key}nsfw{i}.jpg"
            output_path = os.path.join(OUTPUT_DIR, output_file)
            
            # Skip si existe déjà
            if os.path.exists(output_path):
                print(f"\n[{current}/{total_images}] {output_file}")
                print(f"  ⊘ Already exists, skipping")
                skipped += 1
                continue
            
            print(f"\n[{current}/{total_images}] {output_file}")
            print(f"  Prompt: {prompt[:100]}...")
            
            # Télécharger
            if download_image(prompt, output_path):
                success += 1
            else:
                failed += 1
                print(f"  ✗ FAILED after all retries")
            
            # Délai avant prochaine image (sauf dernière)
            if current < total_images:
                print(f"\n  ⏱️  Waiting 120s before next image...")
                print(f"  📊 Progress: {success} success, {skipped} skipped, {failed} failed")
                elapsed = time.time() - start_time
                eta = (elapsed / current) * (total_images - current)
                print(f"  ⏱️  Elapsed: {elapsed/60:.1f}min, ETA: {eta/60:.1f}min")
                time.sleep(120)  # 2 minutes
    
    # Résumé final
    elapsed = time.time() - start_time
    print(f"\n{'='*80}")
    print(f"✨ GÉNÉRATION TERMINÉE!")
    print(f"{'='*80}")
    print(f"✓ Success: {success}/{total_images}")
    print(f"⊘ Skipped: {skipped}/{total_images}")
    print(f"✗ Failed: {failed}/{total_images}")
    print(f"⏱️  Total time: {elapsed/60:.1f} minutes ({elapsed/3600:.1f} hours)")
    print(f"📁 Output: {OUTPUT_DIR}")

if __name__ == "__main__":
    main()
