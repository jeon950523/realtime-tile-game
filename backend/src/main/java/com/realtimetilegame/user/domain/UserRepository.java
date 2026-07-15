package com.realtimetilegame.user.domain;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(long userId);

    Optional<User> findByIdForUpdate(long userId);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByNicknameIgnoreCase(String nickname);

    User save(User user);

    User saveAndFlush(User user);
}
