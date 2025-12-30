# 🎉 Naruto AI Chat - Version 2.30.0

## ✨ Nouveautés majeures

### 🎨 Création de personnages personnalisés

Vous pouvez maintenant **créer vos propres personnages** entièrement personnalisables !

#### Fonctionnalités

✅ **Informations de base**
- Nom
- Description courte
- Photo/Avatar (depuis galerie)

✅ **Apparence physique complète**
- Description physique détaillée
- Âge
- Taille
- Couleur des cheveux
- Couleur des yeux
- Type de corps

✅ **Personnalité & Contexte**
- Tempérament
- Scénario/Background
- Message d'accueil personnalisé

✅ **Analyse photo automatique (bêta)**
- Bouton "Analyser la photo" pour générer automatiquement le descriptif physique
- Détection d'âge, cheveux, yeux, morphologie (placeholder, intégration API vision à venir)

✅ **Sauvegarde persistante**
- Base de données Room pour stocker les personnages
- Les personnages créés restent disponibles après fermeture de l'app
- Modification et suppression possibles

✅ **Génération de prompts**
- Prompts SFW et NSFW générés automatiquement selon les caractéristiques
- Chat immédiat avec le personnage créé

#### Comment utiliser

1. Sur l'écran de sélection, appuyez sur le bouton **"+ Créer personnage"**
2. Remplissez les informations (nom et description minimum)
3. (Optionnel) Ajoutez une photo et cliquez sur "Analyser"
4. Sauvegardez
5. Votre personnage apparaît dans la liste !

---

## 🎨 Galeries NSFW (Freebox)

### Script de génération depuis Freebox

Nous avons créé un **script Python optimisé** pour générer les 39 galeries NSFW directement depuis votre Freebox avec ComfyUI local :

📁 **Fichiers créés** :
- `/workspace/freebox_nsfw_generator_local.py` - Script de génération
- `/workspace/FREEBOX_NSFW_GENERATION_GUIDE.md` - Guide complet

✅ **Avantages de la génération locale** :
- ⚡ Plus rapide (réseau local)
- 💰 Gratuit et illimité
- 🔓 Aucune censure
- 🎨 Contrôle total sur la qualité

📊 **Détails de génération** :
- 13 personnages (Naruto, Sakura, Hinata, Ino, Temari, Tsunade, Tenten, Konan, Kurenai, Anko, Kushina, Mikoto, Sasuke)
- 3 images par personnage
- **39 images NSFW au total**
- Résolution : 512x768
- Steps : 8 (optimisé pour ARM CPU)

🚀 **Pour lancer** (voir guide détaillé dans `FREEBOX_NSFW_GENERATION_GUIDE.md`) :
```bash
# SSH sur Freebox
ssh root@88.174.155.230

# Copier et lancer le script
python3 /root/freebox_nsfw_generator_local.py
```

⏱️ **Temps estimé** : 30-45 minutes pour les 39 images

---

## 🔧 Améliorations techniques

### Base de données Room
- **CustomCharacterDatabase** : Gestion persistante des personnages
- **CustomCharacterEntity** : Modèle de données complet
- **CustomCharacterRepository** : Accès aux données avec Coroutines Flow

### Architecture
- ViewModel dédié : `CreateCharacterViewModel`
- Écran UI Compose moderne : `CreateCharacterScreen`
- Navigation intégrée dans `NarutoAIChatApp`

### Dépendances ajoutées
- `androidx.room:room-runtime:2.6.1`
- `androidx.room:room-ktx:2.6.1`
- KSP (Kotlin Symbol Processing) pour Room

---

## 📝 Fichiers modifiés

### Nouveaux fichiers
- `app/src/main/java/com/narutoai/chat/data/CustomCharacterDatabase.kt`
- `app/src/main/java/com/narutoai/chat/viewmodel/CreateCharacterViewModel.kt`
- `app/src/main/java/com/narutoai/chat/ui/screens/CreateCharacterScreen.kt`
- `freebox_nsfw_generator_local.py`
- `FREEBOX_NSFW_GENERATION_GUIDE.md`
- `generate_nsfw_stable_horde.py` (alternatif)
- `generate_pollination_ultra_safe.py` (alternatif)

### Fichiers modifiés
- `app/build.gradle.kts` - Room + KSP
- `build.gradle.kts` - KSP plugin
- `app/src/main/java/com/narutoai/chat/ui/NarutoAIChatApp.kt` - Navigation
- `app/src/main/java/com/narutoai/chat/ui/screens/CharacterSelectionScreen.kt` - Bouton création

---

## 🐛 Corrections

Aucune correction de bug spécifique dans cette version - focus sur les nouvelles fonctionnalités.

---

## 📱 Installation

Téléchargez l'APK depuis les [Releases GitHub](https://github.com/Douv21/Naruto-ai-/releases/tag/v2.30.0)

---

## 🔮 Prochaines étapes

1. **Analyse photo IA complète** - Intégration API vision (Groq Vision, GPT-4 Vision)
2. **Liste des personnages personnalisés** - Écran dédié avec modification/suppression
3. **Import/Export** - Partager des personnages avec d'autres utilisateurs
4. **Galerie photo** - Plusieurs photos par personnage
5. **Finalisation galeries NSFW** - Récupération images depuis Freebox et intégration

---

## ⚠️ Notes importantes

### Analyse photo
L'analyse automatique de photo est en **bêta** (placeholder). Elle affiche un message mais ne génère pas encore de descriptif réel. L'intégration d'une vraie API de vision sera ajoutée dans une prochaine version.

### Galeries NSFW Freebox
Le script de génération doit être **exécuté manuellement sur votre Freebox** via SSH. Une fois les images générées, elles doivent être copiées dans le projet Android (`character_images/`) puis dans `drawable-nodpi/` avant de reconstruire l'APK.

---

## 💬 Support

Pour toute question ou problème :
- **Issues GitHub** : [Créer une issue](https://github.com/Douv21/Naruto-ai-/issues)
- **Logs** : Activez les logs dans Paramètres pour debug

---

**Développé avec ❤️ pour la communauté Naruto AI Chat**

Version : 2.30.0  
Date : 30 décembre 2025  
Build : 54
