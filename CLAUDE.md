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
Le mod tourne sur le **client ET le serveur** (`environment: "*"`) — les joueurs doivent installer le JAR Fabric côté client pour le GUI HDV/Bank.

## Convention de version
- Format : `0.x.y-beta` (dans `gradle.properties` → `mod_version`)
- **Incrémenter la version avant chaque rebuild/push.**
- À chaque rebuild : mettre à jour `mod_version` dans `gradle.properties`, puis `git commit` + `git push`

---

## Architecture économie
- Source de vérité : `shards.json` sur le serveur (`LocalEconomy.java`)
- Toutes les opérations sont instantanées (pas d'HTTP pour le gameplay)
- Après chaque op, événement async vers le bot pour sync DB Discord
- `ECONOMY_SALARY` = notification only côté bot (ne pas appeler `db.addShards`, déjà fait via `ECONOMY_REWARD`)

## Architecture marché
- Annonces : `marche.json` sur le serveur (`MarketManager.java`)
- `MARKET_SYNC` envoyé au bot 3s après `SERVER_START` et à chaque reconnexion
- Achat au meilleur prix automatique, peut fractionner sur plusieurs vendeurs
- `FrenchItemNames.toDisplay()` strip n'importe quel namespace (pas seulement `minecraft:`)

## Architecture crédits
- Crédits : `nouvelle-terre-credits.json` sur le serveur (`LoanManager.java`)
- Création : le prêteur définit montant, durée (jours) et pénalité de base (◆/j)
- Au déclenchement, le montant est **transféré automatiquement** du prêteur à l'emprunteur
- Pénalités automatiques : vérifiées toutes les minutes (1200 ticks), appliquées chaque jour de retard
- Pénalité jour N = `penaltyBase + (N-1) * 5` ◆ (augmente de 5 ◆/j par défaut)
- `LocalEconomy.forceDeduct()` permet de passer en solde négatif pour les pénalités
- Remboursement : l'emprunteur renvoie le principal au prêteur via `/bank`
- Pardon : le prêteur peut annuler un crédit sans remboursement

---

## Commandes in-game
| Commande | Description |
|---|---|
| `/economie bourse` | Solde du joueur (redondant avec le HUD, conservé pour admin/debug) |
| `/economie admin give/take/check <joueur>` | Admin (op 2) |
| `/hdv` | Ouvre le GUI Marché (screen client Fabric) |
| `/bank` | Ouvre le GUI Banque (screen client Fabric) |
| `/discord` | Lier compte Minecraft ↔ Discord |
| `/conflit <cible> <raison>` | Déclarer un conflit RP |
| `/evenement <message>` | Narration (op only) |

> Toutes les opérations marché (vendre, acheter, retirer) se font **uniquement via `/hdv`**.
> Virements, crédits et historique se gèrent via `/bank`.

---

## Structure des fichiers Java
```
NouvelleTerreBridge.java       → Point d'entrée serveur : init config, events, commands, networking
NouvelleTerreBridgeClient.java → Point d'entrée client : récepteurs packets, init HUD
ModConfig.java                 → Config serveur (config/nouvelle-terre-bridge.json)
                                 Champs : botUrl, sharedSecret, activerEvenementServeur/Joueur, delaiVideFileAttente

commands/
  EconomieCommand.java     → /economie bourse + admin give/take/check + constantes SEP_* + fmt()
  HdvCommand.java          → /hdv : envoie HDV_OPEN au client via ServerPlayNetworking
  BankCommand.java         → /bank : envoie BANK_OPEN au client
  LierCommand.java         → /discord — liaison compte Minecraft ↔ Discord
  ConflitCommand.java      → /conflit — déclaration conflit RP
  EventNarratifCommand.java → /evenement — narration (op only)

economy/
  LocalEconomy.java        → Singleton shards.json
                             API : getBalance/addShards/removeShards/forceDeduct/transfer/estConnu/getSoldesKeys
  TransactionLog.java      → In-memory 50 dernières transactions/joueur (non persisté, reset au restart)
                             Types : BUY/SELL/TRANSFER_IN/TRANSFER_OUT/REWARD/LOAN_OUT/LOAN_IN/LOAN_REPAY_OUT/LOAN_REPAY_IN/LOAN_PENALTY
  KillRewards.java         → Récompenses ◆ par kill mob (map Class → shards)
  PlaytimeTracker.java     → Récompense +5 ◆ / 30 min de jeu + getTicksUntilReward
  RecurringTransfer.java   → POJO virement récurrent (id, from, to, amount, intervalTicks, ticksSince)
  RecurringTransferManager.java → Singleton nouvelle-terre-virements.json, tick-based
  Loan.java                → POJO crédit (id, lender, borrower, principal, dueTimestamp, penaltyBase,
                             penaltyIncrease, daysOverdue, totalPenalty, repaid, lastPenaltyMs)
  LoanManager.java         → Singleton nouvelle-terre-credits.json, check pénalités toutes les 1200 ticks

events/
  PlayerEvents.java        → JOIN (premier/retour) / LEAVE — dispatch bot + sendBalanceToPlayer à la connexion
  ServerEvents.java        → SERVER_START / SERVER_STOP / MARKET_SYNC 3s après démarrage

http/
  EventDispatcher.java     → HTTP async vers bot Railway, file d'attente offline
  EventQueue.java          → Persistance JSON de la file d'attente

mixin/
  LivingEntityMixin.java   → Intercepte les morts joueurs → event PLAYER_DEATH

network/
  HdvNetworking.java       → Canaux : HDV_OPEN / HDV_ACTION / HDV_RESULT / NT_VERSION / NT_BALANCE
                             Actions : ACTION_BUY(0) / ACTION_SELL(1) / ACTION_WITHDRAW(2)
  BankNetworking.java      → Canaux : BANK_OPEN / BANK_ACTION / BANK_RESULT / BANK_REQUEST
                             Actions : LOAN_CREATE(0) / LOAN_REPAY(1) / LOAN_FORGIVE(2) /
                                       TRANSFER(3) / RECURRING_CREATE(4) / RECURRING_CANCEL(5)

client/                    ← @Environment(CLIENT) uniquement
  HdvScreen.java           → Screen marché : 4 onglets (Marché / Vendre / Mon Shop / Boutiques)
                             Chip solde haut-droit → BANK_REQUEST → ouvre BankScreen
  BankScreen.java          → Screen banque : 5 onglets (Compte / Economie / Classement / Credits / Virements)
  BalanceHudOverlay.java   → HUD solde (coin haut-droit) via HudRenderCallback
                             cachedBalance mis à jour par NT_BALANCE / HDV_OPEN / HDV_RESULT / BANK_RESULT
  ClientConfig.java        → Config client-only (config/nouvelle-terre-client.json) — champ : hudEnabled
  NouvelleSettingsScreen.java → Écran paramètres : toggle HUD solde
  ModMenuIntegration.java  → Hook ModMenu optionnel (modCompileOnly)

market/
  MarketManager.java       → Singleton marche.json — CRUD annonces
  MarketListing.java       → POJO annonce (id, seller, item, quantity, pricePerUnit)
  MarketActions.java       → Logique métier : buy / sellByItemId / withdraw
  FrenchItemNames.java     → Dictionnaire FR↔MC + toDisplay() (strip namespace)
```

---

## Format des paquets réseau

### HDV (marché)
```
HDV_OPEN  : int balance | listings[]
HDV_RESULT: bool ok | string msg | int balance | listings[]
NT_BALANCE: int balance   — sync solde hors HDV (join, kill, playtime, virement récurrent)
listings[]: int count → (int id, string seller, string itemId, int qty, int price) × count
```

### Bank
```
BANK_OPEN  : int balance | int ticksReward | txs[] | int totalShards | int playerCount
             | leaderboard[] | loansAsLender[] | loansAsBorrower[] | known[] | recurring[]
BANK_RESULT: bool ok | string msg | [même contenu que BANK_OPEN]
txs[]         : int count → (int type, string label, int amount, long timestamp) × count
leaderboard[] : int count → (string name, int balance) × count
loansAs*[]    : int count → (int id, string other, int principal, long dueMs,
                             int daysOverdue, int totalPenalty, int nextPenalty, bool repaid) × count
known[]       : int count → string × count
recurring[]   : int count → (int id, string to, int amount, int intervalTicks, int ticksUntilNext) × count
```

---

## GUI HDV — décisions techniques

- Screen Fabric pur — pas de `ScreenHandler`, pas de slots vanilla
- Items rendus en 2× (32×32 px) via `drawItemScaled()` — transform matricielle sur `ctx.getMatrices()`
- La vente lit l'inventaire côté client (`client.player.getInventory().main`) — le serveur revalide
- Sidebar catégories : icône item Minecraft + compteur d'annonces par catégorie (`CAT_ICONS` map)
- Tri : enum `SortMode` (PRICE_ASC / PRICE_DESC / NAME) cyclé par le bouton "⇅"
- Scrollbar visuelle 4 px — thumb proportionnel au ratio visRows/totalRows
- Toast bottom-right avec accent coloré sur la bordure gauche (vert succès, rouge erreur)
- **Chip solde** haut-droit : cliquable → envoie `BANK_REQUEST` → ouvre `BankScreen`
- **Modal achat z-order** : `renderBuyModal()` dans `ctx.getMatrices().push() / translate(0,0,300) / pop()` — sinon texte des cards passe devant (batching Minecraft)

## GUI Bank — décisions techniques

- **Onglet Virements** : 2 cards (`cardW = (pw - GAP) / 2`), `renderInfoCard()` partagé
- **Dropdowns** : rendu dans `render()` après le tab content, overlay `0xAA000000` + scissor + scroll. Champs montant cachés via `setY(-200)` quand dropdown ouvert
- **Positions UI dans render()** : `trfDropX/Y/W`, `recurDropX/Y/W`, `trfSendBtnY`, `recurCreateBtnY`, `recurCancelBtnY[]` — relus dans `mouseClicked()`
- Pénalité check : `while` dans `LoanManager.tick()` rattrape plusieurs jours si serveur éteint
- `lastPenaltyMs` initialisé à `dueTimestamp` → premier jour de retard = J+1 après échéance
- Solde peut passer négatif via `forceDeduct()` uniquement pour les pénalités crédit
- `buildCasingMap` inclut les vendeurs HDV pour la liste joueurs connus dans dropdowns

## HUD Solde — décisions techniques

- `BalanceHudOverlay.cachedBalance` statique, initialisé à `-1` (affiche `? ◆`)
- `NouvelleTerreBridge.sendBalanceToPlayer(player)` appelé : connexion JOIN, kill reward, playtime reward, virement récurrent (sender + destinataire si connectés)
- Caché quand `HdvScreen` est ouvert (le solde est visible dans la chip du HDV)
- `ClientConfig` chargé dans `onInitializeClient()`, persisté dans `config/nouvelle-terre-client.json`
- ModMenu = `modCompileOnly "com.terraformersmc:modmenu:7.2.2"` — entrypoint `modmenu` dans `fabric.mod.json`

## Couleurs communes (HdvScreen / BankScreen)
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

## UI — Constantes EconomieCommand.java
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

---

## Dead code supprimé — NE PAS RECRÉER
- `VenteCommand`, `AchatCommand`, `MarcheCommand` → remplacées par `HdvScreen` + `MarketActions`
- `EconomyManager` → remplacé par `LocalEconomy`
- `shop/` → renommé en `market/`
- `TerritoryEvents` → stub Cadmus non implémenté, supprimé
- `HdvGui`, `HdvState`, `HdvScreenHandler`, `HdvSearchHandler`, `HdvSellPriceHandler` → ancien GUI coffre vanilla
- `CadmusIntegration`, `HdvCategory`, `EconomyEvents`, `VenteScreenHandler/Factory` → supprimés
- `ResourcePackManager` → resource pack supprimé (plus nécessaire avec screen client)
- `activerEvenementEconomie`, `activerEvenementTerritoire` → flags supprimés de `ModConfig`
