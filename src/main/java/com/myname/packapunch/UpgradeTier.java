package com.myname.packapunch;

public class UpgradeTier {
    public enum CurrencyType {
        ITEM,
        SCOREBOARD,
        XP
    }

    private final CurrencyType currencyType;
    private final String id;
    private final int cost;
    private final float multiplier;

    public UpgradeTier(CurrencyType currencyType, String id, int cost, float multiplier) {
        this.currencyType = currencyType;
        this.id = id;
        this.cost = cost;
        this.multiplier = multiplier;
    }

    public CurrencyType getCurrencyType() {
        return currencyType;
    }

    public String getId() {
        return id;
    }

    public int getCost() {
        return cost;
    }

    public float getMultiplier() {
        return multiplier;
    }
}
