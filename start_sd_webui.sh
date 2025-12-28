#!/bin/bash
# Script de démarrage SD WebUI sur Freebox
# À exécuter sur la Freebox : bash start_sd_webui.sh

echo "🚀 Démarrage Stable Diffusion WebUI sur port 33437"
echo "=================================================="

# Vérifier si déjà lancé
if pgrep -f "webui.sh" > /dev/null; then
    echo "⚠️  SD WebUI déjà en cours d'exécution"
    echo "PID: $(pgrep -f webui.sh)"
    echo ""
    echo "Pour redémarrer, tuer d'abord le processus:"
    echo "  pkill -f webui.sh"
    exit 1
fi

# Aller dans le dossier
cd /root/stable-diffusion-webui || {
    echo "❌ Dossier /root/stable-diffusion-webui introuvable"
    echo ""
    echo "Pour installer SD WebUI:"
    echo "  cd /root"
    echo "  git clone https://github.com/AUTOMATIC1111/stable-diffusion-webui.git"
    exit 1
}

echo "✓ Dossier SD WebUI trouvé"

# Créer le fichier de config si nécessaire
cat > webui-user.sh << 'EOF'
#!/bin/bash
export COMMANDLINE_ARGS="--listen --port 33437 --skip-torch-cuda-test --no-half --api --xformers"
EOF
chmod +x webui-user.sh

echo "✓ Configuration créée"

# Lancer en arrière-plan
nohup ./webui.sh > /tmp/sd-webui.log 2>&1 &
SD_PID=$!

echo "✓ SD WebUI démarré (PID: $SD_PID)"
echo ""
echo "📋 Commandes utiles:"
echo "  Logs:     tail -f /tmp/sd-webui.log"
echo "  Status:   ps aux | grep webui"
echo "  Arrêter:  kill $SD_PID"
echo "  Tester:   curl http://localhost:33437"
echo ""
echo "⏱️  Première exécution: 10-30 minutes (téléchargement)"
echo "⏱️  Exécutions suivantes: 2-3 minutes"
echo ""
echo "🌐 Une fois prêt, accessible sur:"
echo "   http://88.174.155.230:33437"

# Attendre 5 secondes et afficher début des logs
sleep 5
echo ""
echo "📝 Premiers logs:"
echo "================="
tail -20 /tmp/sd-webui.log
