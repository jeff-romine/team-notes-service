package io.github.jeffromine.notes.application.port;

import io.github.jeffromine.notes.domain.model.Membership;

import java.util.List;
import java.util.UUID;

public interface MembershipRepository {
    List<Membership> byTeam(UUID teamId);

    List<Membership> byUser(String username);

    /** Insert or replace the role. */
    void put(Membership membership);

    void remove(UUID teamId, String username);
}
