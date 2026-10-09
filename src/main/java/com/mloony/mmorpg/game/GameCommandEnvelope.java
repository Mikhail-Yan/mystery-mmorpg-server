package com.mloony.mmorpg.game;

import com.mloony.mmorpg.protocol.ClientMessage;

import io.netty.channel.Channel;

public record GameCommandEnvelope(Channel channel, ClientMessage message) {
}
