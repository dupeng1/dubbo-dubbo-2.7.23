package org.apache.dubbo.config.demo;

import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.rpc.RpcContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;


/**
 * 1、把远端服务转换为Invoker，Protocol的refer，Protocol的默认实现DubboProtocol
 * 2、Invoker为DubboInvoker
 * 3、把Invoker转换为客户端需要的接口（如GreetingService），ProxyFactory的getProxy，ProxyFactory的默认实现JavassistProxyFactory
 * 它主要使用代理对服务接口的调用转换为对Invoker的调用
 */
public class ReferenceConfigTest {
    public static void main(String[] args) throws Exception{
    }
    
    public void testSync() {
        ReferenceConfig<GreetingService> referenceConfig = new ReferenceConfig<GreetingService>();
          //设置应用程序信息
          referenceConfig.setApplication(new ApplicationConfig("first-dubbo-consumer"));
          //设置服务注册中心
          RegistryConfig registryConfig = new RegistryConfig("zookeeper://127.0.0.1:2181");
          referenceConfig.setRegistry(registryConfig);
          //设置服务接口和超时时间
          referenceConfig.setInterface(GreetingService.class);
          referenceConfig.setTimeout(5000);
          //设置自定义负载均衡策略与集群容错策略
          referenceConfig.setLoadbalance("myLoadBalance");
          referenceConfig.setCluster("myBroadcast");
          //设置服务分组与版本
          referenceConfig.setVersion("1.0.0");
          referenceConfig.setGroup("dubbo");
          //引用服务
          GreetingService greetingService = referenceConfig.get();
          //设置隐式参数
          RpcContext.getContext().setAttachment("company", "alibaba");
          //调用服务
          greetingService.sayHello("world");
    }

    public void testAsync() throws Exception{
        ReferenceConfig<GreetingService> referenceConfig = new ReferenceConfig<GreetingService>();
        //设置应用程序信息
        referenceConfig.setApplication(new ApplicationConfig("first-dubbo-consumer"));
        //设置服务注册中心
        RegistryConfig registryConfig = new RegistryConfig("zookeeper://127.0.0.1:2181");
        referenceConfig.setRegistry(registryConfig);
        //设置服务接口和超时时间
        referenceConfig.setInterface(GreetingService.class);
        referenceConfig.setTimeout(5000);
        //设置自定义负载均衡策略与集群容错策略
        referenceConfig.setLoadbalance("myLoadBalance");
        referenceConfig.setCluster("myBroadcast");
        //设置服务分组与版本
        referenceConfig.setVersion("1.0.0");
        referenceConfig.setGroup("dubbo");
        //设置为异步
        referenceConfig.setAsync(true);
        //引用服务
        GreetingService greetingService = referenceConfig.get();
        //设置隐式参数
        RpcContext.getContext().setAttachment("company", "alibaba");
        //调用服务，由于是异步调用，所以该方法马上返回null
        System.out.println(greetingService.sayHello("world"));
        //等待结果
        Future<String> future = RpcContext.getContext().getFuture();
        //future.get会阻塞调用线程直到结果返回，缺点是当业务线程调用get方法后线程会被阻塞，这不是我们想要的，所以Dubbo提供了在future对象上设置回调函数的方式
        //真正实现异步调用
        System.out.println(future.get());
    }

    public void testAsyncForCallback() throws Exception{
        ReferenceConfig<GreetingService> referenceConfig = new ReferenceConfig<GreetingService>();
        //设置应用程序信息
        referenceConfig.setApplication(new ApplicationConfig("first-dubbo-consumer"));
        //设置服务注册中心
        RegistryConfig registryConfig = new RegistryConfig("zookeeper://127.0.0.1:2181");
        referenceConfig.setRegistry(registryConfig);
        //设置服务接口和超时时间
        referenceConfig.setInterface(GreetingService.class);
        referenceConfig.setTimeout(5000);
        //设置自定义负载均衡策略与集群容错策略
        referenceConfig.setLoadbalance("myLoadBalance");
        referenceConfig.setCluster("myBroadcast");
        //设置服务分组与版本
        referenceConfig.setVersion("1.0.0");
        referenceConfig.setGroup("dubbo");
        //设置为异步
        referenceConfig.setAsync(true);
        //引用服务
        GreetingService greetingService = referenceConfig.get();
        //设置隐式参数
        RpcContext.getContext().setAttachment("company", "alibaba");
        //调用服务，由于是异步调用，所以该方法马上返回null
        System.out.println(greetingService.sayHello("world"));
        //异步执行回调，获取CompletableFuture类型的future，然后就可以基于CompletableFuture的能力做一系列操作，这里通过调用whenComplete设置了回调函数
        //作用是当服务提供端产生响应结果后调用设置的回调函数，函数内判断如果异常t不为null，则打印异常信息，否则打印响应结果
        CompletableFuture<String> future = RpcContext.getContext().getCompletableFuture();
        future.whenComplete((v,t)-> {
            if (t != null) {
                t.printStackTrace();
            } else {
                System.out.println(v);
            }
        });
    }

    public void testAsyncForCallback2() throws Exception{
        ReferenceConfig<GreetingServiceAsync> referenceConfig = new ReferenceConfig<GreetingServiceAsync>();
        //设置应用程序信息
        referenceConfig.setApplication(new ApplicationConfig("first-dubbo-consumer"));
        //设置服务注册中心
        RegistryConfig registryConfig = new RegistryConfig("zookeeper://127.0.0.1:2181");
        referenceConfig.setRegistry(registryConfig);
        //设置服务接口和超时时间
        referenceConfig.setInterface(GreetingService.class);
        referenceConfig.setTimeout(5000);
        //设置自定义负载均衡策略与集群容错策略
        referenceConfig.setLoadbalance("myLoadBalance");
        referenceConfig.setCluster("myBroadcast");
        //设置服务分组与版本
        referenceConfig.setVersion("1.0.0");
        referenceConfig.setGroup("dubbo");
        //设置为异步
        referenceConfig.setAsync(true);
        //引用服务
        GreetingServiceAsync greetingService = referenceConfig.get();
        //设置隐式参数
        RpcContext.getContext().setAttachment("company", "alibaba");
        //调用greetingService.sayHello直接返回了CompletableFuture对象，并在其上设置了回调函数
        CompletableFuture<String> future = greetingService.sayHello("world");
        future.whenComplete((v,t)-> {
            if (t != null) {
                t.printStackTrace();
            } else {
                System.out.println(v);
            }
        });
    }
}
