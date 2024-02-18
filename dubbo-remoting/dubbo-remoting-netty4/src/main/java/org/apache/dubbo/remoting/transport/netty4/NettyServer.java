/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.remoting.transport.netty4;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ExecutorUtil;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.RemotingServer;
import org.apache.dubbo.remoting.transport.AbstractServer;
import org.apache.dubbo.remoting.transport.dispatcher.ChannelHandlers;
import org.apache.dubbo.remoting.utils.UrlUtils;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.dubbo.common.constants.CommonConstants.IO_THREADS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.KEEP_ALIVE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SSL_ENABLED_KEY;


/**
 * NettyServer.
 */

/**
 * 1、如果服务提供方的逻辑处理能迅速完成，并且不会发起新的I/O请求，那么直接在I/O线程上处理会更快，因为这样减少了线程池调度与上下文切换的开销
 * 但如果逻辑较慢，或者需要发起新的I/O请求，比如需要查询数据库，则I/O线程必须发请求到新的线程池进行处理，否则I/O线程会被阻塞，导致不能接收其他请求
 * 2、根据请求的消息类是被I/O线程处理还是被业务线程池处理，Dubbo提供了下面的几种线程模型
 * all：所有消息都派发到业务线程池，这些消息包括请求、响应、连接事件、断开事件、心跳事件等（biz）
 * direct：所有消息都不派发到业务线程池，全部在I/O线程上直接执行（worker）
 * messasge：只有请求响应消息派发到业务线程池（biz），其他消息如连接事件、断开事件、心跳事件等直接在I/O线程上执行（worker）
 * execution：只把请求类消息派发到业务线程池（biz），但是响应、连接事件、断开事件、心跳事件等消息直接在I/O线程上执行（worker）
 * connection：在I/O线程上将连接事件、断开事件放入队列，有序地逐个执行（worker），其他消息派发到业务线程池处理（biz）
 * 3、Dubbo线程模型为了尽早释放Netty的I/O线程，某些线程模型会把请求投递到线程池就行异步处理。这里的线程池ThreadPool也是一个扩展接口SPI
 * FixedThreadPool：创建一个具有固定个数线程的线程池
 * LimitedThreadPool：创建一个线程池，这个线程池中的线程个数随着需要量动态增加，但是数量不超过配置的阈值。另外，空闲线程不会被回收，会一直存在
 * EagerThreadPool：创建一个线程池，这个线程池中，当所有核心线程都处于忙碌状态时，将创建新的线程来执行新的任务，而不是把任务放入线程池阻塞队列
 * CachedThreadPool：创建一个自适应线程池，当线程空闲1分钟时，线程会被回收；当有新请求到来时，会创建新线程
 * 4、Netty中存在两种线程：boss线程和work线程
 * boss线程：accept客户端的链接，将接收的链接注册到一个work线程上，个数通常情况下服务端每绑定一个端口，开启一个boss线程
 * work线程：处理注册在其身上的链接上的各种io事件，个数通常是默认核数+1，一个work线程可以注册多个connection，
 * 一个connection只能注册在一个work线程上
 */
public class NettyServer extends AbstractServer implements RemotingServer {

    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);
    /**
     * the cache for alive worker channel.
     * <ip:port, dubbo channel>
     */
    private Map<String, Channel> channels;
    /**
     * netty server bootstrap.
     */
    private ServerBootstrap bootstrap;
    /**
     * the boss channel that receive connections and dispatch these to worker channel.
     */
	private io.netty.channel.Channel channel;

	//使用两级线程池，bossGroup和workerGroup线程组称为I/O线程
    //主要用来接收客户端链接请求
    private EventLoopGroup bossGroup;
    //完成TCP三次握手的链接分发给workerGroup来处理
    private EventLoopGroup workerGroup;

    public NettyServer(URL url, ChannelHandler handler) throws RemotingException {
        // you can customize name and type of client thread pool by THREAD_NAME_KEY and THREADPOOL_KEY in CommonConstants.
        // the handler will be wrapped: MultiMessageHandler->HeartbeatHandler->handler
        super(ExecutorUtil.setThreadName(url, SERVER_THREAD_POOL_NAME), ChannelHandlers.wrap(handler, url));
    }

    /**
     * Init and start netty server
     *
     * @throws Throwable
     */
    @Override
    protected void doOpen() throws Throwable {
        //创建ServerBootstrap
        bootstrap = new ServerBootstrap();
        //设置Netty的boss线程池
        bossGroup = createBossGroup();
        //设置Netty的worker线程池
        workerGroup = createWorkerGroup();
        //设置NettyServer，添加handler到管线
        final NettyServerHandler nettyServerHandler = createNettyServerHandler();
        channels = nettyServerHandler.getChannels();

        initServerBootstrap(nettyServerHandler);

        // bind
        //绑定本地端口，并启动监听服务
        ChannelFuture channelFuture = bootstrap.bind(getBindAddress());
        channelFuture.syncUninterruptibly();
        channel = channelFuture.channel();

    }

    protected EventLoopGroup createBossGroup() {
        return NettyEventLoopFactory.eventLoopGroup(1, "NettyServerBoss");
    }

    protected EventLoopGroup createWorkerGroup() {
        return NettyEventLoopFactory.eventLoopGroup(
                getUrl().getPositiveParameter(IO_THREADS_KEY, Constants.DEFAULT_IO_THREADS),
                "NettyServerWorker");
    }

    protected NettyServerHandler createNettyServerHandler() {
        return new NettyServerHandler(getUrl(), this);
    }

    protected void initServerBootstrap(NettyServerHandler nettyServerHandler) {
        boolean keepalive = getUrl().getParameter(KEEP_ALIVE_KEY, Boolean.FALSE);

        bootstrap.group(bossGroup, workerGroup)
                .channel(NettyEventLoopFactory.serverSocketChannelClass())
                .option(ChannelOption.SO_REUSEADDR, Boolean.TRUE)
                .childOption(ChannelOption.TCP_NODELAY, Boolean.TRUE)
                .childOption(ChannelOption.SO_KEEPALIVE, keepalive)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        // FIXME: should we use getTimeout()?
                        //添加handler到接收链接的管线
                        int idleTimeout = UrlUtils.getIdleTimeout(getUrl());
                        NettyCodecAdapter adapter = new NettyCodecAdapter(getCodec(), getUrl(), NettyServer.this);
                        if (getUrl().getParameter(SSL_ENABLED_KEY, false)) {
                            ch.pipeline().addLast("negotiation",
                                    SslHandlerInitializer.sslServerHandler(getUrl(), nettyServerHandler));
                        }
                        ch.pipeline()
                                .addLast("decoder", adapter.getDecoder())//解码器handler
                                .addLast("encoder", adapter.getEncoder())//编码器handler
                                .addLast("server-idle-handler", new IdleStateHandler(0, 0, idleTimeout, MILLISECONDS))//心跳检查handler
                                .addLast("handler", nettyServerHandler);//业务handler
                    }
                });
    }

    @Override
    protected void doClose() throws Throwable {
        try {
            if (channel != null) {
                // unbind.
                channel.close();
            }
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }
        try {
            Collection<Channel> channels = getChannels();
            if (channels != null && channels.size() > 0) {
                for (Channel channel : channels) {
                    try {
                        channel.close();
                    } catch (Throwable e) {
                        logger.warn(e.getMessage(), e);
                    }
                }
            }
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }
        try {
            if (bootstrap != null) {
                bossGroup.shutdownGracefully().syncUninterruptibly();
                workerGroup.shutdownGracefully().syncUninterruptibly();
            }
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }
        try {
            if (channels != null) {
                channels.clear();
            }
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }
    }

    @Override
    public Collection<Channel> getChannels() {
        Collection<Channel> chs = new ArrayList<>(this.channels.size());
        chs.addAll(this.channels.values());
        // check of connection status is unnecessary since we are using channels in NettyServerHandler
//        for (Channel channel : this.channels.values()) {
//            if (channel.isConnected()) {
//                chs.add(channel);
//            } else {
//                channels.remove(NetUtils.toAddressString(channel.getRemoteAddress()));
//            }
//        }
        return chs;
    }

    @Override
    public Channel getChannel(InetSocketAddress remoteAddress) {
        return channels.get(NetUtils.toAddressString(remoteAddress));
    }

    @Override
    public boolean canHandleIdle() {
        return true;
    }

    @Override
    public boolean isBound() {
        return channel.isActive();
    }

    protected EventLoopGroup getBossGroup() {
        return bossGroup;
    }

    protected EventLoopGroup getWorkerGroup() {
        return workerGroup;
    }

    protected ServerBootstrap getServerBootstrap() {
        return bootstrap;
    }

    protected io.netty.channel.Channel getBossChannel() {
        return channel;
    }

    protected Map<String, Channel> getServerChannels() {
        return channels;
    }
}
