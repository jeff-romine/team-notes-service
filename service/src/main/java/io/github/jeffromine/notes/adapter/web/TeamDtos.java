package io.github.jeffromine.notes.adapter.web;

import io.github.jeffromine.notes.application.TeamQueries;
import io.github.jeffromine.notes.domain.model.Membership;
import io.github.jeffromine.notes.domain.model.Team;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TeamDtos {

    private TeamDtos() {}

    public record CreateTeamRequest(@NotNull String name) {}

    public record MembershipRequest(@NotNull Membership.Role role) {}

    public record TeamResponse(UUID id, String name, Instant createdAt) {
        public static TeamResponse of(Team t) {
            return new TeamResponse(t.id(), t.name(), t.createdAt());
        }
    }

    public record MemberResponse(String username, Membership.Role role) {
        public static MemberResponse of(Membership m) {
            return new MemberResponse(m.username(), m.role());
        }
    }

    public record MyTeam(UUID id, String name, Membership.Role role) {}

    public record MeResponse(String username, String displayName, boolean admin, List<MyTeam> teams) {
        public static MeResponse of(TeamQueries.Me me, boolean admin) {
            return new MeResponse(me.user().username(), me.user().displayName(), admin,
                    me.teams().stream().map(t -> new MyTeam(t.team().id(), t.team().name(), t.role())).toList());
        }
    }
}
