# 📦 Release Notes v2.26.5 - Fix WorkManager Bundle Limit

## 🔧 Corrections critiques

### ❌ Erreur résolue : "Data cannot occupy more than 10240 bytes"

**Problème** : WorkManager a une limite de **10KB** pour les données d'entrée/sortie (`Bundle`). Les URLs d'images (Base64 ou longues URLs) dépassaient cette limite.

**Solution** : 
- ✅ **Sauvegarde dans SharedPreferences** : L'URL de l'image est stockée dans `SharedPreferences` (illimité)
- ✅ **Worker retourne un flag** : Au lieu de passer l'URL complète (>10KB), on retourne juste `"success"`
- ✅ **ViewModel lit depuis SharedPrefs** : Le `ChatViewModel` récupère l'URL depuis `SharedPreferences` quand le Worker réussit

```kotlin
// ImageGenerationWorker.kt - AVANT (❌ dépassait 10KB)
val outputData = workDataOf(KEY_RESULT_URL to imageUrl) // Base64 ou longue URL

// APRÈS (✅ <100 bytes)
prefs.edit().putString(KEY_LATEST_IMAGE_URL, imageUrl).apply()
val outputData = workDataOf(KEY_RESULT_URL to "success")
```

### 🚫 Freebox temporairement désactivée

**Raison** : ComfyUI sur port 33437 n'est PAS accessible depuis Internet (timeout).

**Actions** :
- ✅ Le Worker utilise **Stable Horde** même si "Freebox" est sélectionnée
- ⚠️ Message de fallback : "Freebox désactivée, utilisation de Stable Horde"

**Pour réactiver Freebox** (nécessite configuration réseau) :
1. Ouvrir le port 33437 dans le NAT/Firewall de la Freebox
2. OU relancer ComfyUI avec `--listen 0.0.0.0` au lieu de `localhost`

## 🎯 APIs disponibles

| API | Statut | Notes |
|-----|--------|-------|
| 🟡 **Freebox** | Désactivée temporairement | Port 33437 non accessible depuis Internet |
| 🟢 **Stable Horde** | ✅ Opérationnelle | URLs directes (r2=false) |
| 🟢 **Pollination AI** | ✅ Opérationnelle | URLs directes |

## 📝 Changements techniques

### `ImageGenerationWorker.kt`
```kotlin
// Sauvegarder URL dans SharedPreferences
val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
prefs.edit().putString(KEY_LATEST_IMAGE_URL, imageUrl).apply()

// Retourner uniquement un flag
Result.success(workDataOf(KEY_RESULT_URL to "success"))
```

### `ChatViewModel.kt`
```kotlin
// Lire URL depuis SharedPreferences
val prefs = getApplication<Application>().getSharedPreferences("image_worker_results", MODE_PRIVATE)
val imageUrl = prefs.getString("latest_image_url", null)
```

## 🚀 Prochaines étapes

1. **Configurer le réseau Freebox** pour ouvrir le port 33437
2. **Réactiver Freebox** une fois accessible depuis Internet
3. **Tester toutes les APIs** pour s'assurer qu'elles fonctionnent

---

**Note** : Cette version corrige définitivement l'erreur "Data cannot occupy more than 10240 bytes" en utilisant `SharedPreferences` au lieu de `WorkManager Bundle`.
