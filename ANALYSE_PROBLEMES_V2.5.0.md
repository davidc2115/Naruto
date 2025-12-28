## 🔍 ANALYSE COMPLÈTE DES PROBLÈMES

### ❌ Problème 1: Erreur 429
**Cause racine trouvée:**
1. Freebox (88.174.155.230:7860) ne répond PAS (timeout après 60s)
2. App bascule sur Pollination AI en fallback
3. Mais appelle Groq PUIS Pollination trop rapidement (< 1s d'écart)
4. Pollination AI rate-limite à 429

**Solution appliquée:**
- Ajout `delay(3000ms)` après Groq et avant Pollination AI
- Dans `generateImageFromConversation()` ligne ~250
- Dans `generateVideoFromConversation()` ligne ~380

### ❌ Problème 2: Vignettes non ressemblantes
**Cause:** Les images actuelles ne correspondent pas aux descriptions physiques exactes

**Solution requise:**
- Générer 13 nouvelles vignettes avec Stable Diffusion
- Basées sur `physicalDescription` de chaque Character
- MAIS: Freebox inaccessible, Pollination AI instable (502/429)

### ❌ Problème 3: Galleries incomplètes
**État actuel:**
- Naruto: 10 images ✅
- Sasuke: 8 images ✅  
- Hinata: 4 images ⚠️
- Itachi: 1 image ⚠️
- Sakura: 0 images ❌
- Kakashi: 0 images ❌
- Célébrités: 0 images ❌

## ✅ SOLUTIONS PROPOSÉES

### Option A: API Stable Diffusion intégrée (IMPOSSIBLE)
- Stable Diffusion nécessite 4-8 GB VRAM
- APK Android ne peut pas héberger un modèle de 2-4 GB
- **REJETÉ**

### Option B: Service cloud stable (RECOMMANDÉ)
1. Utiliser **HuggingFace Inference API** (gratuit, stable)
2. Ou **fal.ai** (rapide, fiable)
3. Intégrer dans `FreeboxMediaClient` comme fallback secondaire

### Option C: Pré-générer TOUTES les images (SOLUTION IMMÉDIATE)
1. Générer les 13 vignettes + galleries manquantes EN DEHORS de l'app
2. Les intégrer comme ressources dans l'APK
3. **APK final: ~30-40 MB** (acceptable)

## 📋 PLAN D'ACTION

Je recommande **Option C** pour résoudre IMMÉDIATEMENT :
1. ✅ Corriger délai 429 (FAIT)
2. 🔄 Générer 13 vignettes ressemblantes avec IA stable
3. 🔄 Générer galleries complètes (10 images/perso)
4. 🔄 Intégrer dans APK
5. 🔄 Build v2.5.0

**Voulez-vous que je procède avec l'Option C ?**
