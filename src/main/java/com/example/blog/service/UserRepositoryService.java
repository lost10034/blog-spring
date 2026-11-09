package com.example.blog.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.blog.repository.User.UserRepository;



@Service
public class UserRepositoryService {
    private final UserRepository userRepository;

    public UserRepositoryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public  void updateStatus(UUID id,LocalDateTime time,boolean status){
        userRepository.statusUpdate(id, status, time);
    } 
}
