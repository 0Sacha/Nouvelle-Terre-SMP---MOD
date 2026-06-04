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
| `/hdv` | Ouvre le GUI HDV (coffre virtuel 6 rangées) |
| `/discord` | Lier compte Minecraft ↔ Discord |
| `/conflit <cible> <raison>` | Déclarer un conflit RP |
| `/evenement <message>` | Narration (op only) |

> Toutes les opérations marché (vendre, acheter, retirer) se font **uniquement via `/hdv`** — aucune commande chat `/marche`.

## Structure des fichiers Java
```
commands/
  EconomieCommand.java     → /economie (bourse, virer, admin) + constantes SEP_* + fmt()
  MarcheCommand.java       → /marche (vendre, acheter, annonces, retirer) — PAS de <joueur>
  HdvCommand.java          → /hdv (ouvre le GUI)
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
  MarketActions.java       → Logique métier buy/sell/withdraw — appelé uniquement par HdvGui
  FrenchItemNames.java     → Dico FR↔MC, toDisplay() strip namespace
  HdvGui.java              → GUI coffre virtuel : open/buildPage/handleClick (4 modes)
  HdvState.java            → État par joueur (mode, page, vendeur, qté vente, searchQuery)
  HdvScreenHandler.java    → Subclasse GenericContainerScreenHandler, override onSlotClick/onClosed
  HdvSearchHandler.java    → Enclume détournée pour recherche texte
  HdvSellPriceHandler.java → Enclume détournée pour saisie du prix de vente
```

## Dead code — NE PAS RECRÉER
- `VenteCommand`, `AchatCommand`, `MarcheCommand` — remplacées par HdvGui + MarketActions
- `EconomyManager` — remplacé par LocalEconomy
- `EconomyEvents` — méthodes mortes
- `VenteScreenHandler/Factory` — TYPE = null, jamais enregistrée
- `CadmusIntegration` — stub vide
- `HdvCategory` — supprimé, catégories retirées de l'UI (première page = vendeurs)

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

## Resource pack HDV dark-theme

Généré et servi automatiquement par le mod, style Paladium.

### Fichiers générés à la volée (pur Java, aucune image externe)
| Fichier dans le pack | Rôle |
|---|---|
| `assets/minecraft/textures/gui/container/generic_54.png` | Fond sombre du coffre 6 rangées (#1a1b1e, barre titre #23272a) |

> PNG encodé en **RGBA (color type 6)** — Minecraft 1.20.1 crash si les textures GUI sont en RGB (type 2).
> `slot.png` retiré — son chemin dans l'atlas sprite 1.20.1 est différent ; le fichier inconnu faisait rejeter tout le pack.

### Envoi aux joueurs
- Au démarrage du serveur : génère le ZIP + le sauvegarde dans `<gameDir>/nouvelle-terre-hdv.zip`
- `PlayerEvents` envoie `ResourcePackSendS2CPacket(url, hash, required, null)` à chaque connexion
- Config dans `nouvelle-terre-bridge.json` :
  - `resourcePackUrl` — **URL directe** (recommandée, ex: GitHub Releases). Si renseigné, le serveur HTTP intégré ne démarre PAS.
  - `resourcePackHost` / `resourcePackPort` — fallback serveur HTTP intégré (port 25566 souvent bloqué par les hébergeurs)
  - `resourcePackRequired` — si `true`, le joueur est déconnecté s'il refuse

### Workflow resource pack (GitHub Releases)
1. Push sur `main` → GitHub Actions génère `nouvelle-terre-hdv.zip` via `.github/scripts/gen_resourcepack.py`
2. Le ZIP est attaché à la Release GitHub avec le SHA-1 dans la description
3. Configurer `resourcePackUrl` dans `nouvelle-terre-bridge.json` avec l'URL de la Release

### Décision technique
- Port 25566 bloqué par Minestrator → passage au mode URL directe (GitHub Releases)
- Encodeur PNG pur Java (pas d'AWT) — fonctionne sur serveur headless Linux sans config supplémentaire
- `slot.png` sprite retiré du pack — chemin dans l'atlas 1.20.1 différent, faisait rejeter tout le pack

## GUI HDV — implémenté et fonctionnel

### Architecture
- `HdvScreenHandler` sous-classe `GenericContainerScreenHandler`, override `onSlotClick` (bloque tout vanilla) et `onClosed` (nettoie le state)
- **PAS de Mixin pour les clics HDV** — le override direct est fiable et plus simple
- `HdvGui.STATES : Map<UUID, HdvState>` — un état par joueur connecté

### Modes de navigation (HdvState.Mode)
| Mode | Description |
|---|---|
| `VENDORS` | **Accueil** : grille de têtes de joueurs avec skin (NBT SkullOwner), pagination |
| `ITEMS` | Items achetables filtrés par vendeur ; propres annonces grisées |
| `MY_SHOP` | Ses propres annonces ; D = retirer ; bouton + Vendre → mode SELL |
| `SELL` | Sélection quantité (presets 1/4/8/16/32/64/tout) ; confirmer → enclume prix |

### Recherche (style Paladium)
- Bouton `🔍 Rechercher` au slot 6 du header de la vue ITEMS
- Ouvre `HdvSearchHandler` (enclume détournée) : `AnvilScreenHandler` avec `ScreenHandlerContext.EMPTY`
- Le joueur tape dans le field texte natif de l'enclume → clic sur l'output (slot 2) → `output.getName()` = texte tapé
- `HdvGui.openHdvWithSearch(player, query)` rouvre le HDV en mode ITEMS avec `state.searchQuery`
- Escape → `onClosed` (confirmed=false) → `server.execute(() → openHdv(player))` (sur le tick suivant, évite récursion)
- Filtre combinable avec vendeur : match sur nom FR, item ID, ou pseudo vendeur
- Filtre actif : bouton change en `§b🔍 "query"` — G=effacer, D=modifier

### Clics en mode ITEMS
- G (clic gauche) = achète 1 unité
- D (clic droit) = achète min(64, stock)
- Shift = achète tout le stock

### Vente via GUI
1. `Ma Boutique` → clic sur `+ Vendre` (item en main obligatoire)
2. Page SELL : aperçu item + sélecteurs quantité
3. Clic `✅ Confirmer` → ouvre `HdvSellPriceHandler` (enclume) → joueur tape le prix → `MarketActions.sell()`

### Décisions techniques à retenir
- `onSlotClick` dans la sous-classe bloque TOUTES les actions vanilla (PICKUP, QUICK_MOVE, SWAP, etc.)
- `SkullOwner` NBT suffit pour afficher les vraies têtes de joueurs sans dépendances
- Achat/retrait/vente délégués à `MarketActions` — aucune commande chat pour le marché
