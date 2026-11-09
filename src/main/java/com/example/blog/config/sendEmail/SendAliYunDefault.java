package com.example.blog.config.sendEmail;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class SendAliYunDefault {
    private static final String REGION_ID = "cn-hangzhou";

    @Bean
    public com.aliyun.dm20151123.Client dmClient()throws Exception{
         return new com.aliyun.dm20151123.Client(new com.aliyun.teaopenapi.models.Config()
                 .setRegionId(REGION_ID)
                 .setCredential(new com.aliyun.credentials.Client()));
    }

}
