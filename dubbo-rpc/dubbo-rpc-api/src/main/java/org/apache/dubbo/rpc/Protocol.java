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

import java.util.Collections;
import java.util.List;

/**
 * Protocol. (API/SPI, Singleton, ThreadSafe)
 */

/**
 * 远程调用层：封装RPC调用逻辑
 * 1、扩展接口Protocol，对应的实现有RegistryProtocol、DubboProtocol、InjvmProtocol
 * 2、Dubbo会使用动态编译技术为接口Protocol生成一个适配器类Protocol$Adaptive的对象实例，在Dubbo框架中需要使用Protocol的实例时，
 * 实际上就是使用Protocol$Adaptive的对象实例来获取具体的SPI实现类
 * 3、扩展点接口的类都含有@SPI注解，这里注解中的"dubbo"说明Protocol扩展接口SPI的默认实现是DubboProtocol
 */
@SPI("dubbo")
public interface Protocol {

    /**
     * Get default port when user doesn't config the port.
     *
     * @return default port
     */
    int getDefaultPort();

    /**
     * Export service for remote invocation: <br>
     * 1. Protocol should record request source address after receive a request:
     * RpcContext.getContext().setRemoteAddress();<br>
     * 2. export() must be idempotent, that is, there's no difference between invoking once and invoking twice when
     * export the same URL<br>
     * 3. Invoker instance is passed in by the framework, protocol needs not to care <br>
     *
     * @param <T>     Service type
     * @param invoker Service invoker
     * @return exporter reference for exported service, useful for unexport the service later
     * @throws RpcException thrown when error occurs during export the service, for example: port is occupied
     */
    //当执行protocol.export(wrapperInvoker)方法的时候，实际调用了protocol的适配器类Protocol$Adaptive的export方法
    //如果为远程服务暴露，则其内部根据URL中Protocol的类型为registry，会选择Protocol的实现类RegistryProtocol
    //如果为本地服务暴露，则其内部根据URL中Protocol的类型为injvm，会选择Protocol的实现类InjvmProtocol
    //但由于Dubbo SPI的扩展点使用了Wrapper自动增强，这里就使用了ProtocolFilterWrapper、ProtocolListenerWrapper、QosProtocolWrapper对其进行了增强
    //最后一层层调用才会调用到RegistryProtocol的export方法
    @Adaptive
    <T> Exporter<T> export(Invoker<T> invoker) throws RpcException;

    /**
     * Refer a remote service: <br>
     * 1. When user calls `invoke()` method of `Invoker` object which's returned from `refer()` call, the protocol
     * needs to correspondingly execute `invoke()` method of `Invoker` object <br>
     * 2. It's protocol's responsibility to implement `Invoker` which's returned from `refer()`. Generally speaking,
     * protocol sends remote request in the `Invoker` implementation. <br>
     * 3. When there's check=false set in URL, the implementation must not throw exception but try to recover when
     * connection fails.
     *
     * @param <T>  Service type
     * @param type Service class
     * @param url  URL address for the remote service
     * @return invoker service's local proxy
     * @throws RpcException when there's any error while connecting to the service provider
     */
    @Adaptive
    <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException;

    /**
     * Destroy protocol: <br>
     * 1. Cancel all services this protocol exports and refers <br>
     * 2. Release all occupied resources, for example: connection, port, etc. <br>
     * 3. Protocol can continue to export and refer new service even after it's destroyed.
     */
    void destroy();

    /**
     * Get all servers serving this protocol
     *
     * @return
     */
    default List<ProtocolServer> getServers() {
        return Collections.emptyList();
    }

}