# Mystery MMORPG server (Java + Spring Boot + Maven + Netty TCP + JSON)

Минимальный игровой сервер с авторитетным состоянием мира.

## Возможности

- поле 100x100 клеток;
- TCP сервер на Netty, порт 7777;
- JSON Lines протокол: один JSON = одна строка `\n`;
- игроки подключаются командой JOIN;
- MOVE_TO строит BFS-путь по 4 направлениям и двигает игрока по 1 клетке за tick;
- типы мобов: GOBLIN, ORC, ARCHER;
- типы препятствий: WALL, ROCK, TREE, WATER;
- оружие: SWORD, SPEAR, BOW;
- атака с дальностью, точностью и уклонением моба;
- DEFEND снижает урон мобов в 2 раза;
- DODGE временно дает 60% шанс уклониться от атаки моба;
- простой AI мобов;
- состояние мира рассылается всем клиентам каждые несколько tick'ов;
- игровое состояние изменяется только из одного `game-loop` потока.

## Запуск

```bash
mvn spring-boot:run
```

или

```bash
mvn clean package
java -jar target/minimal-mmorpg-server-0.0.1-SNAPSHOT.jar
```

## Подключение

```bash
nc localhost 7777
```

Каждая команда должна завершаться переводом строки.

### JOIN

```json
{"type":"JOIN","name":"Mikhail"}
```

### Получить состояние

```json
{"type":"STATE"}
```

### Идти в клетку

```json
{"type":"MOVE_TO","x":20,"y":30}
```

### Атаковать моба мечом

```json
{"type":"ATTACK","targetId":"PUT-MOB-UUID-HERE","weapon":"SWORD"}
```

Варианты оружия: `SWORD`, `SPEAR`, `BOW`.

### Защита

```json
{"type":"DEFEND"}
```

### Уклонение

```json
{"type":"DODGE"}
```

### Ping

```json
{"type":"PING"}
```

## Архитектура

```text
TCP Client
   |
   v
Netty
  - LineBasedFrameDecoder
  - StringDecoder/StringEncoder
  - GameMessageHandler
   |
   v
BlockingQueue<GameCommandEnvelope>
   |
   v
Single-thread GameEngine, tick = 50ms
   |
   +-- PathFinder
   +-- Combat
   +-- Mob AI
   +-- Player movement
   |
   v
GameWorld 100x100
```

Netty-потоки не изменяют `GameWorld` напрямую. Они только складывают команды в очередь. Это убирает большую часть проблем с гонками при изменении игрового состояния.

## Чего нет

- нет БД;
- нет аккаунтов/аутентификации;
- нет инвентаря;
- нет PvP;
- нет persistence состояния;
- line-of-sight для лука реализован только для прямой горизонтальной/вертикальной линии;
- мобы после смерти не respawn'ятся;
- нет cooldown атак;
- клиентам рассылается полный snapshot, а не delta-events.

Это MVP-скелет, который удобно дальше развивать в сторону PostgreSQL, inventory/equipment, зон мира, Kafka и полноценного бинарного протокола.
