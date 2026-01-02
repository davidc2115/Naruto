# 📖 Guide : Configuration Google Gemini Vision API

## 🎯 Pourquoi Gemini Vision ?

Depuis janvier 2025, **TOUS les modèles Groq Vision sont décommissionnés**. L'app utilise désormais **Google Gemini Vision**, qui offre :

- ✅ **API GRATUITE** (pas de carte bancaire requise)
- ✅ **Quota généreux** : 60 requêtes/minute, 1500/jour
- ✅ **Modèle performant** : `gemini-1.5-flash-latest`
- ✅ **Support actif** : API officielle Google
- ✅ **Images jusqu'à 4MB** (vs 500KB pour Groq)

---

## 🚀 Obtenir une clé API (3 minutes)

### Étape 1 : Accéder à Google AI Studio
1. **Ouvrir** : [https://makersuite.google.com/app/apikey](https://makersuite.google.com/app/apikey)
2. **Se connecter** avec votre compte Google (gratuit, pas de CB)

### Étape 2 : Créer une clé
1. Cliquer sur **"Get API Key"** ou **"Create API Key"**
2. Sélectionner un projet (ou créer un nouveau)
3. La clé est générée instantanément (format : `AIzaSy...`)

### Étape 3 : Copier la clé
1. Cliquer sur **l'icône de copie** (📋)
2. **Conserver précieusement** cette clé

> ⚠️ **Important** : Ne partagez JAMAIS votre clé publiquement !

---

## 📱 Configuration dans l'app

### Méthode 1 : Via les Paramètres (recommandé)
1. Ouvrir l'app **Naruto AI Chat**
2. Aller dans **Paramètres** (⚙️ en haut à droite)
3. Section **"🆕 Google Gemini Vision"**
4. Coller votre clé dans le champ **"Clé API Google Gemini"**
5. Cliquer **"Enregistrer"**
6. ✅ Confirmation : "✅ Clé configurée: AIzaSy...xxxx"

### Méthode 2 : Directement depuis l'app
1. Créer un nouveau personnage (**"+ Créer"**)
2. Sélectionner une photo
3. Cliquer **"Analyser avec Gemini"**
4. Si pas de clé, un message s'affiche
5. Suivre le lien vers `makersuite.google.com`
6. Retourner dans **Paramètres** pour configurer

---

## 🎨 Utilisation : Analyser une photo

### Créer un personnage avec analyse
1. **Menu principal** > **"+ Créer"**
2. **Section Photo** :
   - Cliquer **"Choisir une photo"**
   - Sélectionner une image (portrait de préférence)
3. Cliquer **"🔍 Analyser avec Gemini"**
4. ⏳ **Analyse en cours...** (5-10 secondes)
5. ✅ **Résultat** :
   - Description physique complète générée
   - Champs auto-remplis (âge, cheveux, yeux, etc.)

### Informations extraites
L'analyse remplit automatiquement :
- **Âge** : Estimation ou tranche (ex: "25-30 ans")
- **Cheveux** : Couleur et style (ex: "blonds longs")
- **Yeux** : Couleur (ex: "bleus")
- **Teint** : Peau (ex: "clair", "mat", "foncé")
- **Morphologie** : Type de corps (ex: "athlétique", "mince")
- **Taille** : Estimation (ex: "moyenne ~170cm")
- **Traits distinctifs** : Tatouages, cicatrices, etc.
- **Description complète** : 2-3 phrases détaillées

---

## 🔒 Sécurité et confidentialité

### ✅ Stockage local uniquement
- La clé est **stockée localement** sur votre appareil
- Utilisation de **SharedPreferences** Android sécurisées
- **Aucune télémétrie** : Pas d'envoi vers serveurs tiers

### ✅ Affichage sécurisé
- Clé **masquée par défaut** dans les paramètres
- Affichage partiel : `AIzaSy...xxxx` (10 premiers + 4 derniers caractères)
- Bouton 👁️ pour révéler temporairement

### ✅ Communication chiffrée
- **HTTPS uniquement** pour toutes les requêtes
- Pas de stockage de vos photos sur nos serveurs
- Analyse **directe** avec l'API Google (pas d'intermédiaire)

---

## 📊 Limites et quotas

### Quotas gratuits (par projet)
- **60 requêtes/minute** (1 par seconde en moyenne)
- **1500 requêtes/jour** (largement suffisant)
- **Reset automatique** : Chaque jour à minuit UTC

### Cas d'usage typiques
| Usage | Requêtes/jour | Suffisant ? |
|-------|---------------|-------------|
| Créer 5 personnages/jour | 5 | ✅ Oui (0.3%) |
| Créer 20 personnages/jour | 20 | ✅ Oui (1.3%) |
| Créer 100 personnages/jour | 100 | ✅ Oui (6.7%) |
| Créer 500 personnages/jour | 500 | ✅ Oui (33%) |

> 💡 **Astuce** : 1500 requêtes/jour = analyser **1500 photos** ! Amplement suffisant.

### Si quota dépassé
- **Erreur** : "Quota exceeded"
- **Solution** : Attendre le reset quotidien (minuit UTC)
- **Alternative** : Créer un nouveau projet Google avec nouvelle clé

---

## 🐛 Résolution de problèmes

### ❌ "Clé API manquante"
**Cause** : Aucune clé configurée  
**Solution** :
1. Obtenir une clé sur [makersuite.google.com](https://makersuite.google.com/app/apikey)
2. L'ajouter dans Paramètres > Google Gemini Vision

### ❌ "HTTP 400: Invalid API key"
**Cause** : Clé incorrecte ou mal copiée  
**Solution** :
1. Vérifier que la clé commence par `AIza`
2. Pas d'espaces avant/après
3. Régénérer une nouvelle clé si nécessaire

### ❌ "HTTP 403: API not enabled"
**Cause** : API Gemini pas activée pour ce projet  
**Solution** :
1. Aller sur [console.cloud.google.com](https://console.cloud.google.com)
2. Sélectionner votre projet
3. **APIs & Services** > **Library**
4. Chercher "Generative Language API"
5. Cliquer **"Enable"**

### ❌ "HTTP 429: Quota exceeded"
**Cause** : Plus de 60 req/min ou 1500 req/jour  
**Solution** :
1. Attendre quelques heures (reset à minuit UTC)
2. Ou créer un nouveau projet Google

### ❌ "Impossible de charger l'image"
**Cause** : Format non supporté ou image corrompue  
**Solution** :
- Utiliser **JPEG** ou **PNG**
- Taille recommandée : < 2MB (accepte jusqu'à 4MB)
- Résolution recommandée : 1024x1024 max

### ❌ "Aucune réponse générée"
**Cause** : Image trop floue ou contenu non analysable  
**Solution** :
- Utiliser un **portrait clair** et net
- Bon éclairage
- Visage bien visible

---

## 💡 Conseils pour une analyse optimale

### 📷 Qualité de la photo
- ✅ **Portrait frontal** ou 3/4 (meilleur angle)
- ✅ **Bon éclairage** : Lumière naturelle idéale
- ✅ **Haute résolution** : 1024x1024 minimum
- ✅ **Net et clair** : Pas de flou
- ❌ Éviter : Photos de groupe, contre-jour, flou

### 🎯 Type de personnages
Gemini analyse particulièrement bien :
- 👤 **Personnages réalistes** (photos, photomontages)
- 🎨 **Illustrations détaillées** (style semi-réaliste)
- 🖼️ **Art conceptuel** (concept art de qualité)
- ❓ Moins fiable : Dessins cartoon très stylisés

### ⚡ Optimisation des requêtes
- 📦 **Compresser** vos images avant upload (l'app le fait déjà)
- 🔄 **Éviter analyses répétées** de la même photo
- 💾 **Sauvegarder** la description générée (copier/coller)

---

## 🔄 Comparaison : Groq Vision vs Gemini Vision

| Critère | Groq Vision (OLD) | Gemini Vision (NEW) |
|---------|-------------------|---------------------|
| **Statut** | ❌ Tous décommissionnés | ✅ Actif et maintenu |
| **Gratuit** | ✅ Oui | ✅ Oui |
| **Quota** | ❓ Non spécifié | ✅ 60/min, 1500/jour |
| **Taille image max** | 500 KB | 4 MB (8x plus) |
| **Qualité** | Bonne | Excellente |
| **Documentation** | Limitée | Complète |
| **Support** | ❌ Fin de vie | ✅ Google officiel |
| **Vitesse** | Rapide (~5s) | Rapide (~5-10s) |
| **Précision** | ~80% | ~90% |

---

## 📚 Ressources officielles

### Documentation Google
- **Guide API** : [ai.google.dev/gemini-api/docs](https://ai.google.dev/gemini-api/docs)
- **Vision spécifique** : [ai.google.dev/gemini-api/docs/vision](https://ai.google.dev/gemini-api/docs/vision)
- **Pricing & Quotas** : [ai.google.dev/gemini-api/docs/pricing](https://ai.google.dev/gemini-api/docs/pricing)

### Obtenir une clé
- **Google AI Studio** : [makersuite.google.com/app/apikey](https://makersuite.google.com/app/apikey)
- **Console Cloud** : [console.cloud.google.com](https://console.cloud.google.com)

### Support
- **GitHub Issues** : [Votre repo GitHub]
- **Discord** : [Votre serveur Discord si applicable]

---

## 🎉 Prêt à créer des personnages !

Une fois votre clé configurée :

1. ✅ **Analyser des photos** instantanément
2. ✅ **Créer des personnages** détaillés
3. ✅ **Gagner du temps** (auto-remplissage)
4. ✅ **Profiter** de 1500 analyses/jour gratuites !

---

## 🆘 Besoin d'aide ?

### Questions fréquentes
**Q: Dois-je payer ?**  
R: Non ! L'API Gemini est 100% gratuite dans les limites du quota (1500/jour).

**Q: Faut-il une carte bancaire ?**  
R: Non, juste un compte Google gratuit.

**Q: Mes photos sont-elles stockées ?**  
R: Non, elles sont analysées en temps réel et jamais sauvegardées par l'app ou Google.

**Q: Puis-je partager ma clé ?**  
R: Non, gardez-la privée. Chaque personne doit créer sa propre clé.

**Q: Combien de clés puis-je créer ?**  
R: Autant que vous voulez (une par projet Google).

**Q: Groq fonctionne-t-il encore pour le chat ?**  
R: Oui ! Groq reste utilisé pour les conversations (inchangé).

---

_Guide mis à jour pour v2.41.0 - Janvier 2025_
