package com.mloony.mmorpg.game;

import com.mloony.mmorpg.protocol.ClientMessage;

public record GameCommandEnvelope(
        GameCommandContext context,
        ClientMessage message
) {
}
