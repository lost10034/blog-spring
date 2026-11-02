package com.example.blog.config;

import java.security.SecureRandom;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RandomConfig {
    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }
}