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

или:

```bash
mvn clean package
java -jar target/minimal-mmorpg-server-0.0.1-SNAPSHOT.jar
```

Подключение:

```bash
nc localhost 7777
```

## Тесты

`WorldGeneratorTest` проверяет:

- одинаковый seed -> одинаковый terrain, biome, obstacles и resources;
- центральный spawn и дорожную сеть;
- `MOUNTAIN/HILL -> MOUNTAINS`;
- `MUD -> SWAMP`;
- корректность ресурсов по правилам биома;
- отсутствие препятствий на дорогах;
- спавн мобов только из таблицы фауны текущего биома.

## REST API

Кроме Netty TCP на `7777`, сервер поднимает Spring MVC REST API на `8888`.
Оба транспорта используют **один и тот же `GameEngine` и одну очередь команд**:

```text
Netty TCP :7777 ----\
                      -> GameCommandEnvelope -> game-loop -> GameWorld
REST HTTP :8888 ----/
```

REST controller не изменяет `GameWorld` из HTTP-потока напрямую. Он формирует тот же `ClientMessage`, кладет его в очередь `GameEngine` и ожидает `ServerMessage`. Поэтому инвариант "состояние мира меняет только один game-loop thread" сохраняется.

Контроллер:

```text
com.example.mmorpg.rest.GameRestController
```

### REST endpoints

| HTTP | Endpoint | Эквивалент Netty команды |
|---|---|---|
| POST | /api/game/players | JOIN |
| POST | /api/game/players/{playerId}/move | MOVE_TO |
| POST | /api/game/players/{playerId}/attack | ATTACK |
| POST | /api/game/players/{playerId}/defend | DEFEND |
| POST | /api/game/players/{playerId}/dodge | DODGE |
| GET | /api/game/map | MAP |
| GET | /api/game/state | STATE |
| GET | /api/game/ping | PING |
| DELETE | /api/game/players/{playerId} | disconnect/leave |
| POST | /api/game/command?playerId=... | универсальный ClientMessage |

### JOIN

```bash
curl -X POST http://localhost:8888/api/game/players \
  -H "Content-Type: application/json" \
  -d '{"name":"Mikhail"}'
```

Ответ:

```json
{
  "type": "JOINED",
  "message": "Joined world",
  "data": {
    "id": "PLAYER_UUID",
    "name": "Mikhail",
    "hp": 100,
    "position": {"x": 50, "y": 50}
  }
}
```

Для REST MVP UUID игрока используется как идентификатор игровой сессии и передается в path/query параметре.

### MOVE_TO

```bash
curl -X POST http://localhost:8888/api/game/players/PLAYER_UUID/move \
  -H "Content-Type: application/json" \
  -d '{"x":52,"y":50}'
```

### ATTACK

```bash
curl -X POST http://localhost:8888/api/game/players/PLAYER_UUID/attack \
  -H "Content-Type: application/json" \
  -d '{"targetId":"MOB_UUID","weapon":"SWORD"}'
```

Доступные значения `weapon`:

```text
SWORD
SPEAR
BOW
```

### DEFEND / DODGE

```bash
curl -X POST http://localhost:8888/api/game/players/PLAYER_UUID/defend
curl -X POST http://localhost:8888/api/game/players/PLAYER_UUID/dodge
```

### MAP / STATE / PING

```bash
curl http://localhost:8888/api/game/map
curl http://localhost:8888/api/game/state
curl http://localhost:8888/api/game/ping
```

REST-клиент не получает push-рассылку `STATE`, как постоянное Netty-соединение. Для REST состояние читается через `GET /api/game/state`.

### Универсальный ClientMessage endpoint

Если нужно тестировать тот же JSON, который отправляется по Netty TCP:

```bash
curl -X POST 'http://localhost:8888/api/game/command?playerId=PLAYER_UUID' \
  -H "Content-Type: application/json" \
  -d '{"type":"MOVE_TO","x":60,"y":40}'
```

Для `JOIN`, `MAP`, `STATE`, `PING` параметр `playerId` не нужен.

Примеры запросов `rest-examples.http`.

### Порты

```yaml
server:
  port: 8888       # Spring REST

game:
  port: 7777       # Netty TCP
```
