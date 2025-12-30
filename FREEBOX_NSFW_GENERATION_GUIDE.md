# 🎨 Guide : Génération Galeries NSFW sur Freebox

## 📋 Prérequis

- Freebox accessible en SSH : `88.174.155.230`
- ComfyUI installé et fonctionnel sur Freebox
- Connexion SSH configurée

## 🚀 Étapes de génération

### 1️⃣ Connexion SSH à la Freebox

```bash
ssh root@88.174.155.230
# Password: bagbot
```

### 2️⃣ Vérifier que ComfyUI tourne

```bash
# Vérifier si ComfyUI est actif
curl -s http://127.0.0.1:33437/ | head -5

# Si ComfyUI n'est pas actif, le démarrer:
cd /root/ComfyUI
python main.py --listen 0.0.0.0 --port 33437 &
```

### 3️⃣ Copier le script sur la Freebox

**Option A : Via SCP (depuis ta machine locale)**

```bash
# Depuis /workspace
scp /workspace/freebox_nsfw_generator_local.py root@88.174.155.230:/root/
```

**Option B : Créer directement sur la Freebox**

```bash
# Sur la Freebox (en SSH)
cat > /root/freebox_nsfw_generator_local.py << 'EOF'
[copier le contenu du fichier freebox_nsfw_generator_local.py]
EOF

chmod +x /root/freebox_nsfw_generator_local.py
```

### 4️⃣ Lancer la génération

```bash
# Sur la Freebox
cd /root
python3 freebox_nsfw_generator_local.py

# OU en arrière-plan avec logs:
nohup python3 freebox_nsfw_generator_local.py > nsfw_generation.log 2>&1 &

# Pour suivre la progression:
tail -f nsfw_generation.log
```

### 5️⃣ Vérifier la génération

```bash
# Sur la Freebox
ls -lh /root/naruto_nsfw_gallery/
# Devrait contenir 39 fichiers PNG
```

### 6️⃣ Récupérer les images

**Option A : SCP vers le projet Android (RECOMMANDÉ)**

```bash
# Depuis ta machine locale (dans /workspace)
scp root@88.174.155.230:/root/naruto_nsfw_gallery/*.png /workspace/character_images/

# Ou pour être sûr:
mkdir -p /workspace/character_images
scp root@88.174.155.230:/root/naruto_nsfw_gallery/*.png /workspace/character_images/
```

**Option B : Via rsync (plus rapide si plusieurs tentatives)**

```bash
rsync -avz --progress root@88.174.155.230:/root/naruto_nsfw_gallery/*.png /workspace/character_images/
```

### 7️⃣ Copier dans le projet Android

```bash
# Copier dans drawable-nodpi
cp /workspace/character_images/*nsfw*.png /workspace/app/src/main/res/drawable-nodpi/

# Vérifier
ls -lh /workspace/app/src/main/res/drawable-nodpi/*nsfw*.png
```

## ⏱️ Temps estimé

- **Par image** : ~30-60 secondes (sur ARM, local)
- **Par personnage** (3 images) : ~2-3 minutes
- **Total** (13 personnages, 39 images) : **~30-45 minutes**

## 🔍 Monitoring en temps réel

```bash
# Voir les logs en direct
tail -f /root/nsfw_generation.log

# Compter les images générées
ls /root/naruto_nsfw_gallery/*.png | wc -l

# Voir la taille totale
du -sh /root/naruto_nsfw_gallery/
```

## ❌ Troubleshooting

### ComfyUI ne répond pas

```bash
# Redémarrer ComfyUI
pkill -f "python.*main.py"
cd /root/ComfyUI
python main.py --listen 0.0.0.0 --port 33437 &
```

### Script bloqué

```bash
# Trouver le process
ps aux | grep freebox_nsfw_generator

# Killer si nécessaire
pkill -f freebox_nsfw_generator

# Relancer
python3 /root/freebox_nsfw_generator_local.py
```

### Images corrompues

```bash
# Vérifier les images
cd /root/naruto_nsfw_gallery
file *.png | grep -v "PNG image"

# Supprimer les corrompues et regénérer
rm [fichier_corrompu].png
```

## 📦 Après génération

Une fois les 39 images copiées dans `/workspace/character_images/`, lancer :

```bash
cd /workspace
# Copier dans drawable-nodpi
cp character_images/*nsfw*.png app/src/main/res/drawable-nodpi/

# Builder l'APK
./gradlew assembleRelease

# Ou créer un commit + release GitHub
git add .
git commit -m "v2.29.0 - Galeries NSFW complètes (39 images)"
git push
```

## 🎯 Résultat attendu

```
/root/naruto_nsfw_gallery/
├── narutoNSFW1.png
├── narutoNSFW2.png
├── narutoNSFW3.png
├── sakuraNSFW1.png
├── sakuraNSFW2.png
├── sakuraNSFW3.png
├── hinataNSFW1.png
├── hinataNSFW2.png
├── hinataNSFW3.png
└── ... (30 autres fichiers)
```

Total: **39 images PNG** (512x768, ~100-300KB chacune)
