package com.example.blog.service.limit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.example.blog.adapter.LimitAdapter;
import com.example.blog.enums.StatusLimit;

import io.lettuce.core.RedisClient;

/**
 * 真实 Redis 集成测试：验证 Lua 脚本的限流逻辑是否真正生效。
 * 默认不执行（避免依赖本机 Redis），设置环境变量后运行：
 *
 * <pre>
 * REDIS_LIMIT_LIVE=true REDIS_HOST=localhost REDIS_PORT=6379 \
 * ./gradlew test --tests 'com.example.blog.service.limit.RedisLimitServiceLiveTest' -i --rerun
 * </pre>
 *
 * <p>REDIS_HOST / REDIS_PORT 可省略，默认 localhost:6379。
 * 注意 fix.lua 使用了 {@code EXPIRE ... NX}，需要 Redis 7.0+。
 */
@EnabledIfEnvironmentVariable(named = "REDIS_LIMIT_LIVE", matches = "true")
class RedisLimitServiceLiveTest {

    private RedisClient client;
    private RedisLimitService service;

    @BeforeEach
    void setUp() {
        String host = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        client = RedisClient.create("redis://" + host + ":" + port);
        service = new RedisLimitService(client);
    }

    @AfterEach
    void tearDown() {
        client.shutdown();
    }

    /** 冷却锁：SET NX，第一次放行，冷却期内拒绝 */
    @Test
    void expiredLimitAllowsFirstCallAndBlocksDuringCooldown() {
        LimitAdapter adapter = LimitAdapter.builder()
                .key("test:limit:expired:" + UUID.randomUUID())
                .value("1").destory(60).timeOut(3).build();

        assertThat(service.expiredLimit(adapter)).isEqualTo(StatusLimit.SUCCESS);
        assertThat(service.expiredLimit(adapter)).isEqualTo(StatusLimit.FAULT);
    }

    /** 固定窗口：limit=3，第 4 次起拒绝 */
    @Test
    void fixedWindowBlocksAfterLimit() {
        LimitAdapter adapter = LimitAdapter.builder()
                .key("test:limit:fix:" + UUID.randomUUID())
                .limit(3).window(60).timeOut(3).build();

        assertThat(service.fix(adapter)).isEqualTo(StatusLimit.SUCCESS);
        assertThat(service.fix(adapter)).isEqualTo(StatusLimit.SUCCESS);
        assertThat(service.fix(adapter)).isEqualTo(StatusLimit.SUCCESS);
        assertThat(service.fix(adapter)).isEqualTo(StatusLimit.FAULT);
    }

    /** 滑动窗口：limit=2，第 3 次起拒绝 */
    @Test
    void slidingWindowBlocksAfterLimit() {
        LimitAdapter adapter = LimitAdapter.builder()
                .key("test:limit:slide:" + UUID.randomUUID())
                .limit(2).window(60).destory(120).timeOut(3).build();

        assertThat(service.slide(adapter)).isEqualTo(StatusLimit.SUCCESS);
        assertThat(service.slide(adapter)).isEqualTo(StatusLimit.SUCCESS);
        assertThat(service.slide(adapter)).isEqualTo(StatusLimit.FAULT);
    }
}
