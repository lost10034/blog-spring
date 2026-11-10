package com.example.blog.interfaces;


import com.example.blog.adapter.LimitAdapter;
import com.example.blog.enums.StatusLimit;

public interface RedisLimit {
    public StatusLimit expiredLimit(int second, int timeOut, String key, String value);
    public StatusLimit expiredLimit(LimitAdapter limitAdapter);
    public StatusLimit fix(int max, int second, int timeOut, String key);
    public StatusLimit fix(LimitAdapter limitAdapter);
    public StatusLimit slide(LimitAdapter limitAdapter);
}
