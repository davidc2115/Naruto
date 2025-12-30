# 📦 Release Notes v2.28.2 - Fix ComfyUI Detection

## 🔧 Corrections : Détection ComfyUI améliorée

**Problème** : ComfyUI détecté comme "non accessible" même s'il tourne.

**Causes identifiées** :
1. ❌ Requête `HEAD` pas supportée par ComfyUI → Changé en `GET`
2. ❌ Timeout trop court (15s) → Augmenté à **30 secondes**
3. ❌ Logs insuffisants → Logs détaillés ajoutés

## ✅ Corrections appliquées

### 1. GET au lieu de HEAD
```kotlin
// AVANT (v2.28.1) - HEAD peut ne pas fonctionner
.head()

// APRÈS (v2.28.2) - GET plus fiable
.get()
```

### 2. Timeout augmenté
```kotlin
// AVANT
PING_TIMEOUT = 15000L // 15s

// APRÈS  
PING_TIMEOUT = 30000L // 30s (ComfyUI prend 3-7s pour répondre)
```

### 3. Logs détaillés dans ComfyUIClient
```kotlin
🔍 Test accessibilité ComfyUI: http://88.174.155.230:33437
✅ ComfyUI accessible (3542ms, HTTP 200)
// OU
❌ ComfyUI non accessible: SocketTimeoutException: timeout
```

### 4. Logs détaillés dans ImageWorker
```kotlin
🏠 Freebox/ComfyUI sélectionné
🔍 Test d'accessibilité de ComfyUI...
✅ ComfyUI accessible, génération...
// OU
⚠️ ComfyUI NON ACCESSIBLE (http://88.174.155.230:33437)
❌ Exception test ComfyUI: SocketTimeoutException: Connect timed out
```

## 🔍 Diagnostic amélioré

Avec v2.28.2, tu verras **exactement** pourquoi ComfyUI ne fonctionne pas :

```bash
adb logcat | grep "ComfyUIClient\|ImageWorker"
```

### Scénario 1 : ComfyUI accessible ✅
```
ComfyUIClient: 🔍 Test accessibilité ComfyUI: http://88.174.155.230:33437
ComfyUIClient: ✅ ComfyUI accessible (3542ms, HTTP 200)
ImageWorker: ✅ ComfyUI accessible, génération...
ImageWorker: ✅ Freebox réussi (ComfyUI local)
```

### Scénario 2 : Port fermé ❌
```
ComfyUIClient: 🔍 Test accessibilité ComfyUI: http://88.174.155.230:33437
ComfyUIClient: ❌ ComfyUI non accessible: ConnectException: Connection refused
ImageWorker: ⚠️ ComfyUI NON ACCESSIBLE
ImageWorker: 🔄 Fallback automatique vers Pollination AI...
```

### Scénario 3 : Timeout réseau ⏱️
```
ComfyUIClient: 🔍 Test accessibilité ComfyUI: http://88.174.155.230:33437
ComfyUIClient: ❌ ComfyUI non accessible: SocketTimeoutException: timeout
ImageWorker: ⚠️ ComfyUI NON ACCESSIBLE
ImageWorker: 🔄 Fallback automatique vers Pollination AI...
```

## 🧪 Test

1. **Génère une image avec "Freebox"**
2. **Regarde les logs** :
   ```bash
   adb logcat | grep "ComfyUIClient\|ImageWorker"
   ```
3. **Identifie le problème** :
   - `✅ ComfyUI accessible` → Freebox devrait fonctionner
   - `❌ ConnectException: Connection refused` → Port 33437 fermé
   - `❌ SocketTimeoutException` → Réseau trop lent
   - `❌ UnknownHostException` → DNS ne résout pas l'IP

## 🛠️ Solutions selon l'erreur

### Si `Connection refused`
Le port 33437 est fermé :
- Vérifie que ComfyUI tourne : `ps aux | grep comfy`
- Ouvre le port dans la Freebox : NAT/Port Forwarding
- Relance ComfyUI avec `--listen 0.0.0.0 --port 33437`

### Si `SocketTimeoutException`
Réseau trop lent :
- Teste depuis un navigateur : `http://88.174.155.230:33437`
- Si ça marche dans le navigateur mais pas dans l'app → Problème Android
- Essaie avec WiFi au lieu de données mobiles

### Si `UnknownHostException`
DNS ne résout pas l'IP :
- Vérifie que `88.174.155.230` est la bonne IP
- Teste : `ping 88.174.155.230`

## 📊 Changements

### ComfyUIClient.kt
- GET au lieu de HEAD
- PING_TIMEOUT: 15s → 30s
- Logs détaillés (durée, code HTTP, type d'exception)

### ImageGenerationWorker.kt
- Suppression de `runBlocking` (déjà dans Dispatchers.IO)
- Logs d'exception détaillés avec classe et message

## 🎯 Résultat attendu

Si ComfyUI est **vraiment accessible**, Freebox devrait maintenant fonctionner.

Si ça fallback toujours sur Pollination AI, les **logs te diront exactement pourquoi** (port fermé, timeout, etc.).

---

**Partage les logs pour que je puisse t'aider si ça ne marche toujours pas** ! 📋
