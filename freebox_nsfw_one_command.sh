#!/bin/bash
# Génération NSFW Freebox - ONE COMMAND
# À lancer sur la Freebox via SSH

cd /tmp

cat > gen_nsfw.py << 'EOFPYTHON'
#!/usr/bin/env python3
"""Génération NSFW locale Freebox (13 persos × 3 images = 39)"""
import os, time, json, requests
from uuid import uuid4

URL = "http://127.0.0.1:33437"
OUT = "/tmp/nsfw_gallery"
os.makedirs(OUT, exist_ok=True)

CHARS = {
    "naruto_1": "Naruto Uzumaki, young adult male 18+, spiky blonde hair, blue eyes, athletic body, nude, NSFW explicit",
    "naruto_2": "Naruto Uzumaki, young adult male 18+, spiky blonde hair, blue eyes, muscular, nude, NSFW explicit",
    "naruto_3": "Naruto Uzumaki, young adult male 18+, blonde hair, tan skin, nude, NSFW explicit",
    "sakura_1": "Sakura Haruno, young adult woman 18+, pink hair, green eyes, petite body, nude, NSFW explicit",
    "sakura_2": "Sakura Haruno, young adult woman 18+, pink hair, athletic body, nude, NSFW explicit",
    "sakura_3": "Sakura Haruno, young adult woman 18+, pink short hair, nude, NSFW explicit",
    "hinata_1": "Hinata Hyuga, young adult woman 18+, long dark hair, pale lavender eyes, curvy body, nude, NSFW explicit",
    "hinata_2": "Hinata Hyuga, young adult woman 18+, dark hair, pale skin, large breasts, nude, NSFW explicit",
    "hinata_3": "Hinata Hyuga, young adult woman 18+, long hair, shy expression, nude, NSFW explicit",
    "sasuke_1": "Sasuke Uchiha, young adult male 18+, black hair, dark eyes, athletic body, nude, NSFW explicit",
    "sasuke_2": "Sasuke Uchiha, young adult male 18+, spiky black hair, muscular, nude, NSFW explicit",
    "sasuke_3": "Sasuke Uchiha, young adult male 18+, dark hair, pale skin, nude, NSFW explicit",
    "kakashi_1": "Kakashi Hatake, adult male 25+, silver hair, muscular body, nude, NSFW explicit",
    "kakashi_2": "Kakashi Hatake, adult male 25+, white spiky hair, athletic, nude, NSFW explicit",
    "kakashi_3": "Kakashi Hatake, adult male 25+, silver hair, toned body, nude, NSFW explicit",
    "tsunade_1": "Tsunade Senju, mature woman 30+, blonde hair, large breasts, curvy body, nude, NSFW explicit",
    "tsunade_2": "Tsunade Senju, mature woman 30+, long blonde hair, voluptuous, nude, NSFW explicit",
    "tsunade_3": "Tsunade Senju, mature woman 30+, blonde ponytail, busty, nude, NSFW explicit",
    "ino_1": "Ino Yamanaka, young adult woman 18+, long blonde hair, blue eyes, slim body, nude, NSFW explicit",
    "ino_2": "Ino Yamanaka, young adult woman 18+, platinum blonde hair, athletic, nude, NSFW explicit",
    "ino_3": "Ino Yamanaka, young adult woman 18+, blonde ponytail, nude, NSFW explicit",
    "temari_1": "Temari, young adult woman 18+, blonde hair with four ponytails, athletic body, nude, NSFW explicit",
    "temari_2": "Temari, young adult woman 18+, blonde spiky hair, toned body, nude, NSFW explicit",
    "temari_3": "Temari, young adult woman 18+, blonde hair, strong physique, nude, NSFW explicit",
    "tenten_1": "Tenten, young adult woman 18+, brown hair in twin buns, athletic body, nude, NSFW explicit",
    "tenten_2": "Tenten, young adult woman 18+, dark brown hair, slim, nude, NSFW explicit",
    "tenten_3": "Tenten, young adult woman 18+, hair buns, toned body, nude, NSFW explicit",
    "gaara_1": "Gaara, young adult male 18+, red hair, pale skin, lean body, nude, NSFW explicit",
    "gaara_2": "Gaara, young adult male 18+, spiky red hair, athletic, nude, NSFW explicit",
    "gaara_3": "Gaara, young adult male 18+, short red hair, muscular, nude, NSFW explicit",
    "jiraiya_1": "Jiraiya, adult male 30+, long white hair, muscular body, nude, NSFW explicit",
    "jiraiya_2": "Jiraiya, adult male 30+, white spiky hair, strong physique, nude, NSFW explicit",
    "jiraiya_3": "Jiraiya, adult male 30+, white hair, toned body, nude, NSFW explicit",
    "orochimaru_1": "Orochimaru, adult male 25+, long black hair, pale skin, slim body, nude, NSFW explicit",
    "orochimaru_2": "Orochimaru, adult male 25+, dark hair, androgynous body, nude, NSFW explicit",
    "orochimaru_3": "Orochimaru, adult male 25+, black hair, lean physique, nude, NSFW explicit",
    "konan_1": "Konan, young adult woman 18+, blue hair with paper flower, athletic body, nude, NSFW explicit",
    "konan_2": "Konan, young adult woman 18+, short blue hair, slim, nude, NSFW explicit",
    "konan_3": "Konan, young adult woman 18+, blue-purple hair, graceful body, nude, NSFW explicit"
}

def wf(p, s):
    return {
        "3": {"inputs": {"seed": s, "steps": 6, "cfg": 4.0, "sampler_name": "euler", "scheduler": "simple", "denoise": 1.0, "model": ["4", 0], "positive": ["6", 0], "negative": ["7", 0], "latent_image": ["5", 0]}, "class_type": "KSampler"},
        "4": {"inputs": {"ckpt_name": "sd_v15.safetensors"}, "class_type": "CheckpointLoaderSimple"},
        "5": {"inputs": {"width": 512, "height": 512, "batch_size": 1}, "class_type": "EmptyLatentImage"},
        "6": {"inputs": {"text": p, "clip": ["4", 1]}, "class_type": "CLIPTextEncode"},
        "7": {"inputs": {"text": "low quality, blurry, deformed", "clip": ["4", 1]}, "class_type": "CLIPTextEncode"},
        "8": {"inputs": {"samples": ["3", 0], "vae": ["4", 2]}, "class_type": "VAEDecode"},
        "9": {"inputs": {"filename_prefix": "nsfw", "images": ["8", 0]}, "class_type": "SaveImage"}
    }

def gen(iid, prompt):
    try:
        print(f"\n[{iid}] Start...")
        seed = int(time.time() * 1000 + hash(iid)) % 2147483647
        
        r = requests.post(f"{URL}/prompt", json={"prompt": wf(prompt, seed), "client_id": str(uuid4())}, timeout=30)
        pid = r.json()["prompt_id"]
        print(f"[{iid}] Submitted: {pid}")
        
        start = time.time()
        for _ in range(60):  # 60 × 10s = 10 min max
            time.sleep(10)
            r2 = requests.get(f"{URL}/history/{pid}", timeout=30)
            h = r2.json()
            
            if pid in h and "outputs" in h[pid]:
                for nid, nout in h[pid]["outputs"].items():
                    if "images" in nout and nout["images"]:
                        fn = nout["images"][0]["filename"]
                        r3 = requests.get(f"{URL}/view", params={"filename": fn, "type": "output"}, timeout=60)
                        
                        fp = os.path.join(OUT, f"{iid}.png")
                        with open(fp, 'wb') as f:
                            f.write(r3.content)
                        
                        elapsed = int(time.time() - start)
                        print(f"[{iid}] ✅ Done in {elapsed}s ({len(r3.content)//1024}KB)")
                        return True
        
        print(f"[{iid}] ❌ Timeout")
        return False
    except Exception as e:
        print(f"[{iid}] ❌ Error: {e}")
        return False

print("╔═══════════════════════════════════════════╗")
print("║  🚀 Génération NSFW Freebox (39 images)  ║")
print("╚═══════════════════════════════════════════╝\n")
print(f"ComfyUI: {URL}")
print(f"Output: {OUT}")
print(f"Images: {len(CHARS)}\n")

start_global = time.time()
success = 0

for i, (iid, prompt) in enumerate(CHARS.items(), 1):
    print(f"\n{'='*50}")
    print(f"Progress: {i}/{len(CHARS)}")
    print(f"{'='*50}")
    
    if gen(iid, prompt):
        success += 1
    
    if i < len(CHARS):
        print(f"\n💤 Wait 5s...\n")
        time.sleep(5)

elapsed = time.time() - start_global
print(f"\n{'='*60}")
print(f"✅ Success: {success}/{len(CHARS)}")
print(f"⏱️ Time: {elapsed/60:.1f} min ({elapsed/60/success:.1f} min/img)")
print(f"📁 {OUT}")
print(f"\nArchive: tar -czf nsfw_gallery.tar.gz {OUT}")
print(f"{'='*60}\n")
EOFPYTHON

echo "🚀 Lancement génération..."
python3 gen_nsfw.py

echo ""
echo "✅ Terminé !"
echo "📁 Images dans : /tmp/nsfw_gallery/"
echo "📦 Archive : tar -czf /tmp/nsfw_gallery.tar.gz /tmp/nsfw_gallery/"
