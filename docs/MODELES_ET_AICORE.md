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

Sept préréglages, en téléchargement direct HTTP (fichiers publics, aucune clé ni compte requis —
même mécanisme que l'import par URL déjà existant), répartis en trois profils :

| Modèle | Profil | Taille | Paramètres | Licence |
|---|---|---|---|---|
| Qwen3 0.6B | ⚡ Rapide | ~0,40 Go | 0,6 Md | Apache 2.0 |
| Gemma 3 1B | ⚡ Rapide | ~0,81 Go | 1 Md | Gemma (Google) |
| Llama 3.2 1B | ⚡ Rapide | ~0,81 Go | 1 Md | Llama 3.2 Community License |
| Phi-4-mini | ★ Qualité | ~2,49 Go | 3,8 Md | MIT |
| Qwen3 4B | ★ Qualité | ~2,50 Go | 4 Md | Apache 2.0 |
| Gemma 3 4B | ★ Qualité | ~2,49 Go | 4 Md | Gemma (Google) |
| Bonsai 27B (Q1_0) | 🐘 Énorme (expérimental) | ~3,80 Go | 27 Md (~1,1 bit/poids) | Apache 2.0 |

Détail dans [`engine/RecommendedModels.kt`](../app/src/main/java/com/opencompanion/app/engine/RecommendedModels.kt).
Le profil **Rapide** vise une réponse quasi instantanée même en CPU pur ; le profil **Qualité**
donne de bien meilleures réponses mais reste nettement plus confortable avec le GPU Vulkan
activé (voir `docs/VULKAN_NOTES.md`) qu'en CPU pur sur un téléphone d'entrée de gamme.

### Bonsai 27B : un 27 Md de paramètres qui tient dans ~3,8 Go

[`prism-ml/Bonsai-27B-gguf`](https://huggingface.co/prism-ml/Bonsai-27B-gguf) compresse un
modèle de 27 milliards de paramètres (base Qwen3.6, architecture GGUF `qwen35`) en quantification
"ternaire" native (type de tenseur `Q1_0`, ~1,1 bit par poids au lieu des ~4 bits habituels d'un
GGUF classique type Q4_K_M) — d'où une taille de fichier comparable à un modèle 4 Md malgré un
nombre de paramètres bien plus élevé. Point vérifié avant de l'ajouter : certains articles autour
de ce modèle indiquent qu'un fork spécial de llama.cpp est nécessaire pour le faire tourner, mais
le sous-module `external/llama.cpp` de ce dépôt (épinglé sur un commit de septembre 2026)
reconnaît déjà nativement l'architecture `qwen35` et le type `GGML_TYPE_Q1_0` — CPU **et**
Vulkan (`ggml/src/ggml-vulkan/vulkan-shaders/`) — donc aucun changement de moteur n'a été
nécessaire pour l'ajouter à la liste. Le fichier proposé (`Bonsai-27B-Q1_0.gguf`, ~3,80 Go) est le
modèle de texte seul ; les fichiers annexes du dépôt (`mmproj-*`, vision) et le "drafter"
(`dspark-*`, décodage spéculatif) ne sont pas utilisés ici. Malgré sa taille de fichier modeste,
c'est un modèle 27 Md : à réserver aux appareils avec beaucoup de RAM, idéalement combiné au
réglage `Réglages → Matériel → Couches déchargées sur le GPU` en mode hybride plutôt que tout
CPU. **Non testé sur un appareil physique** — même réserve que pour Gemini Nano/Vulkan ci-dessus.

Ces sept modèles restent des suggestions : l'import libre par fichier ou URL directe
(`ModelManager`) fonctionne toujours avec n'importe quel autre GGUF.

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
