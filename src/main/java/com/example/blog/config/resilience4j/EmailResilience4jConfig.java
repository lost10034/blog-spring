package com.example.blog.config.resilience4j;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.functions.CheckedSupplier;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;

@Configuration
public class EmailResilience4jConfig {

    /**
     * Registry 必须是单例（Spring 的 @Configuration 默认单例，这里在构造时创建一次）：
     * 熔断器 / 限流器需要跨请求共享同一份状态，否则每次 basicDefault() 都新建 Registry，
     * 失败计数无法累积，熔断永远不会打开。
     */
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final RetryRegistry retryRegistry;

    public EmailResilience4jConfig() {
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(
                        IOException.class,
                        TimeoutException.class,
                        com.aliyun.tea.TeaException.class
                )
                .ignoreExceptions(
                        IllegalArgumentException.class,
                        NullPointerException.class
                )
                .build());

        this.rateLimiterRegistry = RateLimiterRegistry.of(RateLimiterConfig.custom()
                .timeoutDuration(Duration.ZERO)
                .limitRefreshPeriod(Duration.ofMillis(1000))
                .limitForPeriod(10)
                .build());

        this.retryRegistry = RetryRegistry.of(RetryConfig.custom().maxAttempts(2) // 总共最多调用 2 次，含首次调用
                .waitDuration(Duration.ofMillis(500))
                .retryOnResult(EmailResilience4jConfig::isServerError)
                .retryOnException(e ->
                        e instanceof IOException || e instanceof TimeoutException).build());
    }

    public <T> CheckedSupplier<T> basicDefault(CheckedSupplier<T> function, String name) {
        return decorate(function, circuitBreakerRegistry, rateLimiterRegistry, retryRegistry, name);
    }

    public static boolean isServerError(Object response) {
        if (response == null) {
            return false;
        }
        try {
            Object statusCode = response.getClass().getMethod("getStatusCode").invoke(response);
            return statusCode instanceof Number
                    && ((Number) statusCode).intValue() >= 500
                    && ((Number) statusCode).intValue() < 600;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public <T> CheckedSupplier<T> decorate(CheckedSupplier<T> function, CircuitBreakerRegistry circuitBreakerBasic, RateLimiterRegistry rateLimiter, RetryRegistry registry, String name) {
        return Decorators.ofCheckedSupplier(function).withCircuitBreaker(circuitBreakerBasic.circuitBreaker(name)).
                withRateLimiter(rateLimiter.rateLimiter(name)).withRetry(registry.retry(name)).decorate();
    }
}
