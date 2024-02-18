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
package org.apache.dubbo.remoting.exchange.support.header;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.ExecutionException;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.exchange.ExchangeChannel;
import org.apache.dubbo.remoting.exchange.ExchangeHandler;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;
import org.apache.dubbo.remoting.exchange.support.DefaultFuture;
import org.apache.dubbo.remoting.transport.ChannelHandlerDelegate;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletionStage;

import static org.apache.dubbo.common.constants.CommonConstants.READONLY_EVENT;


/**
 * ExchangeReceiver
 */

/**
 * 1、基于消息头部（Header）的信息交换处理器实现类
 * 2、实现了Request和Response的概念，当接收到received请求后，将请求转为reply
 * 3、对于双向通信，HeaderExchangeHandler首先向后进行调用，得到调用结果，然后将调用结果封装到Response对象中，最后再将该对象返回给服务消费端
 * 如果请求不合法，或者调用失败，则将错误信息封装到Response对象中，并返回给服务消费方
 * 4、下一个handler是DubboProtocol#requestHandler
 */
public class HeaderExchangeHandler implements ChannelHandlerDelegate {

    protected static final Logger logger = LoggerFactory.getLogger(HeaderExchangeHandler.class);

    private final ExchangeHandler handler;

    public HeaderExchangeHandler(ExchangeHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("handler == null");
        }
        this.handler = handler;
    }

    //处理返回消息
    static void handleResponse(Channel channel, Response response) throws RemotingException {
        if (response != null && !response.isHeartbeat()) {
            DefaultFuture.received(channel, response);
        }
    }

    //是否是客户端
    private static boolean isClientSide(Channel channel) {
        InetSocketAddress address = channel.getRemoteAddress();
        URL url = channel.getUrl();
        return url.getPort() == address.getPort() &&
                NetUtils.filterLocalHost(url.getIp())
                        .equals(NetUtils.filterLocalHost(address.getAddress().getHostAddress()));
    }

    void handlerEvent(Channel channel, Request req) throws RemotingException {
        //如果是只读事件，设置通道为只读
        if (req.getData() != null && req.getData().equals(READONLY_EVENT)) {
            channel.setAttribute(Constants.CHANNEL_ATTRIBUTE_READONLY_KEY, Boolean.TRUE);
        }
    }

    void handleRequest(final ExchangeChannel channel, Request req) throws RemotingException {
        Response res = new Response(req.getId(), req.getVersion());
        //检测请求是否合法，不合法则返回状态码为BAD_REQUEST的响应
        if (req.isBroken()) {
            Object data = req.getData();

            String msg;
            if (data == null) {
                msg = null;
            } else if (data instanceof Throwable) {
                msg = StringUtils.toString((Throwable) data);
            } else {
                msg = data.toString();
            }
            res.setErrorMessage("Fail to decode request due to: " + msg);
            // 设置 BAD_REQUEST 状态
            res.setStatus(Response.BAD_REQUEST);

            channel.send(res);
            return;
        }
        // find handler by message class.
        //获取data字段值，也就是RpcInvocation对象
        Object msg = req.getData();
        try {
            //调用DubboProtocol的reply方法，委托给DubboProtocol的reply
            CompletionStage<Object> future = handler.reply(channel, msg);
            // 等返回结果后异步回调，等执行完毕拿到结果再把结果写回消费端
            future.whenComplete((appResult, t) -> {
                try {
                    if (t == null) {
                        res.setStatus(Response.OK);
                        res.setResult(appResult);
                    } else {
                        res.setStatus(Response.SERVICE_ERROR);
                        res.setErrorMessage(StringUtils.toString(t));
                    }
                    channel.send(res);
                } catch (RemotingException e) {
                    logger.warn("Send result to consumer failed, channel is " + channel + ", msg is " + e);
                }
            });
        } catch (Throwable e) {
            res.setStatus(Response.SERVICE_ERROR);
            res.setErrorMessage(StringUtils.toString(e));
            channel.send(res);
        }
    }

    @Override
    public void connected(Channel channel) throws RemotingException {
        ExchangeChannel exchangeChannel = HeaderExchangeChannel.getOrAddChannel(channel);
        handler.connected(exchangeChannel);
    }

    @Override
    public void disconnected(Channel channel) throws RemotingException {
        ExchangeChannel exchangeChannel = HeaderExchangeChannel.getOrAddChannel(channel);
        try {
            handler.disconnected(exchangeChannel);
        } finally {
            DefaultFuture.closeChannel(channel);
            HeaderExchangeChannel.removeChannel(channel);
        }
    }

    @Override
    public void sent(Channel channel, Object message) throws RemotingException {
        Throwable exception = null;
        try {
            ExchangeChannel exchangeChannel = HeaderExchangeChannel.getOrAddChannel(channel);
            handler.sent(exchangeChannel, message);
        } catch (Throwable t) {
            exception = t;
            HeaderExchangeChannel.removeChannelIfDisconnected(channel);
        }
        if (message instanceof Request) {
            Request request = (Request) message;
            DefaultFuture.sent(channel, request);
        }
        if (exception != null) {
            if (exception instanceof RuntimeException) {
                throw (RuntimeException) exception;
            } else if (exception instanceof RemotingException) {
                throw (RemotingException) exception;
            } else {
                throw new RemotingException(channel.getLocalAddress(), channel.getRemoteAddress(),
                        exception.getMessage(), exception);
            }
        }
    }

    @Override
    public void received(Channel channel, Object message) throws RemotingException {
        final ExchangeChannel exchangeChannel = HeaderExchangeChannel.getOrAddChannel(channel);
        //如果是请求类型消息
        if (message instanceof Request) {
            // handle request.
            // 处理请求
            Request request = (Request) message;
            if (request.isEvent()) {
                //处理事件对象
                handlerEvent(channel, request);
            } else {
                //需要有返回值的请求，即需要响应，toway，此时为双向通信，需要获取调用结果并返回给服务消费端
                //无论需要有返回值，还是不需要有返回值，最终都是DubboProtocol类的reply方法来执行具体的服务
                if (request.isTwoWay()) {
                    handleRequest(exchangeChannel, request);
                } else {
                    //不需要有返回值的请求，oneway，此时为单向通信，仅向后调用指定服务即可，无需返回调用结果
                    handler.received(exchangeChannel, request.getData());
                }
            }
        }
        //响应消息，服务消费方会执行此处逻辑，消费端发起请求后得到的Response
        else if (message instanceof Response) {
            handleResponse(channel, (Response) message);
        }
        //处理 String
        else if (message instanceof String) {
            //客户端侧，不支持String
            if (isClientSide(channel)) {
                Exception e = new Exception("Dubbo client can not supported string message: " + message + " in channel: " + channel + ", url: " + channel.getUrl());
                logger.error(e.getMessage(), e);
            }
            //服务端侧，目前是telnet命令
            else {
                String echo = handler.telnet(channel, (String) message);
                if (echo != null && echo.length() > 0) {
                    channel.send(echo);
                }
            }
        }
        //提交给装饰的"handler"继续处理
        else {
            handler.received(exchangeChannel, message);
        }
    }

    @Override
    public void caught(Channel channel, Throwable exception) throws RemotingException {
        if (exception instanceof ExecutionException) {
            ExecutionException e = (ExecutionException) exception;
            Object msg = e.getRequest();
            if (msg instanceof Request) {
                Request req = (Request) msg;
                if (req.isTwoWay() && !req.isHeartbeat()) {
                    Response res = new Response(req.getId(), req.getVersion());
                    res.setStatus(Response.SERVER_ERROR);
                    res.setErrorMessage(StringUtils.toString(e));
                    channel.send(res);
                    return;
                }
            }
        }
        ExchangeChannel exchangeChannel = HeaderExchangeChannel.getOrAddChannel(channel);
        try {
            handler.caught(exchangeChannel, exception);
        } finally {
            HeaderExchangeChannel.removeChannelIfDisconnected(channel);
        }
    }

    @Override
    public ChannelHandler getHandler() {
        if (handler instanceof ChannelHandlerDelegate) {
            return ((ChannelHandlerDelegate) handler).getHandler();
        } else {
            return handler;
        }
    }
}
