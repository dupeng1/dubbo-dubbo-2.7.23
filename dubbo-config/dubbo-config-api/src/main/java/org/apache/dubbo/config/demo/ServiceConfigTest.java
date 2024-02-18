package org.apache.dubbo.config.demo;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.registry.RegistryFactory;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * ref：对外提供服务的实际类
 * 1、具体服务到Invoker的转换，ProxyFactory的getInvoker，ProxyFactory的默认实现JavassistProxyFactory
 * 2、Invoker为AbstractProxyInvoker
 * 3、Invoker转换为Exporter，Protocol的export，Protocol的默认实现DubboProtocol
 */
public class ServiceConfigTest {
    public static void main(String[] args) {
        
    }
    
    public void testSync() {
          ServiceConfig serviceConfig = new ServiceConfig();
          //设置应用程序配置
          serviceConfig.setApplication(new ApplicationConfig("first-dubbo-provider"));
          //设置服务注册中心信息，可知服务注册中心使用了Zookeeper，并且zookeeper的地址为127.0.0.1，启动端口为2181，服务提供方会将服务注册到该中心
          RegistryConfig registryConfig = new RegistryConfig("zookeeper://127.0.0.1:2181");
          serviceConfig.setRegistry(registryConfig);
          //设置接口与实现类
          serviceConfig.setInterface("");
          serviceConfig.setRef(null);
          //设置服务分组与版本，在Dubbo中“服务接口+服务分组+服务版本”唯一确定一个服务，同一个服务接口可以有不同的版本以便服务升级，另外每个服务接口可以属于不同分组，
          //所以当调用方消费服务时一定要设置正确的分组与版本
          serviceConfig.setVersion("1.0.0");
          serviceConfig.setGroup("dubbo");
          //设置线程池策略
          Map<String, String> parameters = new HashMap<>();
          parameters.put("threadpool","mythreadpool");
          serviceConfig.setParameters(parameters);
          //导出服务，启动NettyServer监听链接请求，并将服务注册到服务注册中心
          serviceConfig.export();
    }
    
    public void testAsync() {
          ServiceConfig<GreetingServiceAsync> serviceConfig = new ServiceConfig<GreetingServiceAsync>();
          //设置应用程序配置
          serviceConfig.setApplication(new ApplicationConfig("first-dubbo-provider"));
          //设置服务注册中心信息，可知服务注册中心使用了Zookeeper，并且zookeeper的地址为127.0.0.1，启动端口为2181，服务提供方会将服务注册到该中心
          RegistryConfig registryConfig = new RegistryConfig("zookeeper://127.0.0.1:2181");
          serviceConfig.setRegistry(registryConfig);
          //设置接口与实现类
          serviceConfig.setInterface("");
          serviceConfig.setRef(null);
          //设置服务分组与版本，在Dubbo中“服务接口+服务分组+服务版本”唯一确定一个服务，同一个服务接口可以有不同的版本以便服务升级，另外每个服务接口可以属于不同分组，
          //所以当调用方消费服务时一定要设置正确的分组与版本
          serviceConfig.setVersion("1.0.0");
          serviceConfig.setGroup("dubbo");
          //设置线程池策略
          Map<String, String> parameters = new HashMap<>();
          parameters.put("threadpool","mythreadpool");
          serviceConfig.setParameters(parameters);
          //导出服务，启动NettyServer监听链接请求，并将服务注册到服务注册中心
          serviceConfig.export();
    }

    public void testMock(String type) {
        //获取服务注册中心工厂
        RegistryFactory registryFactory = ExtensionLoader.getExtensionLoader(RegistryFactory.class).getAdaptiveExtension();
        //根据zookeeper地址，获取具体的zookeeper注册中心的客户端实例
        Registry registry = registryFactory.getRegistry(URL.valueOf("zookeeper://127.0.0.1:2181"));
        //将降级方案注册到zookeeper，其中type为降级方案（比如force或者fail），执行代码后，配置信息会被保存到服务注册中心
        //然后消费端启动时会获取到该配置
        registry.register(URL.valueOf("override://0.0.0.0/GreetingService?category=configurators&" +
                "dynamic=false&application=first-dubbo-consumer&" +
                "mock=" + type + ":return+null&group=dubbo&version=1.0.0"));
        //取消配置，当服务提供方的服务恢复后，可以执行代码取消服务降级方案
        registry.unregister(URL.valueOf("override://0.0.0.0/GreetingService?category=configurators&" +
                "dynamic=false&application=first-dubbo-consumer&" +
                "mock=" + type + ":return+null&group=dubbo&version=1.0.0"));
    }
}
