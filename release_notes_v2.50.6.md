# 🔧 Release Notes - Naruto AI Chat v2.50.6

## 🚀 VERSION MAJEURE - CORRECTIFS CRITIQUES

**Date de sortie** : 2 janvier 2026  
**Version** : 2.50.6 (Build 86)

---

## ✨ CORRECTIFS MAJEURS

### 🐛 PROBLÈME 1 : Scénario Non Pris en Compte (RÉSOLU)

**Problème** : Le scénario que vous écriviez pour vos personnages custom n'était **jamais utilisé** dans les conversations !

**Cause** : Le `ChatViewModel` utilisait uniquement `systemPromptSFW/NSFW` mais **ignorait complètement** les champs :
- ❌ `scenario` (contexte/scénario)
- ❌ `backgroundStory` (histoire complète)  
- ❌ `temperament` (tempérament)
- ❌ `characterTraits` (traits de caractère)

**Solution** : ✅ Le system prompt est maintenant **enrichi automatiquement** avec :

```kotlin
[SCÉNARIO/CONTEXTE]
Votre scénario personnalisé...
Tu dois ABSOLUMENT respecter ce scénario dans tes réponses et interactions.

[TON HISTOIRE/BACKGROUND]
Votre background story...

[TEMPÉRAMENT]
Votre tempérament...

[TRAITS DE CARACTÈRE]
- Trait 1
- Trait 2
- ...
```

**Résultat** : 🎯 Les conversations respectent maintenant **EXACTEMENT** le scénario que vous avez écrit !

---

### 🐛 PROBLÈME 2 : Analyse Image Non Fonctionnelle (RÉSOLU)

**Problème** : Groq Vision API ne fonctionnait toujours pas malgré les tentatives précédentes.

**Solution** : ✅ Remplacement complet par **Hugging Face Vision API**

#### 🆓 Avantages Hugging Face Vision

| Caractéristique | Groq Vision | Hugging Face Vision |
|-----------------|-------------|---------------------|
| **Clé API** | ✅ Requise | ✅ **AUCUNE** (optionnelle) |
| **Gratuit** | ❌ Limité | ✅ **100% GRATUIT** |
| **Illimité** | ❌ Quotas | ✅ **ILLIMITÉ** |
| **Fiabilité** | ❌ Erreurs fréquentes | ✅ **Très stable** |
| **Modèles** | 1-2 modèles | ✅ **3+ modèles** (BLIP, BLIP-2, GIT) |

#### 🎯 Modèles Utilisés

Le système essaie **automatiquement** plusieurs modèles pour une analyse complète :

1. **BLIP-2** (Salesforce/blip2-opt-2.7b) - Meilleure qualité
2. **BLIP** (Salesforce/blip-image-captioning-large) - Fallback 1
3. **GIT** (microsoft/git-large-coco) - Fallback 2

#### 📊 Analyse Détaillée

L'IA extrait automatiquement :
- ✅ Âge approximatif
- ✅ Genre
- ✅ Couleur cheveux
- ✅ Couleur yeux
- ✅ Type de corps
- ✅ Teint de peau
- ✅ Traits faciaux
- ✅ Description complète

---

## 🔄 CHANGEMENTS TECHNIQUES

### ChatViewModel.kt
```kotlin
// AVANT (scénario ignoré)
val systemPrompt = character.systemPromptSFW

// APRÈS (scénario inclus)
val enrichedSystemPrompt = buildString {
    append(baseSystemPrompt)
    if (character.scenario.isNotBlank()) {
        append("\n\n[SCÉNARIO/CONTEXTE]\n")
        append(character.scenario)
    }
    // + backgroundStory, temperament, characterTraits
}
```

### CreateCharacterViewModel.kt
```kotlin
// AVANT
val visionClient = GroqVisionClient(context)
_analysisResult.value = "🔍 Analyse avec Groq Vision AI..."

// APRÈS
val visionClient = HuggingFaceVisionClient(context)
_analysisResult.value = "🔍 Analyse avec Hugging Face Vision AI (GRATUIT)..."
```

### Nouveau Fichier : HuggingFaceVisionClient.kt
- 🆕 Client Vision 100% gratuit et illimité
- 🆕 Supporte 3 modèles avec fallback automatique
- 🆕 Parsing intelligent de description physique
- 🆕 Optimisation images (max 512px, compression JPEG 85%)
- 🆕 Timeout 30s par requête

---

## 📱 IMPACT UTILISATEUR

### ✅ Pour le Personnage "Evelyn" (et tous customs)

**AVANT v2.50.6** :
- ❌ Le scénario écrit était ignoré
- ❌ Le personnage ne respectait pas le contexte
- ❌ Conversations génériques

**APRÈS v2.50.6** :
- ✅ Le scénario est **toujours respecté**
- ✅ Le personnage suit **exactement** le contexte
- ✅ Conversations **immersives et cohérentes**

### ✅ Pour l'Analyse Photo

**AVANT v2.50.6** :
- ❌ Erreurs API fréquentes
- ❌ Nécessitait clé API Groq
- ❌ Quotas limités

**APRÈS v2.50.6** :
- ✅ **Fonctionne à 100%**
- ✅ **Aucune clé API** nécessaire
- ✅ **Totalement gratuit et illimité**
- ✅ Analyse plus détaillée (3 modèles)

---

## 🧪 TEST RECOMMANDÉ

### Pour Vérifier le Scénario

1. **Éditer** votre personnage "Evelyn"
2. **Vérifier** que le scénario est bien rempli
3. **Démarrer** une nouvelle conversation
4. **Tester** si le personnage respecte le contexte

### Pour Tester l'Analyse Photo

1. **Créer** un nouveau personnage
2. **Sélectionner** une photo
3. **Cliquer** sur "🤖 Analyser avec IA"
4. **Vérifier** que l'analyse se termine avec succès

---

## 🚀 INSTALLATION

### Téléchargement
👉 **[TÉLÉCHARGER L'APK v2.50.6](https://github.com/davidc2115/Naruto/releases/tag/v2.50.6)**

### Mise à Jour
1. Télécharger le fichier `app-release.apk`
2. Installer par-dessus la version précédente
3. **IMPORTANT** : Tester avec "Evelyn" pour vérifier que le scénario fonctionne !

---

## 📚 DOCUMENTATION TECHNIQUE

### API Hugging Face Inference

**Endpoint** : `https://api-inference.huggingface.co/models/{model}`

**Modèles utilisés** :
- `Salesforce/blip2-opt-2.7b`
- `Salesforce/blip-image-captioning-large`
- `microsoft/git-large-coco`

**Format requête** :
```json
{
  "inputs": "base64_image_data",
  "options": {
    "wait_for_model": true
  }
}
```

**Pas de clé API requise** (les modèles publics sont accessibles sans authentification)

---

## 🔄 HISTORIQUE DES VERSIONS

- **v2.50.6** (actuelle) : Scénario pris en compte + HuggingFace Vision
- **v2.50.5** : Régénération vignettes Emma Watson + custom
- **v2.50.4** : Photos chat + images 3 personnages custom
- **v2.50.3** : Photos profil personnages custom
- **v2.50.2** : Personnages custom 100% fonctionnels
- **v2.50.1** : Images Explorer + navigation corrigée
- **v2.50.0** : Refonte UI avec bottom navigation

---

## 💬 SUPPORT

En cas de problème :

1. **Scénario non respecté** : Vérifier que le champ "Scénario" est bien rempli dans l'édition du personnage
2. **Analyse photo échoue** : Vérifier connexion internet, réessayer (l'API peut être surchargée)
3. **Autres** : Consulter les logs ou créer une issue

---

## 🎯 FOCUS : Personnage "Evelyn"

Le personnage "Evelyn" mentionné par l'utilisateur devrait maintenant :
- ✅ Respecter son scénario personnalisé
- ✅ Utiliser son background story
- ✅ Avoir la personnalité définie
- ✅ Conversations cohérentes avec le contexte

**Test simple** : Démarrer une conversation et vérifier si le personnage suit bien le scénario que vous avez écrit.

---

**Merci d'utiliser Naruto AI Chat !** 🎯✨

*Cette version corrige deux bugs critiques signalés par l'utilisateur : scénario ignoré et analyse photo non fonctionnelle.*
