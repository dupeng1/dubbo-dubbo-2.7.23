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
package org.apache.dubbo.rpc.cluster;

import org.apache.dubbo.common.Node;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;

import java.util.List;

/**
 * Directory. (SPI, Prototype, ThreadSafe)
 * <p>
 * <a href="http://en.wikipedia.org/wiki/Directory_service">Directory Service</a>
 *
 * @see org.apache.dubbo.rpc.cluster.Cluster#join(Directory)
 */

/**
 * 1、代表了多个Invoker（对于消费端来说，每个Invoker代表了一个服务提供者），其内部维护着一个List，并且这个List内容是动态变化的。比如当服务提供者集群新增或者减少机器时
 * 服务注册中心就会推送当前服务提供者的地址列表，然后Directory中的List就会根据服务提供者地址列表相应变化
 * 2、在Dubbo中，Directory的实现有RegistryDirectory和StaticDirectory，其中前者管理的Invoker列表是根据服务注册中心的推送变化而变化的。而后者是当消费端使用了多注册中心
 * 时，其把所有服务注册中心的Invoker列表汇集到一个Invoker列表中
 * 3、消费端启动时何时构建RegistryDirectory？
 * 调用RegistryProtocol类的refer()方法创建的，由于RegistryProtocol是一个SPI，所以这里是通过其适配器类Protocol$Adaptive进行间接调用的，另外这里的
 * ProtocolListenerWrapper、QosProtocolWrapper和ProtocolFilterWrapper是对RegistryProtocol类的功能的增强
 * 4、RegistryDirectory管理的Invoker列表如何动态变化？
 * 创建完RegistryDirectory后，调用了其subscribe()方法，这里假设使用的服务注册中心为Zookeeper，这样就会去Zookeeper订阅需要调用的服务提供者的地址列表，然后添加一个监听器
 * （设置完监听器后，同步返回了订阅的服务地址列表、路由规则、配置信息，然后同步调用了RegistryDirectory的notify方法），当Zookeeper服务端发现服务提供者地址列表发生变化后，
 * 会将地址列表推送到服务消费端，然后zkClient会回调该监听器的notify方法
 * 5、每个需要消费的服务都被包装为ReferenceConfig，在应用启动时会调用每个服务对应的ReferenceConfig的get方法，然后会为每个服务创建一个自己的RegistryDirectory对象，
 * 每个RegistryDirectory管理该服务提供者的地址列表、路由规则、动态配置等信息，当服务提供者的信息发生变化时，RegistryDirectory会动态地得到变化通知，并自动更新
 * @param <T>
 */
public interface Directory<T> extends Node {

    /**
     * get service type.
     *
     * @return service type.
     */
    Class<T> getInterface();

    /**
     * list invokers.
     *
     * @return invokers
     */
    List<Invoker<T>> list(Invocation invocation) throws RpcException;

    List<Invoker<T>> getAllInvokers();

    URL getConsumerUrl();

    boolean isDestroyed();

    void discordAddresses();

}