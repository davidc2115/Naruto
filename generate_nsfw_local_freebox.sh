#!/bin/bash
# Script à lancer DIRECTEMENT sur la Freebox (en SSH)
# Génère les galeries NSFW localement via ComfyUI

COMFYUI_URL="http://localhost:33437"
OUTPUT_DIR="/root/character_images_nsfw"

mkdir -p "$OUTPUT_DIR"

echo "╔════════════════════════════════════════════════════╗"
echo "║  🎨 Générateur NSFW Local Freebox ComfyUI        ║"
echo "╚════════════════════════════════════════════════════╝"
echo ""
echo "📍 Running on Freebox locally"
echo "🔗 ComfyUI: $COMFYUI_URL"
echo "📁 Output: $OUTPUT_DIR"
echo ""

# Test ComfyUI
echo "🔍 Testing ComfyUI..."
if curl -s --max-time 10 "$COMFYUI_URL" > /dev/null; then
    echo "✅ ComfyUI accessible"
else
    echo "❌ ComfyUI not running!"
    echo "Start it with: cd /root/ComfyUI && python main.py --listen 0.0.0.0 --port 33437"
    exit 1
fi

# Personnages avec prompts NSFW
declare -A CHARACTERS
CHARACTERS[naruto]="Naruto Uzumaki, young adult male, spiky blonde hair, blue eyes, whisker marks, athletic muscular nude body, explicit NSFW 18+"
CHARACTERS[sakura]="Sakura Haruno, young adult woman, pink hair, green eyes, feminine nude body, medium breasts, explicit NSFW 18+"
CHARACTERS[hinata]="Hinata Hyuga, young adult woman, long dark indigo hair, pale eyes, nude hourglass figure, large breasts, explicit NSFW 18+"
CHARACTERS[ino]="Ino Yamanaka, young adult woman, platinum blonde ponytail, nude slender body, explicit NSFW 18+"
CHARACTERS[temari]="Temari, young adult woman, blonde four ponytails, nude tall athletic body, explicit NSFW 18+"
CHARACTERS[tsunade]="Tsunade, mature woman, blonde ponytails, nude voluptuous large breasts, explicit NSFW 18+"
CHARACTERS[tenten]="Tenten, young adult woman, brown hair buns, nude athletic toned body, explicit NSFW 18+"
CHARACTERS[konan]="Konan, young adult woman, short blue-grey hair, nude slender body, explicit NSFW 18+"
CHARACTERS[kurenai]="Kurenai, mature woman, long black curly hair, red eyes, nude curvaceous body, explicit NSFW 18+"
CHARACTERS[anko]="Anko, young adult woman, short purple hair, nude curvy body, explicit NSFW 18+"
CHARACTERS[kushina]="Kushina, young adult woman, long vibrant red hair, nude hourglass body, explicit NSFW 18+"
CHARACTERS[mikoto]="Mikoto, mature woman, long black hair, nude graceful body, explicit NSFW 18+"
CHARACTERS[sasuke]="Sasuke Uchiha, young adult male, black hair, dark eyes, nude slender athletic body, explicit NSFW 18+"

TOTAL=0

for CHAR_ID in "${!CHARACTERS[@]}"; do
    PROMPT="${CHARACTERS[$CHAR_ID]}"
    
    echo ""
    echo "======================================================================"
    echo "👤 $CHAR_ID"
    echo "======================================================================"
    
    for i in 1 2 3; do
        echo ""
        echo "📸 Image $i/3"
        echo "  📝 $PROMPT"
        
        # Génération via API Python (plus simple)
        python3 << EOF
import requests
import json
import time
from uuid import uuid4

def generate():
    workflow = {
        "3": {
            "inputs": {
                "seed": int(time.time() * 1000) % 2147483647,
                "steps": 8,
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
        "4": {"inputs": {"ckpt_name": "sd_v15.safetensors"}, "class_type": "CheckpointLoaderSimple"},
        "5": {"inputs": {"width": 512, "height": 768, "batch_size": 1}, "class_type": "EmptyLatentImage"},
        "6": {"inputs": {"text": "${PROMPT}", "clip": ["4", 1]}, "class_type": "CLIPTextEncode"},
        "7": {"inputs": {"text": "low quality, blurry", "clip": ["4", 1]}, "class_type": "CLIPTextEncode"},
        "8": {"inputs": {"samples": ["3", 0], "vae": ["4", 2]}, "class_type": "VAEDecode"},
        "9": {"inputs": {"filename_prefix": "nsfw_${CHAR_ID}_${i}", "images": ["8", 0]}, "class_type": "SaveImage"}
    }
    
    client_id = str(uuid4())
    
    # Submit
    r = requests.post("${COMFYUI_URL}/prompt", json={"prompt": workflow, "client_id": client_id}, timeout=30)
    if r.status_code != 200:
        print(f"  ❌ Submit failed: {r.status_code}")
        return False
    
    prompt_id = r.json()["prompt_id"]
    print(f"  ✅ Submitted: {prompt_id}")
    
    # Wait (local = fast, ~30-60s)
    for _ in range(120):  # 10 minutes max
        time.sleep(5)
        hr = requests.get(f"${COMFYUI_URL}/history/{prompt_id}", timeout=10)
        if hr.status_code == 200:
            history = hr.json()
            if prompt_id in history and "outputs" in history[prompt_id]:
                print(f"  ✅ Generated!")
                
                # Download image
                outputs = history[prompt_id]["outputs"]
                for node_id, node_out in outputs.items():
                    if "images" in node_out and node_out["images"]:
                        filename = node_out["images"][0]["filename"]
                        subfolder = node_out["images"][0].get("subfolder", "")
                        
                        img_url = f"${COMFYUI_URL}/view?filename={filename}&subfolder={subfolder}&type=output"
                        img_r = requests.get(img_url, timeout=30)
                        if img_r.status_code == 200:
                            with open("${OUTPUT_DIR}/${CHAR_ID}nsfw${i}.png", "wb") as f:
                                f.write(img_r.content)
                            print(f"  💾 ${OUTPUT_DIR}/${CHAR_ID}nsfw${i}.png")
                            return True
    
    print(f"  ❌ Timeout")
    return False

generate()
EOF
        
        if [ $? -eq 0 ]; then
            ((TOTAL++))
        fi
        
        sleep 3
    done
    
    echo ""
    echo "⏳ Pause 10s before next character..."
    sleep 10
done

echo ""
echo "======================================================================"
echo "🎉 COMPLETED"
echo "======================================================================"
echo "✅ Images: $TOTAL/39"
echo "📁 $OUTPUT_DIR"
