package io.github.jeffromine.notes.config;

import io.github.jeffromine.notes.domain.core.Limits;
import io.github.jeffromine.notes.domain.model.Membership;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/** Everything under {@code notes.*} in application.yml; every value can be overridden by an environment variable. */
@ConfigurationProperties("notes")
public record NotesProperties(
        Store store,
        Security security,
        List<String> adminUsers,
        Seed seed,
        Sse sse,
        LimitValues limits,
        Attachments attachments,
        Search search,
        Cors cors) {

    /** @param snapshotPath where the in-memory store persists its JSON snapshot; null keeps data in memory only */
    public record Store(Path snapshotPath) {}

    public record Security(int bcryptStrength) {}

    public record Seed(boolean enabled, List<SeedUser> users, List<SeedTeam> teams) {
        public record SeedUser(String username, String password, String displayName) {}

        public record SeedTeam(String name, List<SeedMember> members) {}

        public record SeedMember(String username, Membership.Role role) {}
    }

    public record Sse(Duration heartbeat, Duration timeout) {}

    public record LimitValues(
            int titleMaxChars,
            int bodyMaxChars,
            int tagsMax,
            int entryBodyMaxChars,
            int entriesMax,
            int attachmentsMax,
            int filenameMaxChars,
            int teamNameMaxChars) {}

    public record Attachments(Path dir, DataSize maxSize, List<String> allowedTypes, int extractMaxChars) {}

    public record Search(boolean reindexOnStartup) {}

    public record Cors(List<String> allowedOrigins) {}

    /** Empty when no snapshot path is configured (the {@code store} block may be absent or empty). */
    public java.util.Optional<Path> snapshotPath() {
        return java.util.Optional.ofNullable(store).map(Store::snapshotPath);
    }

    public Limits toLimits() {
        return new Limits(
                limits.titleMaxChars(),
                limits.bodyMaxChars(),
                limits.tagsMax(),
                limits.entryBodyMaxChars(),
                limits.entriesMax(),
                limits.attachmentsMax(),
                attachments.maxSize().toBytes(),
                Set.copyOf(attachments.allowedTypes()),
                limits.filenameMaxChars(),
                limits.teamNameMaxChars());
    }
}
