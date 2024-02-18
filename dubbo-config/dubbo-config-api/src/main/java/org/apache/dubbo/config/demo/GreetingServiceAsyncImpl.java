package org.apache.dubbo.config.demo;

import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.rpc.RpcContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author dupeng
 * @version V1.0
 * Copyright 2022 Kidswant Children Products Co., Ltd.
 * @Title: GreetingServiceAsyncImpl
 * @Description: TODO
 * @date 2024/2/17 15:57
 */
public class GreetingServiceAsyncImpl implements GreetingServiceAsync {
    //创建业务自定义线程池
    private final ThreadPoolExecutor bizThreadpool = new ThreadPoolExecutor(8,16,1, TimeUnit.MINUTES,
            new SynchronousQueue<>(), new NamedThreadFactory("biz-thread-pool"),
            new ThreadPoolExecutor.CallerRunsPolicy());


    /**
     * CompletableFuture签名的接口实现异步执行需要接口方法的返回值为CompletableFuture，并且方法内部使用CompletableFuture.supplyAsync
     * 让本该由Dubbo内部线程池中的线程处理的服务，转换为由业务自定义线程池中的线程来处理，CompletableFuture.supplyAsync方法会马上返回一个CompletableFuture对象
     * （所以Dubbo内部线程池中的线程会得到及时释放），传递的业务函数则由业务线程池bizThreadpool执行
     * 调用sayHello方法的线程是Dubbo线程模型线程池中的线程，而业务处理是由bizThreadpool中的线程处理的
     * @param content
     * @return
     */
    @Override
    public CompletableFuture<String> sayHello(String content) {
        //为supplyAsync提供自定义线程池bizThreadpool，避免使用JDK公用线程池
        //使用CompletableFuture.supplyAsync让服务处理异步化
        //保存当前线程的上下文
        RpcContext context = RpcContext.getContext();
        return CompletableFuture.supplyAsync(()->{
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("async return");
            return "Hello" + content + " " + context.getAttachment("company");
        }, bizThreadpool);
    }
}
