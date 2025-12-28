# 🔧 Naruto AI Chat v2.15.1 - Freebox Port Update

**Date:** 28 Décembre 2025  
**Version Code:** 26

---

## 🔧 CHANGEMENT

### Port Freebox Mis à Jour

**Ancien port:** ~~7860~~  
**Nouveau port:** **33437**

**URL Freebox SD WebUI:**
- Avant: `http://88.174.155.230:7860`
- Maintenant: **`http://88.174.155.230:33437`**

---

## 📝 FICHIERS MODIFIÉS

### Code

- ✅ `FreeboxMediaClient.kt` - Port 7860 → 33437
- ✅ `generate_nsfw_freebox_v4.py` - Port 7860 → 33437

### Documentation

- ✅ `RELEASE_NOTES_v2.15.0.md` - Port mis à jour
- ✅ `CONFIGURATION_ACTUELLE.md` - Port mis à jour
- ✅ `REPONSE_DEMANDES.md` - Port mis à jour
- ✅ `FREEBOX_SD_WEBUI_SETUP.md` - Port mis à jour

---

## 🎯 FONCTIONNALITÉS (Inchangées)

Toutes les fonctionnalités de la v2.15.0 sont préservées :

- ✅ Priorité Freebox > Pollination AI
- ✅ Détection source (Freebox vs Pollination AI)
- ✅ Personnages NSFW adultes (18+)
- ✅ Logs explicites
- ✅ Timeout 120s pour génération CPU

---

## 📦 INSTALLATION

**Télécharger:** https://github.com/mel805/naruto-ai-chat/releases/tag/v2.15.1

**Configuration Freebox:**
```bash
# Pour installer SD WebUI sur Freebox avec port 33437:
ssh -p 33000 root@88.174.155.230

# Lancer SD WebUI sur port 33437
cd /root/stable-diffusion-webui
./webui.sh --listen --port 33437
```

---

## ✅ VÉRIFICATION

**Tester l'accès Freebox SD WebUI:**
```bash
curl -I http://88.174.155.230:33437
```

**L'app utilisera automatiquement:**
1. **Freebox** (port 33437) si accessible
2. **Pollination AI** sinon (fallback)

---

**Version:** 2.15.1  
**Date:** 28 Décembre 2025  
**Changement:** Port Freebox 7860 → 33437

🍜 **Dattebayo!** 🍜
