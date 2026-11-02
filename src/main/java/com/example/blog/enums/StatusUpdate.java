package com.example.blog.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import lombok.AllArgsConstructor;

@AllArgsConstructor 
public enum StatusUpdate {
  NOTFOUND(0),
  UPDATECOMPLETE(1);
  private  final int value;
   @JsonValue 
   public  int getValue(){
    return  value;
   }
    
} 