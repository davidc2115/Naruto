# 🔧 Configuration Actuelle - Naruto AI Chat v2.14+

**Date:** 28 Décembre 2025  
**Status:** ✅ Configuré et Prêt

---

## ✅ CONFIGURATIONS EFFECTUÉES

### 1. 🎯 Priorité Freebox > Pollination AI

**Fichier:** `FreeboxMediaClient.kt`

La logique de génération d'images/vidéos suit maintenant cet ordre strict:

```
┌─────────────────────────────────────┐
│   Demande Génération Image/Vidéo    │
└──────────────┬──────────────────────┘
               │
               ▼
    ┌──────────────────────┐
    │ 1️⃣ PRIORITÉ: FREEBOX │
    │  - Ping 3s           │
    │  - Si accessible ✅  │
    └──────┬───────────────┘
           │
           ├─► Accessible? ──► FREEBOX SD WebUI (local, illimité, NSFW sans censure)
           │                   Timeout: 120s
           │                   Source: data:image/png;base64
           │
           └─► Timeout? ────► 2️⃣ FALLBACK: Pollination AI (cloud, gratuit)
                              Timeout: 120s  
                              Source: https://image.pollinations.ai/
```

**Logs améliorés:**
- `🎯 PRIORITÉ 1: Tentative génération via Freebox...`
- `✅ Freebox accessible! Génération locale...`
- `⚠️ Freebox non accessible`
- `🔄 FALLBACK: Utilisation Pollination AI`
- `📍 Source: Freebox Stable Diffusion (local)` ou `Pollination AI (cloud)`

---

### 2. 🔞 Âge des Personnages NSFW (18+)

**Fichier:** `Characters.kt`

**Correction effectuée:** Tous les personnages Naruto sont maintenant **18 ans (adultes)** dans les prompts NSFW.

| Personnage | Ancien Âge NSFW | Nouveau Âge NSFW | Status |
|------------|-----------------|------------------|--------|
| Naruto Uzumaki | ~~17 ans~~ | **18 ans (adulte)** | ✅ Corrigé |
| Sasuke Uchiha | ~~17 ans~~ | **18 ans (adulte)** | ✅ Corrigé |
| Sakura Haruno | ~~17 ans~~ | **18 ans (adulte)** | ✅ Corrigé |
| Hinata Hyuga | ~~17 ans~~ | **18 ans (adulte)** | ✅ Corrigé |
| Kakashi Hatake | 26 ans | 26 ans | ✅ Déjà adulte |
| Itachi Uchiha | 21 ans | 21 ans | ✅ Déjà adulte |

**Format des prompts NSFW:**
```kotlin
systemPromptNSFW = """Tu es [Personnage], 18 ans (adulte), ...
```

**IMPORTANT:** Les conversations NSFW sont maintenant **UNIQUEMENT avec des personnages adultes (18+)**.

---

### 3. 📸 Galeries NSFW

**Status Actuel:** 20/195 images générées

| Personnage | Images NSFW | Status |
|------------|-------------|--------|
| Naruto | 15/15 | ✅ Complet |
| Sasuke | 5/15 | ⚠️ Partiel |
| Sakura | 0/15 | ❌ À générer |
| Kakashi | 0/15 | ❌ À générer |
| Hinata | 0/15 | ❌ À générer |
| Itachi | 0/15 | ❌ À générer |
| Brad Pitt | 0/15 | ❌ À générer |
| Leonardo DiCaprio | 0/15 | ❌ À générer |
| The Rock | 0/15 | ❌ À générer |
| Scarlett Johansson | 0/15 | ❌ À générer |
| Margot Robbie | 0/15 | ❌ À générer |
| Emma Watson | 0/15 | ❌ À générer |
| Zendaya | 0/15 | ❌ À générer |

**Génération:** Utiliser le script `generate_nsfw_all_characters.py`

---

## 🚀 INSTALLATION STABLE DIFFUSION FREEBOX

### Status: ❌ NON INSTALLÉ

**URL Cible:** `http://88.174.155.230:33437`  
**Status Actuel:** Inaccessible (timeout connexion)

### Option 1: Installer SD WebUI sur Freebox (Recommandé pour production)

Suivre le guide complet: [`FREEBOX_SD_WEBUI_SETUP.md`](/workspace/FREEBOX_SD_WEBUI_SETUP.md)

**Avantages:**
- ✅ Génération locale **illimitée et gratuite**
- ✅ **Privacy totale** (aucune donnée en ligne)
- ✅ **NSFW sans censure** (aucun filtre)
- ✅ Contrôle total (modèles, CFG, steps)

**Inconvénients:**
- ⚠️ Installation manuelle (~30-60 min)
- ⚠️ Plus lent (30-120s par image sur CPU)
- ⚠️ Freebox doit être allumée 24/7

**Commandes rapides:**
```bash
# 1. Connexion SSH
ssh -p 33000 root@88.174.155.230

# 2. Installation
cd /root
git clone https://github.com/AUTOMATIC1111/stable-diffusion-webui.git
cd stable-diffusion-webui
./webui.sh --listen --port 33437

# 3. Créer service systemd
sudo systemctl enable sd-webui
sudo systemctl start sd-webui

# 4. Vérifier
curl http://88.174.155.230:33437
```

### Option 2: Continuer avec Pollination AI uniquement

**Avantages:**
- ✅ Aucune installation nécessaire
- ✅ Rapide (2-10s par image)
- ✅ Gratuit et illimité (avec rate limits)

**Inconvénients:**
- ⚠️ Rate limits (429 errors si trop de requêtes)
- ⚠️ Pas de contrôle sur le modèle
- ⚠️ NSFW parfois filtré/censuré

**L'app fonctionne déjà en mode Pollination AI uniquement via fallback automatique.**

---

## 📋 GÉNÉRATION GALERIES NSFW

### Script: `generate_nsfw_all_characters.py`

**Utilise:** Pollination AI (car Freebox pas encore installée)

**Lancement:**
```bash
cd /workspace
python3 generate_nsfw_all_characters.py
```

**Détails:**
- 13 personnages × 15 images = **195 images NSFW**
- 3 niveaux par personnage:
  - Images 1-5: **Sensuel** (nu artistique)
  - Images 6-10: **Sexy** (érotique)
  - Images 11-15: **Explicit** (scènes intimes)
- Format: 768×768 JPG
- Delay anti-rate-limit: 10-20s entre chaque
- Durée estimée: **~30-40 minutes**

**IMPORTANT:** Tous les personnages sont décrits comme **adultes 18+** dans les prompts.

---

## 🔍 VÉRIFICATIONS

### Tester Freebox SD WebUI

```bash
# Ping simple
curl -I http://88.174.155.230:33437

# Test génération
curl -X POST http://88.174.155.230:33437/sdapi/v1/txt2img \
  -H "Content-Type: application/json" \
  -d '{"prompt": "test", "steps": 10, "width": 512, "height": 512}'
```

### Vérifier Images NSFW Locales

```bash
cd /workspace/app/src/main/res/drawable-nodpi
ls -lh *nsfw*.jpg | wc -l  # Compter
ls -lh *nsfw*.jpg | head   # Lister premières
```

### Tester App Android

1. Build APK: `./gradlew assembleRelease`
2. Installer sur device
3. Sélectionner personnage
4. Activer mode NSFW (toggle dans profil)
5. Générer image (icône 📷)
6. Observer logs:
   - Source = "Freebox" si SD accessible
   - Source = "Pollination AI" si fallback

---

## 📱 INTÉGRATION APP

### Détection Source Image

**Dans `ChatViewModel.kt`:**

```kotlin
val source = if (imageUrl.startsWith("data:image")) 
    "Freebox"  // Base64 = local Freebox
else 
    "Pollination AI"  // URL = cloud

val message = "✅ Image générée ($source)"
```

**Messages utilisateur:**
- `✅ Image générée avec succès (Freebox)` ← Génération locale
- `✅ Image générée avec succès (Pollination AI)` ← Génération cloud

---

## 🎯 PROCHAINES ÉTAPES

### Court Terme

1. ✅ **Corriger âge personnages** (FAIT ✅)
2. ✅ **Configurer priorité Freebox > Pollination** (FAIT ✅)
3. ⏳ **Générer 175 images NSFW manquantes**
   ```bash
   python3 generate_nsfw_all_characters.py
   ```
4. ⏳ **Installer Freebox SD WebUI** (optionnel mais recommandé)

### Moyen Terme

1. AnimateDiff pour vraies vidéos animées
2. Sélection modèle SD dans settings
3. ControlNet pour poses précises
4. Indicateur "Freebox active/inactive" dans UI

---

## ⚠️ AVERTISSEMENTS

### Mode NSFW

- **UNIQUEMENT pour adultes 18+**
- Tous les personnages dans prompts NSFW sont **adultes (18+ ans)**
- Contenu explicite sans censure (si Freebox)
- Respect des CGU Pollination AI (filtres possibles)

### Privacy

- **Freebox:** 100% local, aucune donnée envoyée online
- **Pollination AI:** Requêtes envoyées à API cloud externe

### Rate Limits

- **Freebox:** Illimité (local)
- **Pollination AI:** Rate limits 429 si >20 requêtes/min

---

## 📞 SUPPORT

### Logs Freebox

```bash
# Voir logs service SD
sudo journalctl -u sd-webui -f

# Status service
sudo systemctl status sd-webui

# Redémarrer
sudo systemctl restart sd-webui
```

### Debug App Android

```bash
# Logs FreeboxMedia
adb logcat | grep FreeboxMedia

# Logs Pollination
adb logcat | grep PollinationAI

# Logs génération images
adb logcat | grep "Image générée"
```

---

## ✅ CHECKLIST FINALE

- [x] Âge personnages corrigé (18+ dans NSFW)
- [x] Priorité Freebox > Pollination configurée
- [x] Logs améliorés et explicites
- [x] Script génération NSFW créé
- [ ] Installer Freebox SD WebUI (optionnel)
- [ ] Générer 175 images NSFW manquantes
- [ ] Tester génération avec Freebox (si installée)
- [ ] Tester fallback Pollination AI

---

**Version App:** 2.14.0+  
**Dernière Mise à Jour:** 28 Décembre 2025  
**Configuration:** ✅ Prête pour utilisation (Pollination AI) / ⏳ Freebox à installer
