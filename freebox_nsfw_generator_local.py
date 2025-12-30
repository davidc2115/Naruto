#!/usr/bin/env python3
"""
Script à exécuter LOCALEMENT sur la Freebox
Génère 39 images NSFW via ComfyUI local (rapide)
"""

import os
import sys
import time
import json
import requests
from uuid import uuid4

# Config locale
COMFYUI_URL = "http://127.0.0.1:33437"
OUTPUT_DIR = "/root/naruto_nsfw_gallery"
IMAGES_PER_CHARACTER = 3

# Timeouts réduits (local = rapide)
TIMEOUT_SUBMIT = 10
TIMEOUT_CHECK = 5
MAX_WAIT_PER_IMAGE = 300  # 5 minutes max par image

# Tous les personnages
CHARACTERS = {
    "naruto": {
        "name": "Naruto Uzumaki",
        "prompt": "Naruto Uzumaki, young adult male 18+ years old, spiky blonde hair, blue eyes, three whisker marks on each cheek, athletic muscular body, tan skin, abs, nude naked body, explicit NSFW adult content, photorealistic, masterpiece, best quality"
    },
    "sakura": {
        "name": "Sakura Haruno",
        "prompt": "Sakura Haruno, young adult woman 18+ years old, pink shoulder-length hair, green eyes, fair skin, feminine athletic body, medium breasts, beautiful face, nude naked body, explicit NSFW adult content, photorealistic, masterpiece, best quality"
    },
    "hinata": {
        "name": "Hinata Hyuga",
        "prompt": "Hinata Hyuga, young adult woman 18+ years old, long dark indigo hair, pale lavender eyes, very fair porcelain skin, hourglass figure, large breasts, shy gentle face, nude naked body, explicit NSFW adult content, photorealistic, masterpiece, best quality"
    },
    "ino": {
        "name": "Ino Yamanaka",
        "prompt": "Ino Yamanaka, young adult woman 18+ years old, long platinum blonde hair in high ponytail, blue-green eyes, fair skin, slender feminine curves, medium breasts, confident expression, nude naked body, explicit NSFW adult content, photorealistic, masterpiece, best quality"
    },
    "temari": {
        "name": "Temari",
        "prompt": "Temari, young adult woman 18+ years old, blonde hair in four spiky ponytails, teal eyes, fair skin, tall athletic figure, medium-large breasts, fierce beauty, nude naked body, explicit NSFW adult content, photorealistic, masterpiece, best quality"
    },
    "tsunade": {
        "name": "Tsunade",
        "prompt": "Tsunade, mature woman 30-40 years old, long blonde hair in low ponytails, golden eyes, fair skin, voluptuous hourglass figure, very large breasts, youthful beauty, nude naked body, explicit NSFW adult content, photorealistic, masterpiece, best quality"
    },
    "tenten": {
        "name": "Tenten",
        "prompt": "Tenten, young adult woman 18+ years old, dark brown hair in two buns, brown eyes, fair skin, athletic toned body, medium breasts, determined expression, nude naked body, explicit NSFW adult content, photorealistic, masterpiece, best quality"
    },
    "konan": {
        "name": "Konan",
        "prompt": "Konan, young adult woman 18+ years old, short straight blue-grey hair, amber eyes, pale skin, slender graceful curves, medium breasts, serene beauty, nude naked body, explicit NSFW adult content, photorealistic, masterpiece, best quality"
    },
    "kurenai": {
        "name": "Kurenai",
        "prompt": "Kurenai Yuhi, mature woman 30+ years old, long black curly hair, crimson red eyes, fair skin, curvaceous feminine figure, large breasts, elegant beauty, nude naked body, explicit NSFW adult content, photorealistic, masterpiece, best quality"
    },
    "anko": {
        "name": "Anko",
        "prompt": "Anko Mitarashi, young adult woman 18+ years old, short spiky purple hair, light brown eyes, tan skin, curvy athletic body, large breasts, playful expression, nude naked body, explicit NSFW adult content, photorealistic, masterpiece, best quality"
    },
    "kushina": {
        "name": "Kushina",
        "prompt": "Kushina Uzumaki, young adult woman 18+ years old, long vibrant red hair, violet eyes, fair skin, hourglass figure with curves, large breasts, beautiful face, nude naked body, explicit NSFW adult content, photorealistic, masterpiece, best quality"
    },
    "mikoto": {
        "name": "Mikoto",
        "prompt": "Mikoto Uchiha, mature woman 30+ years old, long straight black hair, dark eyes, fair skin, graceful feminine figure, medium-large breasts, elegant beauty, nude naked body, explicit NSFW adult content, photorealistic, masterpiece, best quality"
    },
    "sasuke": {
        "name": "Sasuke",
        "prompt": "Sasuke Uchiha, young adult male 18+ years old, black spiky hair, dark eyes, pale skin, slender athletic muscular body, abs, handsome face, nude naked body, explicit NSFW adult content, photorealistic, masterpiece, best quality"
    }
}

def create_workflow(prompt, seed):
    """Crée un workflow ComfyUI optimisé"""
    return {
        "3": {
            "inputs": {
                "seed": seed,
                "steps": 8,  # Rapide pour ARM
                "cfg": 5.0,
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
            "inputs": {"width": 512, "height": 768, "batch_size": 1},
            "class_type": "EmptyLatentImage"
        },
        "6": {
            "inputs": {"text": prompt, "clip": ["4", 1]},
            "class_type": "CLIPTextEncode"
        },
        "7": {
            "inputs": {
                "text": "low quality, blurry, distorted, ugly, deformed, bad anatomy, censored",
                "clip": ["4", 1]
            },
            "class_type": "CLIPTextEncode"
        },
        "8": {
            "inputs": {"samples": ["3", 0], "vae": ["4", 2]},
            "class_type": "VAEDecode"
        },
        "9": {
            "inputs": {
                "filename_prefix": "nsfw",
                "images": ["8", 0]
            },
            "class_type": "SaveImage"
        }
    }

def submit_prompt(workflow, client_id):
    """Soumet un prompt à ComfyUI"""
    try:
        response = requests.post(
            f"{COMFYUI_URL}/prompt",
            json={"prompt": workflow, "client_id": client_id},
            timeout=TIMEOUT_SUBMIT
        )
        
        if response.status_code == 200:
            data = response.json()
            return data.get("prompt_id")
        else:
            print(f"      ❌ Submit HTTP {response.status_code}")
            return None
            
    except Exception as e:
        print(f"      ❌ Submit error: {e}")
        return None

def wait_for_completion(prompt_id):
    """Attend la complétion avec polling"""
    start = time.time()
    
    while (time.time() - start) < MAX_WAIT_PER_IMAGE:
        try:
            response = requests.get(
                f"{COMFYUI_URL}/history/{prompt_id}",
                timeout=TIMEOUT_CHECK
            )
            
            if response.status_code == 200:
                history = response.json()
                
                if prompt_id in history:
                    outputs = history[prompt_id].get("outputs", {})
                    
                    if outputs:
                        elapsed = int(time.time() - start)
                        print(f"      ✅ Completed in {elapsed}s")
                        
                        # Extraire filename
                        for node_id, node_out in outputs.items():
                            if "images" in node_out and node_out["images"]:
                                img = node_out["images"][0]
                                return img.get("filename"), img.get("subfolder", "")
                        
                        return None, None
            
            elapsed = int(time.time() - start)
            if elapsed % 15 == 0:  # Log toutes les 15s
                print(f"      ⏳ {elapsed}s elapsed...")
            
            time.sleep(3)
            
        except Exception as e:
            print(f"      ⚠️ Check error: {e}")
            time.sleep(3)
    
    print(f"      ❌ Timeout after {MAX_WAIT_PER_IMAGE}s")
    return None, None

def download_image(filename, subfolder=""):
    """Télécharge l'image"""
    try:
        url = f"{COMFYUI_URL}/view"
        params = {
            "filename": filename,
            "subfolder": subfolder,
            "type": "output"
        }
        
        response = requests.get(url, params=params, timeout=30)
        
        if response.status_code == 200:
            size_kb = len(response.content) // 1024
            print(f"      ✅ Downloaded: {size_kb}KB")
            return response.content
        else:
            print(f"      ❌ Download HTTP {response.status_code}")
            return None
            
    except Exception as e:
        print(f"      ❌ Download error: {e}")
        return None

def generate_one_image(char_id, char_data, img_num):
    """Génère UNE image complète"""
    print(f"\n    📸 Image {img_num}/{IMAGES_PER_CHARACTER}")
    
    # Seed unique
    seed = int(time.time() * 1000) % 2147483647
    
    # Workflow
    workflow = create_workflow(char_data["prompt"], seed)
    client_id = str(uuid4())
    
    # Submit
    print(f"      📤 Submitting...")
    prompt_id = submit_prompt(workflow, client_id)
    if not prompt_id:
        return None
    
    print(f"      ✅ Prompt ID: {prompt_id}")
    
    # Wait
    filename, subfolder = wait_for_completion(prompt_id)
    if not filename:
        return None
    
    # Download
    return download_image(filename, subfolder)

def main():
    print("╔═══════════════════════════════════════════════════════════════╗")
    print("║  🎨 Freebox Local NSFW Gallery Generator (ComfyUI)          ║")
    print("╚═══════════════════════════════════════════════════════════════╝\n")
    
    print(f"📊 Configuration:")
    print(f"  - ComfyUI: {COMFYUI_URL}")
    print(f"  - Characters: {len(CHARACTERS)}")
    print(f"  - Images/character: {IMAGES_PER_CHARACTER}")
    print(f"  - Total images: {len(CHARACTERS) * IMAGES_PER_CHARACTER}")
    print(f"  - Output: {OUTPUT_DIR}/")
    print(f"  - Steps: 8 (fast for ARM CPU)")
    print(f"  - Resolution: 512x768")
    
    # Test ComfyUI
    print(f"\n🔍 Testing ComfyUI...")
    try:
        response = requests.get(COMFYUI_URL, timeout=10)
        if response.status_code == 200:
            print(f"  ✅ ComfyUI is running")
        else:
            print(f"  ⚠️ ComfyUI responded with HTTP {response.status_code}")
    except Exception as e:
        print(f"  ❌ ComfyUI not accessible: {e}")
        print(f"\n💡 Start ComfyUI with:")
        print(f"     cd /root/ComfyUI && python main.py --listen 0.0.0.0 --port 33437")
        sys.exit(1)
    
    # Create output directory
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    
    print(f"\n🚀 Starting generation...\n")
    
    total_success = 0
    total_attempts = 0
    start_time = time.time()
    
    for i, (char_id, char_data) in enumerate(CHARACTERS.items(), 1):
        print(f"\n{'='*70}")
        print(f"  👤 {char_data['name'].upper()} ({i}/{len(CHARACTERS)})")
        print(f"{'='*70}")
        
        char_success = 0
        
        for img_num in range(1, IMAGES_PER_CHARACTER + 1):
            total_attempts += 1
            
            img_data = generate_one_image(char_id, char_data, img_num)
            
            if img_data:
                filename = f"{char_id}nsfw{img_num}.png"
                filepath = os.path.join(OUTPUT_DIR, filename)
                
                with open(filepath, 'wb') as f:
                    f.write(img_data)
                
                print(f"      💾 Saved: {filepath}")
                total_success += 1
                char_success += 1
            else:
                print(f"      ❌ Failed to generate image {img_num}")
            
            # Petite pause entre images
            if img_num < IMAGES_PER_CHARACTER:
                time.sleep(2)
        
        print(f"\n  ✅ {char_data['name']}: {char_success}/{IMAGES_PER_CHARACTER} images")
        
        # Pause entre personnages
        if i < len(CHARACTERS):
            print(f"  ⏳ Pause 5s before next character...")
            time.sleep(5)
    
    elapsed = time.time() - start_time
    
    print(f"\n\n{'='*70}")
    print(f"🎉 GENERATION COMPLETED")
    print(f"{'='*70}")
    print(f"✅ Success: {total_success}/{len(CHARACTERS) * IMAGES_PER_CHARACTER}")
    print(f"📊 Success rate: {total_success/total_attempts*100:.1f}%")
    print(f"⏱️ Total time: {elapsed/60:.1f} minutes ({elapsed/3600:.1f} hours)")
    print(f"⚡ Average: {elapsed/total_attempts:.1f}s per image")
    print(f"📁 Output directory: {OUTPUT_DIR}")
    print(f"\n💡 Copy images to Android project:")
    print(f"   scp root@88.174.155.230:{OUTPUT_DIR}/*.png /workspace/character_images/")

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n⚠️ Generation interrupted by user")
        sys.exit(0)
    except Exception as e:
        print(f"\n\n❌ Fatal error: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
