package com.example.blog.config.ThreadPool;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;


@Configuration 
@EnableAsync 
public class SendEmailPool {
    @Bean 
    public  ThreadPoolTaskExecutor sendEmailExecutor(ThreadPoolTaskExecutorBuilder builder ){
        ThreadPoolTaskExecutor executor= builder.corePoolSize(2)
       .maxPoolSize(4).queueCapacity(100)
       .threadNamePrefix("email").awaitTermination(true).awaitTerminationPeriod(Duration.ofSeconds(20)).build();
            executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
       return executor;
    }
}
