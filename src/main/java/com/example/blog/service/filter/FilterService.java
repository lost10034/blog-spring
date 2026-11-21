package com.example.blog.service.filter;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.example.blog.adapter.BloomFilterServiceAdapter;
import com.example.blog.enums.BloomCommand;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.CommandOutput;
import io.lettuce.core.output.IntegerListOutput;
import io.lettuce.core.output.IntegerOutput;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.ProtocolKeyword;
import jakarta.annotation.PreDestroy;

/**
 * 布隆过滤器命令封装:方法名与 {@link BloomCommand} 中的命令一一对应,
 * 统一用 Lettuce 的 dispatch 派发自定义模块命令,并带超时降级。
 */
@Service
public class FilterService {
    private static final Logger logger = LoggerFactory.getLogger(FilterService.class);

    /** 应用生命周期内复用的长连接:Lettuce 连接线程安全,断线由 autoReconnect 自动恢复 */
    private final StatefulRedisConnection<String, String> connection;
    private final RedisAsyncCommands<String, String> async;

    FilterService(@Qualifier("asyncClient") RedisClient redisClient) {
        this.connection = redisClient.connect(StringCodec.UTF8);
        this.async = connection.async();
    }

    @PreDestroy
    void shutdown() {
        connection.close();
    }

    /** BF.RESERVE key error_rate capacity [EXPANSION expansion] [NONSCALING] */
    public Optional<String> bfReserve(BloomFilterServiceAdapter adapter) {
        // RedisBloom 不允许 NONSCALING 与非零 EXPANSION 共存("Nonscaling filters cannot expand")
        if (adapter.nonScaling() && adapter.expansion() > 0) {
            throw new IllegalArgumentException("NONSCALING 与 EXPANSION 不能同时使用");
        }
        CommandArgs<String, String> args = new CommandArgs<>(StringCodec.UTF8)
                .addKey(adapter.key())
                .add(adapter.errorRate())
                .add(adapter.capacity());
        if (adapter.expansion() > 0) {
            args.add("EXPANSION").add((long) adapter.expansion());
        }
        if (adapter.nonScaling()) {
            args.add("NONSCALING");
        }
        return execute(BloomCommand.BF_RESERVE, new StatusOutput<>(StringCodec.UTF8), args, adapter.timeoutSeconds());
    }

    /** BF.ADD key item -> 1/0 */
    public Optional<Long> bfAdd(BloomFilterServiceAdapter adapter) {
        return execute(BloomCommand.BF_ADD, new IntegerOutput<>(StringCodec.UTF8), singleArgs(adapter), adapter.timeoutSeconds());
    }

    /** BF.EXISTS key item -> 1/0 */
    public Optional<Long> bfExists(BloomFilterServiceAdapter adapter) {
        return execute(BloomCommand.BF_EXISTS, new IntegerOutput<>(StringCodec.UTF8), singleArgs(adapter), adapter.timeoutSeconds());
    }

    /** BF.MADD key item [item ...] -> [1/0, ...] */
    public Optional<List<Long>> bfMAdd(BloomFilterServiceAdapter adapter) {
        return execute(BloomCommand.BF_MADD, new IntegerListOutput<>(StringCodec.UTF8), multiArgs(adapter), adapter.timeoutSeconds());
    }

    /** BF.MEXISTS key item [item ...] -> [1/0, ...] */
    public Optional<List<Long>> bfMExists(BloomFilterServiceAdapter adapter) {
        return execute(BloomCommand.BF_MEXISTS, new IntegerListOutput<>(StringCodec.UTF8), multiArgs(adapter), adapter.timeoutSeconds());
    }

    /** 单元素命令:取 items[0] */
    private static CommandArgs<String, String> singleArgs(BloomFilterServiceAdapter adapter) {
        String[] items = adapter.items();
        if (items == null || items.length == 0) {
            throw new IllegalArgumentException("items 至少需要一个元素");
        }
        return new CommandArgs<>(StringCodec.UTF8).addKey(adapter.key()).addValue(items[0]);
    }

    /** 批量命令:把所有 items 作为 item [item ...] 追加 */
    private static CommandArgs<String, String> multiArgs(BloomFilterServiceAdapter adapter) {
        String[] items = adapter.items();
        if (items == null || items.length == 0) {
            throw new IllegalArgumentException("items 至少需要一个元素");
        }
        return new CommandArgs<>(StringCodec.UTF8).addKey(adapter.key()).addValues(items);
    }

    /** 自定义模块命令派发:Lettuce 7.x 的 dispatch 必须显式提供 CommandOutput 才能解码回复 */
    private <T> Optional<T> execute(ProtocolKeyword command,
                                    CommandOutput<String, String, T> output,
                                    CommandArgs<String, String> args,
                                    int timeoutSeconds) {
        return await(timeoutSeconds, async.dispatch(command, output, args));
    }

    /** 超时/中断/执行失败统一降级为 Optional.empty() 并记日志 */
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
