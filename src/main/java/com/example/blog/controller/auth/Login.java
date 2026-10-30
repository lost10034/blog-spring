package com.example.blog.controller.auth;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController 
@RequestMapping ("/api/auth")
public class Login {
    public static void main(String[] args) {
        
    }
    @PostMapping("login")
    public String postMethodName(@RequestBody String entity) {
        
        return entity;
    }
    
    
}
