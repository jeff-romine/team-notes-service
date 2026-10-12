package io.github.jeffromine.notes.application;

import io.github.jeffromine.notes.domain.core.Rejection;
import io.github.jeffromine.notes.domain.core.TeamCommand.CreateTeam;
import io.github.jeffromine.notes.domain.core.TeamCommand.PutMember;
import io.github.jeffromine.notes.domain.core.TeamCommand.RemoveMember;
import io.github.jeffromine.notes.domain.core.TeamState;
import io.github.jeffromine.notes.domain.model.Membership;
import io.github.jeffromine.notes.support.ShellHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static io.github.jeffromine.notes.support.Fixtures.ALICE;
import static io.github.jeffromine.notes.support.Fixtures.BOB;
import static io.github.jeffromine.notes.support.Fixtures.CAROL;
import static io.github.jeffromine.notes.support.Fixtures.PLATFORM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamCommandsTest {

    @TempDir
    Path dir;
    ShellHarness h;

    @BeforeEach
    void setUp() {
        h = new ShellHarness(dir);
    }

    @Test
    void createThenManageMembers() {
        UUID id = h.context.newId();
        TeamState created = h.teamCommands.handle(new CreateTeam(CAROL, h.context.now(), id, "research"));
        assertThat(created.team()).isPresent();
        assertThat(h.memberships.byTeam(id)).containsExactly(new Membership(id, CAROL, Membership.Role.ADMIN));
        h.teamCommands.handle(new PutMember(CAROL, h.context.now(), id, ALICE, Membership.Role.MEMBER));
        assertThat(h.teamQueries.members(ALICE, id)).hasSize(2);
        assertThatThrownBy(() -> h.teamCommands.handle(new PutMember(CAROL, h.context.now(), id, "nobody", Membership.Role.MEMBER)))
                .isInstanceOf(RejectionException.class)
                .extracting(e -> ((RejectionException) e).rejection()).isEqualTo(new Rejection.NotFound("user"));
        assertThatThrownBy(() -> h.teamCommands.handle(new RemoveMember(CAROL, h.context.now(), id, CAROL)))
                .extracting(e -> ((RejectionException) e).rejection()).isInstanceOf(Rejection.LastAdmin.class);
        h.teamCommands.handle(new RemoveMember(ALICE, h.context.now(), id, ALICE));
        assertThat(h.memberships.byUser(ALICE)).extracting(Membership::teamId).containsExactly(PLATFORM);
    }

    @Test
    void meListsTeamsSortedByName() {
        TeamQueries.Me me = h.teamQueries.me(BOB);
        assertThat(me.teams()).extracting(t -> t.team().name()).containsExactly("data", "platform");
        assertThat(me.teams()).extracting(TeamQueries.TeamMembership::role)
                .containsExactly(Membership.Role.ADMIN, Membership.Role.MEMBER);
        assertThatThrownBy(() -> h.teamQueries.team(CAROL, PLATFORM)).isInstanceOf(RejectionException.class);
    }
}
