# 🔓 Guide : Ouvrir SSH externe sur Freebox

Pour permettre la génération NSFW directement depuis le cloud, il faut rendre le SSH de ta Freebox accessible depuis Internet.

## 📋 Étape 1 : Activer SSH sur la Freebox

1. **Connecte-toi à l'interface Freebox** : http://mafreebox.freebox.fr/
2. Va dans **Paramètres** → **Mode avancé**
3. **Système** → **SSH**
4. **Active SSH** si pas déjà fait
5. Note le **port SSH local** (généralement 22)

---

## 📋 Étape 2 : Configurer redirection de port (NAT/PAT)

1. Dans l'interface Freebox, va dans **Paramètres**
2. **Gestion des ports**
3. Clique **Ajouter une redirection**

### Configuration redirection

| Paramètre | Valeur |
|-----------|--------|
| **IP de destination** | IP locale de ta Freebox (ex: 192.168.1.254) |
| **Port externe** | 2222 (ou autre port >1024 pour sécurité) |
| **Port interne** | 22 (port SSH local) |
| **Protocole** | TCP |
| **Commentaire** | SSH externe |

4. **Sauvegarde**

---

## 📋 Étape 3 : Tester connexion SSH

Depuis un autre appareil (ordinateur, téléphone) :

```bash
# Test avec port externe
ssh -p 2222 root@88.174.155.230
```

✅ **Si ça marche** : Tu vois le prompt SSH de la Freebox  
❌ **Si ça ne marche pas** : Vérifie pare-feu et redirection

---

## 📋 Étape 4 : Sécuriser l'accès

⚠️ **IMPORTANT** : SSH externe = risque de sécurité

### Recommandations

1. **Utilise un port non-standard** (ex: 2222, 2345)
2. **Change le mot de passe root** si facile
3. **Désactive connexion par mot de passe** (utilise clés SSH)
4. **Installe fail2ban** pour bloquer attaques brute-force

### Générer clé SSH (sur ton PC)

```bash
# Génère paire de clés
ssh-keygen -t ed25519 -C "freebox-ssh"

# Copie clé publique vers Freebox
ssh-copy-id -p 2222 root@88.174.155.230
```

Ensuite, modifie `/etc/ssh/sshd_config` sur Freebox :

```
PasswordAuthentication no
PubkeyAuthentication yes
PermitRootLogin prohibit-password
```

Redémarre SSH :

```bash
systemctl restart sshd
```

---

## 🚀 Étape 5 : Lancer génération NSFW depuis cloud

Une fois SSH accessible, tu peux me donner :

1. **Port SSH externe** (ex: 2222)
2. **Confirmation** que connexion fonctionne

Je pourrai alors :

```bash
# Depuis cloud → SSH → Freebox
ssh -p 2222 root@88.174.155.230 << 'ENDSSH'
cd /tmp
cat > gen.py << 'EOF'
# Script génération local
EOF
python3 gen.py
ENDSSH
```

---

## ⚙️ Alternative : Port forwarding temporaire

Si tu ne veux pas laisser SSH ouvert en permanence :

1. **Ouvre SSH** seulement pendant génération
2. **Désactive redirection** après

Ou utilise **tunnel SSH inverse** (plus sécurisé) :

```bash
# Sur Freebox, crée tunnel vers serveur cloud
ssh -R 2222:localhost:22 user@cloud-server.com

# Depuis cloud, connecte via tunnel
ssh -p 2222 localhost
```

---

## 🔍 Diagnostic

### Test port ouvert (depuis Internet)

```bash
# Test depuis autre réseau
telnet 88.174.155.230 2222

# Ou avec nmap
nmap -p 2222 88.174.155.230
```

### Vérifier logs SSH (sur Freebox)

```bash
tail -f /var/log/auth.log
# ou
journalctl -u ssh -f
```

---

## ❓ Troubleshooting

| Problème | Solution |
|----------|----------|
| "Connection refused" | SSH pas démarré sur Freebox |
| "Connection timed out" | Redirection port pas configurée |
| "Permission denied" | Mot de passe incorrect ou clé SSH manquante |
| "Too many authentication failures" | Limite SSH atteinte, attends 5 min |

---

## 📞 Si tu préfères éviter SSH externe

**Option 1** : Lance génération **localement** sur ton PC  
→ Script Python qui se connecte à ComfyUI local (127.0.0.1)

**Option 2** : Utilise **APIs cloud** (Pollination, Stable Horde)  
→ Plus lent mais pas besoin Freebox

**Option 3** : **TeamViewer/AnyDesk** pour contrôler Freebox  
→ Plus sécurisé que SSH externe

---

## ✅ Une fois configuré

Dis-moi juste :

> "SSH ouvert sur port 2222" (ou autre port)

Et je lance immédiatement la génération NSFW des 39 images ! 🚀

---

**Sécurité** : Si tu laisses SSH ouvert longtemps, surveille les logs pour détecter tentatives d'intrusion.
