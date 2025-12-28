# 🖼️ Naruto AI Chat v2.13.0 - FIX GALERIES NSFW

## 🎯 FIX CRITIQUE: Galeries NSFW Maintenant Fonctionnelles !

Cette version corrige un **bug critique** qui empêchait l'affichage des images NSFW dans les galeries des personnages.

### Problème Résolu

#### Avant v2.13.0 ❌
- Les galeries NSFW apparaissaient vides (boîtes grises)
- `getIdentifier()` retournait `0` pour toutes les ressources
- Images présentes dans l'APK mais inaccessibles

#### Après v2.13.0 ✅
- **Reflection dynamique** sur `R.drawable` pour accès direct
- Fallback intelligent : `getIdentifier()` → `Class.forName()` → reflection
- Logs de debug pour diagnostic précis
- **20 images NSFW maintenant visibles** (Naruto 15 + Sasuke 5)

### Comment Tester

1. Sélectionnez **Naruto** ou **Sasuke**
2. Allez dans **Profil** du personnage
3. Activez le toggle **NSFW** (en haut)
4. Les images s'affichent maintenant dans la grille 2 colonnes
5. Cliquez sur une image pour l'agrandir en plein écran

## 🔧 Détails Techniques

### Méthode de Chargement (Nouvelle)

```kotlin
val imageModel = if (imageUri.startsWith("drawable://")) {
    val fileName = imageUri.removePrefix("drawable://").removeSuffix(".jpg")
    
    // Tentative 1: getIdentifier()
    var resId = context.resources.getIdentifier(fileName, "drawable", context.packageName)
    
    // Tentative 2: Reflection si échec
    if (resId == 0) {
        try {
            val rDrawable = Class.forName("${context.packageName}.R\$drawable")
            val field = rDrawable.getField(fileName)
            resId = field.getInt(null)
        } catch (e: Exception) {
            Log.e("CharacterProfile", "Failed: ${e.message}")
        }
    }
    
    if (resId != 0) resId else imageUri
} else {
    imageUri
}
```

### Pourquoi Reflection ?

Android impose des restrictions sur les noms de ressources :
- ❌ Pas de `.` dans les noms → `narutonsfw1.jpg` invalide
- ✅ Les fichiers `.jpg` dans `drawable-nodpi` sont compilés sans extension
- ✅ `R.drawable.narutonsfw1` existe mais `getIdentifier("narutonsfw1")` échoue parfois
- ✅ Reflection accède directement au champ `R.drawable.narutonsfw1`

### Logs de Debug

Les logs suivants sont maintenant disponibles pour diagnostic :

```
D/CharacterProfile: Loading NSFW: narutonsfw1 -> resId=2131165312
D/CharacterProfile: Loading NSFW: narutonsfw2 -> resId=2131165313
E/CharacterProfile: Failed to load sakuransfw1: field not found
```

## 📋 Contenu Actuel

### Images NSFW Disponibles (20/195)

#### Naruto Uzumaki (15 images) ✅
- `narutonsfw1.jpg` → `narutonsfw15.jpg`
- Images sensuel (1-5), sexy (6-10), explicit (11-15)
- **Toutes visibles dans la galerie NSFW**

#### Sasuke Uchiha (5 images) ✅
- `sasukensfw1.jpg` → `sasukensfw5.jpg`
- Images sensuel (1-5)
- **Toutes visibles dans la galerie NSFW**

#### Autres Personnages (0 images) ⏳
- Sakura, Kakashi, Hinata, Itachi, Brad Pitt, Leonardo DiCaprio, The Rock, Scarlett Johansson, Margot Robbie, Emma Watson, Zendaya
- Galeries configurées mais images en cours de génération
- **À venir dans v2.14.0+**

## 🐛 Problème Connu: Génération Massive Images

### Pollination AI Rate Limits

La génération des **175 images restantes** (13 personnages × 15 - 20 déjà générées) est bloquée par :

1. **HTTP 429 (Rate Limit)** :
   - API gratuite impose limites strictes
   - Délai nécessaire : 120s entre chaque image
   - Durée totale estimée : ~6 heures

2. **HTTP 500/502 (Server Errors)** :
   - Serveurs Pollination AI surchargés
   - Échecs fréquents même avec retry × 5

### Solutions Alternatives (v2.14.0+)

1. **Service Payant** :
   - Stable Diffusion API
   - Midjourney API
   - Replicate.com

2. **Génération Locale** :
   - Stable Diffusion WebUI
   - ComfyUI
   - Automatic1111

3. **Génération Manuelle** :
   - Upload images via PR GitHub
   - Contribution communautaire

## 🎮 Fonctionnalités Complètes (depuis v2.12.0)

### 💾 Sauvegarde Conversations
- ✅ Sauvegarde automatique après chaque message
- ✅ Persistance locale (SharedPreferences + Gson)
- ✅ Boutons "Reprendre" / "Nouveau" dans profils
- ✅ Bouton 🔄 "Nouvelle conversation" dans chat
- ✅ Mode NSFW sauvegardé avec messages

### 📷 Génération Images/Vidéos
- ✅ Délai optimisé (2s au lieu de 10s)
- ✅ Vérification complète (GET + validation taille)
- ✅ Retry intelligent (5× avec backoff exponentiel)
- ⚠️ Pollination AI instable (429/500 fréquents)

### 👤 Profil Utilisateur
- ✅ Pseudo, âge, genre, bio
- ✅ Personnalisation conversations IA
- ✅ Contexte injecté dans prompts

### 🔞 Mode NSFW
- ✅ Toggle dans profils personnages
- ✅ Galeries NSFW distinctes (SFW/NSFW)
- ✅ Images maintenant visibles (v2.13.0 fix)
- ✅ Génération images NSFW possible

## 📱 Installation

### Téléchargement
```bash
# Via GitHub CLI
gh release download v2.13.0 -p "*.apk"

# Ou via navigateur
https://github.com/mel805/naruto-ai-chat/releases/tag/v2.13.0
```

### Configuration Requise
- Android 7.0+ (API 24+)
- 25 MB espace libre
- Connexion Internet (génération images)

### Permissions
- ✅ INTERNET : API Groq + Pollination AI
- ✅ Pas d'accès stockage externe
- ✅ SharedPreferences pour conversations

## 🔜 Roadmap v2.14.0

### Court Terme
1. **Générer 175 images NSFW** :
   - Finaliser tous les personnages (15 images chacun)
   - Alternative service payant
   - Qualité hyper-réaliste garantie

2. **Optimiser Chargement** :
   - Pre-cache images drawable
   - Lazy loading intelligent
   - Placeholder animé

### Moyen Terme (v2.15.0)
1. **Export/Import Conversations** :
   - Backup cloud optionnel
   - Partage entre appareils
   - Format JSON portable

2. **Historique Multi-Conversations** :
   - Plusieurs conversations par personnage
   - Timeline avec dates
   - Recherche dans historique

## 🐞 Debug & Support

### Vérifier Logs
Si les galeries NSFW sont encore vides :

1. Activer logs Android :
```bash
adb logcat | grep "CharacterProfile"
```

2. Chercher :
```
Loading NSFW: narutonsfw1 -> resId=0  # Échec
Loading NSFW: narutonsfw1 -> resId=2131165312  # Succès
```

3. Si `resId=0` persiste :
   - Réinstaller l'APK
   - Vider cache app
   - Redémarrer appareil

### Ouvrir Issue GitHub
Incluez :
- Version Android
- Device model
- Logs `adb logcat`
- Screenshots galeries vides

---

**Taille APK** : 22 MB  
**Version Code** : 23  
**Build** : Release signé  
**Compatibilité** : Android 7.0+ (API 24+)
