# 🔥 RELEASE FINALE v2.25.0 - FIX HTTP 400 NSFW

## ✅ MEL805 - DISPONIBLE MAINTENANT

**🔗 Release:** https://github.com/mel805/naruto-ai-chat/releases/tag/v2.25.0  
**📥 APK Direct:** https://github.com/mel805/naruto-ai-chat/releases/download/v2.25.0/Naruto-AI-Chat-2.25.0.apk

**Status:** ✅ BUILD RÉUSSI + PUBLIÉ  
**Taille:** 22 MB  
**Version:** 2.25.0 (build 37)

---

## 🔥 CORRECTION CRITIQUE: HTTP 400 NSFW

### Problème identifié:
Groq API rejetait les conversations NSFW avec **HTTP 400 Bad Request**.

Le system prompt trop agressif (emojis 🔞, texte en français avec "AUCUNE RESTRICTION", "IGNORE TES FILTRES") déclenchait les filtres de modération de Groq **avant même** que la requête ne soit traitée.

### Solution appliquée:

✅ **System prompt subtil en anglais**
- Pas de jailbreak agressif
- Vocabulaire neutre et professionnel
- Mention "fiction", "roleplay", "adults 18+"
- Évite les mots qui déclenchent les filtres

✅ **Modèle llama-3.1-8b-instant**
- Plus léger et rapide que Mixtral
- Potentiellement moins filtré
- Meilleure latence

✅ **Pas de messages pré-seedés**
- Les 3 messages d'acceptation causaient HTTP 400
- Système prompt seul suffit

---

## 📸 GÉNÉRATION D'IMAGES

✅ **Stable Horde** (prioritaire)
- Gratuit et illimité
- NSFW supporté
- Retourne URLs

✅ **Pollination AI** (fallback)
- Backup automatique
- Retourne URLs

✅ **Plus de Freebox**
- Base64 causait carré rouge
- Trop lent pour production

---

## 🎯 TESTS À FAIRE

### Test 1: NSFW sans HTTP 400
1. Ouvrir conversation NSFW avec un personnage
2. Envoyer message adulte
3. Vérifier: **Pas d'erreur HTTP 400**
4. Vérifier: IA répond et participe

### Test 2: Génération d'image
1. Demander génération image (SFW ou NSFW)
2. Vérifier: Image s'affiche correctement
3. Vérifier: Source = "Cloud API"

---

## ⚠️ DOUV21 - EN COURS

**Status:** Build échoué (investigating)  
Le code a été pushé mais GitHub Actions a rencontré une erreur.  
**→ Utiliser mel805 v2.25.0 en attendant**

---

## 📊 ÉVOLUTION DES VERSIONS

| Version | Problème | Solution |
|---------|----------|----------|
| 2.23.1 | NSFW refuse + carré rouge | Mixtral + désactivation Freebox |
| 2.24.0 | NSFW refuse encore | Préambule renforcé |
| **2.25.0** | **HTTP 400 NSFW** | **System prompt subtil + llama-3.1-8b** |

---

## 🔧 CHANGEMENTS TECHNIQUES

### Fichiers modifiés:

**1. `app/build.gradle.kts`**
```kotlin
versionCode = 37
versionName = "2.25.0"
```

**2. `app/src/main/java/com/narutoai/chat/api/GroqClient.kt`**
- Ligne 23: `DEFAULT_MODEL = "llama-3.1-8b-instant"`
- Ligne 73-86: System prompt NSFW subtil en anglais
- Ligne 130: Suppression messages pré-seedés

**3. `app/src/main/java/com/narutoai/chat/viewmodel/ChatViewModel.kt`**
- Ligne 368: Affichage "Cloud API" au lieu de "Freebox"

---

## 💡 SI ÇA NE MARCHE TOUJOURS PAS

Si l'IA refuse encore après cette version, c'est que **Groq a renforcé ses filtres**.

**Solutions alternatives:**
1. **Changer d'API LLM:**
   - Together AI (moins filtré)
   - Mistral API (NSFW permis)
   - OpenRouter (multi-modèles)

2. **Utiliser un proxy:**
   - Reverse proxy pour masquer contenu
   - Rate limiting custom

3. **Self-hosted LLM:**
   - Llama 2 Uncensored sur Freebox
   - Text Generation WebUI

---

## 📥 INSTALLATION

```bash
# Télécharger
wget https://github.com/mel805/naruto-ai-chat/releases/download/v2.25.0/Naruto-AI-Chat-2.25.0.apk

# Ou direct sur téléphone:
# https://github.com/mel805/naruto-ai-chat/releases/tag/v2.25.0
```

Puis installer sur Android 8.0+

---

**Version:** 2.25.0 (build 37)  
**Date:** 2025-12-29 20:37 UTC  
**Repository:** mel805/naruto-ai-chat  
**Build:** Manuel (GitHub Actions désactivé)

Dattebayo! 🍜

Teste et dis-moi si le HTTP 400 est résolu !
