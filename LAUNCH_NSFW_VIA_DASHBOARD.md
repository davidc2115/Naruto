# 🎨 Lancer génération NSFW via Dashboard Freebox

Le port SSH n'est pas accessible depuis Internet, mais tu peux lancer la génération via le **dashboard web** !

## 📋 Méthode via Dashboard Web

### 1️⃣ Accéder au dashboard

Ouvre ton navigateur : http://88.174.155.230:33002

**Credentials** :
- Username: `bagbot`
- Password: `bagbot`

### 2️⃣ Vérifier ComfyUI

Dans le terminal du dashboard, vérifie si ComfyUI tourne :

```bash
curl http://127.0.0.1:33437/
```

Si **pas de réponse**, démarre ComfyUI :

```bash
cd /root/ComfyUI
nohup python main.py --listen 0.0.0.0 --port 33437 > comfyui.log 2>&1 &
```

### 3️⃣ Copier le script

**Option A : Créer le fichier directement**

Dans le terminal du dashboard :

```bash
cat > /root/freebox_nsfw_generator_local.py << 'EOF'
[copier tout le contenu du fichier depuis /workspace/freebox_nsfw_generator_local.py]
EOF

chmod +x /root/freebox_nsfw_generator_local.py
```

**Option B : Via upload web (si disponible)**

Upload le fichier `/workspace/freebox_nsfw_generator_local.py` via l'interface web vers `/root/`

### 4️⃣ Lancer la génération

```bash
cd /root
nohup python3 freebox_nsfw_generator_local.py > nsfw_generation.log 2>&1 &
```

### 5️⃣ Suivre la progression

```bash
tail -f /root/nsfw_generation.log
```

Ou :

```bash
# Compter les images générées
ls /root/naruto_nsfw_gallery/*.png 2>/dev/null | wc -l
```

### 6️⃣ Récupérer les images (après génération)

**Via SCP (depuis ton PC en réseau local)** :

```bash
# Si tu es sur le même réseau que la Freebox
scp root@192.168.X.X:/root/naruto_nsfw_gallery/*.png /workspace/character_images/

# Ou si tu as configuré un VPN/tunnel
scp root@88.174.155.230:/root/naruto_nsfw_gallery/*.png /workspace/character_images/
```

**Via dashboard web** :

1. Compresser les images sur la Freebox :
```bash
cd /root
tar -czf nsfw_gallery.tar.gz naruto_nsfw_gallery/
```

2. Télécharger `nsfw_gallery.tar.gz` via l'interface web

3. Extraire sur ton PC :
```bash
tar -xzf nsfw_gallery.tar.gz -C /workspace/character_images/
```

### 7️⃣ Intégrer dans l'APK

```bash
# Copier dans drawable-nodpi
cp /workspace/character_images/*nsfw*.png /workspace/app/src/main/res/drawable-nodpi/

# Rebuild APK
cd /workspace
./gradlew assembleRelease

# Créer nouveau release
gh release create v2.33.0 \
  app/build/outputs/apk/release/app-release.apk \
  --title "v2.33.0 - Galeries NSFW complètes" \
  --notes "Ajout de 39 images NSFW (13 personnages × 3)"
```

---

## ⏱️ Temps estimé

- **Génération** : 30-45 minutes (39 images @ 1-2 min/image)
- **Download** : 1-2 minutes (dépend de la connexion)
- **Build APK** : 1-2 minutes
- **Total** : ~35-50 minutes

---

## 🔍 Troubleshooting

### Dashboard non accessible

- Vérifie que ta Freebox est allumée
- Vérifie l'IP : `88.174.155.230`
- Essaye de ping : `ping 88.174.155.230`

### ComfyUI ne démarre pas

```bash
# Voir les logs
tail -100 /root/ComfyUI/comfyui.log

# Redémarrer
pkill -f "python.*main.py"
cd /root/ComfyUI
python main.py --listen 0.0.0.0 --port 33437 &
```

### Script bloqué

```bash
# Vérifier si le script tourne
ps aux | grep freebox_nsfw_generator

# Killer si nécessaire
pkill -f freebox_nsfw_generator

# Voir les logs
tail -50 /root/nsfw_generation.log

# Relancer
python3 /root/freebox_nsfw_generator_local.py &
```

---

## 📊 Résultat attendu

```
/root/naruto_nsfw_gallery/
├── narutoNSFW1.png
├── narutoNSFW2.png
├── narutoNSFW3.png
├── sakuraNSFW1.png
├── sakuraNSFW2.png
├── sakuraNSFW3.png
├── hinataNSFW1.png
├── ... (total 39 fichiers)
```

---

**Bonne génération ! 🎨**
