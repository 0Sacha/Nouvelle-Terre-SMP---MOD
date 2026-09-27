# Spec — couche Fabric à réécrire pour le portage NeoForge natif

Ce document précise, fonction par fonction, tout ce qui touche réellement l'API du
loader (Fabric aujourd'hui) dans NouvelleTerreBridge, pour permettre une réécriture
propre en NeoForge plutôt qu'un renommage mécanique. **Portée volontairement
étroite** : la couche métier (économie, marché, quêtes, service, http — voir
CLAUDE.md) n'a aucune dépendance Fabric réelle et n'est PAS concernée par ce
document ; elle continue d'être adaptée mécaniquement (renommage Yarn→Mojang des
types Minecraft qu'elle référence), pas réécrite.

⚠ **Contrainte transversale : transparence totale pour le joueur.** Aucun
changement de gameplay, de nom de commande, d'identifiant d'item, de texte affiché,
ni de comportement visible. Le namespace de ressources reste `nouvelle-terre-bridge`
(le modId technique NeoForge, `nouvelle_terre_bridge`, n'apparaît nulle part en jeu —
seulement dans l'écran des mods F3/menu mods, jamais dans un message, un item ou une
commande).

---

## 1. Entrypoint serveur — `NouvelleTerreBridge.java` (`onInitialize()`, 1148 lignes)

Actuellement `implements ModInitializer`, une seule méthode `onInitialize()` qui fait
tout dans cet ordre (l'ordre compte, voir notes) :

1. **Enregistrement des 7 items** (`SHARD`, `SHARD_5/10/20/50/100`, `PARCHEMIN`) via
   `Registry.register(BuiltInRegistries.ITEM, ResourceLocation.of(MOD_ID, id), item)`,
   puis ajout aux onglets créatifs via `ItemGroupEvents.modifyEntriesEvent(...)`
   (INGREDIENTS pour les Shards, TOOLS pour le Parchemin).
   Les classes `ShardItem`/`ParcheminItem` elles-mêmes n'ont aucune dépendance
   Fabric — seul ce bloc d'enregistrement est concerné.
2. Chargement config (`ModConfig.charger()`, `MaintenanceMode.load()`).
3. `EventQueue`/`EventDispatcher` init (HTTP pur, hors sujet).
4. **`ServerLifecycleEvents.SERVER_STARTED`** → capture `serveur` (champ statique
   partagé, utilisé par du code sans contexte serveur comme les notifications de
   production) + appelle `ShopThresholds.deriverEtMigrer(s)` (les recettes ne sont
   chargées qu'au démarrage, c'est le seul moment où dériver les prix).
   **`ServerLifecycleEvents.SERVER_STOPPED`** → remet `serveur = null`.
   ⚠ Enregistré ici et PAS dans `ServerEvents.register()`, qui se désactive
   entièrement si les événements bot sont coupés en config — cette capture doit
   survivre à ce cas.
5. `ServerEvents.register()`, `PlayerEvents.register()`, `KillRewards.register()`,
   `PlaytimeTracker.register()`, `RecurringTransferManager.register()`,
   `LoanManager.register()` — chacun encapsule ses propres registrations d'events
   (détaillées section 2).
6. Chargement des managers économie (`ShopThresholds.load()`,
   `ServerShopPriceManager.load()` — **dans cet ordre**, `resyncBasePrices()`
   dépend de `ShopThresholds` déjà chargé —, `ProductionTracker.load()`,
   `ProductionShopManager.purgerAnnoncesLegacy()`, etc.). Aucune dépendance Fabric.
7. **`PlayerBlockBreakEvents.AFTER`** → anti-exploit production (détail section 2.1).
8. **`ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY`** → alimente les quêtes
   KILL (détail section 2.2, variante n°1 — `KillRewards` en a une deuxième).
9. **`ServerTickEvents.END_SERVER_TICK`** → `QuestManager::tick` (rollover
   journalier des quêtes, vérifié chaque minute côté logique).
10. **`CommandRegistrationCallback`** → enregistre les 16 commandes (détail
    section 2.3). Les corps de commande eux-mêmes (Brigadier pur) ne changent pas.
11. `ServiceNetworkHandler.register()` + 8 méthodes privées
    `registerXNetworking()` (Hdv, Bank, Quest, Registre, Production, Conflit, Hub,
    Shop) — chacune appelle `NtNet.surServeur(CANAL, handler)`. **Ces handlers ne
    sont PAS concernés par la réécriture** : ils lisent un buffer, appellent la
    couche métier, répondent — mécanique, loader-agnostique une fois `NtNet`
    réécrit (section 4). Seul le `PacketByteBufs.create()` utilisé pour allouer les
    buffers de réponse doit être remplacé partout (balayage mécanique, ~8 fichiers).
12. **`ServerPlayConnectionEvents.JOIN`** → envoie le solde, rafraîchit le pool de
    quêtes, garantit le Parchemin (détail section 2.4).
13. **`ServerPlayerEvents.AFTER_RESPAWN`** → redonne le Parchemin après une mort,
    même sans keepInventory.

## 2. Événements Fabric — détail et correspondance NeoForge

### 2.1 `PlayerBlockBreakEvents.AFTER` (`NouvelleTerreBridge.java`)
Signature Fabric : `(World world, PlayerEntity player, BlockPos pos, BlockState
state, BlockEntity blockEntity)`. Comportement actuel :
- Ignore si le bloc a été posé par un joueur (`PlacedBlockTracker.estPoseParJoueur`)
  — anti-exploit : poser/casser en boucle ne doit rien créditer.
- Sinon, calcule les drops réels via `Block.getDroppedStacks(state, world, pos,
  blockEntity, player, player.getMainHandStack())` (Fortune/Silk Touch inclus),
  ajoute chaque stack à `ProductionTracker` et à `QuestManager.onItemHarvested`.

**→ NeoForge `BlockDropsEvent`** (et non `BlockEvent.BreakEvent`, qui se déclenche
*avant* la casse — les drops n'y seraient pas encore les vrais, cf. plan). Cet event
fournit directement la liste des drops réels ; le calcul manuel via
`Block.getDroppedStacks` disparaît, remplacé par la liste de l'event. Le reste
(garde-fou pose-puis-casse, alimentation `ProductionTracker`/`QuestManager`) est
identique.

### 2.2 `ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY` (2 sites)
Signature Fabric : `(ServerWorld world, Entity entity, LivingEntity killedEntity)`.
- **`NouvelleTerreBridge.java`** : si `entity instanceof ServerPlayer`, alimente
  `QuestManager.onMobKilled(pseudo, typeId, server)`.
- **`KillRewards.java`** : table `Class<?> → montant ◆` pour ~19 mobs (zombies,
  squelettes, boss...), verse la récompense au joueur tueur et envoie un message
  chat. Import wildcard `net.minecraft.entity.mob.*` à éclater en imports Mojang
  précis (`net.minecraft.world.entity.monster.Zombie`, etc.).

**→ NeoForge `LivingDeathEvent`** (bus `NeoForge.EVENT_BUS`). L'entité qui meurt est
`event.getEntity()` ; l'attaquant potentiel est `event.getSource().getEntity()`, à
tester `instanceof ServerPlayer`. Les deux sites deviennent chacun un listener sur
ce même event (comportements indépendants, aucune fusion nécessaire).

### 2.3 `CommandRegistrationCallback` (`NouvelleTerreBridge.java`)
Un seul wrapper enregistrant 16 `XxxCommand.register(dispatcher)`. Les corps de
commande (`commands/*.java`, Brigadier pur, zéro API Fabric à l'intérieur) ne
changent pas.

**→ NeoForge `RegisterCommandsEvent`** (mod bus). `event.getDispatcher()` remplace
le premier paramètre du callback Fabric ; les 16 appels `XxxCommand.register(...)`
restent identiques.

### 2.4 `ServerPlayConnectionEvents.JOIN` / `DISCONNECT` (2 sites)
- **`NouvelleTerreBridge.java` (JOIN)** : dans `server.execute(...)`, envoie le
  solde (`sendBalanceToPlayer`), rafraîchit le pool de quêtes du joueur, donne le
  Parchemin si absent.
- **`events/PlayerEvents.java` (JOIN)**, détail complet le plus riche du mod :
  1. Pécule de départ (500 ◆) si `!FirstJoinTracker.hasReceived(pseudo)`.
  2. Bonus quotidien (+25 ◆) sinon, via `DailyBonusTracker.claimToday`.
  3. `EventDispatcher.envoyer("PLAYER_JOIN", {player, uuid, premiere_mc, balance})`.
  4. Envoie `NT_VERSION` au client (version du mod, lue via `FabricLoader` — voir
     section 3).
  5. Envoie `NT_NOM_RP` pour chaque joueur déjà en ligne (peuplage du cache client
     à la connexion).
  6. `EventDispatcher.fetchNomRP(uuid, server, callback)` — au retour :
     - Met à jour `NouvelleTerreBridge.nomsRP` (cache serveur).
     - Rediffuse `NT_NOM_RP` à tous les joueurs connectés.
     - Crée/recrée une **scoreboard team** `"nt_" + uuid[0..8]`, prefix
       `"§fNomRP §8(§7"`, suffix `"§8)"` — c'est ce qui fait afficher `NomRP
       (pseudo)` dans la tab list nativement (`PlayerListEntry.displayName` doit
       rester null).
     - Broadcast `"§8[RP] §f{nom} §8(§7{pseudo}§8) §7est arrivé..."`.
  - **`events/PlayerEvents.java` (DISCONNECT)** : arrête le suivi playtime, retire
    la scoreboard team, broadcast le message de départ RP, envoie `PLAYER_LEAVE`.
  - **`events/PlayerEvents.java` (`ServerMessageEvents.ALLOW_CHAT_MESSAGE`)** :
    si un nom RP est connu pour l'expéditeur, annule le message signé et rebroadcast
    manuellement `"§8<§f{nom}§8> §f{contenu}"` comme message système. Retourne
    `false` pour annuler l'original, `true` sinon (laisse passer normalement).

**→ NeoForge `PlayerEvent.PlayerLoggedInEvent`/`PlayerLoggedOutEvent`** (event bus)
pour JOIN/DISCONNECT — `event.getEntity()` donne le `ServerPlayer`, le paramètre
`server`/`handler` Fabric est retrouvé via `player.getServer()` /
`player.connection`. Les DEUX sites JOIN (NouvelleTerreBridge + PlayerEvents)
restent deux listeners indépendants sur ce même event NeoForge.

**→ NeoForge `ServerChatEvent`** pour le chat RP : contrairement à Fabric, cet event
permet de **remplacer directement le message** (`event.setMessage(...)` ou
équivalent) plutôt que d'annuler puis rebroadcaster à la main — simplification
réelle, à vérifier lors de l'implémentation si l'event expose bien un setter ou
seulement `setCanceled()` + rebroadcast manuel comme aujourd'hui.

### 2.5 `ServerPlayerEvents.AFTER_RESPAWN` (`NouvelleTerreBridge.java`)
Signature Fabric : `(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean
alive)`. Comportement : `donnerParcheminSiAbsent(newPlayer)`.

**→ NeoForge `PlayerEvent.PlayerRespawnEvent`** — `event.getEntity()` remplace
`newPlayer`.

### 2.6 `ServerTickEvents.END_SERVER_TICK` (4 sites, tous triviaux)
- `NouvelleTerreBridge.java` → `QuestManager::tick`
- `economy/PlaytimeTracker.java` → `PlaytimeTracker::onTick`
- `economy/LoanManager.java` → `server -> getInstance().tick(server)`
- `economy/RecurringTransferManager.java` → `server -> getInstance().tick(server)`

**→ NeoForge `ServerTickEvent.Post`** — `event.getServer()` remplace le paramètre
`server`. Quatre listeners indépendants, aucune fusion nécessaire.

### 2.7 `ServerLifecycleEvents.SERVER_STARTED`/`SERVER_STOPPING` (`events/ServerEvents.java`)
Derrière un garde `if (!config.isActiverEvenementServeur()) return;` :
- STARTED : envoie `SERVER_START` au bot (`version`, `maxPlayers`), puis planifie
  `envoyerSyncMarche()` 3s plus tard (laisser le bot traiter SERVER_START d'abord).
- STOPPING : envoie `SERVER_STOP` (`onlinePlayers`).

**→ NeoForge `ServerStartedEvent`/`ServerStoppingEvent`**. Le champ `data.put
("version", "1.20.1")` est une valeur figée pré-existante, sans lien avec ce
portage — ne pas la "corriger" au passage sans qu'on le demande.

---

## 3. `FabricLoader.getInstance()` — balayage mécanique (~19 fichiers)

Deux usages seulement dans tout le mod :
1. **Chemins de fichiers** : `.getGameDir()`/`.getConfigDir()` pour localiser les
   `.json` du mod (économie, quêtes, config...). Fichiers concernés :
   `MaintenanceMode`, `ModConfig`, `client/ClientConfig`,
   `economy/{DailyBonusTracker,FirstJoinTracker,LoanManager,LocalEconomy,
   PlayerLevelManager,ProductionTracker,QuestManager,RecurringTransferManager,
   SauvegardeFichier,ServerShopPriceManager,ShopThresholds}`,
   `http/EventQueue`, `market/MarketManager`, `service/ServiceManager`.
   **→ `FMLPaths.GAMEDIR.get()`/`FMLPaths.CONFIGDIR.get()`** (`net.neoforged.fml.loading.FMLPaths`).
2. **Version du mod** : `.getModContainer(MOD_ID).map(c ->
   c.getMetadata().getVersion().getFriendlyString())` — 2 sites
   (`events/PlayerEvents.java` pour `NT_VERSION`,
   `NouvelleTerreBridgeClient.java` pour la comparaison client/serveur).
   **→ `ModList.get().getModContainerById(MOD_ID).map(c ->
   c.getModInfo().getVersion().toString())`**.
   ⚠ Avec le qualificatif `+neoforge.N` ajouté à `mod_version` pendant les tests,
   s'assurer que la comparaison égalité-stricte client/serveur reste cohérente
   (les deux côtés tournant la même build, le toast « mod obsolète » ne doit pas
   se déclencher à tort).

Ce balayage ne change aucun comportement — c'est un remplacement 1:1, pas une
réécriture conceptuelle.

## 4. Réseau — `network/NtNet.java` + `NtPayload.java`

Déjà conçu comme un unique point de passage (voir CLAUDE.md) : `NtPayload` est un
`record` générique portant un `byte[]` brut, `NtNet` réexpose
`canal/surServeur/surClient/versServeur/versClient`. **Aucun des ~530 appels
`readInt`/`writeString`/… dans les 34 canaux, ni les handlers `registerXNetworking()`
côté serveur, ni les 22 `NtNet.surClient(...)` côté client (`NouvelleTerreBridgeClient.java`)
ne changent** — seul le contenu de `NtNet.java` est réécrit :

- **Enregistrement** : NeoForge exige type+codec+handler en un seul appel
  (`PayloadRegistrar.playBidirectional`), au moment de `RegisterPayloadHandlersEvent`
  — alors que `canal()` (appelé à l'init statique des classes `*Networking.java`) et
  `surServeur`/`surClient` (appelés depuis `onInitialize`/`onInitializeClient`, à un
  moment différent) sont aujourd'hui découplés. Solution : `canal()` crée le
  `CustomPacketPayload.Type<NtPayload>` et une case mutable (récepteur serveur +
  récepteur client, remplis plus tard) ; un nouveau `NtNet.enregistrer(event)`,
  appelé depuis le listener `RegisterPayloadHandlersEvent`, boucle sur tous les
  canaux déclarés et appelle `playBidirectional` avec un `DirectionalPayloadHandler`
  qui délègue à la case mutable au moment de l'appel (donc peu importe l'ordre
  réel entre `canal()`/`surServeur()`/l'event, tant que `surServeur`/`surClient`
  sont appelés avant qu'un vrai paquet n'arrive — toujours vrai en pratique).
- **Envoi** : `PacketDistributor.sendToPlayer(joueur, payload)` /
  `PacketDistributor.sendToServer(payload)`.
- **Contexte** : `IPayloadContext.player()` renvoie un `Player` commun (à caster en
  `ServerPlayer` côté serveur) ; `MinecraftServer` s'obtient via
  `((ServerPlayer) context.player()).getServer()`.

`CustomPacketPayload.Id<T>` (nom Yarn) devient `CustomPacketPayload.Type<T>` en
Mojang — déjà renommé dans les 34 fichiers `network/*Networking.java` et dans
`NtPayload.java` (mécanique, fait). Le champ/méthode `getId()` de `NtPayload`
devient `type()` (nom de méthode imposé par l'interface `CustomPacketPayload`
Mojang).

`PacketByteBufs.create()`/`.empty()` (Fabric, utilisé ~8 fichiers pour allouer les
buffers de réponse) → remplacé par un petit helper dans `NtNet`
(`NtNet.buffer()` → `new FriendlyByteBuf(Unpooled.buffer())`), pour n'avoir qu'un
seul endroit à changer plutôt que 8.

## 5. Entrypoint client — `NouvelleTerreBridgeClient.java` (`onInitializeClient()`, 649 lignes)

`implements ClientModInitializer`. Structure :
1. `ClientConfig.load()`, `NotificationHud.register()`, ajout des 9 widgets HUD à
   `HudEditorScreen.WIDGETS`.
2. **`HudRenderCallback.EVENT.register(...)`** (+ un second site indépendant dans
   `client/NotificationHud.java` pour les toasts) — rend les widgets HUD activés,
   sauf si F3 actif, éditeur HUD ouvert, ou un autre écran occupe déjà l'affichage
   (sauf le chat).
   **→ NeoForge `RegisterGuiOverlaysEvent`** (enregistrement) + rendu dans le
   callback de l'`IGuiOverlay` fourni. Deux overlays distincts (HUD principal +
   toasts), pas de fusion nécessaire.
3. **`KeyBindingHelper.registerKeyBinding(...)`** → touche H, catégorie
   `"key.categories.nouvelle-terre-bridge"`, ouvre `HudEditorScreen`.
   **→ NeoForge `RegisterKeyMappingsEvent`** (mod bus), type `KeyMapping` vanilla
   inchangé.
4. **`ClientPlayConnectionEvents.JOIN`/`DISCONNECT`** → Discord RPC
   (`DiscordRPCManager.onJoin/onLeave`) + vidage du cache `nomsRP` à la déconnexion.
   **→ NeoForge `ClientPlayerNetworkEvent.LoggingIn`/`LoggingOut`**.
5. **`ClientTickEvents.END_CLIENT_TICK`** → tick Discord RPC + détection de la
   touche HUD (`hudKey.wasPressed()`).
   **→ NeoForge `ClientTickEvent.Post`**.
6. 22× `NtNet.surClient(CANAL, handler)` — **inchangés**, voir section 4.

`client/ModMenuIntegration.java` est déjà du code mort (entièrement commenté,
`maven.terraformersmc.com` indisponible) : à supprimer, pas à porter. Aucun
écran de config n'est actuellement exposé nulle part ailleurs (`NouvelleSettingsScreen`
n'est atteint que depuis le hub in-game) — rien à remplacer fonctionnellement.

---

## 6. Mixins (11 fichiers) — comportement exact avant conversion

Tous déjà en mappings Mojang pour les *types*, mais les chaînes `method = "..."`
des `@Inject` utilisent encore des noms/descripteurs Yarn à corriger quel que soit
le sort de chaque mixin (converti en event ou conservé).

### Convertis en event NeoForge (8) :

| Mixin | Cible/injection actuelle | Comportement | Devient |
|---|---|---|---|
| `LivingEntityMixin` | `LivingEntity.onDeath` HEAD | Si `ServerPlayer`, envoie `PLAYER_DEATH` (pseudo, uuid, message de mort, cause) au bot | `LivingDeathEvent` |
| `MobDropMixin` | `LivingEntity.onDeath` HEAD+TAIL | Pose/lève un `ThreadLocal<Boolean> PLAYER_KILL` si la victime n'est pas un joueur et l'attaquant en est un | fusionné dans le listener `LivingDropsEvent` ci-dessous (le `ThreadLocal` de coordination entre 2 mixins n'a plus de raison d'exister si l'event donne directement les deux entités) |
| `EntityDropMixin` | `Entity.dropStack(ItemStack,float)` HEAD | Si `PLAYER_KILL`, ajoute le stack à `ProductionTracker` | `LivingDropsEvent` — `event.getEntity()` (victime), `event.getSource().getEntity()` (attaquant), `event.getDrops()` (liste des `ItemEntity`/stacks) ; vérifier `!(victime instanceof ServerPlayer) && attaquant instanceof ServerPlayer` puis itérer les drops |
| `BlockItemMixin` | `BlockItem.place(ItemPlacementContext)` RETURN, si accepté | Marque la position posée dans `PlacedBlockTracker` (uniquement si joueur, côté serveur) | `BlockEvent.EntityPlaceEvent` |
| `CraftingResultSlotMixin` | `ResultSlot.onTakeItem` HEAD, `@Shadow CraftingContainer input` | Détecte un décompactage (1 item en entrée → 4 ou 9 en sortie) via la grille de craft : si oui, **retire** 1 du compteur du bloc source et ne crédite rien ; sinon crédite normalement `ProductionTracker`+`QuestManager` | `PlayerEvent.ItemCraftedEvent` — fournit `event.getCraftingContainer()` équivalent, le `@Shadow` disparaît |
| `ParcheminDropMixin` | `ServerPlayer.dropSelectedItem(boolean)` HEAD, cancellable | Si l'item en main est le Parchemin, annule (retourne `false`) | `ItemTossEvent` (annulable) — couvre en prime le glisser hors inventaire (limite actuellement documentée dans CLAUDE.md, qui disparaît) |
| `InGameHudMixin` | `Gui.render` HEAD | Remet `debugHudActive = false` chaque frame | Suppression pure — remplacé par une lecture directe de `Minecraft.getInstance().getDebugOverlay().showDebugScreen()` là où `debugHudActive` est consulté (HUD render), plus besoin de flag intermédiaire |
| `DebugHudMixin` | `DebugScreenOverlay.render` HEAD | Met `debugHudActive = true` | Idem — supprimé, même remplacement |

⚠ Pour `MobDropMixin`/`EntityDropMixin` → `LivingDropsEvent` : vérifier à
l'implémentation que `event.getSource().getEntity()` est bien peuplé de façon
fiable pour un attaquant joueur (sinon garder un mécanisme équivalent au
`ThreadLocal`, mais cette fois porté par l'event lui-même plutôt que par deux
mixins séparés).

### Restent des mixins (3) — pas d'event NeoForge équivalent :

| Mixin | Cible Yarn actuelle | Devient (Mojang) |
|---|---|---|
| `PlayerManagerMixin` | `PlayerList.checkCanJoin(SocketAddress, GameProfile)` cancellable, renvoie `Component` | `PlayerList.canPlayerLogin(SocketAddress, GameProfile)` — **nom de méthode différent en Mojang**, à vérifier précisément à l'implémentation |
| `ServerPlayerEntityMixin` | `ServerPlayer.getPlayerListName()` cancellable | `ServerPlayer.getTabListDisplayName()` (déjà noté dans CLAUDE.md) |
| `AbstractClientPlayerEntityMixin` | `Entity.getDisplayName()` cancellable, filtré `instanceof AbstractClientPlayer` | Nom inchangé en Mojang (`Entity.getDisplayName()`) |

Les descripteurs de méthode Yarn dans les `@Inject(method = "...(L...;)L...;")`
(ex. `EntityDropMixin`, `BlockItemMixin`) n'ont plus lieu d'être puisque ces deux
mixins-là sont supprimés (convertis en event) — seuls les 3 mixins restants
gardent une signature à vérifier/mettre à jour en Mojang.

---

## 7. Items — `item/ShardItem.java` / `item/ParcheminItem.java`

Aucune dépendance Fabric dans les classes elles-mêmes (déjà `extends Item` vanilla,
déjà `Item.Settings`/`Item.Properties` vanilla). Seul le **bloc d'enregistrement**
dans `NouvelleTerreBridge.java` (section 1, point 1) change :

**→ `DeferredRegister.createItems(NAMESPACE)`** où `NAMESPACE` est le namespace de
ressources `"nouvelle-terre-bridge"` (⚠ pas le modId technique
`"nouvelle_terre_bridge"` — voir contrainte transversale en tête de document) +
**`BuildCreativeModeTabContentsEvent`** pour l'ajout aux onglets créatifs
(remplace `ItemGroupEvents.modifyEntriesEvent`).

---

## Fichiers listés dans ce document (couche à réécrire)

```
NouvelleTerreBridge.java          (entrypoint + items + 5 events + commandes + réseau serveur)
NouvelleTerreBridgeClient.java    (entrypoint client + 4 events + réseau client)
network/NtNet.java                (réécriture complète de l'intérieur, contrat inchangé)
network/NtPayload.java            (Id→Type, getId()→type() — déjà fait mécaniquement)
events/PlayerEvents.java          (JOIN/DISCONNECT/chat RP)
events/ServerEvents.java          (STARTED/STOPPING)
economy/KillRewards.java          (combat event + imports mob Yarn)
economy/PlaytimeTracker.java      (tick event, 1 ligne)
economy/LoanManager.java          (tick event, 1 ligne)
economy/RecurringTransferManager.java (tick event, 1 ligne)
client/ModMenuIntegration.java    (suppression)
mixin/LivingEntityMixin.java      → event
mixin/MobDropMixin.java           → event (fusionné avec EntityDropMixin)
mixin/EntityDropMixin.java        → event (fusionné avec MobDropMixin)
mixin/BlockItemMixin.java         → event
mixin/CraftingResultSlotMixin.java → event
mixin/ParcheminDropMixin.java     → event
mixin/InGameHudMixin.java         → supprimé
mixin/DebugHudMixin.java          → supprimé
mixin/PlayerManagerMixin.java     (reste mixin, renommage Mojang)
mixin/ServerPlayerEntityMixin.java (reste mixin, renommage Mojang)
mixin/AbstractClientPlayerEntityMixin.java (reste mixin, renommage Mojang)
```

Tout le reste du mod (couche métier, écrans clients, les 34 fichiers `network/*Networking.java`,
les 16 fichiers `commands/*.java`) continue de recevoir uniquement le renommage
mécanique Yarn→Mojang déjà en cours — pas de réécriture.
