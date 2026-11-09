package com.example.blog.repository.User;


import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.blog.entity.User;


public interface UserRepository extends JpaRepository<User, UUID>, UserRepositoryExtra{
      }
