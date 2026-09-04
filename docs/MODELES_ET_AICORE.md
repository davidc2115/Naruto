# Moteurs d'IA : Gemini Nano (AICore) et modèles GGUF recommandés

Ce document résume ce qui a été vérifié (sources publiques, septembre 2026) pour les deux
ajouts apportés au moteur d'inférence : le backend optionnel **Gemini Nano via AICore**, et les
**préréglages de modèles GGUF** proposés en un tap dans Réglages → Modèle.

## Gemini Nano / AICore : ce que c'est vraiment

Contrairement à un modèle GGUF, Gemini Nano n'est **pas un fichier qu'on embarque dans l'app**.
C'est un modèle propriétaire de Google exécuté par **AICore**, un service système Android,
accessible uniquement via l'API **ML Kit GenAI Prompt**
(`com.google.mlkit:genai-prompt`, encore en **Beta** au moment de l'écriture) :

- **Génération sur l'appareil**, sans appel réseau au moment de discuter — mais le service
  AICore lui-même dépend de **Google Play Services**, et Gemini Nano n'est réellement
  disponible (supporté *et* déjà téléchargé) que sur une partie des appareils Android récents,
  Pixel en tête. Ce n'est donc pas un remplacement universel de llama.cpp, plutôt un raccourci
  "rapide" quand l'appareil le permet.
- **Quota strict** : entrée + sortie plafonnées à environ **4000 tokens au total**, imposé par
  AICore lui-même. Inadapté à une conversation longue ou à un contexte volumineux — c'est
  pourquoi ce backend utilise son propre budget de troncature de l'historique
  ([`PromptBuilder.buildNanoPrompt`], plus strict que celui de llama.cpp).
- **API Beta** : le nom des classes, méthodes et constantes utilisées dans
  [`engine/NanoBridge.kt`](../app/src/main/java/com/opencompanion/app/engine/NanoBridge.kt)
  (`Generation.getClient()`, `GenerativeModel.checkStatus()`, `FeatureStatus`, `DownloadStatus`,
  `generateContentStream(...)`) est celui documenté officiellement par Google au moment de
  l'écriture (voir sources en bas de page) — **non testé sur un appareil physique** (aucun
  Pixel avec Gemini Nano dans l'environnement de conception). À vérifier en priorité lors du
  premier essai réel.

### Comment ce dépôt s'en protège

Même philosophie défensive que pour le backend Vulkan (voir `docs/VULKAN_NOTES.md`) :
`NanoBridge.checkAvailability()` et `NanoBridge.generate()` absorbent **toute** exception et la
traduisent en un état applicatif normal (`NanoAvailability.UNAVAILABLE`, ou
`GenerationEvent.Error`) plutôt que de laisser planter l'app. Trois réglages possibles dans
Réglages → « Moteur d'IA » :

- **Auto** (par défaut) : essaie Gemini Nano si AICore le rapporte *disponible maintenant* (pas
  "téléchargeable", pas "en cours de téléchargement" — dans ces deux cas, retombe directement
  sur llama.cpp plutôt que d'attendre) ; si la génération Gemini Nano échoue en cours de route,
  **bascule automatiquement** sur le modèle GGUF local configuré (voir `ChatViewModel.
  runNanoGeneration`), comme le repli GPU→CPU existant.
- **Gemini Nano (AICore)** : forcé, sans repli automatique — utile pour tester ce backend
  spécifiquement.
- **Modèle local (llama.cpp)** : comportement inchangé par rapport à avant cet ajout.

## Modèles GGUF recommandés

Six préréglages, en téléchargement direct HTTP (fichiers publics, aucune clé ni compte requis —
même mécanisme que l'import par URL déjà existant), répartis en deux profils :

| Modèle | Profil | Taille | Paramètres | Licence |
|---|---|---|---|---|
| Qwen3 0.6B | ⚡ Rapide | ~0,40 Go | 0,6 Md | Apache 2.0 |
| Gemma 3 1B | ⚡ Rapide | ~0,81 Go | 1 Md | Gemma (Google) |
| Llama 3.2 1B | ⚡ Rapide | ~0,81 Go | 1 Md | Llama 3.2 Community License |
| Phi-4-mini | ★ Qualité | ~2,49 Go | 3,8 Md | MIT |
| Qwen3 4B | ★ Qualité | ~2,50 Go | 4 Md | Apache 2.0 |
| Gemma 3 4B | ★ Qualité | ~2,49 Go | 4 Md | Gemma (Google) |

Détail dans [`engine/RecommendedModels.kt`](../app/src/main/java/com/opencompanion/app/engine/RecommendedModels.kt).
Le profil **Rapide** vise une réponse quasi instantanée même en CPU pur ; le profil **Qualité**
donne de bien meilleures réponses mais reste nettement plus confortable avec le GPU Vulkan
activé (voir `docs/VULKAN_NOTES.md`) qu'en CPU pur sur un téléphone d'entrée de gamme.

### Retiré : Bonsai 27B — planté en usage réel, leçon retenue

[`prism-ml/Bonsai-27B-gguf`](https://huggingface.co/prism-ml/Bonsai-27B-gguf) (27 Md de
paramètres, base Qwen3.6/architecture GGUF `qwen35`, quantification "ternaire" native au format
`Q1_0`, ~3,80 Go) a été ajouté un temps à cette liste, puis retiré après un signalement concret :
il fait planter l'application sur un téléphone réel. Ce qui avait été vérifié avant l'ajout était
réel mais **insuffisant** — leçon à retenir pour tout futur modèle "exotique" proposé ici :

- **Vérifié (et correct)** : le sous-module `external/llama.cpp` reconnaît nativement le type de
  tenseur `GGML_TYPE_Q1_0` et l'architecture `qwen35`, CPU et Vulkan — donc pas besoin d'un fork
  spécial de llama.cpp pour que le *format* du fichier soit reconnu, contrairement à ce que
  laissent penser certains articles à propos de ce modèle.
- **Pas vérifié (et probablement la vraie cause du plantage)** : reconnaître un type de donnée
  (comment les poids sont stockés) ne garantit pas que tous les **opérateurs de calcul** requis
  par le graphe d'inférence du modèle soient implémentés pour un backend donné. L'architecture de
  Bonsai mélange environ 75 % de couches d'attention "linéaire" (façon state-space/gated, plus
  proche d'un Mamba que d'un transformeur classique) et 25 % d'attention classique — un mélange
  qui sollicite des opérateurs spécifiques (scan/état récurrent...) potentiellement absents ou
  incomplets côté Vulkan sur la version de llama.cpp embarquée. Rien de tout ça n'a été contrôlé
  avant l'ajout initial.
- Le pic mémoire réel (poids + cache KV, avec un contexte non trivial) n'a pas non plus été
  mesuré sur un appareil réel avant l'ajout — un simple dépassement mémoire (OOM) tue le
  processus tout aussi brutalement qu'un opérateur manquant.
- Dans les deux cas (opérateur non implémenté → `GGML_ASSERT`/`abort()`, ou OOM), le plantage
  est **impossible à rattraper** côté JNI : ce sont des arrêts bas niveau du processus, pas des
  exceptions C++ — voir `docs/VULKAN_NOTES.md` et le commentaire de `nativeGenerate` dans
  `opencompanion_bridge.cpp`. `nativeLoadModel` a quand même été renforcé d'un `try/catch` (utile
  contre un vrai échec d'allocation C++, `std::bad_alloc` par exemple) mais ça ne couvre pas ces
  deux cas précis.

**Règle retenue pour un futur ajout de modèle inhabituel** : vérifier le support du *format*
(type de tenseur, architecture reconnue) ne suffit pas — il faut aussi soit trouver un rapport
d'usage réel de ce modèle précis avec llama.cpp + Vulkan sur Android, soit accepter de ne
l'ajouter qu'après un test sur un appareil physique, jamais uniquement sur la base d'une lecture
de code.

Ces six modèles restent des suggestions : l'import libre par fichier ou URL directe
(`ModelManager`) fonctionne toujours avec n'importe quel autre GGUF — y compris Bonsai 27B, pour
qui voudrait tenter sa chance en connaissance de cause.

## Sources consultées

- [AI on Android](https://developer.android.com/ai) — Google
- [Gemini Nano sur Android (AICore)](https://developer.android.com/ai/gemini-nano)
- [ML Kit GenAI — Prompt API, démarrage](https://developers.google.com/ml-kit/genai/prompt/android/get-started)
- [ML Kit GenAI Prompt API — annonce alpha/beta](https://android-developers.googleblog.com/2025/10/ml-kit-genai-prompt-api-alpha-release.html)
- [`com.google.mlkit.genai.prompt.GenerativeModel` (référence API)](https://developers.google.com/android/reference/kotlin/com/google/mlkit/genai/prompt/GenerativeModel)
- [`com.google.mlkit.genai.common` (référence API)](https://developers.google.com/android/reference/com/google/mlkit/genai/common/package-summary)
- Dépôts Hugging Face publics (`unsloth/...-GGUF`) pour les tailles exactes de fichiers Q4_K_M
  des modèles recommandés.
- [`prism-ml/Bonsai-27B-gguf`](https://huggingface.co/prism-ml/Bonsai-27B-gguf) — fiche modèle,
  taille exacte du fichier `Bonsai-27B-Q1_0.gguf` et architecture GGUF (`qwen35`).
- Code source du sous-module `external/llama.cpp` embarqué (`src/llama-arch.cpp`,
  `src/models/models.h`, `ggml/include/ggml.h`, `ggml/src/ggml-vulkan/`) — vérification directe
  du support de l'architecture `qwen35` et du type `Q1_0` (CPU + Vulkan) avant d'ajouter Bonsai
  27B à la liste, plutôt que de se fier uniquement aux articles tiers à son sujet.
