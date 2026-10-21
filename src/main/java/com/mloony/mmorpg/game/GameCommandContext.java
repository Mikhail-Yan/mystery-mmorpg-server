package com.mloony.mmorpg.game;

import com.mloony.mmorpg.protocol.ServerMessage;

import java.util.UUID;

/**
 * Transport-neutral context for a game command.
 * Netty and REST both submit commands through the same game-loop queue.
 */
public interface GameCommandContext {
    UUID playerId();
    void playerId(UUID playerId);
    void reply(ServerMessage message);
}
