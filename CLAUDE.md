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
Le mod tourne sur le **client ET le serveur** (`environment: "*"`) — les joueurs doivent installer le JAR Fabric côté client pour le GUI HDV/Bank/Registre.

## Convention de version
- Format : `0.x.y-beta` (dans `gradle.properties` → `mod_version`)
- **Incrémenter la version avant chaque rebuild/push.**
- Version actuelle : `0.2.36-beta` (GUI /production : liste scrollable + barres de progression, boutons admin op only)
- À chaque rebuild : mettre à jour `mod_version` dans `gradle.properties`, puis `git commit` + `git push`

---

## Architecture économie
- Source de vérité : `shards.json` sur le serveur (`LocalEconomy.java`)
- Toutes les opérations sont instantanées (pas d'HTTP pour le gameplay)
- Après chaque op, événement async vers le bot pour sync DB Discord
- `ECONOMY_SALARY` = notification only côté bot (ne pas appeler `db.addShards`, déjà fait via `ECONOMY_REWARD`)
- `PLAYER_JOIN` inclut `balance` → bot fait `UPDATE joueurs SET shards=? WHERE uuid=?` pour resync au login
- **Robinets (argent créé par le serveur via `addShards`)** : récompenses de quêtes SHARDS,
  quête communautaire (◆ par contributeur), bonus quotidien de connexion (+25 ◆/jour réel),
  kills de mobs (`KillRewards`), temps de jeu (+5 ◆/30 min), conversion des récompenses items
  non récupérées à minuit, 500 ◆ de départ
- **Puits (argent détruit/absorbé)** : achats au shop auto `$Serveur`, coûts d'acceptation de quêtes

## Architecture marché
- Annonces : `marche.json` sur le serveur (`MarketManager.java`)
- `MARKET_SYNC` envoyé au bot 3s après `SERVER_START` et à chaque reconnexion
- Achat au meilleur prix automatique, peut fractionner sur plusieurs vendeurs
- `FrenchItemNames.toDisplay()` strip n'importe quel namespace (pas seulement `minecraft:`)
- Catégories HDV : Tout, Blocs, Matériaux, Outils, Nourriture, Potions, **Médical**, Divers
- **Shop auto (`$Serveur`)** : seuils créés dynamiquement au premier contact avec un item
  (`ShopThresholds.getOrCreate()`, calculés par `Rarity` vanilla). **Stock illimité** :
  l'achat ne décrémente pas la quantité, pas de SALE_COMPLETED envoyé au bot.
  L'argent des achats va sur le compte `$Serveur` (préfixe `$` = compte système,
  **exclu** du classement, des stats économie, du total shards et des dropdowns joueurs)

## Architecture crédits
- Crédits + propositions : `nouvelle-terre-credits.json` sur le serveur (`LoanManager.java`, clés `loans` + `requests`)
- **Flux sur proposition** : le **prêteur** propose un crédit (emprunteur, montant, durée, pénalité ◆/j)
  via `/bank` → Crédits → "Proposer un credit". Aucun fonds transféré à ce stade.
- L'emprunteur est notifié en chat (s'il est en ligne) et voit la proposition dans `/bank` → Crédits
  ("ON VOUS PROPOSE UN CREDIT") avec boutons Accepter / Refuser
- **Accepter** (`ACTION_LOAN_ACCEPT`, côté emprunteur) : crédit créé + montant transféré prêteur → emprunteur
- **Refuser/Annuler** (`ACTION_LOAN_DECLINE`) : l'emprunteur refuse OU le prêteur annule sa proposition
- Une seule proposition en attente par paire emprunteur/prêteur
- Pénalités automatiques : vérifiées toutes les minutes (1200 ticks), appliquées chaque jour de retard
- Pénalité jour N = `penaltyBase + (N-1) * 5` ◆ (augmente de 5 ◆/j par défaut)
- `LocalEconomy.forceDeduct()` permet de passer en solde négatif pour les pénalités
- Remboursement : l'emprunteur renvoie le principal au prêteur via `/bank`
- Pardon ("Effacer la dette") : le prêteur peut annuler un crédit sans remboursement

## Architecture noms RP (personnages)
- Cache serveur : `NouvelleTerreBridge.nomsRP` — `ConcurrentHashMap<String, String>` uuid→nom_rp
- Peuplé à la connexion via `EventDispatcher.fetchNomRP(uuid, server, callback)`
- Endpoint bot : `GET {base}/joueur/{uuid}?secret=...` → `{ "nom_rp": "Jean Dupont" }`
- Si 404 ou pas de personnage confirmé : le cache reste vide, pseudo MC utilisé partout
- À la déconnexion : entrée supprimée du cache
- **Tab list** : **scoreboard team** côté serveur — `"nt_" + uuid[0..8]`, prefix=`"§fNomRP §8(§7"`, suffix=`"§8)"`.
  Minecraft affiche nativement `NomRP (pseudo)`. Créée après `fetchNomRP`, supprimée à la déconnexion.
  `PlayerListEntry.displayName` reste null → Minecraft utilise le team prefix/suffix. ✓
- **Nameplate** (au-dessus de la tête) : **NON FONCTIONNEL — abandonné**. Ne pas retenter :
  `AbstractClientPlayerEntityMixin` cible `Entity.getDisplayName()` avec garde instanceof + cache NT_NOM_RP,
  mais un mod client tiers rend son propre nameplate par-dessus.
- **Chat** : `ServerMessageEvents.ALLOW_CHAT_MESSAGE` — annule le message signé, rebroadcast
  `§8<§fNomRP§8> §fcontenu` comme message système
- **Registre** : `EventDispatcher.fetchPersonnages()` → `GET {base}/personnages?secret=...`
  → `[{ "nom_rp": "...", "pseudo_mc": "...", "en_ligne": bool/int/string }]`
  Tri : en ligne en premier (point vert), puis alphabétique

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
| `/quetes` | Ouvre le GUI Quêtes |
| `/quetes refresh` | Recharge quetes-templates.json (op 2) |
| `/quetes reset` | Réinitialise toute la progression (op 2) |
| `/registre` | Ouvre le GUI Registre des personnages (screen client Fabric) |
| `/production` | Ouvre le GUI Production naturelle (tous les joueurs, boutons admin si op 2) |
| `/production reset/info/recheck/reload` | Sous-commandes texte admin (op 2) |

> Toutes les opérations marché (vendre, acheter, retirer) se font **uniquement via `/hdv`**.
> Virements, crédits et historique se gèrent via `/bank`.

---

## Structure des fichiers Java
```
NouvelleTerreBridge.java       → Point d'entrée serveur : init config, events, commands, networking
                                 + nomsRP : ConcurrentHashMap<String,String> (cache uuid→nom_rp partagé)
NouvelleTerreBridgeClient.java → Point d'entrée client : récepteurs packets, init HUD
                                 + récepteur REGISTRE_OPEN → ouvre RegistreScreen
ModConfig.java                 → Config serveur (config/nouvelle-terre-bridge.json)
                                 Champs : botUrl, sharedSecret, activerEvenementServeur/Joueur, delaiVideFileAttente

commands/
  EconomieCommand.java     → /economie bourse + admin give/take/check + constantes SEP_* + fmt()
  HdvCommand.java          → /hdv : envoie HDV_OPEN au client via ServerPlayNetworking
  BankCommand.java         → /bank : envoie BANK_OPEN au client
  LierCommand.java         → /discord — liaison compte Minecraft ↔ Discord
  ConflitCommand.java      → /conflit — déclaration conflit RP
  EventNarratifCommand.java → /evenement — narration (op only)
  QuetesCommand.java       → /quetes (ouvre GUI via QUEST_OPEN), /quetes refresh, /quetes reset
  RegistreCommand.java     → /registre : appelle fetchPersonnages, envoie REGISTRE_OPEN au client
  ProductionCommand.java   → /production (ouvre GUI via PROD_OPEN, tous joueurs)
                             + reset/info/recheck/reload en texte (op 2)

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
  Quest.java               → POJO quête (id, type, target, quantity, rewardType/Shards/Item/Xp, tags, label, expiresAt)
  QuestGenerator.java      → Pool de ~70 templates (KILL/HARVEST/DELIVERY × FACILE/MOYEN/DIFFICILE/LÉGENDAIRE)
                             + generateDailies() (3 journalières : 1 par difficulté, expirent à minuit)
                             + generateCommunity() (pool dédié de 10 objectifs serveur)
                             + nextMidnightMs() (epoch du prochain minuit, heure locale serveur)
  QuestManager.java        → Singleton quetes.json (players + globalGroup + dailySolo + community + dailyDate)
                             API : load/reset, accept/claim/cancel, collectReward/cancelPending,
                             onMobKilled/onItemHarvested (avec MinecraftServer), tick(server)
                             - **Auto-claim** : quête KILL/HARVEST à 100 % → récompense immédiate
                               (SHARDS versés direct + msg chat ; ITEM → pendingRewards "À Réclamer")
                             - **Rollover journalier** : tick vérifie chaque minute si la date a changé (00h réel)
                               → deliverAllPending (items donnés si place, sinon convertis en shards créés,
                               valeur = prix shop auto × qté), retire les journalières expirées, régénère
                               dailySolo + community, broadcast serveur
                             - **Quête communautaire** : progression globale sans acceptation, contributors
                               map name→contribution, à l'objectif : +reward ◆ créés pour CHAQUE contributeur
  DailyBonusTracker.java   → Bonus quotidien +25 ◆ créés à la première connexion de chaque jour réel
                             Persistance nouvelle-terre-bonus.json (pseudo → date), hook dans PlayerEvents.JOIN

events/
  PlayerEvents.java        → JOIN / LEAVE — dispatch bot, nom RP, chat RP, balance sync
                             - PLAYER_JOIN inclut balance (resync shards bot)
                             - fetchNomRP → nomsRP cache + PlayerListS2CPacket UPDATE_DISPLAY_NAME
                             - ALLOW_CHAT_MESSAGE → cancel signé + rebroadcast <NomRP> msg
                             - PLAYER_LEAVE → retire du cache + message départ RP
  ServerEvents.java        → SERVER_START / SERVER_STOP / MARKET_SYNC 3s après démarrage

http/
  EventDispatcher.java     → HTTP async vers bot Railway, file d'attente offline
                             + fetchNomRP() : GET /joueur/{uuid}?secret=... → nom_rp
                             + fetchPersonnages() : GET /personnages?secret=... → liste personnages
                             Secret URL-encodé (URLEncoder.encode) pour éviter les chars spéciaux dans l'URI
  EventQueue.java          → Persistance JSON de la file d'attente

mixin/
  LivingEntityMixin.java           → Intercepte les morts joueurs → event PLAYER_DEATH
  InGameHudMixin.java              → @Inject InGameHud.render HEAD → reset debugHudActive = false
  DebugHudMixin.java               → @Inject DebugHud.render HEAD → set debugHudActive = true (détection F3)
  ServerPlayerEntityMixin.java     → @Inject getPlayerListName HEAD → retourne "§fNomRP §8(§7pseudo§8)"
                                     depuis NouvelleTerreBridge.nomsRP (tab list côté serveur)
  AbstractClientPlayerEntityMixin.java → @Inject getDisplayName HEAD (CLIENT) → lit PlayerListEntry.getDisplayName()
                                         pour que le nameplate au-dessus de la tête affiche le nom RP

network/
  HdvNetworking.java       → Canaux : HDV_OPEN / HDV_ACTION / HDV_RESULT / NT_VERSION / NT_BALANCE
                             Actions : ACTION_BUY(0) / ACTION_SELL(1) / ACTION_WITHDRAW(2)
  BankNetworking.java      → Canaux : BANK_OPEN / BANK_ACTION / BANK_RESULT / BANK_REQUEST
                             Actions : LOAN_REQUEST(0) / LOAN_REPAY(1) / LOAN_FORGIVE(2) /
                                       TRANSFER(3) / RECURRING_CREATE(4) / RECURRING_CANCEL(5) /
                                       LOAN_ACCEPT(6) / LOAN_DECLINE(7)
  QuestNetworking.java     → Canaux : QUEST_OPEN (S→C, ouvre GUI) / QUEST_ACTION (C→S) / QUEST_RESULT (S→C)
                             Actions : ACTION_ACCEPT(0) / ACTION_CLAIM(1)
  RegistreNetworking.java  → Canal : REGISTRE_OPEN (S→C, ouvre RegistreScreen)
  ProductionNetworking.java → Canaux : PROD_OPEN (S→C, ouvre GUI) / PROD_ACTION (C→S) / PROD_RESULT (S→C)
                             Actions (op only, revalidées serveur) : RESET(0) / RECHECK(1) / RELOAD(2)

client/                    ← @Environment(CLIENT) uniquement
  HdvScreen.java           → Screen marché : 4 onglets (Marché / Vendre / Mon Shop / Boutiques)
                             Chip solde haut-droit → BANK_REQUEST → ouvre BankScreen
                             Catégorie "Médical" : items cottonmod (coton, bandage, medkit, plantes...)
  BankScreen.java          → Screen banque : 5 onglets (Compte / Economie / Classement / Credits / Virements)
  QuetesScreen.java        → Screen quêtes : 2 onglets (Disponibles / Mes Quêtes), PW=420 PH=300,
                             cards avec barre de progression, boutons Accepter/Réclamer
  ProductionScreen.java    → Screen production : liste scrollable (icône + nom FR + barre + count/seuil + statut),
                             tri : en vente d'abord puis progression desc. Boutons admin (Recheck/Recharger/Reset)
                             rendus uniquement si isOp (revalidé serveur). PW_MAX=520 PH_MAX=420
  RegistreScreen.java      → Screen registre personnages : liste scrollable, PW_MAX=400 PH_MAX=300
                             record PersonnageData(String nomRp, String pseudoMc, boolean enLigne)
                             Tri : en ligne en premier (point vert), puis alphabétique
                             Row : point coloré + nomRp (blanc) + "— pseudoMc" (gris) + "● en ligne" si online
  BalanceHudOverlay.java   → Contient uniquement `cachedBalance` statique (int, init -1)
                             Mis à jour par NT_BALANCE / HDV_OPEN / HDV_RESULT. Plus de rendering ici.
  HudEditorScreen.java     → Éditeur HUD (touche H). Deux modes :
                               Mode PANEL : panneau centré en haut (PW=372px), grille 2 colonnes de cards.
                                 Chaque card : preview widget, toggle ACTIVÉ/DÉSACTIVÉ, bouton OPTIONS ⚙.
                                 Bouton "Placer les widgets" → bascule en mode LAYOUT.
                               Mode LAYOUT : panneau caché, widgets activés draggables avec bordure or.
                                 Bouton "Terminer" centré en haut. ESC → retour mode PANEL.
                             WIDGETS (List<HudWidget>) statique — initialisé dans NouvelleTerreBridgeClient.
                             Positions relatives (0.0-1.0), snap aux bords, sauvegarde sur close.
  ClientConfig.java        → Config client-only (config/nouvelle-terre-client.json)
                             Champs : discordRPCEnabled, hudEnabled/balanceX/Y, coordsEnabled/X/Y/ShowDecimals,
                             compassEnabled/X/Y/ShowDegrees (legacy), timeEnabled/X/Y/ShowIcon,
                             santeEnabled/X/Y, nourritureEnabled/X/Y, fpsEnabled/X/Y/ShowPing,
                             biomeEnabled/X/Y, notifEnabled/X/Y, armureEnabled/X/Y,
                             xpEnabled/X/Y, dimensionEnabled/X/Y, effetsEnabled/X/Y
  NouvelleSettingsScreen.java → Bouton "Éditeur HUD →" + toggle Discord RPC
  ModMenuIntegration.java  → Hook ModMenu optionnel (modCompileOnly)

client/hud/                ← Widgets HUD individuels
  HudWidget.java           → Classe abstraite : id, label, anchorX/Y, enabled, getPixelX/Y (clamped),
                             resetToDefault(), renderCheckbox() helper. loadFromConfig/saveToConfig abstraits.
                             isDragOnly() → false par défaut (true = widget de position pure, pas rendu dans le HUD).
  BalanceWidget.java       → Affiche cachedBalance + " ◆". Pas de paramètres.
  CoordsWidget.java        → "XYZ x / y / z". Paramètre : coordsShowDecimals.
  TimeWidget.java          → Heure Minecraft HH:MM. Paramètre : timeShowIcon (☀/☽).
  SanteWidget.java         → Santé du joueur "X / max ♥". Couleur : rouge < 25%, or < 60%, rouge vif sinon.
  NourritureWidget.java    → Niveau de faim "Faim X / 20". Couleur : rouge ≤4, or ≤10, vert sinon.
  FpsWidget.java           → FPS + ping optionnel. FPS mesuré via FpsWidget.onFrame() appelé dans HudRenderCallback.
                             Paramètre : fpsShowPing.
  BiomeWidget.java         → Biome courant (getKey().getPath(), premier caractère capitalisé).
  ArmureWidget.java        → "Arm. X / 20". Couleur : rouge ≤4, or ≤14, vert sinon.
  XpWidget.java            → "Niv. X" + barre de progression XP (4px, vert). mc.player.experienceLevel/experienceProgress.
  DimensionWidget.java     → Dimension courante : "Monde" (vert), "Nether" (rouge), "End" (violet).
  EffetsWidget.java        → Liste d'effets de potion actifs (max 5), chiffres romains + durée en secondes.
                             Hauteur variable : Math.max(14, count * 12 + 6). Vert=bénéfique, rouge=négatif.
  NotificationWidget.java  → Widget de position pour les toasts. isDragOnly()=true, render() vide.
                             Aperçu mock dans la card de l'éditeur. Position lue par NotificationHud.

market/
  MarketManager.java       → Singleton marche.json — CRUD annonces
  MarketListing.java       → POJO annonce (id, seller, item, quantity, pricePerUnit)
  MarketActions.java       → Logique métier : buy / sellByItemId / withdraw
  FrenchItemNames.java     → Dictionnaire FR↔MC + toDisplay() (strip namespace)
                             Inclut items cottonmod : coton, fil, tissu, aloé, camomille, calendula,
                             bandage, medkit, parachute, etc. (namespace cottonmod:*)
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
             | leaderboard[] | loansAsLender[] | loansAsBorrower[]
             | requestsAsLender[] | requestsAsBorrower[] | known[] | recurring[]
BANK_RESULT: bool ok | string msg | [même contenu que BANK_OPEN]
txs[]         : int count → (int type, string label, int amount, long timestamp) × count
leaderboard[] : int count → (string name, int balance) × count
loansAs*[]    : int count → (int id, string other, int principal, long dueMs,
                             int daysOverdue, int totalPenalty, int nextPenalty, bool repaid) × count
requestsAs*[] : int count → (int id, string other, int principal, int durationDays, int penaltyBase) × count
known[]       : int count → string × count
recurring[]   : int count → (int id, string to, int amount, int intervalTicks, int ticksUntilNext) × count
```

### Quêtes
```
QUEST_OPEN  : int level | int xp | int xpToNext | available[] | active[] | pending[]
              | groupPending[] | lbCompleted[] | lbLevel[] | community
QUEST_ACTION: int action | int param (questId ou index selon l'action)
QUEST_RESULT: bool ok | string msg | [même contenu que QUEST_OPEN]
community   : bool has → (string label, string type, string target, int quantity,
                          int progress, int rewardShards, bool completed, int myContribution)
Actions : ACCEPT(0) / CLAIM(1) / CANCEL(2) / COLLECT(3) / CANCEL_PENDING(4)
```

### Registre
```
REGISTRE_OPEN : int count → (string nomRp, string pseudoMc, bool enLigne) × count
```

### Production
```
PROD_OPEN  : bool isOp | entries[]
PROD_ACTION: int action (RESET 0 / RECHECK 1 / RELOAD 2 — op only)
PROD_RESULT: bool ok | string msg | bool isOp | entries[]
entries[]  : int count → (string itemId, long count, long seuil, int prix, int quantite, bool enVente) × count
```

---

## Événements bot Discord

| Type | Champs data | Description |
|---|---|---|
| `PLAYER_JOIN` | player, uuid, premiere_mc, **balance** | Connexion — bot UPDATE shards + en_ligne |
| `PLAYER_LEAVE` | player, uuid, nom_rp? | Déconnexion — bot UPDATE en_ligne=false |
| `PLAYER_DEATH` | player, uuid, cause | Mort joueur |
| `ECONOMY_REWARD` | player, amount, reason | Gain ◆ (kill, playtime) |
| `ECONOMY_TRANSFER` | from, to, amount | Virement |
| `ECONOMY_ADMIN` | admin, target, action, amount | Admin give/take |
| `MARKET_SYNC` | listings[] | Resync marché complet |
| `SERVER_START` / `SERVER_STOP` | — | Démarrage/arrêt |

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

## Système HUD — décisions techniques

- `BalanceHudOverlay.cachedBalance` statique, initialisé à `-1` (affiche `? ◆`), mis à jour depuis réseau
- `NouvelleTerreBridge.sendBalanceToPlayer(player)` appelé : JOIN, kill reward, playtime reward, virement récurrent
- Rendering HUD : single `HudRenderCallback` dans `NouvelleTerreBridgeClient` itère `HudEditorScreen.WIDGETS`
- HUD masqué quand F3 actif : `NouvelleTerreBridgeClient.debugHudActive` (mis à false par `InGameHudMixin`, true par `DebugHudMixin`)
- HUD masqué quand screen quelconque ouvert — SAUF `ChatScreen` (commandes/tchat) ET `HudEditorScreen`
- Chat ouvert : widgets chevauchant la barre de saisie (< 15px du bas) masqués individuelement
- `HudEditorScreen` rend les widgets lui-même en mode LAYOUT, sinon `HudRenderCallback` les rend
- `HudWidget.getPixelX/Y` clamp automatiquement pour rester dans les bords de l'écran
- Positions stockées en fractions `0.0–1.0` → indépendantes de la résolution
- Preview widget dans card : anchorX/Y temporairement modifiés puis restaurés, scissor appliqué pour clipper
- Widgets `isDragOnly()` (ex: NotificationWidget) : render() = no-op, aperçu mock dans card, zone fantôme en mode placement
- Grille de cards scrollable (VISIBLE_ROWS=2, molette) — taille panneau constante quel que soit le nb de widgets
- `FpsWidget.onFrame()` appelé dans le HudRenderCallback à chaque frame pour mesurer les FPS
- Boutons des cards sans shadow (`drawText(..., false)`) pour aspect moins "bold"
- Snap aux bords : si le widget passe à moins de 8px d'un bord pendant le drag, il se colle
- `HudEditorScreen.removed()` → `saveAll()` → `ClientConfig.save()` — sauvegarde à la fermeture uniquement
- Touche H par défaut (catégorie `key.categories.nouvelle-terre-bridge`), rebindable dans Contrôles
- ModMenu = `modCompileOnly "com.terraformersmc:modmenu:7.2.2"` — entrypoint `modmenu` dans `fabric.mod.json`

## Système noms RP — décisions techniques

- **Signed chat 1.20.1** : `GameProfile.getName()` ne peut pas être changé → seule solution = `ALLOW_CHAT_MESSAGE` cancel + rebroadcast system message
- **Tab list** : `ServerPlayerEntityMixin.getPlayerListName()` lit le cache `nomsRP` côté serveur
  → `PlayerListS2CPacket(UPDATE_DISPLAY_NAME)` broadcast immédiat après fetchNomRP pour que tous les clients voient le nom RP
- **Nameplate** : `entity.getDisplayName()` est appelé côté client (pas via tab list) → `AbstractClientPlayerEntityMixin` nécessaire pour intercepter et retourner `PlayerListEntry.getDisplayName()`
- **Compatibilité "Styled Player List"** : ce mod client lit `GameProfile.getName()` directement — incompatible avec le renommage tab list. Avec le mixin client `AbstractClientPlayerEntityMixin`, le nameplate fonctionne indépendamment.
- `URLEncoder.encode(secret, UTF_8)` dans les query params GET — les chars spéciaux (`=`, `+`, etc.) cassent `URI.create()` sinon

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
