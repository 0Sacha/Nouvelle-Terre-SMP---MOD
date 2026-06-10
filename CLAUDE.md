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
# JAR → build/libs/nouvelle-terre-bridge-{mod_version}.jar
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
  LocalEconomy.java        → Singleton shards.json, estConnu(), addShards/removeShards/transfer, getSoldesKeys()
  TransactionLog.java      → Singleton in-memory, 50 dernières transactions par joueur (TYPE_BUY/SELL/TRANSFER_IN/TRANSFER_OUT/REWARD)
  KillRewards.java         → Récompenses kill mob
  PlaytimeTracker.java     → Récompenses 30min (auto) uniquement + getTicksUntilReward (salaire supprimé)
  RecurringTransfer.java   → POJO virement récurrent (id, from, to, amount, intervalTicks, ticksSince)
  RecurringTransferManager.java → Singleton nouvelle-terre-virements.json, tick-based, add/cancel/getForPlayer

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
  HdvNetworking.java       → Identifiants canaux HDV_OPEN / HDV_ACTION / HDV_RESULT + constantes ACTION_* (0-5 : BUY/SELL/WITHDRAW/TRANSFER/RECURRING_CREATE/RECURRING_CANCEL)

client/                    ← Code client uniquement (@Environment(CLIENT))
  HdvScreen.java           → Screen Fabric fenêtré semi-transparent : 4 onglets + onglet Profil (chip cliquable) + modal achat + toast

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

### Flux d'action (achat / vente / retrait / virement)
1. `HdvScreen` → `ClientPlayNetworking.send(HDV_ACTION, buf)` avec type + données
2. `NouvelleTerreBridge.registerHdvNetworking()` reçoit → appelle `MarketActions.*` ou `LocalEconomy.transfer()`
3. Réponse → `ServerPlayNetworking.send(HDV_RESULT, buf)` avec ok + message + nouvelles annonces + données profil
4. `NouvelleTerreBridgeClient` reçoit → `screen.handleResult(...)` met à jour l'UI

### Flux HDV_OPEN / HDV_RESULT — format paquet
```
HDV_OPEN  : int balance | listings[] | int ticksReward | transactions[] | known[] | recurring[]
HDV_RESULT: bool ok | string msg | int balance | listings[] | int ticksReward | transactions[] | known[] | recurring[]
listings[]    : int count → (int id, string seller, string itemId, int qty, int price) × count
transactions[]: int count → (int type, string label, int amount, long timestamp) × count
known[]       : int count → string[] — joueurs connus (casing correct, triés alphabétiquement)
recurring[]   : int count → (int id, string to, int amount, int intervalTicks, int ticksUntilNext) × count
```

### HdvScreen — onglets
| Onglet | Description |
|---|---|
| 🏪 Marché | Grille 5 col filtrée par catégorie + recherche + tri Prix↑/↓/Nom + scrollbar |
| 💰 Vendre | Inventaire joueur lu client-side → formulaire qté/prix → `sellByItemId()` serveur |
| 🛒 Mon Shop | Mes annonces avec bouton Retirer (strip bas = rouge au hover) |
| 👥 Boutiques | Liste vendeurs → détail boutique → achat |
| ★ Profil | Accessible via chip solde (haut droite). Header strip (nom + solde) + 3 cards : Récompense (barre prog auto) \| Virement ponctuel (dropdown + montant + envoyer) \| Virement récurrent (dropdown + montant + sélecteur < 1h/6h/12h/24h > + créer). Liste virements récurrents actifs avec bouton Annuler. Transactions au bas. |

### Couleurs (HdvScreen)
```java
C_BG      = 0xFF14161A   // fond principal
C_PANEL   = 0xFF1B1D22   // cartes / panneaux
C_SURFACE = 0xFF21242C   // formulaires / modals
C_HOVER   = 0xFF282B34   // hover
C_STRIP   = 0xFF1E2128   // bandes prix bas de carte
C_BORDER  = 0xFF2A2D38   // bordures
C_GOLD    = 0xFFE8A838   // accent or (prix, onglet actif, bordure hover)
C_RED     = 0xFFBF2040   // erreur / retrait
C_GREEN   = 0xFF2EAD6B   // succès
C_WHITE   = 0xFFFFFFFF
C_MID     = 0xFF9096A3   // texte secondaire
C_DIM     = 0xFF565C6A   // labels, placeholders
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
- Onglet actif = texte sans shadow (`drawText(..., false)`) sur fond or pour éviter l'effet "gras"
- Mon Shop : strip bas (24px) bascule entre prix et "Retirer" selon hover — même draw call, pas d'overlay séparé (évite le batching text-over-fill de Minecraft)
- TransactionLog : in-memory uniquement (50 entries / joueur), pas persisté sur disque — reset au restart serveur
- Countdown reward dans l'onglet Profil : calculé en soustrayant (now - screenOpenTime) / 50ms des ticks initiaux reçus du serveur — live sans round-trip réseau
- 💎 → ◆ (U+25C6) partout : les emoji BMP-ext sont hors BMP et Minecraft ne les rend pas
- **Chip solde** (haut droite) : cliquable → ouvre onglet Profil. Texte sans shadow ni bold (`drawText(..., false)`)
- **Profil — layout** : header strip pleine largeur (nom + solde) + 3 cards égales (`cardW = (pw - GAP*2) / 3`) : Récompense | Virement ponctuel | Virement récurrent. `renderInfoCard()` dessine fond + bordure + accent gauche. Liste virements récurrents actifs. Transactions scrollables en bas.
- **Barre de progression** card Récompense : `1f - ticks / totalTicks` → 0 = vide, 1 = plein (prêt).
- **Dropdown joueurs** (cards 2 et 3) : pas de `TextFieldWidget`, dropdown custom rendu APRÈS `super.render()`. Un seul ouvert à la fois. Quand ouvert : overlay `0xAA000000` + bordure or + ombre. Les champs montant sont cachés via `setY(-200)` quand un dropdown est ouvert (sinon ils passent à travers l'overlay via `super.render()`).
- **Virement récurrent** : `RecurringTransferManager` tick-based persisté JSON. Intervalles : 1h/6h/12h/24h (constantes INTERVALS[]). Sélecteur `< label >` dans card 3. Liste actifs avec countdown + bouton Annuler (rouge au hover).
- **Positions UI calculées dans render()** : `profileDropX/Y/W`, `recurDropX/Y/W`, `profileTransferBtnY`, `recurCreateBtnY`, `recurIntervalBtnY`, `recurCancelBtnY[]` — champs d'instance relus dans les click handlers.

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
