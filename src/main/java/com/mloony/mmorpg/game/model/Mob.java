package com.mloony.mmorpg.game.model;

import java.util.UUID;

public class Mob {
    private final UUID id;
    private final MobType type;
    private Position position;
    private int hp;

    public Mob(UUID id, MobType type, Position position) {
        this.id = id;
        this.type = type;
        this.position = position;
        this.hp = type.maxHp();
    }

    public UUID getId() {
        return id;
    }

    public MobType getType() {
        return type;
    }

    public Position getPosition() {
        return position;
    }

    public int getHp() {
        return hp;
    }

    public boolean isAlive() {
        return hp > 0;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public void damage(int amount) {
        hp = Math.max(0, hp - amount);
    }
}
