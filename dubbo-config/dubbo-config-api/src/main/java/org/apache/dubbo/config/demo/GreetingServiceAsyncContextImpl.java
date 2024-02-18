package org.apache.dubbo.config.demo;

import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.rpc.AsyncContext;
import org.apache.dubbo.rpc.RpcContext;

import javax.naming.Name;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author dupeng
 * @version V1.0
 * Copyright 2022 Kidswant Children Products Co., Ltd.
 * @Title: GreetingServiceAsyncContextImpl
 * @Description: TODO
 * @date 2024/2/17 16:15
 */
public class GreetingServiceAsyncContextImpl implements GreetingService {
    //创建业务自定义线程池
    private final ThreadPoolExecutor bizThreadpool = new ThreadPoolExecutor(8,16,1, TimeUnit.MINUTES,
            new SynchronousQueue<>(), new NamedThreadFactory("biz-thread-pool"),
            new ThreadPoolExecutor.CallerRunsPolicy());
    @Override
    public String sayHello(String content) {
        //开启服务异步执行
        final AsyncContext asyncContext = RpcContext.startAsync();
        bizThreadpool.execute(() -> {
            //如果要使用上下文，则必须要放在第一句执行，切换上下文
            asyncContext.signalContextSwitch();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //写回响应
            asyncContext.write("Hello" + content + " " + RpcContext.getContext().getAttachment("company"));
        });
        return null;
    }
}
