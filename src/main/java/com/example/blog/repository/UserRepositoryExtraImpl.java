package com.example.blog.repository;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.blog.enums.StatusUpdate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

public record UserRepositoryExtraImpl (@PersistenceContext EntityManager entityManager  )implements UserRepositoryExtra  {
     @Override 
     @Transactional 
     public  StatusUpdate  statusUpdate(UUID id,boolean status,LocalDateTime updateTime){
    int result=entityManager.createQuery("""
                          update User u
                                      set u.status = :status, u.updateTime = :updateTime
                                     where u.id = :id""").setParameter("updateTime", updateTime).setParameter("status", status)
                                     .setParameter("id",id ).executeUpdate();
               return  result==0?StatusUpdate.NOTFOUND:StatusUpdate.UPDATECOMPLETE;
                    
     }
}

