package com.example.blog.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.blog.entity.User;
import com.example.blog.repository.UserRepository;

import lombok.Getter;



@Service
@Getter 
public class UserRepositoryService {
    private final UserRepository userRepository;
    private final Logger logger =LoggerFactory.getLogger(UserRepositoryService.class);
    public UserRepositoryService(UserRepository userRepository) {
        this.userRepository = userRepository;

    }

    public  void updateStatus(UUID id,LocalDateTime time,boolean status){
        userRepository.statusUpdate(id, status, time);
    } 
    public void save(User user){
        userRepository.save(user);
    }
}
