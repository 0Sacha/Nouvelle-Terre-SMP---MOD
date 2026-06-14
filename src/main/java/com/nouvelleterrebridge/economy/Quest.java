package com.nouvelleterrebridge.economy;

public class Quest {
    public int    id;
    public String type;     // "KILL" | "HARVEST"
    public String target;   // entity type id ou item id
    public int    quantity;
    public int    reward;
    public String label;

    public Quest() {}
}
