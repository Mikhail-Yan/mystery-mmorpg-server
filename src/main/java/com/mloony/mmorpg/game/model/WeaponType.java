package com.mloony.mmorpg.game.model;

public enum WeaponType {
    SWORD(20, 1, 0.95),
    SPEAR(16, 2, 0.90),
    BOW(12, 5, 0.80);

    private final int damage;
    private final int range;
    private final double accuracy;

    WeaponType(int damage, int range, double accuracy) {
        this.damage = damage;
        this.range = range;
        this.accuracy = accuracy;
    }

    public int damage() {
        return damage;
    }

    public int range() {
        return range;
    }

    public double accuracy() {
        return accuracy;
    }
}
