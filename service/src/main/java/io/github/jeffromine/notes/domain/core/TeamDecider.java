package io.github.jeffromine.notes.domain.core;

import io.github.jeffromine.notes.domain.core.Rejection.Forbidden;
import io.github.jeffromine.notes.domain.core.Rejection.Invalid;
import io.github.jeffromine.notes.domain.core.Rejection.LastAdmin;
import io.github.jeffromine.notes.domain.core.Rejection.NotFound;
import io.github.jeffromine.notes.domain.core.TeamCommand.CreateTeam;
import io.github.jeffromine.notes.domain.core.TeamCommand.PutMember;
import io.github.jeffromine.notes.domain.core.TeamCommand.RemoveMember;
import io.github.jeffromine.notes.domain.core.TeamEvent.MemberPut;
import io.github.jeffromine.notes.domain.core.TeamEvent.MemberRemoved;
import io.github.jeffromine.notes.domain.core.TeamEvent.TeamCreated;
import io.github.jeffromine.notes.domain.model.Membership;
import io.github.jeffromine.notes.domain.model.Membership.Role;
import io.github.jeffromine.notes.domain.model.Team;

import java.util.List;
import java.util.Optional;

/**
 * The functional core for teams and membership. Same shape as {@link NoteDecider}.
 *
 * <p>Rules: the creator becomes the first admin; only admins manage members, except that any member may remove
 * themselves; a team is invisible (not found) to non-members; the last admin can be neither demoted nor removed.
 */
public final class TeamDecider {

    private TeamDecider() {}

    // ------------------------------------------------------------------ shared helpers

    /** The team, if it exists and the actor belongs to it. */
    private static Optional<Team> visible(TeamState state, String actor) {
        return state.team().filter(t -> AccessPolicy.isMember(state.memberships(), actor));
    }

    private static NoteStateFree evolveMemberPut(TeamState state, Membership membership) {
        List<Membership> others = state.memberships().stream()
                .filter(m -> !m.username().equals(membership.username()))
                .toList();
        List<Membership> next = new java.util.ArrayList<>(others);
        next.add(membership);
        return new NoteStateFree(new TeamState(state.team(), next, state.knownUsers()));
    }

    /** Wrapper so the shared fold has one named home; unwrapped by the dispatcher. */
    private record NoteStateFree(TeamState state) {}

    // ------------------------------------------------------------------ CreateTeam

    static final class CreateTeamBlock {
        static Optional<Rejection> guard(CreateTeam c, TeamState state, Limits limits) {
            if (state.team().isPresent()) {
                return Optional.of(Invalid.of("teamId", "a team with this id already exists"));
            }
            if (c.name() == null || c.name().isBlank()) {
                return Optional.of(Invalid.of("name", "must not be blank"));
            }
            if (c.name().length() > limits.teamNameMaxChars()) {
                return Optional.of(Invalid.of("name", "must be at most " + limits.teamNameMaxChars() + " characters"));
            }
            return Optional.empty();
        }

        static List<TeamEvent> decide(CreateTeam c) {
            Team team = new Team(c.teamId(), c.name().strip(), c.at());
            return List.of(
                    new TeamCreated(team, c.actor()),
                    new MemberPut(new Membership(c.teamId(), c.actor(), Role.ADMIN), c.actor(), c.at()));
        }

        static TeamState evolve(TeamState state, TeamCreated e) {
            return new TeamState(Optional.of(e.team()), state.memberships(), state.knownUsers());
        }
    }

    // ------------------------------------------------------------------ PutMember

    static final class PutMemberBlock {
        static Optional<Rejection> guard(PutMember c, TeamState state) {
            if (visible(state, c.actor()).isEmpty()) {
                return Optional.of(new NotFound("team"));
            }
            if (!AccessPolicy.isAdmin(state.memberships(), c.actor())) {
                return Optional.of(new Forbidden("only a team admin manages members"));
            }
            if (c.role() == null) {
                return Optional.of(Invalid.of("role", "must be ADMIN or MEMBER"));
            }
            if (!state.knownUsers().contains(c.username())) {
                return Optional.of(new NotFound("user"));
            }
            boolean demotingLastAdmin = c.role() == Role.MEMBER
                    && state.membership(c.username()).map(m -> m.role() == Role.ADMIN).orElse(false)
                    && state.adminCount() == 1;
            if (demotingLastAdmin) {
                return Optional.of(new LastAdmin(c.teamId()));
            }
            return Optional.empty();
        }

        /** Same role already: nothing happened. */
        static List<TeamEvent> decide(PutMember c, TeamState state) {
            boolean unchanged = state.membership(c.username()).map(m -> m.role() == c.role()).orElse(false);
            if (unchanged) {
                return List.of();
            }
            return List.of(new MemberPut(new Membership(c.teamId(), c.username(), c.role()), c.actor(), c.at()));
        }
    }

    // ------------------------------------------------------------------ RemoveMember

    static final class RemoveMemberBlock {
        static Optional<Rejection> guard(RemoveMember c, TeamState state) {
            if (visible(state, c.actor()).isEmpty()) {
                return Optional.of(new NotFound("team"));
            }
            boolean leavingSelf = c.username().equals(c.actor());
            if (!leavingSelf && !AccessPolicy.isAdmin(state.memberships(), c.actor())) {
                return Optional.of(new Forbidden("only a team admin removes other members"));
            }
            Optional<Membership> target = state.membership(c.username());
            if (target.isEmpty()) {
                return Optional.of(new NotFound("membership"));
            }
            if (target.get().role() == Role.ADMIN && state.adminCount() == 1) {
                return Optional.of(new LastAdmin(c.teamId()));
            }
            return Optional.empty();
        }

        static List<TeamEvent> decide(RemoveMember c) {
            return List.of(new MemberRemoved(c.teamId(), c.username(), c.actor(), c.at()));
        }

        static TeamState evolve(TeamState state, MemberRemoved e) {
            List<Membership> next = state.memberships().stream()
                    .filter(m -> !m.username().equals(e.username()))
                    .toList();
            return new TeamState(state.team(), next, state.knownUsers());
        }
    }

    // ------------------------------------------------------------------ dispatchers

    public static Optional<Rejection> guard(TeamCommand command, TeamState state, Limits limits) {
        return switch (command) {
            case CreateTeam c -> CreateTeamBlock.guard(c, state, limits);
            case PutMember c -> PutMemberBlock.guard(c, state);
            case RemoveMember c -> RemoveMemberBlock.guard(c, state);
        };
    }

    public static List<TeamEvent> decide(TeamCommand command, TeamState state) {
        return switch (command) {
            case CreateTeam c -> CreateTeamBlock.decide(c);
            case PutMember c -> PutMemberBlock.decide(c, state);
            case RemoveMember c -> RemoveMemberBlock.decide(c);
        };
    }

    public static TeamState evolve(TeamState state, TeamEvent event) {
        return switch (event) {
            case TeamCreated e -> CreateTeamBlock.evolve(state, e);
            case MemberPut e -> evolveMemberPut(state, e.membership()).state();
            case MemberRemoved e -> RemoveMemberBlock.evolve(state, e);
        };
    }

    public static Decision<TeamEvent, TeamState> apply(TeamCommand command, TeamState state, Limits limits) {
        Optional<Rejection> refusal = guard(command, state, limits);
        if (refusal.isPresent()) {
            return new Decision.Rejected<>(refusal.get());
        }
        List<TeamEvent> events = decide(command, state);
        TeamState next = state;
        for (TeamEvent event : events) {
            next = evolve(next, event);
        }
        return new Decision.Accepted<>(events, next);
    }
}
