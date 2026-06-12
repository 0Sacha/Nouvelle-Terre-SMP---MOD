package com.nouvelleterrebridge.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class BalanceHudOverlay {

    public static int cachedBalance = -1;

    private BalanceHudOverlay() {}
}
