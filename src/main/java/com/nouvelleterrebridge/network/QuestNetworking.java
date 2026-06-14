package com.nouvelleterrebridge.network;

import net.minecraft.util.Identifier;

public class QuestNetworking {
    public static final Identifier QUEST_OPEN   = new Identifier("nouvelle-terre-bridge", "quest_open");
    public static final Identifier QUEST_ACTION = new Identifier("nouvelle-terre-bridge", "quest_action");
    public static final Identifier QUEST_RESULT = new Identifier("nouvelle-terre-bridge", "quest_result");

    public static final int ACTION_ACCEPT = 0;
    public static final int ACTION_CLAIM  = 1;
}
