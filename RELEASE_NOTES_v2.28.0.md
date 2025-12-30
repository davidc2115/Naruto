# 📦 Release Notes v2.28.0 - Sauvegarde Persistante + Vidéos Fixes + Mode NSFW

## 🎯 Changements majeurs

### 1. ✅ Images et vidéos PERSISTENT dans les conversations

**Problème** : Les images/vidéos disparaissaient après fermeture de l'app.

**Solution** : Fichiers sauvegardés dans un dossier **permanent** :
- **Avant** : `cacheDir` (temporaire, supprimé par Android)
- **Après** : `filesDir/generated_images` et `filesDir/generated_videos` (permanent)

**Résultat** :
- ✅ Images/vidéos persistent après fermeture de l'app
- ✅ Sauvegardées automatiquement dans la conversation
- ✅ Rechargées au retour dans la conversation

### 2. 🎬 Vidéos maintenant fonctionnelles

**Problème** : "Image Pollination AI ratée" au lieu de la vidéo.

**Solution** :
- Téléchargement et sauvegarde en fichier local `.mp4`
- Support fichiers locaux dans Coil
- Worker en arrière-plan avec notifications

**Résultat** :
- ✅ Vidéos se chargent correctement
- ✅ Sauvegardées comme fichiers locaux
- ✅ Persistent dans les conversations

### 3. 🔞 Mode NSFW pris en compte lors de la génération

**Problème** : Le mode NSFW n'était pas passé aux APIs.

**Solution** : Le flag `isNSFW` est maintenant transmis :
- ✅ Images : `ImageGenerationWorker` → APIs
- ✅ Vidéos : `VideoGenerationWorker` → PollinationAI
- ✅ Prompts : Groq adapte selon mode NSFW

**Résultat** :
- ✅ Contenu NSFW si mode activé
- ✅ Contenu SFW si mode désactivé

## 🔧 Changements techniques

### Fichiers permanents (images)
```kotlin
// AVANT (v2.27.2) - Temporaire
val imageFile = File(cacheDir, "generated_image_X.png") // Supprimé par Android

// APRÈS (v2.28.0) - Permanent
val imagesDir = File(filesDir, "generated_images")
val imageFile = File(imagesDir, "image_X.png") // Persiste toujours
```

### Fichiers permanents (vidéos)
```kotlin
// Télécharger et sauvegarder
val videosDir = File(filesDir, "generated_videos")
val videoFile = File(videosDir, "video_X.mp4")
videoFile.writeBytes(videoBytes)

// Sauvegarder dans SharedPreferences
prefs.putString("latest_video_url", videoFile.absolutePath)
```

### Support fichiers locaux vidéo (ChatScreen)
```kotlin
AsyncImage(
    model = if (videoUrl.startsWith("/")) {
        File(videoUrl) // Fichier local
    } else {
        videoUrl // URL
    }
)
```

### Sauvegarde automatique
```kotlin
// Après chaque ajout d'image ou vidéo
_messages.value = messages + ChatMessage(imageUrl = url)
saveCurrentConversation() // Auto-save ✅
```

### Gestion du cache
```kotlin
// Images: Garder les 50 dernières (au lieu de 10)
imageFiles.sortedByDescending { lastModified() }.drop(50).forEach { delete() }

// Vidéos: Garder les 20 dernières
videoFiles.sortedByDescending { lastModified() }.drop(20).forEach { delete() }
```

## 📊 Résumé des corrections

| Problème | Avant | Après |
|----------|-------|-------|
| **Images persistent** | ❌ Supprimées (cache) | ✅ Permanent (filesDir) |
| **Vidéos persistent** | ❌ Supprimées | ✅ Permanent (filesDir) |
| **Vidéos s'affichent** | ❌ Image ratée | ✅ Vidéo .mp4 |
| **Mode NSFW** | ❌ Ignoré | ✅ Pris en compte |
| **Sauvegarde auto** | ⚠️ Manuelle | ✅ Automatique |

## 🧪 Test

### Test 1 : Persistence des images
1. Génère une image dans une conversation
2. ✅ Image s'affiche
3. Ferme l'app complètement
4. Rouvre l'app et va dans la même conversation
5. ✅ L'image est toujours là !

### Test 2 : Persistence des vidéos
1. Génère une vidéo dans une conversation
2. ✅ Vidéo s'affiche (pas d'image Pollination ratée)
3. Ferme l'app complètement
4. Rouvre l'app et va dans la même conversation
5. ✅ La vidéo est toujours là !

### Test 3 : Mode NSFW
1. Active le mode NSFW
2. Génère une image
3. ✅ Le contenu devrait être NSFW (si le prompt le demande)
4. Désactive le mode NSFW
5. Génère une image
6. ✅ Le contenu devrait être SFW

## 🗑️ Gestion de l'espace

Pour éviter de remplir le stockage :
- **Images** : 50 dernières conservées (~5MB)
- **Vidéos** : 20 dernières conservées (~50MB)
- **Nettoyage automatique** à chaque génération

## 📝 Structure des dossiers

```
/data/data/com.narutoai.chat/files/
├── generated_images/
│   ├── image_1234567890.png
│   ├── image_1234567891.png
│   └── ... (50 max)
└── generated_videos/
    ├── video_1234567890.mp4
    ├── video_1234567891.mp4
    └── ... (20 max)
```

## 🚀 Prochaines étapes

- ✅ Images persistent
- ✅ Vidéos persistent
- ✅ Mode NSFW pris en compte
- 🔄 Les conversations sont maintenant complètement sauvegardées avec leurs médias

---

**Cette version corrige définitivement la persistence des images/vidéos** ! 🎉
