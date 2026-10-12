package io.github.jeffromine.notes.adapter.web;

import io.github.jeffromine.notes.adapter.web.TeamDtos.CreateTeamRequest;
import io.github.jeffromine.notes.adapter.web.TeamDtos.MeResponse;
import io.github.jeffromine.notes.adapter.web.TeamDtos.MemberResponse;
import io.github.jeffromine.notes.adapter.web.TeamDtos.MembershipRequest;
import io.github.jeffromine.notes.adapter.web.TeamDtos.TeamResponse;
import io.github.jeffromine.notes.application.CommandContext;
import io.github.jeffromine.notes.application.TeamCommands;
import io.github.jeffromine.notes.application.TeamQueries;
import io.github.jeffromine.notes.domain.core.TeamCommand;
import io.github.jeffromine.notes.domain.core.TeamState;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class TeamController {

    private final TeamCommands commands;
    private final TeamQueries queries;
    private final CommandContext context;

    public TeamController(TeamCommands commands, TeamQueries queries, CommandContext context) {
        this.commands = commands;
        this.queries = queries;
        this.context = context;
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return MeResponse.of(queries.me(authentication.getName()), admin);
    }

    @PostMapping("/teams")
    public ResponseEntity<TeamResponse> create(@Valid @RequestBody CreateTeamRequest body, Principal principal) {
        TeamState state = commands.handle(new TeamCommand.CreateTeam(principal.getName(), context.now(), context.newId(), body.name()));
        TeamResponse team = TeamResponse.of(state.team().orElseThrow());
        return ResponseEntity.created(URI.create("/api/teams/" + team.id())).body(team);
    }

    @GetMapping("/teams/{teamId}")
    public TeamResponse get(@PathVariable UUID teamId, Principal principal) {
        return TeamResponse.of(queries.team(principal.getName(), teamId));
    }

    @GetMapping("/teams/{teamId}/members")
    public List<MemberResponse> members(@PathVariable UUID teamId, Principal principal) {
        return queries.members(principal.getName(), teamId).stream().map(MemberResponse::of).toList();
    }

    @PutMapping("/teams/{teamId}/members/{username}")
    public MemberResponse putMember(@PathVariable UUID teamId, @PathVariable String username,
                                    @Valid @RequestBody MembershipRequest body, Principal principal) {
        TeamState state = commands.handle(new TeamCommand.PutMember(principal.getName(), context.now(), teamId, username, body.role()));
        return MemberResponse.of(state.membership(username).orElseThrow());
    }

    @DeleteMapping("/teams/{teamId}/members/{username}")
    public ResponseEntity<Void> removeMember(@PathVariable UUID teamId, @PathVariable String username, Principal principal) {
        commands.handle(new TeamCommand.RemoveMember(principal.getName(), context.now(), teamId, username));
        return ResponseEntity.noContent().build();
    }
}
