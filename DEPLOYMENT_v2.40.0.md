# 🚀 Instructions de Déploiement - v2.40.0

## 📦 Résumé des Modifications

Cette version contient :
- ✅ 3 nouveaux personnages adultes avec descriptions ultra-détaillées
- ✅ Système de génération d'images ultra-amélioré
- ✅ Génération de vignettes et galeries automatiques
- ✅ Fix Groq Vision (v2.39.4 incluse)
- ✅ Version : 2.39.4 → 2.40.0 (Build 69)

---

## 📂 Fichiers Modifiés

```
modified:   app/build.gradle.kts                                (version 2.40.0)
modified:   app/src/main/java/com/narutoai/chat/data/Characters.kt    (+500 lignes)
modified:   app/src/main/java/com/narutoai/chat/viewmodel/ChatViewModel.kt  (+150 lignes)

# Fichiers de la v2.39.4 (déjà modifiés) :
modified:   GROQ_API_SETUP.md
modified:   RELEASE_NOTES_v2.31.0.md
modified:   app/src/main/java/com/narutoai/chat/api/GroqVisionClient.kt

new file:   release_notes_v2.39.4.md
new file:   release_notes_v2.40.0.md
new file:   DEPLOYMENT_v2.40.0.md (ce fichier)
```

---

## 🔧 Étape 1 : Commit et Push

### Vérifier l'état Git

```bash
cd /workspace
git status
```

### Ajouter tous les fichiers

```bash
git add -A
```

### Créer le commit

```bash
git commit -m "feat(v2.40.0): Nouveaux personnages + génération images ultra-améliorée

NOUVEAUX PERSONNAGES (3):
- Sofia Martinez: Collègue espagnole taquine et séduisante (28 ans)
- Luna Chen: Voisine mystérieuse et artiste (26 ans)
- Chloé Dubois: Amie d'enfance, relation qui évolue (27 ans)

Tous avec descriptions physiques ultra-détaillées, scénarios complets,
system prompts SFW/NSFW soft, et background stories approfondis.

GÉNÉRATION D'IMAGES AMÉLIORÉE:
- Prise en compte description physique COMPLÈTE du personnage
- Déduction automatique de la TENUE depuis conversation
- Déduction automatique de la POSE/ACTION depuis contexte
- Déduction automatique du LIEU/SETTING depuis dialogue
- Expression faciale adaptée au mood conversation
- Mode NSFW suggestif/sensuel (pas explicite)
- Prompts Groq optimisés avec instructions détaillées

VIGNETTES & GALERIES:
- Fonction generateCharacterThumbnail() via Pollination AI
- Fonction generateCharacterGallery() avec 6 variations automatiques
- Support SFW et NSFW soft

TECHNIQUE:
- Refactorisation ChatViewModel.generateImageFromConversation()
- System prompts Groq expert optimisés
- Max 100 mots pour prompts détaillés sans surcharge
- Cohérence totale des personnages générés

FIXES (v2.39.4 incluse):
- Système de fallback Groq Vision (3 modèles)
- Documentation mise à jour

Version: 2.39.4 → 2.40.0
Build: 68 → 69
Date: 2 janvier 2026"
```

### Push vers GitHub

```bash
git push origin cursor/api-model-error-fix-50fb
```

Si vous êtes sur une autre branche ou voulez pousser sur `main` :

```bash
# Pour pousser sur main :
git push origin HEAD:main

# Ou si vous voulez merger dans main :
git checkout main
git merge cursor/api-model-error-fix-50fb
git push origin main
```

---

## 🏗️ Étape 2 : Build de l'APK

### Option A : Build Local (Recommandé pour tester)

```bash
cd /workspace

# Nettoyer les builds précédents
./gradlew clean

# Build APK Release (signé avec keystore debug)
./gradlew assembleRelease

# L'APK sera dans :
# app/build/outputs/apk/release/app-release.apk
```

### Option B : Build via GitHub Actions (Automatique)

Si vous avez GitHub Actions configuré, le push déclenchera automatiquement le build.

**Vérifier le workflow** :

```bash
# Voir si un workflow existe
ls -la .github/workflows/

# Si oui, le build se lancera automatiquement après le push
```

**Suivre le build** :

1. Allez sur GitHub : https://github.com/mel805/naruto-ai-chat
2. Cliquez sur l'onglet "Actions"
3. Vous verrez le workflow en cours
4. Attendez que le build soit vert ✅

### Option C : Build Manuel sans Gradle Wrapper

```bash
# Si le gradlew ne fonctionne pas :
cd /workspace
gradle assembleRelease
```

---

## 📦 Étape 3 : Créer la Release GitHub

### Via Interface Web (Recommandé)

1. **Aller sur GitHub** :
   ```
   https://github.com/mel805/naruto-ai-chat/releases/new
   ```

2. **Remplir les champs** :
   - **Tag version** : `v2.40.0`
   - **Target** : `main` (ou votre branche)
   - **Release title** : `v2.40.0 - Nouveaux Personnages + Génération Images Ultra-Améliorée`

3. **Description** : Copier-coller depuis `release_notes_v2.40.0.md`

4. **Upload APK** :
   - Cliquez sur "Attach binaries"
   - Upload `app/build/outputs/apk/release/app-release.apk`
   - Renommez en : `naruto-ai-chat-v2.40.0.apk`

5. **Publier** :
   - Si c'est une pre-release : Cocher "This is a pre-release"
   - Sinon : Cliquer "Publish release"

### Via GitHub CLI (si installé)

```bash
cd /workspace

# Créer la release
gh release create v2.40.0 \
  --title "v2.40.0 - Nouveaux Personnages + Génération Images Ultra-Améliorée" \
  --notes-file release_notes_v2.40.0.md \
  app/build/outputs/apk/release/app-release.apk

# Vérifier la release
gh release view v2.40.0
```

---

## 🔗 Étape 4 : Obtenir le Lien de Release

### Une fois la release publiée :

Le lien sera :
```
https://github.com/mel805/naruto-ai-chat/releases/tag/v2.40.0
```

### Lien direct vers l'APK :

```
https://github.com/mel805/naruto-ai-chat/releases/download/v2.40.0/naruto-ai-chat-v2.40.0.apk
```

---

## ✅ Checklist de Déploiement

### Avant le Push
- [x] Code testé localement (syntaxe Kotlin OK)
- [x] Version bumped (2.40.0, build 69)
- [x] Release notes créées
- [x] Nouveaux personnages ajoutés
- [x] Génération d'images améliorée
- [ ] **À FAIRE : Build APK local pour tester**

### Push Git
- [ ] `git add -A`
- [ ] `git commit` avec message détaillé
- [ ] `git push origin [branche]`
- [ ] Vérifier que le push a réussi sur GitHub

### Build & Release
- [ ] Build APK (local ou GitHub Actions)
- [ ] Tester l'APK sur un appareil
- [ ] Créer la release GitHub (tag v2.40.0)
- [ ] Upload APK dans la release
- [ ] Publier la release
- [ ] Vérifier que la release est publique

### Post-Release
- [ ] Tester le lien de téléchargement
- [ ] Partager la release
- [ ] Monitorer les retours utilisateurs

---

## 🛠️ Troubleshooting

### Problème : Push refusé

```bash
# Si la branche distante a divergé :
git pull --rebase origin [branche]
git push origin [branche]

# Ou forcer le push (ATTENTION, écrase l'historique distant) :
git push origin [branche] --force
```

### Problème : Build Gradle échoue

```bash
# Vérifier les erreurs :
./gradlew assembleRelease --stacktrace

# Nettoyer le cache :
./gradlew clean
rm -rf .gradle
./gradlew assembleRelease
```

### Problème : APK non signé

```bash
# L'APK est déjà configuré pour être signé avec naruto-debug.keystore
# Si problème, vérifier app/build.gradle.kts lignes 24-31
```

### Problème : GitHub Actions échoue

1. Voir les logs dans l'onglet Actions sur GitHub
2. Vérifier que le workflow `.github/workflows/*.yml` est correct
3. Vérifier les secrets GitHub (si utilisés)

---

## 📞 Support

Si vous rencontrez des problèmes :

1. **Logs Gradle** : `./gradlew assembleRelease --info`
2. **Logs Git** : `git status`, `git log`
3. **GitHub Issues** : Créer une issue si besoin

---

## 🎉 Félicitations !

Une fois déployé, partagez le lien :

```
📱 Téléchargez Naruto AI Chat v2.40.0 :
https://github.com/mel805/naruto-ai-chat/releases/tag/v2.40.0

✨ Nouveautés :
- 3 nouveaux personnages adultes ultra-détaillés
- Génération d'images prenant en compte conversation + tenue + pose
- Vignettes et galeries automatiques
```

---

**Bon déploiement ! 🚀**

Date : 2 janvier 2026  
Version : 2.40.0  
Build : 69
