#!/usr/bin/env python3
"""
Génération de VRAIES images hyper-réalistes pour chaque personnage
Utilise Pollination AI avec prompts ULTRA-DÉTAILLÉS tirés de Characters.kt
Télécharge les images dans character_images/
"""

import requests
import json
import time
import os
from pathlib import Path
from urllib.parse import quote

# Dossier de sortie
OUTPUT_DIR = "character_images"
Path(OUTPUT_DIR).mkdir(exist_ok=True)

# Prompts ULTRA-DÉTAILLÉS basés sur Characters.kt
CHARACTERS = {
    "Naruto": {
        "base_prompt": "close-up portrait of Naruto Uzumaki, 17 year old male ninja, spiky bright golden blonde hair sticking up like hedgehog spikes, vivid ocean blue eyes full of determination, six thin whisker marks on cheeks (3 per cheek like cat whiskers), athletic muscular but lean build, confident smile, orange and black ninja jacket with high collar, metal headband with leaf symbol on forehead, energetic expression, tan skin, scars on hands from training",
        "style": "anime art, manga style, vibrant colors",
        "quality": "detailed anime character, best quality, masterpiece, 8k",
        "variations": [
            "front view, looking at camera with big smile",
            "side profile, determined expression",
            "three quarter view, energetic pose",
            "close-up face, laughing cheerfully",
            "action pose, ready to fight",
            "sitting eating ramen, happy expression",
            "training pose, serious face",
            "victory sign gesture, winking",
            "running forward, dynamic movement",
            "hands in pockets, cool confident look"
        ]
    },
    
    "Sasuke": {
        "base_prompt": "close-up portrait of Sasuke Uchiha, 17 year old male ninja, spiky jet black hair swept backwards, very pale porcelain skin, deep black eyes with intense cold stare, aristocratic sharp facial features, serious brooding expression, dark blue high-collar shirt with Uchiha clan symbol, metal headband, handsome but intimidating aura, lean athletic build",
        "style": "anime art, manga style, dramatic shading",
        "quality": "detailed anime character, best quality, masterpiece, 8k",
        "variations": [
            "front view, cold intense stare",
            "side profile, aloof expression",
            "Sharingan activated eyes glowing red with tomoe",
            "close-up face, serious determined look",
            "action pose with sword, dramatic",
            "standing with arms crossed, cool",
            "lightning jutsu hand signs, focused",
            "looking back over shoulder, mysterious",
            "moonlight dramatic lighting, brooding",
            "intense battle expression, powerful"
        ]
    },
    
    "Sakura": {
        "base_prompt": "close-up portrait of Sakura Haruno, 17 year old female ninja, long bright pink hair in high ponytail, vibrant green emerald eyes, fair delicate skin, athletic feminine build, determined confident expression, red and black combat outfit, medical ninja gear, beautiful strong features, Inner Sakura symbol on headband",
        "style": "anime art, manga style, vibrant colors",
        "quality": "detailed anime character, best quality, masterpiece, 8k",
        "variations": [
            "front view, confident smile",
            "side profile, determined look",
            "three quarter view, medical ninjutsu pose",
            "close-up face, kind gentle expression",
            "action fighting pose, powerful",
            "healing jutsu green chakra glow",
            "serious battle expression, fierce",
            "cheerful happy smile, friendly",
            "training outfit, athletic pose",
            "thinking pose hand on chin"
        ]
    },
    
    "Kakashi": {
        "base_prompt": "close-up portrait of Kakashi Hatake, 26 year old male ninja, spiky silver-gray hair defying gravity, left eye covered by metal headband tilted, right eye visible dark, black mask covering lower face from nose down, relaxed mysterious expression with eye smiling, dark blue ninja vest, holding orange Icha Icha book, cool laid-back aura",
        "style": "anime art, manga style, cool aesthetic",
        "quality": "detailed anime character, best quality, masterpiece, 8k",
        "variations": [
            "front view, relaxed eye smile",
            "side profile, reading book",
            "Sharingan eye revealed glowing red",
            "close-up visible eye, mysterious",
            "action pose with lightning jutsu",
            "sitting casually, laid back",
            "teaching pose, wise expression",
            "battle ready stance, serious",
            "peace sign gesture, cheerful",
            "leaning against wall, cool"
        ]
    },
    
    "Hinata": {
        "base_prompt": "close-up portrait of Hinata Hyuga, 17 year old female ninja, very long silky dark blue-black hair reaching hips, pale lavender-white pearl Byakugan eyes, extremely pale delicate porcelain skin, shy gentle expression with blushing pink cheeks, lavender and beige traditional outfit, timid sweet demeanor, elegant graceful posture, soft features",
        "style": "anime art, manga style, soft romantic aesthetic",
        "quality": "detailed anime character, best quality, masterpiece, 8k",
        "variations": [
            "front view, shy smile blushing",
            "side profile, elegant graceful",
            "Byakugan activated eyes with veins",
            "close-up face, gentle kind expression",
            "Gentle Fist fighting stance, determined",
            "looking down shyly, timid",
            "training pose, focused serious",
            "happy confident smile, grown",
            "protective stance, brave",
            "hands together nervous, cute"
        ]
    },
    
    "Itachi": {
        "base_prompt": "close-up portrait of Itachi Uchiha, 21 year old male, long jet black hair in low ponytail, stress lines under eyes, very pale sickly skin, deep black eyes with sorrowful gaze, melancholic sad expression, black cloak with red clouds Akatsuki uniform, metal headband with slash mark, tragic aura, handsome but weary features",
        "style": "anime art, manga style, dark dramatic",
        "quality": "detailed anime character, best quality, masterpiece, 8k",
        "variations": [
            "front view, sad melancholic expression",
            "side profile, tired weary look",
            "Mangekyō Sharingan activated glowing red",
            "close-up face, sorrowful eyes",
            "moonlight dramatic lighting, tragic",
            "standing in shadows, mysterious",
            "hand reaching out, emotional",
            "looking back, regretful",
            "wind blowing hair, cinematic",
            "serious battle expression, powerful"
        ]
    },
    
    "Brad_Pitt": {
        "base_prompt": "close-up portrait of Brad Pitt at age 60, golden blonde hair with distinguished gray streaks, piercing bright blue eyes, chiseled square jawline, sun-kissed tan skin, charming confident smile with wrinkles showing maturity, masculine mature features, casual stylish clothing, Hollywood star presence, warm friendly expression",
        "style": "photorealistic portrait, professional photography, 8k uhd",
        "quality": "sharp focus, detailed face, natural skin texture, studio lighting, hyper realistic",
        "variations": [
            "front view, confident charismatic smile",
            "side profile, distinguished handsome",
            "three quarter view, movie star look",
            "close-up face, intense blue eyes",
            "casual suit, elegant sophisticated",
            "laughing cheerfully, charming",
            "serious thoughtful expression, mature",
            "outdoor natural lighting, relaxed",
            "golden hour lighting, cinematic",
            "professional headshot, Hollywood"
        ]
    },
    
    "Leonardo_DiCaprio": {
        "base_prompt": "close-up portrait of Leonardo DiCaprio at age 49, golden blonde hair styled back, intense piercing blue eyes, mature masculine handsome features, passionate determined expression, elegant dark suit, environmental activist vibe, mature Hollywood leading man, charismatic presence, facial hair light beard",
        "style": "photorealistic portrait, professional photography, 8k uhd",
        "quality": "sharp focus, detailed face, natural skin texture, studio lighting, hyper realistic",
        "variations": [
            "front view, passionate intense stare",
            "side profile, mature distinguished",
            "three quarter view, thoughtful",
            "close-up face, determined expression",
            "tuxedo formal wear, elegant",
            "smiling warmly, charming",
            "serious environmental activist look",
            "outdoor nature setting, natural",
            "dramatic cinematic lighting, powerful",
            "professional actor headshot"
        ]
    },
    
    "Dwayne_Johnson": {
        "base_prompt": "close-up portrait of Dwayne The Rock Johnson at age 51, completely bald shaved head, brown eyes with iconic raised eyebrow, massive muscular build showing arms, large detailed Polynesian tribal tattoo visible on left arm and shoulder, tan Samoan skin, huge charismatic smile showing teeth, athletic gym wear, intimidating yet friendly presence, powerful physique",
        "style": "photorealistic portrait, professional photography, 8k uhd",
        "quality": "sharp focus, detailed face, natural skin texture, dramatic lighting, hyper realistic",
        "variations": [
            "front view, charismatic huge smile",
            "side profile, showing tattoo detail",
            "three quarter view, raised eyebrow look",
            "close-up face, intense powerful stare",
            "flexing muscles pose, strong",
            "laughing cheerfully, friendly",
            "serious tough expression, intimidating",
            "gym workout scene, athletic",
            "dramatic hero lighting, cinematic",
            "professional action star headshot"
        ]
    },
    
    "Scarlett_Johansson": {
        "base_prompt": "close-up portrait of Scarlett Johansson at age 39, platinum blonde wavy hair, magnetic green eyes, full sensual lips, fair luminous glowing skin, athletic feminine curves, confident sensual expression, elegant black dress, Hollywood glamour style, powerful beautiful features, sophisticated mature beauty",
        "style": "photorealistic portrait, professional photography, 8k uhd",
        "quality": "sharp focus, detailed face, natural skin texture, studio glamour lighting, hyper realistic",
        "variations": [
            "front view, confident sensual look",
            "side profile, elegant glamorous",
            "three quarter view, mysterious smile",
            "close-up face, intense green eyes",
            "red carpet elegant dress, stunning",
            "natural beauty minimal makeup, gorgeous",
            "serious powerful expression, strong",
            "laughing joyfully, charming",
            "golden hour soft lighting, beautiful",
            "professional actress headshot, iconic"
        ]
    },
    
    "Margot_Robbie": {
        "base_prompt": "close-up portrait of Margot Robbie at age 34, golden blonde wavy hair, sparkling bright blue eyes, radiant huge cheerful smile, sun-kissed Australian tan skin, perfect beautiful features, fun energetic joyful vibe, stylish modern trendy outfit, Barbie-like stunning beauty, charismatic friendly presence, flawless complexion",
        "style": "photorealistic portrait, professional photography, 8k uhd",
        "quality": "sharp focus, detailed face, natural skin texture, bright cheerful lighting, hyper realistic",
        "variations": [
            "front view, huge radiant smile",
            "side profile, stunning beautiful",
            "three quarter view, cheerful happy",
            "close-up face, sparkling blue eyes",
            "pink Barbie style outfit, iconic",
            "laughing joyfully, fun energy",
            "glamorous red carpet, elegant",
            "casual beach style, natural beauty",
            "golden sunshine lighting, glowing",
            "professional actress headshot, gorgeous"
        ]
    },
    
    "Emma_Watson": {
        "base_prompt": "close-up portrait of Emma Watson at age 34, short elegant brown pixie cut hair, expressive intelligent brown eyes, fair British skin, refined elegant sophisticated features, poised graceful posture, sophisticated minimalist black outfit, feminist activist aura, mature Hermione grown up, gentle warm kind expression, natural beauty",
        "style": "photorealistic portrait, professional photography, 8k uhd",
        "quality": "sharp focus, detailed face, natural skin texture, soft elegant lighting, hyper realistic",
        "variations": [
            "front view, intelligent warm smile",
            "side profile, elegant sophisticated",
            "three quarter view, poised graceful",
            "close-up face, expressive brown eyes",
            "formal elegant dress, refined",
            "casual chic style, natural",
            "serious thoughtful expression, activist",
            "smiling gently, kind friendly",
            "soft natural lighting, beautiful",
            "professional actress headshot, iconic"
        ]
    },
    
    "Zendaya": {
        "base_prompt": "close-up portrait of Zendaya at age 28, long curly voluminous brown hair, hazel expressive striking eyes, caramel glowing radiant skin, tall elegant model slender build, confident modern fierce expression, avant-garde high fashion outfit, Gen Z icon style, stunning mixed-race features, fierce model gaze, powerful presence",
        "style": "photorealistic portrait, professional photography, 8k uhd",
        "quality": "sharp focus, detailed face, natural skin texture, high fashion lighting, hyper realistic",
        "variations": [
            "front view, fierce confident model gaze",
            "side profile, elegant striking",
            "three quarter view, powerful pose",
            "close-up face, hazel eyes intense",
            "haute couture fashion, stunning",
            "smiling beautifully, warm friendly",
            "serious powerful expression, strong",
            "natural curly hair glory, gorgeous",
            "dramatic editorial lighting, cinematic",
            "professional model headshot, iconic"
        ]
    }
}


def generate_image_url(character_name, base_prompt, variation, style, quality, model="flux-realism"):
    """
    Génère l'URL Pollination AI pour une image
    """
    full_prompt = f"{base_prompt}, {variation}, {style}, {quality}"
    encoded_prompt = quote(full_prompt)
    
    # URL Pollination AI
    url = f"https://image.pollinations.ai/prompt/{encoded_prompt}?width=768&height=1024&model={model}&enhance=true&nologo=true&seed={int(time.time()*1000)}"
    
    return url


def download_image(url, filename):
    """
    Télécharge une image depuis une URL
    """
    try:
        print(f"  📥 Téléchargement: {filename}")
        response = requests.get(url, timeout=60)
        response.raise_for_status()
        
        with open(filename, 'wb') as f:
            f.write(response.content)
        
        size_kb = os.path.getsize(filename) / 1024
        print(f"  ✅ Sauvegardé: {size_kb:.1f} KB")
        return True
        
    except Exception as e:
        print(f"  ❌ Erreur: {e}")
        return False


def generate_character_gallery(char_name, char_data, count=10):
    """
    Génère une galerie de {count} images pour un personnage
    """
    print(f"\n{'='*70}")
    print(f"🎨 {char_name}")
    print(f"{'='*70}")
    
    images = []
    model = "flux" if "anime" in char_data["style"] else "flux-realism"
    
    for i, variation in enumerate(char_data["variations"][:count], 1):
        print(f"\n  Image {i}/{count}: {variation}")
        
        # Générer URL
        url = generate_image_url(
            char_name,
            char_data["base_prompt"],
            variation,
            char_data["style"],
            char_data["quality"],
            model
        )
        
        # Télécharger
        filename = f"{OUTPUT_DIR}/{char_name.lower().replace(' ', '_')}_{i}.png"
        success = download_image(url, filename)
        
        if success:
            images.append({
                "index": i,
                "variation": variation,
                "url": url,
                "filename": filename
            })
        
        # Pause entre images
        time.sleep(2)
    
    return images


def main():
    print("""
╔═══════════════════════════════════════════════════════════════╗
║       GÉNÉRATION D'IMAGES HYPER-RÉALISTES v3.0             ║
║   Télécharge 10 vraies images par personnage (130 total)    ║
╚═══════════════════════════════════════════════════════════════╝
    """)
    
    all_results = {}
    total_images = 0
    
    for char_name, char_data in CHARACTERS.items():
        images = generate_character_gallery(char_name, char_data, count=10)
        all_results[char_name] = {
            "base_prompt": char_data["base_prompt"],
            "style": char_data["style"],
            "images": images,
            "count": len(images)
        }
        total_images += len(images)
    
    # Sauvegarder métadonnées JSON
    with open("character_images_metadata.json", 'w', encoding='utf-8') as f:
        json.dump(all_results, f, indent=2, ensure_ascii=False)
    
    # Générer code Kotlin pour Characters.kt
    print("\n" + "="*70)
    print("📝 Génération du code Kotlin...")
    
    kotlin_code = []
    for char_name, data in all_results.items():
        urls = [img["url"] for img in data["images"]]
        kotlin_urls = ",\n            ".join([f'"{url}"' for url in urls])
        
        kotlin_code.append(f"""
// {char_name} - {data['count']} images
gallery = listOf(
            {kotlin_urls}
        )""")
    
    with open("gallery_kotlin_code.txt", 'w', encoding='utf-8') as f:
        f.write("\n\n".join(kotlin_code))
    
    # Résumé final
    print("\n" + "="*70)
    print("🎉 GÉNÉRATION TERMINÉE!")
    print("="*70)
    print(f"📊 Statistiques:")
    print(f"   - Personnages: {len(all_results)}")
    print(f"   - Images totales: {total_images}")
    print(f"   - Dossier images: {OUTPUT_DIR}/")
    print(f"   - Métadonnées: character_images_metadata.json")
    print(f"   - Code Kotlin: gallery_kotlin_code.txt")
    print("="*70)
    
    print("\n✅ Prochaines étapes:")
    print("   1. Vérifie les images dans character_images/")
    print("   2. Copie le code Kotlin de gallery_kotlin_code.txt")
    print("   3. Remplace les gallery dans Characters.kt")
    print("   4. Build et test l'app!\n")


if __name__ == "__main__":
    main()
