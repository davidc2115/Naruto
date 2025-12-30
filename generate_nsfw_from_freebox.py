#!/usr/bin/env python3
"""
Script pour générer les galeries NSFW via ComfyUI sur Freebox
http://88.174.155.230:33437
"""

import os
import time
import json
import requests
import base64
from uuid import uuid4

# Configuration ComfyUI Freebox
COMFYUI_URL = "http://88.174.155.230:33437"
OUTPUT_DIR = "character_images_freebox"
IMAGES_PER_CHARACTER = 3
DELAY_BETWEEN_REQUESTS = 5  # secondes

# Timeout long car ComfyUI sur ARM est lent
TIMEOUT_SUBMIT = 30
TIMEOUT_CHECK = 10
TIMEOUT_DOWNLOAD = 180  # 3 minutes pour génération

# Personnages
CHARACTERS = {
    "naruto": {
        "name": "Naruto Uzumaki",
        "physical": "young adult male ninja, 18-22 years old, spiky blonde hair, ocean blue eyes, three whisker marks on each cheek, athletic muscular body, tan skin, abs, confident smile",
        "nsfw_style": "handsome naked male, masculine body, detailed anatomy, seductive pose"
    },
    "sakura": {
        "name": "Sakura Haruno",
        "physical": "young adult woman, 18-22 years old, pink shoulder-length hair, green eyes, fair skin, feminine athletic body, medium breasts, beautiful face",
        "nsfw_style": "beautiful naked woman, sensual pose, elegant curves, intimate setting"
    },
    "hinata": {
        "name": "Hinata Hyuga",
        "physical": "young adult woman, 18-22 years old, long dark indigo hair, pale lavender eyes, very fair porcelain skin, hourglass figure, large breasts, shy gentle expression",
        "nsfw_style": "beautiful naked woman, voluptuous body, sensual pose, soft lighting"
    }
}

def create_comfyui_workflow(prompt, negative_prompt="low quality, blurry, distorted", steps=8, cfg=5.0):
    """Crée un workflow ComfyUI simple pour txt2img NSFW"""
    seed = int(time.time() * 1000) % 2147483647
    
    workflow = {
        "3": {
            "inputs": {
                "seed": seed,
                "steps": steps,
                "cfg": cfg,
                "sampler_name": "euler",
                "scheduler": "normal",
                "denoise": 1.0,
                "model": ["4", 0],
                "positive": ["6", 0],
                "negative": ["7", 0],
                "latent_image": ["5", 0]
            },
            "class_type": "KSampler"
        },
        "4": {
            "inputs": {
                "ckpt_name": "sd_v15.safetensors"
            },
            "class_type": "CheckpointLoaderSimple"
        },
        "5": {
            "inputs": {
                "width": 512,
                "height": 768,
                "batch_size": 1
            },
            "class_type": "EmptyLatentImage"
        },
        "6": {
            "inputs": {
                "text": prompt,
                "clip": ["4", 1]
            },
            "class_type": "CLIPTextEncode"
        },
        "7": {
            "inputs": {
                "text": negative_prompt,
                "clip": ["4", 1]
            },
            "class_type": "CLIPTextEncode"
        },
        "8": {
            "inputs": {
                "samples": ["3", 0],
                "vae": ["4", 2]
            },
            "class_type": "VAEDecode"
        },
        "9": {
            "inputs": {
                "filename_prefix": "naruto_nsfw",
                "images": ["8", 0]
            },
            "class_type": "SaveImage"
        }
    }
    return workflow

def submit_prompt_to_comfyui(workflow, client_id):
    """Soumet un prompt à ComfyUI"""
    try:
        payload = {
            "prompt": workflow,
            "client_id": client_id
        }
        
        print(f"  📤 Envoi à ComfyUI...")
        response = requests.post(
            f"{COMFYUI_URL}/prompt",
            json=payload,
            timeout=TIMEOUT_SUBMIT
        )
        response.raise_for_status()
        
        result = response.json()
        prompt_id = result.get("prompt_id")
        print(f"  ✅ Prompt ID: {prompt_id}")
        return prompt_id
    except Exception as e:
        print(f"  ❌ Erreur soumission: {e}")
        return None

def wait_for_completion(prompt_id, max_wait=180):
    """Attend la complétion de la génération"""
    start = time.time()
    print(f"  ⏳ Attente génération (max {max_wait}s)...")
    
    while (time.time() - start) < max_wait:
        try:
            response = requests.get(
                f"{COMFYUI_URL}/history/{prompt_id}",
                timeout=TIMEOUT_CHECK
            )
            
            if response.status_code == 200:
                history = response.json()
                if prompt_id in history:
                    prompt_data = history[prompt_id]
                    if "outputs" in prompt_data:
                        print(f"  ✅ Génération terminée!")
                        return prompt_data["outputs"]
            
            time.sleep(5)
            elapsed = int(time.time() - start)
            print(f"  ⏳ {elapsed}s écoulées...")
            
        except Exception as e:
            print(f"  ⚠️ Erreur check: {e}")
            time.sleep(5)
    
    print(f"  ❌ Timeout après {max_wait}s")
    return None

def download_image_from_comfyui(filename, subfolder="", image_type="output"):
    """Télécharge une image depuis ComfyUI"""
    try:
        url = f"{COMFYUI_URL}/view?filename={filename}&subfolder={subfolder}&type={image_type}"
        print(f"  📥 Téléchargement: {filename}")
        
        response = requests.get(url, timeout=TIMEOUT_DOWNLOAD)
        response.raise_for_status()
        
        return response.content
    except Exception as e:
        print(f"  ❌ Erreur download: {e}")
        return None

def generate_nsfw_image(character_name, physical_desc, nsfw_style, variation):
    """Génère une image NSFW via ComfyUI Freebox"""
    
    # Créer prompt NSFW
    prompt = f"{character_name}, {physical_desc}, {nsfw_style}, NSFW explicit adult content 18+, nude, naked body, variation {variation}, highly detailed, photorealistic, masterpiece, best quality"
    negative = "low quality, blurry, distorted, ugly, deformed, censored, clothed"
    
    print(f"  📝 Prompt: {prompt[:80]}...")
    
    # Créer workflow
    workflow = create_comfyui_workflow(prompt, negative, steps=8, cfg=5.0)
    client_id = str(uuid4())
    
    # Soumettre
    prompt_id = submit_prompt_to_comfyui(workflow, client_id)
    if not prompt_id:
        return None
    
    # Attendre complétion
    outputs = wait_for_completion(prompt_id, max_wait=180)
    if not outputs:
        return None
    
    # Extraire filename
    for node_id, node_output in outputs.items():
        if "images" in node_output:
            images = node_output["images"]
            if len(images) > 0:
                img_info = images[0]
                filename = img_info.get("filename")
                subfolder = img_info.get("subfolder", "")
                
                # Télécharger
                image_data = download_image_from_comfyui(filename, subfolder)
                if image_data:
                    print(f"  ✅ Image téléchargée: {len(image_data) // 1024}KB")
                    return image_data
    
    print(f"  ❌ Aucune image dans les outputs")
    return None

def generate_gallery_for_character(char_id, char_data):
    """Génère une galerie NSFW pour un personnage"""
    print(f"\n{'='*70}")
    print(f"🎨 Génération NSFW Freebox pour: {char_data['name']}")
    print(f"{'='*70}")
    
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    success_count = 0
    
    for i in range(1, IMAGES_PER_CHARACTER + 1):
        print(f"\n📸 Image {i}/{IMAGES_PER_CHARACTER}")
        
        image_data = generate_nsfw_image(
            char_data['name'],
            char_data['physical'],
            char_data['nsfw_style'],
            i
        )
        
        if image_data:
            filename = f"{char_id}nsfw{i}.png"
            filepath = os.path.join(OUTPUT_DIR, filename)
            
            with open(filepath, 'wb') as f:
                f.write(image_data)
            
            print(f"  💾 Sauvegardé: {filepath}")
            success_count += 1
        
        # Pause entre images
        if i < IMAGES_PER_CHARACTER:
            print(f"  ⏳ Pause {DELAY_BETWEEN_REQUESTS}s...")
            time.sleep(DELAY_BETWEEN_REQUESTS)
    
    print(f"\n✅ {char_data['name']}: {success_count}/{IMAGES_PER_CHARACTER} images")
    return success_count

def main():
    print("╔══════════════════════════════════════════════════════════════════╗")
    print("║  🎨 Générateur NSFW via Freebox ComfyUI                        ║")
    print("╚══════════════════════════════════════════════════════════════════╝")
    print(f"\n📊 Configuration:")
    print(f"  - ComfyUI: {COMFYUI_URL}")
    print(f"  - Personnages: {len(CHARACTERS)}")
    print(f"  - Images/personnage: {IMAGES_PER_CHARACTER}")
    print(f"  - Total: {len(CHARACTERS) * IMAGES_PER_CHARACTER} images")
    print(f"  - Output: {OUTPUT_DIR}/")
    
    # Test connexion
    print(f"\n🔍 Test connexion ComfyUI...")
    try:
        response = requests.get(f"{COMFYUI_URL}", timeout=30)
        if response.status_code == 200:
            print(f"  ✅ ComfyUI accessible")
        else:
            print(f"  ⚠️ ComfyUI répond: HTTP {response.status_code}")
    except Exception as e:
        print(f"  ❌ ComfyUI inaccessible: {e}")
        print(f"\n⚠️ Vérifiez que ComfyUI tourne sur {COMFYUI_URL}")
        return
    
    print(f"\n🚀 Démarrage génération...\n")
    
    total_success = 0
    start_time = time.time()
    
    for char_id, char_data in CHARACTERS.items():
        success = generate_gallery_for_character(char_id, char_data)
        total_success += success
        
        # Pause entre personnages
        print(f"\n⏳ Pause 10s avant personnage suivant...")
        time.sleep(10)
    
    elapsed = time.time() - start_time
    
    print("\n" + "="*70)
    print("🎉 GÉNÉRATION TERMINÉE")
    print("="*70)
    print(f"✅ Images: {total_success}/{len(CHARACTERS) * IMAGES_PER_CHARACTER}")
    print(f"⏱️ Temps: {elapsed/60:.1f} minutes")
    print(f"📁 Dossier: {os.path.abspath(OUTPUT_DIR)}")

if __name__ == "__main__":
    main()
