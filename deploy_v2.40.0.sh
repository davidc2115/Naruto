#!/bin/bash

# Script de déploiement Naruto AI Chat v2.40.0
# Usage: ./deploy_v2.40.0.sh

set -e  # Exit on error

echo "🚀 ===== DÉPLOIEMENT NARUTO AI CHAT v2.40.0 ====="
echo ""

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Step 1: Git Status
echo -e "${YELLOW}📋 Étape 1: Vérification Git${NC}"
git status
echo ""
read -p "Continuer avec le commit ? (y/n) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]
then
    echo -e "${RED}❌ Annulé${NC}"
    exit 1
fi

# Step 2: Git Add
echo -e "${YELLOW}📦 Étape 2: Ajout des fichiers${NC}"
git add -A
echo -e "${GREEN}✅ Fichiers ajoutés${NC}"
echo ""

# Step 3: Git Commit
echo -e "${YELLOW}💾 Étape 3: Commit${NC}"
git commit -m "feat(v2.40.0): Nouveaux personnages + génération images ultra-améliorée

NOUVEAUX PERSONNAGES (3):
- Sofia Martinez: Collègue espagnole taquine et séduisante (28 ans)
- Luna Chen: Voisine mystérieuse et artiste (26 ans)
- Chloé Dubois: Amie d'enfance, relation qui évolue (27 ans)

Tous avec descriptions physiques ultra-détaillées, scénarios complets,
system prompts SFW/NSFW soft, et background stories approfondis.

GÉNÉRATION D'IMAGES AMÉLIORÉE:
- Prise en compte description physique COMPLÈTE du personnage
- Déduction automatique de la TENUE depuis conversation
- Déduction automatique de la POSE/ACTION depuis contexte
- Déduction automatique du LIEU/SETTING depuis dialogue
- Expression faciale adaptée au mood conversation
- Mode NSFW suggestif/sensuel (pas explicite)
- Prompts Groq optimisés avec instructions détaillées

VIGNETTES & GALERIES:
- Fonction generateCharacterThumbnail() via Pollination AI
- Fonction generateCharacterGallery() avec 6 variations automatiques
- Support SFW et NSFW soft

TECHNIQUE:
- Refactorisation ChatViewModel.generateImageFromConversation()
- System prompts Groq expert optimisés
- Max 100 mots pour prompts détaillés sans surcharge
- Cohérence totale des personnages générés

FIXES (v2.39.4 incluse):
- Système de fallback Groq Vision (3 modèles)
- Documentation mise à jour

Version: 2.39.4 → 2.40.0
Build: 68 → 69
Date: 2 janvier 2026"

echo -e "${GREEN}✅ Commit créé${NC}"
echo ""

# Step 4: Git Push
echo -e "${YELLOW}🚀 Étape 4: Push vers GitHub${NC}"
echo "Quelle branche voulez-vous push ?"
echo "1) cursor/api-model-error-fix-50fb (branche actuelle)"
echo "2) main"
echo "3) Autre"
read -p "Choix (1/2/3): " branch_choice

case $branch_choice in
    1)
        BRANCH="cursor/api-model-error-fix-50fb"
        ;;
    2)
        BRANCH="main"
        # Merge d'abord si on push sur main
        echo -e "${YELLOW}🔀 Merge vers main...${NC}"
        CURRENT_BRANCH=$(git branch --show-current)
        git checkout main
        git merge $CURRENT_BRANCH
        ;;
    3)
        read -p "Nom de la branche: " BRANCH
        ;;
    *)
        echo -e "${RED}❌ Choix invalide${NC}"
        exit 1
        ;;
esac

echo -e "${YELLOW}Pushing vers $BRANCH...${NC}"
git push origin $BRANCH

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Push réussi !${NC}"
else
    echo -e "${RED}❌ Erreur lors du push${NC}"
    echo "Essayez manuellement : git push origin $BRANCH"
    exit 1
fi
echo ""

# Step 5: Build APK
echo -e "${YELLOW}🏗️  Étape 5: Build APK${NC}"
read -p "Voulez-vous build l'APK maintenant ? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]
then
    echo -e "${YELLOW}🔨 Nettoyage...${NC}"
    ./gradlew clean
    
    echo -e "${YELLOW}🔨 Build APK Release...${NC}"
    ./gradlew assembleRelease
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ APK construit avec succès !${NC}"
        echo -e "📦 APK: ${GREEN}app/build/outputs/apk/release/app-release.apk${NC}"
        
        # Copier l'APK avec un nom propre
        cp app/build/outputs/apk/release/app-release.apk naruto-ai-chat-v2.40.0.apk
        echo -e "📦 Copié vers: ${GREEN}naruto-ai-chat-v2.40.0.apk${NC}"
    else
        echo -e "${RED}❌ Erreur lors du build${NC}"
        echo "Essayez manuellement : ./gradlew assembleRelease"
        exit 1
    fi
else
    echo -e "${YELLOW}⏭️  Build APK skippé${NC}"
fi
echo ""

# Step 6: GitHub Release
echo -e "${YELLOW}📦 Étape 6: GitHub Release${NC}"
echo "Pour créer la release GitHub :"
echo ""
echo -e "${GREEN}Option A - Via GitHub CLI (si installé):${NC}"
echo "  gh release create v2.40.0 \\"
echo "    --title \"v2.40.0 - Nouveaux Personnages + Génération Images Ultra-Améliorée\" \\"
echo "    --notes-file release_notes_v2.40.0.md \\"
echo "    naruto-ai-chat-v2.40.0.apk"
echo ""
echo -e "${GREEN}Option B - Via interface web:${NC}"
echo "  1. Allez sur: https://github.com/mel805/naruto-ai-chat/releases/new"
echo "  2. Tag: v2.40.0"
echo "  3. Title: v2.40.0 - Nouveaux Personnages + Génération Images Ultra-Améliorée"
echo "  4. Description: Copier depuis release_notes_v2.40.0.md"
echo "  5. Upload: naruto-ai-chat-v2.40.0.apk"
echo "  6. Publish release"
echo ""

read -p "Voulez-vous essayer via GitHub CLI ? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]
then
    if command -v gh &> /dev/null
    then
        echo -e "${YELLOW}🚀 Création release via gh CLI...${NC}"
        gh release create v2.40.0 \
            --title "v2.40.0 - Nouveaux Personnages + Génération Images Ultra-Améliorée" \
            --notes-file release_notes_v2.40.0.md \
            naruto-ai-chat-v2.40.0.apk
        
        if [ $? -eq 0 ]; then
            echo -e "${GREEN}✅ Release créée !${NC}"
            echo ""
            echo -e "🔗 ${GREEN}Lien de la release:${NC}"
            echo "https://github.com/mel805/naruto-ai-chat/releases/tag/v2.40.0"
        else
            echo -e "${RED}❌ Erreur création release${NC}"
            echo "Créez la release manuellement via l'interface web"
        fi
    else
        echo -e "${RED}❌ GitHub CLI (gh) n'est pas installé${NC}"
        echo "Créez la release manuellement via l'interface web"
    fi
else
    echo -e "${YELLOW}⏭️  Création release skippée${NC}"
    echo "Créez la release manuellement via l'interface web"
fi
echo ""

# Final Summary
echo -e "${GREEN}🎉 ===== DÉPLOIEMENT TERMINÉ =====${NC}"
echo ""
echo -e "${GREEN}✅ Code pushé vers GitHub${NC}"
echo -e "   Branch: $BRANCH"
echo ""
echo -e "${GREEN}📦 APK prêt:${NC}"
echo -e "   naruto-ai-chat-v2.40.0.apk"
echo ""
echo -e "${GREEN}🔗 Prochaine étape:${NC}"
echo "   Créer la release sur GitHub (si pas déjà fait)"
echo "   https://github.com/mel805/naruto-ai-chat/releases/new"
echo ""
echo -e "${GREEN}📄 Documentation:${NC}"
echo "   - release_notes_v2.40.0.md"
echo "   - DEPLOYMENT_v2.40.0.md"
echo ""
echo -e "${YELLOW}🎭 Nouveaux personnages:${NC}"
echo "   - Sofia Martinez (collègue taquine)"
echo "   - Luna Chen (voisine mystérieuse)"
echo "   - Chloé Dubois (amie d'enfance)"
echo ""
echo -e "${YELLOW}🎨 Améliorations:${NC}"
echo "   - Génération images ultra-détaillée"
echo "   - Prise en compte tenue + pose + contexte"
echo "   - Vignettes et galeries automatiques"
echo ""
echo "✨ Merci et bon chat ! ✨"
