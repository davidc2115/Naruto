# 🔑 Configuration Multi-Clés Groq - Rotation Automatique

**Nouveau système de rotation automatique de clés API pour éviter les limites!**

---

## 🎯 Pourquoi Multi-Clés?

**Problème:** Groq gratuit = 14,400 requêtes/jour par clé

**Solution:** 
- ✅ **Ajouter plusieurs clés** → Capacité multipliée
- ✅ **Rotation automatique** → Pas de coupure de service
- ✅ **Gestion intelligente** → Détection et évitement des erreurs
- ✅ **Statistiques en temps réel** → Surveillance de l'utilisation

---

## 📊 Avantages

| Nombre de Clés | Requêtes/Jour | Utilisateurs Supportés |
|----------------|---------------|------------------------|
| 1 clé          | 14,400        | ~50-100 utilisateurs   |
| 2 clés         | 28,800        | ~100-200 utilisateurs  |
| 3 clés         | 43,200        | ~150-300 utilisateurs  |
| 5 clés         | 72,000        | ~250-500 utilisateurs  |

---

## 🚀 Configuration

### Étape 1: Obtenir Plusieurs Clés Groq

**Option A: Plusieurs Comptes (Recommandé)**

1. Créer 3-5 comptes Groq différents:
   - Email 1: votre-email@gmail.com
   - Email 2: votre-email+groq1@gmail.com
   - Email 3: votre-email+groq2@gmail.com
   - etc.

2. Pour chaque compte:
   - Aller sur https://console.groq.com/keys
   - Créer une clé API
   - Copier la clé (commence par `gsk_`)

**Option B: Demander à des Amis**

1. Demander à 2-3 amis de créer un compte Groq
2. Récupérer leurs clés API
3. Partager l'accès à l'app

---

### Étape 2: Ajouter les Clés dans l'App

1. **Ouvrir l'app** "Naruto AI Chat"
2. **Cliquer sur ⚙️** (Paramètres en haut à droite)
3. **Section "Clés API Groq"**
4. **Cliquer** "Ajouter une clé Groq"
5. **Coller la clé** (doit commencer par `gsk_`)
6. **Répéter** pour chaque clé

**Nombre recommandé:** 3-5 clés

---

### Étape 3: Tester la Connexion

1. Dans Paramètres
2. Cliquer **"Tester la connexion"**
3. Attendre ✅ **"Connexion réussie!"**
4. **C'est prêt!**

---

## 🔄 Comment Fonctionne la Rotation?

### Rotation Automatique

Le système change de clé automatiquement quand:

1. **Rate Limit Atteint (429)**
   - Détection instantanée
   - Passage à la clé suivante
   - Pas d'interruption pour l'utilisateur

2. **Erreur d'Authentification (401)**
   - Clé invalide détectée
   - Rotation vers clé valide
   - Notification à l'utilisateur

3. **3 Erreurs Consécutives**
   - Clé temporairement désactivée
   - Rotation vers clé saine
   - Réactivation après succès

### Algorithme de Rotation

```
┌─────────────┐
│   Clé 1     │ ◄── Clé active
│ (Active)    │
└─────────────┘
       │
       ▼ (Rate limit atteint)
┌─────────────┐
│   Clé 2     │ ◄── Rotation automatique
│ (Active)    │
└─────────────┘
       │
       ▼ (Rate limit atteint)
┌─────────────┐
│   Clé 3     │ ◄── Continue la rotation
│ (Active)    │
└─────────────┘
       │
       ▼ (Retour au début)
┌─────────────┐
│   Clé 1     │
│ (Active)    │
└─────────────┘
```

---

## 📱 Interface de Gestion

### Écran Paramètres

**Section "Clés API Groq"**
- ➕ **Ajouter une clé** : Ajouter nouvelle clé
- ✅ **Tester connexion** : Vérifier que tout fonctionne
- 📊 **Statistiques** : Voir l'utilisation de chaque clé

**Carte de Clé**
```
┌─────────────────────────────────┐
│ gsk_abc1...xyz9     [ACTIVE]    │
│ ✅ 1,234 réussies • ❌ 2 erreurs │
│                           [🗑️]   │
└─────────────────────────────────┘
```

- **Clé masquée** : Sécurité (montre seulement début et fin)
- **Badge ACTIVE** : Clé actuellement utilisée
- **Statistiques** : Compteur de succès et erreurs
- **Supprimer** : Retirer une clé

---

## 🎨 Nouvelle Fonctionnalité: Images & Vidéos

### Configuration Replicate API

**Étape 1: Obtenir une Clé**

1. Aller sur **https://replicate.com**
2. Créer un compte (gratuit)
3. Aller dans **Account → API Tokens**
4. Copier le token (commence par `r8_`)

**Étape 2: Configurer dans l'App**

1. Ouvrir **Paramètres**
2. Section **"Clé API Replicate"**
3. Coller la clé
4. Cliquer **"Sauvegarder"**

---

### Utiliser la Génération d'Images/Vidéos

**Dans une Conversation:**

1. Ouvrir un chat avec un personnage
2. Cliquer sur **📸** (icône photo) en haut
3. Choisir:
   - **📸 Générer Image** : Crée une image basée sur la conversation
   - **🎬 Générer Vidéo** : Crée une vidéo courte animée

**Temps de Génération:**
- 🖼️ **Image** : ~30-60 secondes
- 🎬 **Vidéo** : ~2-4 minutes

---

## 💰 Coûts

### Groq (Chat)
- **Gratuit** : 14,400 req/jour par clé
- **Coût avec 3 clés** : 0€
- **Limite totale** : 43,200 req/jour

### Replicate (Images/Vidéos)
- **Gratuit** : $5 de crédit initial
- **Images** : ~$0.002 par image (~2,500 images gratuites)
- **Vidéos** : ~$0.02 par vidéo (~250 vidéos gratuites)

**Recommandation:** Utiliser images/vidéos avec modération

---

## 🔍 Monitoring

### Voir les Statistiques

**Dans Paramètres:**

Chaque clé affiche:
- ✅ **Nombre de réussites** : Requêtes traitées avec succès
- ❌ **Nombre d'erreurs** : Tentatives échouées
- 🟢 **Status ACTIVE** : Clé actuellement utilisée

**Interpréter les Stats:**

```
✅ 5,000 réussies • ❌ 2 erreurs  ← Excellente clé
✅ 14,300 réussies • ❌ 45 erreurs ← Proche de la limite
✅ 0 réussies • ❌ 15 erreurs      ← Clé problématique (à retirer)
```

---

## 🆚 Comparaison: Freebox vs Groq Multi-Clés

| Feature                  | Freebox TinyLlama | Groq Multi-Clés |
|--------------------------|-------------------|-----------------|
| **Configuration**        | Complexe          | ✅ Simple       |
| **Vitesse**              | ~5-10 tok/s       | ✅ ~200 tok/s   |
| **Qualité**              | TinyLlama 1B      | ✅ Llama 70B    |
| **Maintenance**          | Oui (serveur)     | ✅ Non          |
| **Limites**              | ∞ Illimité        | 14K-72K/jour    |
| **Setup temps**          | 30-60 min         | ✅ 2-5 min      |
| **Disponibilité**        | Dépend serveur    | ✅ 99.9%        |
| **Images/Vidéos**        | ❌ Non            | ✅ Oui          |

**Verdict:** Groq Multi-Clés est **meilleur pour la plupart des cas**

---

## 🛠️ Dépannage

### Erreur "Aucune clé API configurée"

**Solution:**
1. Aller dans Paramètres
2. Ajouter au moins 1 clé Groq
3. Tester la connexion

---

### Toutes les Clés Montrent des Erreurs

**Causes possibles:**
1. **Toutes les clés ont atteint la limite** → Attendre 24h
2. **Clés invalides** → Vérifier sur console.groq.com
3. **Problème réseau** → Vérifier connexion Internet

**Solution:**
- Ajouter de nouvelles clés
- Attendre le reset quotidien (minuit UTC)
- Vérifier la connexion

---

### Génération Image/Vidéo Échoue

**Vérifier:**
1. ✅ Clé Replicate configurée
2. ✅ Clé commence par `r8_`
3. ✅ Crédit disponible sur replicate.com
4. ✅ Bonne connexion Internet

---

### "Rate limit exceeded"

**C'est normal!** Le système gère automatiquement:
1. Détecte le rate limit
2. Passe à la clé suivante
3. Continue sans interruption

**Si ça arrive souvent:**
- Ajouter plus de clés
- Réduire l'utilisation

---

## 💡 Astuces & Optimisations

### Optimiser l'Utilisation

1. **3-5 clés recommandées** pour usage régulier
2. **Nettoyer l'historique** pour économiser tokens
3. **Messages courts** = moins de tokens
4. **Générer images avec modération** (coût Replicate)

### Partage avec Amis

**Si vous partagez l'app:**
- Chaque ami peut ajouter sa propre clé Groq
- Configuration locale (pas de serveur partagé)
- Chacun gère ses propres clés

---

## 📈 Capacité par Nombre de Clés

### Calcul des Capacités

**Hypothèses:**
- Conversation moyenne = 10 messages
- Message moyen = 30 tokens requête + 100 tokens réponse
- Limite Groq = 14,400 req/jour par clé

| Clés | Req/Jour | Conversations/Jour | Utilisateurs Actifs |
|------|----------|-------------------|---------------------|
| 1    | 14,400   | ~1,440            | 50-100              |
| 2    | 28,800   | ~2,880            | 100-200             |
| 3    | 43,200   | ~4,320            | 150-300             |
| 5    | 72,000   | ~7,200            | 250-500             |
| 10   | 144,000  | ~14,400           | 500-1000            |

---

## ✅ Checklist Configuration Complète

- [ ] Créé 3-5 comptes Groq
- [ ] Obtenu 3-5 clés API (commencent par `gsk_`)
- [ ] Ajouté toutes les clés dans l'app
- [ ] Testé la connexion ✅
- [ ] Créé compte Replicate (optionnel)
- [ ] Ajouté clé Replicate (commence par `r8_`)
- [ ] Testé génération d'image
- [ ] Vérifié les statistiques des clés
- [ ] **PROFITER de conversations illimitées!** 🎉

---

## 🎊 C'est Tout!

**Vous avez maintenant:**
- ✅ Système multi-clés avec rotation automatique
- ✅ Capacité multipliée (14K → 43K+ requêtes/jour)
- ✅ Génération d'images et vidéos
- ✅ Meilleure qualité (Llama 70B vs TinyLlama 1B)
- ✅ Plus rapide (200 tok/s vs 5-10 tok/s)
- ✅ Aucune maintenance de serveur

**Fini la Freebox!** 🚀

---

## 🔗 Liens Utiles

**Groq:**
- Console: https://console.groq.com
- API Keys: https://console.groq.com/keys
- Documentation: https://console.groq.com/docs

**Replicate:**
- Site: https://replicate.com
- API Tokens: https://replicate.com/account/api-tokens
- Models: https://replicate.com/explore

---

**Date:** 26 Décembre 2025  
**Version:** 2.0.0  
**Status:** ✅ Production Ready  
**Ancien Système:** Freebox TinyLlama (Retiré)  
**Nouveau Système:** Groq Multi-Clés + Replicate  
