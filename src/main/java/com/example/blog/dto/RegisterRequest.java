package com.example.blog.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * RegisterRequest
 */
public record RegisterRequest (
     @NotBlank 
    @Size (max = 24,min = 1)
      String userName,
      @NotBlank 
      @Size (max = 24,min = 6)
      String passWord,
      @Email 
      @NotBlank 
      String email,
      @NotBlank 
      @Size (max = 6,min = 6)
     String captcha
){
     
    
}