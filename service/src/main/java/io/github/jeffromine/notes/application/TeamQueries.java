package io.github.jeffromine.notes.application;

import io.github.jeffromine.notes.application.port.MembershipRepository;
import io.github.jeffromine.notes.application.port.TeamRepository;
import io.github.jeffromine.notes.application.port.UserRepository;
import io.github.jeffromine.notes.domain.core.AccessPolicy;
import io.github.jeffromine.notes.domain.core.Rejection;
import io.github.jeffromine.notes.domain.model.Membership;
import io.github.jeffromine.notes.domain.model.Team;
import io.github.jeffromine.notes.domain.model.User;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read paths for the signed-in user and their teams. */
public final class TeamQueries {

    public record TeamMembership(Team team, Membership.Role role) {}

    public record Me(User user, List<TeamMembership> teams) {}

    private final UserRepository users;
    private final TeamRepository teams;
    private final MembershipRepository memberships;

    public TeamQueries(UserRepository users, TeamRepository teams, MembershipRepository memberships) {
        this.users = users;
        this.teams = teams;
        this.memberships = memberships;
    }

    public Me me(String username) {
        User user = users.findByUsername(username)
                .orElseThrow(() -> new RejectionException(new Rejection.NotFound("user")));
        List<Membership> mine = memberships.byUser(username);
        Map<UUID, Team> byId = teams.findAllById(mine.stream().map(Membership::teamId).toList()).stream()
                .collect(Collectors.toMap(Team::id, Function.identity()));
        List<TeamMembership> teamMemberships = mine.stream()
                .filter(m -> byId.containsKey(m.teamId()))
                .map(m -> new TeamMembership(byId.get(m.teamId()), m.role()))
                .sorted((a, b) -> a.team().name().compareToIgnoreCase(b.team().name()))
                .toList();
        return new Me(user, teamMemberships);
    }

    /** @throws RejectionException NotFound when the team does not exist or the actor is not a member */
    public Team team(String actor, UUID teamId) {
        List<Membership> members = memberships.byTeam(teamId);
        return teams.findById(teamId)
                .filter(t -> AccessPolicy.isMember(members, actor))
                .orElseThrow(() -> new RejectionException(new Rejection.NotFound("team")));
    }

    public List<Membership> members(String actor, UUID teamId) {
        team(actor, teamId);
        return memberships.byTeam(teamId).stream()
                .sorted((a, b) -> a.username().compareTo(b.username()))
                .toList();
    }
}
