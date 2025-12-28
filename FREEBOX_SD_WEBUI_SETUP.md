# 🎨 Installation Stable Diffusion WebUI sur Freebox

## ⚠️ STATUS ACTUEL

**Freebox SD WebUI** n'est actuellement **PAS accessible** à `http://88.174.155.230:33437`

Cela peut être dû à :
- Service non démarré sur la Freebox
- Port 33437 fermé/bloqué
- Installation manquante

---

## 📋 PRÉREQUIS

### Freebox
- Freebox Delta ou compatible (ARM64 ou x86_64)
- SSH activé (port 33000)
- Au moins 4GB RAM + 10GB espace disque
- Accès root

### Logiciels Nécessaires
- Python 3.8+
- Git
- CUDA (si GPU Nvidia) ou CPU fallback

---

## 🚀 INSTALLATION AUTOMATIQUE (Recommandé)

### Étape 1: Connexion SSH

```bash
ssh -p 33000 root@88.174.155.230
```

### Étape 2: Installation des Dépendances

```bash
# Mise à jour système
apt update && apt upgrade -y

# Python 3.10+ et dépendances
apt install -y python3 python3-pip python3-venv git wget

# Bibliothèques système
apt install -y libgl1-mesa-glx libglib2.0-0
```

### Étape 3: Télécharger Stable Diffusion WebUI

```bash
cd /root
git clone https://github.com/AUTOMATIC1111/stable-diffusion-webui.git
cd stable-diffusion-webui
```

### Étape 4: Configuration pour Freebox

Créer `webui-user.sh` :

```bash
cat > webui-user.sh << 'EOF'
#!/bin/bash

# Configuration Freebox (CPU ARM/x86)
export COMMANDLINE_ARGS="--listen --port 33437 --skip-torch-cuda-test --no-half --precision full --medvram --opt-sub-quad-attention"

# Si très peu de RAM (< 2GB)
# export COMMANDLINE_ARGS="$COMMANDLINE_ARGS --lowvram --always-batch-cond-uncond"

EOF

chmod +x webui-user.sh
```

### Étape 5: Télécharger un Modèle Léger

```bash
# Créer dossier modèles
mkdir -p models/Stable-diffusion

# Télécharger Realistic Vision V5.1 (4GB - bon compromis)
cd models/Stable-diffusion
wget https://huggingface.co/SG161222/Realistic_Vision_V5.1_noVAE/resolve/main/Realistic_Vision_V5.1.safetensors

# Alternative plus légère: SD 1.5 (2GB)
# wget https://huggingface.co/runwayml/stable-diffusion-v1-5/resolve/main/v1-5-pruned-emaonly.safetensors

cd ../..
```

### Étape 6: Lancer le WebUI

```bash
./webui.sh
```

**Première exécution** : Téléchargement de ~4GB de dépendances (10-30 min)

### Étape 7: Ouvrir le Port 33437

```bash
# Vérifier si le service est actif
netstat -tulpn | grep 33437

# Autoriser port dans firewall (si nécessaire)
iptables -A INPUT -p tcp --dport 33437 -j ACCEPT
```

### Étape 8: Créer Service Systemd (Démarrage Auto)

```bash
cat > /etc/systemd/system/sd-webui.service << 'EOF'
[Unit]
Description=Stable Diffusion WebUI
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/root/stable-diffusion-webui
ExecStart=/root/stable-diffusion-webui/webui.sh
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# Activer et démarrer
systemctl daemon-reload
systemctl enable sd-webui
systemctl start sd-webui

# Vérifier status
systemctl status sd-webui
```

---

## 🧪 TESTER L'INSTALLATION

### Test 1: Accès Local (depuis Freebox)

```bash
curl http://localhost:33437
```

### Test 2: Accès Externe (depuis votre PC)

```bash
curl http://88.174.155.230:33437
```

### Test 3: Génération d'Image

```bash
curl -X POST http://88.174.155.230:33437/sdapi/v1/txt2img \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "a beautiful landscape, mountains, sunset, 8k",
    "negative_prompt": "blurry, low quality",
    "steps": 20,
    "width": 512,
    "height": 512,
    "cfg_scale": 7
  }' | jq '.images[0]' -r | base64 -d > test.png
```

### Test 4: Via Python

```bash
python3 /workspace/generate_nsfw_freebox_v4.py
```

---

## ⚙️ CONFIGURATION OPTIMALE

### Pour Freebox avec 2GB RAM

Dans `webui-user.sh` :

```bash
export COMMANDLINE_ARGS="--listen --port 33437 --skip-torch-cuda-test --no-half --precision full --lowvram --medvram --opt-sub-quad-attention --xformers"
```

### Pour Freebox avec 4GB+ RAM

```bash
export COMMANDLINE_ARGS="--listen --port 33437 --skip-torch-cuda-test --no-half --precision full --medvram --opt-sub-quad-attention"
```

### Modèles Recommandés par Taille

| Modèle | Taille | RAM Min | Qualité | NSFW |
|--------|--------|---------|---------|------|
| SD 1.5 | 2GB | 4GB | Bonne | ✅ |
| Realistic Vision V5.1 | 4GB | 6GB | Excellente | ✅ |
| DreamShaper | 2GB | 4GB | Très bonne | ✅ |
| Anything V5 (Anime) | 2GB | 4GB | Excellente | ✅ |

---

## 🐛 DÉPANNAGE

### Erreur: "Connection refused"

```bash
# Vérifier service actif
systemctl status sd-webui

# Redémarrer
systemctl restart sd-webui

# Voir logs
journalctl -u sd-webui -f
```

### Erreur: "Out of Memory"

Réduire taille images ou ajouter swap :

```bash
# Ajouter 4GB swap
fallocate -l 4G /swapfile3
chmod 600 /swapfile3
mkswap /swapfile3
swapon /swapfile3
echo '/swapfile3 none swap sw 0 0' >> /etc/fstab
```

### Génération Très Lente

**Normal sur CPU** : 30-120s par image

Pour accélérer :
- Réduire steps (20 → 15)
- Réduire résolution (768 → 512)
- Utiliser modèle plus léger

### Port 33437 Bloqué

```bash
# Changer port dans webui-user.sh
export COMMANDLINE_ARGS="--listen --port 8080 ..."

# Puis adapter app Android et scripts Python
```

---

## 📱 INTÉGRATION APP ANDROID

Une fois WebUI accessible, **l'app v2.14.0** utilisera automatiquement :

1. **Freebox** en priorité (génération locale, gratuit, illimité)
2. **Pollination AI** en fallback (si Freebox inaccessible)

### Avantages Freebox

- ✅ **Gratuit et illimité**
- ✅ **Génération locale** (pas de rate limits)
- ✅ **Privacy totale** (aucune donnée envoyée en ligne)
- ✅ **NSFW sans censure** (aucune restriction)
- ✅ **Contrôle complet** (modèles, steps, CFG)

### Inconvénients

- ⚠️ **Plus lent** : 30-120s vs 2-5s (Pollination AI)
- ⚠️ **Configuration requise** (installation manuelle)
- ⚠️ **Dépend de Freebox** (doit être allumée 24/7)

---

## ✅ VÉRIFICATION FINALE

Quand tout fonctionne, vous devriez voir :

```bash
# Service actif
$ systemctl status sd-webui
● sd-webui.service - Stable Diffusion WebUI
   Active: active (running)

# Port ouvert
$ netstat -tulpn | grep 33437
tcp  0.0.0.0:33437  LISTEN  12345/python3

# API accessible
$ curl http://88.174.155.230:33437
<html>...</html>
```

---

## 🚀 LANCER GÉNÉRATION MASSIVE

Une fois WebUI fonctionnel :

```bash
cd /workspace
python3 generate_nsfw_freebox_v4.py
```

**Durée estimée** : ~2-3 heures pour 195 images (30-60s par image)

---

## 📞 SUPPORT

Si problèmes persistent :

1. Vérifier logs : `journalctl -u sd-webui -n 100`
2. Tester localement : `curl http://localhost:33437`
3. Vérifier RAM : `free -h`
4. Vérifier espace : `df -h`

**Commandes utiles** :

```bash
# Redémarrer service
systemctl restart sd-webui

# Voir logs en temps réel
journalctl -u sd-webui -f

# Arrêter service
systemctl stop sd-webui

# Désactiver démarrage auto
systemctl disable sd-webui
```

---

**Date** : 28 Décembre 2025  
**Version App** : v2.14.0  
**Status Freebox** : ⏳ Installation nécessaire
