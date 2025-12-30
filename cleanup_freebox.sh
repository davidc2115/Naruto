#!/bin/bash
# Script nettoyage Freebox - Supprimer Stable Diffusion, garder Discord bot + dashboard

echo "╔════════════════════════════════════════════════════════╗"
echo "║  🧹 Nettoyage Freebox - Suppression Stable Diffusion  ║"
echo "╚════════════════════════════════════════════════════════╝"
echo ""

# 1. Arrêter processus ComfyUI et SD
echo "1️⃣ Arrêt processus Stable Diffusion..."
pkill -f comfy 2>/dev/null
pkill -f "stable-diffusion" 2>/dev/null
pkill -f "main.py" 2>/dev/null
echo "   ✅ Processus arrêtés"
echo ""

# 2. Supprimer ComfyUI
echo "2️⃣ Suppression ComfyUI..."
if [ -d "/home/bagbot/ComfyUI" ]; then
    SIZE=$(du -sh /home/bagbot/ComfyUI 2>/dev/null | cut -f1)
    echo "   📁 Taille: $SIZE"
    rm -rf /home/bagbot/ComfyUI
    echo "   ✅ ComfyUI supprimé"
else
    echo "   ⚠️  ComfyUI non trouvé"
fi
echo ""

# 3. Supprimer stable-diffusion-webui
echo "3️⃣ Suppression stable-diffusion-webui..."
if [ -d "/home/bagbot/stable-diffusion-webui" ]; then
    SIZE=$(du -sh /home/bagbot/stable-diffusion-webui 2>/dev/null | cut -f1)
    echo "   📁 Taille: $SIZE"
    rm -rf /home/bagbot/stable-diffusion-webui
    echo "   ✅ stable-diffusion-webui supprimé"
else
    echo "   ⚠️  stable-diffusion-webui non trouvé"
fi
echo ""

# 4. Supprimer modèles SD (fichiers volumineux)
echo "4️⃣ Recherche modèles .safetensors et .ckpt..."
find /home/bagbot -name "*.safetensors" -o -name "*.ckpt" 2>/dev/null | while read file; do
    SIZE=$(du -sh "$file" 2>/dev/null | cut -f1)
    echo "   🗑️  $file ($SIZE)"
    rm -f "$file"
done
echo "   ✅ Modèles supprimés"
echo ""

# 5. Nettoyer cache Python/pip
echo "5️⃣ Nettoyage cache Python..."
rm -rf /home/bagbot/.cache/pip 2>/dev/null
rm -rf /home/bagbot/.cache/huggingface 2>/dev/null
rm -rf /home/bagbot/.cache/torch 2>/dev/null
echo "   ✅ Cache Python nettoyé"
echo ""

# 6. Nettoyer /tmp
echo "6️⃣ Nettoyage /tmp..."
rm -rf /tmp/nsfw_gallery 2>/dev/null
rm -f /tmp/gen_nsfw.py /tmp/nsfw_gen.log /tmp/nsfw_gen.pid 2>/dev/null
rm -rf /tmp/ComfyUI* 2>/dev/null
echo "   ✅ /tmp nettoyé"
echo ""

# 7. Vérifier applications conservées
echo "7️⃣ Vérification applications conservées..."
echo "   ✅ Bag-bot: $([ -d /home/bagbot/Bag-bot ] && echo 'OK' || echo 'MANQUANT')"
echo "   ✅ dashboard-pro: $([ -d /home/bagbot/dashboard-pro ] && echo 'OK' || echo 'MANQUANT')"
echo "   ✅ bot: $([ -d /home/bagbot/bot ] && echo 'OK' || echo 'MANQUANT')"
echo ""

# 8. Vérifier processus Discord bot
echo "8️⃣ Processus Discord bot..."
ps aux | grep -E '(node|discord|pm2)' | grep -v grep | head -5
echo ""

# 9. Espace disque après nettoyage
echo "9️⃣ Espace disque après nettoyage..."
df -h / | tail -1
echo ""

# 10. RAM disponible
echo "🔟 RAM disponible..."
free -h | grep Mem
echo ""

echo "╔════════════════════════════════════════════════════════╗"
echo "║  ✅ NETTOYAGE TERMINÉ !                                ║"
echo "╚════════════════════════════════════════════════════════╝"
echo ""
echo "📊 Résumé:"
echo "   ✅ ComfyUI supprimé"
echo "   ✅ stable-diffusion-webui supprimé"
echo "   ✅ Modèles SD supprimés"
echo "   ✅ Cache Python nettoyé"
echo "   ✅ /tmp nettoyé"
echo "   ✅ Discord bot conservé"
echo "   ✅ Dashboard conservé"
echo ""
echo "🔄 Redémarrer services si nécessaire:"
echo "   pm2 list"
echo "   pm2 restart all"
