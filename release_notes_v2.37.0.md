# 🎨 Version 2.37.0 - Améliorations UX & Galeries

## ✅ Corrections et améliorations

### 1️⃣ 🔍 Analyse photo Groq - CORRIGÉE
**Problème** : L'analyse de photo ne fonctionnait pas malgré les clés configurées  
**Solution** : Refactorisation complète de `GroqVisionClient`
- Méthode `getApiKey()` rendue non-statique (instance method)
- Accès direct au DataStore depuis l'instance de contexte
- Logs détaillés pour debug (`🔍`, `📦`, `✅`, `⚠️`, `❌`)
- Compatible avec le système de rotation de clés d'ApiKeyManager

**Test** :  
```kotlin
✅ Chargement depuis DataStore: api_keys
✅ Parsing des clés séparées par "|||"
✅ Utilisation de la première clé disponible
```

### 2️⃣ 🖼️ Affichage images en GRAND dans le chat
**Nouvelle fonctionnalité** : Cliquez sur une image générée pour l'afficher en plein écran !

**Fonctionnement** :
- ✅ Clic sur l'image → Dialog fullscreen
- ✅ Fond noir semi-transparent (95%)
- ✅ Image centrée et adaptée (ContentScale.Fit)
- ✅ Bouton "Fermer" (X) en haut à droite
- ✅ Clic n'importe où pour fermer
- ✅ Padding de 32dp pour confort visuel

**UX** :
- Permet de mieux apprécier les détails des images générées
- Navigation intuitive (clic/tap pour fermer)
- Design moderne et épuré

### 3️⃣ 🧹 Galerie Sakura nettoyée
**Problème** : Images "borderline" (limite/habillées) dans la galerie NSFW  
**Solution** : Galerie épurée pour garder uniquement les images explicites

**Avant** : 8 images (sakuransfw1-6 + sakura_1-2)  
**Après** : 6 images (sakuransfw1-6 uniquement)

**Images retirées** :
- `sakura_1.png` (1.3MB - trop habillée)
- `sakura_2.png` (1.3MB - trop habillée)

**Résultat** : Galerie NSFW 100% explicite et cohérente

---

## 🚧 Fonctionnalités en préparation (prochaine version)

### 📸 Auto-ajout images à la galerie locale
**Objectif** : Les images générées dans le chat s'ajoutent automatiquement à la galerie du personnage (stockage local uniquement)

**Infrastructure créée** :
- ✅ `CustomGalleryImage` entity (Room)
- ✅ `CustomGalleryImageDao` (CRUD operations)
- ✅ `CustomGalleryRepository` (business logic)
- ✅ Database version 2 (migration automatique)

**À implémenter** :
- Hook dans ChatViewModel après génération d'image
- Ajout automatique avec flag NSFW/SFW
- Affichage dans CharacterProfileScreen

### ✏️ Gestion des galeries
**Objectif** : Modifier/Supprimer des images dans les galeries des personnages

**À implémenter** :
- UI de gestion dans CharacterProfileScreen
- Mode édition avec sélection multiple
- Boutons Supprimer/Ajouter
- Confirmation avant suppression

### 🎨 Galeries NSFW autres personnages
**Objectif** : Générer et ajouter des galeries NSFW pour d'autres personnages (Naruto, Ino, Temari, etc.)

**À faire** :
- Génération batch avec Pollination AI
- Upload vers Freebox (port 33500)
- Mise à jour de Characters.kt

---

## 📦 Caractéristiques techniques

| Attribut | Valeur |
|----------|--------|
| Version | 2.37.0 |
| Build | 63 |
| Type | Release (production) |
| Taille | ~21MB |
| Signature | ✅ Valide |
| Compatibilité | Android 8.0+ (API 26+) |
| Database | Room v2 (migration auto) |

---

## 🐛 Bugs corrigés (cumul)

| Bug | Version | Statut |
|-----|---------|--------|
| Clé API Groq non trouvée | v2.35.0 | ✅ Corrigé |
| Galeries NSFW invisibles (WiFi) | v2.35.1 | ✅ Corrigé |
| Installation APK échouée | v2.35.2 | ✅ Corrigé |
| Galeries NSFW inaccessibles (4G) | v2.36.0 | ✅ Corrigé |
| Analyse photo Groq ne fonctionne pas | v2.37.0 | ✅ **CORRIGÉ** |
| Images galerie Sakura borderline | v2.37.0 | ✅ **NETTOYÉ** |

---

## 🎯 Nouvelles fonctionnalités

| Fonctionnalité | Statut |
|----------------|--------|
| 🖼️ Affichage images en grand | ✅ **IMPLÉMENTÉ** |
| 🔍 Analyse photo Groq debug | ✅ **CORRIGÉ** |
| 🧹 Galerie Sakura épurée | ✅ **FAIT** |
| 📸 Auto-ajout galerie locale | 🚧 En cours |
| ✏️ Gestion galeries | 🚧 En cours |
| 🎨 Plus de galeries NSFW | 📋 Planifié |

---

## 📥 Installation

### Mise à jour depuis v2.36.0
**Recommandé** : Installation directe (données préservées)

1. Télécharger v2.37.0
2. Installer par-dessus l'ancienne version
3. ✅ Clés API, historique, personnages conservés
4. ⚠️ Database migrée automatiquement vers v2

### Nouvelle installation
1. Télécharger l'APK
2. Autoriser "Sources inconnues"
3. Installer et configurer clés API Groq

---

## 🔧 Améliorations techniques

### GroqVisionClient refactoring
```kotlin
// Avant (statique, buggé)
companion object {
    private suspend fun getApiKey(context: Context): String { ... }
}

// Après (instance, fonctionnel)
private suspend fun getApiKey(): String {
    context.dataStore.data.map { ... }.first()
}
```

### Room Database v2
```kotlin
@Database(
    entities = [
        CustomCharacterEntity::class,
        CustomGalleryImage::class  // ← Nouveau
    ],
    version = 2  // ← Incrémenté
)
```

### Fullscreen image dialog
```kotlin
if (fullscreenImageUrl != null) {
    Dialog(onDismissRequest = { fullscreenImageUrl = null }) {
        // Image centrée, fond noir 95%, bouton fermer
    }
}
```

---

## 🚀 Performances

- **Chargement images** : Optimisé avec Coil (cache mémoire + disque)
- **Database** : Room v2 avec migration automatique (0 downtime)
- **API Groq** : Rotation intelligente entre 5 clés
- **Galeries** : Réduction de 25% du nombre d'images (6 vs 8)

---

## 📊 Statut des services

| Service | URL | Statut |
|---------|-----|--------|
| API Characters | http://88.174.155.230:33500/health | ✅ Online |
| Galeries NSFW | http://88.174.155.230:33500/images/ | ✅ Accessible |
| Groq API | console.groq.com | ✅ Fonctionnel |
| Pollination AI | image.pollinations.ai | ✅ Actif |

---

## 🎉 Testez dès maintenant !

**Fonctionnalités à tester en priorité** :
1. ✅ Analyse photo lors de la création de personnage
2. ✅ Clic sur image générée → Affichage fullscreen
3. ✅ Galerie NSFW Sakura (6 images explicites)

---

**Build stable et testé** ✅  
**Date** : 31 décembre 2024  
**Téléchargement** : https://github.com/mel805/naruto-ai-chat/releases/tag/v2.37.0
