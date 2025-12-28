# 🧪 Guide de Test - Génération d'Images et Vidéos

## Vue d'ensemble

L'application utilise maintenant deux systèmes de génération d'images:

1. **Pollination AI** - Pour galeries et vignettes (gratuit, rapide, public)
2. **Freebox Stable Diffusion** - Pour images/vidéos dans conversations (local, gratuit, illimité)

---

## 🔧 Configuration Requise

### Freebox Stable Diffusion WebUI

**Prérequis**:
- Freebox Delta ou Ultra avec Debian installé
- Python 3.10+
- GPU NVIDIA (pour génération rapide)
- 10+ GB espace disque

**Installation** (si pas déjà fait):
```bash
# Sur la Freebox
cd /home/freebox
git clone https://github.com/AUTOMATIC1111/stable-diffusion-webui.git
cd stable-diffusion-webui

# Installer
./webui.sh --listen --api --port 7860

# Ou avec arguments complets
./webui.sh --listen --api --port 7860 --xformers --no-half-vae --enable-insecure-extension-access
```

**Vérification**:
```bash
# Tester l'accès
curl http://88.174.155.230:7860/

# Tester l'API
curl http://88.174.155.230:7860/sdapi/v1/sd-models
```

**État Actuel**: 
- URL configurée: `http://88.174.155.230:7860`
- L'app teste la connexion avant chaque génération
- Message d'erreur clair si le serveur n'est pas accessible

---

## 📸 Test 1: Génération d'Images dans Conversation

### Fonctionnalité
- Génère une image basée sur le contexte des 5 derniers messages
- Utilise Groq pour créer un prompt en anglais
- Génère via Freebox Stable Diffusion
- Style adapté: anime (Naruto) ou realistic (célébrités)

### Étapes de Test

1. **Démarrer conversation** avec un personnage
2. **Discuter** quelques messages (5+)
3. **Cliquer** sur l'icône 📷 (photo library) dans la TopBar
4. **Sélectionner** "Générer Image"
5. **Observer**:
   - Test de connexion Freebox
   - Création du prompt par Groq
   - Génération de l'image
   - Affichage dans le chat

### Erreurs Possibles

**"Freebox Stable Diffusion non accessible"**
- ✅ **Solution**: Démarrer le serveur WebUI
  ```bash
  cd /home/freebox/stable-diffusion-webui
  ./webui.sh --listen --api --port 7860
  ```

**"Échec de création du prompt"**
- ✅ **Solution**: Vérifier clés Groq API actives
- Voir GROQ_MULTIKEY_SETUP.md

**"Erreur génération image: Connection refused"**
- ✅ **Solution**: Vérifier firewall/port 7860 ouvert

### Résultat Attendu

```
[Image générée] http://88.174.155.230:7860/outputs/image_xxx.png
```

---

## 🎬 Test 2: Génération de Vidéos dans Conversation

### Fonctionnalité
- Génère une vidéo courte (2-4 sec) basée sur la conversation
- Utilise Groq pour créer un prompt avec détails de mouvement
- Génère via Freebox Stable Diffusion (img2img sequence)

### Étapes de Test

1. **Démarrer conversation** avec un personnage
2. **Discuter** d'une action ou scène dynamique
3. **Cliquer** sur l'icône 📷 dans la TopBar
4. **Sélectionner** "Générer Vidéo"
5. **Observer**:
   - Test de connexion Freebox
   - Création du prompt vidéo
   - Génération (plus long que image)
   - Affichage dans le chat

### Résultat Attendu

```
[Vidéo générée] http://88.174.155.230:7860/outputs/video_xxx.mp4
```

**Note**: La vidéo est en fait une séquence d'images (2-4 secondes)

---

## 🖼️ Test 3: Génération Galerie 10 Images

### Fonctionnalité
- Génère 10 images hyper-réalistes du personnage
- Utilise Pollination AI (gratuit, rapide, pas de clé)
- Basé sur `physicalDescription` détaillée

### Étapes de Test

1. **Sélectionner** un personnage
2. **Cliquer** pour voir les détails (page de présentation)
3. **Aller** à l'onglet "Galerie"
4. **Cliquer** "Générer 10 images"
5. **Observer**:
   - 10 requêtes à Pollination AI
   - Images apparaissent progressivement
   - Grille 2 colonnes
   - Compteur "(10 photos)"

### Résultat Attendu

**Galerie affichée**:
```
┌─────────┬─────────┐
│ Image 1 │ Image 2 │
├─────────┼─────────┤
│ Image 3 │ Image 4 │
├─────────┼─────────┤
│ Image 5 │ Image 6 │
├─────────┼─────────┤
│ Image 7 │ Image 8 │
├─────────┼─────────┤
│ Image 9 │ Image10 │
└─────────┴─────────┘
```

### Avantages Pollination AI
- ✅ **Gratuit** (pas de clé API)
- ✅ **Rapide** (2-5 sec par image)
- ✅ **Public** (pas de setup)
- ✅ **Qualité** correcte pour vignettes

---

## 🧪 Test 4: Vignettes Personnages

### Fonctionnalité
- Génère vignette optimisée pour sélection
- Format portrait
- Affichage dans CharacterSelectionScreen

### Étapes de Test

1. **Écran** de sélection des personnages
2. **Observer**: Chaque personnage a une vignette
3. **Si vide**: Génération automatique au premier affichage

### Code Concerné

```kotlin
// Dans CharacterSelectionScreen.kt
viewModel.generateCharacterThumbnail(character) { thumbnailUrl ->
    // Mise à jour automatique
}
```

---

## 📊 Tests de Performance

### Temps Moyens (à mesurer)

**Pollination AI** (images galerie):
- 1 image: ~2-5 secondes
- 10 images: ~20-50 secondes total

**Freebox Stable Diffusion** (conversation):
- Image: ~10-30 secondes (selon GPU)
- Vidéo: ~30-120 secondes (séquence)

**Groq** (prompts):
- Prompt image: ~1-2 secondes
- Prompt vidéo: ~1-2 secondes

### Optimisations Possibles

1. **Cache** les prompts similaires
2. **Paralléliser** génération galerie (actuellement séquentiel)
3. **Précharger** vignettes au lancement app
4. **Compression** images pour stockage

---

## 🐛 Debugging

### Logs à Vérifier

**ChatViewModel.kt**:
```kotlin
// Ajoutez ces logs pour debugging
Log.d("FREEBOX", "Testing connection to Freebox...")
Log.d("FREEBOX", "Ping result: ${pingResult.isSuccess}")
Log.d("GROQ", "Generated prompt: $imagePrompt")
Log.d("FREEBOX", "Image URL: $imageUrl")
```

**PollinationAIClient.kt**:
```kotlin
Log.d("POLLINATION", "Generating image ${i+1}/$count")
Log.d("POLLINATION", "Prompt: $prompt")
Log.d("POLLINATION", "Result URL: $url")
```

### Commandes Utiles

**Tester Freebox manuellement**:
```bash
# Ping
curl http://88.174.155.230:7860/

# Liste modèles
curl http://88.174.155.230:7860/sdapi/v1/sd-models

# Test génération simple
curl -X POST http://88.174.155.230:7860/sdapi/v1/txt2img \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "a beautiful anime girl",
    "steps": 20,
    "width": 512,
    "height": 512
  }'
```

**Logs Android**:
```bash
adb logcat | grep -E "(FREEBOX|POLLINATION|GROQ)"
```

---

## ✅ Checklist Complète

### Configuration
- [ ] Freebox WebUI installé et accessible
- [ ] Port 7860 ouvert
- [ ] Modèles Stable Diffusion téléchargés
- [ ] Clés Groq API configurées dans l'app

### Tests Fonctionnels
- [ ] Message d'accueil apparaît au démarrage
- [ ] Roleplay fonctionne (actions et pensées)
- [ ] Génération image conversation (Freebox)
- [ ] Génération vidéo conversation (Freebox)
- [ ] Génération galerie 10 images (Pollination)
- [ ] Vignettes personnages (Pollination)
- [ ] Gestion erreurs (Freebox offline)

### Tests Personnages
- [ ] Naruto: *actions* énergiques, "dattebayo!"
- [ ] Sasuke: Minimaliste, "Hn", "..."
- [ ] Sakura: Équilibre force/douceur
- [ ] Kakashi: Cool, livre orange
- [ ] Hinata: Timide, bégaiement, rougit
- [ ] Itachi: Mélancolique, philosophique
- [ ] Brad Pitt: Charmant, humble
- [ ] Leonardo DiCaprio: Passionné écologie
- [ ] The Rock: ÉNERGIQUE, motivant
- [ ] Scarlett: Confiante, voix rauque
- [ ] Margot: Pétillante, slang aussie
- [ ] Emma: Intelligente, féministe
- [ ] Zendaya: Cool, Gen Z, mode

### Performance
- [ ] Temps génération acceptable
- [ ] UI ne freeze pas pendant génération
- [ ] Indicateurs de chargement clairs
- [ ] Messages d'erreur utiles

---

## 📝 Rapport de Test (Template)

```markdown
### Test: [Nom du test]
**Date**: [Date]
**Personnage**: [Nom]
**Environnement**: [Android version, device]

#### Configuration Freebox
- WebUI accessible: ✅ / ❌
- Port 7860: ✅ / ❌
- Modèles installés: [Liste]

#### Résultats
- Génération image: ✅ / ❌ (temps: XX sec)
- Génération vidéo: ✅ / ❌ (temps: XX sec)
- Galerie 10 images: ✅ / ❌ (temps: XX sec)
- Roleplay correct: ✅ / ❌
- Message accueil: ✅ / ❌

#### Erreurs Rencontrées
[Décrire les erreurs]

#### Screenshots
[Ajouter captures d'écran]

#### Notes
[Observations supplémentaires]
```

---

## 🔗 Ressources

- **Stable Diffusion WebUI**: https://github.com/AUTOMATIC1111/stable-diffusion-webui
- **Pollination AI**: https://pollinations.ai/
- **Groq API**: https://console.groq.com/
- **Setup Freebox**: Voir `FREEBOX_MEDIA_SETUP.md`
- **Setup Groq**: Voir `GROQ_MULTIKEY_SETUP.md`

---

**Version**: 2.2.0  
**Date**: 26 Décembre 2024  
**Status**: Prêt pour tests
