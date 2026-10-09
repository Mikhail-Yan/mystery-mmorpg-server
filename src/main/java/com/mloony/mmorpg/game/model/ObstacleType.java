package com.mloony.mmorpg.game.model;

public enum ObstacleType {
    WALL(true, true),
    ROCK(true, true),
    TREE(true, false),
    WATER(true, false);

    private final boolean blocksMovement;
    private final boolean blocksProjectiles;

    ObstacleType(boolean blocksMovement, boolean blocksProjectiles) {
        this.blocksMovement = blocksMovement;
        this.blocksProjectiles = blocksProjectiles;
    }

    public boolean blocksMovement() {
        return blocksMovement;
    }

    public boolean blocksProjectiles() {
        return blocksProjectiles;
    }
}
