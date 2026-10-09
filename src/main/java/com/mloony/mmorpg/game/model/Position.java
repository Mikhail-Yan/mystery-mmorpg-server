package com.mloony.mmorpg.game.model;

public record Position(int x, int y) {
    public int manhattanDistance(Position other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y);
    }
}
