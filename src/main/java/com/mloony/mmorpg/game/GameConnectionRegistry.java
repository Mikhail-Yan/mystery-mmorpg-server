package com.mloony.mmorpg.game;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.springframework.stereotype.Component;

@Component
public class GameConnectionRegistry {
    private final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    public void add(Channel channel) {
        channels.add(channel);
    }

    public void remove(Channel channel) {
        channels.remove(channel);
    }

    public ChannelGroup channels() {
        return channels;
    }
}
