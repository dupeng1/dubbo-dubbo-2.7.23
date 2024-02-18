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
package org.apache.dubbo.rpc;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.extension.SPI;

import static org.apache.dubbo.rpc.Constants.PROXY_KEY;

/**
 * ProxyFactory. (API/SPI, Singleton, ThreadSafe)
 */
/**
 * 服务代理层：
 * 1、该层主要时对服务消费端使用的接口进行代理，把本地调用透明地转换为远程调用（getProxy）
 * 2、对服务提供方的服务实现类进行代理，把服务实现类转换为Wrapper类，这是为了减少反射调用（getInvoker）
 * 3、Proxy层的SPI扩展接口为ProxyFactory，Dubbo提供的实现类主要有JavassistProxyFactory（默认使用）和JdkProxyFactory
 * 4、扩展接口ProxyFactory的适配器类为ProxyFactory$Adaptive，根据参数proxy来选择使用JdkProxyFactory
 * 还是使用JavassistProxyFactory做代理工厂
 */
@SPI("javassist")
public interface ProxyFactory {

    /**
     * create proxy.
     *
     * @param invoker
     * @return proxy
     */
    //创建代理
    @Adaptive({PROXY_KEY})
    <T> T getProxy(Invoker<T> invoker) throws RpcException;

    /**
     * create proxy.
     *
     * @param invoker
     * @return proxy
     */
    //创建代理
    @Adaptive({PROXY_KEY})
    <T> T getProxy(Invoker<T> invoker, boolean generic) throws RpcException;

    /**
     * create invoker.
     *
     * @param <T>
     * @param proxy
     * @param type
     * @param url
     * @return invoker
     */
    //创建调用程序
    //执行扩展接口ProxyFactory的适配器类ProxyFactory$Adaptive的getInvoker方法，其内部根据URL里的proxy的类型选择具体的代理工厂，
    //这里默认proxy类型为javassist，所以又调用了JavassistProxyFactory的getInvoker获取代理类
    @Adaptive({PROXY_KEY})
    <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) throws RpcException;

}