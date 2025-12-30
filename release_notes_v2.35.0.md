# 🔧 Version 2.35.0 - Corrections critiques

## ✅ Correctifs critiques

### 1. 📸 Analyse photo Groq fixée
- **RÉSOLU** : Erreur "clé API Groq non configurée"
- L'analyse de photo utilise maintenant **DataStore** pour charger les clés Groq
- Compatible avec les 5 clés configurées via le gestionnaire de clés
- Debug logs améliorés pour tracer le chargement des clés

### 2. 🔞 Galeries NSFW affichées
- **RÉSOLU** : Images NSFW invisibles
- URLs modifiées pour utiliser l'**API Freebox** (port 33500)
- 21 images NSFW uploadées sur la Freebox
- Format : `http://88.174.155.230:33500/images/[nom].png`
- Galeries Sakura (8 images) et Hinata (4 images) fonctionnelles

### 3. ⚙️ Paramètres simplifiés
- **SUPPRIMÉ** : Section "Choix de l'API de génération"
- **SUPPRIMÉ** : Configuration Replicate API
- L'application utilise désormais **Pollination AI** uniquement
- Interface paramètres épurée et plus claire

## 🎯 Statut Pollination AI

✅ **Actif par défaut**
- Rapide, gratuit, illimité
- Supporte contenu NSFW
- Aucune configuration nécessaire

## 📦 Fichiers modifiés

- `GroqVisionClient.kt` - Fix chargement clés API depuis DataStore
- `Characters.kt` - URLs galeries NSFW vers Freebox
- `SettingsScreen.kt` - Suppression sections API/Replicate
- `build.gradle.kts` - Version 2.35.0 (Build 59)

## 🐛 Bugs corrigés

| Bug | Statut | Solution |
|-----|--------|----------|
| Analyse photo - Clé Groq non configurée | ✅ Corrigé | DataStore à la place de SharedPreferences |
| Galeries NSFW invisibles | ✅ Corrigé | URLs Freebox directes |
| Interface paramètres encombrée | ✅ Simplifié | Suppression choix API |

## 🚀 Mise à jour

**Recommandée** pour tous les utilisateurs
- Les personnages créés sont conservés
- Les clés API Groq sont préservées
- L'historique de chat reste intact

---

**Build**: 59  
**Date**: 30 décembre 2025  
**Taille**: ~27MB  
