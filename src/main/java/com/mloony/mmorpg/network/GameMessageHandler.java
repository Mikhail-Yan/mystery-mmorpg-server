package com.mloony.mmorpg.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mloony.mmorpg.game.GameConnectionRegistry;
import com.mloony.mmorpg.game.GameEngine;
import com.mloony.mmorpg.protocol.ClientMessage;
import com.mloony.mmorpg.protocol.ServerMessage;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public class GameMessageHandler extends SimpleChannelInboundHandler<String> {
    private final ObjectMapper mapper;
    private final GameEngine engine;
    private final GameConnectionRegistry connections;

    public GameMessageHandler(ObjectMapper mapper, GameEngine engine, GameConnectionRegistry connections) {
        this.mapper = mapper;
        this.engine = engine;
        this.connections = connections;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        connections.add(ctx.channel());
        write(ctx, ServerMessage.ok("WELCOME", "Send JOIN first", null));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        try {
            ClientMessage command = mapper.readValue(msg, ClientMessage.class);
            engine.submit(ctx.channel(), command);
        } catch (Exception e) {
            write(ctx, ServerMessage.error("Invalid JSON: " + e.getMessage()));
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        connections.remove(ctx.channel());
        engine.disconnect(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    private void write(ChannelHandlerContext ctx, ServerMessage message) {
        try {
            ctx.writeAndFlush(mapper.writeValueAsString(message) + "\n");
        } catch (Exception e) {
            ctx.close();
        }
    }
}
