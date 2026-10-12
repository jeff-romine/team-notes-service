package io.github.jeffromine.notes.adapter.persistence.memory;

import io.github.jeffromine.notes.application.port.MembershipRepository;
import io.github.jeffromine.notes.domain.model.Membership;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InMemoryMembershipRepository implements MembershipRepository {

    private final InMemoryStore store;

    public InMemoryMembershipRepository(InMemoryStore store) {
        this.store = store;
    }

    @Override
    public List<Membership> byTeam(UUID teamId) {
        return store.read(s -> List.copyOf(s.membershipsByTeam.getOrDefault(teamId, Map.of()).values()));
    }

    @Override
    public List<Membership> byUser(String username) {
        return store.read(s -> s.membershipsByTeam.values().stream()
                .map(m -> m.get(username))
                .filter(m -> m != null)
                .toList());
    }

    @Override
    public void put(Membership membership) {
        store.mutate(s -> s.membershipsByTeam.computeIfAbsent(membership.teamId(), k -> new HashMap<>())
                .put(membership.username(), membership));
    }

    @Override
    public void remove(UUID teamId, String username) {
        store.mutate(s -> {
            Map<String, Membership> members = s.membershipsByTeam.get(teamId);
            if (members != null) {
                members.remove(username);
            }
        });
    }
}
