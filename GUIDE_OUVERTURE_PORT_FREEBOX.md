# 🔓 Guide : Ouvrir des ports sur Freebox

## 📋 Ports à ouvrir

| Port  | Service                | Protocole | Priorité |
|-------|------------------------|-----------|----------|
| 33437 | ComfyUI (si utilisé)   | TCP       | ⚠️       |
| 33500 | API Personnages (NSFW) | TCP       | ✅ Recommandé |

---

## 🌐 Méthode 1 : Via Freebox OS (Interface Web)

### Étape 1 : Connexion à Freebox OS

1. Ouvrez votre navigateur et allez sur : **http://mafreebox.freebox.fr**
2. Connectez-vous avec votre **mot de passe Freebox**

### Étape 2 : Accéder à la configuration NAT/PAT

1. Cliquez sur **"Paramètres de la Freebox"** (icône engrenage)
2. Allez dans **"Gestion des ports"**
3. Cliquez sur **"Ajouter une redirection"**

### Étape 3 : Configurer la redirection de port

#### Pour le port 33437 (ComfyUI) :

```
Protocole       : TCP
Port externe    : 33437
IP destination  : 192.168.1.37 (votre Freebox)
Port interne    : 33437
Description     : ComfyUI API
```

#### Pour le port 33500 (API Personnages - RECOMMANDÉ) :

```
Protocole       : TCP
Port externe    : 33500
IP destination  : 192.168.1.37 (votre Freebox)
Port interne    : 33500
Description     : Characters API
```

### Étape 4 : Activer et sauvegarder

1. Cochez **"Activer"**
2. Cliquez sur **"Enregistrer"**
3. Attendez 10-15 secondes pour que la règle soit appliquée

---

## 🔍 Méthode 2 : Via l'application mobile Freebox Connect

1. Installez **Freebox Connect** (iOS/Android)
2. Connectez-vous avec vos identifiants Freebox
3. Allez dans **"Paramètres"** → **"Redirections de ports"**
4. Ajoutez les ports comme ci-dessus

---

## ✅ Vérification que le port est ouvert

### Depuis votre terminal (après configuration) :

```bash
# Tester depuis l'extérieur (4G/autre réseau)
curl -I http://88.174.155.230:33437/health
curl -I http://88.174.155.230:33500/health
```

Si vous obtenez une réponse HTTP, le port est ouvert !

### Utiliser un service en ligne :

1. Allez sur : **https://www.yougetsignal.com/tools/open-ports/**
2. Entrez votre IP : **88.174.155.230**
3. Testez les ports **33437** et **33500**

---

## 🔐 Sécurité recommandée

⚠️ **ATTENTION** : Ouvrir un port expose votre service sur Internet.

### Recommandations :

1. **Ne pas ouvrir 33437** si ComfyUI n'est plus utilisé (vous l'avez nettoyé)
2. **Ouvrir seulement 33500** pour l'API Personnages (NSFW)
3. Ajouter un **mot de passe** à l'API si sensible (optionnel)
4. Vérifier régulièrement les **logs de connexion**

---

## 🚨 Problèmes courants

### Port refusé après configuration ?

```bash
# Vérifier que le service écoute bien
ssh -p 33000 bagbot@88.174.155.230
sudo netstat -tlnp | grep 33500
```

### IP 192.168.1.37 incorrecte ?

Vérifiez votre IP locale :
```bash
ssh -p 33000 bagbot@88.174.155.230
ip addr show | grep 192.168
```

---

## 📞 Support Freebox

Si vous avez des difficultés :
- **Assistance** : 3244 (depuis un fixe)
- **Forum** : https://forum.universfreebox.com
- **Doc officielle** : https://www.free.fr/assistance/

---

## ⚡ Action recommandée

**Pour l'application Naruto AI**, ouvrez **uniquement le port 33500** :
- ✅ Permet l'accès aux galeries NSFW depuis l'extérieur
- ✅ API Personnages accessible en 4G/5G
- 🔒 Plus sécurisé que d'ouvrir plusieurs ports

Le port **33437** n'est plus nécessaire (ComfyUI supprimé).
