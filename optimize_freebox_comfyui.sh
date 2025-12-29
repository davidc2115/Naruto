#!/bin/bash
# Script d'optimisation ComfyUI pour Freebox (ARM CPU, 964MB RAM)
# Usage: ./optimize_freebox_comfyui.sh

set -e

echo "🔧 Optimisation ComfyUI pour Freebox..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Configuration
FREEBOX_IP="88.174.155.230"
FREEBOX_PORT="33000"
FREEBOX_USER="bagbot"
FREEBOX_PASS="bagbot"
COMFYUI_DIR="/home/bagbot/ComfyUI"

echo ""
echo "📊 État système actuel:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Se connecter via SSH et afficher l'état
sshpass -p "$FREEBOX_PASS" ssh -p "$FREEBOX_PORT" "$FREEBOX_USER@$FREEBOX_IP" << 'ENDSSH'

echo "💾 Mémoire disponible:"
free -h

echo ""
echo "🔄 Processus ComfyUI:"
ps aux | grep -E "python.*main.py" | grep -v grep || echo "Aucun processus ComfyUI"

echo ""
echo "🛑 Arrêt des anciens processus..."
pkill -f "python.*main.py.*comfy" 2>/dev/null || echo "Aucun processus à arrêter"
sleep 2

echo ""
echo "🧹 Nettoyage mémoire..."
# Vider les caches système
sync
echo 3 > /proc/sys/vm/drop_caches 2>/dev/null || echo "Besoin de root pour drop_caches (ignoré)"

echo ""
echo "📝 Configuration optimisations ARM..."
cd ~/ComfyUI

# Créer un script de démarrage optimisé
cat > start_optimized.sh << 'EOF'
#!/bin/bash
# Démarrage ComfyUI optimisé pour ARM CPU

source ~/ComfyUI/venv/bin/activate

# Variables d'environnement pour limiter l'utilisation mémoire
export OMP_NUM_THREADS=2           # Limiter threads OpenMP (ARM a 4 cores)
export MKL_NUM_THREADS=2           # Limiter threads MKL
export NUMEXPR_NUM_THREADS=2       # Limiter threads NumExpr
export PYTORCH_ENABLE_MPS_FALLBACK=0  # Désactiver MPS (pas sur ARM)
export PYTORCH_NO_CUDA_MEMORY_CACHING=1  # Pas de cache CUDA

# Limiter l'utilisation mémoire Python
ulimit -v 700000  # Limite ~700MB de RAM virtuelle

# Démarrer ComfyUI avec options optimisées
python main.py \
  --listen 0.0.0.0 \
  --port 33437 \
  --lowvram \
  --cpu \
  --preview-method none \
  --disable-xformers \
  --dont-upcast-attention \
  --cache-lru 1 \
  > ~/comfyui_optimized.log 2>&1 &

echo "PID: $!"
echo "ComfyUI démarré en mode optimisé ARM"
EOF

chmod +x start_optimized.sh

echo ""
echo "🚀 Démarrage ComfyUI optimisé..."
nohup bash start_optimized.sh &
sleep 5

echo ""
echo "✅ ComfyUI redémarré avec optimisations:"
echo "  - Threads limités: 2 (au lieu de 4)"
echo "  - RAM limitée: ~700MB max"
echo "  - Cache désactivé"
echo "  - Preview désactivé (économie RAM)"
echo "  - XFormers désactivé (pas compatible ARM)"

echo ""
echo "📋 Processus actuel:"
ps aux | grep -E "python.*main.py" | grep -v grep

echo ""
echo "📄 Logs (10 dernières lignes):"
tail -10 ~/comfyui_optimized.log 2>&1 || echo "Pas encore de logs"

ENDSSH

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Optimisation terminée !"
echo ""
echo "🔍 Pour vérifier l'état:"
echo "   ssh -p 33000 bagbot@88.174.155.230"
echo "   tail -f ~/comfyui_optimized.log"
echo ""
echo "🌐 URL ComfyUI:"
echo "   http://88.174.155.230:33437"
echo ""
