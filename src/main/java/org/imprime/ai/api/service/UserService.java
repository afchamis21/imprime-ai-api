package org.imprime.ai.api.service;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.repo.cache.UserInMemoryCache;
import org.imprime.ai.api.repo.db.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserInMemoryCache userInMemoryCache;

    public Optional<User> findUserByEmail(@Nullable String email) {
        log.debug("Search user with email [{}]", email);
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        return userRepository.findUserByEmail(email);
    }

    public Optional<User> findUserById(Long userId) {
        return userRepository.findById(userId);
    }

    public Optional<User> findUserByGuid(String userGuid) {
        return userInMemoryCache.findUserByGuid(userGuid);
    }
}
