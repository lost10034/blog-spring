package com.example.blog.interfaces;




import com.example.blog.enums.StatusLimit;

import io.lettuce.core.codec.RedisCodec;

public interface RedisLimit {
    public <K, V> StatusLimit expiredLimit(int second,int timeOut, K key, V value, RedisCodec<K, V> codec);
    public <K>  StatusLimit fix(int max,int second ,int timeOut, K key, RedisCodec<K,Integer> codec);
    public <K>  StatusLimit slide(int max,String destory,int window,int timeOut,K key,RedisCodec<K,String> codec);

}
