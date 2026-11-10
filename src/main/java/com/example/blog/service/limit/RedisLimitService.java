package com.example.blog.service.limit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    private static final String SLIDE_SCRIPT = loadScript("lua/slide.lua");
    private static final String BASIC_SCRIPT = loadScript("lua/basic.lua");
    private static final String FIX_STRING = loadScript("lua/fix.lua");

    /** 应用生命周期内复用的长连接：Lettuce 连接线程安全可并发调用，断线由 autoReconnect 自动恢复 */
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
        try {
            String[] argv = {
                String.valueOf(limitAdapter.value()),
                String.valueOf(limitAdapter.destory())
            };
            String[] keys = { limitAdapter.key() };
            RedisFuture<Long> allowed = async.eval(BASIC_SCRIPT, ScriptOutputType.INTEGER, keys, argv);
            return Long.valueOf(1L).equals(allowed.get(limitAdapter.timeOut(), TimeUnit.SECONDS)) ? StatusLimit.SUCCESS : StatusLimit.FAULT;
        } catch (TimeoutException e) {
            return StatusLimit.TIMEOUT;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return StatusLimit.CANCEL;
        } catch (ExecutionException e) {
            return StatusLimit.FAULT;
        }
    }

    @Override
    public StatusLimit expiredLimit(int second, int timeOut, String key, String value) {
        try {
            String result = async.set(key, value, SetArgs.Builder.ex(second).nx()).get(timeOut, TimeUnit.SECONDS);
            return "OK".equals(result) ? StatusLimit.SUCCESS : StatusLimit.FAULT;
        } catch (TimeoutException e) {
            // *秒内没等到 Redis 响应
            return StatusLimit.TIMEOUT;
        } catch (InterruptedException e) {
            // 等待期间线程被中断:恢复中断标记后返回
            Thread.currentThread().interrupt();
            return StatusLimit.CANCEL;
        } catch (ExecutionException e) {
            // Redis 侧命令执行失败
            return StatusLimit.FAULT;
        }
    }

    @Override
    public StatusLimit fix(int max, int second, int timeOut, String key) {
        try {
            Long count = async.incr(key).get(timeOut, TimeUnit.MILLISECONDS);
            if (count == 1) {
                async.expire(key, second);
            }
            return StatusLimit.SUCCESS;
        } catch (TimeoutException e) {
            // * 毫秒内没等到 Redis 响应
            return StatusLimit.TIMEOUT;
        } catch (InterruptedException e) {
            // 等待期间线程被中断:恢复中断标记后返回
            Thread.currentThread().interrupt();
            return StatusLimit.CANCEL;
        } catch (ExecutionException e) {
            // Redis 侧命令执行失败
            return StatusLimit.FAULT;
        }
    }

    @Override
    public StatusLimit fix(LimitAdapter limitAdapter) {
        try {
            String[] argv = {
                String.valueOf(limitAdapter.limit()),
                String.valueOf(limitAdapter.window())
            };
            String[] keys = { limitAdapter.key() };
            RedisFuture<Long> allowed = async.eval(FIX_STRING, ScriptOutputType.INTEGER, keys, argv);
            return Long.valueOf(1L).equals(allowed.get(limitAdapter.timeOut(), TimeUnit.SECONDS)) ? StatusLimit.SUCCESS : StatusLimit.FAULT;
        } catch (TimeoutException e) {
            // *秒内没等到 Redis 响应
            return StatusLimit.TIMEOUT;
        } catch (InterruptedException e) {
            // 等待期间线程被中断:恢复中断标记后返回
            Thread.currentThread().interrupt();
            return StatusLimit.CANCEL;
        } catch (ExecutionException e) {
            // Redis 侧命令执行失败
            return StatusLimit.FAULT;
        }
    }

    @Override
    public StatusLimit slide(LimitAdapter limitAdapter) {
        long nowMillis = System.currentTimeMillis();
        long windowMillis = TimeUnit.SECONDS.toMillis(limitAdapter.window());
        String uuid = UUID.randomUUID().toString();
        try {
            String[] argv = {
                String.valueOf(windowMillis),
                String.valueOf(limitAdapter.limit()),
                String.valueOf(nowMillis),
                String.valueOf(limitAdapter.destory()),
                uuid
            };
            String[] keys = { limitAdapter.key() };
            RedisFuture<Long> future = async.eval(SLIDE_SCRIPT, ScriptOutputType.INTEGER, keys, argv);
            Long allowed = future.get(limitAdapter.timeOut(), TimeUnit.SECONDS);
            return Long.valueOf(1L).equals(allowed) ? StatusLimit.SUCCESS : StatusLimit.FAULT;
        } catch (TimeoutException e) {
            return StatusLimit.TIMEOUT;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return StatusLimit.CANCEL;
        } catch (ExecutionException e) {
            return StatusLimit.FAULT;
        }
    }
}
