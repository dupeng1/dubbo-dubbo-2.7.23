package org.apache.dubbo.config.demo;

import java.util.concurrent.CompletableFuture;

/**
 * @author dupeng
 * @version V1.0
 * Copyright 2022 Kidswant Children Products Co., Ltd.
 * @Title: GreetingServiceAsync
 * @Description: TODO
 * @date 2024/2/17 16:00
 */
public interface GreetingServiceAsync {
    public CompletableFuture<String> sayHello(String content);
}
