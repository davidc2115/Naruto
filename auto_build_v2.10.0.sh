#!/bin/bash
# Script automatique: Attend la fin de génération → Met à jour Characters.kt → Build v2.10.0

echo "🤖 SCRIPT AUTOMATIQUE V2.10.0"
echo "Attend la génération des 195 images NSFW..."
echo ""

# Attendre que toutes les images soient générées
while true; do
    COUNT=$(ls /workspace/app/src/main/res/drawable-nodpi/*nsfw*.jpg 2>/dev/null | wc -l)
    
    if [ "$COUNT" -eq 195 ]; then
        echo "✅ 195/195 images générées!"
        break
    fi
    
    PERCENT=$((COUNT * 100 / 195))
    echo "⏳ Progression: $COUNT/195 ($PERCENT%) - Attente 60s..."
    sleep 60
done

echo ""
echo "════════════════════════════════════════════════════════════════════════════════"
echo "🔄 MISE À JOUR CHARACTERS.KT"
echo "════════════════════════════════════════════════════════════════════════════════"

cd /workspace
python3 update_characters_nsfw_galleries.py

if [ $? -eq 0 ]; then
    echo "✅ Characters.kt mis à jour avec succès"
else
    echo "❌ Erreur mise à jour Characters.kt"
    exit 1
fi

echo ""
echo "════════════════════════════════════════════════════════════════════════════════"
echo "📦 PRÉPARATION VERSION 2.10.0"
echo "════════════════════════════════════════════════════════════════════════════════"

# Mettre à jour version dans build.gradle.kts
sed -i 's/versionCode = 17/versionCode = 18/' app/build.gradle.kts
sed -i 's/versionName = "2.9.2"/versionName = "2.10.0"/' app/build.gradle.kts

echo "✅ Version mise à jour: 2.10.0 (code 18)"

echo ""
echo "════════════════════════════════════════════════════════════════════════════════"
echo "🔄 GIT COMMIT & PUSH"
echo "════════════════════════════════════════════════════════════════════════════════"

git add -A
git commit -m "v2.10.0: 195 Images NSFW + Galeries SFW/NSFW

🔞 CONTENU ADULTE
- 195 images NSFW (13 persos × 15 images)
- 5 sensuelles + 5 sexy + 5 nsfw par personnage
- Toggle SFW/NSFW dans CharacterProfileScreen
- galleryNSFW ajouté à tous les personnages

✨ NOUVELLES FONCTIONNALITÉS
- Galeries séparées SFW/NSFW
- Bouton toggle dans profil personnage
- 195 nouvelles images haute qualité
- Navigation fullscreen améliorée

🎨 AMÉLIORATIONS
- Compteur dynamique par galerie
- Affichage conditionnel selon mode
- Images optimisées (max 150KB)
"

git push origin cursor/groq-api-image-video-5770
git tag -f v2.10.0 -m "Release v2.10.0: 195 Images NSFW"
git push -f origin v2.10.0

echo "✅ Git commit & push terminés"

echo ""
echo "════════════════════════════════════════════════════════════════════════════════"
echo "🏗️ DÉCLENCHEMENT BUILD GITHUB ACTIONS"
echo "════════════════════════════════════════════════════════════════════════════════"
echo "Build en cours sur GitHub Actions..."
echo "URL: https://github.com/mel805/naruto-ai-chat/actions"

echo ""
echo "════════════════════════════════════════════════════════════════════════════════"
echo "✅ SCRIPT TERMINÉ"
echo "════════════════════════════════════════════════════════════════════════════════"
echo ""
echo "📋 ACTIONS EFFECTUÉES:"
echo "   ✅ 195 images NSFW générées"
echo "   ✅ Characters.kt mis à jour"
echo "   ✅ Version 2.10.0 préparée"
echo "   ✅ Commit & push effectués"
echo "   🔄 Build GitHub Actions en cours"
echo ""
echo "🎯 PROCHAINE ÉTAPE MANUELLE:"
echo "   → Attendre la fin du build GitHub Actions (~5 min)"
echo "   → Télécharger l'APK"
echo "   → Créer la release v2.10.0"
echo ""
