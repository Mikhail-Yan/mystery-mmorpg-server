package com.mloony.mmorpg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "game")
public record GameProperties(String host, int port, long tickMs, int stateBroadcastEveryTicks) {
    public GameProperties {
        if (host == null || host.isBlank())
            host = "0.0.0.0";
        if (port == 0)
            port = 7777;
        if (tickMs == 0)
            tickMs = 50;
        if (stateBroadcastEveryTicks == 0)
            stateBroadcastEveryTicks = 5;
    }
}
