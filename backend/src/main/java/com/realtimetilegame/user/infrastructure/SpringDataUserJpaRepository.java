package com.realtimetilegame.user.infrastructure;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.realtimetilegame.user.domain.User;

interface SpringDataUserJpaRepository extends JpaRepository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") long userId);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByNicknameIgnoreCase(String nickname);
}
