# 📡 Version 2.35.1 - Galeries NSFW réseau local

## 🔧 Hotfix galeries NSFW

### URLs modifiées pour IP locale Freebox
- **Ancienne config** : `http://88.174.155.230:33500/images/...` (IP publique - port fermé)
- **Nouvelle config** : `http://192.168.1.37:33500/images/...` (IP locale)

### ✅ Fonctionnement

Les galeries NSFW fonctionnent maintenant **sur le réseau WiFi domestique** :
- ✅ Connexion à la Freebox sur le réseau local
- ✅ API serveur actif (port 33500)
- ✅ 21 images NSFW disponibles
- ⚠️ **Nécessite connexion WiFi local** (même réseau que la Freebox)

### 📸 Images disponibles

**Sakura** : 8 images NSFW
- `sakuransfw1.png` à `sakuransfw6.png`
- `sakura_1.png`, `sakura_2.png`

**Hinata** : 4 images NSFW
- `hinatansfw1.png`, `hinatansfw3.png`
- `hinata_1.png`, `hinata_2.png`

### 🔒 Note importante

**IMPORTANT** : Les galeries NSFW sont accessibles uniquement :
1. ✅ Quand le téléphone est connecté au **même WiFi que la Freebox**
2. ✅ L'API serveur `characters-api` doit être actif (vérifié : ✅ online)

Si vous souhaitez accéder aux galeries **depuis l'extérieur** :
1. Ouvrir le port **33500** sur la Freebox (configuration NAT/PAT)
2. Remplacer `192.168.1.37` par votre IP publique `88.174.155.230` dans l'app

---

**Build**: 60  
**Date**: 30 décembre 2025  
**Taille**: ~27MB  
