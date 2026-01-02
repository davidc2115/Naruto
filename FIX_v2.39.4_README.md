# 🔧 Fix v2.39.4 - Groq Vision API Model Update

## 🚨 Problème résolu

L'analyse d'image lors de la création de personnages échouait avec l'erreur :

```
Erreur API Groq Vision: HTTP 400
Model 'llama-3.2-90b-vision-preview' has been decommissioned
```

## ✅ Solution

**Système de fallback automatique** entre 3 modèles vision :

1. 🥇 `llama-3.2-90b-vision-instruct` (principal)
2. 🥈 `llama-3.2-11b-vision-preview` (alternative)
3. 🥉 `llava-v1.5-7b-4096-preview` (fallback)

## 📦 Fichiers modifiés

```
✅ GroqVisionClient.kt      - Logique de fallback intelligente
✅ build.gradle.kts         - Version 2.38.0 → 2.39.4
✅ GROQ_API_SETUP.md        - Documentation mise à jour
✅ RELEASE_NOTES_v2.31.0.md - Avertissement ajouté
📄 release_notes_v2.39.4.md - Notes de version complètes
📄 SUMMARY_v2.39.4.md       - Résumé technique
```

## 🎯 Impact

- ✅ **0 action requise** par l'utilisateur
- ✅ **Résilience** face aux modèles décommissionnés
- ✅ **Logs détaillés** pour le débogage
- ✅ **Performance** maintenue

## 🧪 Pour tester

```bash
# Compiler et installer
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Voir les logs
adb logcat | grep "GroqVision"
```

Dans l'app :
1. Créer un personnage
2. Ajouter une photo
3. Cliquer "Analyser la photo"
4. ✅ Succès !

## 📚 Documentation

- **[release_notes_v2.39.4.md](./release_notes_v2.39.4.md)** - Notes de version détaillées
- **[SUMMARY_v2.39.4.md](./SUMMARY_v2.39.4.md)** - Résumé technique complet
- **[GROQ_API_SETUP.md](./GROQ_API_SETUP.md)** - Guide de configuration

## 🔗 Liens

- [Groq Deprecations](https://console.groq.com/docs/deprecations)
- [Groq Console](https://console.groq.com)

---

**Version :** 2.39.4  
**Build :** 68  
**Date :** 2 janvier 2026  
**Branche :** cursor/api-model-error-fix-50fb
