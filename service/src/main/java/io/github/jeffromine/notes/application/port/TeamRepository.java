package io.github.jeffromine.notes.application.port;

import io.github.jeffromine.notes.domain.model.Team;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepository {
    Optional<Team> findById(UUID id);

    List<Team> findAllById(Collection<UUID> ids);

    /** @return true when inserted, false when a team with that id already existed (nothing changed) */
    boolean insertIfAbsent(Team team);
}
