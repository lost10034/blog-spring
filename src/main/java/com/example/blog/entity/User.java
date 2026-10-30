package com.example.blog.entity;


import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder 
@Data 
@AllArgsConstructor 
@NoArgsConstructor 
@Entity 
@Table(name = "users",indexes = {@Index (name="idx_name",columnList = "use_name")
,@Index (name="idx_status",columnList = "use_status")})
public class User {
    @Id 
    @GeneratedValue 
    private  UUID id;
    @Column (name ="use_name",unique = true,nullable = true,length = 24)
    private  String userName;
    @Column(name = "pass_word",nullable = true,length =255 )
    private  String passWord;
    @Column (name = "create_time",nullable = true)
    private  LocalDateTime createTime;
    @Column (name = "update_time",nullable = true)
    private  LocalDateTime updateTime;
    @Column (name = "use_email",unique = true,nullable = true)
    private  String email;
    @Column (name = "use_status",nullable = true)
    private  boolean status;

}
