# Release Notes v2.10.0

## 🔞 VERSION ADULTE - CONTENU NSFW

### ✨ NOUVEAUTÉS MAJEURES

#### 🖼️ 195 Images NSFW Intégrées
- **5 images sensuelles** par personnage (13 personnages)
  - Tenues légères, poses suggestives
  - Torse nu, sous-vêtements visibles
  
- **5 images sexy** par personnage
  - Lingerie, poses provocantes
  - Scènes de douche, intimité
  
- **5 images NSFW explicites** par personnage
  - Nudité complète, poses érotiques
  - Scènes intimes, contenu adulte

**Total : 195 images haute qualité (15 par personnage)**

#### 🎨 Galeries SFW / NSFW Séparées
- Toggle dans le profil personnage
- Bouton "Voir NSFW 🔞" / "Voir SFW"
- Galeries distinctes pour chaque mode
- Visualisation plein écran avec navigation

#### 🔞 Génération d'Images NSFW Dynamique
- Mode NSFW activable dans ChatViewModel
- Prompts automatiquement adaptés au contexte
- Instructions explicites injectées :
  - "Generate an EXPLICIT NSFW/adult/erotic scene"
  - "Include nudity, sensual poses, intimate details"
- Compatible Groq API + Pollination AI

### 🐛 CORRECTIONS

#### ✅ Affichage Images/Vidéos Corrigé
- **ImageRequest.Builder** complet pour Coil 2.5
- **Placeholder** coloré (primaryContainer) pendant chargement
- **Error** avec couleur errorContainer si échec
- **Crossfade** pour transitions douces

### 🎨 AMÉLIORATIONS UI

#### Galerie Personnage
- Interface épurée avec toggle SFW/NSFW
- Compteur dynamique d'images
- Navigation fullscreen améliorée
- Preview en grille 3 colonnes

#### Chat
- Images générées visibles dans conversation
- Vidéos avec preview + bouton navigateur
- Feedback visuel immédiat (placeholder)

### 📦 CONTENU TECHNIQUE

#### Nouvelles Images (drawable-nodpi/)
```
narutoNSFW1.jpg → narutoNSFW15.jpg
sasukeNSFW1.jpg → sasukeNSFW15.jpg
sakuraNSFW1.jpg → sakuraNSFW15.jpg
kakashiNSFW1.jpg → kakashiNSFW15.jpg
itachiNSFW1.jpg → itachiNSFW15.jpg
hinataNSFW1.jpg → hinataNSFW15.jpg
leonardoNSFW1.jpg → leonardoNSFW15.jpg
bradNSFW1.jpg → bradNSFW15.jpg
margotNSFW1.jpg → margotNSFW15.jpg
scarlettNSFW1.jpg → scarlettNSFW15.jpg
emmaNSFW1.jpg → emmaNSFW15.jpg
rockNSFW1.jpg → rockNSFW15.jpg
zendayaNSFW1.jpg → zendayaNSFW15.jpg
```

#### Character.kt
- Nouveau champ `galleryNSFW: List<String>`
- Tous les 13 personnages ont leurs 15 images NSFW référencées

#### ChatViewModel.kt
- Variable `_isNSFWMode` pour activer mode adulte
- Prompts conditionnels selon mode
- Instructions NSFW injectées dynamiquement

### 📱 TAILLE APK
- **Estimée : ~50 MB** (195 images NSFW + 130 images SFW existantes)
- Optimisation JPEG (qualité 85, max 150 KB/image)

### ⚠️ AVERTISSEMENT
**Application réservée aux adultes (18+)**

Ce contenu est explicite et destiné à un public averti. Les images NSFW incluent :
- Nudité complète
- Poses érotiques
- Scènes intimes/sexuelles
- Contenu adulte explicite

### 🎯 PERSONNAGES INCLUS
- **Naruto** : 6 persos (Naruto, Sasuke, Sakura, Hinata, Kakashi, Itachi)
- **Célébrités masculines** : 3 persos (Leonardo DiCaprio, Brad Pitt, Dwayne Johnson)
- **Célébrités féminines** : 4 persos (Margot Robbie, Scarlett Johansson, Emma Watson, Zendaya)

### 🛠️ INSTALLATION
1. Télécharger `Naruto-AI-Chat-v2.10.0.apk`
2. Activer "Sources inconnues" si nécessaire
3. Installer l'APK
4. Ouvrir l'app et profiter du contenu NSFW

---

**Version précédente** : v2.9.2 (Fix images + script NSFW)  
**Date de sortie** : Décembre 2024  
**Taille** : ~50 MB  
**Plateforme** : Android 5.0+
