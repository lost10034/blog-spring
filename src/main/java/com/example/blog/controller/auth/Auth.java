package com.example.blog.controller.auth;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.blog.service.CaptchaService;
import com.example.blog.service.EmailSendService;
import com.example.blog.service.PassWordHashService;
import com.example.blog.service.TemplateService;

import jakarta.validation.Valid;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

import com.example.blog.adapter.AliYunAdapter;
import com.example.blog.dto.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController 
@RequestMapping ("/api/v1/auth")
public class Auth {
    private  final PassWordHashService passWordHashService;
    private  final CaptchaService captchaService;
    private  final TemplateService templateService;
    private  final EmailSendService emailSendService;
    private Auth(PassWordHashService passWordHashService,CaptchaService captchaService,TemplateService templateService,EmailSendService emailSendService){
        this.passWordHashService=passWordHashService;
        this.captchaService=captchaService;
        this.templateService=templateService;
        this.emailSendService=emailSendService;
        
    }
    @PostMapping("/login")
    public String postMethodLogin(@RequestBody String entity) {
        
        return entity;
    }
    @PostMapping("/register")
    public String postMethodRegisrer(@Valid @RequestBody RegisterRequest registerRequest  ) {
         try{
           CompletableFuture<String> code=passWordHashService.hash(registerRequest.passWord());
            int captcha= captchaService.emailCaptcha();
            String template=templateService.renderEmail(captcha, registerRequest.userName(), captcha);
            AliYunAdapter adapter=AliYunAdapter.builder().template(template).accountName(registerRequest.userName()).accountType(1)
            .toAddress(registerRequest.email()).forName("MSZ-010").subject("验证码").limitValue(String.valueOf(captcha)).build();
           
            emailSendService.SendEmailCaptchaAliYun(adapter);
        }catch(RejectedExecutionException e){
            return "";
        }catch(Exception e){
               
            }
        return  "";
    }
    
    
    
}
