#!/bin/bash
# Monitor NSFW generation progress on Freebox

HOST="bagbot@88.174.155.230"
PORT="33000"
PASS="bagbot"
TOTAL=15

echo "╔═══════════════════════════════════════════════╗"
echo "║  📊 Monitor NSFW Generation - Freebox        ║"
echo "╚═══════════════════════════════════════════════╝"
echo ""
echo "Target: $TOTAL images"
echo "Checking every 30s... (Ctrl+C to stop)"
echo ""

START_TIME=$(date +%s)

while true; do
    # Get count
    COUNT=$(sshpass -p "$PASS" ssh -p $PORT -o StrictHostKeyChecking=no $HOST "ls /tmp/nsfw_gallery/*.png 2>/dev/null | wc -l" 2>/dev/null | tr -d ' ')
    
    # Get last log line
    LAST_LOG=$(sshpass -p "$PASS" ssh -p $PORT -o StrictHostKeyChecking=no $HOST "tail -1 /tmp/nsfw_gen.log 2>/dev/null" 2>/dev/null)
    
    # Calculate progress
    if [ -z "$COUNT" ]; then
        COUNT=0
    fi
    
    PERCENT=$((COUNT * 100 / TOTAL))
    ELAPSED=$(($(date +%s) - START_TIME))
    ELAPSED_MIN=$((ELAPSED / 60))
    
    # Progress bar
    BAR_LEN=30
    FILLED=$((PERCENT * BAR_LEN / 100))
    BAR=$(printf "%${FILLED}s" | tr ' ' '█')
    EMPTY=$(printf "%$((BAR_LEN - FILLED))s" | tr ' ' '░')
    
    # Display
    clear
    echo "╔═══════════════════════════════════════════════╗"
    echo "║  📊 NSFW Generation Progress                  ║"
    echo "╚═══════════════════════════════════════════════╝"
    echo ""
    echo "📁 Images: $COUNT/$TOTAL"
    echo "📊 Progress: [$BAR$EMPTY] $PERCENT%"
    echo "⏱️  Elapsed: ${ELAPSED_MIN}m ${ELAPSED}s"
    echo ""
    echo "📝 Last log:"
    echo "   $LAST_LOG"
    echo ""
    
    # Check if done
    if [ "$COUNT" -eq "$TOTAL" ]; then
        echo "✅ GENERATION COMPLETE!"
        echo ""
        echo "📥 Download images:"
        echo "   scp -P $PORT $HOST:/tmp/nsfw_gallery/*.png ./character_images_nsfw/"
        echo ""
        echo "📦 Or download archive:"
        echo "   ssh -p $PORT $HOST 'cd /tmp && tar -czf nsfw_gallery.tar.gz nsfw_gallery/'"
        echo "   scp -P $PORT $HOST:/tmp/nsfw_gallery.tar.gz ."
        break
    fi
    
    # ETA
    if [ "$COUNT" -gt 0 ]; then
        AVG_TIME=$((ELAPSED / COUNT))
        REMAINING=$((TOTAL - COUNT))
        ETA=$((REMAINING * AVG_TIME / 60))
        echo "🎯 ETA: ~${ETA} minutes"
    fi
    
    echo ""
    echo "Next check in 30s... (Ctrl+C to stop)"
    sleep 30
done
