package com.example.blog.adapter;

import lombok.Builder;

@Builder 
public record AliYunAdapter(String template,String accountName,int accountType,String ReplyAddress,String toAddress,String forName,String subject,String limitValue) {
}