package com.mloony.mmorpg.rest.model;

import java.util.UUID;

import com.mloony.mmorpg.game.model.WeaponType;

public record AttackRequest(UUID targetId, WeaponType weapon) {
}