# 🔄 Système de Fallback - Schéma de fonctionnement

## 📊 Architecture du système

```
┌─────────────────────────────────────────────────────────────────┐
│                    Utilisateur crée un personnage               │
│                         + Ajoute une photo                       │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│              GroqVisionClient.analyzePhotoForCharacter()        │
│                                                                  │
│  1. Charge et compresse l'image (max 500KB)                    │
│  2. Récupère la clé API depuis DataStore                       │
│  3. Lance la boucle de fallback                                │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│                    BOUCLE DE FALLBACK                            │
│                                                                  │
│  Pour chaque modèle dans [                                      │
│    "llama-3.2-90b-vision-instruct",                            │
│    "llama-3.2-11b-vision-preview",                             │
│    "llava-v1.5-7b-4096-preview"                                │
│  ]                                                              │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│           Tentative avec modèle N (tryAnalyzeWithModel)         │
│                                                                  │
│  1. Construit la requête JSON avec le modèle                   │
│  2. Envoie à l'API Groq (POST /chat/completions)              │
│  3. Attend la réponse (timeout: 60s)                          │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
                    ┌──────┴──────┐
                    │   Réponse   │
                    └──────┬──────┘
                           │
           ┌───────────────┴───────────────┐
           │                               │
           ▼                               ▼
    ┌──────────┐                   ┌──────────────┐
    │ HTTP 200 │                   │ HTTP 4xx/5xx │
    │ (Succès) │                   │   (Erreur)   │
    └─────┬────┘                   └──────┬───────┘
          │                               │
          │                               ▼
          │                        ┌─────────────────────────────┐
          │                        │  Parser l'erreur JSON       │
          │                        │                             │
          │                        │  Vérifier le type d'erreur: │
          │                        └──────┬──────────────────────┘
          │                               │
          │                 ┌─────────────┴─────────────┐
          │                 │                           │
          │                 ▼                           ▼
          │      ┌─────────────────────┐    ┌──────────────────┐
          │      │ Erreur de MODÈLE    │    │ Erreur d'API     │
          │      │ (decommissioned,    │    │ (unauthorized,   │
          │      │  not found, invalid)│    │  rate_limit, etc)│
          │      └──────┬──────────────┘    └────────┬─────────┘
          │             │                            │
          │             │                            │
          │             ▼                            ▼
          │      ┌─────────────┐          ┌──────────────────┐
          │      │ CONTINUER   │          │  ARRÊTER LA      │
          │      │ (essayer le │          │  BOUCLE          │
          │      │ modèle      │          │  (retourner      │
          │      │ suivant)    │          │  l'erreur)       │
          │      └─────────────┘          └──────────────────┘
          │
          │
          ▼
┌──────────────────────────────────────────────────────────────────┐
│                    ✅ SUCCÈS                                      │
│                                                                   │
│  1. Parse la réponse JSON                                        │
│  2. Extrait la description physique                              │
│  3. Crée l'objet PhysicalDescription                            │
│  4. Log: "✅ Succès avec modèle: [nom]"                         │
│  5. Retourne Result.success(description)                        │
└─────────────────────────┬─────────────────────────────────────────┘
                          │
                          ▼
                 ┌─────────────────┐
                 │ Remplir les     │
                 │ champs du       │
                 │ personnage      │
                 │ automatiquement │
                 └─────────────────┘
```

## 🎯 Cas d'usage détaillés

### Cas 1 : Succès immédiat (modèle principal fonctionne)

```
📱 Utilisateur: "Analyser la photo"

🔄 Système:
  [1] Tentative: llama-3.2-90b-vision-instruct
      → HTTP 200 ✅
      → "✅ Succès avec modèle: llama-3.2-90b-vision-instruct"
      → Analyse terminée (5-10s)

✅ Résultat: Description physique générée
```

**Durée totale**: ~5-10 secondes (1 seule requête API)

---

### Cas 2 : Fallback (1er modèle décommissionné)

```
📱 Utilisateur: "Analyser la photo"

🔄 Système:
  [1] Tentative: llama-3.2-90b-vision-instruct
      → HTTP 400 ❌
      → Error: "model_decommissioned"
      → "⚠️ Modèle échoué, essayer le suivant"
      
  [2] Tentative: llama-3.2-11b-vision-preview
      → HTTP 200 ✅
      → "✅ Succès avec modèle: llama-3.2-11b-vision-preview"
      → Analyse terminée

✅ Résultat: Description physique générée (avec un modèle différent)
```

**Durée totale**: ~10-15 secondes (2 requêtes API)

---

### Cas 3 : Fallback multiple (2 premiers échouent)

```
📱 Utilisateur: "Analyser la photo"

🔄 Système:
  [1] Tentative: llama-3.2-90b-vision-instruct
      → HTTP 400 ❌ (model_decommissioned)
      
  [2] Tentative: llama-3.2-11b-vision-preview
      → HTTP 404 ❌ (model_not_found)
      
  [3] Tentative: llava-v1.5-7b-4096-preview
      → HTTP 200 ✅
      → "✅ Succès avec modèle: llava-v1.5-7b-4096-preview"

✅ Résultat: Description physique générée (avec le 3ème modèle)
```

**Durée totale**: ~15-20 secondes (3 requêtes API)

---

### Cas 4 : Échec total (tous les modèles échouent)

```
📱 Utilisateur: "Analyser la photo"

🔄 Système:
  [1] Tentative: llama-3.2-90b-vision-instruct
      → HTTP 400 ❌ (model_decommissioned)
      
  [2] Tentative: llama-3.2-11b-vision-preview
      → HTTP 400 ❌ (model_decommissioned)
      
  [3] Tentative: llava-v1.5-7b-4096-preview
      → HTTP 400 ❌ (model_decommissioned)

❌ Résultat: Erreur détaillée affichée
   "Erreur API Groq Vision: Aucun modèle disponible n'a fonctionné.
   
   Modèles testés:
   llama-3.2-90b-vision-instruct, llama-3.2-11b-vision-preview, 
   llava-v1.5-7b-4096-preview
   
   Erreurs:
   - Modèle llama-3.2-90b-vision-instruct échoué: HTTP 400 - model_decommissioned
   - Modèle llama-3.2-11b-vision-preview échoué: HTTP 400 - model_decommissioned
   - Modèle llava-v1.5-7b-4096-preview échoué: HTTP 400 - model_decommissioned"
```

**Durée totale**: ~15-20 secondes (3 requêtes API)

---

### Cas 5 : Erreur d'API (pas de fallback)

```
📱 Utilisateur: "Analyser la photo"

🔄 Système:
  [1] Tentative: llama-3.2-90b-vision-instruct
      → HTTP 401 ❌ (unauthorized)
      → "Clé API invalide"
      → ARRÊT IMMÉDIAT (pas de fallback)

❌ Résultat: "Erreur API: HTTP 401 - Clé API invalide"
```

**Durée totale**: ~2-3 secondes (1 seule requête, pas de fallback inutile)

**Raison**: Si la clé API est invalide, inutile d'essayer d'autres modèles

---

## 🎨 Logs visuels dans logcat

### Succès avec fallback

```
D/GroqVision: 🔍 Chargement clés API depuis DataStore...
D/GroqVision: ✅ 1 clé(s) API trouvée(s) après parsing
D/GroqVision: 🔑 Utilisation clé: gsk_12345678...wxyz1234
D/GroqVision: Image compressée: 234KB, qualité: 85

D/GroqVision: 🔄 Tentative avec modèle: llama-3.2-90b-vision-instruct
W/GroqVision: ⚠️ Modèle llama-3.2-90b-vision-instruct échoué: Erreur API Groq Vision: HTTP 400
                Modèle tenté: llama-3.2-90b-vision-instruct
                Code: model_decommissioned
                The model 'llama-3.2-90b-vision-instruct' has been decommissioned...

D/GroqVision: 🔄 Tentative avec modèle: llama-3.2-11b-vision-preview
D/GroqVision: Réponse brute: {"age":"25-30 ans","gender":"femme","hairColor":"châtain long",...}
D/GroqVision: ✅ Succès avec modèle: llama-3.2-11b-vision-preview
```

---

## 🔧 Code clé - Logique de décision

```kotlin
// Boucle sur tous les modèles
for (model in VISION_MODELS) {
    android.util.Log.d("GroqVision", "🔄 Tentative avec modèle: $model")
    
    try {
        val result = tryAnalyzeWithModel(model, base64Image, apiKey)
        android.util.Log.d("GroqVision", "✅ Succès avec modèle: $model")
        return@withContext Result.success(result)  // ← SUCCÈS : On arrête ici
        
    } catch (e: Exception) {
        val errorMsg = "Modèle $model échoué: ${e.message}"
        android.util.Log.w("GroqVision", "⚠️ $errorMsg")
        errors.add(errorMsg)
        
        // 🔍 Analyse du type d'erreur
        if (e.message?.contains("decommissioned") == true || 
            e.message?.contains("not found") == true ||
            e.message?.contains("invalid") == true) {
            continue  // ← ERREUR DE MODÈLE : Essayer le suivant
        }
        
        // ❌ ERREUR D'API : Arrêter immédiatement
        throw e
    }
}

// 💥 Aucun modèle n'a fonctionné
return@withContext Result.failure(
    Exception("Erreur API Groq Vision: Aucun modèle disponible n'a fonctionné...")
)
```

---

## 📊 Comparaison Avant/Après

### ❌ AVANT (v2.38.0)

```
Modèle unique: llama-3.2-90b-vision-preview

┌──────────┐
│ Analyse  │
└────┬─────┘
     │
     ▼
  HTTP 400 (model_decommissioned)
     │
     ▼
  ❌ ÉCHEC
  "Erreur API: HTTP 400"
```

**Résultat**: Impossible d'analyser des photos (feature cassée)

---

### ✅ APRÈS (v2.39.4)

```
3 modèles avec fallback automatique

┌──────────┐
│ Analyse  │
└────┬─────┘
     │
     ▼
[1] llama-3.2-90b-vision-instruct
     │
     ├─ HTTP 200 → ✅ Succès
     │
     └─ HTTP 400 (decommissioned)
         │
         ▼
    [2] llama-3.2-11b-vision-preview
         │
         ├─ HTTP 200 → ✅ Succès
         │
         └─ HTTP 400 (decommissioned)
             │
             ▼
        [3] llava-v1.5-7b-4096-preview
             │
             ├─ HTTP 200 → ✅ Succès
             │
             └─ HTTP 400 → ❌ Tous échoués
```

**Résultat**: L'analyse fonctionne tant qu'au moins 1 modèle est actif

---

## 🎁 Avantages du système

| Aspect | Avant | Après |
|--------|-------|-------|
| **Résilience** | ❌ Cassé si modèle décommissionné | ✅ Fonctionne avec 1 des 3 modèles |
| **Performance** | ⚡ 1 requête (5-10s) | ⚡ 1-3 requêtes (5-20s max) |
| **Maintenance** | ❌ Mise à jour manuelle requise | ✅ Aucune action utilisateur |
| **Débogage** | ⚠️ Logs basiques | ✅ Logs détaillés avec emojis |
| **UX** | ❌ Erreur cryptique | ✅ Message clair et détaillé |

---

**Architecture conçue le :** 2 janvier 2026  
**Version :** 2.39.4  
**Auteur :** Cursor AI Assistant
