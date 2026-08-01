package com.nouvelleterrebridge.market;

/**
 * POJO représentant une annonce de vente sur le marché (HDV).
 */
public class MarketListing {
    public int id;
    public String seller;
    public String item; // ex: "minecraft:diamond"
    public int quantity;
    public int pricePerUnit;
    public long timestamp;
    public String itemNBT; // Données NBT (enchantements, etc.) en format SNBT — null = item vierge

    public MarketListing() {}

    public MarketListing(int id, String seller, String item, int quantity, int pricePerUnit) {
        this.id = id;
        this.seller = seller;
        this.item = item;
        this.quantity = quantity;
        this.pricePerUnit = pricePerUnit;
        this.timestamp = System.currentTimeMillis();
        this.itemNBT = null;
    }

    public MarketListing(int id, String seller, String item, int quantity, int pricePerUnit, String itemNBT) {
        this.id = id;
        this.seller = seller;
        this.item = item;
        this.quantity = quantity;
        this.pricePerUnit = pricePerUnit;
        this.timestamp = System.currentTimeMillis();
        this.itemNBT = itemNBT;
    }

    public int getTotal() {
        return quantity * pricePerUnit;
    }
}
