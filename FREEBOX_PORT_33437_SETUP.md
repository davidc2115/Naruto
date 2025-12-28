# 🔧 Configuration Freebox Stable Diffusion - Port 33437

## ✅ Correction Appliquée

**Version 2.5.1** : Port Stable Diffusion corrigé de `7860` → `33437`

**Fichier modifié** : `FreeboxMediaClient.kt` ligne 29

```kotlin
private const val FREEBOX_BASE_URL = "http://88.174.155.230:33437"
```

---

## ⚠️ PROBLÈME DÉTECTÉ

**Les 2 ports sont inaccessibles** (testé depuis le serveur de build) :
- ❌ Port 7860 : timeout
- ❌ Port 33437 : `Connection refused`

### Causes Possibles

1. **Stable Diffusion WebUI n'est pas démarré** sur la Freebox
2. **Port 33437 fermé** dans le firewall Freebox
3. **Service non accessible** depuis l'externe (seulement localhost?)
4. **Port différent** : peut-être 7861, 8080, ou autre?

---

## 📋 VÉRIFICATIONS À FAIRE SUR LA FREEBOX

### 1. Vérifier que Stable Diffusion WebUI est démarré

```bash
# Sur la Freebox, vérifier les processus
ps aux | grep "webui"
ps aux | grep "stable"

# Ou vérifier les ports en écoute
netstat -tlnp | grep LISTEN
```

### 2. Tester localement depuis la Freebox

```bash
# Test depuis la Freebox elle-même
curl http://localhost:33437/sdapi/v1/sd-models

# OU essayer les ports communs
curl http://localhost:7860/sdapi/v1/sd-models
curl http://localhost:7861/sdapi/v1/sd-models
curl http://localhost:8080/sdapi/v1/sd-models
```

### 3. Vérifier le firewall

```bash
# Vérifier iptables
sudo iptables -L -n | grep 33437

# Ou UFW si installé
sudo ufw status

# Ouvrir le port si nécessaire
sudo ufw allow 33437/tcp
```

### 4. Démarrer Stable Diffusion WebUI

```bash
# Exemple de commande pour démarrer
cd /chemin/vers/stable-diffusion-webui
./webui.sh --listen --port 33437

# OU avec arguments spécifiques
python launch.py --listen --port 33437 --api
```

---

## 🔍 COMMENT TROUVER LE BON PORT

### Méthode 1: Vérifier les processus

```bash
sudo netstat -tlnp | grep python
# Chercher le port utilisé par le processus Stable Diffusion
```

### Méthode 2: Vérifier les logs

```bash
# Logs Stable Diffusion WebUI (emplacement peut varier)
tail -f /var/log/stable-diffusion-webui.log
tail -f ~/stable-diffusion-webui/webui.log

# Chercher une ligne comme:
# "Running on local URL:  http://0.0.0.0:33437"
```

### Méthode 3: Vérifier la configuration

```bash
# Fichier de config (emplacement peut varier)
cat ~/stable-diffusion-webui/config.json
cat ~/stable-diffusion-webui/webui-user.sh
```

---

## ✅ SOLUTION TEMPORAIRE

**L'app fonctionne quand même !**

Grâce au fallback automatique :
1. App essaie Freebox (port 33437)
2. Si échec → bascule sur **Pollination AI**
3. Delay 3s pour éviter 429
4. Génération réussie avec Pollination AI

**Avantages :**
- ✅ Pas de dépendance à la Freebox
- ✅ Plus rapide (pas d'attente timeout)
- ✅ Toujours disponible

**Inconvénients :**
- ⚠️ Rate limits Pollination AI (d'où le delay 3s)
- ⚠️ Pas de contrôle sur le modèle utilisé

---

## 🚀 POUR ACTIVER LA FREEBOX

Une fois que vous aurez :
1. ✅ Démarré Stable Diffusion WebUI
2. ✅ Ouvert le port 33437
3. ✅ Vérifié que c'est accessible depuis l'externe

**L'app utilisera automatiquement la Freebox** au lieu de Pollination AI !

Testez avec :
```bash
curl -X POST http://88.174.155.230:33437/sdapi/v1/txt2img \
  -H "Content-Type: application/json" \
  -d '{"prompt":"test","steps":10,"width":512,"height":512}'
```

Si ça retourne du JSON avec des images en base64 → **C'EST BON** ! ✅

---

## 📝 NOTES

**Version actuelle (v2.5.1):**
- Port Freebox: `33437` (corrigé)
- Fallback: Pollination AI (avec delay 3s)
- Erreur 429: **RÉSOLUE** ✅
- 45 images locales + 130 URLs disponibles

**Contact:**
Si vous avez besoin d'aide pour configurer la Freebox, fournissez :
1. Sortie de `netstat -tlnp | grep LISTEN`
2. Sortie de `ps aux | grep webui`
3. Log Stable Diffusion WebUI
