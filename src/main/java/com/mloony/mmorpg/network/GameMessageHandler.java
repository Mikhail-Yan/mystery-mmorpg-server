package com.mloony.mmorpg.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mloony.mmorpg.game.GameConnectionRegistry;
import com.mloony.mmorpg.game.GameEngine;
import com.mloony.mmorpg.protocol.ClientMessage;
import com.mloony.mmorpg.protocol.ServerMessage;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public class GameMessageHandler extends SimpleChannelInboundHandler<String> {
    private static final Logger log = LoggerFactory.getLogger(GameMessageHandler.class);

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
        log.info("TCP client connected channel={} remote={}", ctx.channel().id().asShortText(),
                ctx.channel().remoteAddress());
        write(ctx, ServerMessage.ok("WELCOME", "Send JOIN first", null));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        try {
            ClientMessage command = mapper.readValue(msg, ClientMessage.class);
            log.debug("TCP command channel={} playerId={} type={}", ctx.channel().id().asShortText(),
                    ctx.channel().attr(GameEngine.PLAYER_ID).get(), command.type());
            engine.submit(ctx.channel(), command);
        } catch (Exception e) {
            log.warn("Invalid TCP JSON channel={} remote={} error={}", ctx.channel().id().asShortText(),
                    ctx.channel().remoteAddress(), e.getMessage());
            write(ctx, ServerMessage.error("Invalid JSON: " + e.getMessage()));
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("TCP client disconnected channel={} remote={} playerId={}", ctx.channel().id().asShortText(),
                ctx.channel().remoteAddress(), ctx.channel().attr(GameEngine.PLAYER_ID).get());
        connections.remove(ctx.channel());
        engine.disconnect(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("TCP channel error channel={} remote={}", ctx.channel().id().asShortText(),
                ctx.channel().remoteAddress(), cause);
        ctx.close();
    }

    private void write(ChannelHandlerContext ctx, ServerMessage message) {
        try {
            ctx.writeAndFlush(mapper.writeValueAsString(message) + "\n");
        } catch (Exception e) {
            log.warn("Cannot serialize/send TCP response channel={}", ctx.channel().id().asShortText(), e);
            ctx.close();
        }
    }
}
