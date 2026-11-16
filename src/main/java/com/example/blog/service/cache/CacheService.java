package com.example.blog.service.cache;


import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PreDestroy;

/**
 * 基于 Lettuce 异步 API 的缓存封装。
 * 所有方法都带超时等待;未命中、超时、Redis 执行失败统一折叠为 Optional.empty()(超时/中断/执行失败均记入日志)。
 */
@Service
public class CacheService {
    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);

    /** 应用生命周期内复用的长连接:Lettuce 连接线程安全可并发调用,断线由 autoReconnect 自动恢复 */
    private final StatefulRedisConnection<String, String> connection;
    private final RedisAsyncCommands<String, String> async;

    CacheService(@Qualifier("asyncClient") RedisClient redisClient) {
        this.connection = redisClient.connect(StringCodec.UTF8);
        this.async = connection.async();
    }

    @PreDestroy
    void shutdown() {
        connection.close();
    }

    /** GET:key 不存在、超时或执行失败返回 Optional.empty() */
    public Optional<String> get(String key, int timeoutSeconds) {
        return await(timeoutSeconds, async.get(key));
    }

    /** DEL:varargs 必须是最后一个参数,所以超时参数放在前面 */
    public Optional<Long> del(int timeoutSeconds, String... keys) {
        return await(timeoutSeconds, async.del(keys));
    }

    /** SET:无过期时间,长期保留 */
    public Optional<String> set(String key, String value, int timeoutSeconds) {
        return await(timeoutSeconds, async.set(key, value));
    }

    /** SET key value EX seconds [NX]:nx=true 时仅当 key 不存在才写入 */
    public Optional<String> setWithExpire(String key, String value, int expireSeconds, int timeoutSeconds, boolean nx) {
        SetArgs args = nx ? SetArgs.Builder.ex(expireSeconds).nx() : SetArgs.Builder.ex(expireSeconds);
        return await(timeoutSeconds, async.set(key, value, args));
    }

    /** 统一等待结果:把 Future 转成有界阻塞等待,超时/中断/失败折叠为 Optional.empty(),错误均记入日志 */
    private <T> Optional<T> await(int timeoutSeconds, RedisFuture<T> future) {
        try {
            return Optional.ofNullable(future.get(timeoutSeconds, TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            logger.warn("Redis 命令在 {} 秒内未响应,按未命中处理", timeoutSeconds, e);
            return Optional.empty();
        } catch (InterruptedException e) {
            // 等待期间线程被中断:恢复中断标记后按未命中处理
            Thread.currentThread().interrupt();
            logger.warn("等待 Redis 响应期间线程被中断,按未命中处理", e);
            return Optional.empty();
        } catch (ExecutionException e) {
            logger.warn("Redis 命令执行失败,按未命中处理", e);
            return Optional.empty();
        }
    }
}
