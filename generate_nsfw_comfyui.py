#!/usr/bin/env python3
"""
Générateur de galeries NSFW via ComfyUI sur Freebox
Utilise l'API WebSocket de ComfyUI pour générer 195 images NSFW
13 personnages × 15 images = 195 images totales
"""

import requests
import json
import time
import websocket
import uuid
import base64
from urllib.parse import urlencode

# Configuration ComfyUI Freebox
COMFYUI_URL = "http://88.174.155.230:33437"
COMFYUI_WS = "ws://88.174.155.230:33437/ws"
OUTPUT_DIR = "/workspace/character_images"

# Personnages avec descriptions physiques
CHARACTERS = {
    "Naruto": {
        "desc": "athletic 20-year-old male with spiky blonde hair, ocean blue eyes, whisker marks on cheeks, tanned skin, lean muscular build",
        "negative": "female, woman, breasts"
    },
    "Sasuke": {
        "desc": "athletic 20-year-old male with jet black spiky hair, dark intense eyes, pale skin, lean muscular build",
        "negative": "female, woman, breasts"
    },
    "Sakura": {
        "desc": "athletic 20-year-old female with pink shoulder-length hair, bright green eyes, fair porcelain skin, toned feminine figure, delicate features",
        "negative": "male, penis, masculine"
    },
    "Hinata": {
        "desc": "elegant 20-year-old female with long indigo-blue hair, lavender pearl eyes, porcelain skin, graceful feminine curves",
        "negative": "male, penis, masculine"
    },
    "Ino": {
        "desc": "attractive 20-year-old female with long platinum blonde hair in ponytail, light blue eyes, fair skin, slender curvy figure",
        "negative": "male, penis, masculine"
    },
    "Tsunade": {
        "desc": "voluptuous mature woman 30-looking with long blonde hair in twin tails, honey-brown eyes, fair skin, legendary hourglass figure",
        "negative": "male, penis, masculine"
    },
    "Kushina": {
        "desc": "beautiful mature woman with long red hair, violet eyes, fair skin, curvy feminine figure, warm motherly features",
        "negative": "male, penis, masculine"
    },
    "Temari": {
        "desc": "athletic 21-year-old female with blonde hair in four ponytails, teal eyes, tanned skin, strong toned figure",
        "negative": "male, penis, masculine"
    },
    "TenTen": {
        "desc": "athletic 20-year-old female with brown hair in twin buns, dark eyes, fair skin, fit toned figure",
        "negative": "male, penis, masculine"
    },
    "Konan": {
        "desc": "elegant 30-year-old female with blue hair, amber eyes, pale skin, slender graceful figure, piercing on lip",
        "negative": "male, penis, masculine"
    },
    "Mei": {
        "desc": "beautiful mature woman 35 with long auburn-red hair, green eyes, fair skin, voluptuous hourglass figure",
        "negative": "male, penis, masculine"
    },
    "Anko": {
        "desc": "wild 30-year-old female with short spiky purple hair, dark eyes, fair skin, curvy athletic figure",
        "negative": "male, penis, masculine"
    },
    "Kaguya": {
        "desc": "ethereal goddess-like woman with long white hair, pale lavender eyes, porcelain white skin, perfect hourglass figure",
        "negative": "male, penis, masculine"
    }
}

# Variations NSFW progressives
NSFW_LEVELS = [
    # Niveau 1-3: Suggestif
    ("seductive pose, bedroom eyes, intimate lighting, sultry expression", 20),
    ("alluring pose, sensual gaze, romantic atmosphere, teasing expression", 20),
    ("provocative pose, flirtatious smile, dim lighting, suggestive", 20),
    
    # Niveau 4-6: Léger
    ("sensual pose, revealing outfit, soft lighting, confident expression", 25),
    ("intimate pose, elegant lingerie, romantic setting, seductive look", 25),
    ("alluring pose, partially undressed, atmospheric lighting, sultry", 25),
    
    # Niveau 7-9: Modéré
    ("erotic pose, minimal clothing, intimate setting, passionate expression", 30),
    ("sensual nude pose, artistic lighting, bedroom scene, seductive gaze", 30),
    ("intimate nude pose, romantic atmosphere, sensual expression", 30),
    
    # Niveau 10-12: Explicite
    ("explicit erotic pose, full nudity, intimate bedroom, passionate", 35),
    ("erotic full nude pose, sensual lighting, intimate setting, seductive", 35),
    ("explicit nude pose, provocative angle, bedroom scene, lustful", 35),
    
    # Niveau 13-15: Très explicite
    ("explicit erotic scene, full nudity, intimate act suggestion, passionate", 40),
    ("highly explicit erotic pose, full nude body, intimate bedroom, intense", 40),
    ("extreme explicit erotic scene, full nudity, provocative intimate pose", 40)
]

def create_workflow(prompt, negative_prompt, seed, steps=25):
    """Crée un workflow ComfyUI pour txt2img"""
    workflow = {
        "3": {
            "inputs": {
                "seed": seed,
                "steps": steps,
                "cfg": 8.0,
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
                "height": 768,  # Portrait
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

def submit_prompt(workflow, client_id):
    """Soumet un prompt à ComfyUI"""
    data = {
        "prompt": workflow,
        "client_id": client_id
    }
    
    response = requests.post(f"{COMFYUI_URL}/prompt", json=data, timeout=30)
    if response.status_code != 200:
        raise Exception(f"Erreur soumission: {response.status_code} - {response.text}")
    
    return response.json()["prompt_id"]

def wait_for_completion(prompt_id, client_id, timeout=600):
    """Attend la complétion via WebSocket"""
    ws_url = f"{COMFYUI_WS}?clientId={client_id}"
    
    completed = False
    start_time = time.time()
    
    def on_message(ws, message):
        nonlocal completed
        try:
            data = json.loads(message)
            if data.get("type") == "executing":
                node = data.get("data", {}).get("node")
                if node is None:  # Exécution terminée
                    completed = True
                    ws.close()
        except Exception as e:
            print(f"  Erreur parsing WebSocket: {e}")
    
    def on_error(ws, error):
        print(f"  Erreur WebSocket: {error}")
    
    ws = websocket.WebSocketApp(ws_url,
                                on_message=on_message,
                                on_error=on_error)
    
    # Lancer WebSocket dans un thread
    import threading
    ws_thread = threading.Thread(target=ws.run_forever)
    ws_thread.daemon = True
    ws_thread.start()
    
    # Attendre complétion
    while not completed and (time.time() - start_time) < timeout:
        time.sleep(1)
    
    ws.close()
    
    if not completed:
        raise Exception("Timeout génération")
    
    return True

def get_image(prompt_id):
    """Récupère l'image générée"""
    # Récupérer historique
    response = requests.get(f"{COMFYUI_URL}/history/{prompt_id}", timeout=30)
    if response.status_code != 200:
        raise Exception(f"Erreur récupération historique: {response.status_code}")
    
    history = response.json()
    if prompt_id not in history:
        raise Exception("Prompt ID introuvable dans historique")
    
    outputs = history[prompt_id]["outputs"]
    
    # Trouver l'image
    for node_id, output in outputs.items():
        if "images" in output and len(output["images"]) > 0:
            img_info = output["images"][0]
            filename = img_info["filename"]
            subfolder = img_info.get("subfolder", "")
            img_type = img_info.get("type", "output")
            
            # Construire URL
            params = {
                "filename": filename,
                "type": img_type
            }
            if subfolder:
                params["subfolder"] = subfolder
            
            url = f"{COMFYUI_URL}/view?{urlencode(params)}"
            
            # Télécharger
            img_response = requests.get(url, timeout=60)
            if img_response.status_code == 200:
                return img_response.content
            else:
                raise Exception(f"Erreur téléchargement image: {img_response.status_code}")
    
    raise Exception("Aucune image trouvée dans outputs")

def generate_image(prompt, negative_prompt, steps=25):
    """Génère une image via ComfyUI"""
    client_id = str(uuid.uuid4())
    seed = int(time.time() * 1000) % 2147483647
    
    # Créer workflow
    workflow = create_workflow(prompt, negative_prompt, seed, steps)
    
    # Soumettre
    prompt_id = submit_prompt(workflow, client_id)
    
    # Attendre
    wait_for_completion(prompt_id, client_id)
    
    # Récupérer image
    img_data = get_image(prompt_id)
    
    return img_data

def main():
    print("="*70)
    print("🔞 GÉNÉRATION GALERIES NSFW via ComfyUI Freebox")
    print("="*70)
    print(f"\n📦 {len(CHARACTERS)} personnages × 15 images = {len(CHARACTERS) * 15} images")
    print(f"🌐 ComfyUI: {COMFYUI_URL}")
    print(f"📁 Output: {OUTPUT_DIR}")
    print(f"⏱️  Temps estimé: ~{len(CHARACTERS) * 15 * 2}min (CPU ARM)")
    print("\n⚠️  ATTENTION: Contenu NSFW explicite 18+")
    print("\nDébut dans 3 secondes...")
    time.sleep(3)
    
    total_success = 0
    total_images = 0
    start_time = time.time()
    
    for char_name, char_info in CHARACTERS.items():
        print(f"\n{'='*70}")
        print(f"📸 PERSONNAGE: {char_name}")
        print(f"{'='*70}")
        
        char_success = 0
        
        for i, (nsfw_variation, steps) in enumerate(NSFW_LEVELS, 1):
            print(f"\n[{char_name}] Image {i}/15 - Niveau {(i-1)//3 + 1}/5")
            
            # Construire prompt
            prompt = f"NSFW explicit adult content 18+, {char_info['desc']}, {nsfw_variation}, "
            prompt += "photorealistic, hyperdetailed, 8k uhd, sharp focus, cinematic lighting, "
            prompt += "anatomically correct, perfect anatomy, masterpiece quality, high resolution"
            
            negative = f"low quality, blurry, distorted, ugly, deformed, {char_info['negative']}, "
            negative += "bad anatomy, extra limbs, watermark, text, censored"
            
            filename = f"{OUTPUT_DIR}/{char_name.lower()}_nsfw_{i:02d}.png"
            
            print(f"  Prompt: {nsfw_variation[:50]}...")
            print(f"  Steps: {steps}")
            
            try:
                img_data = generate_image(prompt, negative, steps)
                
                # Sauvegarder
                with open(filename, 'wb') as f:
                    f.write(img_data)
                
                print(f"  ✅ OK ({len(img_data) // 1024}KB) - {filename}")
                char_success += 1
                total_success += 1
                
            except Exception as e:
                print(f"  ❌ Erreur: {e}")
            
            total_images += 1
            
            # Pause courte (ComfyUI local, pas de rate limit)
            if i < len(NSFW_LEVELS):
                print(f"  ⏳ Pause 3s...")
                time.sleep(3)
        
        print(f"\n✅ {char_name}: {char_success}/15 images générées")
        
        # Pause entre personnages
        print(f"\n⏸️  Pause 10s avant personnage suivant...")
        time.sleep(10)
    
    elapsed = time.time() - start_time
    
    print("\n" + "="*70)
    print("📊 RÉSUMÉ FINAL")
    print("="*70)
    print(f"✅ Images générées: {total_success}/{total_images}")
    print(f"⏱️  Temps total: {elapsed/60:.1f} minutes")
    print(f"📁 Dossier: {OUTPUT_DIR}")
    print("\n🎉 Génération terminée !")

if __name__ == "__main__":
    main()
