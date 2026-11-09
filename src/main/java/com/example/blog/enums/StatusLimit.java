package com.example.blog.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;

@AllArgsConstructor 
public enum StatusLimit {
    SUCCESS(1),
    FAULT(0),
    PENDING(2),
    TIMEOUT(3),
    CANCEL(4);
    private  final int value;
    @JsonValue 
    public  int getValue(){
        return  value;
    }
    
}
