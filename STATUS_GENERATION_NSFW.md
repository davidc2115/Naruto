# 🚀 Génération NSFW - Status

## ✅ **LANCÉE EN BACKGROUND SUR FREEBOX**

### 📊 Informations

```
SSH     : bagbot@88.174.155.230:33000
Process : PID 119725 (python3 gen_nsfw.py)
Logs    : /tmp/nsfw_gen.log
Output  : /tmp/nsfw_gallery/
Images  : 15 images (test réduit)
```

### ⏱️ Progression actuelle

```
Progress: 1/15
[naruto_1] Submitted: 14056481-0a17-40b7-919f-b75181eaa416
Status: En cours (>3 min pour première image)
Files: 0 (normal, première image pas encore terminée)
```

### 🐢 Performance CPU ARM

**Temps/image** : ~3-5 min (4 steps, 384x512)  
**Total estimé** : 15 images × 4 min = **~60 minutes**

---

## 🔍 Surveiller progression

### Méthode 1 : Logs en temps réel

```bash
ssh -p 33000 bagbot@88.174.155.230
# Password: bagbot

tail -f /tmp/nsfw_gen.log
```

Tu verras :
```
[naruto_1] ✅ Done 180s (512KB)
[naruto_2] Start...
[naruto_2] Submitted: xxx-xxx-xxx
...
```

### Méthode 2 : Compter fichiers

```bash
watch -n 10 "ssh -p 33000 bagbot@88.174.155.230 'ls -lh /tmp/nsfw_gallery/ | tail -20'"
```

### Méthode 3 : Script auto-check

```bash
while true; do
  COUNT=$(ssh -p 33000 bagbot@88.174.155.230 "ls /tmp/nsfw_gallery/*.png 2>/dev/null | wc -l")
  echo "$(date '+%H:%M:%S') - Images: $COUNT/15"
  if [ "$COUNT" -eq 15 ]; then
    echo "✅ TERMINÉ !"
    break
  fi
  sleep 30
done
```

---

## 📥 Récupérer images (quand terminé)

### Check si terminé

```bash
ssh -p 33000 bagbot@88.174.155.230 "tail -5 /tmp/nsfw_gen.log"
```

Doit afficher :
```
✅ Success: 15/15
⏱️ Time: 60.3 min
📁 /tmp/nsfw_gallery
```

### Télécharger toutes les images

```bash
# Archive
ssh -p 33000 bagbot@88.174.155.230 "cd /tmp && tar -czf nsfw_gallery.tar.gz nsfw_gallery/"

# Download
scp -P 33000 bagbot@88.174.155.230:/tmp/nsfw_gallery.tar.gz .

# Extract
tar -xzf nsfw_gallery.tar.gz
```

### Télécharger images individuelles

```bash
mkdir -p character_images_nsfw
scp -P 33000 bagbot@88.174.155.230:/tmp/nsfw_gallery/*.png ./character_images_nsfw/
```

---

## 🔧 Contrôle du processus

### Vérifier si actif

```bash
ssh -p 33000 bagbot@88.174.155.230 "ps aux | grep gen_nsfw.py | grep -v grep"
```

### Arrêter génération

```bash
ssh -p 33000 bagbot@88.174.155.230 "kill $(cat /tmp/nsfw_gen.pid)"
```

### Relancer si erreur

```bash
ssh -p 33000 bagbot@88.174.155.230 "cd /tmp && python3 gen_nsfw.py"
```

---

## 📊 Logs diagnostic

### Dernières 50 lignes

```bash
ssh -p 33000 bagbot@88.174.155.230 "tail -50 /tmp/nsfw_gen.log"
```

### Images générées

```bash
ssh -p 33000 bagbot@88.174.155.230 "ls -lh /tmp/nsfw_gallery/"
```

### Espace disque

```bash
ssh -p 33000 bagbot@88.174.155.230 "df -h /tmp"
```

---

## ⚠️ Si génération trop lente

### Option 1 : Réduire résolution

Modifie le script :

```python
"5": {"inputs": {"width": 256, "height": 256, ...  # Au lieu de 384x512
```

### Option 2 : Réduire steps

```python
"3": {"inputs": {"seed": s, "steps": 3, ...  # Au lieu de 4
```

### Option 3 : Générer par batch

Au lieu de 15 d'un coup, génère 5 par 5 :

```python
# Modifie CHARS pour ne garder que 5 persos
CHARS = {
    "naruto_1": "...",
    "sakura_1": "...",
    "hinata_1": "...",
    "sasuke_1": "...",
    "kakashi_1": "..."
}
```

Lance 3 fois le script avec des personnages différents.

---

## ✅ Une fois terminé

1. **Vérifie images** : `ls -lh /tmp/nsfw_gallery/`
2. **Télécharge** : `scp -P 33000 bagbot@88.174.155.230:/tmp/nsfw_gallery.tar.gz .`
3. **Intègre dans APK** : Copie images dans `/workspace/character_images/`
4. **Test** : Vérifie que galeries s'affichent dans l'app

---

## 📞 Statut actuel

| Item | Status |
|------|--------|
| SSH connexion | ✅ OK (port 33000) |
| ComfyUI | ✅ Actif (v0.6.0) |
| Script lancé | ✅ PID 119725 |
| Images générées | ⏳ 0/15 (première en cours) |
| Temps écoulé | ~3 min |
| ETA | ~60 min total |

---

**🎯 Action** : Attends ~60 minutes, puis check `/tmp/nsfw_gallery/` !

**📱 Pendant ce temps** : Teste v2.33.0 (personnages créés maintenant utilisables !)
