package com.realtimetilegame.user.application;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.realtimetilegame.auth.application.AuthService;
import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.user.domain.AvatarType;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;

@Service
public class UserProfileService {
    private final UserRepository userRepository;
    private final Clock clock;

    public UserProfileService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public User get(long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public User update(long userId, String nickname, String avatarType) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String normalizedNickname = AuthService.normalizeNickname(nickname);
        if (!user.nickname().equalsIgnoreCase(normalizedNickname)
            && userRepository.existsByNicknameIgnoreCase(normalizedNickname)) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }
        user.updateProfile(normalizedNickname, AvatarType.from(avatarType), LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }
    }
}
