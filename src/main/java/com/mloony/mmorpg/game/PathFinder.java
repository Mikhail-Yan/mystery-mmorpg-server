package com.mloony.mmorpg.game;

import org.springframework.stereotype.Component;

import com.mloony.mmorpg.game.model.Position;

import java.util.*;

@Component
public class PathFinder {
    private static final int[][] DIRS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    public Deque<Position> find(GameWorld world, UUID moverId, Position start, Position target) {
        if (!world.inBounds(target) || world.isBlockedFor(moverId, target)) {
            return new ArrayDeque<>();
        }

        Queue<Position> queue = new ArrayDeque<>();
        Map<Position, Position> prev = new HashMap<>();
        queue.add(start);
        prev.put(start, null);

        while (!queue.isEmpty()) {
            Position cur = queue.poll();
            if (cur.equals(target))
                break;
            for (int[] d : DIRS) {
                Position next = new Position(cur.x() + d[0], cur.y() + d[1]);
                if (!world.inBounds(next) || prev.containsKey(next))
                    continue;
                if (!next.equals(target) && world.isBlockedFor(moverId, next))
                    continue;
                prev.put(next, cur);
                queue.add(next);
            }
        }

        if (!prev.containsKey(target))
            return new ArrayDeque<>();
        ArrayDeque<Position> path = new ArrayDeque<>();
        for (Position p = target; p != null && !p.equals(start); p = prev.get(p)) {
            path.addFirst(p);
        }
        return path;
    }
}
