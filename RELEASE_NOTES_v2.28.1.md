# 📦 Release Notes v2.28.1 - Fix Freebox Fallback Logging

## 🔧 Correction : Freebox fallback systématique

**Problème rapporté** : Même en sélectionnant "Freebox", les images sont toujours générées par Pollination AI.

**Cause probable** : ComfyUI sur `http://88.174.155.230:33437` n'est pas accessible depuis l'appareil Android :
- Port 33437 fermé/non forwardé
- ComfyUI non démarré
- Timeout réseau

**Solution** : Logs détaillés ajoutés pour diagnostiquer :

### Logs ajoutés
```kotlin
🏠 Freebox/ComfyUI sélectionné
🔍 Test d'accessibilité de ComfyUI...
✅ ComfyUI accessible, génération...
// OU
⚠️ ComfyUI NON ACCESSIBLE (http://88.174.155.230:33437)
🔄 Fallback automatique vers Pollination AI...
```

### Notifications améliorées
- **ComfyUI inaccessible** : "ComfyUI non accessible. Utilisation de Pollination AI..."
- **ComfyUI OK mais génération échoue** : "Erreur génération Freebox. Utilisation de Pollination AI..."
- **ComfyUI OK et génération OK** : "Source: Freebox (ComfyUI local)"

## 🔍 Diagnostic

Avec cette version, tu peux voir exactement pourquoi Freebox fail :

```bash
adb logcat | grep "ImageWorker"
```

Cherche :
- `🔍 Test d'accessibilité de ComfyUI...`
- `✅ ComfyUI accessible` → Freebox fonctionne
- `⚠️ ComfyUI NON ACCESSIBLE` → Port 33437 fermé ou ComfyUI down

## 🛠️ Solutions si ComfyUI inaccessible

### Solution 1 : Ouvrir le port 33437
```bash
# Sur la Freebox (si tu as accès SSH)
# Vérifier que ComfyUI tourne
ps aux | grep comfy

# Relancer ComfyUI avec --listen 0.0.0.0
cd /root/ComfyUI
python main.py --listen 0.0.0.0 --port 33437 &
```

### Solution 2 : Configurer le NAT/Port Forwarding
1. Accède à l'interface Freebox (mafreebox.freebox.fr)
2. Paramètres > Mode avancé > Redirections de ports
3. Ajoute : Port externe 33437 → Port interne 33437

### Solution 3 : Test depuis un navigateur
Ouvre `http://88.174.155.230:33437` dans un navigateur depuis le même appareil que l'APK :
- ✅ ComfyUI UI s'affiche → Le port est ouvert, problème dans l'app
- ❌ Timeout ou erreur → Le port est fermé

## 📊 Changements

### ImageGenerationWorker.kt
```kotlin
// Test d'accessibilité AVANT de générer
val isAvailable = comfyClient.isAvailable()
if (!isAvailable) {
    // Fallback immédiat
    Log.w("ComfyUI NON ACCESSIBLE")
    return PollinationAI
}

// Si accessible, essayer de générer
val result = comfyClient.generateImage(...)
if (result.isSuccess) {
    // Freebox OK ✅
} else {
    // Fallback après erreur
}
```

## 🎯 Prochaines étapes

Cette version ne corrige pas le problème d'accessibilité de ComfyUI, mais **t'aide à diagnostiquer** pourquoi Freebox ne fonctionne pas.

Pour v2.29.0 (à venir) :
- 🎨 Galeries NSFW pour les 13 personnages
- ➕ Interface pour créer des personnages personnalisés
- 🔧 Plus d'optimisations Freebox

---

**Note** : Si ComfyUI n'est vraiment pas accessible, Pollination AI est un excellent fallback (rapide et fiable) !
