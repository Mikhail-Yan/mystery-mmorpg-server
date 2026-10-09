package com.mloony.mmorpg.game.model;

public enum MobType {
    GOBLIN(40, 8, 1, 0.15), 
    ORC(80, 14, 1, 0.05), 
    ARCHER(50, 10, 4, 0.10);

    private final int maxHp;
    private final int damage;
    private final int attackRange;
    private final double dodgeChance;

    MobType(int maxHp, int damage, int attackRange, double dodgeChance) {
        this.maxHp = maxHp;
        this.damage = damage;
        this.attackRange = attackRange;
        this.dodgeChance = dodgeChance;
    }

    public int maxHp() {
        return maxHp;
    }

    public int damage() {
        return damage;
    }

    public int attackRange() {
        return attackRange;
    }

    public double dodgeChance() {
        return dodgeChance;
    }
}
