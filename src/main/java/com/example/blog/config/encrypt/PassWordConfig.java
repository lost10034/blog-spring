package com.example.blog.config.encrypt;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
@Configuration 
public class PassWordConfig {
    @Bean 
    PasswordEncoder passWordEncoder(){
        return  Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
