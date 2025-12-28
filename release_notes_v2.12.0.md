# 🎮 Naruto AI Chat v2.12.0 - CONVERSATIONS SAUVEGARDÉES + FIX GÉNÉRATION

## 💾 SYSTÈME SAUVEGARDE CONVERSATIONS (NOUVEAU !)

Cette version révolutionne l'expérience utilisateur avec la **sauvegarde automatique de toutes vos conversations** !

### Fonctionnalités de Sauvegarde

#### ✅ Sauvegarde Automatique
- **Sauvegarde après chaque message** : Vos conversations sont automatiquement enregistrées localement sur votre smartphone
- **Persistance totale** : Messages, mode NSFW, et contexte entièrement conservés
- **Aucune perte de données** : Fermez l'app et reprenez exactement où vous étiez

#### 🔄 Reprise & Nouvelle Conversation
- **Bouton "Reprendre"** : Retrouvez votre conversation exactement comme vous l'avez laissée
- **Bouton "Nouveau"** : Commencez une nouvelle conversation (l'ancienne est supprimée automatiquement)
- **Un personnage = Une conversation** : Chaque personnage garde sa propre conversation active

#### 🎯 Interface Intuitive
- Dans le **profil du personnage** :
  - Si conversation existe → Boutons "Reprendre" et "Nouveau"
  - Si aucune conversation → Bouton "Commencer la conversation"
- Dans le **chat** :
  - Bouton 🔄 en haut pour réinitialiser la conversation à tout moment

## 🐛 FIX GÉNÉRATION IMAGES/VIDÉOS

### Problèmes Résolus
- ✅ **Délai initial optimisé** : 10s → 2s pour une meilleure expérience utilisateur
- ✅ **Vérification complète** : Téléchargement complet de l'image au lieu d'un simple HEAD
- ✅ **Validation taille** : Détection des images invalides (min 1KB)
- ✅ **Retry intelligent** : 5 tentatives avec backoff exponentiel

### Comment Tester
1. Ouvrez un chat avec un personnage
2. Cliquez sur l'icône 📷 en haut à droite
3. Choisissez "Générer image" ou "Générer vidéo"
4. L'image/vidéo devrait maintenant s'afficher correctement dans la conversation

**Note** : Pollination AI peut encore rencontrer des erreurs 500/429 en cas de forte charge. Réessayez après quelques minutes si nécessaire.

## 🖼️ GALERIES NSFW - STATUS

### Images Disponibles
Actuellement, **20 images NSFW** sont intégrées dans l'APK pour Naruto (narutonsfw1.jpg à narutonsfw15.jpg).

### Pourquoi Certaines Galeries Sont Vides ?
- Les 175 images restantes (13 personnages × 15 images - 20 déjà générées) nécessitent une génération manuelle
- Pollination AI impose des limitations strictes (rate limit 429, erreurs 500)
- **Solution temporaire** : Les galeries sont configurées mais affichent des placeholders vides

### Debug Intégré
Un log de débogage a été ajouté pour identifier les problèmes de chargement :
```
Loading NSFW: narutonsfw1 -> resId=2131165312
```

## 🔧 TECHNIQUE

### Nouvelles Dépendances
- **Gson 2.10.1** : Sérialisation JSON pour sauvegarde conversations

### Nouveaux Fichiers
- `ConversationManager.kt` (88 lignes) : Gestionnaire de persistance
  - `saveConversation()` : Sauvegarde messages + mode NSFW
  - `loadConversation()` : Chargement depuis SharedPreferences
  - `deleteConversation()` : Suppression pour nouvelle conversation
  - `hasConversation()` : Vérification existence

### Modifications ViewModel
- `selectCharacter(character, loadSaved)` : Charge automatiquement si existe
- `hasSavedConversation(characterId)` : Vérification UI
- `startNewConversation()` : Supprime ancienne + réinitialise
- `saveCurrentConversation()` : Auto-appelé après chaque message

### Modifications UI
- **CharacterProfileScreen** :
  - Boutons conditionnels (Reprendre/Nouveau vs Commencer)
  - Passage `loadSaved` à `onStartChat()`
- **ChatScreen** :
  - Bouton 🔄 "Nouvelle conversation" en TopAppBar
- **PollinationAIClient** :
  - Délai initial 2s au lieu de 10s
  - GET complet avec validation taille

## 📱 INSTALLATION

### Téléchargement
```bash
# Via GitHub CLI
gh release download v2.12.0 -p "*.apk"

# Ou via navigateur
https://github.com/mel805/naruto-ai-chat/releases/tag/v2.12.0
```

### Permissions
- ✅ INTERNET : Génération images/vidéos (Pollination AI)
- ✅ Pas de permissions supplémentaires requises
- ✅ Stockage local via SharedPreferences (pas d'accès fichiers)

## 🎯 PROCHAINES ÉTAPES

### Court Terme (v2.13.0)
1. **Génération 175 images NSFW** :
   - Images explicites haute qualité
   - Solution : Service payant ou génération manuelle
2. **Fix drawable loading** :
   - Investiguer `getIdentifier()` qui retourne 0
   - Alternative : Utiliser directement les resource IDs

### Moyen Terme (v2.14.0)
1. **Export/Import conversations** :
   - Partage entre appareils
   - Backup cloud optionnel
2. **Historique conversations** :
   - Garder plusieurs conversations par personnage
   - Timeline avec dates

## 🐞 PROBLÈMES CONNUS

1. **Galeries NSFW vides** :
   - Seul Naruto a 15 images (narutonsfw1-15)
   - Autres personnages : galeries configurées mais images manquantes
   - Workaround : À venir avec génération massive

2. **Pollination AI instable** :
   - Erreurs 500/502 fréquentes
   - Rate limit 429 si utilisation intensive
   - Solution : Retry automatique (5×) avec backoff

3. **GitHub Actions 403** :
   - Release automatique échoue (permissions)
   - Workaround : Release manuelle via `gh CLI`

## 🙏 FEEDBACK

Si vous rencontrez des problèmes :
1. Vérifiez que vous avez bien la **v2.12.0** (Settings → About)
2. Essayez de **vider le cache** de l'app
3. Pour la génération d'images : **attendez 30s entre chaque tentative**
4. Ouvrez une issue sur GitHub avec :
   - Version Android
   - Steps pour reproduire
   - Logs si possible

---

**Taille APK** : 22 MB  
**Version Code** : 22  
**Build** : Release signé  
**Compatibilité** : Android 7.0+ (API 24+)
