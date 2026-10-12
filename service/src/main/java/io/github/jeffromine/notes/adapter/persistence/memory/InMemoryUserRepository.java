package io.github.jeffromine.notes.adapter.persistence.memory;

import io.github.jeffromine.notes.application.port.UserRepository;
import io.github.jeffromine.notes.domain.model.User;

import java.util.Optional;

public final class InMemoryUserRepository implements UserRepository {

    private final InMemoryStore store;

    public InMemoryUserRepository(InMemoryStore store) {
        this.store = store;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return store.read(s -> Optional.ofNullable(s.users.get(username)));
    }

    @Override
    public boolean insertIfAbsent(User user) {
        return store.write(s -> s.users.putIfAbsent(user.username(), user) == null);
    }
}
