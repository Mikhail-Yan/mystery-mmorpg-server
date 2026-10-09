package com.mloony.mmorpg.network;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import com.mloony.mmorpg.config.GameProperties;

@Component
public class NettyGameServer {
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
        boss = new NioEventLoopGroup(1);
        worker = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(boss, worker)
                .channel(NioServerSocketChannel.class)
                .childHandler(initializer)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true);

        serverChannel = bootstrap.bind(properties.host(), properties.port()).sync().channel();
        System.out.printf("Netty TCP server started on %s:%d%n", properties.host(), properties.port());
    }

    @PreDestroy
    void stop() {
        if (serverChannel != null) serverChannel.close();
        if (worker != null) worker.shutdownGracefully();
        if (boss != null) boss.shutdownGracefully();
    }
}
