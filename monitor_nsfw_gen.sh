#!/bin/bash
# Monitoring de la génération des 195 images NSFW

echo "🔄 Monitoring génération NSFW..."
echo "Appuyez sur Ctrl+C pour arrêter"
echo ""

while true; do
    clear
    echo "════════════════════════════════════════════════════════════════════════════════"
    echo "   🔞 GÉNÉRATION IMAGES NSFW - MONITORING EN DIRECT"
    echo "════════════════════════════════════════════════════════════════════════════════"
    echo ""
    
    # Compter images générées
    COUNT=$(ls /workspace/app/src/main/res/drawable-nodpi/*nsfw*.jpg 2>/dev/null | wc -l)
    PERCENT=$((COUNT * 100 / 195))
    REMAINING=$((195 - COUNT))
    ETA_MIN=$((REMAINING * 20 / 60))
    
    echo "📊 Progression: $COUNT / 195 images ($PERCENT%)"
    echo "⏳ Restant: $REMAINING images (~$ETA_MIN minutes)"
    echo ""
    
    # Barre de progression
    BAR_WIDTH=50
    FILLED=$((COUNT * BAR_WIDTH / 195))
    printf "["
    for i in $(seq 1 $BAR_WIDTH); do
        if [ $i -le $FILLED ]; then
            printf "█"
        else
            printf "·"
        fi
    done
    printf "] $PERCENT%%\n\n"
    
    # Dernières lignes du log
    echo "════════════════════════════════════════════════════════════════════════════════"
    echo "📝 DERNIÈRES ACTIVITÉS:"
    echo "════════════════════════════════════════════════════════════════════════════════"
    tail -20 /tmp/nsfw_gen.log 2>/dev/null | grep -E "✅|❌|📛|SENSUEL|SEXY|NSFW" | tail -15
    echo ""
    
    # Statistiques
    echo "════════════════════════════════════════════════════════════════════════════════"
    echo "💾 STOCKAGE:"
    TOTAL_SIZE=$(du -sh /workspace/app/src/main/res/drawable-nodpi/*nsfw*.jpg 2>/dev/null | awk '{sum+=$1} END {print sum}')
    [ -n "$TOTAL_SIZE" ] && echo "   Taille totale: ${TOTAL_SIZE} MB" || echo "   Taille totale: Calcul..."
    echo ""
    
    if [ $COUNT -eq 195 ]; then
        echo "🎉 GÉNÉRATION TERMINÉE ! 195/195 images générées"
        break
    fi
    
    sleep 30
done
