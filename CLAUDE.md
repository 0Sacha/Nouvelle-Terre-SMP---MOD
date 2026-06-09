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
Le mod tourne sur le **client ET le serveur** (`environment: "*"`) — les joueurs doivent installer le JAR Fabric côté client pour le GUI HDV.

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
| `/hdv` | Ouvre le GUI HDV custom (screen client Fabric) |
| `/discord` | Lier compte Minecraft ↔ Discord |
| `/conflit <cible> <raison>` | Déclarer un conflit RP |
| `/evenement <message>` | Narration (op only) |

> Toutes les opérations marché (vendre, acheter, retirer) se font **uniquement via `/hdv`** — aucune commande chat `/marche`.

## Structure des fichiers Java
```
commands/
  EconomieCommand.java     → /economie (bourse, virer, admin) + constantes SEP_* + fmt()
  HdvCommand.java          → /hdv : envoie HDV_OPEN au client via ServerPlayNetworking
  LierCommand.java         → /discord
  ConflitCommand.java      → /conflit
  EventNarratifCommand.java → /evenement

economy/
  LocalEconomy.java        → Singleton shards.json, estConnu(), addShards/removeShards/transfer
  KillRewards.java         → Récompenses kill mob
  PlaytimeTracker.java     → Récompenses 30min + salaire horaire

events/
  PlayerEvents.java        → JOIN / LEAVE / première connexion + envoi resource pack
  ServerEvents.java        → SERVER_START / SERVER_STOP / MARKET_SYNC + init ResourcePackManager
  TerritoryEvents.java     → Cadmus stub (non implémenté)

http/
  EventDispatcher.java     → HTTP async vers bot, file d'attente offline
  EventQueue.java          → File persistante JSON

mixin/
  LivingEntityMixin.java   → Morts joueurs → PLAYER_DEATH

network/
  HdvNetworking.java       → Identifiants canaux HDV_OPEN / HDV_ACTION / HDV_RESULT + constantes ACTION_*

client/                    ← Code client uniquement (@Environment(CLIENT))
  HdvScreen.java           → Screen Fabric full-screen : 4 onglets + modal achat + toast

resourcepack/
  ResourcePackManager.java → Génère ZIP dark-theme, calcule hash SHA-1 (télécharge depuis URL directe)

shop/
  MarketManager.java       → Singleton marche.json, CRUD annonces
  MarketListing.java       → POJO annonce
  MarketActions.java       → Logique métier buy / sell / sellByItemId / withdraw
  FrenchItemNames.java     → Dico FR↔MC, toDisplay() strip namespace
  HdvGui.java              → ⚠️ DEAD CODE — ancien GUI coffre, plus appelé
  HdvState.java            → ⚠️ DEAD CODE — état ancien GUI
  HdvScreenHandler.java    → ⚠️ DEAD CODE — sous-classe GenericContainerScreenHandler
  HdvSearchHandler.java    → ⚠️ DEAD CODE — enclume recherche
  HdvSellPriceHandler.java → ⚠️ DEAD CODE — enclume saisie prix
```

## Dead code — NE PAS RECRÉER
- `VenteCommand`, `AchatCommand`, `MarcheCommand` — remplacées par HdvScreen + MarketActions
- `EconomyManager` — remplacé par LocalEconomy
- `EconomyEvents` — méthodes mortes
- `VenteScreenHandler/Factory` — TYPE = null, jamais enregistrée
- `CadmusIntegration` — stub vide
- `HdvCategory` — supprimé
- `HdvGui`, `HdvState`, `HdvScreenHandler`, `HdvSearchHandler`, `HdvSellPriceHandler` — ancien système coffre vanilla, remplacé par `HdvScreen`

## GUI HDV — architecture client-serveur

### Flux d'ouverture
1. Joueur tape `/hdv`
2. `HdvCommand` → `ServerPlayNetworking.send(player, HDV_OPEN, buf)` avec balance + toutes les annonces
3. `NouvelleTerreBridgeClient` reçoit → `client.setScreen(new HdvScreen(...))`

### Flux d'action (achat / vente / retrait)
1. `HdvScreen` → `ClientPlayNetworking.send(HDV_ACTION, buf)` avec type + données
2. `NouvelleTerreBridge.registerHdvNetworking()` reçoit → appelle `MarketActions.*`
3. Réponse → `ServerPlayNetworking.send(HDV_RESULT, buf)` avec ok + message + nouvelles annonces
4. `NouvelleTerreBridgeClient` reçoit → `screen.handleResult(...)` met à jour l'UI

### HdvScreen — onglets
| Onglet | Description |
|---|---|
| 🏪 Marché | Grille 5 col filtrée par catégorie + recherche + tri Prix↑/↓/Nom + scrollbar |
| 💰 Vendre | Inventaire joueur lu client-side → formulaire qté/prix → `sellByItemId()` serveur |
| 🛒 Mon Shop | Mes annonces avec bouton Retirer |
| 👥 Boutiques | Liste vendeurs → détail boutique → achat |

### Couleurs (HdvScreen)
```java
C_BG    = 0xFF1e1f22   // fond principal
C_PANEL = 0xFF2b2d31   // cartes / panneaux
C_HOVER = 0xFF313338   // hover
C_GOLD  = 0xFFf0b232   // accent or (prix, onglet actif, bordure hover)
C_SEP   = 0xFF3a3c40   // séparateurs, boutons secondaires
C_RED   = 0xFFd4183d   // erreur / retrait
C_GREEN = 0xFF23a55a   // succès (toast)
```

### Décisions techniques à retenir
- Le GUI est un `Screen` Fabric pur — pas de `ScreenHandler`, pas de slots vanilla
- Items rendus en 2× (32×32 px) via `drawItemScaled()` qui pousse une transform matricielle sur `ctx.getMatrices()` avant `ctx.drawItem(stack, 0, 0)`
- La vente lit l'inventaire côté client (`client.player.getInventory().main`) — le serveur revalide
- `MarketActions.sell()` requiert l'item en main ; `MarketActions.sellByItemId()` cherche dans l'inventaire
- `@Environment(EnvType.CLIENT)` sur `HdvScreen` et `NouvelleTerreBridgeClient`
- Sidebar catégories : icône item Minecraft + compteur d'annonces par catégorie (CAT_ICONS map)
- Tri : enum `SortMode` (PRICE_ASC / PRICE_DESC / NAME) cyclé par le bouton "⇅" à droite de la recherche
- Scrollbar visuelle (4 px) à droite de la grille — thumb proportionnel au ratio visRows/totalRows
- Toast bottom-right avec accent coloré sur la bordure gauche (vert = succès, rouge = erreur)
- Modal achat : bouton MAX calcule `min(stock, balance / prixUnit)`

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
> Le resource pack n'est plus nécessaire pour le GUI HDV (remplacé par le screen client), mais reste envoyé pour d'autres usages potentiels.

### Envoi aux joueurs
- `ResourcePackManager.init()` au démarrage : en mode URL directe, **télécharge le fichier** pour calculer le vrai SHA-1 (le ZIP Python de la CI ≠ ZIP Java en mémoire)
- `PlayerEvents` envoie `ResourcePackSendS2CPacket(url, hash, required, null)` à chaque connexion
- Config dans `nouvelle-terre-bridge.json` :
  - `resourcePackUrl` — URL fixe GitHub Releases latest : `https://github.com/0Sacha/Nouvelle-Terre-SMP---MOD/releases/latest/download/nouvelle-terre-hdv.zip`
  - `resourcePackHost` / `resourcePackPort` — fallback serveur HTTP intégré (port 25566 souvent bloqué)
  - `resourcePackRequired` — si `true`, le joueur est déconnecté s'il refuse

### Workflow resource pack (GitHub Releases)
1. Push sur `main` → GitHub Actions génère `nouvelle-terre-hdv.zip` via `.github/scripts/gen_resourcepack.py`
2. Le ZIP + le JAR sont attachés à la Release GitHub (tag `v{version}-{sha}`)
3. L'URL `releases/latest/download/nouvelle-terre-hdv.zip` pointe toujours vers la dernière release → **ne jamais changer le config**

### Décision technique
- Port 25566 bloqué par Minestrator → passage au mode URL directe (GitHub Releases)
- Encodeur PNG pur Java (pas d'AWT) — fonctionne sur serveur headless Linux
- `slot.png` retiré du pack — chemin dans l'atlas 1.20.1 différent, faisait rejeter tout le pack
- Hash calculé depuis le fichier téléchargé (pas le ZIP Java) pour éviter mismatch client
