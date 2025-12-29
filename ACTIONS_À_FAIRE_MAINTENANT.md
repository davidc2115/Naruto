# 🎯 ACTIONS À FAIRE MAINTENANT

**Status**: ✅ Tout le code est prêt et poussé !  
**Problème**: Rate limit GitHub API (trop de requêtes aujourd'hui)

---

## ⚡ OPTION 1: MERGER DANS MAIN (5 MINUTES) ⭐

**LA PLUS RAPIDE** pour avoir v2.23.1 immédiatement !

```bash
# Sur ton PC local, dans ton repo naruto-ai-chat
git fetch origin
git checkout main
git merge origin/cursor/freebox-stable-diffusion-setup-335a
git push origin main

# Ça va automatiquement déclencher le build v2.23.1 ! ✅
```

**Ensuite**:
1. Va sur https://github.com/mel805/naruto-ai-chat/actions
2. Attends ~5 minutes que le build termine
3. Télécharge l'APK depuis les artifacts
4. Installe sur ton téléphone !

---

## ⚙️ OPTION 2: DÉCLENCHER MANUELLEMENT (10 MINUTES)

Si tu veux garder la branche séparée:

1. Va sur https://github.com/mel805/naruto-ai-chat/actions
2. Clique sur le workflow "Android CI"
3. Clique "Run workflow" (bouton bleu à droite)
4. Choisis `cursor/freebox-stable-diffusion-setup-335a`
5. Clique "Run workflow"

**Résultat**: Build v2.23.1 se lance immédiatement

---

## 📦 OPTION 3: PUBLIER LES APK DÉJÀ BUILDÉS (1 HEURE)

Tu as **3 APK prêts** mais pas encore sur les releases:
- ✅ v2.18.0 (22 MB)
- ✅ v2.19.0 (22 MB)
- ✅ v2.20.0 (22 MB)

**Attends 1 heure** (rate limit GitHub API expire), puis lance:

```bash
cd /workspace

# v2.18.0
gh release create v2.18.0 \
  --title "v2.18.0 - Fix NSFW + UI Clavier + Vitesse 3x" \
  --notes-file RELEASE_NOTES_v2.18.0.md \
  /tmp/apks/v2.18/naruto-ai-chat-apk/Naruto-AI-Chat-v2.18.0.apk

# v2.19.0
gh release create v2.19.0 \
  --title "v2.19.0 - Vidéo SFW/NSFW + ComfyUI Optimisé 🎬" \
  --notes-file RELEASE_NOTES_v2.19.0.md \
  /tmp/apks/v2.19/naruto-ai-chat-apk/Naruto-AI-Chat-v2.19.0.apk

# v2.20.0
gh release create v2.20.0 \
  --title "v2.20.0 - FIX NSFW ULTRA-RENFORCÉ 🔞" \
  --notes-file RELEASE_NOTES_v2.20.0.md \
  /tmp/apks/v2.20/naruto-ai-chat-apk/Naruto-AI-Chat-v2.20.0.apk
```

---

## 🌐 OPTION 4: VIA GITHUB WEB UI (15 MINUTES)

Si tu préfères l'interface graphique:

### Pour chaque version (v2.18, v2.19, v2.20):
1. Va sur https://github.com/mel805/naruto-ai-chat/releases/new
2. **Tag**: Choisis le tag existant (v2.18.0, v2.19.0, ou v2.20.0)
3. **Title**: Copie depuis `RELEASE_NOTES_vX.Y.Z.md` (première ligne)
4. **Description**: Copie tout le contenu du fichier `RELEASE_NOTES_vX.Y.Z.md`
5. **Attach binaries**: Upload l'APK depuis `/tmp/apks/vX.Y/naruto-ai-chat-apk/Naruto-AI-Chat-vX.Y.0.apk`
6. Clique "Publish release"

---

## 🎯 VERSION RECOMMANDÉE: v2.23.1

**Pourquoi ?**
- ✅ **Stable Horde** (gratuit, illimité, NSFW)
- ✅ **Fallback Pollination AI** automatique
- ✅ **32 images NSFW** incluses dans l'APK
- ✅ **Choix d'API** (actuellement hardcodé, UI à venir v2.24)
- ✅ **Tous les fix NSFW** des versions précédentes

**Comment l'avoir ?**
→ **OPTION 1** (merge dans main) est la plus rapide !

---

## 📊 RÉCAPITULATIF

| Version | Code | APK | Release | Recommandation |
|---------|------|-----|---------|----------------|
| v2.18.0 | ✅ Poussé | ✅ Prêt | ⏳ À publier | Bonne |
| v2.19.0 | ✅ Poussé | ✅ Prêt | ⏳ À publier | Bonne |
| v2.20.0 | ✅ Poussé | ✅ Prêt | ⏳ À publier | Bonne |
| v2.21.0 | ✅ Poussé | ❌ Cancelled | ❌ | Skip |
| v2.22.0 | ✅ Poussé | 🔄 À builder | ❌ | Skip |
| v2.23.0 | ✅ Poussé | 🔄 À builder | ❌ | Bonne |
| **v2.23.1** | ✅ Poussé | 🔄 **À builder** | ❌ | **⭐ RECOMMANDÉ** |

---

## 🔗 LIENS UTILES

- **Releases**: https://github.com/mel805/naruto-ai-chat/releases
- **Actions**: https://github.com/mel805/naruto-ai-chat/actions
- **Branche dev**: https://github.com/mel805/naruto-ai-chat/tree/cursor/freebox-stable-diffusion-setup-335a

---

## ❓ QUESTIONS FRÉQUENTES

### Pourquoi les builds v2.21/v2.22/v2.23 ne se sont pas déclenchés ?
Les workflows GitHub Actions ne se déclenchent que sur `main`. La branche `cursor/...` n'a pas déclenché les builds automatiquement. **Solution**: Merge dans `main` (OPTION 1).

### Les APK v2.18/v2.19/v2.20 sont-ils disponibles longtemps ?
Non, les artifacts GitHub expirent après **90 jours**. Publie-les sur les releases rapidement !

### Quelle est la différence entre v2.23.0 et v2.23.1 ?
- **v2.23.0**: Stable Horde intégré + 32 images NSFW
- **v2.23.1**: + Choix d'API (Stable Horde ↔ Pollination) + Fallback automatique

### Stable Horde est vraiment illimité ?
Oui ! C'est un réseau décentralisé de GPU bénévoles. Queue possible aux heures de pointe (30s-5min), mais **100% gratuit et sans limite**.

---

## 🆘 EN CAS DE PROBLÈME

### "Rate limit API"
```bash
# Voir quand ça expire
gh api rate_limit --jq '.resources.core.reset | strftime("%H:%M:%S")'
```

### "Build ne se déclenche pas"
→ Merge dans `main` (OPTION 1) ou déclenche manuellement (OPTION 2)

### "APK introuvable"
→ Ils sont dans `/tmp/apks/` sur le serveur cloud (expirent dans quelques jours)

---

**🎉 TU ES À 5 MINUTES D'AVOIR v2.23.1 AVEC STABLE HORDE !**

**Choisis OPTION 1 et lance le merge maintenant** 🚀
