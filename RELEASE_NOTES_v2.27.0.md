# 📦 Release Notes v2.27.0 - Debug Pollination + Réactivation Freebox

## 🔧 Corrections

### 1. Pollination AI - Debug affichage images

**Problème rapporté** : L'image est générée (notification de succès) mais ne s'affiche pas.

**Changements** :
- ✅ Ajout de **logs de debug détaillés** pour tracer le problème
- ✅ Logs de l'URL générée par Pollination AI
- ✅ Logs de la longueur de l'URL
- ✅ Logs de la lecture depuis SharedPreferences
- ✅ Logs de la création du message avec image

**Logs ajoutés** :
```kotlin
// ImageGenerationWorker.kt
android.util.Log.d("ImageWorker", "🎨 URL générée: $url")
android.util.Log.d("ImageWorker", "📏 Longueur URL: ${url.length} caractères")

// ChatViewModel.kt
android.util.Log.d("ChatViewModel", "📖 Lecture URL depuis SharedPrefs")
android.util.Log.d("ChatViewModel", "✅ URL complète: $imageUrl")
android.util.Log.d("ChatViewModel", "📏 Longueur URL: ${imageUrl?.length ?: 0} caractères")
android.util.Log.d("ChatViewModel", "🖼️ Création message avec imageUrl")
```

### 2. Freebox - RÉACTIVATION

**Changements** :
- ✅ **Freebox RÉACTIVÉE** dans le Worker
- ✅ **Fallback automatique** : Si Freebox échoue → Pollination AI
- ✅ Timeout augmenté : 15s (au lieu de 3s) pour le PING
- ✅ Timeout augmenté : 10 min (au lieu de 3 min) pour la génération

**Code Worker** :
```kotlin
"freebox" -> {
    val comfyClient = ComfyUIClient()
    val freeboxResult = comfyClient.generateImage(...)
    if (freeboxResult.isSuccess) {
        freeboxResult // ✅ Freebox OK
    } else {
        // ⚠️ Freebox échec → Pollination AI
        PollinationAIClient().generateImage(...)
    }
}
```

## 🎯 Options de génération

Maintenant dans **Paramètres > Génération d'images** :

| Option | Comportement | Vitesse | Notes |
|--------|-------------|---------|-------|
| **Pollination AI** | Direct Pollination | 10-20s | ✅ Rapide et fiable (défaut) |
| **Freebox** | ComfyUI local, fallback Pollination | Variable | 🏠 Local puis cloud |
| **Stable Horde** | Queue workers gratuits | 2-10 min | 🐢 Lent mais gratuit |
| **Auto** | Pollination AI | 10-20s | 🔄 Intelligent |

## 🔍 Diagnostic du problème d'affichage

Avec cette version, tu peux :
1. Générer une image avec Pollination AI
2. Ouvrir les **logs Android** (`adb logcat | grep "ImageWorker\|ChatViewModel"`)
3. Chercher :
   - `🎨 URL générée:` → Voir l'URL de Pollination AI
   - `📖 Lecture URL depuis SharedPrefs` → Vérifier la lecture
   - `🖼️ Création message avec imageUrl` → Confirmer la création du message

**Scénarios possibles** :
- ✅ URL générée mais pas dans SharedPrefs → Problème de sauvegarde
- ✅ URL dans SharedPrefs mais pas affichée → Problème Coil/AsyncImage
- ✅ URL absente → Problème génération Pollination

## 🚀 Test recommandé

1. **Générer une image** avec Pollination AI (défaut)
2. **Regarder les logs** pour voir l'URL complète
3. **Copier l'URL** et l'ouvrir dans un navigateur pour vérifier qu'elle fonctionne
4. **Tester Freebox** : Sélectionner "Freebox" dans les paramètres

## 📝 Changements techniques

### `ImageGenerationWorker.kt`
- Réactivation Freebox avec fallback
- Logs de debug pour URL générée
- Support des 3 APIs avec logs distincts

### `ChatViewModel.kt`
- Logs de debug pour lecture SharedPreferences
- Logs de debug pour création du message avec image

### `ComfyUIClient.kt`
- PING_TIMEOUT: 15s (résout "inaccessible")
- GENERATION_TIMEOUT: 10 min

---

**Note** : Cette version ajoute des logs de debug pour identifier pourquoi les images Pollination AI ne s'affichent pas. Partage les logs pour que je puisse diagnostiquer !
