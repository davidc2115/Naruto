#!/usr/bin/env python3
"""
Script patient pour générer via Freebox ComfyUI (lent mais fonctionne)
"""

import os
import time
import requests
import json
import base64
from uuid import uuid4

COMFYUI_URL = "http://88.174.155.230:33437"
OUTPUT_DIR = "character_images_freebox"

# Timeouts très longs pour Freebox ARM
TIMEOUT_CONNECTION = 60  # 1 minute pour établir connexion
TIMEOUT_READ = 600  # 10 minutes pour lire réponse
POLL_INTERVAL = 10  # Vérifier toutes les 10 secondes
MAX_WAIT_GENERATION = 900  # 15 minutes max par image

# 3 personnages prioritaires pour test
CHARACTERS = {
    "naruto": "Naruto Uzumaki, young adult male, spiky blonde hair, blue eyes, whisker marks, athletic muscular body, nude, NSFW 18+",
    "sakura": "Sakura Haruno, young adult woman, pink hair, green eyes, feminine body, medium breasts, nude, NSFW 18+",
    "hinata": "Hinata Hyuga, young adult woman, long dark indigo hair, pale eyes, hourglass figure, large breasts, nude, NSFW 18+"
}

def create_simple_workflow(prompt, seed=None):
    """Workflow minimal pour txt2img rapide"""
    if seed is None:
        seed = int(time.time() * 1000) % 2147483647
    
    return {
        "3": {
            "inputs": {
                "seed": seed,
                "steps": 5,  # TRÈS rapide
                "cfg": 4.0,
                "sampler_name": "euler",
                "scheduler": "simple",
                "denoise": 1.0,
                "model": ["4", 0],
                "positive": ["6", 0],
                "negative": ["7", 0],
                "latent_image": ["5", 0]
            },
            "class_type": "KSampler"
        },
        "4": {
            "inputs": {"ckpt_name": "sd_v15.safetensors"},
            "class_type": "CheckpointLoaderSimple"
        },
        "5": {
            "inputs": {"width": 512, "height": 512, "batch_size": 1},
            "class_type": "EmptyLatentImage"
        },
        "6": {
            "inputs": {"text": prompt, "clip": ["4", 1]},
            "class_type": "CLIPTextEncode"
        },
        "7": {
            "inputs": {"text": "low quality, blurry", "clip": ["4", 1]},
            "class_type": "CLIPTextEncode"
        },
        "8": {
            "inputs": {"samples": ["3", 0], "vae": ["4", 2]},
            "class_type": "VAEDecode"
        },
        "9": {
            "inputs": {"filename_prefix": "nsfw", "images": ["8", 0]},
            "class_type": "SaveImage"
        }
    }

def submit_prompt(workflow, client_id):
    """Soumet le prompt avec timeout long"""
    try:
        print(f"    📤 Envoi workflow...")
        response = requests.post(
            f"{COMFYUI_URL}/prompt",
            json={"prompt": workflow, "client_id": client_id},
            timeout=(TIMEOUT_CONNECTION, TIMEOUT_READ)
        )
        
        if response.status_code == 200:
            data = response.json()
            prompt_id = data.get("prompt_id")
            print(f"    ✅ Soumis: {prompt_id}")
            return prompt_id
        else:
            print(f"    ❌ HTTP {response.status_code}: {response.text[:200]}")
            return None
    except requests.exceptions.Timeout:
        print(f"    ❌ Timeout soumission")
        return None
    except Exception as e:
        print(f"    ❌ Erreur: {e}")
        return None

def wait_for_image(prompt_id):
    """Attend la génération avec polling patient"""
    start = time.time()
    print(f"    ⏳ Attente génération (max {MAX_WAIT_GENERATION//60} min)...")
    
    while (time.time() - start) < MAX_WAIT_GENERATION:
        try:
            elapsed = int(time.time() - start)
            print(f"    ⏱️ {elapsed}s - Vérification...")
            
            response = requests.get(
                f"{COMFYUI_URL}/history/{prompt_id}",
                timeout=(30, 120)
            )
            
            if response.status_code == 200:
                history = response.json()
                
                if prompt_id in history:
                    outputs = history[prompt_id].get("outputs", {})
                    
                    if outputs:
                        print(f"    ✅ Terminé en {elapsed}s!")
                        
                        # Extraire info image
                        for node_id, node_out in outputs.items():
                            if "images" in node_out and node_out["images"]:
                                img = node_out["images"][0]
                                return img.get("filename"), img.get("subfolder", "")
                        
                        print(f"    ⚠️ Outputs sans images: {outputs}")
                        return None, None
            
            time.sleep(POLL_INTERVAL)
            
        except Exception as e:
            print(f"    ⚠️ Erreur check: {e}")
            time.sleep(POLL_INTERVAL)
    
    print(f"    ❌ Timeout après {MAX_WAIT_GENERATION}s")
    return None, None

def download_image(filename, subfolder=""):
    """Télécharge l'image générée"""
    try:
        url = f"{COMFYUI_URL}/view?filename={filename}&subfolder={subfolder}&type=output"
        print(f"    📥 Download: {filename}")
        
        response = requests.get(url, timeout=(60, 300))
        
        if response.status_code == 200:
            size_kb = len(response.content) // 1024
            print(f"    ✅ {size_kb}KB")
            return response.content
        else:
            print(f"    ❌ HTTP {response.status_code}")
            return None
    except Exception as e:
        print(f"    ❌ Download error: {e}")
        return None

def generate_one_image(char_id, prompt, image_num):
    """Génère UNE image complète"""
    print(f"\n  🎨 {char_id} #{image_num}")
    print(f"    📝 {prompt[:80]}...")
    
    # Workflow
    workflow = create_simple_workflow(prompt)
    client_id = str(uuid4())
    
    # Submit
    prompt_id = submit_prompt(workflow, client_id)
    if not prompt_id:
        return None
    
    # Wait
    filename, subfolder = wait_for_image(prompt_id)
    if not filename:
        return None
    
    # Download
    return download_image(filename, subfolder)

def main():
    print("╔════════════════════════════════════════════════════╗")
    print("║  🐌 Générateur PATIENT Freebox ComfyUI NSFW      ║")
    print("╚════════════════════════════════════════════════════╝")
    print(f"\n⚙️ Config:")
    print(f"  - URL: {COMFYUI_URL}")
    print(f"  - Personnages: {len(CHARACTERS)} (test)")
    print(f"  - Images/perso: 3")
    print(f"  - Timeout/image: {MAX_WAIT_GENERATION//60} min")
    print(f"  - Steps: 5 (ultra rapide)")
    print(f"  - Résolution: 512x512")
    
    # Test connexion
    print(f"\n🔍 Test connexion...")
    try:
        response = requests.get(COMFYUI_URL, timeout=(60, 120))
        if response.status_code == 200:
            print(f"  ✅ ComfyUI OK ({response.elapsed.total_seconds():.1f}s)")
        else:
            print(f"  ⚠️ HTTP {response.status_code}")
    except Exception as e:
        print(f"  ❌ Inaccessible: {e}")
        return
    
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    print(f"\n🚀 Génération (lente mais sûre)...\n")
    
    total = 0
    start_global = time.time()
    
    for char_id, base_prompt in CHARACTERS.items():
        print(f"\n{'='*70}")
        print(f"👤 {char_id.upper()}")
        print(f"{'='*70}")
        
        for i in range(1, 4):
            img_data = generate_one_image(char_id, base_prompt, i)
            
            if img_data:
                filepath = os.path.join(OUTPUT_DIR, f"{char_id}nsfw{i}.png")
                with open(filepath, 'wb') as f:
                    f.write(img_data)
                print(f"  💾 {filepath}")
                total += 1
            
            # Pause inter-images
            if i < 3:
                print(f"  ⏳ Pause 5s...")
                time.sleep(5)
        
        # Pause inter-personnages
        print(f"\n⏳ Pause 10s avant personnage suivant...")
        time.sleep(10)
    
    elapsed = time.time() - start_global
    
    print(f"\n\n{'='*70}")
    print(f"🎉 TERMINÉ")
    print(f"{'='*70}")
    print(f"✅ Images: {total}/{len(CHARACTERS) * 3}")
    print(f"⏱️ Temps: {elapsed/60:.1f} minutes")
    print(f"📁 {os.path.abspath(OUTPUT_DIR)}")

if __name__ == "__main__":
    main()
