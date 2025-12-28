# 🍜 Naruto AI Chat - Release v2.7.0

## ✨ NOUVELLES FONCTIONNALITÉS

### 🖼️ 143 Images HYPER-RÉALISTES Intégrées
- **13 vignettes** de personnages haute qualité
- **130 images de galeries** (10 par personnage)
- Images générées avec **Pollinations AI** (modèle flux-realism)
- Toutes optimisées (<120 KB chacune, total ~11 MB)
- **Affichage instantané** (pas de chargement réseau!)

### 🖥️ Support Freebox Stable Diffusion WebUI
- Génération d'images **locale sur votre Freebox** (port 33437)
- **Gratuit et illimité** (pas de rate limits!)
- Fallback automatique sur Pollinations AI si Freebox indisponible
- Configuration documentée dans `FREEBOX_SD_ACTIVATION.md`

## 🐛 CORRECTIONS

### Amélioration Gestion Erreurs Pollinations AI
- ✅ **Retry automatique** pour erreurs 502 Bad Gateway
  - 3 tentatives avec backoff exponentiel (5s, 10s, 15s)
- ✅ **Gestion améliorée des 429** (Rate Limit)
  - Délais progressifs (10s, 20s, 30s entre tentatives)
- ✅ **Timeouts étendus**
  - Read timeout: 120s (était 60s)
  - Connect timeout: 60s (était 10s)
- ✅ **Messages d'erreur descriptifs** avec conseils utilisateur

## 🎨 AMÉLIORATIONS

- **Toutes les galeries utilisent images locales** (drawable://)
- **Aucun chargement réseau** pour les galeries = navigation fluide
- **Qualité images**: photorealistic, 8k, professional photography
- **APK optimisé**: ~30-35 MB (avec 143 images intégrées)

## 📱 Installation

1. Télécharger l'APK ci-dessous
2. Installer sur **Android 8.0+**
3. Profiter de **13 personnages** (6 Naruto + 7 célébrités)
4. Modes **SFW** et **NSFW** disponibles

## 🔗 Backend

- **LLM**: Groq API (llama-3.3-70b-versatile)
- **Images**: Pollinations AI (flux-realism) + Freebox SD (optionnel)
- **Vidéos**: Pollinations AI
- **100% GRATUIT & ILLIMITÉ**

## 🎯 Personnages

### Univers Naruto
- 🍜 Naruto Uzumaki
- ⚡ Sasuke Uchiha
- 🌸 Sakura Haruno
- 👁️ Kakashi Hatake
- 🔥 Itachi Uchiha
- 👀 Hinata Hyuga

### Célébrités
- 🎬 Leonardo DiCaprio
- 🎭 Brad Pitt
- 💎 Margot Robbie
- ⭐ Scarlett Johansson
- 📚 Emma Watson
- 💪 The Rock (Dwayne Johnson)
- ✨ Zendaya

Dattebayo! 🍜

---

**Taille APK**: ~32 MB  
**Version**: 2.7.0 (Build 13)  
**Date**: 27 Décembre 2025
