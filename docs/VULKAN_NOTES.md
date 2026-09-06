# Vulkan sur Android : état réel et limites connues

Ce document résume ce qui a été vérifié en documentation/issues publiques de
llama.cpp au moment de la conception de ce projet (septembre 2026), pour
éviter de faire croire que "Vulkan sur Android" est un problème résolu et
sans surprise. Ça ne l'est pas complètement.

## Pièges vérifiés pendant la conception : outils hôte manquants

En testant réellement la compilation native de ce projet (voir plus bas),
la configuration CMake du backend Vulkan a échoué deux fois de suite avant
d'aboutir — les deux fois pour des **outils hôte** absents (rien à voir
avec l'appareil Android cible) :

1. `CMake Error: Could NOT find Vulkan (missing: glslc) (found version "1.3.275")`
   — `glslc` compile les shaders GLSL de `ggml-vulkan` en SPIR-V au moment
   du build ; ni le NDK ni le SDK Android ne le fournissent.
2. `Could not find a package configuration file provided by "SPIRV-Headers"`
   — les en-têtes du format SPIR-V, requis pour la configuration CMake du
   backend Vulkan.

Sur Debian/Ubuntu, les trois paquets `glslc spirv-headers libvulkan-dev`
suffisent à les installer (voir `.github/workflows/android-build.yml`) ;
sur Windows/macOS, le Vulkan SDK LunarG fournit l'équivalent — **mais
installer ces paquets ne suffisait pas** : le toolchain Android restreint
par défaut `find_package()` au seul sysroot du NDK pendant la
cross-compilation, donc CMake ne "voyait" pas `SPIRV-HeadersConfig.cmake`
pourtant bien présent sous `/usr/share/cmake/SPIRV-Headers/` sur la machine
hôte. Le correctif, dans `app/build.gradle.kts`
(`CMAKE_FIND_ROOT_PATH_MODE_PACKAGE=BOTH` /
`CMAKE_FIND_ROOT_PATH_MODE_PROGRAM=BOTH`), autorise explicitement CMake à
chercher aussi hors du sysroot pour ces paquets et programmes hôte. Voir
aussi le README pour les prérequis d'un build local.

## Piège vérifié en usage réel : `vk_video/` manquant dans les en-têtes Vulkan isolés

Une fois l'OOM CI réglé (section suivante), la compilation a échoué une marche plus loin,
toujours sur `ggml-vulkan.cpp` :
`vulkan_core.h:12325:10: fatal error: 'vk_video/vulkan_video_codec_av1std.h' file not found`.

Rappel du contexte (voir la section "en-têtes C++ Vulkan" plus bas) : pour contourner un bug de
déduplication de CMake, seul le sous-répertoire `vulkan/` (et `spirv/` pour SPIR-V) de
`/usr/include` est copié dans un dossier isolé exposé au compilateur — pas tout `/usr/include`,
pour ne pas lui exposer les en-têtes glibc de la machine hôte. Ce qui n'avait pas été anticipé :
`vulkan_core.h` inclut aussi `<vk_video/vulkan_video_codec_*.h>` (extensions vidéo, ajoutées au
paquet Vulkan-Headers depuis 2023) — un **troisième** sous-répertoire du même paquet, absent de
la copie isolée. Corrigé dans `app/src/main/cpp/CMakeLists.txt` : `vk_video/` est copié au même
titre que `vulkan/` et `spirv/`, avec le même avertissement CMake s'il venait à manquer.

**Validé** : reconfiguré CMake et recompilé `ggml-vulkan.cpp.o` seul après le correctif — le
dossier isolé contient bien `vk_video/` et la compilation aboutit sans erreur.

## Piège vérifié en usage réel : OOM du compilateur sur `mul_mm.comp.cpp`, pas une erreur de code

Début septembre 2026, la CI (`.github/workflows/android-build.yml`) a échoué plusieurs fois de
suite sur ce projet, avec des tentatives de correction automatiques qui n'ont jamais réglé le
vrai problème (mise à jour de CMake, ajout de paquets, correction de la syntaxe YAML, et jusqu'à
un remplacement — erroné — de `llama_memory_seq_rm`/`llama_memory_clear` par des noms de
fonctions qui **n'existent pas** dans la version de `external/llama.cpp` embarquée ici, `commit
8887a48f0` ; voir `git log` de `opencompanion_bridge.cpp` pour ce commit et sa correction).

La vraie cause, reproduite ici même dans un environnement au gabarit comparable à un runner
GitHub Actions "privé" (2 vCPU / 8 Go de RAM, voir la documentation GitHub sur les runners
hébergés) : `ggml-vulkan` génère un fichier C++ (`mul_mm.comp.cpp`) qui contient **toutes les
variantes de shaders** de multiplication matricielle sous forme de tableaux C++ énormes. Le
compiler (via `clang++` du NDK) demande plusieurs gigaoctets de RAM à lui seul ; dès que deux
instances tournent en parallèle (compilation à `-j2` par défaut sur un runner 2 cœurs), le noyau
tue le processus (`Killed`, message de l'OOM killer du cgroup — **pas** un message `error:` du
compilateur). C'est pour ça que les tentatives de correction précédentes ne marchaient jamais :
il n'y avait pas de bug de code à corriger, juste pas assez de mémoire pendant la compilation.

**Correctif appliqué** (`.github/workflows/android-build.yml`) : 8 Go de swap ajoutés juste après
le clonage du dépôt, avant toute étape lourde. C'est la mitigation standard pour ce problème connu
de `ggml-vulkan` sur des machines à mémoire limitée — plus lent dans le pire cas (swap), mais ça ne
plante plus.

Premier essai (script `fallocate -l 8G /swapfile` à la main) : a lui-même échoué en CI —
`fallocate: fallocate failed: Text file busy` — parce que l'image Ubuntu des runners GitHub
Actions a déjà un fichier swap actif à ce chemin précis (`/swapfile`), donc l'écrire par-dessus
échoue. Corrigé en passant par l'action dédiée `pierotofy/set-swap-space`, qui gère elle-même le
dimensionnement et l'emplacement plutôt que de refaire ce calcul à la main.

**Validé** : recompilé `opencompanion_bridge.cpp.o` seul (a réussi du premier coup, avant même le
crash — la correction de code du bot était donc un faux positif) ; `mul_mm.comp.cpp` recompilé
en solo (`ninja -j1`, sans concurrence mémoire) a bien dépassé le point où le build à plusieurs
jobs plantait, confirmant que la RAM disponible — pas le code — était la cause. Le fichier est
lui-même très lent à compiler (30+ minutes sur un CPU faible même seul) ; la CI GitHub, avec un
vCPU généralement moins contraint et le swap ajouté, devrait s'en sortir plus vite et sans OOM —
à confirmer sur le prochain run réel.

**Leçon retenue, dans la continuité de celle sur Bonsai 27B plus bas** : un "correctif" poussé
par un outil (bot ou agent) qui n'a pas d'abord identifié la vraie cause d'un échec de CI peut
introduire une régression qui a l'air plausible (un nom de fonction "corrigé") sans l'être —
toujours vérifier le message d'erreur réel avant d'appliquer un correctif de compilation.

## Piège vérifié pendant la conception : en-têtes C++ Vulkan (vulkan.hpp) non propagés

Une fois `glslc`/`SPIRV-Headers` trouvés, la compilation de `ggml-vulkan.cpp`
échouait avec `fatal error: 'vulkan/vulkan.hpp' file not found` malgré
`find_package(Vulkan)` détectant bien Vulkan 1.3.275 et `Vulkan_INCLUDE_DIR`
correctement renseigné dans le cache CMake. Investigation (via
`get_target_property`, inspection directe du `build.ninja` généré, et
`clang++ -E -v` sur le compilateur NDK) : la cible importée `Vulkan::Vulkan`
reçoit bien la bonne propriété `INTERFACE_INCLUDE_DIRECTORIES`, mais CMake la
supprime silencieusement de la ligne de commande de compilation parce que la
liste d'includes *implicites* du compilateur NDK pour ce triplet se termine
par `.../sysroot/usr/include` — et CMake traite notre `/usr/include` (chemin
HÔTE, différent, non lié par un lien symbolique) comme un doublon de ce
répertoire implicite. `target_include_directories()` ne suffit donc pas dans
ce cas précis en cross-compilation.

Contournement retenu (`app/src/main/cpp/CMakeLists.txt`) : passer le
répertoire comme flag de compilation brut (`target_compile_options(...
"-isystem...")`) plutôt que comme propriété d'includes — les flags de
compilation échappent à cette déduplication. Deuxième piège rencontré en
testant ce contournement : sur Linux, le seul répertoire trouvé est
`/usr/include` lui-même (racine de TOUS les en-têtes système, pas un
répertoire Vulkan dédié comme le serait `$VULKAN_SDK/include`) — l'ajouter
tel quel en `-isystem` expose toute la libc hôte (glibc) au compilateur
cross vers bionic, ce qui casse la compilation autrement (`bits/wordsize.h
file not found`, en-têtes glibc multiarch introuvables hors de leur
arborescence). Solution : copier uniquement les sous-répertoires
autonomes `vulkan/` et `spirv/` (aucune dépendance glibc, seulement la STL
C++ déjà fournie par le NDK) dans un dossier isolé sous le répertoire de
build, et n'exposer que ce dossier isolé en `-isystem`.

## Piège vérifié pendant la conception : symbole Vulkan 1.1 absent avant l'API 28

Une fois la compilation résolue, l'édition de liens de `libopencompanion_bridge.so`
échouait avec `undefined symbol: vkGetPhysicalDeviceFeatures2`. Cause : le
backend Vulkan de llama.cpp appelle cette fonction "core" Vulkan 1.1 (pas la
variante d'extension `vkGetPhysicalDeviceFeatures2KHR`) sans garde de
disponibilité. Or, en inspectant les stubs `libvulkan.so` versionnés par API
fournis par le NDK (`nm -D` sur chaque `sysroot/usr/lib/aarch64-linux-android/<API>/libvulkan.so`),
ce symbole n'est exporté par le loader Vulkan d'Android qu'à partir de
**l'API 28 (Android 9)** — absent des stubs 24/26/27. Avec un `minSdk`
inférieur, soit l'édition de liens échoue directement (notre cas), soit,
en la contournant, le chargement de la bibliothèque planterait au runtime
sur un appareil réel trop ancien — et comme le backend CPU est compilé dans
la **même** bibliothèque native, ce plantage casserait aussi le repli CPU.
D'où le choix de fixer `minSdk = 28` dans `app/build.gradle.kts` plutôt que
de complexifier l'architecture (bibliothèque native séparée chargée
conditionnellement) pour gagner deux versions d'API dont la part
d'appareils actifs est aujourd'hui marginale.

## Ce qui est solide

- Le backend Vulkan de ggml (`GGML_VULKAN=ON`) compile pour Android via le
  NDK (`android.toolchain.cmake`, `ANDROID_ABI=arm64-v8a`) — voir
  `app/src/main/cpp/CMakeLists.txt`. Le NDK fournit à la fois les en-têtes
  Vulkan et la bibliothèque stub `libvulkan.so` nécessaires à la
  compilation ; le pilote réel est fourni par l'appareil à l'exécution.
- Le backend CPU est **toujours** compilé en plus, jamais à la place :
  `InferenceEngine.ensureModelLoaded(useGpu = false)` fonctionne sur
  n'importe quel appareil, y compris ceux sans Vulkan.
- Le choix de couches déchargées sur GPU (`n_gpu_layers`) est standard :
  l'API publique `llama.h` (`llama_model_load_from_file` avec
  `llama_model_params.n_gpu_layers`) gère elle-même le placement des couches
  sur les backends compilés, sans code de sélection de device à écrire côté
  app. **Historique** : la première version passait systématiquement 999
  (toutes les couches sur GPU) dès que Vulkan était activé — un choix
  tout-ou-rien qui laissait le CPU inactif pendant toute la génération et
  pouvait dépasser la VRAM disponible sur certains appareils. Depuis, ce
  nombre est configurable (`Réglages → Matériel → Couches déchargées sur le
  GPU`, `SettingsRepository.gpuLayers`, défaut 20) : une valeur inférieure au
  nombre réel de couches donne un vrai mode hybride, GPU et CPU travaillant
  ensemble sur le même modèle plutôt que l'un ou l'autre en exclusivité.

## Vitesse : réutilisation du cache KV entre tours

Jusqu'ici, `nativeGenerate()` effaçait tout le cache KV (`llama_memory_clear`) et redécodait
l'intégralité du prompt — system prompt + tout l'historique gardé — à **chaque** message. Sur une
conversation qui s'allonge, ce retraitement du prompt (pas la génération token par token) finit
par dominer largement le temps de réponse, ce qui a été rapporté comme "les IA sont trop longues
à répondre". Le commentaire d'origine en tête de fichier assumait que la vitesse de
prompt-processing de llama.cpp sur mobile compenserait largement ce choix — en usage réel, non.

Le cache est maintenant réutilisé d'un tour à l'autre : `ModelSession::cached_tokens` retient les
tokens réellement résidents dans le cache après le dernier appel réussi (prompt effectivement
décodé + réponse générée). À l'appel suivant, on compare token à token le nouveau prompt avec
`cached_tokens` ; le plus long préfixe identique est conservé (`llama_memory_seq_rm(mem, 0,
common_prefix, -1)` retire seulement ce qui diverge, pas tout), et seule la partie qui diverge est
redécodée. Le placement automatique des positions de `llama_batch_get_one` (voir plus haut) se
base sur l'occupation réelle du cache KV (`seq_pos_max + 1`), donc reprendre le décodage après un
`seq_rm` partiel fonctionne sans code de positionnement explicite à écrire.

Cette comparaison est **sûre par construction** : elle s'arrête au premier token différent, donc
ne peut jamais réutiliser à tort un fragment de cache qui ne correspond plus au prompt actuel — au
pire (personnage différent, historique tronqué/édité, ou reformulation par
`ThinkBlockFilter`/le découpage en bulles côté Kotlin), elle ne trouve aucun préfixe commun et se
comporte exactement comme avant (tout redécodé). Le gain dépend donc de la stabilité du texte
reconstruit par `PromptBuilder.kt` d'un tour à l'autre : fort pour le system prompt et les tours
anciens (qui ne changent jamais une fois écrits), plus limité sur le tour juste précédent si le
modèle a pensé (`<think>`, filtré donc absent du texte re-rendu) ou si la réponse a été scindée en
plusieurs bulles — un motif de plus pour la consigne "jamais de balises `<think>`" déjà présente
dans `LANGUAGE_AND_TONE_DIRECTIVE`.

L'état du cache est explicitement invalidé (`cached_tokens.clear()`) après toute issue où il n'est
plus fiable : erreur de décodage, exception C++, ou échec partiel de `llama_memory_seq_rm` (auquel
cas on retombe sur un `llama_memory_clear` complet). Un rechargement du modèle (changement de
réglage GPU/contexte, repli CPU après plantage Vulkan...) crée de toute façon un nouveau
`ModelSession`, donc un cache vide — pas de risque de réutiliser un cache d'un modèle différent.

**Validé** (contrairement à la plupart de ce document — cette logique est indépendante du
backend GPU, donc testable en CPU pur hors Android) : harness hôte reproduisant fidèlement cette
logique (`nativeGenerate` sans le pont JNI), Qwen3-0.6B-Q4_K_M réel, 4 tours de conversation
consécutifs avec patron de dialogue du modèle. Résultat : aucun plantage, taux de réutilisation du
cache croissant comme attendu (0 % au 1er tour → 84 % → 91 % → 94 % au 4e), et texte généré
cohérent à chaque tour (le modèle référence correctement les échanges précédents dans son
raisonnement), signe qu'aucun décalage de position n'a corrompu le contexte. Reste à valider en
conditions réelles : le gain de temps perçu (pas seulement le ratio de tokens réutilisés) sur
appareil Android, et sur une conversation beaucoup plus longue que 4 tours.

## Chargement du modèle : filet `try/catch` ajouté

`nativeLoadModel()` n'avait, à l'origine, aucune protection contre une exception C++ (contrairement
à `nativeGenerate()`, protégé depuis le départ — voir plus bas) : un échec d'allocation mémoire
(`std::bad_alloc` sur un modèle trop gros pour l'appareil) ou une exception levée à
l'initialisation du device Vulkan aurait traversé la frontière JNI et abattu tout le processus.
Un `try/catch` a été ajouté autour du chargement, symétrique à celui de `nativeGenerate()`.
**Limite importante** : ça ne protège pas contre un `GGML_ASSERT`/`abort()` interne à llama.cpp
(un opérateur de calcul non implémenté pour l'architecture ou le backend chargé, par exemple) —
ce sont des arrêts bas niveau du processus, pas des exceptions C++, donc rigoureusement rien côté
JNI ne peut les rattraper. C'est précisément ce qui a motivé le retrait de Bonsai 27B de la liste
de modèles recommandés (voir `docs/MODELES_ET_AICORE.md`) plutôt qu'une tentative de correctif :
sans accès à un appareil réel pour obtenir les logs du plantage, impossible de savoir avec
certitude s'il s'agit de ce cas précis ou d'un simple manque de mémoire.

## Ce qui est documenté comme fragile

Plusieurs rapports (issues publiques `ggml-org/llama.cpp`) décrivent deux
familles de problèmes sur Android :

1. **Échecs de compilation croisée** liés à la génération des shaders
   "cooperative matrix" (symboles du type
   `flash_attn_f32_f16_f16_f16acc_cm2_len` non déclarés). Un correctif
   ("fix coopmat shader generation when cross-compiling") est entré dans
   llama.cpp ; le sous-module de ce dépôt est épinglé sur un commit
   postérieur à ce correctif. Si un `git submodule update --remote` future
   fait remonter ce genre d'erreur, revenir au commit épinglé actuel le
   temps de vérifier qu'un correctif équivalent existe en amont.
2. **Plantages à l'exécution** (`vk::DeviceLostError`) rapportés sur
   certains GPU Adreno (Snapdragon), en particulier à taille de batch
   élevée. Rien ne garantit qu'un appareil donné soit épargné.

## Comment ce dépôt s'en protège

- `opencompanion_bridge.cpp` encadre **toute** la boucle de génération dans
  un `try/catch` C++ : une exception levée par le pilote Vulkan (via
  Vulkan-Hpp) ne doit jamais traverser la frontière JNI — ça abattrait tout
  le processus de l'app, pas seulement la génération en cours. Le code
  natif renvoie un code d'erreur négatif à la place.
- Côté Kotlin, `ChatViewModel` intercepte ce code d'erreur
  (`GenerationEvent.GpuFailure`), désactive le GPU pour la session
  (`SettingsRepository.markGpuUnstable`), recharge le modèle en CPU pur et
  **retente une fois automatiquement** la même requête, plutôt que de
  planter ou de laisser l'utilisateur face à une réponse vide.
- Le réglage GPU explicite de l'utilisateur (`Réglages → Utiliser le GPU`)
  n'est jamais modifié silencieusement : seul le mode "session" est mis en
  pause après un plantage, pour ne pas cacher un vrai bug matériel/pilote
  derrière un simple oubli de réactivation.

## Piège vérifié en usage réel : chargement du modèle bloqué indéfiniment

Premier vrai test sur un appareil physique (après les correctifs CI ci-dessus, qui avaient
laissé le backend Vulkan jamais réellement testé hors du cloud) : l'app restait bloquée sur
l'écran "chargement du modèle" sans jamais aboutir ni afficher d'erreur.

Cause probable : `nativeLoadModel()` (JNI) est un appel **bloquant**, pas une fonction suspend.
Avant ce correctif, `ensureModelLoaded()` l'appelait via un simple `withContext(Dispatchers.IO)`
sans aucun délai — si l'appel natif se bloque réellement (le cas documenté plus haut : le pilote
Vulkan d'un appareil qui bloque plutôt que d'échouer à l'initialisation), rien côté Kotlin ne
pouvait s'en apercevoir ni reprendre la main : l'utilisateur restait bloqué sans recours, aucun
message d'erreur, aucun moyen de continuer sans forcer la fermeture de l'app.

**Correctif** (`InferenceEngine.ensureModelLoaded`) : l'appel natif est lancé sur son propre thread
(`engineScope.async`) et attendu avec un délai de 45s (`withTimeoutOrNull`). Point important : un
`withTimeoutOrNull` placé directement autour d'un appel bloquant ne sert à rien (il attend quand
même la fin réelle de l'appel avant de constater le dépassement) — c'est `await()` sur le
`Deferred` séparé qui est un vrai point de suspension annulable. Limite assumée : l'appel natif
bloqué continue de tourner en arrière-plan (aucune API Kotlin ne peut interrompre un appel JNI
bloquant en cours) ; s'il aboutit après coup, la session orpheline est libérée aussitôt pour ne
pas fuir la mémoire native (un modèle peut peser plusieurs Go).

Au niveau `ChatViewModel.loadModelIfNeeded` : même repli automatique CPU qu'un échec de
génération GPU (voir plus haut) — un dépassement de délai avec GPU activé désactive le GPU pour
la session et relance le chargement en CPU pur, une seule fois, avant d'afficher une vraie erreur
si ça échoue aussi. Avant ce correctif, ce repli n'existait que pour les échecs de GÉNÉRATION,
pas de CHARGEMENT — un point mort pour tout appareil dont le pilote Vulkan bloque dès l'init.

**Non vérifié** : le vrai comportement du pilote Vulkan de l'appareil concerné (bloqué vs juste
très lent), faute de logcat récupéré au moment du blocage — le délai de 45s est un choix
raisonnable mais pas calibré sur mesure réelle. À ajuster si de vrais logs `adb logcat -s
OpenCompanionNative` montrent un chargement légitimement plus long (gros modèle sur stockage lent
+ contexte proche de 16384) déclenchant ce filet à tort.

## Ce qui reste à valider sur de vrais appareils

Ce projet a été conçu dans un environnement cloud sans GPU ni Vulkan : tout
ce qui précède est une conception défensive basée sur la documentation et
les retours publics, **pas une vérification sur matériel réel**. À tester
en priorité sur un ou deux appareils Android physiques différents (idéalement
un avec GPU Adreno, un avec GPU Mali) :

- Chargement d'un petit modèle GGUF (< 2 Go) avec GPU activé, en observant
  le débit (tokens/s) affiché par les logs `adb logcat -s OpenCompanionNative`.
- Comparaison CPU vs GPU sur le même modèle et le même prompt.
- **Réutilisation du cache KV** (voir plus haut) : vérifier sur plusieurs tours
  consécutifs d'une même conversation que la réponse générée reste cohérente
  (pas de mots tronqués/incohérents en début de réponse, signe d'un
  positionnement KV décalé) et que le débit perçu s'améliore réellement à
  mesure que la conversation avance, en comparant les temps de première
  réponse (`adb logcat -s OpenCompanionNative`) entre le 2e et le 10e message
  d'une même conversation.
- Comportement en cas de génération longue (contexte proche de la limite) :
  vérifier qu'aucun plantage n'apparaît et que le repli CPU se déclenche
  proprement si un problème survient.
