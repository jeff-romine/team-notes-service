package io.github.jeffromine.notes.application;

import io.github.jeffromine.notes.application.port.MembershipRepository;
import io.github.jeffromine.notes.application.port.TeamRepository;
import io.github.jeffromine.notes.application.port.UserRepository;
import io.github.jeffromine.notes.domain.core.Decision;
import io.github.jeffromine.notes.domain.core.Limits;
import io.github.jeffromine.notes.domain.core.Rejection;
import io.github.jeffromine.notes.domain.core.TeamCommand;
import io.github.jeffromine.notes.domain.core.TeamDecider;
import io.github.jeffromine.notes.domain.core.TeamEvent;
import io.github.jeffromine.notes.domain.core.TeamEvent.MemberPut;
import io.github.jeffromine.notes.domain.core.TeamEvent.MemberRemoved;
import io.github.jeffromine.notes.domain.core.TeamEvent.TeamCreated;
import io.github.jeffromine.notes.domain.core.TeamState;
import io.github.jeffromine.notes.domain.model.Team;

import java.util.Optional;
import java.util.Set;

/** The imperative shell for team commands. Same shape as {@link NoteCommands}; teams have no effects. */
public final class TeamCommands {

    private final TeamRepository teams;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final Limits limits;

    public TeamCommands(TeamRepository teams, MembershipRepository memberships, UserRepository users, Limits limits) {
        this.teams = teams;
        this.memberships = memberships;
        this.users = users;
        this.limits = limits;
    }

    public TeamState handle(TeamCommand command) {
        TeamState state = prepare(command);
        return switch (TeamDecider.apply(command, state, limits)) {
            case Decision.Rejected<TeamEvent, TeamState> r -> throw new RejectionException(r.rejection());
            case Decision.Accepted<TeamEvent, TeamState> a -> {
                for (TeamEvent event : a.events()) {
                    persist(event);
                }
                yield a.state();
            }
        };
    }

    public TeamState prepare(TeamCommand command) {
        Optional<Team> team = teams.findById(command.teamId());
        Set<String> known = switch (command) {
            case TeamCommand.PutMember c -> users.findByUsername(c.username()).map(u -> Set.of(u.username())).orElse(Set.of());
            case TeamCommand.CreateTeam c -> Set.of();
            case TeamCommand.RemoveMember c -> Set.of();
        };
        return new TeamState(team, memberships.byTeam(command.teamId()), known);
    }

    private void persist(TeamEvent event) {
        switch (event) {
            case TeamCreated e -> {
                if (!teams.insertIfAbsent(e.team())) {
                    throw new RejectionException(Rejection.Invalid.of("teamId", "a team with this id already exists"));
                }
            }
            case MemberPut e -> memberships.put(e.membership());
            case MemberRemoved e -> memberships.remove(e.teamId(), e.username());
        }
    }
}
