package com.mloony.mmorpg.network;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.mloony.mmorpg.config.GameProperties;

@Component
public class NettyGameServer {
    private static final Logger log = LoggerFactory.getLogger(NettyGameServer.class);

    private final GameProperties properties;
    private final GameChannelInitializer initializer;
    private EventLoopGroup boss;
    private EventLoopGroup worker;
    private Channel serverChannel;

    public NettyGameServer(GameProperties properties, GameChannelInitializer initializer) {
        this.properties = properties;
        this.initializer = initializer;
    }

    @PostConstruct
    void start() throws InterruptedException {
        log.info("Starting Netty TCP server host={} port={}", properties.host(), properties.port());

        boss = new NioEventLoopGroup(1);
        worker = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap().group(boss, worker).channel(NioServerSocketChannel.class)
                .childHandler(initializer).childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        serverChannel = bootstrap.bind(properties.host(), properties.port()).sync().channel();
        log.info("Netty TCP server started host={} port={} channel={}", properties.host(), properties.port(),
                serverChannel.id().asShortText());
    }

    @PreDestroy
    void stop() {
        log.info("Stopping Netty TCP server");
        if (serverChannel != null)
            serverChannel.close();
        if (worker != null)
            worker.shutdownGracefully();
        if (boss != null)
            boss.shutdownGracefully();
        log.info("Netty TCP server stop requested");
    }
}
