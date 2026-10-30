package com.example.blog.repository;
import java.util.List;
import java.util.UUID;
import com.example.blog.entity.User;
public interface UserRepository {
     User save(User user);
      User findById(UUID id);
      List<User> findAll();
      void  deleteById(UUID id);
      User update(User user );
} 
