# 🚀 Lancer génération NSFW sur Freebox

SSH ouvert sur **port 33000** ✅

## 📋 Méthode 1 : One-liner (RECOMMANDÉ)

### Étape 1 : Connecte-toi à ta Freebox

```bash
ssh -p 33000 root@88.174.155.230
```

### Étape 2 : Copie-colle cette commande

```bash
cd /tmp && curl -sL https://raw.githubusercontent.com/mel805/naruto-ai-chat/main/freebox_nsfw_one_command.sh | bash
```

**OU** si pas de `curl` :

```bash
cd /tmp && python3 << 'EOF'
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
        print(f"[{iid}] Start...")
        seed = int(time.time() * 1000 + hash(iid)) % 2147483647
        r = requests.post(f"{URL}/prompt", json={"prompt": wf(prompt, seed), "client_id": str(uuid4())}, timeout=30)
        pid = r.json()["prompt_id"]
        print(f"[{iid}] Submitted: {pid}")
        start = time.time()
        for _ in range(60):
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

print("🚀 Génération NSFW (39 images)...")
start_global = time.time()
success = 0
for i, (iid, prompt) in enumerate(CHARS.items(), 1):
    print(f"\nProgress: {i}/{len(CHARS)}")
    if gen(iid, prompt):
        success += 1
    if i < len(CHARS):
        time.sleep(5)
elapsed = time.time() - start_global
print(f"\n✅ Success: {success}/{len(CHARS)}")
print(f"⏱️ Time: {elapsed/60:.1f} min")
print(f"📁 {OUT}")
EOF
```

### ⏱️ Durée estimée

- **39 images** × **~3 min/image** = **~2 heures**

---

## 📋 Méthode 2 : Avec script

### Étape 1 : Télécharge le script

```bash
# Sur ton PC
scp -P 33000 freebox_nsfw_one_command.sh root@88.174.155.230:/tmp/
```

### Étape 2 : Connecte + lance

```bash
ssh -p 33000 root@88.174.155.230
cd /tmp
chmod +x freebox_nsfw_one_command.sh
./freebox_nsfw_one_command.sh
```

---

## 📥 Récupérer les images

### Une fois terminé

```bash
# Depuis ta Freebox
cd /tmp
tar -czf nsfw_gallery.tar.gz nsfw_gallery/

# Télécharge sur ton PC
exit
scp -P 33000 root@88.174.155.230:/tmp/nsfw_gallery.tar.gz .

# Extrais
tar -xzf nsfw_gallery.tar.gz
```

### Images individuelles

```bash
scp -P 33000 root@88.174.155.230:/tmp/nsfw_gallery/*.png ./character_images_nsfw/
```

---

## 🔍 Surveiller progression

```bash
# Dans un autre terminal
ssh -p 33000 root@88.174.155.230
watch -n 5 "ls -lh /tmp/nsfw_gallery/ | tail -10"
```

---

## ❌ En cas d'erreur

### ComfyUI pas démarré

```bash
ssh -p 33000 root@88.174.155.230
curl http://127.0.0.1:33437/system_stats

# Si erreur, démarre ComfyUI
cd /path/to/ComfyUI
python3 main.py --listen 0.0.0.0 --port 33437
```

### Python requests manquant

```bash
pip3 install requests
# ou
python3 -m pip install requests
```

---

## ✅ Succès attendu

```
Progress: 39/39
✅ Success: 39/39
⏱️ Time: 117.3 min (3.0 min/img)
📁 /tmp/nsfw_gallery
```

---

## 📞 Si problème

Envoie-moi :

1. **Logs** : Copie-colle sortie terminal
2. **ComfyUI status** : `curl http://127.0.0.1:33437/system_stats`
3. **Images générées** : `ls -lh /tmp/nsfw_gallery/`

---

🚀 **Lance maintenant la commande !**
