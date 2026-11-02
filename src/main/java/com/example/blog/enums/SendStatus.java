package com.example.blog.enums;


import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;
@AllArgsConstructor 
public enum SendStatus {
    SEND_TIMEOUT(504),
    SEND_SUCCESS(200),
    SEND_FAULT(500);
    private  final int value;
    @JsonValue 
    public  int getValue(){
        return  value;
    }

    
}
