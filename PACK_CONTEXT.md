# Nouvelle Terre — Contexte Launcher / Modpack

Ce fichier est un briefing à destination de Claude pour le projet **Nouvelle-Terre-SMP---Pack**.
Il documente les décisions prises dans le repo du mod pour ne pas repartir de zéro.

## Repos liés
- **Mod** : `https://github.com/0Sacha/Nouvelle-Terre-SMP---MOD.git`
- **Bot Discord** : `https://github.com/0Sacha/Nouvelle-Terre-SMP---Discord-BOT.git`
- **Pack** (nouveau) : à créer — `Nouvelle-Terre-SMP---Pack`

## Infrastructure
- Serveur Minecraft : Minestrator — IP `91.197.6.86`, port `24314`
- Bot Discord : Railway (Node.js), user `Nouvelle Terre#9576`
- Guild ID Discord : `1508123190797406432`
- Email : `sacha_laville@outlook.fr`

## Stack technique décidée

### Approche : packwiz + GitHub Pages + Prism Launcher
- Le modpack est géré avec **packwiz** (CLI)
- Les fichiers du pack sont hébergés via **GitHub Pages** du repo Pack
- Les joueurs installent **Prism Launcher**, importent un `.mrpack` une seule fois
- Un hook pre-launch (`packwiz-installer-bootstrap.jar`) vérifie et applique les mises à jour **automatiquement à chaque lancement**
- Les joueurs n'ont jamais à réinstaller manuellement après la première fois

### Pourquoi pas un launcher custom ?
- Les launchers custom nécessitent une approbation Microsoft/Mojang pour l'auth des comptes
- Impossible pour un projet indépendant sans partnership Mojang
- Modrinth App a été écarté car les joueurs devaient réinstaller manuellement à chaque maj du pack

## Mod — infos techniques
- Fabric Loader : `0.15.7`
- Minecraft : `1.20.1`
- Version actuelle du mod : voir `gradle.properties` (`mod_version`)
- Le JAR du mod est publié automatiquement sur GitHub Releases à chaque push sur `main`
  - URL pattern : `https://github.com/0Sacha/Nouvelle-Terre-SMP---MOD/releases/latest/download/nouvelle-terre-bridge-{version}.jar`
- Le mod tourne côté **client ET serveur** — les joueurs doivent l'avoir installé
- Dépendances du mod :
  - `fabric_version=0.92.0+1.20.1`
  - `cadmus_version=1.0.8+1.20.1` (gestion territoires)

## Resource pack HDV
- URL fixe : `https://github.com/0Sacha/Nouvelle-Terre-SMP---MOD/releases/latest/download/nouvelle-terre-hdv.zip`
- Généré par GitHub Actions à chaque release, hash SHA-1 calculé depuis le fichier téléchargé
- Le serveur envoie le resource pack automatiquement aux joueurs à la connexion
- Ne pas inclure le resource pack dans le modpack packwiz (déjà géré serveur-side)

## Ce qu'il faut faire dans le repo Pack

### Structure cible
```
pack.toml                  ← config packwiz (MC version, loader, pack name)
mods/                      ← références mods (.pw.toml par mod)
  nouvelle-terre-bridge.pw.toml
  fabric-api.pw.toml
  cadmus.pw.toml
  [autres mods éventuels]
index.toml                 ← index auto-généré par packwiz
.github/
  workflows/
    deploy.yml             ← génère le pack et publie sur GitHub Pages
README.md                  ← instructions d'installation pour les joueurs
```

### Workflow GitHub Actions à mettre en place
1. Push sur `main` → Actions lance `packwiz refresh` + `packwiz modrinth export` (génère `.mrpack`)
2. Publie les fichiers du pack sur GitHub Pages (branche `gh-pages`)
3. Attache le `.mrpack` à une Release GitHub (pour le téléchargement initial joueur)

### Installation joueur (première fois)
1. Installer **Prism Launcher** : https://prismlauncher.org
2. Télécharger le `.mrpack` depuis les Releases du repo Pack
3. Dans Prism : "Add Instance" → "Import from zip" → sélectionner le `.mrpack`
4. L'instance est configurée automatiquement avec le hook packwiz-installer-bootstrap

### Mise à jour automatique (après la première installation)
- Le `packwiz-installer-bootstrap.jar` est configuré comme argument JVM pre-launch dans Prism
- À chaque lancement, il fetch `https://0sacha.github.io/Nouvelle-Terre-SMP---Pack/pack.toml`
- Compare les hashes, télécharge uniquement les fichiers modifiés
- Transparent pour le joueur

### Lien packwiz-installer-bootstrap à utiliser
```
-javaagent:packwiz-installer-bootstrap.jar=https://0sacha.github.io/Nouvelle-Terre-SMP---Pack/pack.toml
```
(URL à ajuster selon le nom exact du repo GitHub)

## Notes importantes
- Le mod `nouvelle-terre-bridge` n'est pas sur Modrinth → il faudra le référencer en tant que mod "url" dans packwiz (depuis GitHub Releases)
- Cadmus est disponible sur Modrinth → référence directe possible
- Fabric API est sur Modrinth → référence directe possible
- Penser à inclure un `options.txt` ou config de base pour orienter les joueurs (résolution, etc.) — optionnel
