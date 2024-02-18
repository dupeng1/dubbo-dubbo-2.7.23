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
package org.apache.dubbo.remoting;

import org.apache.dubbo.common.extension.SPI;


/**
 * ChannelHandler. (API, Prototype, ThreadSafe)
 *
 * @see org.apache.dubbo.remoting.Transporter#bind(org.apache.dubbo.common.URL, ChannelHandler)
 * @see org.apache.dubbo.remoting.Transporter#connect(org.apache.dubbo.common.URL, ChannelHandler)
 */

/**
 * 当服务消费者调用服务提供者的服务时，提供者用来处理各个消息事件
 * 在Dubbo的实际调用过程中，当消费者调用服务提供者的服务时会发送Netty消息，服务提供者接收到Netty消息会按照下图的流程一层一层执行
 * 最终的消息交由DubboProtocol的requestHandler来处理
 * 1、MultiMessageHandler：处理MultiMessage消息
 * 2、HeartbeatHandler：处理心跳消息，增加时间戳
 * 3、AllChannelHandler：这里实际根据线程模型，选择合适的handler，并非一定是AllChannelHandler
 * 4、DecodeHandler：解码器，recieve事件解码消息
 * 5、HeaderExchangeHandler：实现了数据接收过来数据的分发，能够根据接收过来的数据类型不同作出不同处理
 * 6、DubboProtocol#requestHandler：进行事件处理
 */
@SPI
public interface ChannelHandler {

    /**
     * on channel connected.
     *
     * @param channel channel.
     */
    void connected(Channel channel) throws RemotingException;

    /**
     * on channel disconnected.
     *
     * @param channel channel.
     */
    void disconnected(Channel channel) throws RemotingException;

    /**
     * on message sent.
     *
     * @param channel channel.
     * @param message message.
     */
    void sent(Channel channel, Object message) throws RemotingException;

    /**
     * on message received.
     *
     * @param channel channel.
     * @param message message.
     */
    void received(Channel channel, Object message) throws RemotingException;

    /**
     * on exception caught.
     *
     * @param channel   channel.
     * @param exception exception.
     */
    void caught(Channel channel, Throwable exception) throws RemotingException;

}