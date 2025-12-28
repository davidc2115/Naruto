# 🎨 Naruto AI Chat v2.14.0 - FREEBOX STABLE DIFFUSION + MODE NSFW COMPLET

## 🎯 NOUVEAUTÉS MAJEURES

### 1. 🖼️ **GÉNÉRATION LOCALE VIA FREEBOX** (Nouveau!)

L'app intègre maintenant **FreeboxMediaClient** pour génération d'images/vidéos **100% locale et gratuite** !

#### Comment ça marche ?

```
┌─────────────────────────────────────────┐
│  App Android (v2.14.0)                  │
│                                         │
│  1. Demande génération image/vidéo      │
│     ↓                                   │
│  2. Ping Freebox (3s timeout)           │
│     ├─ Accessible ? → Freebox SD WebUI  │
│     └─ Timeout ? → Pollination AI       │
│                                         │
│  3. Affichage avec source indiquée      │
│     "✅ Image générée (Freebox)"        │
│     "✅ Image générée (Pollination AI)" │
└─────────────────────────────────────────┘
```

#### Avantages Freebox

| Caractéristique | Freebox SD WebUI | Pollination AI |
|----------------|------------------|----------------|
| **Coût** | 0€ (local) | 0€ (API gratuite) |
| **Limites** | **∞ Illimité** | 429 rate limits |
| **Vitesse** | 30-60s | 2-5s |
| **Privacy** | **100% local** | Cloud externe |
| **NSFW** | **Sans censure** | Parfois bloqué |
| **Qualité** | **Contrôle total** | Flux fixe |
| **Disponibilité** | 24/7 si allumée | 99% |

#### Configuration Requise

⚠️ **Freebox SD WebUI n'est PAS installée par défaut** sur votre Freebox !

**Pour l'activer** : Suivez le guide complet dans `FREEBOX_SD_WEBUI_SETUP.md`

**Installation rapide** :

```bash
ssh -p 33000 root@88.174.155.230

# Installer Stable Diffusion WebUI
cd /root
git clone https://github.com/AUTOMATIC1111/stable-diffusion-webui.git
cd stable-diffusion-webui
./webui.sh --listen --port 7860
```

**Durée installation** : 20-40 minutes (première fois)

---

### 2. 🔞 **MODE NSFW 100% FONCTIONNEL**

Le mode NSFW influence maintenant **TOUT** dans l'app :

#### ✅ Conversations NSFW

```kotlin
// SFW Mode
systemPromptSFW = "Tu es Naruto, ninja énergique..."

// NSFW Mode  
systemPromptNSFW = "Tu es Naruto... Plus mature, flirteur, 
                     vulnérable émotionnellement, peut être 
                     intime dans discussions..."
```

**Différences** :
- **Temperature** : 0.8 (SFW) → 0.9 (NSFW) (plus créatif/audacieux)
- **Ton** : Familial → Mature/Flirteur/Intime
- **Sujets** : Restreints → Tous sujets (romance, intimité, etc.)

#### ✅ Génération Images NSFW

Quand mode NSFW activé :

```kotlin
val nsfwContext = if (isNSFWMode) {
    "\n\nIMPORTANT: Generate an EXPLICIT NSFW/adult/erotic scene.
     Include nudity, sensual poses, intimate details, 
     sexual content if contextually appropriate."
} else {
    ""
}
```

**Résultat** :
- Prompts incluent : "nudity, sensual poses, explicit"
- Freebox génère sans censure
- Images NSFW réalistes dans conversation

#### ✅ Génération Vidéos NSFW

Identique aux images, le contexte NSFW est injecté dans les prompts vidéo.

#### ✅ Sauvegarde Mode NSFW

Le mode NSFW est **sauvegardé avec la conversation** :

```kotlin
conversationManager.saveConversation(
    characterId = character.id,
    messages = messages,
    isNSFW = _isNSFWMode.value  // ✅ Sauvegardé
)
```

**Reprise** : Quand vous reprenez une conversation, le mode NSFW est restauré automatiquement.

---

## 📋 GÉNÉRATION MASSIVE IMAGES NSFW

### Script Python Inclus

`generate_nsfw_freebox_v4.py` génère **195 images NSFW explicites** :

- **13 personnages** × 15 images
- **3 niveaux** : Sensuel (1-5), Sexy (6-10), Hardcore (11-15)
- **Prompts détaillés** : Nudité, poses sensuelles, scènes intimes
- **Qualité** : 768×768, 25 steps, CFG 7.5

**Lancer génération** :

```bash
cd /workspace
python3 generate_nsfw_freebox_v4.py
```

**Durée estimée** : 2-3 heures (30-60s par image)

**Prérequis** : Freebox SD WebUI installée et accessible

---

## 🔧 TECHNIQUE

### Nouveau Fichier : FreeboxMediaClient.kt

```kotlin
class FreeboxMediaClient(private val pollinationFallback: PollinationAIClient) {
    
    // Vérifie si Freebox accessible (ping 3s)
    suspend fun isAvailable(): Boolean
    
    // Génère image via Freebox SD WebUI
    // Fallback automatique sur Pollination AI si timeout
    suspend fun generateImage(
        prompt: String,
        negativePrompt: String = "low quality, blurry...",
        width: Int = 512,
        height: Int = 512,
        steps: Int = 20,
        cfgScale: Double = 7.0,
        isNSFW: Boolean = false
    ): Result<String>
    
    // Génère vidéo/GIF animé
    suspend fun generateVideo(
        prompt: String,
        negativePrompt: String = "low quality...",
        width: Int = 512,
        height: Int = 512,
        isNSFW: Boolean = false
    ): Result<String>
    
    // Liste modèles disponibles sur Freebox
    suspend fun getAvailableModels(): Result<List<String>>
}
```

### Intégration ChatViewModel

```kotlin
// Ancien (v2.13.0)
private val pollinationAIClient = PollinationAIClient()

// Nouveau (v2.14.0)
private val pollinationAIClient = PollinationAIClient()
private val freeboxMediaClient = FreeboxMediaClient(pollinationAIClient)

// Génération avec fallback automatique
fun generateImageFromConversation() {
    val result = freeboxMediaClient.generateImage(
        prompt = imagePrompt,
        width = 768,
        height = 768,
        steps = 25,
        cfgScale = 7.5,
        isNSFW = _isNSFWMode.value  // ✅ Mode NSFW transmis
    )
}
```

### Détection Source

```kotlin
val source = if (imageUrl.startsWith("data:image")) 
    "Freebox"  // Base64 = local
else 
    "Pollination AI"  // URL = externe

ChatMessage(
    content = "✅ Image générée ($source)",
    imageUrl = imageUrl
)
```

---

## 📖 DOCUMENTATION COMPLÈTE

### Guide Installation Freebox

Consultez **`FREEBOX_SD_WEBUI_SETUP.md`** pour :

1. **Installation automatique** Stable Diffusion WebUI
2. **Configuration optimale** (RAM, CPU, modèles)
3. **Service systemd** (démarrage auto)
4. **Tests de fonctionnement**
5. **Dépannage complet**

### Commandes Rapides

```bash
# Vérifier status Freebox SD WebUI
curl http://88.174.155.230:7860

# Redémarrer service (si installé)
ssh -p 33000 root@88.174.155.230
systemctl restart sd-webui

# Voir logs
journalctl -u sd-webui -f

# Tester génération
python3 /workspace/generate_nsfw_freebox_v4.py
```

---

## 🎮 UTILISATION

### 1. Mode NSFW Conversations

1. Sélectionner un personnage
2. Aller dans **Profil**
3. Activer toggle **NSFW** (en haut)
4. Commencer conversation
5. **Le personnage adapte son comportement** :
   - Plus mature
   - Peut parler d'intimité
   - Répond aux avances flirteuses
   - Température 0.9 (plus créatif)

### 2. Génération Images NSFW

1. Ouvrir chat avec personnage
2. **Activer mode NSFW** (toggle dans profil)
3. Discuter pour créer contexte
4. Cliquer icône **📷** en haut
5. Choisir **"Générer image"**
6. **Si Freebox active** :
   - Génération locale (30-60s)
   - Image NSFW sans censure
   - Message : "✅ Image générée (Freebox)"
7. **Si Freebox inactive** :
   - Fallback Pollination AI (2-5s)
   - Image NSFW (peut être filtrée)
   - Message : "✅ Image générée (Pollination AI)"

### 3. Génération Vidéos NSFW

Identique aux images :
- Cliquer **🎬 "Générer vidéo"**
- Mode NSFW influence le prompt
- Freebox (priorité) ou Pollination AI (fallback)

---

## ⚠️ STATUS FREEBOX

### État Actuel

🔴 **Freebox SD WebUI PAS installée** sur `http://88.174.155.230:7860`

**Conséquence** : L'app utilise **uniquement Pollination AI** pour l'instant (fallback automatique)

### Pour Activer Freebox

**Option 1 : Installation manuelle** (Recommandé)
- Suivre `FREEBOX_SD_WEBUI_SETUP.md`
- Durée : 30-60 minutes
- Résultat : Génération locale illimitée

**Option 2 : Continuer avec Pollination AI**
- Aucune action requise
- L'app fonctionne déjà
- Limitation : Rate limits 429/500

---

## 🐛 PROBLÈMES CONNUS

### 1. Freebox Timeout

**Symptôme** : Message "❌ Erreur génération: Connection timeout"

**Cause** : Freebox SD WebUI non accessible

**Solution** :
1. Vérifier : `curl http://88.174.155.230:7860`
2. Si timeout → Installer WebUI (voir guide)
3. L'app fallback automatiquement sur Pollination AI

### 2. Pollination AI Rate Limits

**Symptôme** : "❌ Erreur: Rate limit 429"

**Solution** :
- Attendre 1-2 minutes
- Ou installer Freebox SD WebUI (pas de limites)

### 3. Images NSFW Censurées

**Symptôme** : Image floue ou texte "Content filtered"

**Cause** : Pollination AI filtre certains contenus NSFW

**Solution** :
- Installer Freebox SD WebUI (sans censure)
- Ou reformuler prompt (moins explicite)

---

## 📊 COMPARAISON VERSIONS

| Version | Freebox | Mode NSFW | Galeries NSFW | Conversations Sauvées |
|---------|---------|-----------|---------------|----------------------|
| v2.12.0 | ❌ | ⚠️ Partiel | ❌ Vides | ✅ Oui |
| v2.13.0 | ❌ | ⚠️ Partiel | ✅ 20 images | ✅ Oui |
| **v2.14.0** | **✅ Oui** | **✅ Complet** | **✅ 20 images** | **✅ Oui** |

---

## 🚀 PROCHAINES ÉTAPES

### Court Terme (v2.15.0)

1. **Générer 175 images NSFW** :
   - Une fois Freebox installée
   - Lancer `generate_nsfw_freebox_v4.py`
   - Durée : 2-3 heures

2. **UI Amélioration** :
   - Indicateur "Freebox active/inactive"
   - Bouton "Installer Freebox" (deep link vers guide)
   - Progress bar génération locale

### Moyen Terme (v2.16.0)

1. **Modèles Multiples** :
   - Sélection modèle SD dans settings
   - Realistic Vision, DreamShaper, Anything V5
   - Per-character model preferences

2. **AnimateDiff** :
   - Vraies vidéos animées (pas juste GIF)
   - 2-4 secondes, 8-16 frames
   - Génération 2-3 minutes

---

## 📱 INSTALLATION

### Téléchargement

```bash
# Via GitHub CLI
gh release download v2.14.0 -p "*.apk"

# Ou via navigateur
https://github.com/mel805/naruto-ai-chat/releases/tag/v2.14.0
```

### Configuration Requise

- Android 7.0+ (API 24+)
- 25 MB espace libre
- Connexion Internet
- **Freebox (optionnel)** : Pour génération locale illimitée

---

## ✅ RÉCAPITULATIF

### ✅ Ce Qui Fonctionne MAINTENANT

1. **Mode NSFW complet** :
   - Conversations adaptées (systemPromptNSFW)
   - Génération images/vidéos NSFW
   - Sauvegarde mode avec conversation

2. **Freebox intégrée** :
   - FreeboxMediaClient avec fallback intelligent
   - Génération locale si accessible
   - Pollination AI sinon

3. **20 images NSFW** :
   - Naruto : 15 images
   - Sasuke : 5 images
   - Visibles dans galeries

4. **Sauvegarde conversations** :
   - Auto après chaque message
   - Boutons Reprendre/Nouveau
   - Mode NSFW persisté

### ⏳ À Faire (Nécessite Installation Freebox)

1. **Installer SD WebUI** :
   - Suivre `FREEBOX_SD_WEBUI_SETUP.md`
   - 30-60 minutes setup

2. **Générer 175 images NSFW** :
   - Script `generate_nsfw_freebox_v4.py`
   - 2-3 heures génération

---

**Taille APK** : 22 MB  
**Version Code** : 24  
**Build** : Release signé  
**Compatibilité** : Android 7.0+ (API 24+)

**🎨 Génération locale illimitée avec Freebox !** 🎨
