package com.mloony.mmorpg.game.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

public class Player {
    private final UUID id;
    private final String name;
    private Position position;
    private int hp = 100;
    private int defendTicks;
    private int dodgeTicks;
    private final Deque<Position> path = new ArrayDeque<>();

    public Player(UUID id, String name, Position position) {
        this.id = id;
        this.name = name;
        this.position = position;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Position getPosition() {
        return position;
    }

    public int getHp() {
        return hp;
    }

    public int getDefendTicks() {
        return defendTicks;
    }

    public int getDodgeTicks() {
        return dodgeTicks;
    }

    public boolean isAlive() {
        return hp > 0;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public void damage(int damage) {
        this.hp = Math.max(0, this.hp - damage);
    }

    public void healToFull() {
        this.hp = 100;
    }

    public void defend(int ticks) {
        this.defendTicks = Math.max(this.defendTicks, ticks);
    }

    public void dodge(int ticks) {
        this.dodgeTicks = Math.max(this.dodgeTicks, ticks);
    }

    public boolean isDefending() {
        return defendTicks > 0;
    }

    public boolean isDodging() {
        return dodgeTicks > 0;
    }

    public Deque<Position> getPath() {
        return path;
    }

    public void setPath(Deque<Position> newPath) {
        path.clear();
        path.addAll(newPath);
    }

    public void tickStatuses() {
        if (defendTicks > 0)
            defendTicks--;
        if (dodgeTicks > 0)
            dodgeTicks--;
    }
}
