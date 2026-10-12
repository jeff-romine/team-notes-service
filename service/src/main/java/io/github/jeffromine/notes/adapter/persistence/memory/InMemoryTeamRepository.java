package io.github.jeffromine.notes.adapter.persistence.memory;

import io.github.jeffromine.notes.application.port.TeamRepository;
import io.github.jeffromine.notes.domain.model.Team;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryTeamRepository implements TeamRepository {

    private final InMemoryStore store;

    public InMemoryTeamRepository(InMemoryStore store) {
        this.store = store;
    }

    @Override
    public Optional<Team> findById(UUID id) {
        return store.read(s -> Optional.ofNullable(s.teams.get(id)));
    }

    @Override
    public List<Team> findAllById(Collection<UUID> ids) {
        return store.read(s -> ids.stream().map(s.teams::get).filter(Objects::nonNull).toList());
    }

    @Override
    public boolean insertIfAbsent(Team team) {
        return store.write(s -> s.teams.putIfAbsent(team.id(), team) == null);
    }
}
