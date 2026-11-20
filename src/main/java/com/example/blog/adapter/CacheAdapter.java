package com.example.blog.adapter;

import lombok.Builder;

@Builder 
public record CacheAdapter(String key, String value, int expireSeconds, int timeoutSeconds, boolean nx,String[] keys) {
    
}
