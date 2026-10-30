package com.example.blog.config.ThreadPool;

import java.time.Duration;

import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class Argon2IdPool {

    @Bean
    public ThreadPoolTaskExecutor argon2Executor(
            ThreadPoolTaskExecutorBuilder builder) {
        return builder
                .corePoolSize(2)
                .maxPoolSize(2)
                .queueCapacity(10)
                .threadNamePrefix("argon2-")
                .awaitTermination(true)
                .awaitTerminationPeriod(Duration.ofSeconds(30))
                .build();
    }
}