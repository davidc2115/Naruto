# 🔧 CONFIGURATION FREEBOX STABLE DIFFUSION

## ⚠️ ÉTAT ACTUEL

**Port 33437**: ❌ INACCESSIBLE (Connection refused)

Le service Stable Diffusion WebUI n'est **pas démarré** sur votre Freebox.

## 📋 POUR ACTIVER STABLE DIFFUSION SUR FREEBOX

### 1. Se connecter à la Freebox

```bash
ssh freebox@88.174.155.230
# ou via interface web
```

### 2. Vérifier si Stable Diffusion est installé

```bash
# Chercher l'installation
find ~ -name "webui.sh" -o -name "launch.py" 2>/dev/null
find /opt -name "stable-diffusion*" 2>/dev/null
```

### 3. Démarrer Stable Diffusion WebUI

**Si installé** :
```bash
cd ~/stable-diffusion-webui   # ou chemin d'installation
./webui.sh --listen --port 33437 --api --xformers
```

**Si NON installé**, installer d'abord :
```bash
# Installation automatique
wget -q https://raw.githubusercontent.com/AUTOMATIC1111/stable-diffusion-webui/master/webui.sh
chmod +x webui.sh
./webui.sh --listen --port 33437 --api
```

### 4. Ouvrir le port dans le firewall

```bash
# UFW
sudo ufw allow 33437/tcp

# iptables  
sudo iptables -A INPUT -p tcp --dport 33437 -j ACCEPT
sudo iptables-save
```

### 5. Vérifier que ça fonctionne

```bash
# Depuis la Freebox
curl http://localhost:33437/sdapi/v1/sd-models

# Depuis l'externe
curl http://88.174.155.230:33437/sdapi/v1/sd-models
```

## ✅ CONFIGURATION APP (Déjà faite)

L'app est **déjà configurée** pour utiliser Freebox en priorité :

```kotlin
// FreeboxMediaClient.kt - ligne 29
private const val FREEBOX_BASE_URL = "http://88.174.155.230:33437"

// ChatViewModel.kt - ligne 235
val pingResult = freeboxMediaClient.ping()
val usePollination = pingResult.isFailure

// Si Freebox OK → Utilise Freebox (local, rapide)
// Si Freebox KO → Fallback Pollination AI
```

**Timeout ping** : 5 secondes (rapide)

## 🎯 AVANTAGES FREEBOX

Une fois activé :
- ✅ **Gratuit** : pas de rate limits
- ✅ **Rapide** : génération locale
- ✅ **Qualité** : contrôle total sur modèles
- ✅ **Privé** : images ne transitent pas par Internet

## 📝 MODÈLES RECOMMANDÉS

Pour images **hyper-réalistes** :
- `realisticVisionV51_v51VAE.safetensors`
- `deliberate_v2.safetensors`
- `epicrealism_naturalSinRC1VAE.safetensors`

Télécharger depuis :
- https://civitai.com/models/4201/realistic-vision-v51
- https://huggingface.co/models

Placer dans : `~/stable-diffusion-webui/models/Stable-diffusion/`

## 🚀 DÉMARRAGE AUTOMATIQUE (Optionnel)

Créer service systemd :

```bash
sudo nano /etc/systemd/system/sd-webui.service
```

```ini
[Unit]
Description=Stable Diffusion WebUI
After=network.target

[Service]
Type=simple
User=freebox
WorkingDirectory=/home/freebox/stable-diffusion-webui
ExecStart=/home/freebox/stable-diffusion-webui/webui.sh --listen --port 33437 --api --xformers
Restart=always

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable sd-webui
sudo systemctl start sd-webui
```

## ⚙️ CONFIGURATION OPTIMALE

**Fichier** : `~/stable-diffusion-webui/webui-user.sh`

```bash
export COMMANDLINE_ARGS="--listen --port 33437 --api --xformers --medvram --opt-split-attention"
```

**Explications** :
- `--listen` : Écoute sur toutes interfaces (0.0.0.0)
- `--port 33437` : Port personnalisé
- `--api` : Active l'API REST
- `--xformers` : Accélération (si carte NVIDIA)
- `--medvram` : Pour GPU avec 4-8 GB VRAM
- `--lowvram` : Pour GPU avec <4 GB VRAM

## 🧪 TEST APRÈS ACTIVATION

```bash
# Test simple
curl http://88.174.155.230:33437/sdapi/v1/sd-models

# Test génération
curl -X POST http://88.174.155.230:33437/sdapi/v1/txt2img \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "beautiful woman portrait, photorealistic, 8k",
    "negative_prompt": "blurry, low quality",
    "steps": 20,
    "width": 512,
    "height": 768,
    "cfg_scale": 7,
    "sampler_name": "DPM++ 2M Karras"
  }'
```

Si ça retourne du JSON avec `"images": [...]` → **✅ PRÊT !**

---

**L'app basculera AUTOMATIQUEMENT sur Freebox** dès que le service sera actif.

Aucun changement de code nécessaire ! 🎉
