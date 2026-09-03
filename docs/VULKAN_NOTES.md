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

## Ce qui reste à valider sur de vrais appareils

Ce projet a été conçu dans un environnement cloud sans GPU ni Vulkan : tout
ce qui précède est une conception défensive basée sur la documentation et
les retours publics, **pas une vérification sur matériel réel**. À tester
en priorité sur un ou deux appareils Android physiques différents (idéalement
un avec GPU Adreno, un avec GPU Mali) :

- Chargement d'un petit modèle GGUF (< 2 Go) avec GPU activé, en observant
  le débit (tokens/s) affiché par les logs `adb logcat -s OpenCompanionNative`.
- Comparaison CPU vs GPU sur le même modèle et le même prompt.
- Comportement en cas de génération longue (contexte proche de la limite) :
  vérifier qu'aucun plantage n'apparaît et que le repli CPU se déclenche
  proprement si un problème survient.
