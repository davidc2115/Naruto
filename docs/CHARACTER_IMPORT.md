# Format et import des personnages

## Format : Character Card V2

OpenCompanion lit et écrit le format ouvert
[Character Card V2](https://github.com/malfoyslastname/character-card-spec-v2),
largement utilisé par les outils communautaires de chat avec IA. C'est un
choix délibéré : plutôt qu'un format maison, réutiliser une spec publique
signifie qu'une fiche créée ailleurs s'importe directement, et qu'une fiche
créée dans OpenCompanion s'exporte vers d'autres outils compatibles.

```json
{
  "spec": "chara_card_v2",
  "spec_version": "2.0",
  "data": {
    "name": "…",
    "description": "…",
    "personality": "…",
    "scenario": "…",
    "first_mes": "…",
    "mes_example": "…",
    "creator_notes": "…",
    "system_prompt": "…",
    "tags": ["…"],
    "creator": "…",
    "character_version": "…"
  }
}
```

`CharacterCardCodec.kt` (`app/src/main/java/.../charactercard/`) accepte
aussi le format V1 historique, où ces mêmes champs sont directement à la
racine du JSON sans l'enveloppe `spec`/`data`.

## Encodage dans une image PNG

La convention communautaire (compatible avec la plupart des sites et
outils) consiste à embarquer ce JSON, encodé en base64, dans un chunk PNG
`tEXt` de mot-clé `chara` — l'image PNG (l'avatar) et la fiche voyagent
ainsi dans un seul fichier. `PngCharaChunkCodec.kt` lit ce chunk (et
reconnaît aussi le mot-clé `ccv3` de la
[spec V3](https://github.com/kwaroran/character-card-spec-v3)) sans jamais
décoder l'image elle-même — uniquement la structure de chunks PNG.

## Les trois voies d'import

Toutes convergent vers `CharacterImportManager.importFromBytes` dès que les
octets du fichier sont en main — l'origine n'a alors plus d'importance :

1. **Partage depuis une autre app** : "Envoyer vers OpenCompanion" sur une
   image PNG, un fichier `.json`, ou un texte contenant un lien direct,
   depuis n'importe quel navigateur, galerie ou gestionnaire de fichiers
   (voir les `intent-filter` de `MainActivity` dans `AndroidManifest.xml`).
   C'est la voie la plus "fluide" : aucun aller-retour par un sélecteur de
   fichiers.
2. **Sélecteur de fichiers** (Storage Access Framework) : pour un fichier
   déjà présent sur l'appareil.
3. **URL directe** : un champ à coller dans l'app (liste des personnages ou
   réglages) — téléchargement HTTP(S) simple, sans authentification,
   compatible avec n'importe quel hébergeur.

## Export

`CharacterImportManager.exportAsPngOrJson` réencode une fiche locale au
format ci-dessus : si le personnage a un avatar, le JSON est réembarqué
dans une copie du PNG (chunk `tEXt/chara`) ; sinon un fichier `.json` brut
est produit. De quoi partager un personnage créé dans l'app vers un autre
outil compatible, ou simplement le sauvegarder.

## Limites connues

- Seuls les chunks `tEXt` sont lus (pas `zTXt`, compressé) : une fiche
  encodée avec un outil qui compresse le chunk ne sera pas reconnue. Peu
  d'outils communautaires font ce choix en pratique, mais si ça arrive,
  le message d'erreur d'import précisera "chunk 'chara'/'ccv3' absent".
- Le champ `character_book` (lorebook) de la spec V2 n'est pas encore
  supporté (ignoré à l'import, absent à l'export) — voir la section
  "pistes" ci-dessous si quelqu'un veut s'y attaquer.
- L'historique de conversation n'est pas résumé automatiquement quand il
  dépasse la fenêtre de contexte du modèle : les messages les plus anciens
  sont simplement exclus du prompt (voir `PromptBuilder.buildTurns`).
