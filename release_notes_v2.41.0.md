# 🚀 Release Notes v2.41.0

## 🔥 CHANGEMENT MAJEUR : Migration vers Google Gemini Vision

### ❌ Problème identifié
**TOUS les modèles Groq Vision sont décommissionnés** depuis janvier 2025 :
- ❌ `llama-3.2-90b-vision-preview` (décommissionné v2.39.4)
- ❌ `llama-3.2-90b-vision-instruct` (décommissionné)
- ❌ `llama-3.2-11b-vision-preview` (décommissionné)
- ❌ `llava-v1.5-7b-4096-preview` (décommissionné)

**Résultat** : L'analyse d'images lors de la création de personnages ne fonctionnait plus du tout.

---

## ✅ Solution : Google Gemini Vision API

### 🎯 Nouvelle API d'analyse d'images
Remplacement complet de Groq Vision par **Google Gemini Vision** :

#### ✨ Avantages
- ✅ **100% GRATUIT** avec quota généreux
- ✅ **60 requêtes/minute** (très confortable)
- ✅ **1500 requêtes/jour** (largement suffisant)
- ✅ **Modèle actif** : `gemini-1.5-flash-latest`
- ✅ **Plus performant** : Analyse plus précise et détaillée
- ✅ **Plus permissif** : Accepte images jusqu'à 4MB (vs 500KB pour Groq)
- ✅ **Plus stable** : API officielle Google activement maintenue

#### 🔧 Configuration simple
1. **Obtenir une clé gratuite** : [https://makersuite.google.com/app/apikey](https://makersuite.google.com/app/apikey)
2. **Ajouter dans les Paramètres** : Section "Google Gemini Vision"
3. **C'est prêt** : L'analyse fonctionne immédiatement !

---

## 🛠️ Modifications techniques

### 📝 Fichiers créés/modifiés
1. **`GeminiVisionClient.kt`** (NOUVEAU) : Client complet pour Google Gemini Vision API
   - Analyse d'images avec prompt structuré
   - Compression intelligente (jusqu'à 4MB)
   - Parsing JSON robuste
   - Gestion d'erreurs détaillée

2. **`CreateCharacterViewModel.kt`** : Passage de Groq à Gemini
   - Remplacement de `GroqVisionClient` par `GeminiVisionClient`
   - Messages mis à jour ("Google Gemini Vision")
   - Logs détaillés pour debugging

3. **`SettingsScreen.kt`** : Nouvelle section configuration
   - Interface dédiée pour clé Gemini
   - Bouton direct vers obtention de clé
   - Affichage sécurisé (masquage clé)
   - Mise à jour infos API

---

## 🎨 Fonctionnalités inchangées

### ✅ Conservation de Groq pour le chat
- **Groq reste utilisé** pour les conversations IA (inchangé)
- **Groq Vision supprimé** uniquement pour l'analyse d'images
- **Rotation des clés** : Toujours active pour Groq Chat

### ✅ Analyse physique identique
L'analyse retourne toujours les mêmes informations :
- Âge estimé
- Genre
- Couleur cheveux/yeux
- Teint de peau
- Type de corps
- Taille estimée
- Traits du visage
- Signes distinctifs
- Description complète

---

## 📊 Comparaison Groq Vision vs Gemini Vision

| Critère | Groq Vision (OLD) | Gemini Vision (NEW) |
|---------|-------------------|---------------------|
| **Statut** | ❌ Tous décommissionnés | ✅ Actif et maintenu |
| **Quota gratuit** | ❓ Non spécifié | ✅ 60/min, 1500/jour |
| **Taille image max** | 500 KB | 4 MB (8x plus) |
| **Qualité analyse** | Bonne | Excellente |
| **Documentation** | Limitée | Complète |
| **Support** | ❌ Fin de vie | ✅ Google officiel |

---

## 🔐 Sécurité et confidentialité

- 🔒 **Clé stockée localement** : SharedPreferences sécurisées
- 🔒 **Affichage masqué** : Clé cachée par défaut
- 🔒 **Pas de télémétrie** : Aucune donnée envoyée ailleurs
- 🔒 **HTTPS uniquement** : Communication chiffrée

---

## 📱 Guide utilisateur

### 1️⃣ Première utilisation
1. Ouvrir **Paramètres** (⚙️)
2. Section **"Google Gemini Vision"**
3. Cliquer **"Obtenir une clé gratuite"**
4. Se connecter avec compte Google
5. Copier la clé générée (format: `AIzaSy...`)
6. Coller dans l'app et **Enregistrer**

### 2️⃣ Créer un personnage
1. **"+ Créer"** depuis l'accueil
2. **Sélectionner une photo**
3. **"Analyser avec Gemini"**
4. ⏳ Analyse en cours...
5. ✅ **Champs auto-remplis** !

### 3️⃣ Résultat
L'analyse remplira automatiquement :
- Description physique complète
- Âge, taille, morphologie
- Couleurs cheveux/yeux
- Traits distinctifs

---

## 🐛 Résolution d'erreurs

### ❌ "Clé API manquante"
**Solution** : Ajouter votre clé dans Paramètres > Google Gemini Vision

### ❌ "HTTP 400"
**Cause** : Clé API invalide ou expirée  
**Solution** : Régénérer une nouvelle clé sur [makersuite.google.com](https://makersuite.google.com/app/apikey)

### ❌ "Quota dépassé"
**Cause** : Plus de 60 requêtes/minute ou 1500/jour  
**Solution** : Attendre quelques heures (reset quotidien automatique)

### ❌ "Impossible de charger l'image"
**Cause** : Format non supporté ou image corrompue  
**Solution** : Utiliser JPEG/PNG de bonne qualité

---

## 🎯 Prochaines étapes

### Fonctionnalités prévues
- [ ] **Multi-modèles Gemini** : Fallback automatique entre modèles
- [ ] **Cache d'analyse** : Éviter analyses répétées
- [ ] **Batch analysis** : Analyser plusieurs photos d'un coup
- [ ] **Fine-tuning prompts** : Améliorer précision selon types de personnages

---

## 📚 Ressources

- **API Gemini Docs** : https://ai.google.dev/gemini-api/docs/vision
- **Obtenir clé gratuite** : https://makersuite.google.com/app/apikey
- **Limites quotas** : https://ai.google.dev/gemini-api/docs/pricing
- **Code source** : `app/src/main/java/com/narutoai/chat/api/GeminiVisionClient.kt`

---

## 🙏 Migration sans interruption

**Bonne nouvelle** : Migration 100% transparente !
- ✅ Aucune perte de données
- ✅ Personnages existants conservés
- ✅ Clés Groq Chat fonctionnent toujours
- ✅ Groq Vision simplement remplacé

**Seule action requise** : Ajouter une clé Gemini gratuite (30 secondes)

---

## ✨ En résumé

| Avant (v2.40.1) | Après (v2.41.0) |
|-----------------|-----------------|
| ❌ Groq Vision décommissionné | ✅ Gemini Vision actif |
| ❌ Analyse ne fonctionne plus | ✅ Analyse parfaitement fonctionnelle |
| ⚠️ Quota flou | ✅ 60/min, 1500/jour clairs |
| 📦 500KB max | 📦 4MB max |

---

**🎉 Profitez de l'analyse d'images améliorée et 100% GRATUITE ! 🎉**

---

_Version 2.41.0 - Build 71 - Janvier 2025_
