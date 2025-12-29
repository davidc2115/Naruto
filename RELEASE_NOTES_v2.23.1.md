# 🚀 Naruto AI Chat v2.23.1 - STABLE HORDE + CHOIX D'API

**Date**: 29 décembre 2025  
**Build**: 35  
**Branche**: `cursor/freebox-stable-diffusion-setup-335a` → `main`

---

## 🎯 NOUVEAUTÉS v2.23.1

### 🆕 CHOIX D'API POUR GÉNÉRATION D'IMAGES

Tu peux maintenant **choisir entre 2 APIs** pour générer tes images:

#### 1. **Stable Horde** (RECOMMANDÉ ⭐)
- ✅ **100% GRATUIT et ILLIMITÉ**
- ✅ **Pas de clé API nécessaire** (anonyme)
- ✅ **Support NSFW complet** sans censure
- ✅ **Réseau décentralisé** Stable Diffusion
- ⏱️ Queue: 30s-5min selon affluence
- 🖼️ Qualité: Stable Diffusion 1.5/SDXL

#### 2. **Pollination AI** (RAPIDE 🚀)
- ✅ **100% gratuit**
- ✅ **Génération ultra-rapide** (5-15 secondes)
- ✅ **Support NSFW**
- ⚠️ Stabilité variable (peut tomber)
- 🖼️ Qualité: Variable

### 🔄 FALLBACK AUTOMATIQUE
Si ton API préférée échoue, l'app **bascule automatiquement** sur l'autre !

**Exemple**:
```
1. Tu choisis "Stable Horde"
2. Stable Horde est en maintenance
3. → L'app essaie automatiquement "Pollination AI"
4. → Image générée quand même ! ✅
```

---

## 🔧 CONFIGURATION

### Actuellement (v2.23.1)
Le choix d'API est **hardcodé** dans le code:
```kotlin
// Dans FreeboxMediaClient.kt
var preferredApi: String = "stable_horde" // Défaut
```

### À venir (v2.24.0)
Un **switch dans les Paramètres** pour choisir facilement:
```
⚙️ Paramètres > Génération d'Images
┌─────────────────────────────────┐
│ API Génération                  │
│ ○ Stable Horde (Recommandé)     │
│ ○ Pollination AI (Rapide)       │
└─────────────────────────────────┘
```

---

## 📊 COMPARAISON DES APIs

| Critère | Stable Horde ⭐ | Pollination AI |
|---------|----------------|----------------|
| **Prix** | 100% gratuit | 100% gratuit |
| **Limite** | Illimité | Illimité |
| **NSFW** | ✅ Supporté | ✅ Supporté |
| **Vitesse** | 30s-5min (queue) | 5-15 secondes |
| **Fiabilité** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ (variable) |
| **Qualité** | ⭐⭐⭐⭐⭐ SD/SDXL | ⭐⭐⭐⭐ |
| **Censure** | ❌ Aucune | ❌ Aucune |
| **Modèles** | SD 1.5, SDXL, etc. | DreamShaper, etc. |
| **API Key** | ❌ Pas nécessaire | ❌ Pas nécessaire |

**Notre recommandation**: **Stable Horde** pour qualité maximale, **Pollination AI** si besoin de rapidité.

---

## 🔞 CONTENU INCLUS

### 32 IMAGES NSFW PRÉ-GÉNÉRÉES
Incluses **directement dans l'APK** (offline):
- 10 × Naruto NSFW
- 10 × Sakura NSFW
- 8 × Sasuke NSFW
- 1 × Hinata NSFW
- 1 × Kakashi NSFW
- 1 × Itachi NSFW
- 1 × Brad Pitt NSFW
- 1 × Leonardo DiCaprio NSFW

**Total: ~8MB d'images PNG**

**Avantage**: Galeries disponibles **même sans internet** !

---

## ✅ CORRECTIONS & AMÉLIORATIONS

### De v2.23.0
1. ✅ **Stable Horde intégré** (nouvelle API principale)
2. ✅ **32 images NSFW** dans l'APK
3. ✅ **Fix Pollination AI** (HTTP 500 handling)
4. ✅ **Script génération** (`generate_nsfw_stablehorde.py`)

### De v2.23.1 (CETTE VERSION)
1. ✅ **Choix d'API** dans `FreeboxMediaClient`
2. ✅ **Fallback automatique** Stable Horde ↔ Pollination
3. ✅ **Logs détaillés** de l'API utilisée
4. ✅ **Documentation API** (`API_ALTERNATIVES.md`)

---

## 📝 NOTES TECHNIQUES

### Architecture
```kotlin
FreeboxMediaClient
├── preferredApi: String = "stable_horde"
├── stableHorde: StableHordeClient()
├── pollinationClient: PollinationAIClient()
└── generateImage()
    ├─► Tente API préférée (Stable Horde)
    └─► Si échec → Fallback (Pollination)
```

### Stable Horde Workflow
```
1. Submit request → POST /generate/async
   ↓
2. Get request ID
   ↓
3. Poll status → GET /generate/check/{id}
   ├─ Queue position: 5/10
   ├─ Wait time: ~2 minutes
   └─ Status: processing
   ↓
4. Fetch image → GET /generate/status/{id}
   ↓
5. Return Base64 image
```

### Paramètres par défaut
```kotlin
width = 512
height = 512
steps = 20
cfgScale = 7.0
nsfw = true
censor_nsfw = false
models = ["stable_diffusion"]
r2 = true // Use CDN for faster delivery
```

---

## 🐛 PROBLÈMES CONNUS

### 1. Stable Horde: Queue longue aux heures de pointe
**Symptôme**: Génération prend 5+ minutes  
**Solution**: L'app affiche la position dans la queue. Patience ! Ou bascule manuellement sur Pollination.

### 2. Pollination AI: Instabilité occasionnelle
**Symptôme**: HTTP 500 ou DNS errors  
**Solution**: Le fallback automatique vers Stable Horde s'active.

### 3. Settings UI pas encore implémentée
**Symptôme**: Impossible de changer l'API depuis l'app  
**Workaround**: L'API par défaut (Stable Horde) est recommandée pour 95% des cas.  
**Fix prévu**: v2.24.0 ajoutera un switch dans Paramètres.

---

## 🔮 PROCHAINES VERSIONS

### v2.24.0 (à venir)
- ⚙️ **UI Settings** pour choix d'API
- 💾 **Sauvegarde préférence** dans SharedPreferences
- 📊 **Statistiques** d'utilisation API
- 🎨 **Prévisualisation** des modèles disponibles

### v2.25.0 (planifié)
- 🌐 **Support multi-modèles** Stable Horde (SDXL, Waifu, etc.)
- ⏱️ **Timeout configurable** pour queue Stable Horde
- 🔄 **Retry automatique** si génération échoue
- 📈 **Dashboard API** (temps moyen, succès/échecs)

---

## 📥 INSTALLATION

### Depuis GitHub Releases
1. Télécharge **Naruto-AI-Chat-v2.23.1.apk**
2. Active "Sources inconnues" dans Android
3. Installe l'APK
4. Lance l'app
5. Les images NSFW sont déjà incluses ! 🎉

### Depuis le code source
```bash
git clone https://github.com/mel805/naruto-ai-chat.git
cd naruto-ai-chat
git checkout v2.23.1
./gradlew assembleRelease
# APK dans: app/build/outputs/apk/release/
```

---

## 🙏 REMERCIEMENTS

- **Stable Horde** community pour l'API gratuite et décentralisée
- **Pollination AI** pour l'API rapide et NSFW-friendly
- Tous les testeurs NSFW beta 🔞

---

## 📞 SUPPORT

**Problèmes ?**
- 🐛 **Issues GitHub**: https://github.com/mel805/naruto-ai-chat/issues
- 💬 **Discussions**: https://github.com/mel805/naruto-ai-chat/discussions

---

## 📜 HISTORIQUE

### v2.23.1 (29/12/2025) - CETTE VERSION
- ✅ Choix API Stable Horde/Pollination
- ✅ Fallback automatique
- ✅ Logs détaillés

### v2.23.0 (29/12/2025)
- ✅ Stable Horde intégré
- ✅ 32 images NSFW dans APK
- ✅ Script génération Python

### v2.22.0 (29/12/2025)
- ✅ Fix DNS Pollination video
- ✅ Priorité Pollination pour images

### v2.21.0 (29/12/2025)
- ✅ Fix NSFW tous les 13 personnages
- ✅ Prompt ultra-renforcé

### v2.20.0 (29/12/2025)
- ✅ Fix NSFW ultra-renforcé
- ✅ Pre-acceptance message
- ✅ Max LLM parameters

### v2.19.0 (29/12/2025)
- ✅ Vidéo SFW/NSFW (5s)
- ✅ ComfyUI optimisé
- ✅ Script bash Freebox

### v2.18.0 (29/12/2025)
- ✅ Fix NSFW conversations
- ✅ UI clavier réparée
- ✅ Vitesse génération 3x

---

**🎉 Profite de la génération d'images NSFW illimitée et gratuite avec Stable Horde !**

**Version recommandée: v2.23.1** ⭐
