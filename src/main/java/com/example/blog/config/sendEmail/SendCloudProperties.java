package com.example.blog.config.sendEmail;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

import com.sendcloud.sdk.config.Credential;

@ConfigurationProperties(prefix = "sendcloud")
public class  SendCloudProperties {
    private  final String apiKey;
 private final String apiUser;
 private SendCloudProperties(@Value ("${sendcloud.api-user}") String apiUser,@Value ("${sendcloud.api-key}") String apiKey){
    this.apiUser=apiUser;
    this.apiKey=apiKey;
 }
 public  Credential credential()throws Exception{
    if (apiKey==null||apiUser==null||apiKey.isBlank()||apiUser.isBlank()){
        throw new Exception("未配置api-key和api-user");
    }
    return new  Credential(apiUser, apiKey);
 }
} 