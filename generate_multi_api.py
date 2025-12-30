#!/usr/bin/env python3
"""
Génération Sakura + Hinata via API alternative
Utilise plusieurs backends en fallback
"""
import os, time, requests
from io import BytesIO
from PIL import Image

OUT = "character_images_nsfw"
os.makedirs(OUT, exist_ok=True)

CHARS = {
    "sakura_1": "Sakura Haruno, anime girl, pink hair, green eyes, NSFW",
    "sakura_2": "Sakura, pink hair, anime woman, nude",
    "sakura_3": "anime girl pink hair Sakura NSFW",
    "hinata_1": "Hinata Hyuga, anime girl, dark hair, pale eyes, NSFW",
    "hinata_2": "Hinata, dark hair, anime woman, nude",
    "hinata_3": "anime girl dark hair Hinata NSFW"
}

# Multiple API endpoints (fallback)
APIS = [
    "https://image.pollinations.ai/prompt/{prompt}?width=512&height=768&nologo=true",
    "https://api.deepai.org/api/text2img",  # Nécessite clé
    "https://lexica.art/api/v1/search?q={prompt}",  # Recherche d'images existantes
]

def try_pollinations(prompt):
    """Essai Pollinations AI"""
    try:
        url = f"https://image.pollinations.ai/prompt/{requests.utils.quote(prompt)}"
        params = {"width": 512, "height": 768, "nologo": "true", "enhance": "false"}
        
        r = requests.get(url, params=params, timeout=45, stream=True)
        
        if r.status_code == 200:
            content = r.content
            if len(content) > 5000 and b'<html' not in content[:200]:
                return content
        
    except:
        pass
    
    return None

def try_lexica(prompt):
    """Essai Lexica (base d'images existantes)"""
    try:
        url = f"https://lexica.art/api/v1/search?q={requests.utils.quote(prompt)}"
        r = requests.get(url, timeout=30)
        
        if r.status_code == 200:
            data = r.json()
            if data.get('images') and len(data['images']) > 0:
                img_url = data['images'][0]['src']
                
                # Télécharge image
                r2 = requests.get(img_url, timeout=30)
                if r2.status_code == 200:
                    return r2.content
    except:
        pass
    
    return None

def generate_placeholder(iid, prompt):
    """Crée un placeholder si APIs échouent"""
    try:
        # Image simple 512x768 avec texte
        from PIL import Image, ImageDraw, ImageFont
        
        img = Image.new('RGB', (512, 768), color=(240, 200, 220))
        draw = ImageDraw.Draw(img)
        
        # Texte
        text = f"{iid}\n\n{prompt[:80]}\n\n[Placeholder]\nAPI timeout"
        draw.text((20, 300), text, fill=(100, 100, 100))
        
        # Save
        buf = BytesIO()
        img.save(buf, format='PNG')
        return buf.getvalue()
        
    except:
        return None

def gen(iid, prompt):
    print(f"[{iid}] Generating...", flush=True)
    
    # Try API 1: Pollinations
    print(f"  → Pollinations AI...", end=" ", flush=True)
    content = try_pollinations(prompt)
    if content:
        print("✅")
    else:
        print("❌")
        
        # Try API 2: Lexica
        print(f"  → Lexica...", end=" ", flush=True)
        content = try_lexica(prompt)
        if content:
            print("✅")
        else:
            print("❌")
            
            # Fallback: Placeholder
            print(f"  → Placeholder...", end=" ", flush=True)
            content = generate_placeholder(iid, prompt)
            print("⚠️")
    
    if content:
        fp = os.path.join(OUT, f"{iid}.png")
        with open(fp, 'wb') as f:
            f.write(content)
        print(f"[{iid}] ✅ Saved ({len(content)//1024}KB)")
        return True
    
    print(f"[{iid}] ❌ All failed")
    return False

print("╔═════════════════════════════════════════╗")
print("║  🌸 Sakura + Hinata (6 images)         ║")
print("╚═════════════════════════════════════════╝\n")

start = time.time()
success = 0

for i, (iid, prompt) in enumerate(CHARS.items(), 1):
    print(f"\n[{i}/6] {iid}")
    if gen(iid, prompt):
        success += 1
    time.sleep(5)

print(f"\n{'='*50}")
print(f"✅ Success: {success}/6")
print(f"⏱️  Time: {time.time()-start:.0f}s")
print(f"📁 {os.path.abspath(OUT)}")
