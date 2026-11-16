package com.example.blog.service.limit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.example.blog.adapter.LimitAdapter;
import com.example.blog.enums.StatusLimit;
import com.example.blog.interfaces.RedisLimit;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PreDestroy;

@Service
public class RedisLimitService implements RedisLimit {
    private static final Logger logger = LoggerFactory.getLogger(RedisLimitService.class);

    private static final String SLIDE_SCRIPT = loadScript("lua/slide.lua");
    private static final String BASIC_SCRIPT = loadScript("lua/basic.lua");
    private static final String FIX_STRING = loadScript("lua/fix.lua");

    /** 统一的结果判定:脚本/计数结果为 1 视为放行 */
    private static final Function<Long, StatusLimit> ALLOWED = value -> value == 1L ? StatusLimit.SUCCESS : StatusLimit.FAULT;

    /** 应用生命周期内复用的长连接:Lettuce 连接线程安全可并发调用,断线由 autoReconnect 自动恢复 */
    private final StatefulRedisConnection<String, String> connection;
    private final RedisAsyncCommands<String, String> async;

    RedisLimitService(@Qualifier("asyncClient") RedisClient redisClient) {
        this.connection = redisClient.connect(StringCodec.UTF8);
        this.async = connection.async();
    }

    @PreDestroy
    void shutdown() {
        connection.close();
    }

    private static String loadScript(String classpath) {
        try (var in = new ClassPathResource(classpath).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("加载lua脚本失败：" + classpath, e);
        }
    }

    @Override
    public StatusLimit expiredLimit(LimitAdapter limitAdapter) {
        String[] argv = {
            String.valueOf(limitAdapter.value()),
            String.valueOf(limitAdapter.destory())
        };
        String[] keys = { limitAdapter.key() };
        RedisFuture<Long> allowed = async.eval(BASIC_SCRIPT, ScriptOutputType.INTEGER, keys, argv);
        return await(limitAdapter.timeOut(), TimeUnit.SECONDS, allowed, ALLOWED);
    }

    @Override
    public StatusLimit expiredLimit(int second, int timeOut, String key, String value) {
        RedisFuture<String> future = async.set(key, value, SetArgs.Builder.ex(second).nx());
        return await(timeOut, TimeUnit.SECONDS, future, result -> "OK".equals(result) ? StatusLimit.SUCCESS : StatusLimit.FAULT);
    }

    @Override
    public StatusLimit fix(int max, int second, int timeOut, String key) {
        RedisFuture<Long> count = async.incr(key);
        return await(timeOut, TimeUnit.MILLISECONDS, count, c -> {
            if (c == 1L) {
                // 窗口内第一次访问,补设过期时间(异步发出,不等待结果)
                async.expire(key, second);
            }
            return StatusLimit.SUCCESS;
        });
    }

    @Override
    public StatusLimit fix(LimitAdapter limitAdapter) {
        String[] argv = {
            String.valueOf(limitAdapter.limit()),
            String.valueOf(limitAdapter.window())
        };
        String[] keys = { limitAdapter.key() };
        RedisFuture<Long> allowed = async.eval(FIX_STRING, ScriptOutputType.INTEGER, keys, argv);
        return await(limitAdapter.timeOut(), TimeUnit.SECONDS, allowed, ALLOWED);
    }

    @Override
    public StatusLimit slide(LimitAdapter limitAdapter) {
        long nowMillis = System.currentTimeMillis();
        long windowMillis = TimeUnit.SECONDS.toMillis(limitAdapter.window());
        String uuid = UUID.randomUUID().toString();
        String[] argv = {
            String.valueOf(windowMillis),
            String.valueOf(limitAdapter.limit()),
            String.valueOf(nowMillis),
            String.valueOf(limitAdapter.destory()),
            uuid
        };
        String[] keys = { limitAdapter.key() };
        RedisFuture<Long> future = async.eval(SLIDE_SCRIPT, ScriptOutputType.INTEGER, keys, argv);
        return await(limitAdapter.timeOut(), TimeUnit.SECONDS, future, ALLOWED);
    }

    /**
     * 统一等待 Redis 异步结果:超时/中断/执行失败映射为 TIMEOUT/CANCEL/FAULT,
     * 正常返回时由 onSuccess 判定 SUCCESS/FAULT(结果为 null 视为 FAULT)。
     */
    private <T> StatusLimit await(int timeout, TimeUnit unit, RedisFuture<T> future, Function<T, StatusLimit> onSuccess) {
        try {
            T value = future.get(timeout, unit);
            return value == null ? StatusLimit.FAULT : onSuccess.apply(value);
        } catch (TimeoutException e) {
            // 没等到 Redis 响应
            return StatusLimit.TIMEOUT;
        } catch (InterruptedException e) {
            // 等待期间线程被中断:恢复中断标记后返回
            Thread.currentThread().interrupt();
            return StatusLimit.CANCEL;
        } catch (ExecutionException e) {
            // Redis 侧命令执行失败
            logger.warn("Redis 命令执行失败", e);
            return StatusLimit.FAULT;
        }
    }
}
