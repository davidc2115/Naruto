# 🎨 Configuration Freebox Media Server - Images & Vidéos Locales

**Générez des images et vidéos directement sur votre Freebox avec Stable Diffusion!**

---

## 🎯 Pourquoi Freebox pour les Médias?

**Avantages:**
- ✅ **100% Gratuit** - Aucun coût d'API
- ✅ **100% Illimité** - Pas de limite de génération
- ✅ **100% Local** - Vos images restent chez vous
- ✅ **Contrôle Total** - Choix des modèles et paramètres
- ✅ **Aucune Censure** - Modèles uncensored disponibles

**vs Replicate:**
- ❌ Payant après $5 gratuits
- ❌ Limites de requêtes
- ❌ Données envoyées au cloud
- ❌ Dépendance externe

---

## 📋 Prérequis

### Matériel Freebox

**Minimum:**
- Freebox Delta ou Ultra (ARM 64-bit)
- 4 GB RAM minimum (8 GB recommandé)
- 20 GB espace disque libre
- Connexion Internet stable

**Recommandé:**
- 8 GB RAM ou plus
- 50 GB espace disque
- Swap configuré (voir SWAP_10GB.md)

---

## 🚀 Installation Stable Diffusion WebUI

### Étape 1: Connexion SSH à la Freebox

```bash
ssh -p 33000 root@88.174.155.230
# Mot de passe: (votre mot de passe Freebox)
```

### Étape 2: Installer les Dépendances

```bash
# Mettre à jour le système
apt update && apt upgrade -y

# Installer Python 3.10+
apt install python3 python3-pip python3-venv -y

# Installer Git
apt install git -y

# Installer les dépendances pour compilation
apt install build-essential libssl-dev libffi-dev python3-dev -y

# Installer wget et autres outils
apt install wget curl unzip -y
```

### Étape 3: Cloner Stable Diffusion WebUI

```bash
# Aller dans le répertoire home
cd /root

# Cloner le repo (version légère pour ARM)
git clone https://github.com/AUTOMATIC1111/stable-diffusion-webui.git
cd stable-diffusion-webui
```

### Étape 4: Configuration pour ARM/Freebox

Créer un fichier `webui-user.sh`:

```bash
cat > webui-user.sh << 'EOF'
#!/bin/bash

# Configuration pour Freebox ARM
export COMMANDLINE_ARGS="--listen --port 7860 --api --xformers --no-half --precision full --skip-torch-cuda-test --use-cpu all"
export PYTORCH_CUDA_ALLOC_CONF="max_split_size_mb:512"

# Limiter l'utilisation mémoire
export PYTORCH_MPS_HIGH_WATERMARK_RATIO=0.0

EOF

chmod +x webui-user.sh
```

### Étape 5: Installer le WebUI

```bash
# Lancer l'installation (peut prendre 30-60 min)
./webui.sh --skip-torch-cuda-test --use-cpu all

# Si erreur, relancer avec:
./webui.sh --reinstall-xformers --skip-torch-cuda-test
```

### Étape 6: Télécharger un Modèle

```bash
# Aller dans le dossier models
cd models/Stable-diffusion

# Télécharger Realistic Vision (modèle léger et rapide)
wget https://huggingface.co/SG161222/Realistic_Vision_V5.1_noVAE/resolve/main/realisticVisionV51_v51VAE.safetensors

# Alternative: Modèle anime
# wget https://huggingface.co/andite/anything-v4.0/resolve/main/anything-v4.5-pruned.safetensors

# Retour au dossier principal
cd ../..
```

### Étape 7: Configurer le Service Systemd

Créer un service pour démarrage automatique:

```bash
cat > /etc/systemd/system/stable-diffusion.service << 'EOF'
[Unit]
Description=Stable Diffusion WebUI
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/root/stable-diffusion-webui
ExecStart=/root/stable-diffusion-webui/webui.sh --listen --api --port 7860
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# Activer et démarrer le service
systemctl daemon-reload
systemctl enable stable-diffusion
systemctl start stable-diffusion
```

### Étape 8: Vérifier l'Installation

```bash
# Vérifier le status
systemctl status stable-diffusion

# Vérifier les logs
journalctl -u stable-diffusion -f

# Tester l'API
curl http://localhost:7860/sdapi/v1/sd-models
```

### Étape 9: Ouvrir le Port

```bash
# Vérifier que le port 7860 est accessible
netstat -tulpn | grep 7860

# Si nécessaire, ouvrir dans le firewall
ufw allow 7860/tcp

# Test depuis l'extérieur
curl http://88.174.155.230:7860/sdapi/v1/sd-models
```

---

## 🎨 Configuration de l'App Android

L'app est déjà configurée pour utiliser votre Freebox!

**URL par défaut:** `http://88.174.155.230:7860`

**Aucune configuration nécessaire** - L'app utilisera automatiquement:
- `FreeboxMediaClient` pour images
- `FreeboxMediaClient` pour vidéos (frames animées)

---

## 📊 Capacités et Performance

### Images

**Format:** 512x768 (portrait) ou 768x512 (paysage)
**Temps de génération:** 30-90 secondes par image
**Qualité:** Haute (dépend du modèle)
**Styles disponibles:**
- Realistic (photorealistic)
- Anime (manga style)
- Artistic (peinture)
- Cinematic (film quality)

### Vidéos

**Format:** Séquence de frames (pseudo-vidéo)
**Durée:** 1-4 secondes
**FPS:** 6-8 images/seconde
**Temps de génération:** 2-5 minutes

**Note:** Pour de vraies vidéos, installer AnimateDiff (voir section avancée)

---

## 🔧 Modèles Recommandés

### Pour Portraits Réalistes

**Realistic Vision V5.1** (déjà installé)
- Excellent pour portraits
- Bonne gestion des visages
- Rapide sur ARM

**Download:**
```bash
cd /root/stable-diffusion-webui/models/Stable-diffusion
wget https://huggingface.co/SG161222/Realistic_Vision_V5.1_noVAE/resolve/main/realisticVisionV51_v51VAE.safetensors
```

### Pour Style Anime

**Anything V4.5**
- Style manga/anime
- Bon pour personnages Naruto
- Léger et rapide

**Download:**
```bash
cd /root/stable-diffusion-webui/models/Stable-diffusion
wget https://huggingface.co/andite/anything-v4.0/resolve/main/anything-v4.5-pruned.safetensors
```

### Pour Portraits Cinématiques

**DreamShaper**
- Style cinématique
- Bon pour célébrités
- Qualité élevée

**Download:**
```bash
cd /root/stable-diffusion-webui/models/Stable-diffusion
wget https://huggingface.co/Lykon/DreamShaper/resolve/main/DreamShaper_8_pruned.safetensors
```

---

## 🎬 Installation AnimateDiff (Vidéos)

Pour de vraies vidéos animées:

### Étape 1: Installer Extension

```bash
cd /root/stable-diffusion-webui/extensions
git clone https://github.com/continue-revolution/sd-webui-animatediff.git
cd sd-webui-animatediff
pip install -r requirements.txt
```

### Étape 2: Télécharger Modèle Motion

```bash
cd /root/stable-diffusion-webui/extensions/sd-webui-animatediff/model
wget https://huggingface.co/guoyww/animatediff/resolve/main/mm_sd_v15_v2.ckpt
```

### Étape 3: Redémarrer le Service

```bash
systemctl restart stable-diffusion
```

---

## 🔍 Monitoring et Maintenance

### Vérifier l'Utilisation

**RAM:**
```bash
free -h
```

**CPU:**
```bash
top -b -n 1 | grep python
```

**Disque:**
```bash
df -h
du -sh /root/stable-diffusion-webui
```

### Logs

**Voir les logs en temps réel:**
```bash
journalctl -u stable-diffusion -f
```

**Dernières 100 lignes:**
```bash
journalctl -u stable-diffusion -n 100
```

### Redémarrer le Service

**Si problème ou lenteur:**
```bash
systemctl restart stable-diffusion
```

**Arrêter:**
```bash
systemctl stop stable-diffusion
```

**Démarrer:**
```bash
systemctl start stable-diffusion
```

---

## 🐛 Dépannage

### Erreur "Out of Memory"

**Solution:**
1. Ajouter plus de swap (voir SWAP_10GB.md)
2. Réduire la résolution des images
3. Utiliser modèle plus léger

```bash
# Augmenter swap temporairement
fallocate -l 4G /swapfile3
chmod 600 /swapfile3
mkswap /swapfile3
swapon /swapfile3
```

### Service ne Démarre Pas

**Vérifier:**
```bash
systemctl status stable-diffusion
journalctl -u stable-diffusion -n 50
```

**Solution:**
```bash
cd /root/stable-diffusion-webui
./webui.sh --reinstall-torch --skip-torch-cuda-test
systemctl restart stable-diffusion
```

### Génération Très Lente

**Normal sur ARM!** 30-90 secondes par image est attendu.

**Optimisations:**
- Réduire steps (30 → 20)
- Désactiver hi-res fix
- Utiliser modèle plus léger

### Port 7860 Inaccessible

**Vérifier:**
```bash
netstat -tulpn | grep 7860
curl http://localhost:7860/sdapi/v1/sd-models
```

**Solution:**
```bash
# Ouvrir le port
ufw allow 7860/tcp

# Redémarrer service
systemctl restart stable-diffusion
```

---

## 📱 Utilisation dans l'App

### Génération d'Images

1. **Pendant une conversation**, cliquer sur l'icône 📸
2. Sélectionner **"Générer Image"**
3. L'app utilise automatiquement Freebox Stable Diffusion
4. **Attendre 30-90 secondes**
5. Image apparaît dans le chat

### Génération de Vidéos

1. **Pendant une conversation**, cliquer sur l'icône 📸
2. Sélectionner **"Générer Vidéo"**
3. L'app utilise Freebox (frames multiples)
4. **Attendre 2-5 minutes**
5. Vidéo/animation apparaît

### Galerie de Personnages

Pour les vignettes et galeries, l'app utilise **Pollination AI** (gratuit, rapide):
- Génération de vignettes automatique
- Galerie de 6 images par personnage
- Pas besoin de configuration

**Pourquoi Pollination pour vignettes?**
- ⚡ Plus rapide (5-10 sec vs 30-90 sec)
- ☁️ Ne charge pas la Freebox
- 🌐 API gratuite et illimitée
- 📱 Optimisé pour petites images

---

## 🆚 Comparaison des Systèmes

| Feature | Freebox SD | Pollination AI | Replicate |
|---------|------------|----------------|-----------|
| **Coût** | 0€ | 0€ | $0.002/image |
| **Limite** | ∞ | ∞ | $5 gratuit puis payant |
| **Vitesse** | 30-90s | 5-10s | 10-30s |
| **Qualité** | Haute | Moyenne-Haute | Très Haute |
| **Contrôle** | Total | Limité | Moyen |
| **Local** | ✅ Oui | ❌ Cloud | ❌ Cloud |
| **Setup** | 60 min | 0 min | 5 min |

**Stratégie Optimale:**
- **Pollination AI** → Vignettes et galeries (rapide, gratuit)
- **Freebox SD** → Images dans conversations (qualité, local, illimité)
- **Freebox SD** → Vidéos (seule option locale)

---

## 💡 Prompts Efficaces

### Pour Portraits

```
portrait of [NAME], [DESCRIPTION],
photorealistic, detailed face, expressive eyes,
professional lighting, 8k uhd, sharp focus,
masterpiece, best quality
```

### Pour Scènes

```
[SCENE DESCRIPTION],
cinematic lighting, detailed environment,
atmospheric, professional, masterpiece
```

### Negative Prompt Standard

```
(worst quality, low quality:1.4),
bad anatomy, bad hands, blurry, ugly,
watermark, signature, text
```

---

## 🎓 Ressources

**Documentation Stable Diffusion WebUI:**
- GitHub: https://github.com/AUTOMATIC1111/stable-diffusion-webui
- Wiki: https://github.com/AUTOMATIC1111/stable-diffusion-webui/wiki

**Modèles:**
- Civitai: https://civitai.com
- HuggingFace: https://huggingface.co/models?other=stable-diffusion

**Extensions:**
- ControlNet: Contrôle avancé des poses
- AnimateDiff: Vraies vidéos animées
- Deforum: Vidéos complexes

---

## ✅ Checklist Installation

- [ ] SSH connexion établie
- [ ] Python 3.10+ installé
- [ ] Stable Diffusion WebUI cloné
- [ ] Modèle Realistic Vision téléchargé
- [ ] Service systemd configuré
- [ ] Service démarré et actif
- [ ] API accessible sur port 7860
- [ ] Test génération réussi
- [ ] App Android configurée
- [ ] Première image générée ✅

---

## 🎉 Conclusion

**Vous avez maintenant:**
- ✅ Serveur Stable Diffusion sur Freebox
- ✅ Génération d'images illimitée et gratuite
- ✅ Génération de vidéos (frames)
- ✅ Contrôle total des modèles
- ✅ Privacy totale (100% local)
- ✅ Intégration automatique dans l'app

**Plus de frais Replicate!** 🚀

---

**Version:** 1.0  
**Date:** 26 Décembre 2025  
**Système:** Freebox Delta/Ultra + Stable Diffusion WebUI  
**Status:** ✅ Production Ready
