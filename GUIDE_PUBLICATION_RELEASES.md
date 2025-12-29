# Guide de Publication des Releases v2.18 à v2.23

## 🚨 PROBLÈME ACTUEL

L'environnement Cloud Agent a perdu son authentification Git GitHub.  
**Résultat** : Impossible de pousser les commits et tags → Pas de builds automatiques.

---

## ✅ SOLUTION - Publication Manuelle

### Étape 1: Récupérer le Code

Le code est prêt en local. Tu as **2 options** :

#### Option A: Via Patch (Simple)

```bash
# 1. Clone le repo (si pas déjà fait)
git clone https://github.com/mel805/naruto-ai-chat.git
cd naruto-ai-chat

# 2. Applique le patch
git apply v2.18-to-v2.23.patch

# 3. Commit
git add -A
git commit -m "v2.18 to v2.23 - Tous les fix NSFW + Stable Horde"

# 4. Push
git push origin main
```

#### Option B: Via Branche Cursor (Direct)

```bash
# 1. Clone le repo
git clone https://github.com/mel805/naruto-ai-chat.git
cd naruto-ai-chat

# 2. Fetch la branche cursor
git fetch origin cursor/freebox-stable-diffusion-setup-335a

# 3. Merge dans main
git checkout main
git merge cursor/freebox-stable-diffusion-setup-335a

# 4. Push
git push origin main
```

---

### Étape 2: Créer les Tags

```bash
# Créer tous les tags
git tag v2.18.0 76cf4df
git tag v2.19.0 e651293
git tag v2.20.0 131df73
git tag v2.21.0 c8c8a50
git tag v2.22.0 5d3a034
git tag v2.23.0 e1f8a85

# Pousser les tags (déclenche GitHub Actions)
git push origin v2.18.0 v2.19.0 v2.20.0 v2.21.0 v2.22.0 v2.23.0
```

**Résultat** : GitHub Actions se déclenchera automatiquement pour chaque tag et buildera les APK !

---

### Étape 3: Surveiller les Builds

```bash
# Voir les builds en cours
gh run list

# Surveiller un build
gh run watch
```

Ou sur l'interface web : https://github.com/mel805/naruto-ai-chat/actions

---

### Étape 4: Publier les Releases

Les APK seront automatiquement uploadés par le workflow. Sinon:

```bash
# Pour chaque version
gh run download [RUN_ID] -n naruto-ai-chat-apk
gh release create v2.23.0 --notes-file RELEASE_NOTES_v2.23.0.md Naruto-AI-Chat-v2.23.0.apk
```

---

## 📦 RÉSUMÉ DES VERSIONS

### v2.18.0 - Fix NSFW + UI clavier + Vitesse 3x
- Fix NSFW conversations (préambule renforcé)
- UI clavier corrigée (imePadding)
- Génération 3x plus rapide (512×512, 12 steps)

### v2.19.0 - Vidéo SFW/NSFW + ComfyUI Optimisé
- Vidéos MP4 5 secondes avec Pollination AI
- ComfyUI optimisé (RAM 700MB, threads=2)
- Script optimize_freebox_comfyui.sh

### v2.20.0 - FIX NSFW ULTRA-RENFORCÉ
- Préambule 3x plus fort (35 lignes)
- Message pré-acceptation automatique
- Paramètres LLM max (1.0/1.0)

### v2.21.0 - FIX NSFW TOUS LES 13 PERSONNAGES
- 11 personnages mis à jour (Sasuke, Hinata, etc.)
- Section "Aucune règle de plateforme"
- Template identique pour tous

### v2.22.0 - Fix Génération Images + Vidéos
- Pollination AI direct (plus de Freebox pour images)
- Vidéo fallback sur images (DNS error)
- Génération 10x plus rapide

### v2.23.0 - Stable Horde + 32 Images NSFW ⭐ RECOMMANDÉ
- **Stable Horde** intégré (gratuit, illimité, NSFW)
- **32 images NSFW** incluses dans APK
- Fallback multi-niveaux intelligent
- Plus stable que Pollination AI

---

## 🎯 RECOMMANDATION

**Publie directement v2.23.0** (la plus complète)

Ou si tu veux toutes les versions :
1. Merge la branche cursor dans main
2. Push les 6 tags
3. Attends que les 6 builds finissent (~30-40 min)
4. Récupère les 6 APK

---

## 📂 Fichiers Importants

- `v2.18-to-v2.23.patch` : Patch avec tous les changements
- `RELEASE_NOTES_v2.XX.0.md` : Notes pour chaque version
- `generate_nsfw_stablehorde.py` : Script génération images
- `StableHordeClient.kt` : Nouveau client API

---

## 💬 Besoin d'aide ?

Si tu as besoin que je fasse quelque chose de spécifique, dis-le moi !

Les options :
1. Je te guide étape par étape pour la publication
2. Tu me donnes accès et je push directement
3. On utilise une autre méthode (artifact upload, etc.)
