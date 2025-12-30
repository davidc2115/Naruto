#!/usr/bin/env python3
"""Génération simple Pollination AI - Test rapide"""
import os, time, requests
from PIL import Image
from io import BytesIO

API = "https://image.pollinations.ai/prompt"
OUT = "character_images_nsfw"
os.makedirs(OUT, exist_ok=True)

CHARS = {
    "sakura_1": "Sakura Haruno anime girl pink hair green eyes NSFW nude",
    "sakura_2": "Sakura pink hair anime woman nude NSFW",
    "sakura_3": "anime girl pink hair Sakura nude NSFW explicit",
    "hinata_1": "Hinata Hyuga anime girl dark hair pale eyes NSFW nude",
    "hinata_2": "Hinata dark hair anime woman nude NSFW",
    "hinata_3": "anime girl dark hair Hinata nude NSFW explicit"
}

def gen(iid, prompt):
    try:
        print(f"[{iid}] {prompt[:50]}...", flush=True)
        
        # URL simple
        url = f"{API}/{prompt.replace(' ', '%20')}?width=512&height=768&nologo=true"
        
        r = requests.get(url, timeout=60, stream=True)
        
        if r.status_code != 200:
            print(f"[{iid}] ❌ HTTP {r.status_code}")
            return False
        
        content = r.content
        
        # Quick check
        if len(content) < 5000 or b'<html' in content[:200]:
            print(f"[{iid}] ❌ Invalid")
            return False
        
        # Save
        fp = os.path.join(OUT, f"{iid}.png")
        with open(fp, 'wb') as f:
            f.write(content)
        
        print(f"[{iid}] ✅ {len(content)//1024}KB")
        return True
        
    except Exception as e:
        print(f"[{iid}] ❌ {e}")
        return False

print("🚀 Génération Sakura + Hinata (6 images)...\n")
start = time.time()
success = 0

for i, (iid, prompt) in enumerate(CHARS.items(), 1):
    print(f"\n[{i}/6] ", end="")
    if gen(iid, prompt):
        success += 1
    time.sleep(10)

print(f"\n\n✅ Success: {success}/6")
print(f"⏱️  Time: {time.time()-start:.0f}s")
print(f"📁 {os.path.abspath(OUT)}")
