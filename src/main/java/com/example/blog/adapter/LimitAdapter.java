package com.example.blog.adapter;

import lombok.Builder;

/** 限流参数：限流 key 语义上恒为 String，统一由 RedisLimitService 内部用 StringCodec.UTF8 编解码 */
@Builder
public record LimitAdapter(int window, int limit, int timeOut, int destory, int second, String key, String value) {}
