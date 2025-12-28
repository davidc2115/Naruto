# 🔧 GUIDE DE DÉPANNAGE - Stable Diffusion Freebox

**Date:** 28 Décembre 2025  
**Problème:** SD WebUI pas accessible sur port 33437

---

## 🔍 DIAGNOSTIC ACTUEL

### Tests effectués:

```bash
# Port 33437 (nouveau)
curl http://88.174.155.230:33437
→ Connection refused (service pas lancé)

# Port 7860 (ancien)
curl http://88.174.155.230:7860
→ Timeout (rien n'écoute)
```

**Conclusion:** Stable Diffusion WebUI **n'est PAS lancé** sur la Freebox.

---

## ✅ SOLUTION : Démarrer SD WebUI

### Étape 1: Connexion SSH à la Freebox

```bash
ssh -p 33000 root@88.174.155.230
# Entrer le mot de passe root
```

### Étape 2: Vérifier si SD WebUI est installé

```bash
# Vérifier l'installation
ls -la /root/stable-diffusion-webui/

# Si le dossier n'existe pas, installer SD WebUI:
cd /root
git clone https://github.com/AUTOMATIC1111/stable-diffusion-webui.git
```

### Étape 3: Lancer SD WebUI sur port 33437

**Option A: Lancement manuel (test)**
```bash
cd /root/stable-diffusion-webui
./webui.sh --listen --port 33437 --skip-torch-cuda-test --no-half --api
```

**Première exécution:** 10-30 minutes (téléchargement dépendances)

**Option B: Service systemd (démarrage auto)**
```bash
# Créer le service
cat > /etc/systemd/system/sd-webui.service << 'EOF'
[Unit]
Description=Stable Diffusion WebUI
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/root/stable-diffusion-webui
ExecStart=/root/stable-diffusion-webui/webui.sh --listen --port 33437 --skip-torch-cuda-test --no-half --api
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# Activer et démarrer
systemctl daemon-reload
systemctl enable sd-webui
systemctl start sd-webui

# Vérifier status
systemctl status sd-webui
```

### Étape 4: Vérifier que le service écoute

```bash
# Dans la Freebox
netstat -tlnp | grep 33437

# Devrait afficher:
# tcp 0.0.0.0:33437 LISTEN 12345/python3
```

### Étape 5: Tester depuis l'extérieur

```bash
# Depuis votre PC
curl -I http://88.174.155.230:33437

# Devrait retourner:
# HTTP/1.1 200 OK
```

---

## 🔥 COMMANDES RAPIDES

### Démarrage rapide (pour test immédiat)

```bash
ssh -p 33000 root@88.174.155.230 << 'COMMANDS'
cd /root/stable-diffusion-webui
nohup ./webui.sh --listen --port 33437 --skip-torch-cuda-test --no-half --api > /tmp/sd-webui.log 2>&1 &
echo "SD WebUI démarré en arrière-plan"
echo "Logs: tail -f /tmp/sd-webui.log"
COMMANDS
```

### Vérifier logs

```bash
ssh -p 33000 root@88.174.155.230 "tail -f /tmp/sd-webui.log"
# ou si systemd:
ssh -p 33000 root@88.174.155.230 "journalctl -u sd-webui -f"
```

### Arrêter le service

```bash
# Si systemd
ssh -p 33000 root@88.174.155.230 "systemctl stop sd-webui"

# Si lancé manuellement
ssh -p 33000 root@88.174.155.230 "pkill -f webui.sh"
```

---

## 🐛 PROBLÈMES COURANTS

### 1. "Port already in use"

```bash
# Trouver le processus
ssh -p 33000 root@88.174.155.230 "lsof -i :33437"

# Tuer le processus
ssh -p 33000 root@88.174.155.230 "kill -9 <PID>"
```

### 2. "Out of memory"

```bash
# Ajouter swap
ssh -p 33000 root@88.174.155.230 << 'EOF'
fallocate -l 4G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
EOF
```

### 3. "Connection refused" persiste

**Vérifier le firewall:**
```bash
ssh -p 33000 root@88.174.155.230 << 'EOF'
# Autoriser port 33437
iptables -A INPUT -p tcp --dport 33437 -j ACCEPT
iptables-save > /etc/iptables/rules.v4
EOF
```

### 4. Service ne démarre pas

```bash
# Voir les logs d'erreur
ssh -p 33000 root@88.174.155.230 "journalctl -u sd-webui -n 50"
```

---

## 📋 CHECKLIST DE VÉRIFICATION

Après installation, vérifier:

- [ ] SSH accessible: `ssh -p 33000 root@88.174.155.230`
- [ ] SD WebUI installé: `ls /root/stable-diffusion-webui/`
- [ ] Service lancé: `systemctl status sd-webui`
- [ ] Port écoute: `netstat -tlnp | grep 33437`
- [ ] Firewall ouvert: `iptables -L | grep 33437`
- [ ] Accessible externe: `curl http://88.174.155.230:33437`
- [ ] Test génération: `curl -X POST http://88.174.155.230:33437/sdapi/v1/txt2img ...`

---

## 🎯 APRÈS INSTALLATION RÉUSSIE

### L'app Android se connectera automatiquement!

1. **Freebox accessible** → Génération locale (30-120s)
2. **Freebox timeout** → Fallback Pollination AI (2-10s)

### Télécharger la nouvelle version

**Release v2.15.1** avec port 33437:
```bash
# Le build est en cours, il sera disponible à:
# https://github.com/mel805/naruto-ai-chat/releases/tag/v2.15.1
```

---

## 🆘 BESOIN D'AIDE?

### Logs complets

```bash
# Copier tous les logs
ssh -p 33000 root@88.174.155.230 << 'EOF'
echo "=== System Info ==="
uname -a
free -h
df -h

echo -e "\n=== SD WebUI Status ==="
systemctl status sd-webui

echo -e "\n=== Listening Ports ==="
netstat -tlnp | grep -E "(33437|7860)"

echo -e "\n=== Recent Logs ==="
journalctl -u sd-webui -n 50 --no-pager
EOF
```

---

## 💡 ALTERNATIVE : Utiliser Pollination AI uniquement

Si vous ne voulez pas installer SD WebUI sur Freebox:

**L'app fonctionne déjà parfaitement avec Pollination AI!**

- ✅ Gratuit et rapide (2-10s)
- ✅ Aucune installation nécessaire
- ✅ Génération automatique via fallback
- ⚠️ Rate limits possibles (429 errors)

**Rien à faire, l'app utilisera Pollination AI automatiquement!**

---

**Dernière mise à jour:** 28 Décembre 2025  
**Port configuré:** 33437  
**Status:** En attente démarrage sur Freebox

🔧 **Suivez ce guide pour activer SD WebUI sur votre Freebox!** 🔧
