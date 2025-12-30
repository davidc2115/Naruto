#!/usr/bin/env python3
"""
Génération NSFW batch optimisée (3 images en parallèle)
"""

import os
import time
import json
import requests
from uuid import uuid4
from concurrent.futures import ThreadPoolExecutor, as_completed

COMFYUI_URL = "http://88.174.155.230:33437"
OUTPUT_DIR = "character_images_nsfw_batch"
MAX_WORKERS = 3  # 3 générations parallèles

# 13 personnages × 3 images = 39
CHARACTERS = {
    "naruto1": "Naruto Uzumaki, young adult male 18+, spiky blonde hair, blue eyes, nude, NSFW explicit",
    "naruto2": "Naruto Uzumaki, young adult male 18+, spiky blonde hair, blue eyes, nude, NSFW explicit",
    "naruto3": "Naruto Uzumaki, young adult male 18+, spiky blonde hair, blue eyes, nude, NSFW explicit",
    "sakura1": "Sakura Haruno, young adult woman 18+, pink hair, green eyes, nude, NSFW explicit",
    "sakura2": "Sakura Haruno, young adult woman 18+, pink hair, green eyes, nude, NSFW explicit",
    "sakura3": "Sakura Haruno, young adult woman 18+, pink hair, green eyes, nude, NSFW explicit",
    "hinata1": "Hinata Hyuga, young adult woman 18+, long dark hair, pale eyes, nude, NSFW explicit",
    "hinata2": "Hinata Hyuga, young adult woman 18+, long dark hair, pale eyes, nude, NSFW explicit",
    "hinata3": "Hinata Hyuga, young adult woman 18+, long dark hair, pale eyes, nude, NSFW explicit"
}

def workflow(prompt, seed):
    return {
        "3": {"inputs": {"seed": seed, "steps": 4, "cfg": 3.5, "sampler_name": "euler", "scheduler": "simple", "denoise": 1.0, "model": ["4", 0], "positive": ["6", 0], "negative": ["7", 0], "latent_image": ["5", 0]}, "class_type": "KSampler"},
        "4": {"inputs": {"ckpt_name": "sd_v15.safetensors"}, "class_type": "CheckpointLoaderSimple"},
        "5": {"inputs": {"width": 384, "height": 512, "batch_size": 1}, "class_type": "EmptyLatentImage"},
        "6": {"inputs": {"text": prompt, "clip": ["4", 1]}, "class_type": "CLIPTextEncode"},
        "7": {"inputs": {"text": "low quality", "clip": ["4", 1]}, "class_type": "CLIPTextEncode"},
        "8": {"inputs": {"samples": ["3", 0], "vae": ["4", 2]}, "class_type": "VAEDecode"},
        "9": {"inputs": {"filename_prefix": "nsfw", "images": ["8", 0]}, "class_type": "SaveImage"}
    }

def generate_one(img_id, prompt):
    """Génère 1 image (steps=4, 384x512 pour vitesse)"""
    try:
        print(f"[{img_id}] Start...")
        
        # Submit
        seed = int(time.time() * 1000 + hash(img_id)) % 2147483647
        r = requests.post(f"{COMFYUI_URL}/prompt", json={"prompt": workflow(prompt, seed), "client_id": str(uuid4())}, timeout=(20, 90))
        prompt_id = r.json()["prompt_id"]
        print(f"[{img_id}] Submitted: {prompt_id}")
        
        # Wait (max 8 min)
        start = time.time()
        for _ in range(48):  # 48 × 10s = 8 min
            time.sleep(10)
            elapsed = int(time.time() - start)
            
            r2 = requests.get(f"{COMFYUI_URL}/history/{prompt_id}", timeout=(20, 45))
            history = r2.json()
            
            if prompt_id in history and "outputs" in history[prompt_id]:
                outputs = history[prompt_id]["outputs"]
                
                for node_id, node_out in outputs.items():
                    if "images" in node_out and node_out["images"]:
                        filename = node_out["images"][0]["filename"]
                        
                        # Download
                        r3 = requests.get(f"{COMFYUI_URL}/view", params={"filename": filename, "type": "output"}, timeout=(20, 90))
                        
                        filepath = os.path.join(OUTPUT_DIR, f"{img_id}.png")
                        with open(filepath, 'wb') as f:
                            f.write(r3.content)
                        
                        print(f"[{img_id}] ✅ Done in {elapsed}s ({len(r3.content)//1024}KB)")
                        return True
        
        print(f"[{img_id}] ❌ Timeout")
        return False
        
    except Exception as e:
        print(f"[{img_id}] ❌ Error: {e}")
        return False

def main():
    print("╔═══════════════════════════════════════════╗")
    print("║  🚀 Génération NSFW Batch (3 parallèles) ║")
    print("╚═══════════════════════════════════════════╝\n")
    print(f"Images: {len(CHARACTERS)}")
    print(f"Workers: {MAX_WORKERS}")
    print(f"Settings: 4 steps, 384x512, cfg 3.5\n")
    
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    start_global = time.time()
    success = 0
    
    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        futures = {executor.submit(generate_one, img_id, prompt): img_id for img_id, prompt in CHARACTERS.items()}
        
        for future in as_completed(futures):
            img_id = futures[future]
            if future.result():
                success += 1
            print(f"\nProgress: {success}/{len(CHARACTERS)}\n")
    
    elapsed = time.time() - start_global
    
    print(f"\n{'='*50}")
    print(f"✅ Success: {success}/{len(CHARACTERS)}")
    print(f"⏱️ Time: {elapsed/60:.1f} min")
    print(f"📁 {os.path.abspath(OUTPUT_DIR)}")

if __name__ == "__main__":
    main()
