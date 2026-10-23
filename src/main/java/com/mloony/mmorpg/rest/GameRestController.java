package com.mloony.mmorpg.rest;

import com.mloony.mmorpg.game.GameEngine;
import com.mloony.mmorpg.protocol.ClientMessage;
import com.mloony.mmorpg.protocol.CommandType;
import com.mloony.mmorpg.protocol.ServerMessage;
import com.mloony.mmorpg.rest.model.AttackRequest;
import com.mloony.mmorpg.rest.model.JoinRequest;
import com.mloony.mmorpg.rest.model.MoveRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/api/game")
public class GameRestController {
    private static final Logger log = LoggerFactory.getLogger(GameRestController.class);
    private static final long COMMAND_TIMEOUT_SECONDS = 3;

    private final GameEngine engine;

    public GameRestController(GameEngine engine) {
        this.engine = engine;
    }

    @PostMapping("/players")
    public ServerMessage join(@RequestBody(required = false) JoinRequest request) {
        String name = request == null ? null : request.name();
        log.debug("REST JOIN name={}", name);
        return await("JOIN", null,
                engine.submitRest(null, new ClientMessage(CommandType.JOIN, name, null, null, null, null)));
    }

    @PostMapping("/players/{playerId}/move")
    public ServerMessage move(@PathVariable("playerId") UUID playerId, @RequestBody MoveRequest request) {
        log.debug("REST MOVE_TO playerId={} x={} y={}", playerId, request.x(), request.y());
        return await("MOVE_TO", playerId, engine.submitRest(playerId,
                new ClientMessage(CommandType.MOVE_TO, null, request.x(), request.y(), null, null)));
    }

    @PostMapping("/players/{playerId}/attack")
    public ServerMessage attack(@PathVariable("playerId") UUID playerId, @RequestBody AttackRequest request) {
        log.debug("REST ATTACK playerId={} targetId={} weapon={}", playerId, request.targetId(), request.weapon());
        return await("ATTACK", playerId, engine.submitRest(playerId,
                new ClientMessage(CommandType.ATTACK, null, null, null, request.targetId(), request.weapon())));
    }

    @PostMapping("/players/{playerId}/defend")
    public ServerMessage defend(@PathVariable("playerId") UUID playerId) {
        log.debug("REST DEFEND playerId={}", playerId);
        return await("DEFEND", playerId,
                engine.submitRest(playerId, new ClientMessage(CommandType.DEFEND, null, null, null, null, null)));
    }

    @PostMapping("/players/{playerId}/dodge")
    public ServerMessage dodge(@PathVariable("playerId") UUID playerId) {
        log.debug("REST DODGE playerId={}", playerId);
        return await("DODGE", playerId,
                engine.submitRest(playerId, new ClientMessage(CommandType.DODGE, null, null, null, null, null)));
    }

    @DeleteMapping("/players/{playerId}")
    public ServerMessage leave(@PathVariable("playerId") UUID playerId) {
        log.debug("REST LEAVE playerId={}", playerId);
        return await("LEAVE", playerId, engine.disconnectRest(playerId));
    }

    @GetMapping("/map")
    public ServerMessage map() {
        log.debug("REST MAP");
        return await("MAP", null,
                engine.submitRest(null, new ClientMessage(CommandType.MAP, null, null, null, null, null)));
    }

    @GetMapping("/state")
    public ServerMessage state() {
        log.debug("REST STATE");
        return await("STATE", null,
                engine.submitRest(null, new ClientMessage(CommandType.STATE, null, null, null, null, null)));
    }

    @GetMapping("/ping")
    public ServerMessage ping() {
        log.debug("REST PING");
        return await("PING", null,
                engine.submitRest(null, new ClientMessage(CommandType.PING, null, null, null, null, null)));
    }

    /**
     * Контроллер для команд на подобие netty tcp Один контроллер для всех команд
     */
    @PostMapping("/command")
    public ServerMessage command(@RequestParam(value = "playerId", required = false) UUID playerId,
            @RequestBody ClientMessage message) {
        log.debug("REST generic command playerId={} type={}", playerId, message.type());
        return await(message.type() == null ? "UNKNOWN" : message.type().name(), playerId,
                engine.submitRest(playerId, message));
    }

    private ServerMessage await(String operation, UUID playerId, CompletableFuture<ServerMessage> future) {
        try {
            ServerMessage response = future.get(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("REST command completed operation={} playerId={} responseType={}", operation, playerId,
                    response.type());
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            log.warn("REST command interrupted operation={} playerId={}", operation, playerId);
            return ServerMessage.error("REST command interrupted");
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("REST command timed out operation={} playerId={} timeoutSeconds={}", operation, playerId,
                    COMMAND_TIMEOUT_SECONDS);
            return ServerMessage.error("REST command timed out");
        } catch (Exception e) {
            future.cancel(true);
            log.error("REST command failed operation={} playerId={}", operation, playerId, e);
            return ServerMessage.error("REST command failed: " + e.getClass().getSimpleName());
        }
    }
}
