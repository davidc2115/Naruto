# 🔑 Configuration Groq Vision API

Pour utiliser l'analyse automatique de photos avec l'IA, vous devez configurer votre clé API Groq.

## 📋 Étapes

### 1️⃣ Obtenir une clé API Groq

1. Rendez-vous sur https://console.groq.com/keys
2. Créez un compte gratuit (si ce n'est pas déjà fait)
3. Créez une nouvelle clé API
4. Copiez la clé (format: `gsk_...`)

### 2️⃣ Configurer la clé dans l'app

**Option A : Via les paramètres de l'app (RECOMMANDÉ)**

1. Ouvrez l'app Naruto AI Chat
2. Allez dans **Paramètres** ⚙️
3. Section "API Configuration"
4. Collez votre clé Groq Vision API
5. Sauvegardez

**Option B : En modifiant le code (développeurs)**

Modifiez `app/src/main/java/com/narutoai/chat/api/GroqVisionClient.kt` :

```kotlin
private fun getApiKey(context: Context): String {
    // Remplacez YOUR_GROQ_API_KEY_HERE par votre vraie clé
    return "gsk_VotreCléIci123456789"
}
```

**⚠️ ATTENTION** : Ne commitez JAMAIS votre clé API sur GitHub !

### 3️⃣ Tester

1. Créez un nouveau personnage
2. Ajoutez une photo
3. Cliquez sur "Analyser la photo"
4. L'IA devrait analyser l'image en ~5-10 secondes

---

## 🔒 Sécurité

- **Ne partagez JAMAIS votre clé API**
- **Ne la commitez JAMAIS sur Git**
- **Révoquez-la si elle est compromise**

---

## 💰 Coûts

Groq offre un quota gratuit généreux :
- **Gratuit** : ~14 000 requêtes/jour
- Largement suffisant pour usage personnel

Voir : https://console.groq.com/settings/limits

---

## ❓ Problèmes courants

### "Clé API non configurée"
➡️ Vérifiez que vous avez bien ajouté la clé dans les paramètres

### "Erreur API: HTTP 401"
➡️ Clé invalide, créez-en une nouvelle

### "Erreur API: HTTP 429"
➡️ Quota dépassé, attendez 24h ou passez à un plan payant

### "Timeout"
➡️ Connexion Internet lente, réessayez

---

## 🛠️ Pour les développeurs

### Structure

```kotlin
class GroqVisionClient(context: Context) {
    companion object {
        private fun getApiKey(context: Context): String {
            // Lit depuis SharedPreferences
            val prefs = context.getSharedPreferences("naruto_ai_prefs", MODE_PRIVATE)
            return prefs.getString("groq_vision_api_key", "") ?: ""
        }
    }
    
    suspend fun analyzePhotoForCharacter(imageUri: Uri): Result<PhysicalDescription>
}
```

### Ajouter dans SettingsScreen

Pour permettre la configuration depuis l'app :

```kotlin
// Dans SettingsScreen.kt
OutlinedTextField(
    value = groqApiKey,
    onValueChange = { viewModel.updateGroqApiKey(it) },
    label = { Text("Clé API Groq Vision") },
    placeholder = { Text("gsk_...") }
)
```

---

**Besoin d'aide ?** Créez une issue sur GitHub !
