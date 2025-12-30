# 🎉 Naruto AI Chat - Version 2.31.0

## ✨ Nouveautés majeures

### 📋 **Liste des personnages personnalisés**

Gérez tous vos personnages créés depuis un écran dédié !

#### Fonctionnalités

✅ **Écran de liste moderne**
- Affichage de tous les personnages personnalisés
- Avatar/Photo de chaque personnage
- Nom, description, tags (âge, cheveux)
- Indicateur "✨" pour personnages auto-générés

✅ **Actions rapides**
- ✏️ Édition (TODO à implémenter)
- 🗑️ Suppression avec confirmation
- 👤 Sélection pour chat (TODO à implémenter)
- ➕ Création nouveau personnage

✅ **États gérés**
- Loading spinner pendant chargement
- Message si liste vide
- Gestion erreurs

**Comment accéder ?**
- Sur l'écran de sélection, cliquez sur l'icône **👥** (en haut à droite)

---

### 🤖 **Groq Vision API - Analyse photo IA**

L'analyse automatique de photo est maintenant **fonctionnelle** avec une vraie IA !

#### Comment ça marche

1. Créez un personnage
2. Ajoutez une photo
3. Cliquez sur **"Analyser la photo"**
4. L'IA Groq Vision (modèle `llama-3.2-90b-vision-preview`) analyse l'image
5. **Génération automatique** :
   - ✅ Âge estimé
   - ✅ Genre (homme/femme)
   - ✅ Couleur cheveux + style
   - ✅ Couleur yeux
   - ✅ Teint de peau
   - ✅ Type de corps (athlétique, mince, etc.)
   - ✅ Taille estimée
   - ✅ Traits du visage
   - ✅ Signes distinctifs (tatouages, etc.)
   - ✅ Description physique complète (2-3 phrases)

#### Technique

- **API**: Groq Vision API
- **Modèle**: llama-3.2-90b-vision-preview
- **Format**: JSON structuré
- **Compression**: Images redimensionnées à 1024px max, JPEG 85%
- **Taille max**: 500KB en Base64
- **Timeout**: 60 secondes

---

### 🎨 **ViewModels & Architecture**

#### CustomCharactersViewModel

Nouveau ViewModel pour gérer la liste :
- Liste réactive avec Flow
- Suppression avec gestion image
- États (loading, erreur)
- Compteur de personnages

#### GroqVisionClient

Client dédié pour l'API Vision :
- Compression automatique images
- Parsing JSON robuste
- Gestion erreurs
- Extraction intelligente JSON depuis réponse texte

---

## 🔧 Améliorations techniques

### Architecture
- `CustomCharactersListScreen.kt` - UI liste personnages
- `CustomCharactersViewModel.kt` - Logique liste
- `GroqVisionClient.kt` - Client API Vision
- `PhysicalDescription` data class pour résultats analyse

### Navigation
- Nouvelle route `Screen.CUSTOM_CHARACTERS_LIST`
- Bouton **👥** dans CharacterSelectionScreen
- Intégration complète dans NarutoAIChatApp

### UI Components
- `CustomCharacterCard` - Card personnage avec actions
- `Chip` - Badge informatif (âge, cheveux)
- Dialog confirmation suppression
- États empty/loading/error

---

## 📝 Fichiers modifiés

### Nouveaux fichiers
- `CustomCharactersListScreen.kt`
- `CustomCharactersViewModel.kt`
- `GroqVisionClient.kt`

### Fichiers modifiés
- `CreateCharacterViewModel.kt` - Intégration Groq Vision (remplacement placeholder)
- `NarutoAIChatApp.kt` - Navigation vers liste personnages
- `CharacterSelectionScreen.kt` - Bouton accès liste
- `app/build.gradle.kts` - v2.31.0, build 55

---

## 🐛 Corrections

Aucune correction de bug spécifique - focus sur nouvelles fonctionnalités.

---

## 📱 Installation

Téléchargez l'APK depuis les [Releases GitHub](https://github.com/mel805/naruto-ai-chat/releases/tag/v2.31.0)

---

## 🔮 Prochaines étapes (TODO)

1. **Édition personnages** - Modifier un personnage existant
2. **Chat avec personnages personnalisés** - Convertir CustomCharacterEntity en Character
3. **Import/Export** - Partager personnages entre utilisateurs
4. **Galerie multi-photos** - Plusieurs photos par personnage
5. **Finalisation galeries NSFW** - Récupération images Freebox

---

## ⚠️ Notes importantes

### Groq Vision API

L'analyse photo nécessite une **connexion Internet** et envoie l'image au serveur Groq.
- Temps d'analyse : ~5-10 secondes
- Précision : Très bonne pour âge, cheveux, yeux, morphologie
- Confidentialité : Image envoyée à Groq via HTTPS

### Freebox NSFW

Le port SSH de la Freebox n'est **pas accessible depuis Internet**. Pour générer les galeries NSFW :

1. Connecte-toi en **SSH local** (réseau local ou VPN)
2. Lance le script `freebox_nsfw_generator_local.py`
3. Voir `FREEBOX_NSFW_GENERATION_GUIDE.md` pour détails

---

## 💬 Support

Pour toute question ou problème :
- **Issues GitHub** : [Créer une issue](https://github.com/mel805/naruto-ai-chat/issues)
- **Logs** : Activez les logs dans Paramètres

---

**Développé avec ❤️ pour la communauté Naruto AI Chat**

Version : 2.31.0  
Date : 30 décembre 2025  
Build : 55
