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
package org.apache.dubbo.remoting.transport.dispatcher.all;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.ExecutionException;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.transport.dispatcher.ChannelEventRunnable;
import org.apache.dubbo.remoting.transport.dispatcher.ChannelEventRunnable.ChannelState;
import org.apache.dubbo.remoting.transport.dispatcher.WrappedChannelHandler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * 这一次是Dubbo的线程模型层，默认情况下线程模型是all，其对应的处理类是AllChannelHandler
 * 1、消费端发起TCP链接并完成后，NettyServer的connected方法会被激活
 * 2、该方法的执行是在Netty的I/O线程上执行的，为了可以及时释放I/O线程，Netty默认的线程模型为ALL
 * 3、所有消息都派发到Dubbo内部的业务线程池，这些消息包括请求事件、响应事件、连接事件、断开事件、心跳事件
 * 4、该类把I/O线程接收到的所有消息包装为ChannelEventRunnable任务并投递到了线程池里
 * 5、下一个Handler是DecodeHandler
 */
public class AllChannelHandler extends WrappedChannelHandler {

    public AllChannelHandler(ChannelHandler handler, URL url) {
        //父类WrappedChannelHandler构造函数
        super(handler, url);
    }

    //链接完成事件，交给业务线程池（biz）处理
    @Override
    public void connected(Channel channel) throws RemotingException {
        //获取业务线程池
        ExecutorService executor = getExecutorService();
        try {
            //将事件投递给业务线程池处理
            executor.execute(new ChannelEventRunnable(channel, handler, ChannelState.CONNECTED));
        } catch (Throwable t) {
            throw new ExecutionException("connect event", channel, getClass() + " error when process connected event .", t);
        }
    }

    //链接断开事件，交给业务线程池（biz）处理
    @Override
    public void disconnected(Channel channel) throws RemotingException {
        ExecutorService executor = getExecutorService();
        try {
            executor.execute(new ChannelEventRunnable(channel, handler, ChannelState.DISCONNECTED));
        } catch (Throwable t) {
            throw new ExecutionException("disconnect event", channel, getClass() + " error when process disconnected event .", t);
        }
    }

    //请求响应事件，交给业务线程池（biz）处理
    @Override
    public void received(Channel channel, Object message) throws RemotingException {
        ExecutorService executor = getPreferredExecutorService(message);
        try {
            executor.execute(new ChannelEventRunnable(channel, handler, ChannelState.RECEIVED, message));
        } catch (Throwable t) {
        	if(message instanceof Request && t instanceof RejectedExecutionException){
                sendFeedback(channel, (Request) message, t);
                return;
        	}
            throw new ExecutionException(message, channel, getClass() + " error when process received event .", t);
        }
    }

    //异常处理事件，交给业务线程池（biz）处理
    @Override
    public void caught(Channel channel, Throwable exception) throws RemotingException {
        ExecutorService executor = getExecutorService();
        try {
            executor.execute(new ChannelEventRunnable(channel, handler, ChannelState.CAUGHT, exception));
        } catch (Throwable t) {
            throw new ExecutionException("caught event", channel, getClass() + " error when process caught event .", t);
        }
    }
}
