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
package org.apache.dubbo.registry;

import org.apache.dubbo.common.Node;
import org.apache.dubbo.common.URL;

/**
 * Registry. (SPI, Prototype, ThreadSafe)
 *
 * @see org.apache.dubbo.registry.RegistryFactory#getRegistry(URL)
 * @see org.apache.dubbo.registry.support.AbstractRegistry
 */

/**
 * 服务注册中心：主要封装服务地址的注册与发现逻辑
 * 1、服务提供者启动时会把服务注册到服务注册中心
 * 2、消费者启动时会去服务注册中心获取服务提供者的地址列表
 * 3、扩展接口Registry、扩展接口RegistryFactory、扩展接口Directory
 * 4、扩展接口Registry的适配器类Registry$Adaptive，根据参数register来决定使用ZookeeperRegistry、RedisRegistry、MulticastRegistry、DubboRegistry
 */
public interface Registry extends Node, RegistryService {
    default void reExportRegister(URL url) {
        register(url);
    }

    default void reExportUnregister(URL url) {
        unregister(url);
    }
}