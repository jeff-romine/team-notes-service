package io.github.jeffromine.notes.application.port;

import io.github.jeffromine.notes.domain.model.User;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findByUsername(String username);

    /** @return true when inserted, false when a user with that username already existed (nothing changed) */
    boolean insertIfAbsent(User user);
}
