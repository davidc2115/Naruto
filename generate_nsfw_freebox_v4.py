#!/usr/bin/env python3
"""
Génération MASSIVE d'images NSFW via Freebox Stable Diffusion WebUI
URL: http://88.174.155.230:7860
195 images (13 personnages × 15 images)
"""

import requests
import time
import os
import base64
import json

FREEBOX_URL = "http://88.174.155.230:33437"
OUTPUT_DIR = "/workspace/app/src/main/res/drawable-nodpi"

# Définir personnages avec descriptions ULTRA DÉTAILLÉES
CHARACTERS = {
    "naruto": {
        "name": "Naruto Uzumaki",
        "description": "young adult man, spiky blonde hair, bright blue eyes, tanned skin, 3 whisker marks on each cheek, confident smile, athletic muscular body, orange jacket, ninja headband"
    },
    "sasuke": {
        "name": "Sasuke Uchiha", 
        "description": "young adult man, black spiky hair pointing backwards, dark onyx eyes, pale white skin, sharp facial features, lean muscular athletic body, intense gaze, sharingan red eyes"
    },
    "sakura": {
        "name": "Sakura Haruno",
        "description": "young adult woman, pink hair medium length, bright green eyes, fair skin, athletic feminine body, soft facial features, red dress, ninja gloves"
    },
    "kakashi": {
        "name": "Kakashi Hatake",
        "description": "mature adult man, silver-grey spiky hair, one visible dark eye (other covered), fair skin, lean muscular body, mysterious look, mask covering lower face, ninja uniform"
    },
    "hinata": {
        "name": "Hinata Hyuga",
        "description": "young adult woman, long dark blue hair, lavender white eyes (byakugan), pale skin, gentle features, soft curves, shy expression, purple jacket, ninja headband"
    },
    "itachi": {
        "name": "Itachi Uchiha",
        "description": "young adult man, long black hair tied back, dark eyes with red sharingan, pale skin, lean body, serious expression, lines under eyes, black cloak with red clouds"
    },
    "brad": {
        "name": "Brad Pitt",
        "description": "Brad Pitt, adult man, blonde hair, piercing blue eyes, chiseled jawline, muscular athletic body, Hollywood actor, charming smile"
    },
    "leonardo": {
        "name": "Leonardo DiCaprio",
        "description": "Leonardo DiCaprio, adult man, blonde-brown hair, blue eyes, handsome face, athletic body, Hollywood actor, famous star"
    },
    "rock": {
        "name": "The Rock",
        "description": "Dwayne The Rock Johnson, adult man, bald head, brown eyes, Samoan heritage, extremely muscular massive bodybuilder physique, tattooed left shoulder and chest, wrestler actor"
    },
    "scarlett": {
        "name": "Scarlett Johansson",
        "description": "Scarlett Johansson, adult woman, blonde hair, green eyes, beautiful face, curvy feminine body, Hollywood actress, stunning star"
    },
    "margot": {
        "name": "Margot Robbie",
        "description": "Margot Robbie, adult woman, blonde hair, blue eyes, stunning beautiful face, athletic curvy body, Hollywood actress, famous star"
    },
    "emma": {
        "name": "Emma Watson",
        "description": "Emma Watson, adult woman, brown hair, brown eyes, beautiful elegant face, slender feminine body, Hollywood actress, famous star"
    },
    "zendaya": {
        "name": "Zendaya",
        "description": "Zendaya, adult woman, curly brown hair, brown eyes, beautiful mixed-race face, tall slender body, Hollywood actress, model, famous star"
    }
}

def check_freebox_available():
    """Vérifie si la Freebox SD WebUI est accessible"""
    try:
        response = requests.get(f"{FREEBOX_URL}/", timeout=5)
        return response.status_code == 200
    except:
        return False

def generate_prompts(char_key, char_data):
    """Génère 15 prompts NSFW explicites par personnage"""
    name = char_data["name"]
    desc = char_data["description"]
    
    prompts = []
    
    # Images 1-5: SENSUEL (Nu artistique)
    prompts.extend([
        f"masterpiece, best quality, ultra detailed, {name} ({desc}), topless, bare breasts visible, artistic nude pose, soft lighting, intimate bedroom setting, sensual expression, photorealistic, 8K",
        f"high quality photograph, {name} ({desc}), naked upper body, nipples visible, lying on silk sheets, seductive look, cinematic lighting, extremely detailed skin texture",
        f"professional photo, {name} ({desc}), fully naked, breasts and pussy visible, sitting pose, soft shadows, intimate room, photorealistic details, 8K resolution",
        f"artistic nude portrait, {name} ({desc}), completely naked, full frontal nudity, standing elegant pose, natural lighting, bedroom background, ultra realistic rendering",
        f"sensual photograph, {name} ({desc}), nude body, breasts exposed nipples visible, laying down relaxed, intimate atmosphere, professional photography, hyper detailed"
    ])
    
    # Images 6-10: SEXY (Nu érotique)
    prompts.extend([
        f"erotic photo, {name} ({desc}), completely naked, breasts and vagina clearly visible, legs spread open, intimate bedroom, seductive facial expression, photorealistic, 8K ultra detailed",
        f"explicit nude photo, {name} ({desc}), topless and bottomless, pussy exposed genitals visible, kneeling provocative pose, sensual lighting, ultra detailed anatomy, professional quality",
        f"high quality erotic shot, {name} ({desc}), full nudity, breasts and vagina prominently visible, provocative pose, intimate setting, 8K resolution, hyper realistic",
        f"sexy photograph, {name} ({desc}), naked body, nipples and pussy clearly visible, sitting with legs open wide, bedroom scene, cinematic quality, hyper realistic details",
        f"provocative nude, {name} ({desc}), completely naked, full frontal view, breasts and genitals fully exposed, intimate lighting, photorealistic details, 8K quality"
    ])
    
    # Images 11-15: NSFW-EXPLICIT (Scènes sexuelles)
    prompts.extend([
        f"explicit sexual photo, {name} ({desc}), penetration visible, sexual intercourse scene, intimate bedroom, realistic sex depiction, pornographic style, 8K ultra detailed",
        f"hardcore photograph, {name} ({desc}), engaged in sex act, vaginal penetration clearly visible, explicit sexual activity, intimate lighting, photorealistic, professional adult content",
        f"NSFW explicit photo, {name} ({desc}), during intercourse, penetration prominently visible, sexual activity, bedroom setting, ultra realistic details, 8K resolution",
        f"sexual scene photo, {name} ({desc}), having sex, genitals connected visible, explicit penetration, intimate moment, cinematic pornographic quality, hyper detailed",
        f"pornographic photo, {name} ({desc}), explicit sexual intercourse, vaginal penetration clearly visible, realistic sex scene, professional adult content photography, 8K quality"
    ])
    
    return prompts

def generate_image_freebox(prompt, negative_prompt="low quality, blurry, distorted, ugly, deformed, censored", steps=25, cfg=7.5, width=768, height=768):
    """Génère une image via Freebox Stable Diffusion WebUI"""
    
    payload = {
        "prompt": prompt,
        "negative_prompt": negative_prompt,
        "steps": steps,
        "cfg_scale": cfg,
        "width": width,
        "height": height,
        "sampler_name": "Euler a",
        "batch_size": 1,
        "n_iter": 1,
        "seed": -1,
        "enable_hr": False
    }
    
    try:
        response = requests.post(
            f"{FREEBOX_URL}/sdapi/v1/txt2img",
            json=payload,
            timeout=180  # 3 minutes max
        )
        
        if response.status_code == 200:
            result = response.json()
            if result.get("images") and len(result["images"]) > 0:
                return result["images"][0]  # Base64 image
        
        return None
        
    except Exception as e:
        print(f"    ✗ Erreur Freebox: {e}")
        return None

def save_image(base64_str, output_path):
    """Sauvegarde une image base64 en fichier JPG"""
    try:
        image_data = base64.b64decode(base64_str)
        with open(output_path, 'wb') as f:
            f.write(image_data)
        return True
    except Exception as e:
        print(f"    ✗ Erreur sauvegarde: {e}")
        return False

def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    # Vérifier Freebox accessible
    print("🔍 Vérification accès Freebox...")
    if not check_freebox_available():
        print("❌ ERREUR: Freebox SD WebUI non accessible à http://88.174.155.230:7860")
        print("💡 Vérifiez que:")
        print("   1. Le service est démarré sur la Freebox")
        print("   2. Le port 7860 est ouvert")
        print("   3. Vous avez accès Internet")
        return
    
    print("✅ Freebox accessible!\n")
    
    total_images = len(CHARACTERS) * 15
    current = 0
    success = 0
    skipped = 0
    failed = 0
    
    print(f"🚀 GÉNÉRATION MASSIVE: {total_images} images NSFW explicites")
    print(f"📡 Source: Freebox Stable Diffusion WebUI")
    print(f"⏱️  Temps estimé par image: ~30-60s")
    print(f"⏱️  Durée totale estimée: ~{total_images * 45 / 3600:.1f}h")
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
            print(f"  Prompt: {prompt[:80]}...")
            print(f"  🎨 Génération en cours (Freebox)...")
            
            # Générer
            base64_image = generate_image_freebox(prompt)
            
            if base64_image:
                if save_image(base64_image, output_path):
                    file_size = os.path.getsize(output_path)
                    print(f"  ✓ Success: {file_size // 1024} KB")
                    success += 1
                else:
                    failed += 1
            else:
                failed += 1
                print(f"  ✗ FAILED")
            
            # Délai entre images (ne pas surcharger)
            if current < total_images:
                delay = 5  # 5s entre chaque
                print(f"\n  ⏱️  Waiting {delay}s...")
                print(f"  📊 Progress: {success} success, {skipped} skipped, {failed} failed")
                elapsed = time.time() - start_time
                eta = (elapsed / current) * (total_images - current)
                print(f"  ⏱️  Elapsed: {elapsed/60:.1f}min, ETA: {eta/60:.1f}min")
                time.sleep(delay)
    
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
