package com.mloony.mmorpg.rest;

import com.mloony.mmorpg.game.GameEngine;
import com.mloony.mmorpg.protocol.ClientMessage;
import com.mloony.mmorpg.protocol.CommandType;
import com.mloony.mmorpg.protocol.ServerMessage;
import com.mloony.mmorpg.rest.model.AttackRequest;
import com.mloony.mmorpg.rest.model.JoinRequest;
import com.mloony.mmorpg.rest.model.MoveRequest;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/game")
public class GameRestController {
    private static final long COMMAND_TIMEOUT_SECONDS = 3;

    private final GameEngine engine;

    public GameRestController(GameEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/players")
    public ServerMessage join(@RequestBody(required = false) JoinRequest request) {
        String name = request == null ? null : request.name();
        return await(engine.submitRest(null, new ClientMessage(CommandType.JOIN, name, null, null, null, null)));
    }

    @PostMapping("/players/{playerId}/move")
    public ServerMessage move(@PathVariable("playerId") UUID playerId, @RequestBody MoveRequest request) {
        return await(engine.submitRest(playerId,
                new ClientMessage(CommandType.MOVE_TO, null, request.x(), request.y(), null, null)));
    }

    @PostMapping("/players/{playerId}/attack")
    public ServerMessage attack(@PathVariable("playerId") UUID playerId, @RequestBody AttackRequest request) {
        return await(engine.submitRest(playerId,
                new ClientMessage(CommandType.ATTACK, null, null, null, request.targetId(), request.weapon())));
    }

    @PostMapping("/players/{playerId}/defend")
    public ServerMessage defend(@PathVariable("playerId") UUID playerId) {
        return await(engine.submitRest(playerId, new ClientMessage(CommandType.DEFEND, null, null, null, null, null)));
    }

    @PostMapping("/players/{playerId}/dodge")
    public ServerMessage dodge(@PathVariable("playerId") UUID playerId) {
        return await(engine.submitRest(playerId, new ClientMessage(CommandType.DODGE, null, null, null, null, null)));
    }

    @DeleteMapping("/players/{playerId}")
    public ServerMessage leave(@PathVariable("playerId") UUID playerId) {
        return await(engine.disconnectRest(playerId));
    }

    @GetMapping("/map")
    public ServerMessage map() {
        return await(engine.submitRest(null, new ClientMessage(CommandType.MAP, null, null, null, null, null)));
    }

    @GetMapping("/state")
    public ServerMessage state() {
        return await(engine.submitRest(null, new ClientMessage(CommandType.STATE, null, null, null, null, null)));
    }

    @GetMapping("/ping")
    public ServerMessage ping() {
        return await(engine.submitRest(null, new ClientMessage(CommandType.PING, null, null, null, null, null)));
    }

    /**
     * Контроллер для команд на подобие netty tcp Один контроллер для всех команд
     */
    @PostMapping("/command")
    public ServerMessage command(@RequestParam(value = "playerId", required = false) UUID playerId,
            @RequestBody ClientMessage message) {
        return await(engine.submitRest(playerId, message));
    }

    private ServerMessage await(CompletableFuture<ServerMessage> future) {
        try {
            return future.get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return ServerMessage.error("REST command interrupted");
        } catch (Exception e) {
            future.cancel(true);
            return ServerMessage.error("REST command failed: " + e.getClass().getSimpleName());
        }
    }
}
