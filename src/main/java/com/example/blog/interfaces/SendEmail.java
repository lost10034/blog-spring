package com.example.blog.interfaces;

import com.example.blog.enums.SendStatus;

public interface SendEmail {
     public  SendStatus SendEmailCaptcha (String emailAddress,String sendAddress,String template);
}
