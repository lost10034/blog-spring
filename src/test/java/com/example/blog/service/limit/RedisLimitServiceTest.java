package com.example.blog.service.limit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.example.blog.adapter.LimitAdapter;
import com.example.blog.enums.StatusLimit;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.api.async.RedisAsyncCommands;

/**
 * RedisLimitService 单元测试：用 Mockito 模拟 Lettuce 异步客户端，
 * 验证参数传递、脚本/命令结果到 StatusLimit 的映射，以及超时/中断/异常时的降级行为。
 *
 * <p>本测试不依赖真实 Redis（环境无 redis-server）；Lua 脚本的真实执行逻辑需另配 Redis 做集成测试。
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class RedisLimitServiceTest {

    @Mock private RedisClient redisClient;
    @Mock private StatefulRedisConnection<String, String> connection;
    @Mock private RedisAsyncCommands<String, String> async;

    private RedisLimitService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisClient.connect(any(RedisCodec.class))).thenReturn((StatefulRedisConnection) connection);
        when(connection.async()).thenReturn(async);
        service = new RedisLimitService(redisClient);
    }

    @AfterEach
    void clearInterruptFlag() {
        // 避免中断标记泄漏到后续测试
        Thread.interrupted();
    }

    private RedisFuture<Long> stubEval(long result) throws Exception {
        RedisFuture<Long> future = mock(RedisFuture.class);
        doReturn(future).when(async).eval(anyString(), eq(ScriptOutputType.INTEGER), any(String[].class), any(String[].class));
        when(future.get(anyLong(), any(TimeUnit.class))).thenReturn(result);
        return future;
    }

    @Test
    void expiredLimitByAdapterMapsScriptResultAndPassesArguments() throws Exception {
        RedisFuture<Long> future = stubEval(1L);
        LimitAdapter adapter = LimitAdapter.builder()
                .key("limit:captcha").value("1").destory(60).timeOut(2).build();

        assertThat(service.expiredLimit(adapter)).isEqualTo(StatusLimit.SUCCESS);

        ArgumentCaptor<String[]> keys = ArgumentCaptor.forClass(String[].class);
        ArgumentCaptor<String[]> argv = ArgumentCaptor.forClass(String[].class);
        verify(async).eval(anyString(), eq(ScriptOutputType.INTEGER), keys.capture(), argv.capture());
        assertThat(keys.getValue()).containsExactly("limit:captcha");
        assertThat(argv.getValue()).containsExactly("1", "60");
        verify(future).get(eq(2L), eq(TimeUnit.SECONDS));
    }

    @Test
    void expiredLimitByAdapterReturnsFaultWhenScriptRejects() throws Exception {
        stubEval(0L);
        LimitAdapter adapter = LimitAdapter.builder()
                .key("limit:captcha").value("1").destory(60).timeOut(2).build();

        assertThat(service.expiredLimit(adapter)).isEqualTo(StatusLimit.FAULT);
    }

    @Test
    void expiredLimitWithSetNxMapsOkToSuccessAndNullToFault() throws Exception {
        RedisFuture<String> future = mock(RedisFuture.class);
        when(async.set(eq("limit:set"), eq("1"), any())).thenReturn(future);
        when(future.get(anyLong(), any(TimeUnit.class))).thenReturn("OK", (String) null);

        assertThat(service.expiredLimit(3, 2, "limit:set", "1")).isEqualTo(StatusLimit.SUCCESS);
        assertThat(service.expiredLimit(3, 2, "limit:set", "1")).isEqualTo(StatusLimit.FAULT);
    }

    @Test
    void fixByIncrSetsExpiryOnlyOnFirstCall() throws Exception {
        RedisFuture<Long> incr = mock(RedisFuture.class);
        when(async.incr("limit:fix")).thenReturn(incr);
        when(incr.get(anyLong(), any(TimeUnit.class))).thenReturn(1L, 2L);

        assertThat(service.fix(10, 60, 2, "limit:fix")).isEqualTo(StatusLimit.SUCCESS);
        assertThat(service.fix(10, 60, 2, "limit:fix")).isEqualTo(StatusLimit.SUCCESS);

        // 仅窗口内第一次访问补设过期时间
        verify(async, times(1)).expire("limit:fix", 60);
    }

    @Test
    void fixByAdapterMapsScriptResult() throws Exception {
        RedisFuture<Long> future = stubEval(1L);
        LimitAdapter adapter = LimitAdapter.builder()
                .key("limit:fix").limit(5).window(60).timeOut(2).build();

        assertThat(service.fix(adapter)).isEqualTo(StatusLimit.SUCCESS);

        ArgumentCaptor<String[]> argv = ArgumentCaptor.forClass(String[].class);
        verify(async).eval(anyString(), eq(ScriptOutputType.INTEGER), any(String[].class), argv.capture());
        assertThat(argv.getValue()).containsExactly("5", "60");
    }

    @Test
    void slidePassesWindowParametersAndMapsResult() throws Exception {
        RedisFuture<Long> future = stubEval(1L);
        LimitAdapter adapter = LimitAdapter.builder()
                .key("limit:slide").limit(3).window(10).destory(60).timeOut(2).build();

        assertThat(service.slide(adapter)).isEqualTo(StatusLimit.SUCCESS);

        ArgumentCaptor<String[]> argv = ArgumentCaptor.forClass(String[].class);
        verify(async).eval(anyString(), eq(ScriptOutputType.INTEGER), any(String[].class), argv.capture());
        String[] args = argv.getValue();
        assertThat(args).hasSize(5);
        assertThat(args[0]).as("windowMillis").isEqualTo("10000"); // 10 秒 -> 毫秒
        assertThat(args[1]).as("limit").isEqualTo("3");
        assertThat(args[2]).as("nowMillis").isNotBlank();
        assertThat(args[3]).as("destory").isEqualTo("60");
        assertThat(args[4]).as("uuid").isNotBlank();
    }

    @Test
    void awaitMapsTimeoutToTimeoutStatus() throws Exception {
        RedisFuture<Long> future = mock(RedisFuture.class);
        doReturn(future).when(async).eval(anyString(), any(ScriptOutputType.class), any(String[].class), any(String[].class));
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new TimeoutException());

        assertThat(service.expiredLimit(LimitAdapter.builder()
                .key("limit:timeout").value("1").destory(60).timeOut(2).build()))
                .isEqualTo(StatusLimit.TIMEOUT);
    }

    @Test
    void awaitMapsInterruptionToCancelAndRestoresInterruptFlag() throws Exception {
        RedisFuture<Long> future = mock(RedisFuture.class);
        doReturn(future).when(async).eval(anyString(), any(ScriptOutputType.class), any(String[].class), any(String[].class));
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());

        assertThat(service.expiredLimit(LimitAdapter.builder()
                .key("limit:interrupt").value("1").destory(60).timeOut(2).build()))
                .isEqualTo(StatusLimit.CANCEL);
        assertThat(Thread.currentThread().isInterrupted()).as("应恢复中断标记").isTrue();
    }

    @Test
    void awaitMapsExecutionFailureToFault() throws Exception {
        RedisFuture<Long> future = mock(RedisFuture.class);
        doReturn(future).when(async).eval(anyString(), any(ScriptOutputType.class), any(String[].class), any(String[].class));
        when(future.get(anyLong(), any(TimeUnit.class)))
                .thenThrow(new ExecutionException(new RuntimeException("redis error")));

        assertThat(service.expiredLimit(LimitAdapter.builder()
                .key("limit:exec").value("1").destory(60).timeOut(2).build()))
                .isEqualTo(StatusLimit.FAULT);
    }

    @Test
    void awaitMapsNullResultToFault() throws Exception {
        RedisFuture<Long> future = mock(RedisFuture.class);
        doReturn(future).when(async).eval(anyString(), any(ScriptOutputType.class), any(String[].class), any(String[].class));
        when(future.get(anyLong(), any(TimeUnit.class))).thenReturn(null);

        assertThat(service.expiredLimit(LimitAdapter.builder()
                .key("limit:null").value("1").destory(60).timeOut(2).build()))
                .isEqualTo(StatusLimit.FAULT);
    }
}
