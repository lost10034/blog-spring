package com.example.blog.emailSend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.example.blog.config.resilience4j.EmailResilience4jConfig;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.core.functions.CheckedSupplier;

/**
 * 验证 {@link EmailResilience4jConfig} 中 Resilience4j 熔断器配置是否生效。
 *
 * <p>不启动 Spring 上下文、不访问 Redis、不对外发邮件：用一个恒定失败的
 * {@link CheckedSupplier} 替代真实的 SendCloud / 阿里云调用，直接驱动装饰器。
 *
 * <p>配置要点（见 EmailResilience4jConfig#basicDefault）：
 * <ul>
 *   <li>failureRateThreshold=50、slidingWindowSize=20、minimumNumberOfCalls=10</li>
 *   <li>recordExceptions: IOException / TimeoutException / TeaException</li>
 *   <li>ignoreExceptions: IllegalArgumentException / NullPointerException</li>
 *   <li>Retry(maxAttempts=2, 对 IOException 重试)：每次外层 get 会调用下游两次</li>
 * </ul>
 */
class CircuitBreakerTest {

    private final EmailResilience4jConfig config = new EmailResilience4jConfig();

    @Test
    void repeatedFailuresOpenCircuitAndSubsequentCallsAreRejected() {
        AtomicInteger downstreamCalls = new AtomicInteger();
        CheckedSupplier<String> failingDownstream = () -> {
            downstreamCalls.incrementAndGet();
            throw new IOException("模拟下游邮件服务不可用");
        };

        // 装饰器持有 Registry 中的 CircuitBreaker 实例：同一装饰器上的多次调用共享该实例
        CheckedSupplier<String> decorated = config.basicDefault(
                failingDownstream, "cb-fail-open-" + UUID.randomUUID());

        int outerCallsUntilOpen = 0;
        boolean opened = false;
        for (int i = 0; i < 30; i++) {
            try {
                decorated.get();
            } catch (CallNotPermittedException e) {
                outerCallsUntilOpen = i + 1;
                opened = true;
                break;
            } catch (Throwable ignored) {
                // 熔断打开前，下游 IOException 经重试后透传，属于预期
            }
        }

        assertThat(opened).as("连续失败达到阈值后熔断器应进入 OPEN 状态").isTrue();
        assertThat(outerCallsUntilOpen)
                .as("minimumNumberOfCalls=10，熔断应在少量外层调用后打开")
                .isLessThan(30);

        int callsAtOpen = downstreamCalls.get();
        try {
            decorated.get();
            fail("熔断器 OPEN 后应拒绝调用");
        } catch (CallNotPermittedException expected) {
            // 预期：熔断打开后直接抛 CallNotPermittedException
        } catch (Throwable other) {
            fail("熔断打开后应抛 CallNotPermittedException，实际为: " + other);
        }
        assertThat(downstreamCalls.get()).as("熔断打开后不应再调用下游")
                .isEqualTo(callsAtOpen);
    }

    @Test
    void ignoredExceptionsDoNotOpenCircuit() throws InterruptedException {
        AtomicInteger downstreamCalls = new AtomicInteger();
        CheckedSupplier<String> supplier = () -> {
            downstreamCalls.incrementAndGet();
            throw new IllegalArgumentException("被 ignoreExceptions 忽略的异常");
        };

        CheckedSupplier<String> decorated = config.basicDefault(
                supplier, "cb-ignored-" + UUID.randomUUID());

        boolean rejected = false;
        int totalCalls = 12; // 大于 minimumNumberOfCalls=10，确保足以触发熔断（若异常被计为失败）
        for (int i = 0; i < totalCalls; i++) {
            if (i == totalCalls / 2) {
                // RateLimiter 限制 10 次/秒；中途等待刷新，避免限流异常干扰断言
                Thread.sleep(1200);
            }
            try {
                decorated.get();
            } catch (CallNotPermittedException e) {
                rejected = true;
            } catch (Throwable ignored) {
                // IllegalArgumentException 被 CircuitBreaker 忽略后直接透传
            }
        }

        assertThat(rejected).as("ignoreExceptions 中的异常不应记录为失败，因此不应触发熔断").isFalse();
        assertThat(downstreamCalls.get())
                .as("被忽略的异常不会触发 Retry，每次外层调用只调用一次下游")
                .isEqualTo(totalCalls);
    }

    @Test
    void circuitBreakerStateIsSharedAcrossBasicDefaultCalls() {
        String name = "cb-shared-" + UUID.randomUUID();

        // 1) 用同名装饰器把熔断打开
        AtomicInteger failingCalls = new AtomicInteger();
        CheckedSupplier<String> first = config.basicDefault(() -> {
            failingCalls.incrementAndGet();
            throw new IOException("boom");
        }, name);
        openCircuit(first);

        // 2) 同名但重新调用 basicDefault 得到的装饰器，应继承同一熔断器的 OPEN 状态。
        //    这正是修复目标：EmailSendService 每次发信都调用 basicDefault()，
        //    只有 Registry 单例共享，失败计数才能跨请求累积、熔断才能生效。
        AtomicInteger okCalls = new AtomicInteger();
        CheckedSupplier<String> second = config.basicDefault(() -> {
            okCalls.incrementAndGet();
            return "ok";
        }, name);

        try {
            second.get();
            fail("同名熔断器已 OPEN，第二次调用应被拒绝");
        } catch (CallNotPermittedException expected) {
            // 预期：跨 basicDefault 调用仍能看到 OPEN 状态
        } catch (Throwable other) {
            fail("应抛 CallNotPermittedException，实际为: " + other);
        }
        assertThat(okCalls.get()).as("熔断打开后不应再调用下游").isZero();
    }

    private void openCircuit(CheckedSupplier<String> decorated) {
        for (int i = 0; i < 30; i++) {
            try {
                decorated.get();
            } catch (CallNotPermittedException e) {
                return;
            } catch (Throwable ignored) {
                // 继续制造失败直到熔断打开
            }
        }
        fail("未能在预期次数内打开熔断器");
    }
}
