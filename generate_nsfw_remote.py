#!/usr/bin/env python3
"""
Génération NSFW via ComfyUI Freebox DEPUIS INTERNET (ultra-patient)
"""

import os
import time
import json
import requests
from uuid import uuid4

# Config
COMFYUI_URL = "http://88.174.155.230:33437"
OUTPUT_DIR = "character_images_freebox_remote"

# Timeouts TRÈS LONGS (réseau Internet → Freebox)
TIMEOUT_CONNECT = 30
TIMEOUT_READ_SUBMIT = 120  # 2 min pour soumettre
TIMEOUT_READ_CHECK = 60    # 1 min pour vérifier
MAX_WAIT_PER_IMAGE = 900   # 15 minutes par image

# Seulement 3 personnages pour test
CHARACTERS = {
    "naruto": "Naruto Uzumaki, young adult male 18+, spiky blonde hair, blue eyes, whisker marks, athletic muscular nude body, explicit NSFW adult content 18+, naked, photorealistic",
    "sakura": "Sakura Haruno, young adult woman 18+, pink hair, green eyes, nude feminine body, medium breasts, explicit NSFW adult content 18+, naked, photorealistic",
    "hinata": "Hinata Hyuga, young adult woman 18+, long dark indigo hair, pale eyes, nude hourglass figure, large breasts, explicit NSFW adult content 18+, naked, photorealistic"
}

def create_workflow(prompt, seed):
    """Workflow minimal pour génération rapide"""
    return {
        "3": {
            "inputs": {
                "seed": seed,
                "steps": 6,  # ULTRA rapide
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
            "inputs": {"text": "low quality, blurry, censored", "clip": ["4", 1]},
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
    """Soumet le prompt"""
    try:
        print(f"      📤 Submitting...")
        response = requests.post(
            f"{COMFYUI_URL}/prompt",
            json={"prompt": workflow, "client_id": client_id},
            timeout=(TIMEOUT_CONNECT, TIMEOUT_READ_SUBMIT)
        )
        
        if response.status_code == 200:
            data = response.json()
            prompt_id = data.get("prompt_id")
            print(f"      ✅ Submitted: {prompt_id}")
            return prompt_id
        else:
            print(f"      ❌ HTTP {response.status_code}")
            return None
    except Exception as e:
        print(f"      ❌ Error: {e}")
        return None

def wait_for_image(prompt_id):
    """Attend la génération"""
    start = time.time()
    print(f"      ⏳ Waiting (max {MAX_WAIT_PER_IMAGE//60} min)...")
    
    while (time.time() - start) < MAX_WAIT_PER_IMAGE:
        try:
            elapsed = int(time.time() - start)
            if elapsed % 30 == 0 or elapsed < 5:  # Log toutes les 30s
                print(f"      ⏱️ {elapsed}s elapsed...")
            
            response = requests.get(
                f"{COMFYUI_URL}/history/{prompt_id}",
                timeout=(TIMEOUT_CONNECT, TIMEOUT_READ_CHECK)
            )
            
            if response.status_code == 200:
                history = response.json()
                
                if prompt_id in history:
                    outputs = history[prompt_id].get("outputs", {})
                    
                    if outputs:
                        print(f"      ✅ Done in {elapsed}s!")
                        
                        for node_id, node_out in outputs.items():
                            if "images" in node_out and node_out["images"]:
                                img = node_out["images"][0]
                                return img.get("filename"), img.get("subfolder", "")
                        
                        return None, None
            
            time.sleep(10)  # Vérifier toutes les 10s
            
        except Exception as e:
            print(f"      ⚠️ Check error: {e}")
            time.sleep(10)
    
    print(f"      ❌ Timeout after {MAX_WAIT_PER_IMAGE}s")
    return None, None

def download_image(filename, subfolder=""):
    """Télécharge l'image"""
    try:
        url = f"{COMFYUI_URL}/view"
        params = {"filename": filename, "subfolder": subfolder, "type": "output"}
        
        print(f"      📥 Downloading: {filename}")
        response = requests.get(url, params=params, timeout=(TIMEOUT_CONNECT, 180))
        
        if response.status_code == 200:
            size_kb = len(response.content) // 1024
            print(f"      ✅ Downloaded: {size_kb}KB")
            return response.content
        else:
            print(f"      ❌ HTTP {response.status_code}")
            return None
    except Exception as e:
        print(f"      ❌ Error: {e}")
        return None

def generate_one_image(char_id, prompt, img_num):
    """Génère UNE image"""
    print(f"\n    🎨 {char_id.upper()} #{img_num}")
    print(f"      📝 {prompt[:60]}...")
    
    seed = int(time.time() * 1000) % 2147483647
    workflow = create_workflow(prompt, seed)
    client_id = str(uuid4())
    
    prompt_id = submit_prompt(workflow, client_id)
    if not prompt_id:
        return None
    
    filename, subfolder = wait_for_image(prompt_id)
    if not filename:
        return None
    
    return download_image(filename, subfolder)

def main():
    print("╔═══════════════════════════════════════════════════════════╗")
    print("║  🌐 Génération NSFW DEPUIS INTERNET (Ultra-patient)     ║")
    print("╚═══════════════════════════════════════════════════════════╝\n")
    print(f"🔗 ComfyUI: {COMFYUI_URL}")
    print(f"📊 Personnages: {len(CHARACTERS)} (test)")
    print(f"🖼️ Images/perso: 3")
    print(f"📁 Output: {OUTPUT_DIR}/")
    print(f"⏱️ Timeout/image: {MAX_WAIT_PER_IMAGE//60} min")
    print(f"🚀 Steps: 6 (ultra rapide)")
    print(f"📐 Résolution: 512x512\n")
    
    # Test connexion
    print("🔍 Testing connection...")
    try:
        r = requests.get(COMFYUI_URL, timeout=(TIMEOUT_CONNECT, 30))
        if r.status_code == 200:
            print(f"  ✅ ComfyUI accessible ({r.elapsed.total_seconds():.1f}s)\n")
        else:
            print(f"  ⚠️ HTTP {r.status_code}\n")
    except Exception as e:
        print(f"  ❌ Error: {e}\n")
        return
    
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    total = 0
    start_global = time.time()
    
    for char_id, prompt in CHARACTERS.items():
        print(f"\n{'='*70}")
        print(f"  👤 {char_id.upper()}")
        print(f"{'='*70}")
        
        for i in range(1, 4):
            img_data = generate_one_image(char_id, prompt, i)
            
            if img_data:
                filepath = os.path.join(OUTPUT_DIR, f"{char_id}nsfw{i}.png")
                with open(filepath, 'wb') as f:
                    f.write(img_data)
                print(f"      💾 Saved: {filepath}")
                total += 1
            else:
                print(f"      ❌ Failed")
            
            # Pause
            if i < 3:
                print(f"      ⏳ Wait 5s...")
                time.sleep(5)
        
        # Pause entre persos
        print(f"\n  ⏳ Wait 10s before next...")
        time.sleep(10)
    
    elapsed = time.time() - start_global
    
    print(f"\n\n{'='*70}")
    print(f"🎉 COMPLETED")
    print(f"{'='*70}")
    print(f"✅ Images: {total}/{len(CHARACTERS) * 3}")
    print(f"⏱️ Time: {elapsed/60:.1f} minutes")
    print(f"📁 {os.path.abspath(OUTPUT_DIR)}")

if __name__ == "__main__":
    main()
