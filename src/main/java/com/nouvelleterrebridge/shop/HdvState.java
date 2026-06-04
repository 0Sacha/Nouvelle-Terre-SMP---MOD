package com.nouvelleterrebridge.shop;

import net.minecraft.inventory.SimpleInventory;

import java.util.HashMap;
import java.util.Map;

public class HdvState {

    public enum Mode { VENDORS, ITEMS, MY_SHOP, SELL }

    public Mode mode             = Mode.VENDORS;
    public String currentSeller = null;          // filtre par vendeur ou null
    public int page = 0;
    public int selectedSellQty = 1;              // quantité sélectionnée en mode SELL
    public String searchQuery = null;            // filtre texte (via GUI enclume)
    public final SimpleInventory inv;

    public final Map<Integer, Runnable> leftClick  = new HashMap<>();
    public final Map<Integer, Runnable> rightClick = new HashMap<>();
    public final Map<Integer, Runnable> shiftClick = new HashMap<>();

    public HdvState(SimpleInventory inv) {
        this.inv = inv;
    }

    public void clearHandlers() {
        leftClick.clear();
        rightClick.clear();
        shiftClick.clear();
    }
}
