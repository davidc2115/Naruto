# 🎉 Naruto AI Chat - Version 2.32.0

## ✨ Nouveautés

### 🐛 **Fix : Personnages créés maintenant visibles**

Les personnages personnalisés que tu crées apparaissent maintenant **immédiatement** dans la liste !

**Corrections** :
- ✅ Navigation corrigée : après création → redirectionvers liste personnages
- ✅ Logs ajoutés pour debug (voir les personnages en temps réel)
- ✅ Flow réactif optimisé pour affichage instantané

**Comment vérifier** :
1. Crée un personnage
2. Tu es automatiquement redirigé vers "Mes personnages"
3. Ton personnage apparaît dans la liste ✨

---

### 🏷️ **Système d'administration des tags (protégé par mot de passe)**

Gère les tags de tous les personnages depuis un écran sécurisé !

**Fonctionnalités** :
- ✅ Protection par mot de passe (`bagbot`)
- ✅ Écran d'authentification élégant
- ✅ Liste de tous les personnages
- ✅ Ajout/modification de tags par personnage
- ✅ Suppression de tags
- ✅ Déconnexion sécurisée

**Accès** :
1. Ouvre les **Paramètres** ⚙️
2. Scroll en bas → Section **"Administration"** (rouge)
3. Clique sur **"Gestion des tags"**
4. Entre le mot de passe : `bagbot`

**Tags disponibles** :
- Âge
- Couleur cheveux
- Couleur yeux
- Type de corps
- Taille
- ... (extensible !)

---

### 🎨 **Guide génération NSFW via Dashboard Web**

Nouveau guide complet pour lancer la génération depuis le dashboard Freebox !

📁 **Fichier** : `LAUNCH_NSFW_VIA_DASHBOARD.md`

**Pourquoi ?**  
Le port SSH n'est pas accessible depuis Internet. Le dashboard web (http://88.174.155.230:33002) est la solution !

**Contenu** :
- ✅ Accès au dashboard (credentials)
- ✅ Vérification/démarrage ComfyUI
- ✅ Copie et lancement du script
- ✅ Suivi de la progression
- ✅ Récupération des images
- ✅ Intégration dans l'APK
- ✅ Troubleshooting complet

---

## 🔧 Améliorations techniques

### Architecture

**Nouveaux fichiers** :
- `AdminTagsScreen.kt` - UI administration tags
- `AdminTagsViewModel.kt` - Logique authentification + tags
- `LAUNCH_NSFW_VIA_DASHBOARD.md` - Guide dashboard web

**Fichiers modifiés** :
- `CreateCharacterViewModel.kt` - Logs de debug sauvegarde
- `CustomCharactersViewModel.kt` - Logs de debug chargement
- `NarutoAIChatApp.kt` - Navigation corrigée + route ADMIN_TAGS
- `SettingsScreen.kt` - Section administration + callback

### Logs de debug

**CreateCharacterViewModel** :
```kotlin
android.util.Log.d("CreateCharacterVM", "✅ Personnage sauvegardé: ${character.name}")
```

**CustomCharactersViewModel** :
```kotlin
android.util.Log.d("CustomCharactersVM", "📋 Personnages chargés: ${list.size}")
```

### Sécurité

- Mot de passe admin : `bagbot` (même que paramètres)
- Protection par `StateFlow<Boolean>` pour authentification
- Déconnexion automatique si navigation retour
- TODO : Chiffrement mot de passe dans SharedPreferences

---

## 📱 Installation

Téléchargez l'APK depuis les [Releases GitHub](https://github.com/mel805/naruto-ai-chat/releases/tag/v2.32.0)

---

## 🐛 Bugs corrigés

### 1. Personnages créés invisibles ✅

**Problème** : Après création d'un personnage personnalisé, il n'apparaissait pas dans la liste.

**Cause** : Navigation incorrecte (retour à CHARACTER_SELECTION au lieu de CUSTOM_CHARACTERS_LIST).

**Fix** : Redirection automatique vers la liste après création.

### 2. Flow pas à jour ✅

**Problème** : Les personnages n'apparaissaient pas en temps réel.

**Cause** : Flow Room pas optimisé.

**Fix** : Logs ajoutés + `onEach` pour débugger, `SharingStarted.WhileSubscribed(5000)` pour réactivité.

---

## 🎯 TODO restants

1. **Implémentation complète tags** - Sauvegarde persistante des tags (Room ou SharedPreferences)
2. **Édition personnages** - Modifier un personnage existant
3. **Chat avec personnages personnalisés** - Convertir `CustomCharacterEntity` → `Character`
4. **Export/Import personnages** - Partager entre utilisateurs
5. **Galeries NSFW** - Exécuter script sur Freebox via dashboard

---

## 📚 Documentation

- **Release notes complètes** : `RELEASE_NOTES_v2.32.0.md`
- **Guide NSFW Dashboard** : `LAUNCH_NSFW_VIA_DASHBOARD.md`
- **Guide Freebox SSH** : `FREEBOX_NSFW_GENERATION_GUIDE.md`
- **Setup Groq API** : `GROQ_API_SETUP.md`

---

## 💬 Support

Pour toute question ou problème :
- **Issues GitHub** : [Créer une issue](https://github.com/mel805/naruto-ai-chat/issues)
- **Logs** : Activez les logs dans Paramètres et vérifiez Logcat

---

**Développé avec ❤️ pour la communauté Naruto AI Chat**

Version : 2.32.0  
Date : 30 décembre 2025  
Build : 56
