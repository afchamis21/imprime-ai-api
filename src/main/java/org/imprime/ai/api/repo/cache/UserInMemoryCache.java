package org.imprime.ai.api.repo.cache;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.imprime.ai.api.model.User;
import org.imprime.ai.api.model.enums.StatusCd;
import org.imprime.ai.api.repo.db.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Normally I'd have made a cache interface, an abstract class, an ICacheable, etc. etc.
 * But I'm in a hurry.
 *
 * As the app grows, this would be better stored on REDIS but it works for now!
 *
 * Honestly, In Memmory is probably OK, but I can make this better later for sure
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UserInMemoryCache {
    private final UserRepository userRepository;

    private ConcurrentHashMap<String, User> usersByGuid = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        List<User> users = userRepository.findAllByStatus(StatusCd.ACTIVE);

        ConcurrentHashMap<String, User> usersByGuid = new ConcurrentHashMap<>();
        users.forEach(user -> usersByGuid.put(user.getGuid(), user));
        this.usersByGuid = usersByGuid;
    }

    public Optional<User> findUserByGuid(String guid) {
        return Optional.ofNullable(usersByGuid.get(guid));
    }

    public void put(User user) {
        usersByGuid.put(user.getGuid(), user);
    }

    public void delete(String guid) {
        usersByGuid.remove(guid);
    }
}
