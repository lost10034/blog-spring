package com.example.blog.service.PassWord;

import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PassWordHashService {
    private final PasswordEncoder passwordEncoder;

    public PassWordHashService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Async ("argon2Executor")
    public CompletableFuture<String> hash(String passWord){
          String hash=passwordEncoder.encode(passWord);
          return  CompletableFuture.completedFuture(hash);
    }
}
