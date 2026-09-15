package com.nouvelleterrebridge.network;

import net.minecraft.network.packet.CustomPayload;

public class QuestNetworking {
    public static final CustomPayload.Id<NtPayload> QUEST_OPEN = NtNet.canal("quest_open");
    public static final CustomPayload.Id<NtPayload> QUEST_ACTION = NtNet.canal("quest_action");
    public static final CustomPayload.Id<NtPayload> QUEST_RESULT = NtNet.canal("quest_result");

    public static final int ACTION_ACCEPT         = 0;
    public static final int ACTION_CLAIM          = 1;  // KILL/HARVEST : réclamer ; DELIVERY : remettre items
    public static final int ACTION_CANCEL         = 2;  // annuler quête active
    public static final int ACTION_COLLECT        = 3;  // récupérer récompense item (onglet À Réclamer)
    public static final int ACTION_CANCEL_PENDING = 4;  // annuler récompense item en attente
}
