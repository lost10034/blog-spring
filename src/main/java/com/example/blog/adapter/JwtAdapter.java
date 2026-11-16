package com.example.blog.adapter;

import java.util.Map;

import lombok.Builder;

@Builder 
public record JwtAdapter (String id,int millis,Map<String,Object> claim) {
    
}
