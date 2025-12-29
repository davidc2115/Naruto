#!/bin/bash

echo "🚀 AUTO-PUBLICATION DES RELEASES v2.18, v2.19, v2.20"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

cd /workspace

# Fonction pour vérifier le rate limit
check_rate_limit() {
    remaining=$(gh api rate_limit --jq '.resources.core.remaining' 2>/dev/null)
    reset=$(gh api rate_limit --jq '.resources.core.reset' 2>/dev/null)
    reset_time=$(date -d "@$reset" "+%H:%M:%S" 2>/dev/null)
    echo "Rate limit: $remaining requêtes restantes (reset à $reset_time UTC)"
    echo "$remaining"
}

# Attendre que le rate limit soit OK
echo "⏳ Vérification du rate limit..."
while true; do
    remaining=$(check_rate_limit)
    
    if [ "$remaining" -ge 10 ]; then
        echo "✅ Rate limit OK ! ($remaining requêtes disponibles)"
        break
    else
        echo "⏳ Rate limit bas ($remaining), attente 60 secondes..."
        sleep 60
    fi
done

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📦 PUBLICATION DES RELEASES"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# v2.18.0
echo "📦 Publication v2.18.0..."
if gh release create v2.18.0 \
    --title "v2.18.0 - Fix NSFW + UI Clavier + Vitesse 3x" \
    --notes-file RELEASE_NOTES_v2.18.0.md \
    /tmp/apks/v2.18/naruto-ai-chat-apk/Naruto-AI-Chat-v2.18.0.apk 2>&1; then
    echo "✅ v2.18.0 publié !"
else
    echo "❌ Échec v2.18.0"
fi

echo ""
sleep 5

# v2.19.0
echo "📦 Publication v2.19.0..."
if gh release create v2.19.0 \
    --title "v2.19.0 - Vidéo SFW/NSFW + ComfyUI Optimisé 🎬" \
    --notes-file RELEASE_NOTES_v2.19.0.md \
    /tmp/apks/v2.19/naruto-ai-chat-apk/Naruto-AI-Chat-v2.19.0.apk 2>&1; then
    echo "✅ v2.19.0 publié !"
else
    echo "❌ Échec v2.19.0"
fi

echo ""
sleep 5

# v2.20.0
echo "📦 Publication v2.20.0..."
if gh release create v2.20.0 \
    --title "v2.20.0 - FIX NSFW ULTRA-RENFORCÉ 🔞" \
    --notes-file RELEASE_NOTES_v2.20.0.md \
    /tmp/apks/v2.20/naruto-ai-chat-apk/Naruto-AI-Chat-v2.20.0.apk 2>&1; then
    echo "✅ v2.20.0 publié !"
else
    echo "❌ Échec v2.20.0"
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎉 TERMINÉ !"
echo ""
echo "🔗 Vérifie ici : https://github.com/mel805/naruto-ai-chat/releases"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
