package com.nouvelleterrebridge.network;

import net.minecraft.util.Identifier;

public final class HdvNetworking {

    public static final Identifier HDV_OPEN   = new Identifier("nouvelle-terre-bridge", "hdv_open");
    public static final Identifier HDV_ACTION = new Identifier("nouvelle-terre-bridge", "hdv_action");
    public static final Identifier HDV_RESULT = new Identifier("nouvelle-terre-bridge", "hdv_result");
    public static final Identifier NT_VERSION = new Identifier("nouvelle-terre-bridge", "nt_version");

    public static final int ACTION_BUY              = 0;
    public static final int ACTION_SELL             = 1;
    public static final int ACTION_WITHDRAW         = 2;
    public static final int ACTION_TRANSFER         = 3;
    public static final int ACTION_RECURRING_CREATE = 4;
    public static final int ACTION_RECURRING_CANCEL = 5;

    private HdvNetworking() {}
}
