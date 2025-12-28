#!/usr/bin/env python3
"""
Génération des URLs Pollination AI pour toutes les images
SANS téléchargement - les URLs seront utilisées directement dans l'app
"""

import json
from urllib.parse import quote
import time

# Prompts ULTRA-DÉTAILLÉS basés sur Characters.kt
CHARACTERS = {
    "naruto": {
        "base_prompt": "close-up portrait of Naruto Uzumaki, 17 year old male ninja, spiky bright golden blonde hair sticking up like hedgehog spikes, vivid ocean blue eyes full of determination, six thin whisker marks on cheeks (3 per cheek like cat whiskers), athletic muscular but lean build, confident smile, orange and black ninja jacket with high collar, metal headband with leaf symbol on forehead",
        "style": "anime art, manga style, vibrant colors, detailed anime character, best quality, masterpiece",
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
    
    "sasuke": {
        "base_prompt": "close-up portrait of Sasuke Uchiha, 17 year old male ninja, spiky jet black hair swept backwards, very pale porcelain skin, deep black eyes with intense cold stare, aristocratic sharp facial features, serious brooding expression, dark blue high-collar shirt, metal headband",
        "style": "anime art, manga style, dramatic shading, detailed anime character, best quality, masterpiece",
        "variations": [
            "front view, cold intense stare",
            "side profile, aloof expression",
            "Sharingan activated eyes glowing red",
            "close-up face, serious determined look",
            "action pose with sword, dramatic",
            "standing with arms crossed, cool",
            "lightning jutsu hand signs, focused",
            "looking back over shoulder, mysterious",
            "moonlight dramatic lighting, brooding",
            "intense battle expression, powerful"
        ]
    },
    
    "sakura": {
        "base_prompt": "close-up portrait of Sakura Haruno, 17 year old female ninja, long bright pink hair in high ponytail, vibrant green emerald eyes, fair delicate skin, athletic feminine build, determined confident expression, red and black combat outfit",
        "style": "anime art, manga style, vibrant colors, detailed anime character, best quality, masterpiece",
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
    
    "kakashi": {
        "base_prompt": "close-up portrait of Kakashi Hatake, 26 year old male ninja, spiky silver-gray hair defying gravity, left eye covered by metal headband tilted, right eye visible dark, black mask covering lower face from nose down, relaxed mysterious expression, dark blue ninja vest",
        "style": "anime art, manga style, cool aesthetic, detailed anime character, best quality, masterpiece",
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
    
    "hinata": {
        "base_prompt": "close-up portrait of Hinata Hyuga, 17 year old female ninja, very long silky dark blue-black hair reaching hips, pale lavender-white pearl eyes, extremely pale delicate porcelain skin, shy gentle expression with blushing pink cheeks, lavender and beige traditional outfit",
        "style": "anime art, manga style, soft romantic aesthetic, detailed anime character, best quality, masterpiece",
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
    
    "itachi": {
        "base_prompt": "close-up portrait of Itachi Uchiha, 21 year old male, long jet black hair in low ponytail, stress lines under eyes, very pale sickly skin, deep black eyes with sorrowful gaze, melancholic sad expression, black cloak with red clouds, metal headband with slash mark",
        "style": "anime art, manga style, dark dramatic, detailed anime character, best quality, masterpiece",
        "variations": [
            "front view, sad melancholic expression",
            "side profile, tired weary look",
            "Sharingan activated glowing red",
            "close-up face, sorrowful eyes",
            "moonlight dramatic lighting, tragic",
            "standing in shadows, mysterious",
            "hand reaching out, emotional",
            "looking back, regretful",
            "wind blowing hair, cinematic",
            "serious battle expression, powerful"
        ]
    },
    
    "bradPitt": {
        "base_prompt": "close-up portrait of Brad Pitt at age 60, golden blonde hair with distinguished gray streaks, piercing bright blue eyes, chiseled square jawline, sun-kissed tan skin, charming confident smile with wrinkles showing maturity",
        "style": "photorealistic portrait, professional photography, 8k uhd, sharp focus, hyper realistic",
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
    
    "leoDiCaprio": {
        "base_prompt": "close-up portrait of Leonardo DiCaprio at age 49, golden blonde hair styled back, intense piercing blue eyes, mature masculine handsome features, passionate determined expression, elegant dark suit",
        "style": "photorealistic portrait, professional photography, 8k uhd, sharp focus, hyper realistic",
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
    
    "theRock": {
        "base_prompt": "close-up portrait of Dwayne The Rock Johnson at age 51, completely bald shaved head, brown eyes with iconic raised eyebrow, massive muscular build, large Polynesian tribal tattoo on left arm and shoulder, tan Samoan skin, huge charismatic smile",
        "style": "photorealistic portrait, professional photography, 8k uhd, sharp focus, hyper realistic",
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
    
    "scarlett": {
        "base_prompt": "close-up portrait of Scarlett Johansson at age 39, platinum blonde wavy hair, magnetic green eyes, full sensual lips, fair luminous glowing skin, athletic feminine curves, confident sensual expression, elegant black dress",
        "style": "photorealistic portrait, professional photography, 8k uhd, sharp focus, hyper realistic",
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
    
    "margot": {
        "base_prompt": "close-up portrait of Margot Robbie at age 34, golden blonde wavy hair, sparkling bright blue eyes, radiant huge cheerful smile, sun-kissed Australian tan skin, perfect beautiful features, fun energetic joyful vibe",
        "style": "photorealistic portrait, professional photography, 8k uhd, sharp focus, hyper realistic",
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
    
    "emma": {
        "base_prompt": "close-up portrait of Emma Watson at age 34, short elegant brown pixie cut hair, expressive intelligent brown eyes, fair British skin, refined elegant sophisticated features, poised graceful posture, sophisticated minimalist black outfit",
        "style": "photorealistic portrait, professional photography, 8k uhd, sharp focus, hyper realistic",
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
    
    "zendaya": {
        "base_prompt": "close-up portrait of Zendaya at age 28, long curly voluminous brown hair, hazel expressive striking eyes, caramel glowing radiant skin, tall elegant model slender build, confident modern fierce expression, high fashion outfit",
        "style": "photorealistic portrait, professional photography, 8k uhd, sharp focus, hyper realistic",
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


def generate_url(base_prompt, variation, style, model="flux"):
    """Génère l'URL Pollination AI"""
    full_prompt = f"{base_prompt}, {variation}, {style}"
    encoded = quote(full_prompt)
    seed = int(time.time() * 1000) + hash(variation) % 10000
    return f"https://image.pollinations.ai/prompt/{encoded}?width=768&height=1024&model={model}&enhance=true&nologo=true&seed={seed}"


def main():
    print("\n🎨 Génération des URLs Pollination AI...")
    print("="*70 + "\n")
    
    all_urls = {}
    kotlin_code_lines = []
    
    for char_id, char_data in CHARACTERS.items():
        print(f"📝 {char_id}...")
        
        # Choisir le modèle (flux pour anime, flux-realism pour realistic)
        model = "flux" if "anime" in char_data["style"] else "flux-realism"
        
        # Générer 10 URLs
        urls = []
        for variation in char_data["variations"][:10]:
            url = generate_url(
                char_data["base_prompt"],
                variation,
                char_data["style"],
                model
            )
            urls.append(url)
        
        all_urls[char_id] = urls
        
        # Générer code Kotlin
        kotlin_urls = ",\n            ".join([f'"{url}"' for url in urls])
        kotlin_code_lines.append(f"""        // {char_id} gallery
        gallery = listOf(
            {kotlin_urls}
        )""")
    
    # Sauvegarder JSON
    with open("gallery_urls.json", 'w', encoding='utf-8') as f:
        json.dump(all_urls, f, indent=2, ensure_ascii=False)
    
    # Sauvegarder code Kotlin
    with open("gallery_kotlin.txt", 'w', encoding='utf-8') as f:
        f.write(",\n\n".join(kotlin_code_lines))
    
    # Stats
    total = sum(len(urls) for urls in all_urls.values())
    print(f"\n{'='*70}")
    print(f"✅ TERMINÉ!")
    print(f"   - Personnages: {len(all_urls)}")
    print(f"   - URLs totales: {total}")
    print(f"   - Fichier JSON: gallery_urls.json")
    print(f"   - Code Kotlin: gallery_kotlin.txt")
    print(f"{'='*70}\n")


if __name__ == "__main__":
    main()
