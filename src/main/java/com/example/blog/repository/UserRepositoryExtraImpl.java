package com.example.blog.repository;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.blog.enums.StatusUpdate;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

public class UserRepositoryExtraImpl implements UserRepositoryExtra {

    private final EntityManager entityManager;

    public UserRepositoryExtraImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public StatusUpdate statusUpdate(UUID id, boolean status, LocalDateTime updateTime) {
        int result = entityManager.createQuery("""
                              update User u
                                          set u.status = :status, u.updateTime = :updateTime
                                         where u.id = :id""").setParameter("updateTime", updateTime).setParameter("status", status)
                .setParameter("id", id).executeUpdate();
        return result == 0 ? StatusUpdate.NOTFOUND : StatusUpdate.UPDATECOMPLETE;
    }
}
