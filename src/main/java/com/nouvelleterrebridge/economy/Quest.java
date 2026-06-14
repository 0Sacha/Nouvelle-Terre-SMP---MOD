package com.nouvelleterrebridge.economy;

import java.util.ArrayList;
import java.util.List;

public class Quest {
    public int         id;
    public String      type;           // "KILL" | "HARVEST" | "DELIVERY"
    public String      target;         // entity type id (KILL) or item id (HARVEST/DELIVERY)
    public int         quantity;
    public int         levelRequired;
    public int         maxPlayers;     // 1 = solo, 2+ = groupe
    public boolean     isGlobal;       // true = quête groupe partagée globalement

    // Récompense
    public String      rewardType;     // "SHARDS" | "ITEM"
    public int         rewardShards;
    public String      rewardItem;     // item id si rewardType = ITEM
    public int         rewardItemQty;

    // XP gagné à la complétion
    public int         rewardXp;

    // Coût pour accepter (0 = gratuit)
    public int         costShards;

    // Tags : ["SOLO"|"GROUPE"], ["KILL"|"HARVEST"|"DELIVERY"], ["FACILE"|"MOYEN"|"DIFFICILE"|"LÉGENDAIRE"], ["SHARDS"|"ITEM"]
    public List<String> tags = new ArrayList<>();

    public String      label;
    public long        expiresAt;     // epoch ms

    public Quest() {}
}
