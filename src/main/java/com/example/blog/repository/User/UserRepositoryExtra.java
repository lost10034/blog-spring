package com.example.blog.repository.User;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.blog.enums.StatusUpdate;

public interface UserRepositoryExtra {
    StatusUpdate statusUpdate(UUID userId, boolean status, LocalDateTime time);
    
}
