package com.example.blog.interfaces;

import java.util.concurrent.CompletableFuture;

import com.example.blog.adapter.AliYunAdapter;
import com.example.blog.adapter.SendCloudAdapter;
import com.example.blog.enums.SendStatus;

public interface SendEmail {
     public  CompletableFuture<SendStatus> SendEmailCaptchaSendCloud(SendCloudAdapter adapter)throws Exception;
      public  CompletableFuture<SendStatus> SendEmailCaptchaAliYun(AliYunAdapter aliYunAdapter) throws Exception;
}
