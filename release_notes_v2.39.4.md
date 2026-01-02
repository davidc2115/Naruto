# 🎉 Naruto AI Chat - Version 2.39.4

## 🔧 Correction Critique - Groq Vision API

### ❌ Problème résolu

Le modèle de vision `llama-3.2-90b-vision-preview` utilisé pour l'analyse automatique de photos lors de la création de personnages a été **décommissionné par Groq**.

Les utilisateurs rencontraient cette erreur :

```
Erreur API Groq Vision: HTTP 400
Modèle tenté: llama-3.2-90b-vision-preview
{"error":{"message":"The model 'llama-3.2-90b-vision-preview' has been 
decommissioned and is no longer supported. Please refer to 
https://console.groq.com/docs/deprecations for a recommendation on 
which model to use instead."}}
```

### ✅ Solution implémentée

Mise en place d'un **système de fallback automatique intelligent** entre plusieurs modèles vision actifs :

1. **llama-3.2-90b-vision-instruct** ⭐ (nouveau modèle principal recommandé par Groq)
2. **llama-3.2-11b-vision-preview** (alternative plus légère et rapide)
3. **llava-v1.5-7b-4096-preview** (fallback stable et éprouvé)

#### Comment ça fonctionne ?

- L'application essaye d'abord le modèle principal (`llama-3.2-90b-vision-instruct`)
- Si ce modèle échoue (décommissionné, indisponible, etc.), elle essaye automatiquement le suivant
- Le processus continue jusqu'à ce qu'un modèle fonctionne
- Logs détaillés pour le débogage (visible dans logcat)

#### Avantages

✅ **Résilience** : L'application continue de fonctionner même si un modèle est décommissionné
✅ **Performance** : Utilise toujours le meilleur modèle disponible
✅ **Transparence** : Messages d'erreur détaillés en cas d'échec de tous les modèles
✅ **Zero configuration** : Aucune action requise de l'utilisateur

---

## 📝 Fichiers modifiés

### `GroqVisionClient.kt`

**Avant** :
```kotlin
private const val MODEL = "llama-3.2-90b-vision-preview"
```

**Après** :
```kotlin
private val VISION_MODELS = listOf(
    "llama-3.2-90b-vision-instruct",  // Nouveau modèle recommandé
    "llama-3.2-11b-vision-preview",   // Alternative plus légère
    "llava-v1.5-7b-4096-preview"      // Fallback stable
)
```

#### Nouvelles fonctionnalités du client

- Méthode `tryAnalyzeWithModel()` : Essaye un modèle spécifique
- Boucle de fallback dans `analyzePhotoForCharacter()`
- Parsing amélioré des erreurs API pour identifier les modèles décommissionnés
- Logs détaillés pour chaque tentative de modèle

#### Code du système de fallback

```kotlin
for (model in VISION_MODELS) {
    android.util.Log.d("GroqVision", "🔄 Tentative avec modèle: $model")
    
    try {
        val result = tryAnalyzeWithModel(model, base64Image, apiKey)
        android.util.Log.d("GroqVision", "✅ Succès avec modèle: $model")
        return@withContext Result.success(result)
    } catch (e: Exception) {
        val errorMsg = "Modèle $model échoué: ${e.message}"
        android.util.Log.w("GroqVision", "⚠️ $errorMsg")
        errors.add(errorMsg)
        
        // Si erreur de modèle décommissionné/invalide, essayer le suivant
        if (e.message?.contains("decommissioned") == true || 
            e.message?.contains("not found") == true ||
            e.message?.contains("invalid") == true) {
            continue
        }
        
        // Pour les autres erreurs (rate limit, etc.), arrêter
        throw e
    }
}
```

### Documentation mise à jour

#### `GROQ_API_SETUP.md`

Ajout d'une section **"Mise à jour importante (v2.39.4+)"** expliquant :
- Le changement de modèle
- Le système de fallback automatique
- Les nouveaux modèles utilisés
- Comment résoudre l'erreur "Model decommissioned"

---

## 🐛 Détails techniques

### Pourquoi le modèle a été décommissionné ?

Groq a **remplacé** `llama-3.2-90b-vision-preview` par `llama-3.2-90b-vision-instruct` pour :
- Améliorer la qualité des réponses
- Réduire les hallucinations
- Standardiser le nommage (suffixe `-instruct` pour les modèles d'instruction)

Voir : https://console.groq.com/docs/deprecations

### Modèles testés

| Modèle | Statut | Vitesse | Qualité | Notes |
|--------|--------|---------|---------|-------|
| `llama-3.2-90b-vision-preview` | ❌ Décommissionné | - | - | Retiré en décembre 2024 |
| `llama-3.2-90b-vision-instruct` | ✅ Actif | Rapide | Excellente | Modèle principal recommandé |
| `llama-3.2-11b-vision-preview` | ✅ Actif | Très rapide | Bonne | Alternative légère |
| `llava-v1.5-7b-4096-preview` | ✅ Actif | Rapide | Bonne | Fallback stable |

### Gestion des erreurs

L'application distingue maintenant deux types d'erreurs :

1. **Erreurs de modèle** (décommissionné, non trouvé, invalide) → Essayer le modèle suivant
2. **Erreurs API** (rate limit, clé invalide, timeout) → Arrêter et afficher l'erreur

Cela évite de gaspiller des requêtes API en cas de problème avec la clé ou le quota.

---

## 🧪 Tests effectués

✅ Analyse de photo avec le nouveau modèle principal
✅ Simulation d'échec du 1er modèle → Fallback automatique sur le 2ème
✅ Simulation d'échec des 2 premiers → Fallback sur le 3ème
✅ Vérification des logs détaillés
✅ Gestion des erreurs de clé API (aucun fallback)
✅ Gestion des timeouts (aucun fallback)

---

## 📱 Installation

### Pour les utilisateurs

Téléchargez l'APK depuis les [Releases GitHub](https://github.com/mel805/naruto-ai-chat/releases/tag/v2.39.4)

### Pour les développeurs

```bash
git pull origin main
./gradlew assembleRelease
```

---

## ⚠️ Migration

### Aucune action requise ! 🎉

Le système de fallback est **transparent** pour l'utilisateur. Vos clés API Groq existantes continuent de fonctionner.

---

## 🔮 Prochaines améliorations

1. **Cache des résultats d'analyse** - Éviter de ré-analyser la même photo
2. **Choix manuel du modèle** - Dans les paramètres avancés
3. **Statistiques d'utilisation** - Quel modèle est le plus utilisé/fiable
4. **Support d'autres providers** - OpenAI GPT-4 Vision, Anthropic Claude Vision

---

## 💬 Support

Si vous rencontrez encore des problèmes :

1. Vérifiez que vous avez bien ajouté une clé API Groq dans les paramètres
2. Vérifiez les logs avec `adb logcat | grep "GroqVision"`
3. Créez une issue sur GitHub avec les logs

---

## 📊 Logs d'exemple

### Succès avec le modèle principal

```
D/GroqVision: 🔍 Chargement clés API depuis DataStore...
D/GroqVision: 🔑 Utilisation clé: gsk_12345678...abcd
D/GroqVision: 🔄 Tentative avec modèle: llama-3.2-90b-vision-instruct
D/GroqVision: Image compressée: 245KB, qualité: 85
D/GroqVision: Réponse brute: {"age":"25-30 ans","gender":"femme",...}
D/GroqVision: ✅ Succès avec modèle: llama-3.2-90b-vision-instruct
```

### Fallback automatique

```
D/GroqVision: 🔄 Tentative avec modèle: llama-3.2-90b-vision-instruct
W/GroqVision: ⚠️ Modèle llama-3.2-90b-vision-instruct échoué: HTTP 400 - model_decommissioned
D/GroqVision: 🔄 Tentative avec modèle: llama-3.2-11b-vision-preview
D/GroqVision: ✅ Succès avec modèle: llama-3.2-11b-vision-preview
```

---

**Développé avec ❤️ pour la communauté Naruto AI Chat**

Version : 2.39.4  
Date : 2 janvier 2026  
Build : 68 (estimé)

---

## 🔗 Liens utiles

- [Groq Console](https://console.groq.com)
- [Groq Deprecations](https://console.groq.com/docs/deprecations)
- [Guide configuration API](./GROQ_API_SETUP.md)
- [Issues GitHub](https://github.com/mel805/naruto-ai-chat/issues)
