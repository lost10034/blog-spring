package com.example.blog.adapter;

public record BloomFilterServiceAdapter (String key,String[] items,double errorRate,long capacity,int timeoutSeconds,int expansion,boolean nonScaling) {
    
}
