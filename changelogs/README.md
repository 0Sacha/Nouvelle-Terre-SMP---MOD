# Changelogs

Un fichier par version publiée : `changelogs/<version>.md`, par exemple `2.2.3.md`.

Ce fichier sert deux fois :

- il devient la description de la **release GitHub** (voir `.github/workflows/build.yml`) ;
- le **bot Discord** le lit pour `/changelog publier` et `/changelog installation`.

Il s'adresse aux **joueurs** : on y décrit ce qui change pour eux, pas le code. Pas de nom de
classe, pas de détail d'une faille corrigée (« plusieurs failles de l'économie ont été
corrigées » suffit — expliquer comment on trichait donne la recette à ceux qui n'ont pas
encore mis à jour).

## Format

```markdown
# 2.2.3 — Titre court de la version

> Une ou deux phrases d'accroche (facultatif).

<!-- mod: Nom | https://lien | à quoi il sert -->

## ✨ Nouveautés

### ⌛ Nom de la nouveauté
Une phrase qui dit ce que c'est et où ça se trouve.
- Un point par chose à savoir.

## 🔧 Changements

- **Sujet** : ce qui change.

## 🛡️ Sous le capot

- Ce qui ne se voit pas mais compte.
```

## Règles

- **Première ligne** : `# <version> — <titre>`. La version doit être celle de `mod_version`.
- **Sections `##`** : chacune devient un encadré coloré sur Discord. La couleur vient de
  l'émoji qui ouvre le titre :

  | Émoji | Section | Couleur |
  |---|---|---|
  | ✨ 🆕 | Nouveautés | or |
  | 🔧 ⚖ | Changements, équilibrage | bleu |
  | 🐛 ⚠ | Corrections, à savoir | rouge |
  | 🛡 ⚡ | Sous le capot, performance | vert |

  Garder cet ordre, et ne mettre que les sections qui ont du contenu.
- **Sous-titres `###`** : affichés en gras. Un émoji en tête aide à s'y retrouver.
- **Listes** : `- `, une idée par point, pas de point final sur les fragments.
- **Longueur** : un encadré tient 3 800 caractères ; au-delà le bot le coupe en deux, à la
  fin d'une ligne. Viser plus court.
- **`<!-- mod: … -->`** : un par mod que les joueurs doivent **ajouter eux-mêmes** pour
  cette version. Ces lignes sont invisibles sur GitHub ; le bot en fait la liste « Mods à
  ajouter » du message d'installation. Rien à écrire pour Nouvelle Terre Bridge lui-même :
  la marche à suivre et le lien de téléchargement sont générés tout seuls.

## Ce qui n'y va pas

La marche à suivre pour installer la mise à jour. Elle est publiée à part
(`/changelog installation`) : c'est le seul message que tout le monde doit lire et suivre,
il ne doit pas être noyé dans la liste des nouveautés.
