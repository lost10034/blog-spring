package com.example.blog.config.auth;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.blog.entity.User;

import jakarta.validation.Valid;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController 
@RequestMapping ("/api/auth")
public record Auth(PasswordEncoder passWordEncoder) {
    @PostMapping("/login")
    public String postMethodLogin(@RequestBody String entity) {
        
        return entity;
    }
    @PostMapping("/register")
    public String postMethodRegisrer(@Valid @RequestBody User User) {
        
        return  "";
    }
    
    
    
}
