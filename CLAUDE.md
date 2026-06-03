# Nouvelle Terre — Mod Fabric 1.20.1

Mod de bridge entre un serveur Minecraft SMP RP et un bot Discord (Railway).
Lit ce fichier automatiquement pour avoir le contexte complet avant de coder.

## Repos
- Mod : `https://github.com/0Sacha/Nouvelle-Terre-SMP---MOD.git`
- Bot : `https://github.com/0Sacha/Nouvelle-Terre-SMP---Discord-BOT.git`

## Infrastructure
- Serveur Minecraft : Minestrator — IP `91.197.6.86`, port `24314`
- RCON port : `40539` (pas le 25575 par défaut)
- Bot Discord : Railway (Node.js), user `Nouvelle Terre#9576`
- Guild ID : `1508123190797406432`

## Build
```powershell
.\gradlew.bat build          # Windows
./gradlew build              # Linux/Mac
# JAR → build/libs/nouvelle-terre-bridge-1.0.0.jar
# Nécessite Java 17
```
GitHub Action crée une Release automatique à chaque push sur `main`.

## Architecture économie
- Source de vérité : `shards.json` sur le serveur (`LocalEconomy.java`)
- Toutes les opérations sont instantanées (pas d'HTTP pour le gameplay)
- Après chaque op, événement async vers le bot pour sync DB Discord
- ECONOMY_SALARY = notification only côté bot (ne pas appeler db.addShards, déjà fait via ECONOMY_REWARD)

## Architecture marché
- Annonces : `marche.json` sur le serveur (`MarketManager.java`)
- MARKET_SYNC envoyé au bot 3s après SERVER_START et à chaque reconnexion
- Achat au meilleur prix automatique, peut fractionner sur plusieurs vendeurs
- `FrenchItemNames.toDisplay()` strip n'importe quel namespace (pas seulement minecraft:)

## Commandes in-game actuelles
| Commande | Description |
|---|---|
| `/economie bourse` | Solde du joueur |
| `/economie virer <joueur> <montant>` | Envoyer des shards |
| `/economie admin give/take/check <joueur>` | Admin (op 2) |
| `/marche` | Accueil : liste des vendeurs (cliquables) |
| `/marche <joueur>` | Boutique d'un vendeur |
| `/marche vendre <qte> <prix>` | Vendre item en main |
| `/marche acheter <qte> <item>` | Acheter au meilleur prix |
| `/marche annonces` | Ses propres annonces |
| `/marche retirer <id>` | Retirer une annonce |
| `/discord` | Lier compte Minecraft ↔ Discord |
| `/conflit <cible> <raison>` | Déclarer un conflit RP |
| `/evenement <message>` | Narration (op only) |

## Structure des fichiers Java
```
commands/
  EconomieCommand.java     → /economie (bourse, virer, admin) + constantes SEP_* + fmt()
  MarcheCommand.java       → /marche (toutes sous-commandes)
  LierCommand.java         → /discord
  ConflitCommand.java      → /conflit
  EventNarratifCommand.java → /evenement

economy/
  LocalEconomy.java        → Singleton shards.json, estConnu(), addShards/removeShards/transfer
  KillRewards.java         → Récompenses kill mob
  PlaytimeTracker.java     → Récompenses 30min + salaire horaire

events/
  PlayerEvents.java        → JOIN / LEAVE / première connexion
  ServerEvents.java        → SERVER_START / SERVER_STOP / MARKET_SYNC
  TerritoryEvents.java     → Cadmus stub (non implémenté)

http/
  EventDispatcher.java     → HTTP async vers bot, file d'attente offline
  EventQueue.java          → File persistante JSON

mixin/
  LivingEntityMixin.java   → Morts joueurs → PLAYER_DEATH

shop/
  MarketManager.java       → Singleton marche.json, CRUD annonces
  MarketListing.java       → POJO annonce
  FrenchItemNames.java     → Dico FR↔MC, toDisplay() strip namespace
```

## Dead code — NE PAS RECRÉER
- `VenteCommand`, `AchatCommand` — remplacées par MarcheCommand
- `EconomyManager` — remplacé par LocalEconomy
- `EconomyEvents` — méthodes mortes
- `VenteScreenHandler/Factory` — TYPE = null, jamais enregistrée
- `CadmusIntegration` — stub vide

## UI — Constantes visuelles (EconomieCommand.java)
```java
EconomieCommand.SEP_GOLD    // séparateur or
EconomieCommand.SEP_GREEN   // séparateur vert (succès)
EconomieCommand.SEP_RED     // séparateur rouge (erreur)
EconomieCommand.SEP_YELLOW  // séparateur jaune (warning)
EconomieCommand.SEP_DARK    // séparateur gris (admin)
EconomieCommand.fmt(int)    // formatte un nombre avec espaces (1 250)
```
Style général : blocs visuels avec `▬` en couleur, `§8»` comme séparateur label/valeur,
éléments cliquables via `MutableText` + `ClickEvent` + `HoverEvent`.

## Prochain chantier : GUI HDV (/hdv)
Plan validé — **ne pas commencer sans relire ceci** :
- Commande `/hdv` ouvre un **coffre virtuel 6 rangées** (GenericContainerScreenHandler)
- **Mixin** sur `GenericContainerScreenHandler.onSlotClick` pour intercepter les clics côté serveur
- État GUI par joueur stocké dans une `Map<UUID, HdvState>` (quelle page, quelle catégorie)
- **Navigation** : Catégories → Items filtrés → Ma Boutique
- **Clics** : G = achète 1 unité / D = achète une pile / Shift = achète tout le stock
- **Vente via GUI** : bouton [+Vendre] → slot dédié → joueur tape `/hdv vendre <qté> <prix>`
- **Catégories** auto par namespace/ID Minecraft, items de mods → "Divers"
- Aucune texture custom nécessaire (vanilla chest UI)
