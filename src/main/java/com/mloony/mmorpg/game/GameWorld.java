package com.mloony.mmorpg.game;

import org.springframework.stereotype.Component;

import com.mloony.mmorpg.game.model.*;

import java.util.*;

@Component
public class GameWorld {
    public static final int WIDTH = 100;
    public static final int HEIGHT = 100;

    private final Map<UUID, Player> players = new LinkedHashMap<>();
    private final Map<UUID, Mob> mobs = new LinkedHashMap<>();
    private final Map<Position, Obstacle> obstacles = new HashMap<>();
    private final Random random = new Random(42);

    public GameWorld() {
        generateObstacles();
        spawnMobs();
    }

    public Player addPlayer(String name) {
        Position spawn = findFreePosition();
        Player player = new Player(UUID.randomUUID(), name, spawn);
        players.put(player.getId(), player);
        return player;
    }

    public void removePlayer(UUID id) {
        players.remove(id);
    }

    public Optional<Player> player(UUID id) {
        return Optional.ofNullable(players.get(id));
    }

    public Optional<Mob> mob(UUID id) {
        return Optional.ofNullable(mobs.get(id));
    }

    public Collection<Player> players() {
        return players.values();
    }

    public Collection<Mob> mobs() {
        return mobs.values();
    }

    public Collection<Obstacle> obstacles() {
        return obstacles.values();
    }

    public boolean inBounds(Position p) {
        return p.x() >= 0 && p.x() < WIDTH && p.y() >= 0 && p.y() < HEIGHT;
    }

    public boolean isBlocked(Position p) {
        Obstacle obstacle = obstacles.get(p);
        if (obstacle != null && obstacle.type().blocksMovement())
            return true;
        return players.values().stream().anyMatch(pl -> pl.isAlive() && pl.getPosition().equals(p))
                || mobs.values().stream().anyMatch(m -> m.isAlive() && m.getPosition().equals(p));
    }

    public boolean isBlockedFor(UUID moverId, Position p) {
        Obstacle obstacle = obstacles.get(p);
        if (obstacle != null && obstacle.type().blocksMovement())
            return true;
        return players.values().stream()
                .anyMatch(pl -> !pl.getId().equals(moverId) && pl.isAlive() && pl.getPosition().equals(p))
                || mobs.values().stream()
                        .anyMatch(m -> !m.getId().equals(moverId) && m.isAlive() && m.getPosition().equals(p));
    }

    public boolean projectileBlocked(Position from, Position to) {
        if (from.x() != to.x() && from.y() != to.y())
            return false; // minimal line-of-sight implementation
        int dx = Integer.compare(to.x(), from.x());
        int dy = Integer.compare(to.y(), from.y());
        Position p = new Position(from.x() + dx, from.y() + dy);
        while (!p.equals(to)) {
            Obstacle o = obstacles.get(p);
            if (o != null && o.type().blocksProjectiles())
                return true;
            p = new Position(p.x() + dx, p.y() + dy);
        }
        return false;
    }

    private void generateObstacles() {
        for (int x = 15; x <= 25; x++)
            obstacles.put(new Position(x, 20), new Obstacle(new Position(x, 20), ObstacleType.WALL));
        for (int y = 40; y <= 48; y++)
            obstacles.put(new Position(55, y), new Obstacle(new Position(55, y), ObstacleType.WATER));
        for (int i = 0; i < 30; i++)
            putRandomObstacle(i % 2 == 0 ? ObstacleType.ROCK : ObstacleType.TREE);
    }

    private void putRandomObstacle(ObstacleType type) {
        Position p;
        do
            p = new Position(random.nextInt(WIDTH), random.nextInt(HEIGHT));
        while (obstacles.containsKey(p));
        obstacles.put(p, new Obstacle(p, type));
    }

    private void spawnMobs() {
        for (int i = 0; i < 5; i++)
            addMob(MobType.GOBLIN);
        for (int i = 0; i < 3; i++)
            addMob(MobType.ORC);
        for (int i = 0; i < 3; i++)
            addMob(MobType.ARCHER);
    }

    private void addMob(MobType type) {
        Position p = findFreePosition();
        Mob mob = new Mob(UUID.randomUUID(), type, p);
        mobs.put(mob.getId(), mob);
    }

    public Position findFreePosition() {
        Position p;
        do
            p = new Position(random.nextInt(WIDTH), random.nextInt(HEIGHT));
        while (isBlocked(p));
        return p;
    }
}
