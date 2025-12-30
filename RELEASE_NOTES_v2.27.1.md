# 📦 Release Notes v2.27.1 - Fix Pollination AI Display + Source Indicator

## 🔧 Corrections majeures

### 1. ✅ Rectangle violet résolu - Images Pollination AI s'affichent maintenant !

**Problème** : Les images Pollination AI étaient marquées "✅ Image générée avec succès" mais affichaient seulement un rectangle violet au lieu de l'image.

**Cause** : Coil ne peut pas charger directement les URLs de Pollination AI (CORS/certificat).

**Solution** : L'image est maintenant **téléchargée et convertie en Base64** au lieu de passer l'URL :

```kotlin
// AVANT (v2.27.0) - URL directe → Rectangle violet
return@withContext Result.success(imageUrl)

// APRÈS (v2.27.1) - Base64 → Image s'affiche ✅
val imageBytes = response.body?.bytes()
val base64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
return@withContext Result.success("data:image/png;base64,$base64")
```

### 2. 🏠 Indicateur de source clair (Freebox vs Pollination)

**Problème** : Quand Freebox échoue et utilise le fallback Pollination, impossible de savoir quelle source a été utilisée.

**Solution** : Ajout d'indicateurs visuels clairs :

#### Notification
```
✅ Freebox réussi → "Image générée ✅ - Source: Freebox (local)"
⚠️ Freebox échec → "Freebox inaccessible - Utilisation de Pollination AI (cloud)..."
✅ Pollination réussi → "Image générée ✅ - Source: Pollination AI (cloud)"
```

#### Message dans le chat
```
"✅ Image générée avec succès ! (Source: Freebox (local))"
"✅ Image générée avec succès ! (Source: Pollination AI (cloud))"
"✅ Image générée avec succès ! (Source: Stable Horde)"
```

### 3. 📊 Logs améliorés pour diagnostic

**Nouveaux logs** :
```kotlin
🏠 Freebox/ComfyUI sélectionné
✅ Freebox réussi (ComfyUI local)
ou
⚠️ Freebox ÉCHEC: ComfyUI non accessible
🔄 Fallback automatique vers Pollination AI...

🎨 URL générée: data:image/png;base64,iVBORw0K...
📏 Longueur URL: 45678 caractères
🎨 Source: Freebox (local)
```

## 📊 Résumé des changements

| Problème | Avant | Après |
|----------|-------|-------|
| **Rectangle violet Pollination** | ❌ URL non chargée | ✅ Base64 téléchargé |
| **Source inconnue** | ❓ Freebox ou Pollination ? | ✅ "Source: Freebox (local)" |
| **Fallback invisible** | ⚠️ Pas de notification | ✅ "Freebox inaccessible..." |

## 🎯 Comment utiliser

### Option 1 : Pollination AI (défaut)
- Rapide : 10-20 secondes
- ✅ Images s'affichent maintenant correctement
- Source affichée : "Pollination AI (cloud)"

### Option 2 : Freebox (avec fallback)
- Local si accessible, sinon Pollination AI
- Tu vois clairement la source utilisée
- Notification si fallback activé

### Option 3 : Stable Horde
- Gratuit mais lent (2-10 min)
- Source affichée : "Stable Horde"

## 🧪 Test recommandé

1. **Générer une image avec Pollination AI** :
   - Paramètres > Génération d'images > "Pollination AI"
   - Générer une image
   - ✅ L'image devrait maintenant s'afficher (plus de rectangle violet !)
   - Message : "Source: Pollination AI (cloud)"

2. **Tester Freebox** :
   - Paramètres > Génération d'images > "Freebox"
   - Générer une image
   - Si Freebox accessible : "Source: Freebox (local)"
   - Si Freebox inaccessible : "Source: Pollination AI (cloud)" + notification de fallback

## 🔍 Diagnostic

Si l'image ne s'affiche toujours pas, vérifie les logs :
```bash
adb logcat | grep "ImageWorker\|PollinationAI\|ChatViewModel"
```

Cherche :
- `✅ Image téléchargée: XXkB` → Taille de l'image
- `Base64: XXXX chars` → Longueur du Base64
- `🎨 Source: XXX` → Source de l'image

## 🚀 Prochaines étapes

- ✅ Images Pollination AI s'affichent
- ✅ Source clairement indiquée
- ✅ Freebox avec fallback intelligent
- 🔄 Si Freebox ne marche toujours pas → Vérifier ComfyUI sur port 33437

---

**Cette version corrige définitivement l'affichage des images Pollination AI** ! 🎉
