#!/usr/bin/env python3
"""Regénérer sakura_3 et hinata_3"""
import os, time, requests

OUT = "character_images_nsfw"

CHARS = {
    "sakura_3": "Sakura anime pink hair girl NSFW nude explicit",
    "hinata_3": "Hinata anime dark hair girl NSFW nude explicit"
}

def gen(iid, prompt):
    try:
        print(f"[{iid}] Generating...", flush=True)
        url = f"https://image.pollinations.ai/prompt/{requests.utils.quote(prompt)}"
        params = {"width": 512, "height": 768, "nologo": "true"}
        
        for attempt in range(3):
            print(f"  Attempt {attempt+1}/3...", end=" ", flush=True)
            r = requests.get(url, params=params, timeout=60)
            
            if r.status_code == 200 and len(r.content) > 10000:
                fp = os.path.join(OUT, f"{iid}.png")
                with open(fp, 'wb') as f:
                    f.write(r.content)
                print(f"✅ ({len(r.content)//1024}KB)")
                return True
            
            print("❌")
            time.sleep(10)
        
        return False
    except Exception as e:
        print(f"❌ {e}")
        return False

print("🔄 Regénération sakura_3 + hinata_3...\n")

for iid, prompt in CHARS.items():
    gen(iid, prompt)
    time.sleep(5)

print("\n✅ Done!")
