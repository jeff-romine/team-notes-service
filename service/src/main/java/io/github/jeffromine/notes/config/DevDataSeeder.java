package io.github.jeffromine.notes.config;

import io.github.jeffromine.notes.application.CommandContext;
import io.github.jeffromine.notes.application.NoteCommands;
import io.github.jeffromine.notes.application.ReindexService;
import io.github.jeffromine.notes.application.port.MembershipRepository;
import io.github.jeffromine.notes.application.port.NoteRepository;
import io.github.jeffromine.notes.application.port.TeamRepository;
import io.github.jeffromine.notes.application.port.UserRepository;
import io.github.jeffromine.notes.domain.core.NoteCommand;
import io.github.jeffromine.notes.domain.model.BodyFormat;
import io.github.jeffromine.notes.domain.model.Membership;
import io.github.jeffromine.notes.domain.model.Note;
import io.github.jeffromine.notes.domain.model.Team;
import io.github.jeffromine.notes.domain.model.User;
import io.github.jeffromine.notes.domain.model.Visibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Seeds the users and teams from {@code notes.seed} and, on a store with no notes at all, three sample notes
 * (created through the same commands the API uses). Idempotent per entity: a user or team that exists is left
 * alone, and a membership that exists keeps whatever role an admin gave it since. Then rebuilds the search index,
 * because the default index lives in memory and a restarted service would otherwise search an empty one.
 */
@Component
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final NotesProperties props;
    private final UserRepository users;
    private final TeamRepository teams;
    private final MembershipRepository memberships;
    private final NoteRepository notes;
    private final NoteCommands noteCommands;
    private final ReindexService reindex;
    private final PasswordEncoder encoder;
    private final CommandContext context;

    public DevDataSeeder(NotesProperties props, UserRepository users, TeamRepository teams, MembershipRepository memberships,
                         NoteRepository notes, NoteCommands noteCommands, ReindexService reindex, PasswordEncoder encoder,
                         CommandContext context) {
        this.props = props;
        this.users = users;
        this.teams = teams;
        this.memberships = memberships;
        this.notes = notes;
        this.noteCommands = noteCommands;
        this.reindex = reindex;
        this.encoder = encoder;
        this.context = context;
    }

    /** Stable ids so reruns and both store adapters agree on which team is which. */
    public static UUID teamId(String name) {
        return UUID.nameUUIDFromBytes(("team:" + name).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.seed().enabled()) {
            seed();
        }
        if (props.search().reindexOnStartup()) {
            long indexed = reindex.reindex();
            log.info("search index rebuilt: {} notes", indexed);
        }
    }

    private void seed() {
        for (NotesProperties.Seed.SeedUser u : props.seed().users()) {
            if (users.insertIfAbsent(new User(u.username(), encoder.encode(u.password()), u.displayName(), context.now()))) {
                log.info("seeded user {}", u.username());
            }
        }
        for (NotesProperties.Seed.SeedTeam t : props.seed().teams()) {
            UUID id = teamId(t.name());
            if (teams.insertIfAbsent(new Team(id, t.name(), context.now()))) {
                log.info("seeded team {} ({})", t.name(), id);
            }
            Set<String> existing = memberships.byTeam(id).stream().map(Membership::username).collect(java.util.stream.Collectors.toSet());
            for (NotesProperties.Seed.SeedMember m : t.members()) {
                if (!existing.contains(m.username())) {
                    memberships.put(new Membership(id, m.username(), m.role()));
                }
            }
        }
        boolean empty;
        try (Stream<Note> all = notes.scanAll()) {
            empty = all.findAny().isEmpty();
        }
        if (empty && !props.seed().users().isEmpty()) {
            sampleNotes();
        }
    }

    private void sampleNotes() {
        UUID platform = teamId("platform");
        noteCommands.handle(new NoteCommand.CreateNote("alice", context.now(), context.newId(), "Release checklist",
                """
                # Release checklist

                1. Freeze `main` and tag the release candidate.
                2. Run the **smoke suite** against staging.
                3. Announce in the team channel.

                Attach the sign-off PDF to this note when the release ships.
                """, BodyFormat.MARKDOWN, Set.of("release", "process"), Visibility.TEAM, platform));
        noteCommands.handle(new NoteCommand.CreateNote("bob", context.now(), context.newId(), "Onboarding guide",
                "Welcome. Ask for access to the platform and data teams, then read the release checklist. "
                        + "Lunch is at noon; the coffee machine wants descaling on Fridays.",
                BodyFormat.PLAIN, Set.of("onboarding"), Visibility.ORGANIZATION, null));
        noteCommands.handle(new NoteCommand.CreateNote("carol", context.now(), context.newId(), "Ideas parking lot",
                "- rename the data pipeline\n- try the new query planner", BodyFormat.MARKDOWN, Set.of("ideas"),
                Visibility.PRIVATE, null));
        log.info("seeded three sample notes");
    }
}
