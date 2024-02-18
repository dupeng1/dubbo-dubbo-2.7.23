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
package org.apache.dubbo.common.threadpool.support.fixed;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.threadlocal.NamedInternalThreadFactory;
import org.apache.dubbo.common.threadpool.ThreadPool;
import org.apache.dubbo.common.threadpool.support.AbortPolicyWithReport;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_QUEUES;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_THREADS;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_THREAD_NAME;
import static org.apache.dubbo.common.constants.CommonConstants.QUEUES_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.THREADS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.THREAD_NAME_KEY;

/**
 * Creates a thread pool that reuses a fixed number of threads
 *
 * @see java.util.concurrent.Executors#newFixedThreadPool(int)
 */
public class FixedThreadPool implements ThreadPool {

    public static final String NAME = "fixed";

    //创建一个具有固定个数线程的线程池。
    @Override
    public Executor getExecutor(URL url) {
        //获取线程池中线程的名称前缀，如果没有设置，则使用默认名称Dubbo
        String name = url.getParameter(THREAD_NAME_KEY, DEFAULT_THREAD_NAME);
        //获取线程池个数，如果没有设置，则使用默认的数值200
        int threads = url.getParameter(THREADS_KEY, DEFAULT_THREADS);
        //获取线程池队列大小，如果没有设置，则使用默认的数值0
        int queues = url.getParameter(QUEUES_KEY, DEFAULT_QUEUES);
        //使用JUC包的ThreadPoolExecutor创建线程池，
        //1、核心线程个数和最大线程个数都设置为threads，所以创建的线程池是固定线程个数的线程池
        //2、当队列元素为0时，阻塞队列使用的是SynchronousQueue；
        //当队列元素小于0时，使用的是无界阻塞队列LinkedBlockingQueue；
        //当队列元素大于0时，使用的是有界的LinkedBlockingQueue
        //3、这里使用了自定义的线程工厂NamedInternalThreadFactory来为线程创建自定义名称，并将线程设置为daemon线程
        //4、线程池拒绝策略选择了AbortPolicyWithReport，意味着当线程池队列已满并且线程池中线程都忙碌时，新来的任务会被丢弃，并抛出RejectedExecutionException异常
        return new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS,
                queues == 0 ? new SynchronousQueue<Runnable>() :
                        (queues < 0 ? new LinkedBlockingQueue<Runnable>()
                                : new LinkedBlockingQueue<Runnable>(queues)),
                new NamedInternalThreadFactory(name, true), new AbortPolicyWithReport(name, url));
    }

}
