package com.mloony.mmorpg.protocol;

import java.util.UUID;

import com.mloony.mmorpg.game.model.WeaponType;

/**
 * Клиентские сообщения разных типов JOIN, MOVE_TO, ATTACK, DEFEND, DODGE, STATE, PING
 */
public record ClientMessage(CommandType type, String name, Integer x, Integer y, UUID targetId, WeaponType weapon) {
}
