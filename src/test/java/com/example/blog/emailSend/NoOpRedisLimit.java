package com.example.blog.emailSend;

import com.example.blog.adapter.LimitAdapter;
import com.example.blog.enums.StatusLimit;
import com.example.blog.interfaces.RedisLimit;

/**
 * 联调测试专用的空实现：不启动 Spring 上下文、不访问 Redis。
 * EmailSendService 的 SendCloud / 阿里云路径都以 expiredLimit 做冷却锁并依赖返回值拦截，
 * 这里恒返回 SUCCESS，保证真发流程不被拦截。
 */
class NoOpRedisLimit implements RedisLimit {

    @Override
    public StatusLimit expiredLimit(int second, int timeOut, String key, String value) {
        return StatusLimit.SUCCESS;
    }

    @Override
    public StatusLimit expiredLimit(LimitAdapter limitAdapter) {
        return StatusLimit.SUCCESS;
    }

    @Override
    public StatusLimit fix(int max, int second, int timeOut, String key) {
        return StatusLimit.SUCCESS;
    }

    @Override
    public StatusLimit fix(LimitAdapter limitAdapter) {
        return StatusLimit.SUCCESS;
    }

    @Override
    public StatusLimit slide(LimitAdapter limitAdapter) {
        return StatusLimit.SUCCESS;
    }
}
