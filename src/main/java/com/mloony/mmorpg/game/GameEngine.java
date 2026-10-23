package com.mloony.mmorpg.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mloony.mmorpg.config.GameProperties;
import com.mloony.mmorpg.game.model.*;
import com.mloony.mmorpg.protocol.ClientMessage;
import com.mloony.mmorpg.protocol.ServerMessage;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class GameEngine {
    private static final Logger log = LoggerFactory.getLogger(GameEngine.class);

    public static final AttributeKey<UUID> PLAYER_ID = AttributeKey.valueOf("playerId");

    private final GameWorld gameWorld;
    private final PathFinder pathFinder;
    private final GameConnectionRegistry connections;
    private final ObjectMapper mapper;
    private final GameProperties properties;
    private final BlockingQueue<GameCommandEnvelope> commands = new LinkedBlockingQueue<>();

    private final ScheduledExecutorService gameLoop = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "game-loop");
        t.setDaemon(true);
        return t;
    });

    private final Random random = new Random();
    private long tick;

    public GameEngine(GameWorld world, PathFinder pathFinder, GameConnectionRegistry connections, ObjectMapper mapper,
            GameProperties properties) {
        this.gameWorld = world;
        this.pathFinder = pathFinder;
        this.connections = connections;
        this.mapper = mapper;
        this.properties = properties;
    }

    @PostConstruct
    void start() {
        log.info("Starting game loop tickMs={} stateBroadcastEveryTicks={}", properties.tickMs(),
                properties.stateBroadcastEveryTicks());
        gameLoop.scheduleAtFixedRate(this::safeTick, 0, properties.tickMs(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        log.info("Stopping game loop tick={}", tick);
        gameLoop.shutdownNow();
    }

    /**
     * Добавление команд в очередь Netty TCP
     */
    public void submit(Channel channel, ClientMessage message) {
        log.debug("Enqueue TCP command channel={} playerId={} type={}", channel.id().asShortText(),
                channel.attr(PLAYER_ID).get(), message.type());
        commands.offer(new GameCommandEnvelope(nettyContext(channel), message));
    }

    /**
     * REST команды выполняются в той же очереди что и команды netty
     */
    public CompletableFuture<ServerMessage> submitRest(UUID playerId, ClientMessage message) {
        CompletableFuture<ServerMessage> response = new CompletableFuture<>();
        AtomicReference<UUID> playerRef = new AtomicReference<>(playerId);

        GameCommandContext context = new GameCommandContext() {
            @Override
            public UUID playerId() {
                return playerRef.get();
            }

            @Override
            public void playerId(UUID id) {
                playerRef.set(id);
            }

            @Override
            public void reply(ServerMessage message) {
                response.complete(message);
            }
        };

        log.debug("Enqueue REST command playerId={} type={}", playerId, message.type());
        commands.offer(new GameCommandEnvelope(context, message));
        return response;
    }

    public CompletableFuture<ServerMessage> disconnectRest(UUID playerId) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(ServerMessage.error("playerId is required"));
        }
        return submitRest(playerId, new ClientMessage(null, "__DISCONNECT__", null, null, playerId, null));
    }

    public void disconnect(Channel channel) {
        UUID playerId = channel.attr(PLAYER_ID).getAndSet(null);
        if (playerId == null)
            return;

        GameCommandContext noReplyContext = new GameCommandContext() {
            @Override
            public UUID playerId() {
                return playerId;
            }

            @Override
            public void playerId(UUID id) {
                // disconnected Netty session does not keep a player id
            }

            @Override
            public void reply(ServerMessage message) {
                // channel is already inactive; no reply is required
            }
        };

        log.debug("Enqueue TCP disconnect playerId={}", playerId);
        commands.offer(new GameCommandEnvelope(noReplyContext,
                new ClientMessage(null, "__DISCONNECT__", null, null, playerId, null)));
    }

    private GameCommandContext nettyContext(Channel channel) {
        return new GameCommandContext() {
            @Override
            public UUID playerId() {
                return channel.attr(PLAYER_ID).get();
            }

            @Override
            public void playerId(UUID playerId) {
                channel.attr(PLAYER_ID).set(playerId);
            }

            @Override
            public void reply(ServerMessage message) {
                sendToChannel(channel, message);
            }
        };
    }

    private void safeTick() {
        try {
            tick();
        } catch (Exception e) {
            log.error("Unhandled game-loop error tick={} queueSize={}", tick, commands.size(), e);
        }
    }

    private void tick() {
        GameCommandEnvelope env;
        while ((env = commands.poll()) != null)
            process(env);
        movePlayers();
        if (tick % 10 == 0)
            updateMobs();
        gameWorld.players().forEach(Player::tickStatuses);
        if (tick % properties.stateBroadcastEveryTicks() == 0)
            broadcastState();
        tick++;
    }

    private void process(GameCommandEnvelope env) {
        ClientMessage msg = env.message();
        GameCommandContext context = env.context();
        log.debug("Process command tick={} playerId={} type={} internalName={}", tick, context.playerId(), msg.type(),
                msg.name());

        if ("__DISCONNECT__".equals(msg.name())) {
            UUID playerId = msg.targetId() != null ? msg.targetId() : context.playerId();
            if (playerId == null || gameWorld.player(playerId).isEmpty()) {
                context.reply(ServerMessage.error("Player not found"));
                return;
            }

            gameWorld.removePlayer(msg.targetId());
            log.info("Player left world playerId={} tick={}", playerId, tick);

            context.playerId(null);
            context.reply(ServerMessage.ok("LEFT", "Player left world", Map.of("playerId", playerId)));
            return;
        }

        if (msg.type() == null) {
            context.reply(ServerMessage.error("type is required"));
            return;
        }

        switch (msg.type()) {
        case JOIN -> join(context, msg);
        case MOVE_TO -> withPlayer(context, p -> moveTo(context, p, msg));
        case ATTACK -> withPlayer(context, p -> attack(context, p, msg));
        case DEFEND -> withPlayer(context, p -> {
            p.defend(20); // ~1 sec with 50ms tick
            context.reply(ServerMessage.ok("DEFEND", "Defense enabled", Map.of("ticks", 20)));
        });
        case DODGE -> withPlayer(context, p -> {
            p.dodge(10); // ~0.5 sec
            context.reply(ServerMessage.ok("DODGE", "Dodge stance enabled", Map.of("ticks", 10)));
        });
        case STATE -> context.reply(ServerMessage.ok("STATE", "World snapshot", snapshot()));
        case PING -> context.reply(ServerMessage.ok("PONG", "pong", Map.of("tick", tick)));
        }
    }

    private void join(GameCommandContext context, ClientMessage msg) {
        if (context.playerId() != null) {
            context.reply(ServerMessage.error("Already joined"));
            return;
        }
        String name = msg.name() == null || msg.name().isBlank() ? "player" : msg.name().trim();
        Player p = gameWorld.addPlayer(name);
        log.info("Player joined playerId={} name={} position={}", p.getId(), p.getName(), p.getPosition());

        context.playerId(p.getId());
        context.reply(ServerMessage.ok("JOINED", "Joined world", playerView(p)));
    }

    private void moveTo(GameCommandContext context, Player player, ClientMessage msg) {
        if (msg.x() == null || msg.y() == null) {
            context.reply(ServerMessage.error("MOVE_TO requires x and y"));
            return;
        }
        Position target = new Position(msg.x(), msg.y());
        Deque<Position> path = pathFinder.find(gameWorld, player.getId(), player.getPosition(), target);
        if (path.isEmpty() && !player.getPosition().equals(target)) {
            context.reply(ServerMessage.error("No path to target"));
            return;
        }
        player.setPath(path);
        log.debug("Path accepted playerId={} from={} target={} steps={}", player.getId(), player.getPosition(), target,
                path.size());
        context.reply(
                ServerMessage.ok("PATH_ACCEPTED", "Movement started", Map.of("steps", path.size(), "target", target)));
    }

    private void attack(GameCommandContext context, Player player, ClientMessage msg) {
        if (msg.targetId() == null || msg.weapon() == null) {
            context.reply(ServerMessage.error("ATTACK requires targetId and weapon"));
            return;
        }
        Mob mob = gameWorld.mob(msg.targetId()).orElse(null);
        if (mob == null || !mob.isAlive()) {
            context.reply(ServerMessage.error("Target mob not found/alive"));
            return;
        }

        WeaponType weapon = msg.weapon();
        int distance = player.getPosition().manhattanDistance(mob.getPosition());
        if (distance > weapon.range()) {
            context.reply(ServerMessage.error("Target out of range"));
            return;
        }
        if (weapon == WeaponType.BOW && gameWorld.projectileBlocked(player.getPosition(), mob.getPosition())) {
            context.reply(ServerMessage.error("Projectile path is blocked"));
            return;
        }
        if (random.nextDouble() > weapon.accuracy()) {
            context.reply(ServerMessage.ok("ATTACK_RESULT", "Miss", Map.of("targetId", mob.getId())));
            return;
        }
        if (random.nextDouble() < mob.getType().dodgeChance()) {
            context.reply(ServerMessage.ok("ATTACK_RESULT", "Mob dodged", Map.of("targetId", mob.getId())));
            return;
        }

        mob.damage(weapon.damage());
        log.debug("Player attack playerId={} targetId={} weapon={} damage={} mobHp={}", player.getId(), mob.getId(),
                weapon, weapon.damage(), mob.getHp());
        if (!mob.isAlive()) {
            log.info("Mob killed playerId={} mobId={} mobType={} tick={}", player.getId(), mob.getId(), mob.getType(),
                    tick);
        }
        context.reply(ServerMessage.ok("ATTACK_RESULT", mob.isAlive() ? "Hit" : "Mob killed",
                Map.of("targetId", mob.getId(), "damage", weapon.damage(), "mobHp", mob.getHp())));
    }

    private void movePlayers() {
        for (Player p : gameWorld.players()) {
            if (!p.isAlive() || p.getPath().isEmpty())
                continue;
            Position next = p.getPath().peekFirst();
            if (!gameWorld.isBlockedFor(p.getId(), next)) {
                p.setPosition(p.getPath().pollFirst());
            } else {
                p.getPath().clear();
            }
        }
    }

    private void updateMobs() {
        List<Player> alivePlayers = gameWorld.players().stream().filter(Player::isAlive).toList();
        if (alivePlayers.isEmpty())
            return;

        for (Mob mob : gameWorld.mobs()) {
            if (!mob.isAlive())
                continue;
            Player target = alivePlayers.stream()
                    .min(Comparator.comparingInt(p -> p.getPosition().manhattanDistance(mob.getPosition())))
                    .orElse(null);
            if (target == null)
                continue;

            int distance = mob.getPosition().manhattanDistance(target.getPosition());
            if (distance <= mob.getType().attackRange()) {
                mobAttack(mob, target);
            } else {
                moveMobTowards(mob, target.getPosition());
            }
        }
    }

    private void mobAttack(Mob mob, Player player) {
        if (player.isDodging() && random.nextDouble() < 0.60)
            return;
        int damage = mob.getType().damage();
        if (player.isDefending())
            damage = Math.max(1, damage / 2);
        player.damage(damage);
        log.trace("Mob attack mobId={} playerId={} damage={} playerHp={}", mob.getId(), player.getId(), damage,
                player.getHp());
        if (!player.isAlive()) {
            log.info("Player died playerId={} killedByMobId={} mobType={} tick={}", player.getId(), mob.getId(),
                    mob.getType(), tick);
            player.setPath(new ArrayDeque<>());
        }
    }

    private void moveMobTowards(Mob mob, Position target) {
        List<Position> candidates = new ArrayList<>();
        int dx = Integer.compare(target.x(), mob.getPosition().x());
        int dy = Integer.compare(target.y(), mob.getPosition().y());
        if (dx != 0)
            candidates.add(new Position(mob.getPosition().x() + dx, mob.getPosition().y()));
        if (dy != 0)
            candidates.add(new Position(mob.getPosition().x(), mob.getPosition().y() + dy));
        Collections.shuffle(candidates, random);
        for (Position p : candidates) {
            if (gameWorld.inBounds(p) && !gameWorld.isBlockedFor(mob.getId(), p)) {
                mob.setPosition(p);
                return;
            }
        }
    }

    private void withPlayer(GameCommandContext context, java.util.function.Consumer<Player> action) {
        UUID id = context.playerId();
        if (id == null) {
            context.reply(ServerMessage.error("JOIN first"));
            return;
        }
        Player p = gameWorld.player(id).orElse(null);
        if (p == null) {
            context.reply(ServerMessage.error("Player not found"));
            return;
        }
        action.accept(p);
    }

    private Map<String, Object> snapshot() {
        return Map.of("size", Map.of("width", GameWorld.WIDTH, "height", GameWorld.HEIGHT), "players",
                gameWorld.players().stream().map(this::playerView).toList(), "mobs",
                gameWorld.mobs().stream()
                        .map(m -> Map.of("id", m.getId(), "type", m.getType(), "hp", m.getHp(), "position",
                                m.getPosition()))
                        .toList(),
                "obstacles",
                gameWorld.obstacles().stream().map(o -> Map.of("type", o.type(), "position", o.position())).toList(),
                "tick", tick);
    }

    private Map<String, Object> playerView(Player p) {
        return Map.of("id", p.getId(), "name", p.getName(), "hp", p.getHp(), "position", p.getPosition(), "defending",
                p.isDefending(), "dodging", p.isDodging());
    }

    private void broadcastState() {
        ServerMessage state = ServerMessage.ok("STATE", "World snapshot", snapshot());
        String json = toJson(state);

        log.trace("Broadcast STATE tick={} players={} mobs={} connections={}", tick, gameWorld.players().size(),
                gameWorld.mobs().size(), connections.channels().size());
        connections.channels().writeAndFlush(json + "\n");
    }

    private void sendToChannel(Channel channel, ServerMessage message) {
        channel.writeAndFlush(toJson(message) + "\n");
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("Cannot encode JSON valueType={}", value == null ? "null" : value.getClass().getName(), e);
            throw new IllegalStateException("Cannot encode JSON", e);
        }
    }
}
