#!/usr/bin/env python3
"""
Génération NSFW séquentielle ULTRA-PATIENTE (1 par 1, 20 min max par image)
"""

import os
import time
import json
import requests
from uuid import uuid4

COMFYUI_URL = "http://88.174.155.230:33437"
OUTPUT_DIR = "character_images_nsfw_seq"
MAX_WAIT_PER_IMAGE = 1200  # 20 minutes

# Test 3 images seulement
CHARACTERS = {
    "naruto1": "Naruto Uzumaki, young adult male 18+, spiky blonde hair, blue eyes, nude, NSFW explicit",
    "sakura1": "Sakura Haruno, young adult woman 18+, pink hair, green eyes, nude, NSFW explicit",
    "hinata1": "Hinata Hyuga, young adult woman 18+, long dark hair, pale eyes, nude, NSFW explicit"
}

def workflow(prompt, seed):
    """Workflow minimaliste: 3 steps, 384x384"""
    return {
        "3": {"inputs": {"seed": seed, "steps": 3, "cfg": 3.0, "sampler_name": "euler", "scheduler": "simple", "denoise": 1.0, "model": ["4", 0], "positive": ["6", 0], "negative": ["7", 0], "latent_image": ["5", 0]}, "class_type": "KSampler"},
        "4": {"inputs": {"ckpt_name": "sd_v15.safetensors"}, "class_type": "CheckpointLoaderSimple"},
        "5": {"inputs": {"width": 384, "height": 384, "batch_size": 1}, "class_type": "EmptyLatentImage"},
        "6": {"inputs": {"text": prompt, "clip": ["4", 1]}, "class_type": "CLIPTextEncode"},
        "7": {"inputs": {"text": "low quality", "clip": ["4", 1]}, "class_type": "CLIPTextEncode"},
        "8": {"inputs": {"samples": ["3", 0], "vae": ["4", 2]}, "class_type": "VAEDecode"},
        "9": {"inputs": {"filename_prefix": "nsfw", "images": ["8", 0]}, "class_type": "SaveImage"}
    }

def generate_one(img_id, prompt):
    """Génère 1 image avec timeout 20 min"""
    print(f"\n{'='*60}")
    print(f"🎨 [{img_id}] START")
    print(f"Prompt: {prompt[:80]}...")
    print(f"{'='*60}\n")
    
    try:
        # Submit
        seed = int(time.time() * 1000 + hash(img_id)) % 2147483647
        print(f"[{img_id}] 📤 Submitting workflow...")
        
        r = requests.post(
            f"{COMFYUI_URL}/prompt",
            json={"prompt": workflow(prompt, seed), "client_id": str(uuid4())},
            timeout=(30, 120)
        )
        
        if r.status_code != 200:
            print(f"[{img_id}] ❌ Submit failed: {r.status_code}")
            return False
        
        prompt_id = r.json().get("prompt_id")
        if not prompt_id:
            print(f"[{img_id}] ❌ No prompt_id in response")
            return False
        
        print(f"[{img_id}] ✅ Submitted: {prompt_id}")
        print(f"[{img_id}] ⏳ Waiting (max {MAX_WAIT_PER_IMAGE//60} min)...\n")
        
        # Wait
        start = time.time()
        check_count = 0
        
        while time.time() - start < MAX_WAIT_PER_IMAGE:
            check_count += 1
            elapsed = int(time.time() - start)
            
            print(f"[{img_id}] Check #{check_count} ({elapsed}s elapsed)...", end=" ")
            
            try:
                r2 = requests.get(f"{COMFYUI_URL}/history/{prompt_id}", timeout=(30, 60))
                history = r2.json()
                
                if prompt_id in history and "outputs" in history[prompt_id]:
                    outputs = history[prompt_id]["outputs"]
                    print("✅ DONE!")
                    
                    for node_id, node_out in outputs.items():
                        if "images" in node_out and node_out["images"]:
                            filename = node_out["images"][0]["filename"]
                            
                            print(f"[{img_id}] 📥 Downloading {filename}...")
                            r3 = requests.get(
                                f"{COMFYUI_URL}/view",
                                params={"filename": filename, "type": "output"},
                                timeout=(30, 120)
                            )
                            
                            filepath = os.path.join(OUTPUT_DIR, f"{img_id}.png")
                            with open(filepath, 'wb') as f:
                                f.write(r3.content)
                            
                            size_kb = len(r3.content) // 1024
                            print(f"\n🎉 [{img_id}] SUCCESS in {elapsed}s ({size_kb}KB)")
                            print(f"📁 {os.path.abspath(filepath)}\n")
                            return True
                else:
                    print("⏳ Still processing...")
                    
            except Exception as e:
                print(f"⚠️ Check error: {e}")
            
            time.sleep(15)  # Check every 15s
        
        print(f"\n❌ [{img_id}] TIMEOUT after {MAX_WAIT_PER_IMAGE//60} min")
        return False
        
    except Exception as e:
        print(f"\n❌ [{img_id}] ERROR: {e}")
        return False

def main():
    print("\n╔═════════════════════════════════════════════════╗")
    print("║  🐢 Génération NSFW Séquentielle ULTRA-PATIENTE ║")
    print("╚═════════════════════════════════════════════════╝\n")
    print(f"Images: {len(CHARACTERS)}")
    print(f"Timeout: {MAX_WAIT_PER_IMAGE//60} min/image")
    print(f"Settings: 3 steps, 384x384, cfg 3.0")
    print(f"Mode: SEQUENTIAL (1 by 1)\n")
    
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    start_global = time.time()
    results = {}
    
    for i, (img_id, prompt) in enumerate(CHARACTERS.items(), 1):
        print(f"\n📊 Progress: {i-1}/{len(CHARACTERS)} done")
        success = generate_one(img_id, prompt)
        results[img_id] = "✅" if success else "❌"
        
        if success:
            print(f"✨ Total success so far: {sum(1 for v in results.values() if v == '✅')}/{i}")
        
        # Pause entre images
        if i < len(CHARACTERS):
            print(f"\n💤 Waiting 30s before next image...\n")
            time.sleep(30)
    
    elapsed = time.time() - start_global
    success_count = sum(1 for v in results.values() if v == "✅")
    
    print(f"\n{'='*60}")
    print(f"📊 FINAL RESULTS")
    print(f"{'='*60}\n")
    
    for img_id, status in results.items():
        print(f"{status} {img_id}")
    
    print(f"\n✅ Success: {success_count}/{len(CHARACTERS)}")
    print(f"⏱️ Total time: {elapsed/60:.1f} min")
    print(f"📁 Output: {os.path.abspath(OUTPUT_DIR)}")
    print()

if __name__ == "__main__":
    main()
