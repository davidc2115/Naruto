# OpenCompanion

Application Android de chat avec des personnages, propulsée par un moteur
d'IA **entièrement local** : aucune clé d'API, aucun compte, aucun appel à
HuggingFace ni à un service cloud quelconque. Une fois un modèle importé,
l'app fonctionne hors ligne.

> Ce dépôt a été reconstruit de zéro à partir de rien (voir historique git) :
> il ne contient plus le contenu précédent.

## Pourquoi ce projet existe (et ce qu'il n'est pas)

L'idée de départ était de repartir d'un chatbot de personnages, mais sans
aucun des ingrédients qui posaient problème dans la version précédente :
pas de clé d'API à payer/partager, pas de service tiers auquel envoyer ses
conversations, et surtout **aucun personnage calqué sur une personne réelle
ou un personnage sous droit d'auteur**. Les trois personnages fournis par
défaut sont entièrement fictifs et originaux, tout public — ce sont des
exemples pour comprendre le format de fiche personnage, pas une proposition
de contenu figée. Créer ou importer d'autres personnages est libre, mais
reste sous la responsabilité de l'utilisateur.

## Fonctionnalités

- **Moteur IA local** : [llama.cpp](https://github.com/ggml-org/llama.cpp)
  embarqué (sous-module git, `external/llama.cpp`), compilé pour Android via
  le NDK, avec le backend **Vulkan** (GPU) en plus du backend CPU. Le GPU est
  utilisé quand il est disponible et stable ; en cas de plantage du pilote
  (ça arrive sur certains GPU Adreno, voir
  [`docs/VULKAN_NOTES.md`](docs/VULKAN_NOTES.md)), l'app retombe
  automatiquement sur le CPU sans planter.
- **Aucune clé, aucun compte** : ni pour discuter, ni pour importer un
  modèle ou un personnage. Le seul accès réseau optionnel est un
  téléchargement HTTP direct d'un fichier `.gguf` ou d'une fiche personnage
  depuis une URL que *tu* fournis.
- **Personnages créables ou importables**, au format ouvert
  [Character Card V2](https://github.com/malfoyslastname/character-card-spec-v2)
  (JSON, éventuellement embarqué dans un avatar PNG) — voir
  [`docs/CHARACTER_IMPORT.md`](docs/CHARACTER_IMPORT.md). Trois façons
  d'importer, toutes aussi directes :
  1. **Partager** une image ou un JSON depuis une autre application
     (navigateur, galerie…) → "Envoyer vers OpenCompanion".
  2. **Choisir un fichier** déjà sur l'appareil.
  3. **Coller une URL** directe.
- **Réglages fins** : taille de contexte, threads CPU, nombre de couches sur
  GPU, température, top-k/top-p, pénalité de répétition.

**Android 9 (API 28) minimum.** Le backend Vulkan de llama.cpp appelle une
fonction "core" de Vulkan 1.1 (`vkGetPhysicalDeviceFeatures2`) que le loader
Vulkan d'Android n'exporte qu'à partir de l'API 28 — voir
[`docs/VULKAN_NOTES.md`](docs/VULKAN_NOTES.md) pour le détail. En dessous,
même le repli CPU serait affecté puisque les deux backends sont compilés
dans la même bibliothèque native.

## Construire l'application

```bash
git clone --recurse-submodules <url-de-ce-depot>
cd OpenCompanion
# Si cloné sans --recurse-submodules :
git submodule update --init --recursive

./gradlew :app:assembleDebug -PabiFilters=arm64-v8a
```

L'APK debug se trouve ensuite dans `app/build/outputs/apk/debug/`.

Prérequis : JDK 17, Android SDK (plateforme 36), NDK `28.0.13004108`, CMake
`3.31+`, et sur la machine qui compile, les outils hôte du backend Vulkan de
ggml — sur Debian/Ubuntu : `sudo apt-get install glslc spirv-headers
libvulkan-dev` (Vulkan SDK LunarG sur Windows/macOS). Sans eux, la
configuration CMake échoue (voir [`docs/VULKAN_NOTES.md`](docs/VULKAN_NOTES.md)
pour le détail des erreurs rencontrées). Le workflow
[`.github/workflows/android-build.yml`](.github/workflows/android-build.yml)
installe tout cela automatiquement et compile l'APK à chaque push — c'est la
référence si un build local coince sur une histoire de version d'outils.

`-PabiFilters=arm64-v8a` limite le build à l'architecture 64 bits (la
quasi-totalité des appareils depuis 2018) ; `-PabiFilters=arm64-v8a,armeabi-v7a`
couvre aussi les appareils 32 bits plus anciens, au prix d'un APK plus gros
et d'un temps de build plus long.

## Obtenir un modèle

L'app n'embarque aucun modèle (ça ferait plusieurs Go dans l'APK) et ne
propose aucun catalogue intégré — c'est un choix délibéré pour rester
indépendant de toute plateforme. Dans **Réglages → Modèle** :

- **Importer un fichier** : si tu as déjà un `.gguf` sur ton téléphone
  (copié par USB, téléchargé dans un navigateur, etc.).
- **Depuis une URL** : colle un lien HTTP(S) direct vers un `.gguf`, où
  que ce fichier soit hébergé.

N'importe quel modèle au format **GGUF** convient. Pour un premier essai sur
mobile, vise un modèle "small/mini" quantifié (`Q4_K_M` par exemple,
2 à 4 Go) : au-delà, le chargement et la vitesse de génération deviennent
pénibles sur la plupart des téléphones.

## Personnaliser un personnage / en importer un

Voir [`docs/CHARACTER_IMPORT.md`](docs/CHARACTER_IMPORT.md) pour le détail
du format et des trois voies d'import.

## Vulkan : ce qui marche, ce qui est fragile

Voir [`docs/VULKAN_NOTES.md`](docs/VULKAN_NOTES.md) — en résumé, la
compilation Vulkan pour Android via llama.cpp a longtemps été instable sur
certaines combinaisons GPU/pilote (plantages `DeviceLostError` sur certains
Adreno). Le code de ce dépôt est écrit pour s'en protéger (repli CPU
automatique, aucune exception ne doit jamais traverser la frontière JNI),
mais la seule vraie validation possible est sur de vrais appareils — voir ce
document pour savoir quoi tester et comment lire les logs.

## Architecture du code

```
app/src/main/cpp/            Pont JNI C++ vers llama.cpp (opencompanion_bridge.cpp)
app/src/main/java/.../engine/        Moteur : LlamaBridge (JNI brut), InferenceEngine
                                      (façade coroutines/Flow), ModelManager, décodage UTF-8
app/src/main/java/.../data/          Room (personnages, historique), DataStore (réglages)
app/src/main/java/.../charactercard/ Import/export Character Card V2 (JSON + PNG)
app/src/main/java/.../prompt/        Construction du prompt (patron de dialogue + historique)
app/src/main/java/.../ui/            Écrans Compose (liste, éditeur, chat, réglages)
external/llama.cpp/           Sous-module git : moteur d'inférence (licence MIT)
```

## Licence

Code de ce dépôt : MIT (voir [`LICENSE`](LICENSE)). `external/llama.cpp` est
un sous-module distinct, également sous licence MIT — voir sa propre licence
dans ce dossier une fois le sous-module initialisé.
