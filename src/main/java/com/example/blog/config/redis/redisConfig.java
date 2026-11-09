package com.example.blog.config.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.lettuce.core.RedisClient;
import org.springframework.beans.factory.annotation.Value;

@Configuration 
public class redisConfig { 
    @Bean 
public RedisClient asyncClient(
        @Value ("${spring.data.redis.host}") String host,
        @Value("${spring.data.redis.port}") int port) {
    return RedisClient.create("redis://" + host + ":" + port);
}
}
