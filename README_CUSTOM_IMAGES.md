# 📸 Ajout des photos pour les personnages custom

## 🎯 Personnages concernés

Les 3 personnages custom prédéfinis nécessitent des photos hyperréalistes :

### 1. **Sofia Martinez** (colleague_tease)
- **Description** : Collègue espagnole taquine, 28 ans
- **Physique** : Cheveux bruns ondulés avec reflets caramel, yeux noisette pétillants, peau mate, silhouette élancée tonique, sourire espiègle
- **Style** : Business chic (chemisiers, jupes crayon, talons)
- **Fichier attendu** : `sofia_martinez.png` ou `sofia_martinez.jpg`

### 2. **Luna Chen** (mysterious_neighbor)
- **Description** : Voisine asiatique mystérieuse artiste, 26 ans
- **Physique** : Longs cheveux noirs lisses, grands yeux en amande bruns foncés, peau pâle laiteuse, silhouette mince gracieuse
- **Style** : Bohème (robes fluides, kimonos, oversized sweaters)
- **Fichier attendu** : `luna_chen.png` ou `luna_chen.jpg`

### 3. **Chloé Dubois** (friend_to_more)
- **Description** : Amie d'enfance française, 27 ans
- **Physique** : Cheveux châtains mi-longs avec mèches blondes, yeux verts expressifs, peau claire avec taches de rousseur, silhouette tonique athlétique
- **Style** : Décontracté-chic (jeans, t-shirts, baskets)
- **Fichier attendu** : `chloe_dubois.png` ou `chloe_dubois.jpg`

---

## 🛠️ Comment ajouter les photos

### Méthode 1 : Générer avec IA (Recommandé)
1. Utilisez un générateur d'images IA comme :
   - **Midjourney** (meilleure qualité, payant)
   - **Stable Diffusion** (gratuit, local)
   - **Leonardo.ai** (bon compromis)
   - **Pollinations AI** (gratuit)

2. Prompts suggérés :

**Sofia Martinez** :
```
Professional photograph of a beautiful 28-year-old Spanish woman, wavy brown hair with caramel highlights, hazel eyes with mischievous gaze, tanned glowing skin, elegant slim toned figure, wearing business chic outfit with fitted blouse and pencil skirt, high heels, confident posture, playful smile with dimple on left cheek, office background, natural lighting, professional photography, 8k, hyperrealistic
```

**Luna Chen** :
```
Professional photograph of a beautiful 26-year-old Asian woman, very long straight jet-black hair, large almond-shaped dark brown eyes, pale milky skin, delicate graceful features, slim dancer-like silhouette, wearing bohemian flowing dress, mysterious deep gaze, small lotus flower tattoo on shoulder blade, artistic and enigmatic aura, soft lighting, professional photography, 8k, hyperrealistic
```

**Chloé Dubois** :
```
Professional photograph of a beautiful 27-year-old French woman, shoulder-length light brown hair with natural blonde highlights in ponytail, expressive green eyes, fair skin with freckles on nose and cheeks, toned athletic figure, wearing casual-chic outfit with jeans and t-shirt, sneakers, natural beauty, genuine smile with adorable dimples, friendly and spontaneous expression, natural lighting, professional photography, 8k, hyperrealistic
```

### Méthode 2 : Utiliser des photos de banques d'images
1. Sites recommandés :
   - **Unsplash** (gratuit)
   - **Pexels** (gratuit)
   - **Pixabay** (gratuit)

2. Recherchez des modèles correspondant aux descriptions

### Méthode 3 : Utiliser des photos existantes
Si vous avez déjà des photos qui correspondent, utilisez-les !

---

## 📂 Installation des photos

### Étape 1 : Préparer les images
1. Renommez vos 3 photos :
   - `sofia_martinez.png` (ou `.jpg`)
   - `luna_chen.png` (ou `.jpg`)
   - `chloe_dubois.png` (ou `.jpg`)

2. Format recommandé :
   - **Résolution** : 1024x1024 ou plus
   - **Format** : PNG ou JPG
   - **Poids** : < 5 MB par image

### Étape 2 : Copier dans le projet
```bash
# Depuis la racine du projet
cp sofia_martinez.png app/src/main/res/drawable/
cp luna_chen.png app/src/main/res/drawable/
cp chloe_dubois.png app/src/main/res/drawable/
```

### Étape 3 : Recompiler l'app
```bash
./gradlew assembleRelease
```

Ou utilisez GitHub Actions en poussant un commit.

---

## ✅ Vérification

Après installation des photos :

1. **Onglet Explorer**
   - Les 3 personnages custom ont maintenant leurs photos

2. **Profils**
   - Photo principale affichée en grand

3. **Chat**
   - Photo en arrière-plan semi-transparent

---

## 🎨 Conseils pour de meilleures photos

### Qualité
- **Résolution** : Au moins 1024x1024px
- **Format** : PNG pour transparence, JPG pour fichiers légers
- **Éclairage** : Bien éclairé, sans ombres dures

### Style
- **Cadrage** : Portrait ou buste
- **Fond** : Uni ou légèrement flouté
- **Expression** : Correspondant à la personnalité du personnage

### Cohérence
- Même style photographique pour les 3
- Même qualité d'image
- Même type de cadrage

---

## 🚀 Alternative : Utiliser des placeholders temporaires

Si vous voulez tester rapidement sans images :

1. Copiez une image existante 3 fois :
```bash
cp app/src/main/res/drawable/emma.png app/src/main/res/drawable/sofia_martinez.png
cp app/src/main/res/drawable/scarlett.png app/src/main/res/drawable/luna_chen.png
cp app/src/main/res/drawable/margot.png app/src/main/res/drawable/chloe_dubois.png
```

2. Recompilez

3. Remplacez plus tard par les vraies photos

---

## ❓ FAQ

**Q : Puis-je utiliser des photos de célébrités réelles ?**  
R : Techniquement oui pour usage personnel, mais préférez des photos libres de droits ou générées par IA.

**Q : Quelle taille d'image maximum ?**  
R : Recommandé < 5 MB par image pour ne pas alourdir l'APK.

**Q : Puis-je utiliser des GIFs animés ?**  
R : Non, uniquement PNG ou JPG statiques.

**Q : Comment savoir si les photos sont bien installées ?**  
R : Après compilation, ouvrez l'app et vérifiez l'onglet Explorer.

---

## 📝 Status actuel

- ✅ Code mis à jour pour référencer les images
- ✅ Placeholders créés dans `/character_images/`
- ⏳ **Images hyperréalistes à ajouter manuellement**
- ⏳ Recompilation nécessaire après ajout

---

**Bon courage pour trouver/générer les photos parfaites ! 🎨📸**
