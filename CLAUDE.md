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
- Format : `x.y.z` semver (dans `gradle.properties` → `mod_version`) — le suffixe `-beta` a été abandonné en 1.0.0
- **Incrémenter la version avant chaque rebuild/push.**
- Version actuelle : `1.4.0` (refonte UI — listes, saisie numérique, gestion du shop)
  - 1.3.3 : scroll de l'onglet Vendre du HDV
  - 1.3.2 : migration des prix sans perte de progression
  - 1.3.1 : flèche retour hub, /shop, Parchemin offert au shop
  - 1.3.0 : Parchemin + Shop Serveur autonome + prix de référence
  - 1.2.2 : Admin Shop avec prix dynamique + rééquilibrage économie
  - 1.2.1 : quêtes complétées disparaissent et ne peuvent être refaites
  - 1.2.0 : item Shard ◆ monnaie physique + objectifs de quêtes explicites
- À chaque rebuild : mettre à jour `mod_version` dans `gradle.properties`, puis `git commit` + `git push`

---

## Architecture économie
- Source de vérité : `shards.json` sur le serveur (`LocalEconomy.java`)
- Toutes les opérations sont instantanées (pas d'HTTP pour le gameplay)
- Après chaque op, événement async vers le bot pour sync DB Discord
- `ECONOMY_SALARY` = notification only côté bot (ne pas appeler `db.addShards`, déjà fait via `ECONOMY_REWARD`)
- `PLAYER_JOIN` inclut `balance` → bot fait `UPDATE joueurs SET shards=? WHERE uuid=?` pour resync au login
- **Coupures (1.4.0, `ShardDenominations`)** : 6 items de monnaie — 1, 5, 10, 20, 50, 100 ◆.
  Uniquement du rangement : retirer 5 000 ◆ en pièces de 1 remplissait 78 piles d'inventaire.
  Une coupure de 100 vaut exactement cent pièces de 1, il n'y a **aucune** logique de change
  au-delà de ça (pas d'ATM, pas de caisse — voir le stash pour cette refonte-là, abandonnée).
  ⚠ `shard` **garde son identifiant d'origine** (valeur 1) : le renommer aurait fait disparaître
  tous les Shards déjà en circulation chez les joueurs.
  - `ShardItem` porte sa `valeur` ; le clic droit dépose `count × valeur`.
  - `decomposer()` / `donner()` : rendu en grosses coupures d'abord.
  - `retirer()` : prélève les **petites** coupures d'abord, pour ne pas casser un billet de 100
    quand le joueur a l'appoint. Une coupure étant insécable, le prélèvement peut dépasser le
    montant voulu — l'excédent est rendu en monnaie par l'appelant.
  - `totalEnPoche()` : valeur réelle portée, toutes coupures confondues (le dépôt s'en sert).
- **Monnaie physique (`ShardItem`)** : item custom `nouvelle-terre-bridge:shard` ("Shard ◆", 1 item = 1 ◆).
  Retrait : `/bank` → Compte → bouton "Retirer en Shards ◆" → modal montant (`ACTION_WITHDRAW_SHARDS`,
  `removeShards` + items donnés/drop). Dépôt : clic droit avec le stack en main → `depositShards`
  (tout le stack, log TRANSFER_IN, event ECONOMY_REWARD au bot, actionbar + NT_BALANCE).
  **Dépôt groupé (1.4.0)** : `/bank` → Compte → "Déposer des Shards ◆" ouvre un modal avec le
  montant (`ACTION_DEPOSIT_SHARDS`, pré-rempli au total en poche mais **ajustable** — on ne
  dépose pas forcément tout). Le clic droit pile par pile devenait interminable dès qu'un joueur
  avait un peu d'argent. Le serveur re-plafonne au contenu réel de l'inventaire : le client
  borne la saisie mais ne fait pas autorité. `amount = 0` signifie « tout ».
  Déposer un montant que les coupures en poche ne font pas exactement (7 ◆ avec un billet de 100)
  prélève la coupure et **rend l'appoint** en petite monnaie.
  Le retrait rend le montant en coupures optimales (`ShardDenominations.donner`).
  Texture custom or (assets/…/textures/item/shard.png), enregistré dans ItemGroups.INGREDIENTS.
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
- **HDV Onglets** : Marché (joueurs), Vendre, Mon Shop, Boutiques
  Le HDV ne contient **que** les annonces entre joueurs — le Shop Serveur est un écran séparé.
- Catégories HDV : Tout, Blocs, Matériaux, Outils, Nourriture, Potions, **Médical**, Divers
- **Enchantements** : `MarketListing.itemNBT` (SNBT) stocke les données NBT.
  `sellByItemId`/`buy`/`withdraw` le capturent et le restaurent, sinon les annonces
  livraient des items vierges. Les variantes (enchantée / vierge) d'un même item sont
  **distinctes** partout : inventaire de vente, correspondance serveur et agrégation d'achat.
  Le client reçoit le NBT dans HDV_OPEN/HDV_RESULT → tooltip vanilla au survol.

## Architecture Shop Serveur (écran autonome, 1.3.0)
- Écran `ServerShopScreen` — 2 onglets **Acheter / Vendre**, ouvert depuis le Parchemin.
  Ce n'est plus un onglet du HDV et il ne passe plus par `marche.json`.
- Catalogue = items de `ShopThresholds` **dont la production a atteint le seuil**
  (`ServerShopActions.estDebloque()`, revalidé serveur — le client ne fait pas autorité).
- `ProductionShopManager` ne crée plus d'annonces `$Serveur` ; `checkAll()` purge les anciennes.
- **Notification de déblocage (1.4.0)** : `ProductionShopManager.notifierSiDebloque(itemId, avant, apres)`,
  appelé par `ProductionTracker.add()`, envoie un toast vert (`NT_TOAST`) à tous les joueurs
  connectés quand un item franchit son seuil.
  Le franchissement se teste sur l'intervalle `]avant, apres]`, **pas** par égalité avec le
  seuil : une récolte Fortune ou un craft de pile peut sauter la valeur exacte
  (l'ancien `count == entry.seuil` de `checkItem` ratait ces cas).
  Utilise `NouvelleTerreBridge.serveur` (référence statique posée sur SERVER_STARTED, en dehors
  de `ServerEvents` qui se désactive entièrement si les événements bot sont coupés en config).

### Anti-exploit de production (1.4.0)
La production n'est censée compter que la matière qui **entre** réellement dans le monde.
Deux boucles la contournaient, toutes deux fermées :
- **Poser/casser** (`PlacedBlockTracker` + `BlockItemMixin`) : poser puis recasser le même bloc
  512 fois débloquait l'item aussi sûrement que d'en produire 512. Tout bloc posé par un joueur
  est marqué ; le casser consomme la marque et **ne compte pas**.
  Table bornée à 200 000 positions en éviction LRU, non persistée : la marque est posée à la pose,
  donc toujours plus récente que la casse qu'elle annule. Un redémarrage entre les deux fait
  repasser le bloc pour naturel — marginal, et non exploitable sans redémarrer le serveur.
- **Compacter/décompacter** (`CraftingResultSlotMixin`) : 9 diamants → 1 bloc → 9 diamants
  créditait les deux compteurs à chaque tour. Le décompactage retire maintenant 1 du compteur
  du bloc et ne crédite rien → cycle neutre. Le compactage reste compté.
⚠ Ne pas « corriger » le compactage en le décomptant : fabriquer réellement des blocs est la
façon prévue de les débloquer au Shop.
- **Prix de référence (`ShopThresholds.PRIX_REFERENCE`)** : table explicite ◆/unité.
  Indispensable — la rareté vanilla ne reflète pas la valeur : diamant et lingot de
  netherite sont `Rarity.COMMON`, d'où le diamant à 1-2 ◆. La rareté n'est plus qu'un repli.
  Exemples : netherite 900, diamant 120, émeraude 45, or 25, fer 12, charbon 3.
- **Prix dynamiques (`ServerShopPriceManager`)** — `server-shop-prices.json`. Deux facteurs :
  1. **Flux net du shop** (`unitsSold - unitsBought`) : le serveur vend → l'item se raréfie
     et monte ; il rachète → il baisse. +100% à +2048 net, jusqu'à −40% à −1024 net.
  2. **Abondance produite** (`ProductionTracker`, 1.3.2) : décote logarithmique
     `0.10 × log10(production / max(seuil, 64))`, **plafonnée à −30 %** (`DECOTE_MAX`).
     - Rapportée au seuil de l'item, sinon minerai rare et bloc courant seraient incomparables.
     - Plancher de 64 au dénominateur : les items chers ont un seuil de 1 à 4 et
       toucheraient le prix plancher bien trop vite.
     - Plafond nécessaire : le compteur de production ne fait que **monter** (il ignore
       ce qui est consommé, posé ou perdu), donc sans lui tout finirait au prix plancher.
  `getPrice()` recalcule **à la lecture** : la production évolue en continu, un cache
  mis à jour à la dernière transaction serait périmé.
- **Marge de rachat** : `RATIO_RACHAT = 0.55` — le serveur rachète à 55% de son prix de vente.
  Sans cette marge, acheter puis revendre serait neutre et toute variation de prix
  transformerait le shop en machine à shards.
- Le serveur ne rachète **que les piles vierges** (ni NBT, ni dégâts) : impossible d'évaluer
  équitablement un objet enchanté ou abîmé.
- L'argent d'achat va sur `$Serveur` ; le rachat le déduit via `forceDeduct` (compte système
  exclu des totaux, il peut passer négatif — c'est un puits comptable, pas une trésorerie).

## Parchemin (1.3.0)
- Item `nouvelle-terre-bridge:parchemin` — terminal portatif, clic droit → `HubScreen`.
  Nom identique en fr_fr et en_us : « Parchemin » (traduire par « Scroll » cassait la DA RP).
- `HubScreen` : DA carte électronique (substrat vert, bus et pistes cuivre, puces à broches),
  8 entrées → Marché, Shop, Banque, Quêtes, Production, Registre, Conflit, Guide.
- Donné à la connexion et après un respawn (`donnerParcheminSiAbsent`), et récupérable
  gratuitement via le bandeau épinglé du Shop (`ACTION_CLAIM_PARCHEMIN`). **Non vendable.**
- `ParcheminDropMixin` bloque la touche « lâcher » (cible `ServerPlayerEntity.dropSelectedItem`
  et **non** `dropItem` : à ce stade la pile est déjà retirée, l'annuler la supprimerait).
  ⚠ Ne bloque **pas** le glisser hors de l'inventaire — le tooltip ne promet donc que
  la restitution automatique, pas l'impossibilité de le jeter.
- Occupe un slot normal — un vrai slot supplémentaire exigerait de mixiner `PlayerInventory`
  et `PlayerScreenHandler` (sérialisation NBT comprise), risque de perte d'items jugé trop élevé.
- **`HubBackButton`** : flèche « ← » partagée, en tête de chaque écran, qui rouvre le hub
  côté client (aucune donnée serveur requise). Présente dans Hdv, Shop, Bank, Quetes,
  Production, Registre, Conflit et Wiki.
  Les écrans à onglets mémorisent `tabsStartX` au rendu au lieu de recalculer l'offset dans
  `mouseClicked` — le HDV avait déjà 4 px de dérive avant l'ajout de la flèche.

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
| `/conflit` | Ouvre le GUI Conflit RP (liste joueurs en ligne + raison → alerte Discord) |
| `/evenement <message>` | Narration (op only) |
| `/quetes` | Ouvre le GUI Quêtes |
| `/quetes refresh` | Recharge quetes-templates.json (op 2) |
| `/quetes reset` | Réinitialise toute la progression (op 2) |
| `/registre` | Ouvre le GUI Registre des personnages (screen client Fabric) |
| `/production` | Ouvre le GUI Production naturelle (tous les joueurs ; boutons admin si op 2, pas de sous-commandes) |
| `/shop` | Ouvre le GUI Shop Serveur (achat / revente) |

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
  ConflitCommand.java      → /conflit : envoie CONFLIT_OPEN (liste joueurs en ligne) → GUI client
  EventNarratifCommand.java → /evenement — narration (op only)
  QuetesCommand.java       → /quetes (ouvre GUI via QUEST_OPEN), /quetes refresh, /quetes reset
  RegistreCommand.java     → /registre : appelle fetchPersonnages, envoie REGISTRE_OPEN au client
                             en_ligne recalculé depuis server.getPlayerManager() (la DB bot peut être désync)
  ProductionCommand.java   → /production (ouvre GUI via PROD_OPEN, tous joueurs — GUI only, pas de sous-commandes)
  ShopCommand.java         → /shop : envoie SHOP_OPEN au client (Shop Serveur)

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
                             **Cibles 100 % vanilla (1.4.0)** : les 2 templates cottonmod
                             (`cottonmod:cotton`, `cottonmod:bandage`) ont été remplacés par
                             `minecraft:clay_ball` et `minecraft:golden_carrot`. Une quête ne doit
                             jamais dépendre d'un mod : un joueur sans le mod ne peut pas la finir.
                             ⚠ N'introduire aucun identifiant hors `minecraft:` dans ce pool.
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
  ShopThresholds.java      → Singleton seuils-shop.json — seuils de déblocage + prix de base
                             Entry : seuil (production avant mise en vente), prix (◆/unité), quantité (lot)
                             PRIX_REFERENCE : table explicite ◆/unité, fait autorité (la rareté
                             vanilla ne reflète pas la valeur — diamant/netherite sont COMMON).
                             Rareté = repli seulement. Admins peuvent surcharger via le JSON.
                             **Migration (1.3.2)** : Entry.versionPrix + constante VERSION_PRIX.
                             load() → migrerPrix() réapplique PRIX_REFERENCE aux entrées d'une
                             révision antérieure, **sans toucher production.json**. Incrémenter
                             VERSION_PRIX à chaque révision de la table. Une entrée déjà à jour
                             est laissée intacte → les surcharges admin survivent.
                             ⚠ Ne jamais conseiller /production → Reset pour changer les prix :
                             il efface aussi les compteurs de production des joueurs.
                             **Gestion par item (1.4.0)** : `setPrix()` (marque l'entrée à
                             VERSION_PRIX, sinon migrerPrix() écraserait la correction admin
                             au prochain démarrage), `toggleDesactive()`, `supprimer()`.
                             `Entry.desactive` = retiré du catalogue **sans** perdre le compteur
                             de production ni le prix — distinct d'une suppression, l'item peut
                             être remis en vente sans que les joueurs perdent leur progression.
  ServerShopPriceManager.java → Singleton server-shop-prices.json — prix dynamiques du shop
                             Flux net = unitsSold − unitsBought ; vendre fait monter, racheter baisser
                             API : getPrice(), getBuybackPrice(), recordSale(), recordPurchase(), reset()
                             load() → resyncBasePrices() : basePrice est une copie figée de
                             ShopThresholds, à réaligner sinon une révision des prix resterait
                             sans effet sur tout item déjà échangé. Le flux net est préservé.
                             ⚠ Doit être chargé APRÈS ShopThresholds (ordre dans onInitialize).
  ServerShopActions.java   → Achat/revente auprès de $Serveur, marge de rachat 55 %
                             estDebloque() : seuil de production atteint ET entrée non désactivée
                             (`Entry.desactive`), revalidé serveur. Fait aussi autorité pour le
                             statut « en vente » affiché par /production.
                             Ne rachète que les piles vierges (ni NBT, ni dégâts)

item/
  ShardItem.java           → Item monnaie physique "Shard ◆" (1 = 1 ◆). use() côté serveur :
                             depositShards(tout le stack) + actionbar + sendBalanceToPlayer.
  ParcheminItem.java       → Terminal portatif. use() → HUB_OPEN (ouvre HubScreen).
                             maxCount(1), fireproof, non jetable, redonné à la connexion/respawn.
                             Enregistré dans NouvelleTerreBridge (SHARD, id "shard", Rarity.UNCOMMON)

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
  BlockItemMixin.java              → @Inject BlockItem.place RETURN → PlacedBlockTracker.marquer()
                                     Marque tout bloc posé par un joueur (voir anti-exploit ci-dessous)
  CraftingResultSlotMixin.java     → @Inject onTakeItem HEAD → compte le craft en production
                                     @Shadow du champ `input` : à HEAD la grille contient encore les
                                     ingrédients, seul moment où la provenance du résultat est lisible.
                                     Décompactage (1 bloc → 4/9 unités) : ne crédite rien et **retire**
                                     1 du compteur du bloc → le cycle compacter/décompacter est neutre.
                                     Le compactage (9 → 1) reste compté : fabriquer des blocs est la
                                     façon prévue de les débloquer au Shop.
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
                                       LOAN_ACCEPT(6) / LOAN_DECLINE(7) / WITHDRAW_SHARDS(8) /
                                       DEPOSIT_SHARDS(9)
                             DEPOSIT_SHARDS (1.4.0) : `int amount` (0 = tout). Comptage et retrait
                             dans `server.execute` — l'inventaire n'est pas thread-safe, et le
                             montant réellement déposable en dépend.
  QuestNetworking.java     → Canaux : QUEST_OPEN (S→C, ouvre GUI) / QUEST_ACTION (C→S) / QUEST_RESULT (S→C)
                             Actions : ACTION_ACCEPT(0) / ACTION_CLAIM(1)
  RegistreNetworking.java  → Canal : REGISTRE_OPEN (S→C, ouvre RegistreScreen)
  ProductionNetworking.java → Canaux : PROD_OPEN (S→C, ouvre GUI) / PROD_ACTION (C→S) / PROD_RESULT (S→C)
                             Actions (op only, revalidées serveur) : RESET(0) / RECHECK(1) / RELOAD(2)
                                       / SET_PRICE(3) / TOGGLE(4) / DELETE(5)   (1.4.0)
                             PROD_ACTION porte toujours (int action, string itemId, int valeur),
                             même quand l'action n'en a pas besoin : un format unique évite de
                             faire dépendre la lecture du buffer de la valeur de l'action.
  ConflitNetworking.java   → Canaux : CONFLIT_OPEN (S→C, liste joueurs) / CONFLIT_ACTION (C→S, cible+raison)
                             / CONFLIT_RESULT (S→C, ok+msg → toast NotificationHud, ferme le screen si ok)
  HubNetworking.java       → Canaux : HUB_OPEN (S→C, ouvre HubScreen) / HUB_ACTION (C→S)
                             Actions : HDV(0) BANK(1) QUETES(2) PRODUCTION(3) REGISTRE(4)
                                       CONFLIT(5) WIKI(6) SHOP(7)
  ShopNetworking.java      → Canaux : SHOP_OPEN (S→C) / SHOP_ACTION (C→S) / SHOP_RESULT (S→C)
                             Actions : ACTION_BUY(0) / ACTION_SELL(1) / ACTION_CLAIM_PARCHEMIN(2)

client/                    ← @Environment(CLIENT) uniquement
  NumberInput.java         → **Champ numérique partagé (1.4.0)** — prix, quantités, montants.
                             Deux saisies complémentaires, aucune ne suffit seule : paliers
                             (Min/-64/-32/-1/+1/+32/+64/Max) pour ajuster à la souris, et frappe
                             clavier directe pour les gros montants. `keyPressed` traite
                             explicitement GLFW KP_0..KP_9 (320-329) : selon la disposition
                             clavier, `charTyped` ne remonte pas toujours le pavé numérique.
                             Bornes via `setBounds(min,max)` (re-clampe la valeur), position
                             mémorisée au rendu (`lastX/Y/W`) et relue par `mouseClicked`.
                             Hauteur totale = `NumberInput.H` (42 px).
                             ⚠ L'écran hôte doit relayer `keyPressed`/`charTyped`, sinon la
                             frappe clavier ne marche pas (les paliers, eux, fonctionnent seuls).
  HdvScreen.java           → Screen marché : 4 onglets (Marché / Vendre / Mon Shop / Boutiques)
                             - **Marché** : annonces des joueurs uniquement
                             - **Vendre** : créer une annonce (variantes NBT distinctes)
                               Grille d'inventaire scrollable (1.3.3) — elle avait son propre
                               rendu, hors de la grille partagée, et était restée sans décalage :
                               le scissor masquait tout ce qui dépassait, donc inatteignable.
                               handleSellClick lit `hoveredSellItem` (positionné au rendu) au
                               lieu de recalculer les positions, qui se désynchronisaient.
                             - **Mon Shop** : gérer ses annonces (bouton Retirer)
                             - **Boutiques** : tri par vendeur. `shopSellers()` exclut `$Serveur`
                               et soi-même — la liste était gonflée par des entrées non cliquables.
                             **Affichage en liste (1.4.0)** : `renderListRows()` remplace la grille
                             de cards — icône à gauche, prix et bouton d'action à droite. Une ligne
                             par article, donc le scroll se compte en articles et non en rangées.
                             **Scrollbar (1.4.0)** : `renderScrollbar()` + `applyScrollFromMouse()`,
                             pouce **or opaque** et **draggable** (mouseDragged/mouseReleased).
                             L'ancienne barre était en `0x60FFFFFF` et non saisissable : la molette
                             marchait, mais rien ne signalait qu'il restait du contenu — d'où le
                             « scroll cassé » remonté par les joueurs.
                             Les boucles d'onglets itèrent sur `Tab.values()` : un tableau codé en
                             dur avait rendu un onglet ni dessiné ni cliquable.
                             Tooltip vanilla au survol (nom + enchantements) + ligne vendeur/prix
                             Chip solde haut-droit → BANK_REQUEST → ouvre BankScreen
                             Catégorie "Médical" : items cottonmod (coton, bandage, medkit, plantes...)
  ServerShopScreen.java    → Shop Serveur autonome : onglets Acheter / Vendre, **liste** (1.4.0,
                             même forme que le HDV), scrollbar or draggable, modal quantité avec
                             `NumberInput`, tendance de prix en clair sur la ligne
                             Bandeau Parchemin épinglé en tête de l'onglet Acheter (offert) —
                             la liste est décalée de BANNER_H pour ne pas passer dessous
  HubScreen.java           → Hub du Parchemin, DA carte électronique, 8 puces cliquables
  BankScreen.java          → Screen banque : 5 onglets (Compte / Economie / Classement / Credits / Virements)
  QuetesScreen.java        → Screen quêtes : 2 onglets (Disponibles / Mes Quêtes), PW=420 PH=300,
                             cards avec barre de progression, boutons Accepter/Réclamer.
                             Objectifs affichés avec le nom localisé de la cible (targetName(type,target) :
                             ENTITY_TYPE pour KILL, ITEM sinon) — plus de labels poétiques côté UI
  ProductionScreen.java    → Screen production : liste scrollable (icône + nom FR + compteur + barre + statut),
                             tri : en vente d'abord puis progression desc. Boutons admin (Recheck/Recharger/Reset)
                             rendus uniquement si isOp (revalidé serveur). Reset = double-clic ("Confirmer ?" 3 s)
                             → remise à zéro complète : compteurs + seuils dynamiques + annonces auto.
                             PW_MAX=620 PH_MAX=460 ROW_H=40
                             **Recherche (1.4.0)** : champ en tête, filtre sur le nom FR ou l'identifiant.
                             Toutes les bornes de scroll lisent `filtered()`, pas `entries` — sinon
                             le scroll resterait calé sur la liste complète pendant un filtrage.
                             **Gestion du shop (1.4.0, op only)** : clic sur une ligne → modal avec
                             prix (`NumberInput`), Retirer/Remettre en vente, Supprimer (double-clic).
                             Les lignes mémorisent leur index dans `rowBtnBounds` au rendu ; le
                             recalculer au clic se désynchroniserait du scroll et du filtre.
                             ⚠ Le statut « en vente » vient désormais de `ServerShopActions.estDebloque()`.
                             Il lisait `MarketManager.hasAutoListing()`, mécanisme abandonné en 1.3.0
                             (les annonces `$Serveur` sont purgées et jamais recréées) : tous les items
                             s'affichaient donc comme non mis en vente.
  ConflitScreen.java       → Screen conflit RP : liste joueurs en ligne (clic = sélection) + champ raison
                             + bouton rouge "Déclarer le conflit". PW_MAX=340 PH_MAX=320
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
listings[]: int count → (int id, string seller, string itemId, int qty, int price, string nbt) × count
            nbt = SNBT ou "" — sans lui le client ne peut ni afficher les enchantements
            ni distinguer une variante enchantée d'une variante vierge

### Shop Serveur
SHOP_OPEN  : int balance | entries[]
SHOP_ACTION: int action | string itemId | int qty
SHOP_RESULT: bool ok | string msg | int balance | entries[]
entries[]  : int count → (string itemId, int buyPrice, int sellPrice, long netFlow) × count

### Hub (Parchemin)
HUB_OPEN   : (vide)
HUB_ACTION : int action
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
PROD_ACTION: int action | string itemId | int valeur
             (RESET 0 / RECHECK 1 / RELOAD 2 / SET_PRICE 3 / TOGGLE 4 / DELETE 5 — op only)
             itemId = "" et valeur = 0 pour les actions globales
PROD_RESULT: bool ok | string msg | bool isOp | entries[]
entries[]  : int count → (string itemId, long count, long seuil, int prix, int quantite,
                          bool enVente, bool desactive) × count
             enVente = ServerShopActions.estDebloque() (seuil atteint ET non désactivé)
```

### Conflit
```
CONFLIT_OPEN  : int count → string joueur × count (en ligne, sauf soi-même)
CONFLIT_ACTION: string cible | string raison
CONFLIT_RESULT: bool ok | string msg
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

- **Thread des actions HDV (corrigé en 1.4.0)** — le récepteur `HDV_ACTION` doit lire le
  `PacketByteBuf` sur le thread réseau (il est libéré au retour du callback) puis exécuter
  **toute la logique dans `server.execute(...)`**.
  Il exécutait `MarketActions.buy/sellByItemId/withdraw` directement sur le thread netty, donc
  `insertStack()` / `decrement()` touchaient l'inventaire hors du thread serveur, en concurrence
  avec la synchronisation faite au tick : le serveur avait bien l'item enchanté, le client
  recevait une pile vierge — d'où « les items enchantés perdent leur enchantement à l'achat ».
  Le NBT lui-même n'était pas en cause (SNBT → JSON → SNBT vérifié intact).
  Les récepteurs Shop et Production suivaient déjà ce schéma ; HDV était le seul écart.
- Screen Fabric pur — pas de `ScreenHandler`, pas de slots vanilla
- Items rendus en 2× (32×32 px) via `drawItemScaled()` — transform matricielle sur `ctx.getMatrices()`
- La vente lit l'inventaire côté client (`client.player.getInventory().main`) — le serveur revalide
- Sidebar catégories : icône item Minecraft + compteur d'annonces par catégorie (`CAT_ICONS` map)
- Tri : enum `SortMode` (PRICE_ASC / PRICE_DESC / NAME) cyclé par le bouton "⇅"
- **Scrollbar (1.4.0)** : 6 px, piste `C_BORDER` + pouce **or opaque**, saisissable à la souris.
  Elle était en `0x60FFFFFF` et non draggable : la molette fonctionnait, mais rien n'indiquait
  qu'il restait du contenu — les joueurs l'ont signalé comme un « scroll cassé ».
  La piste est mémorisée au rendu (`scrollTrackX/Y/H`, `scrollThumbH`) et relue par
  `mouseClicked`/`mouseDragged`, comme partout ailleurs dans le mod.
- **Liste plutôt que grille (1.4.0)** : `renderListRows()` — une ligne par article, icône à
  gauche, prix et bouton d'action à droite. Le scroll se compte donc en articles, pas en rangées.
- Toast bottom-right avec accent coloré sur la bordure gauche (vert succès, rouge erreur)
- **Chip solde** haut-droit : cliquable → envoie `BANK_REQUEST` → ouvre `BankScreen`
- **Modal achat z-order** : `renderBuyModal()` dans `ctx.getMatrices().push() / translate(0,0,300) / pop()` — sinon le texte des lignes passe devant (batching Minecraft)
- **Prix dynamiques du Shop Serveur** : `ServerShopPriceManager.recordSale()` appelé dans
  `MarketActions.buy()` quand isAuto = true. `filteredListings()` exclut `$Serveur` — le Shop
  Serveur est un écran séparé depuis la 1.3.0.
- **Saisie numérique** : tous les champs prix/quantité passent par `NumberInput` (voir plus haut).
  L'écran hôte doit relayer `keyPressed`/`charTyped` au champ concerné, sinon seuls les paliers
  souris répondent.

## GUI Bank — décisions techniques

- **Onglet Virements** : 2 cards (`cardW = (pw - GAP) / 2`), `renderInfoCard()` partagé
- **Dropdowns** : rendu dans `render()` après le tab content, overlay `0xAA000000` + scissor + scroll. Champs montant cachés via `setY(-200)` quand dropdown ouvert
- **Focus des champs texte** : dans `mouseClicked` onglet TRANSFERS, appeler `super.mouseClicked()` AVANT `handleTransfersClick()` — sinon les TextFieldWidget ne prennent jamais le focus clavier
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

