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
package org.apache.dubbo.rpc.cluster.loadbalance;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcStatus;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * LeastActiveLoadBalance
 * <p>
 * Filter the number of invokers with the least number of active calls and count the weights and quantities of these invokers.
 * If there is only one invoker, use the invoker directly;
 * if there are multiple invokers and the weights are not the same, then random according to the total weight;
 * if there are multiple invokers and the same weight, then randomly called.
 */
public class LeastActiveLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "leastactive";

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        //代码1-8初始化一系列变量
        // Number of invokers
        //1、服务提供者个数
        int length = invokers.size();
        // The least active value of all invokers
        //2、临时变量，用来暂存被调用的最少次数
        int leastActive = -1;
        // The number of invokers having the same least active value (leastActive)
        //3、记录调用次数等于最小调用次数的服务提供者个数
        int leastCount = 0;
        // The index of invokers having the same least active value (leastActive)
        //4、记录调用次数等于最小调用次数的服务提供者的下标
        int[] leastIndexes = new int[length];
        // the weight of every invokers
        //5、记录每个服务提供者的权重
        int[] weights = new int[length];
        // The sum of the warmup weights of all the least active invokers
        //6、记录调用次数等于最小调用次数的服务提供者的权重和
        int totalWeight = 0;
        // The weight of the first least active invoker
        //7、记录第一个调用次数等于最小调用次数的服务提供者的权重
        int firstWeight = 0;
        // Every least active invoker has the same weight value?
        //8、所有的调用次数等于最小调用次数的服务提供者的权重是否一样
        boolean sameWeight = true;


        // Filter out all the least active invokers
        //9、过滤出的所有的调用次数等于最小调用次数的服务提供者
        for (int i = 0; i < length; i++) {
            Invoker<T> invoker = invokers.get(i);
            // Get the active number of the invoker
            //9.1、获取当前服务提供者被调用次数
            int active = RpcStatus.getStatus(invoker.getUrl(), invocation.getMethodName()).getActive();
            // Get the weight of the invoker's configuration. The default value is 100.
            //9.2、计算当前服务提供者的权重（默认为100）
            int afterWarmup = getWeight(invoker, invocation);
            // save for later use
            //9.3、保存当前服务提供者的权重
            weights[i] = afterWarmup;
            // If it is the first invoker or the active number of the invoker is less than the current least active number
            //9.4、如果是第一个服务提供者，或者当前服务提供者的调用次数小于当前的最小调用次数，计算出最小被调用的次数
            if (leastActive == -1 || active < leastActive) {
                // Reset the active number of the current invoker to the least active number
                //当前最小调用次数
                leastActive = active;
                // Reset the number of least active invokers
                //记录调用次数等于最小调用次数的服务提供者个数
                leastCount = 1;
                // Put the first least active invoker first in leastIndexes
                //由于等于最小被调用次数的服务提供者可能不止一个，所以这里把所有等于最小被调用次数的服务提供者记录到leastIndexes数组中
                leastIndexes[0] = i;
                // Reset totalWeight
                totalWeight = afterWarmup;
                // Record the weight the first least active invoker
                firstWeight = afterWarmup;
                // Each invoke has the same weight (only one invoker here)
                sameWeight = true;
                // If current invoker's active value equals with leaseActive, then accumulating.
            }
            //9.5、如果当前提供者的被调用次数等于当前最小调用次数
            else if (active == leastActive) {
                // Record the index of the least active invoker in leastIndexes order
                //记录调用次数等于最小调用次数的服务提供者的下标
                leastIndexes[leastCount++] = i;
                // Accumulate the total weight of the least active invoker
                //累加所有的权重
                totalWeight += afterWarmup;
                // If every invoker has the same weight?
                //9.5.1、所有的Invoker是否权重都一样
                if (sameWeight && afterWarmup != firstWeight) {
                    sameWeight = false;
                }
            }
        }
        // Choose an invoker from all the least active invokers
        //10、如果只有一个最小调用次数的Invoker直接返回
        if (leastCount == 1) {
            // If we got exactly one invoker having the least active value, return this invoker directly.
            return invokers.get(leastIndexes[0]);
        }
        //11、如果最小调用次数的Invoker有多个并且权重不一样，基于权重使用随机选择策略中的方法来选择一个
        if (!sameWeight && totalWeight > 0) {
            // If (not every invoker has the same weight & at least one invoker's weight>0), select randomly based on 
            // totalWeight.
            int offsetWeight = ThreadLocalRandom.current().nextInt(totalWeight);
            // Return a invoker based on the random value.
            for (int i = 0; i < leastCount; i++) {
                int leastIndex = leastIndexes[i];
                offsetWeight -= weights[leastIndex];
                if (offsetWeight < 0) {
                    return invokers.get(leastIndex);
                }
            }
        }
        // If all invokers have the same weight value or totalWeight=0, return evenly.
        //12、如果最小调用次数的Invoker有多个并且权重一样，随机选择一个
        return invokers.get(leastIndexes[ThreadLocalRandom.current().nextInt(leastCount)]);
    }
}
