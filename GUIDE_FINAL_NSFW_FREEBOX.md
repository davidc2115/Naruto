# 🎨 Guide FINAL : Génération NSFW sur Freebox

## ⚠️ Pourquoi pas depuis Internet ?

ComfyUI sur ta Freebox est **accessible** mais la génération est **TRÈS lente** :
- ✅ ComfyUI répond (http://88.174.155.230:33437)
- ✅ API `/prompt` fonctionne
- ❌ Génération : **>10 minutes par image** (CPU ARM lent)
- ❌ 39 images = **~6-7 heures** depuis Internet avec timeouts

**Solution** : Tu dois le faire **localement** (même réseau que la Freebox).

---

## 🚀 Méthode 1 : SSH Local (RECOMMANDÉ)

### Prérequis
- Être sur le **même réseau** que ta Freebox (WiFi/Ethernet)
- Ou avoir un **VPN** configuré vers ton réseau local

### Étapes

#### 1️⃣ Trouver l'IP locale de la Freebox

```bash
# Depuis ton PC, essaye :
ping freebox.local

# Ou scan ton réseau :
nmap -sn 192.168.1.0/24 | grep -B 2 "Freebox"

# IP commune : 192.168.1.254 ou 192.168.1.1
```

#### 2️⃣ SSH sur la Freebox

```bash
ssh root@192.168.1.X  # Remplace X par l'IP trouvée
# Password: bagbot
```

#### 3️⃣ Vérifier ComfyUI

```bash
curl http://127.0.0.1:33437/
```

Si pas de réponse :
```bash
cd /root/ComfyUI
nohup python main.py --listen 0.0.0.0 --port 33437 > comfyui.log 2>&1 &
```

#### 4️⃣ Créer le script

```bash
cat > /root/generate_nsfw.py << 'EOF'
[Copier tout le contenu de /workspace/freebox_nsfw_generator_local.py]
EOF

chmod +x /root/generate_nsfw.py
```

#### 5️⃣ Lancer

```bash
cd /root
nohup python3 generate_nsfw.py > nsfw_gen.log 2>&1 &
```

#### 6️⃣ Suivre

```bash
# Logs
tail -f nsfw_gen.log

# Images générées
ls /root/naruto_nsfw_gallery/*.png | wc -l
```

#### 7️⃣ Récupérer (après ~30-45 min)

```bash
# Depuis ton PC (réseau local)
scp root@192.168.1.X:/root/naruto_nsfw_gallery/*.png /workspace/character_images/
```

---

## 🚀 Méthode 2 : Accès Web Freebox

### Si tu as une interface web admin Freebox

1. Accède à l'interface admin Freebox (généralement http://mafreebox.freebox.fr ou http://192.168.1.254)

2. Va dans **"Terminal"** ou **"SSH"**

3. Exécute les mêmes commandes que Méthode 1 (étapes 3-7)

---

## 🚀 Méthode 3 : Script depuis ton PC (réseau local)

### Si tu es sur le même réseau

```python
# Sur ton PC, exécute ce script Python :
import requests
import time
import os

COMFYUI_URL = "http://192.168.1.X:33437"  # IP locale Freebox
OUTPUT_DIR = "nsfw_images"

# [Même logique que freebox_nsfw_generator_local.py mais depuis ton PC]
# Génération sera plus rapide car réseau local
```

---

## 📊 Temps estimé par méthode

| Méthode | Connexion | Temps/image | Total (39) |
|---------|-----------|-------------|------------|
| Internet | ❌ Lente | 10-15 min | ~7h |
| SSH Local | ✅ Rapide | 1-2 min | **30-45 min** |
| Web Admin | ✅ Rapide | 1-2 min | **30-45 min** |
| PC Local | ✅ Rapide | 1-2 min | **30-45 min** |

---

## 🔧 Configuration Freebox pour SSH externe

### Si tu veux activer SSH depuis Internet

1. **Se connecter à l'interface Freebox** : http://mafreebox.freebox.fr

2. **Activer SSH** :
   - Paramètres → Réseau local → SSH
   - Cocher "Activer SSH"
   - Port : 22 (ou personnalisé)

3. **Redirection de port** :
   - Paramètres → Mode avancé → Redirections de ports
   - Ajouter :
     - Port externe : 2222 (ou autre)
     - Port interne : 22
     - IP destination : IP de la Freebox (192.168.1.X)
     - Protocole : TCP

4. **Test** :
```bash
ssh -p 2222 root@88.174.155.230
```

---

## ⚡ Alternative : Utiliser API Cloud

### Pollination AI (si tu veux skip Freebox)

```bash
cd /workspace
python3 generate_pollination_ultra_safe.py
```

**Avantages** :
- ✅ Rapide (30s-1min/image)
- ✅ Aucune config

**Inconvénients** :
- ⚠️ Rate limits (429 errors possibles)
- ⚠️ Qualité variable

---

## 📁 Après génération

### Une fois les 39 images téléchargées

```bash
# Sur ton PC/workspace
cd /workspace

# Copier dans drawable-nodpi
cp character_images/*nsfw*.png app/src/main/res/drawable-nodpi/

# Build APK
./gradlew assembleRelease

# Release GitHub
gh release create v2.33.0 \
  app/build/outputs/apk/release/app-release.apk \
  --title "v2.33.0 - Galeries NSFW complètes ✨" \
  --notes "Ajout de 39 images NSFW pour tous les personnages"
```

---

## ❓ FAQ

**Q : Pourquoi si lent depuis Internet ?**  
R : ComfyUI tourne sur CPU ARM (pas de GPU), chaque image prend 10-15 min.

**Q : Puis-je accélérer ?**  
R : Oui, réduis les steps à 4-5 dans le script (qualité moindre).

**Q : SSH local ne marche pas ?**  
R : Vérifie que tu es sur le même réseau WiFi/Ethernet que la Freebox.

**Q : Pas d'accès physique à la Freebox ?**  
R : Utilise Pollination AI (script alternatif fourni).

---

## 📞 Support

**Tu bloques ?** Dis-moi :
- Quelle méthode tu essayes
- L'erreur exacte
- Si tu as accès local à la Freebox

Je t'aiderai ! 🚀
