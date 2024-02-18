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
package org.apache.dubbo.rpc.proxy.javassist;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.bytecode.Proxy;
import org.apache.dubbo.common.bytecode.Wrapper;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.proxy.AbstractProxyFactory;
import org.apache.dubbo.rpc.proxy.AbstractProxyInvoker;
import org.apache.dubbo.rpc.proxy.InvokerInvocationHandler;

/**
 * JavassistRpcProxyFactory
 */
/**
 * 默认使用，服务提供方实现类到Invoker的转换，为每个服务提供者实现类生成了Wrapper类，减少反射调用开销
 */
public class JavassistProxyFactory extends AbstractProxyFactory {

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Invoker<T> invoker, Class<?>[] interfaces) {
        //InvokerInvocationHandler为具体拦截器
        //ReferenceConfig的get()方法返回一个代理类，并且方法拦截器为InvokerInvocationHandler
        //所以当消费方调用了服务的接口方法后会被InvokerInvocationHandler拦截
        return (T) Proxy.getProxy(interfaces).newInstance(new InvokerInvocationHandler(invoker));
    }

    /**
     * 获取Invoker
     * @param proxy 扩展接口实现
     * @param type  扩展接口
     * @param url   URL
     * @param <T>
     * @return
     */
    @Override
    public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) {
        // TODO Wrapper cannot handle this scenario correctly: the classname contains '$'
        //把服务实现类转换为Wrapper类，是为了减少反射调用
        final Wrapper wrapper = Wrapper.getWrapper(proxy.getClass().getName().indexOf('$') < 0 ? proxy.getClass() : type);
        //返回AbstractProxyInvoker对象，内部重写doInvoke方法，并委托给Wrapper，实现具体功能
        return new AbstractProxyInvoker<T>(proxy, type, url) {
            //AbstractProxyInvoker委托Wrapper类执行具体逻辑，后者通过调用服务提供方接口的实现类来执行本地服务
            @Override
            protected Object doInvoke(T proxy, String methodName,
                                      Class<?>[] parameterTypes,
                                      Object[] arguments) throws Throwable {
                return wrapper.invokeMethod(proxy, methodName, parameterTypes, arguments);
            }
        };
    }

}
