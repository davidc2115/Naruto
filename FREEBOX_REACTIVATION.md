# 🛠️ Instructions pour réactiver Freebox dans l'APK

## ✅ État actuel de ComfyUI

**Test de connexion** (29/12/2025 23:55) :
- ✅ Port 33437 **accessible** depuis Internet
- ⚠️ **Très lent** : 7 secondes pour répondre au HTML
- ✅ ComfyUI **tourne** et répond (HTTP 200 OK)
- 🌐 Serveur : `Python/3.13 aiohttp/3.13.2`

## ⚠️ Pourquoi Freebox est désactivée dans v2.26.5 ?

Le délai de 7 secondes dépasse le timeout de l'APK (3 secondes par défaut), ce qui cause des erreurs "ComfyUI inaccessible".

## 🔧 Solutions pour réactiver Freebox

### Option 1 : Augmenter le timeout dans l'APK (Recommandé)

Modifier `ComfyUIClient.kt` ligne 34 :

```kotlin
// AVANT
private const val PING_TIMEOUT = 3000L // 3 secondes

// APRÈS
private const val PING_TIMEOUT = 15000L // 15 secondes
```

### Option 2 : Optimiser la connexion réseau Freebox

1. **Vérifier la bande passante** :
   ```bash
   ssh root@88.174.155.230
   iftop -i eth0  # Surveiller le trafic réseau
   ```

2. **Redémarrer ComfyUI** pour libérer la mémoire :
   ```bash
   ssh root@88.174.155.230
   pkill -f comfyui
   cd /root/ComfyUI
   python main.py --listen 0.0.0.0 --port 33437 &
   ```

3. **Vérifier les logs** :
   ```bash
   ssh root@88.174.155.230
   tail -f /root/ComfyUI/comfyui.log  # Si les logs existent
   ```

### Option 3 : Tunnel reverse (si le problème persiste)

Utiliser **ngrok** ou **cloudflared** pour exposer ComfyUI avec une URL HTTPS rapide :

```bash
# Installer cloudflared
wget -q https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64
chmod +x cloudflared-linux-amd64
mv cloudflared-linux-amd64 /usr/local/bin/cloudflared

# Créer tunnel
cloudflared tunnel --url http://localhost:33437
```

Cela donnera une URL type `https://xxx.trycloudflare.com` qui sera rapide.

## 🚀 Réactiver Freebox dans le code

Une fois le problème résolu, modifier `ImageGenerationWorker.kt` ligne 62 :

```kotlin
// DÉSACTIVER ce bloc (v2.26.5) :
"freebox" -> {
    android.util.Log.d("ImageWorker", "⚠️ Freebox désactivée, utilisation de Stable Horde")
    val stableHordeClient = StableHordeClient()
    stableHordeClient.generateImage(...)
}

// RÉACTIVER ce bloc (v2.27.0) :
"freebox" -> {
    val comfyClient = ComfyUIClient()
    comfyClient.generateImage(prompt, negativePrompt, width, height, steps, cfgScale)
}
```

## 📊 Diagnostic réseau

```bash
# Test depuis la Freebox elle-même
curl -w "\n\nTime: %{time_total}s\n" http://localhost:33437

# Test depuis l'extérieur
curl -w "\n\nTime: %{time_total}s\n" http://88.174.155.230:33437
```

Si le temps local est rapide (<1s) mais externe lent (>5s), c'est un problème de :
- **NAT/Firewall** qui ralentit le trafic
- **Bande passante upload** limitée
- **Charge réseau** élevée

## 🎯 Prochaines étapes

1. ✅ Tester Stable Horde dans v2.26.5
2. 🔧 Augmenter timeout PING_TIMEOUT à 15s
3. 🚀 Publier v2.27.0 avec Freebox réactivée
4. 📊 Surveiller logs ComfyUI pour optimiser

---

**Note** : En attendant, Stable Horde et Pollination AI sont parfaitement fonctionnels ! 🚀
