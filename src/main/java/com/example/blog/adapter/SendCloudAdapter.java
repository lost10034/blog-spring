package com.example.blog.adapter;

import com.sendcloud.sdk.model.TextContent.ScContentType;

import lombok.Builder;
@Builder 
public record SendCloudAdapter(String address,String sendAddres,String template,String formName,String subject,ScContentType conteType,
    String text,String limitValue
 ) {
}
 