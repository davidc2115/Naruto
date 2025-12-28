# 🎨 Naruto AI Chat v2.15.0 - Freebox Priority & NSFW Age Fix

**Date de sortie:** 28 Décembre 2025  
**Version Code:** 25  
**Taille APK:** ~23 MB

---

## 🎯 NOUVEAUTÉS MAJEURES

### 1. 🔧 Priorité Freebox Renforcée

**Configuration optimale de la génération d'images/vidéos:**

```
┌─────────────────────────────────────┐
│   Demande Génération Image/Vidéo    │
└──────────────┬──────────────────────┘
               │
               ▼
    ┌──────────────────────┐
    │ 1️⃣ FREEBOX (PRIORITÉ) │
    │  Ping 3s timeout     │
    └──────┬───────────────┘
           │
           ├─► ✅ Accessible → FREEBOX SD WebUI
           │   • Génération locale (30-120s)
           │   • Illimité et gratuit
           │   • NSFW sans censure
           │   • Privacy 100%
           │   • Message: "✅ Image générée (Freebox)"
           │
           └─► ❌ Timeout → POLLINATION AI (Fallback)
               • Génération cloud (2-10s)
               • Gratuit avec rate limits
               • Message: "✅ Image générée (Pollination AI)"
```

**Logs améliorés pour debug:**
- `🎯 PRIORITÉ 1: Tentative génération via Freebox...`
- `✅ Freebox accessible! Génération locale en cours...`
- `⚠️ Freebox non accessible (timeout 3s)`
- `🔄 FALLBACK: Utilisation Pollination AI`
- `📍 Source: Freebox Stable Diffusion (local)` ou `Pollination AI (cloud)`

**Timeout augmenté:**
- Génération: 60s → **120s** (pour CPU Freebox)
- Plus de stabilité pour génération locale

---

### 2. ✅ Correction Âge Personnages Naruto (Mode NSFW)

**Problème corrigé:** Les personnages Naruto avaient 17 ans en mode NSFW

**Corrections effectuées:**

| Personnage | Avant | Après | Status |
|------------|-------|-------|--------|
| **Naruto Uzumaki** | ~~17 ans~~ | **18 ans (adulte)** | ✅ |
| **Sasuke Uchiha** | ~~17 ans~~ | **18 ans (adulte)** | ✅ |
| **Sakura Haruno** | ~~17 ans~~ | **18 ans (adulte)** | ✅ |
| **Hinata Hyuga** | ~~17 ans~~ | **18 ans (adulte)** | ✅ |
| Kakashi Hatake | 26 ans | 26 ans | ✅ Déjà adulte |
| Itachi Uchiha | 21 ans | 21 ans | ✅ Déjà adulte |

**Format des prompts NSFW:**
```kotlin
systemPromptNSFW = """Tu es [Personnage], 18 ans (adulte), ...
```

**IMPORTANT:** Tous les personnages en mode NSFW sont maintenant **adultes (18+)**, conformément aux règles sur le contenu adulte.

---

### 3. 📸 Détection Source Image

**Nouveau:** L'app indique maintenant clairement quelle source a généré l'image

**Dans le chat, vous verrez:**
- `✅ Image générée avec succès (Freebox)` ← Génération locale
- `✅ Image générée avec succès (Pollination AI)` ← Génération cloud

**Détection automatique:**
- `data:image/png;base64,...` = Freebox (local)
- `https://image.pollinations.ai/...` = Pollination AI (cloud)

---

## 🔧 AMÉLIORATIONS TECHNIQUES

### FreeboxMediaClient

**Fichier:** `app/src/main/java/com/narutoai/chat/api/FreeboxMediaClient.kt`

**Changements:**
- ✅ Logs explicites à chaque étape
- ✅ Priorité Freebox strictement respectée
- ✅ Timeout génération: 60s → 120s
- ✅ Détection source (Freebox vs Pollination AI)
- ✅ Messages utilisateur plus clairs

### Characters.kt

**Fichier:** `app/src/main/java/com/narutoai/chat/data/Characters.kt`

**Changements:**
- ✅ Âges corrigés: lignes 132, 281, 426, 721
- ✅ 4 personnages Naruto: 17 → 18 ans (adultes)
- ✅ Mention explicite "(adulte)" dans prompts NSFW

---

## 📋 FONCTIONNALITÉS EXISTANTES (v2.14.0)

### Mode NSFW Complet

- ✅ Conversations NSFW (systemPromptNSFW)
- ✅ Génération images NSFW
- ✅ Génération vidéos NSFW
- ✅ Sauvegarde mode NSFW avec conversation
- ✅ Galeries NSFW (20/195 images disponibles)

### Génération Média

- ✅ Images via Freebox SD WebUI (priorité)
- ✅ Images via Pollination AI (fallback)
- ✅ Vidéos/GIF via Freebox (priorité)
- ✅ Vidéos/GIF via Pollination AI (fallback)

### Personnages

- ✅ 6 personnages Naruto (Naruto, Sasuke, Sakura, Kakashi, Hinata, Itachi)
- ✅ 3 célébrités masculines (Brad Pitt, Leonardo DiCaprio, The Rock)
- ✅ 4 célébrités féminines (Scarlett, Margot, Emma, Zendaya)
- ✅ Descriptions physiques détaillées
- ✅ Background stories complets

### Conversations

- ✅ Chat avec IA via Groq (LLaMA)
- ✅ Multi-clés API Groq (rotation automatique)
- ✅ Sauvegarde auto conversations
- ✅ Mode SFW / NSFW switchable
- ✅ Historique persistant

---

## 🚀 INSTALLATION FREEBOX SD (Optionnel)

**L'app fonctionne sans Freebox** grâce au fallback Pollination AI automatique.

**Pour activer génération locale Freebox:**

1. Suivre guide: `FREEBOX_SD_WEBUI_SETUP.md`
2. SSH: `ssh -p 33000 root@88.174.155.230`
3. Installation: 30-60 minutes
4. Résultat: Génération locale illimitée

**Avantages Freebox:**
- ✅ Illimité (pas de rate limits)
- ✅ NSFW sans censure
- ✅ Privacy 100% locale
- ✅ Gratuit

**Inconvénients:**
- ⚠️ Plus lent (30-120s vs 2-10s)
- ⚠️ Installation manuelle nécessaire

---

## 📱 UTILISATION

### Mode NSFW

1. Sélectionner un personnage
2. Aller dans **Profil**
3. Activer toggle **NSFW** (en haut)
4. Commencer conversation
5. Le personnage adapte son comportement (adulte, 18+)

### Génération Images

1. Ouvrir chat avec personnage
2. Activer mode NSFW (si désiré)
3. Cliquer icône **📷** en haut
4. Choisir "Générer image"
5. L'app essaie **Freebox** (3s)
6. Si timeout → **Pollination AI** automatiquement
7. Image affichée avec source

### Source Affichée

**Regardez le message dans le chat:**
- "✅ Image générée (Freebox)" = Génération locale
- "✅ Image générée (Pollination AI)" = Génération cloud

---

## 🐛 CORRECTIONS

### Bugs Corrigés

- ✅ Âges personnages Naruto en NSFW (17 → 18 ans)
- ✅ Priorité Freebox pas toujours respectée
- ✅ Source image non indiquée
- ✅ Timeout trop court pour génération Freebox CPU

### Améliorations

- ✅ Logs plus clairs et explicites
- ✅ Messages utilisateur plus informatifs
- ✅ Détection source automatique
- ✅ Timeout adapté pour CPU

---

## ⚠️ NOTES IMPORTANTES

### Mode NSFW

- **Contenu adulte 18+ uniquement**
- Tous les personnages en mode NSFW sont **adultes (18+)**
- Respect des CGU et réglementations

### Privacy

- **Freebox:** 100% local, aucune donnée en ligne
- **Pollination AI:** Requêtes envoyées à API cloud

### Performance

- **Freebox:** 30-120s par image (CPU)
- **Pollination AI:** 2-10s par image (cloud)
- Rate limits possibles avec Pollination AI (429 errors)

---

## 📊 COMPARAISON VERSIONS

| Fonctionnalité | v2.14.0 | v2.15.0 |
|----------------|---------|---------|
| Freebox Priority | ⚠️ Basique | ✅ Renforcée |
| Logs explicites | ❌ Non | ✅ Oui |
| Détection source | ❌ Non | ✅ Oui |
| Âges NSFW | ⚠️ 17 ans | ✅ 18+ ans |
| Timeout génération | 60s | ✅ 120s |
| Messages clairs | ⚠️ Basiques | ✅ Explicites |

---

## 🔮 PROCHAINES VERSIONS

### v2.16.0 (Prévu)

- AnimateDiff pour vraies vidéos animées
- Sélection modèle SD dans settings
- ControlNet pour poses précises
- Indicateur "Freebox active/inactive" dans UI
- Progress bar génération locale

### v2.17.0 (Prévu)

- Génération galeries NSFW complètes (195 images)
- Cache images localement
- Optimisations performance
- Mode hors-ligne partiel

---

## 📖 DOCUMENTATION

**Guides fournis:**
- `REPONSE_DEMANDES.md` - Réponse simple aux demandes
- `RESUME_MODIFICATIONS.md` - Détails techniques
- `CONFIGURATION_ACTUELLE.md` - Configuration détaillée
- `FREEBOX_SD_WEBUI_SETUP.md` - Installation Freebox SD
- `CHECKLIST_FINALE.md` - Checklist visuelle

**Scripts:**
- `generate_nsfw_all_characters.py` - Génération 195 images NSFW

---

## 📦 TÉLÉCHARGEMENT

**Taille APK:** ~23 MB  
**Android requis:** 7.0+ (API 26+)  
**Permissions:** Internet, Stockage

**Installation:**
1. Télécharger APK depuis GitHub Release
2. Activer "Sources inconnues" sur Android
3. Installer l'APK
4. Ouvrir l'app et configurer clés Groq

---

## ✅ CHANGELOG DÉTAILLÉ

### Ajouts

- ✅ Logs explicites dans FreeboxMediaClient
- ✅ Détection source image (Freebox vs Pollination AI)
- ✅ Messages utilisateur avec source affichée
- ✅ Âges personnages NSFW corrigés (18+ adultes)

### Modifications

- ✅ Timeout génération: 60s → 120s
- ✅ Priorité Freebox strictement respectée
- ✅ Prompts NSFW: mention "(adulte)" explicite

### Corrections

- ✅ Personnages Naruto: 17 → 18 ans en NSFW
- ✅ Freebox pas toujours essayée en premier
- ✅ Timeout trop court pour CPU Freebox

---

**Version:** 2.15.0  
**Version Code:** 25  
**Date:** 28 Décembre 2025  
**Build:** Release signé  
**Compatibilité:** Android 7.0+ (API 26+)

**🎨 Freebox Priority + NSFW Adult Characters (18+) 🎨**

**Dattebayo!** 🍜
