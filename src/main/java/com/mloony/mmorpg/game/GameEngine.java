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
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

@Component
public class GameEngine {
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
        gameLoop.scheduleAtFixedRate(this::safeTick, 0, properties.tickMs(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        gameLoop.shutdownNow();
    }

    public void submit(Channel channel, ClientMessage message) {
        commands.offer(new GameCommandEnvelope(channel, message));
    }

    public void disconnect(Channel channel) {
        UUID playerId = channel.attr(PLAYER_ID).getAndSet(null);
        if (playerId != null)
            commands.offer(new GameCommandEnvelope(channel,
                    new ClientMessage(null, "__DISCONNECT__", null, null, playerId, null)));
    }

    private void safeTick() {
        try {
            tick();
        } catch (Exception e) {
            e.printStackTrace();
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
        Channel channel = env.channel();

        if ("__DISCONNECT__".equals(msg.name())) {
            gameWorld.removePlayer(msg.targetId());
            return;
        }

        if (msg.type() == null) {
            send(channel, ServerMessage.error("type is required"));
            return;
        }

        switch (msg.type()) {
        case JOIN -> join(channel, msg);
        case MOVE_TO -> withPlayer(channel, p -> moveTo(channel, p, msg));
        case ATTACK -> withPlayer(channel, p -> attack(channel, p, msg));
        case DEFEND -> withPlayer(channel, p -> {
            p.defend(20); // ~1 sec with 50ms tick
            send(channel, ServerMessage.ok("DEFEND", "Defense enabled", Map.of("ticks", 20)));
        });
        case DODGE -> withPlayer(channel, p -> {
            p.dodge(10); // ~0.5 sec
            send(channel, ServerMessage.ok("DODGE", "Dodge stance enabled", Map.of("ticks", 10)));
        });
        case STATE -> send(channel, ServerMessage.ok("STATE", "World snapshot", snapshot()));
        case PING -> send(channel, ServerMessage.ok("PONG", "pong", Map.of("tick", tick)));
        }
    }

    private void join(Channel channel, ClientMessage msg) {
        if (channel.attr(PLAYER_ID).get() != null) {
            send(channel, ServerMessage.error("Already joined"));
            return;
        }
        String name = msg.name() == null || msg.name().isBlank() ? "player" : msg.name().trim();
        Player p = gameWorld.addPlayer(name);
        channel.attr(PLAYER_ID).set(p.getId());
        send(channel, ServerMessage.ok("JOINED", "Joined world", playerView(p)));
    }

    private void moveTo(Channel channel, Player player, ClientMessage msg) {
        if (msg.x() == null || msg.y() == null) {
            send(channel, ServerMessage.error("MOVE_TO requires x and y"));
            return;
        }
        Position target = new Position(msg.x(), msg.y());
        Deque<Position> path = pathFinder.find(gameWorld, player.getId(), player.getPosition(), target);
        if (path.isEmpty() && !player.getPosition().equals(target)) {
            send(channel, ServerMessage.error("No path to target"));
            return;
        }
        player.setPath(path);
        send(channel,
                ServerMessage.ok("PATH_ACCEPTED", "Movement started", Map.of("steps", path.size(), "target", target)));
    }

    private void attack(Channel channel, Player player, ClientMessage msg) {
        if (msg.targetId() == null || msg.weapon() == null) {
            send(channel, ServerMessage.error("ATTACK requires targetId and weapon"));
            return;
        }
        Mob mob = gameWorld.mob(msg.targetId()).orElse(null);
        if (mob == null || !mob.isAlive()) {
            send(channel, ServerMessage.error("Target mob not found/alive"));
            return;
        }

        WeaponType weapon = msg.weapon();
        int distance = player.getPosition().manhattanDistance(mob.getPosition());
        if (distance > weapon.range()) {
            send(channel, ServerMessage.error("Target out of range"));
            return;
        }
        if (weapon == WeaponType.BOW && gameWorld.projectileBlocked(player.getPosition(), mob.getPosition())) {
            send(channel, ServerMessage.error("Projectile path is blocked"));
            return;
        }
        if (random.nextDouble() > weapon.accuracy()) {
            send(channel, ServerMessage.ok("ATTACK_RESULT", "Miss", Map.of("targetId", mob.getId())));
            return;
        }
        if (random.nextDouble() < mob.getType().dodgeChance()) {
            send(channel, ServerMessage.ok("ATTACK_RESULT", "Mob dodged", Map.of("targetId", mob.getId())));
            return;
        }

        mob.damage(weapon.damage());
        send(channel, ServerMessage.ok("ATTACK_RESULT", mob.isAlive() ? "Hit" : "Mob killed",
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
        if (!player.isAlive()) {
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

    private void withPlayer(Channel channel, java.util.function.Consumer<Player> action) {
        UUID id = channel.attr(PLAYER_ID).get();
        if (id == null) {
            send(channel, ServerMessage.error("JOIN first"));
            return;
        }
        Player p = gameWorld.player(id).orElse(null);
        if (p == null) {
            send(channel, ServerMessage.error("Player not found"));
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
        connections.channels().writeAndFlush(json + "\n");
    }

    private void send(Channel channel, ServerMessage message) {
        channel.writeAndFlush(toJson(message) + "\n");
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot encode JSON", e);
        }
    }
}
