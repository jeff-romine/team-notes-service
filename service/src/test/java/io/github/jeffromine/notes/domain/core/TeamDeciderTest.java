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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static io.github.jeffromine.notes.domain.core.DeciderAssert.givenTeam;
import static io.github.jeffromine.notes.support.Fixtures.ALICE;
import static io.github.jeffromine.notes.support.Fixtures.BOB;
import static io.github.jeffromine.notes.support.Fixtures.CAROL;
import static io.github.jeffromine.notes.support.Fixtures.PLATFORM;
import static io.github.jeffromine.notes.support.Fixtures.T0;
import static io.github.jeffromine.notes.support.Fixtures.T1;
import static io.github.jeffromine.notes.support.Fixtures.member;
import static io.github.jeffromine.notes.support.Fixtures.platform;
import static org.assertj.core.api.Assertions.assertThat;

class TeamDeciderTest {

    static final TeamState ALICE_ADMIN_BOB_MEMBER = new TeamState(Optional.of(platform()),
            List.of(member(PLATFORM, ALICE, Role.ADMIN), member(PLATFORM, BOB, Role.MEMBER)),
            Set.of(ALICE, BOB, CAROL));

    @Test
    void creatingATeamMakesTheCreatorItsFirstAdmin() {
        List<TeamEvent> events = givenTeam(TeamState.absent())
                .when(new CreateTeam(ALICE, T0, PLATFORM, " platform "))
                .expectEvents(TeamCreated.class, MemberPut.class);
        assertThat(((TeamCreated) events.get(0)).team().name()).isEqualTo("platform");
        assertThat(((MemberPut) events.get(1)).membership().role()).isEqualTo(Role.ADMIN);
        TeamState folded = events.stream().reduce(TeamState.absent(), TeamDecider::evolve, (a, b) -> b);
        assertThat(folded.team()).isPresent();
        assertThat(folded.adminCount()).isEqualTo(1);
    }

    @Test
    void blankOrLongNamesAreInvalid() {
        givenTeam(TeamState.absent()).when(new CreateTeam(ALICE, T0, PLATFORM, " ")).expectRejected(Invalid.class);
        givenTeam(TeamState.absent()).when(new CreateTeam(ALICE, T0, PLATFORM, "x".repeat(101))).expectRejected(Invalid.class);
    }

    @Test
    void adminAddsAKnownUser_sameRoleIsANoOp() {
        givenTeam(ALICE_ADMIN_BOB_MEMBER).when(new PutMember(ALICE, T1, PLATFORM, CAROL, Role.MEMBER)).expectEvents(MemberPut.class);
        givenTeam(ALICE_ADMIN_BOB_MEMBER).when(new PutMember(ALICE, T1, PLATFORM, BOB, Role.MEMBER)).expectNoEvents();
        givenTeam(ALICE_ADMIN_BOB_MEMBER).when(new PutMember(ALICE, T1, PLATFORM, BOB, Role.ADMIN)).expectEvents(MemberPut.class);
    }

    @Test
    void membersCannotManage_outsidersCannotSee_unknownUsersAreNotFound() {
        givenTeam(ALICE_ADMIN_BOB_MEMBER).when(new PutMember(BOB, T1, PLATFORM, CAROL, Role.MEMBER)).expectRejected(Forbidden.class);
        givenTeam(ALICE_ADMIN_BOB_MEMBER).when(new PutMember(CAROL, T1, PLATFORM, CAROL, Role.ADMIN)).expectRejected(NotFound.class);
        givenTeam(ALICE_ADMIN_BOB_MEMBER).when(new PutMember(ALICE, T1, PLATFORM, "dave", Role.MEMBER)).expectRejected(NotFound.class);
    }

    @Test
    void theLastAdminCanBeNeitherDemotedNorRemoved() {
        LastAdmin demote = givenTeam(ALICE_ADMIN_BOB_MEMBER)
                .when(new PutMember(ALICE, T1, PLATFORM, ALICE, Role.MEMBER)).expectRejected(LastAdmin.class);
        assertThat(demote.teamId()).isEqualTo(PLATFORM);
        givenTeam(ALICE_ADMIN_BOB_MEMBER).when(new RemoveMember(ALICE, T1, PLATFORM, ALICE)).expectRejected(LastAdmin.class);
        TeamState twoAdmins = new TeamState(Optional.of(platform()),
                List.of(member(PLATFORM, ALICE, Role.ADMIN), member(PLATFORM, BOB, Role.ADMIN)), Set.of(ALICE, BOB));
        givenTeam(twoAdmins).when(new PutMember(ALICE, T1, PLATFORM, ALICE, Role.MEMBER)).expectEvents(MemberPut.class);
    }

    @Test
    void adminsRemoveOthers_anyMemberMayLeave() {
        givenTeam(ALICE_ADMIN_BOB_MEMBER).when(new RemoveMember(ALICE, T1, PLATFORM, BOB)).expectEvents(MemberRemoved.class);
        givenTeam(ALICE_ADMIN_BOB_MEMBER).when(new RemoveMember(BOB, T1, PLATFORM, BOB)).expectEvents(MemberRemoved.class);
        givenTeam(ALICE_ADMIN_BOB_MEMBER).when(new RemoveMember(BOB, T1, PLATFORM, ALICE)).expectRejected(Forbidden.class);
        givenTeam(ALICE_ADMIN_BOB_MEMBER).when(new RemoveMember(ALICE, T1, PLATFORM, CAROL)).expectRejected(NotFound.class);
    }

    static Stream<TeamEvent> everyEventType() {
        return Stream.of(
                new TeamCreated(platform(), ALICE),
                new MemberPut(new Membership(PLATFORM, CAROL, Role.MEMBER), ALICE, T1),
                new MemberRemoved(PLATFORM, BOB, ALICE, T1));
    }

    @Test
    void everyEventTypeIsCoveredByTheFoldTable() {
        assertThat(everyEventType().map(e -> (Class<?>) e.getClass()).distinct().count())
                .isEqualTo(TeamEvent.class.getPermittedSubclasses().length);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyEventType")
    void evolveReturnsANewValueAndLeavesItsInputUntouched(TeamEvent event) {
        TeamState snapshot = new TeamState(ALICE_ADMIN_BOB_MEMBER.team(), ALICE_ADMIN_BOB_MEMBER.memberships(),
                ALICE_ADMIN_BOB_MEMBER.knownUsers());
        TeamState after = TeamDecider.evolve(ALICE_ADMIN_BOB_MEMBER, event);
        assertThat(ALICE_ADMIN_BOB_MEMBER).isEqualTo(snapshot);
        assertThat(after).isNotSameAs(ALICE_ADMIN_BOB_MEMBER);
    }
}
