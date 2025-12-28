#!/usr/bin/env python3
"""
Script de génération automatique de 10 images par personnage
Utilise l'API gratuite Pollination AI
"""

import requests
import json
import time
import urllib.parse
from typing import List, Dict

# Configuration
POLLINATION_API_URL = "https://image.pollinations.ai/prompt/{prompt}"
OUTPUT_FILE = "generated_images.json"

# Descriptions physiques ultra-détaillées pour chaque personnage
CHARACTERS = {
    "Naruto Uzumaki": {
        "base_description": "hyper-realistic portrait of a 17-year-old Japanese young man, spiky golden blonde hair pointing in all directions, bright blue ocean eyes, six whisker marks on cheeks (3 per cheek), tan skin, athletic build, wide confident smile, orange and black ninja jacket, Konoha headband",
        "variations": [
            "smiling confidently, golden hour lighting, cinematic",
            "determined expression, studio lighting, heroic angle",
            "laughing energetically, natural outdoor lighting",
            "serious ninja pose, dramatic side lighting",
            "closeup face, eyes sparkling with determination",
            "action pose with hand gesture, dynamic composition",
            "looking up with hope, soft backlight",
            "profile view showing whisker marks, sunset",
            "full body standing pose, confident stance",
            "headshot with slight smile, professional photography"
        ],
        "style": "anime character, photorealistic, 8K, highly detailed"
    },
    
    "Sasuke Uchiha": {
        "base_description": "hyper-realistic portrait of a 17-year-old Japanese young man, spiky black hair swept back, deep black eyes (red Sharingan activated), very pale porcelain skin, aristocratic facial features, cold expression, dark blue high-collar shirt, Konoha headband",
        "variations": [
            "intense cold stare, dramatic chiaroscuro lighting",
            "Sharingan activated with red eyes and tomoe, powerful",
            "side profile, moonlight illumination",
            "serious brooding expression, studio portrait",
            "slight smirk, confident and dangerous",
            "eyes closed in concentration, peaceful moment",
            "looking away with indifference, moody lighting",
            "closeup of Sharingan eye, mystical atmosphere",
            "full body pose with arms crossed, intimidating",
            "contemplative expression, soft window light"
        ],
        "style": "anime character, photorealistic, 8K, film noir aesthetic"
    },
    
    "Sakura Haruno": {
        "base_description": "hyper-realistic portrait of a 17-year-old Japanese young woman, long pink silky hair in ponytail, bright green emerald eyes, clear fair skin, athletic feminine build, determined expression, red and black combat outfit, confident medical ninja",
        "variations": [
            "gentle smile, warm natural lighting",
            "fierce determined expression, dramatic lighting",
            "medical ninja in action, focused concentration",
            "soft compassionate look, studio portrait",
            "powerful fighting stance, dynamic angle",
            "closeup face with caring eyes, golden hour",
            "profile view showing pink hair, elegant",
            "laughing joyfully, bright outdoor setting",
            "serious professional expression, even lighting",
            "full body athletic pose, confident stance"
        ],
        "style": "anime character, photorealistic, 8K, highly detailed"
    },
    
    "Kakashi Hatake": {
        "base_description": "hyper-realistic portrait of a 26-year-old Japanese man, spiky silver-gray hair defying gravity, one eye visible (other covered by headband), black face mask covering lower face, relaxed posture, ninja vest, mysterious aura, holding orange book",
        "variations": [
            "casual lazy expression, soft lighting",
            "eye smiling (crinkled), warm afternoon light",
            "reading Make-Out Paradise book, amused",
            "serious ninja mode, dramatic lighting",
            "Sharingan revealed under headband, powerful",
            "relaxed leaning pose, cool and mysterious",
            "closeup of visible eye, enigmatic",
            "profile view showing silver hair, stylish",
            "full body casual stance, hands in pockets",
            "contemplative look, cinematic composition"
        ],
        "style": "anime character, photorealistic, 8K, mysterious atmosphere"
    },
    
    "Hinata Hyuga": {
        "base_description": "hyper-realistic portrait of a 17-year-old Japanese young woman, long silky dark blue-black hair, pearl white Byakugan eyes (lavender when not activated), very pale delicate skin, shy gentle expression, blushing cheeks, elegant posture, lavender and beige traditional outfit",
        "variations": [
            "shy blushing smile, soft romantic lighting",
            "Byakugan activated with white veined eyes, powerful",
            "gentle caring expression, warm natural light",
            "timid look downward, delicate and sweet",
            "determined fighting stance, graceful strength",
            "closeup face with lavender eyes, ethereal",
            "profile view showing long hair, elegant beauty",
            "soft smile of encouragement, golden hour",
            "full body traditional pose, serene grace",
            "contemplative peaceful expression, studio light"
        ],
        "style": "anime character, photorealistic, 8K, soft ethereal aesthetic"
    },
    
    "Itachi Uchiha": {
        "base_description": "hyper-realistic portrait of a 21-year-old Japanese man, long black hair in low ponytail, stress lines under eyes, deep black eyes (Mangekyō Sharingan red-black pattern), very pale sickly skin, melancholic expression, Akatsuki black cloak with red clouds, tragic aura",
        "variations": [
            "melancholic distant gaze, moonlight",
            "Mangekyō Sharingan activated, mystical powerful",
            "sad contemplative expression, soft lighting",
            "eyes closed in sorrow, dramatic shadows",
            "calm peaceful look, gentle illumination",
            "intense stare with regret, cinematic",
            "profile view showing ponytail, tragic hero",
            "closeup of Mangekyō eye pattern, haunting",
            "full body in Akatsuki cloak, mysterious",
            "philosophical expression, window light"
        ],
        "style": "anime character, photorealistic, 8K, tragic melancholic mood"
    },
    
    "Brad Pitt": {
        "base_description": "hyper-realistic portrait of Brad Pitt at 60 years old, golden blonde hair slightly graying, piercing blue eyes, chiseled jawline, sun-kissed tan skin, charismatic smile, casual stylish clothing, Hollywood star aura, aged handsomely",
        "variations": [
            "charming smile, natural daylight, magazine cover",
            "serious thoughtful expression, studio portrait",
            "laughing genuinely, warm golden hour",
            "intense movie star gaze, dramatic lighting",
            "relaxed confident pose, casual elegance",
            "closeup showing mature features, distinguished",
            "profile view highlighting jawline, classic",
            "wearing sunglasses, cool and mysterious",
            "full body casual stance, effortless style",
            "gentle smile with warmth, soft natural light"
        ],
        "style": "celebrity photography, 8K, cinematic, professional"
    },
    
    "Leonardo DiCaprio": {
        "base_description": "hyper-realistic portrait of Leonardo DiCaprio at 49 years old, golden blonde hair, intense blue eyes, masculine features, passionate expression, elegant suit or casual wear, environmental activist aura, mature Hollywood leading man",
        "variations": [
            "passionate speaking expression, conference lighting",
            "charming smile, classic portrait",
            "intense dramatic gaze, movie poster style",
            "thoughtful environmental advocate look, natural",
            "laughing warmly, candid moment",
            "serious determined expression, studio",
            "closeup showing blue eyes, captivating",
            "profile view, distinguished mature features",
            "full body confident stance, elegant attire",
            "gentle compassionate look, soft light"
        ],
        "style": "celebrity photography, 8K, cinematic, A-list actor"
    },
    
    "Dwayne 'The Rock' Johnson": {
        "base_description": "hyper-realistic portrait of Dwayne Johnson at 51 years old, bald shaved head, brown eyes, massive muscular build, Polynesian tribal tattoo on left arm and chest, tan Samoan skin, huge confident smile, athletic wear, powerful presence",
        "variations": [
            "huge charismatic smile, bright lighting, motivational",
            "intense workout expression, gym lighting, powerful",
            "raised eyebrow expression, signature look",
            "laughing heartily, joyful and energetic",
            "serious determined face, dramatic shadows",
            "flexing muscles pose, showing tattoo, epic",
            "closeup of face, warm friendly eyes",
            "profile showing muscular build, imposing",
            "full body athletic stance, intimidating presence",
            "gentle compassionate smile, soft lighting"
        ],
        "style": "celebrity photography, 8K, action hero, larger than life"
    },
    
    "Scarlett Johansson": {
        "base_description": "hyper-realistic portrait of Scarlett Johansson at 39 years old, platinum blonde wavy hair, green magnetic eyes, full lips, fair luminous skin, athletic feminine curves, confident sensual expression, elegant black dress, Hollywood glamour",
        "variations": [
            "confident sensual smile, studio glamour lighting",
            "Black Widow fierce expression, action pose",
            "elegant sophisticated look, fashion photography",
            "playful flirty gaze, soft warm light",
            "serious dramatic expression, film noir style",
            "laughing genuinely, candid natural moment",
            "closeup showing green eyes and lips, stunning",
            "profile view, classic Hollywood beauty",
            "full body elegant pose, red carpet ready",
            "gentle warm smile, approachable and kind"
        ],
        "style": "celebrity photography, 8K, glamorous, sophisticated"
    },
    
    "Margot Robbie": {
        "base_description": "hyper-realistic portrait of Margot Robbie at 34 years old, golden blonde wavy hair, sparkling blue eyes, radiant glowing smile, sun-kissed Australian tan skin, perfect features, fun energetic vibe, stylish modern outfit, Barbie-esque beauty",
        "variations": [
            "huge radiant smile, bright cheerful lighting",
            "Harley Quinn playful expression, colorful fun",
            "elegant Barbie look, pink glamorous",
            "natural Australian beach beauty, golden hour",
            "sophisticated red carpet pose, stunning",
            "laughing joyfully, contagious happiness",
            "closeup face showing blue eyes, dazzling",
            "profile view, perfect features highlighted",
            "full body confident stance, fashion forward",
            "sweet warm smile, girl-next-door charm"
        ],
        "style": "celebrity photography, 8K, vibrant, modern beauty"
    },
    
    "Emma Watson": {
        "base_description": "hyper-realistic portrait of Emma Watson at 34 years old, short elegant brown hair, expressive brown eyes, fair British skin, intelligent refined features, poised elegant posture, sophisticated minimalist style, feminist activist aura, Hermione grown up",
        "variations": [
            "intelligent thoughtful expression, natural light",
            "passionate speaking at UN, determined",
            "elegant sophisticated smile, fashion portrait",
            "serious activist expression, powerful message",
            "gentle warm compassionate look, soft lighting",
            "reading a book, intellectual and focused",
            "closeup showing brown eyes, depth and wisdom",
            "profile view, classic British elegance",
            "full body poised stance, graceful confidence",
            "friendly approachable smile, warm and kind"
        ],
        "style": "celebrity photography, 8K, elegant, intellectual beauty"
    },
    
    "Zendaya": {
        "base_description": "hyper-realistic portrait of Zendaya at 28 years old, long curly brown hair (style changes), hazel expressive eyes, caramel glowing skin, tall elegant model build, confident modern expression, avant-garde fashion outfit, Gen Z icon, stunning mixed features",
        "variations": [
            "confident fierce model gaze, high fashion",
            "warm friendly smile, approachable and cool",
            "Euphoria dramatic makeup, artistic intense",
            "elegant red carpet pose, fashion icon",
            "natural curly hair beauty, authentic radiance",
            "playful fun expression, youthful energy",
            "closeup face showing hazel eyes, mesmerizing",
            "profile view, statuesque tall beauty",
            "full body runway model pose, commanding presence",
            "sweet genuine smile, relatable and kind"
        ],
        "style": "celebrity photography, 8K, fashion forward, modern icon"
    }
}


def generate_image_url(character_name: str, description: str, variation: str, style: str) -> str:
    """
    Génère l'URL d'image Pollination AI
    """
    full_prompt = f"{description}, {variation}, {style}"
    # URL encode le prompt
    encoded_prompt = urllib.parse.quote(full_prompt)
    url = f"https://image.pollinations.ai/prompt/{encoded_prompt}?width=1024&height=1024&nologo=true&model=flux"
    return url


def generate_gallery_for_character(character_name: str, character_data: Dict) -> List[str]:
    """
    Génère 10 URLs d'images pour un personnage
    """
    print(f"\n🎨 Génération de 10 images pour {character_name}...")
    
    base_desc = character_data["base_description"]
    variations = character_data["variations"]
    style = character_data["style"]
    
    gallery_urls = []
    
    for i, variation in enumerate(variations, 1):
        url = generate_image_url(character_name, base_desc, variation, style)
        gallery_urls.append(url)
        print(f"  ✅ Image {i}/10: {url[:80]}...")
        
        # Petit délai pour ne pas surcharger l'API (optionnel)
        time.sleep(0.5)
    
    return gallery_urls


def generate_all_galleries() -> Dict[str, List[str]]:
    """
    Génère les galeries pour tous les personnages
    """
    print("🚀 Démarrage de la génération d'images pour tous les personnages...\n")
    print("=" * 80)
    
    all_galleries = {}
    
    for character_name, character_data in CHARACTERS.items():
        gallery_urls = generate_gallery_for_character(character_name, character_data)
        all_galleries[character_name] = gallery_urls
        print(f"✅ {character_name}: {len(gallery_urls)} images générées\n")
    
    return all_galleries


def save_to_json(galleries: Dict[str, List[str]]):
    """
    Sauvegarde les URLs dans un fichier JSON
    """
    with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
        json.dump(galleries, f, indent=2, ensure_ascii=False)
    print(f"\n💾 Galeries sauvegardées dans {OUTPUT_FILE}")


def generate_kotlin_code(galleries: Dict[str, List[str]]):
    """
    Génère le code Kotlin pour intégrer les galeries
    """
    print("\n" + "=" * 80)
    print("📝 Code Kotlin à intégrer dans Characters.kt:\n")
    
    for character_name, urls in galleries.items():
        # Convertir en nom de variable Kotlin (camelCase)
        var_name = character_name.lower().replace(" ", "_").replace("'", "").replace("-", "_")
        
        print(f"// Galerie pour {character_name}")
        print(f'gallery = listOf(')
        for url in urls:
            print(f'    "{url}",')
        print('),\n')


def main():
    """
    Fonction principale
    """
    print("""
    ╔═══════════════════════════════════════════════════════════════╗
    ║  Générateur de Galeries d'Images - Naruto AI Chat v2.3.0    ║
    ║  Utilise Pollination AI (gratuit)                            ║
    ╚═══════════════════════════════════════════════════════════════╝
    """)
    
    # Générer toutes les galeries
    all_galleries = generate_all_galleries()
    
    # Sauvegarder en JSON
    save_to_json(all_galleries)
    
    # Générer le code Kotlin
    generate_kotlin_code(all_galleries)
    
    # Statistiques finales
    print("\n" + "=" * 80)
    print(f"📊 STATISTIQUES:")
    print(f"   - Personnages traités: {len(all_galleries)}")
    print(f"   - Images totales générées: {sum(len(urls) for urls in all_galleries.values())}")
    print(f"   - Fichier de sortie: {OUTPUT_FILE}")
    print("=" * 80)
    print("\n✅ Génération terminée avec succès!")
    print("\n💡 Prochaines étapes:")
    print("   1. Copiez le code Kotlin ci-dessus")
    print("   2. Intégrez-le dans app/src/main/java/com/narutoai/chat/data/Characters.kt")
    print("   3. Rebuild l'application")
    print("   4. Les galeries seront automatiquement disponibles!\n")


if __name__ == "__main__":
    main()
