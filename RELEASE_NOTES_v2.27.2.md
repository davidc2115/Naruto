# 📦 Release Notes v2.27.2 - Fix Image Rouge (Fichiers Locaux)

## 🔴 Problème : Image Rouge au lieu de l'image

**Symptôme** : Rectangle/carré rouge à la place de l'image générée (Pollination AI et Freebox).

**Cause** : 
1. **Base64 trop gros** pour SharedPreferences (limite ~2MB)
2. **URL Pollination AI** ne se charge pas directement dans Coil (CORS/certificat)
3. Coil affiche l'**erreur** en rouge

## ✅ Solution : Fichiers Locaux

Au lieu de stocker des URLs ou du Base64, les images sont maintenant **téléchargées et sauvegardées comme fichiers locaux** :

### Avant (v2.27.1) ❌
```kotlin
// Base64 → Trop gros pour SharedPreferences
val base64 = Base64.encodeToString(imageBytes, ...)
prefs.putString("url", "data:image/png;base64,$base64") // > 2MB = CRASH

// OU URL Pollination → Coil ne peut pas charger
prefs.putString("url", "https://image.pollinations.ai/...") // CORS error
```

### Après (v2.27.2) ✅
```kotlin
// Télécharger et sauvegarder en fichier local
val imageFile = File(cacheDir, "generated_image_${timestamp}.png")
imageFile.writeBytes(imageBytes)
prefs.putString("url", imageFile.absolutePath) // Chemin fichier local

// Coil charge depuis le fichier
AsyncImage(model = File(imageUrl))
```

## 🔧 Changements techniques

### 1. Téléchargement et sauvegarde en fichier local
```kotlin
// ImageGenerationWorker.kt
if (imageUrl.contains("pollinations")) {
    // Télécharger l'image
    val imageBytes = downloadImage(imageUrl)
    
    // Sauvegarder en fichier local
    val imageFile = File(cacheDir, "generated_image_${timestamp}.png")
    imageFile.writeBytes(imageBytes)
    
    // Retourner le chemin au lieu de l'URL
    return imageFile.absolutePath
}
```

### 2. Support fichiers locaux dans Coil
```kotlin
// ChatScreen.kt
AsyncImage(
    model = if (imageUrl.startsWith("/")) {
        File(imageUrl) // Fichier local
    } else {
        imageUrl // URL ou Base64
    },
    onError = { error ->
        Log.e("ChatScreen", "Erreur: ${error.message}")
    }
)
```

### 3. Nettoyage automatique du cache
```kotlin
// Garder seulement les 10 dernières images
val imageFiles = cacheDir.listFiles { ... }
    .sortedByDescending { it.lastModified() }
imageFiles.drop(10).forEach { it.delete() }
```

## 📊 Avantages

| Méthode | Avant (v2.27.1) | Après (v2.27.2) |
|---------|-----------------|-----------------|
| **Taille** | Base64 > 2MB | Fichier ~100KB |
| **SharedPreferences** | ❌ Dépassement limite | ✅ Juste le chemin |
| **Chargement Coil** | ❌ URL CORS error | ✅ Fichier local |
| **Performance** | ❌ Lent (Base64) | ✅ Rapide (fichier) |
| **Cache** | ❌ Pas géré | ✅ Auto-nettoyage |

## 🧪 Test

1. **Générer une image avec Pollination AI** :
   - L'image devrait maintenant s'afficher ✅
   - Plus de carré/rectangle rouge ❌
   - Source affichée : "Pollination AI (cloud)"

2. **Générer une image avec Freebox** :
   - Si Freebox OK : "Source: Freebox (local)"
   - Si Freebox échec : Fallback Pollination AI
   - Image s'affiche dans les deux cas ✅

3. **Vérifier les logs** :
   ```bash
   adb logcat | grep "ImageWorker\|ChatScreen"
   ```
   
   Tu devrais voir :
   - `📥 Téléchargement image Pollinations...`
   - `✅ Image sauvegardée: /data/user/0/.../cache/generated_image_XXX.png (45KB)`
   - `✅ Chemin sauvegardé dans SharedPrefs`

4. **Si erreur rouge persiste** :
   - Cherche : `❌ Erreur chargement image:`
   - Le message d'erreur indiquera le problème exact

## 🗑️ Gestion du cache

Les anciennes images sont automatiquement supprimées :
- Seules les **10 dernières images** sont conservées
- Nettoyage à chaque nouvelle génération
- Évite de remplir le stockage de l'appareil

## 🔍 Diagnostic

Si l'image ne s'affiche toujours pas :

1. **Vérifier le chemin du fichier** :
   ```bash
   adb logcat | grep "Chemin sauvegardé"
   ```
   
2. **Vérifier l'erreur Coil** :
   ```bash
   adb logcat | grep "Erreur chargement image"
   ```

3. **Vérifier si le fichier existe** :
   ```bash
   adb shell ls -lh /data/data/com.narutoai.chat/cache/generated_image_*.png
   ```

## 🚀 Résultat attendu

- ✅ Plus de carré/rectangle rouge
- ✅ Images s'affichent correctement (Pollination & Freebox)
- ✅ Source clairement indiquée
- ✅ Cache géré automatiquement

---

**Cette version devrait définitivement résoudre le problème d'affichage des images** ! 🎉
